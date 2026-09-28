package com.ryjs.intercept.service.agent;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Set;

import com.ryjs.intercept.service.agent.transformer.ArTransformer;
import com.ryjs.intercept.service.agent.transformer.RevertSentinel;


public final class ArWatchdog {





    private static final long FIRST_DELAY_MS = 10_000;
    private static final long CYCLE_MS = 60_000;
    private static final int CHUNK = 32;
    private static final long CHUNK_PAUSE_MS = 20;

    private static volatile Instrumentation inst;
    private static volatile ArTransformer ar;
    private static volatile RevertSentinel sentinel;
    private static volatile Thread worker;
    private static volatile boolean fullPassPending = true;
    private static volatile long sweeps;
    private static volatile long refused;

    private ArWatchdog() {}


    public static synchronized void arm(Instrumentation instrumentation, ArTransformer transformer, RevertSentinel tail) {
        inst = instrumentation;
        ar = transformer;
        sentinel = tail;
        fullPassPending = true;
    }

    public static synchronized void start(Instrumentation instrumentation, ArTransformer transformer, RevertSentinel tail) {
        if (worker != null)
            return;
        arm(instrumentation, transformer, tail);
        Thread t = new Thread(ArWatchdog::loop, "intercept-ar-watchdog");
        t.setDaemon(true);
        worker = t;
        t.start();
        ArOrigin.report("哨兵线程启动：" + FIRST_DELAY_MS / 1000 + "s 后先补插一轮已加载类，之后每 " + CYCLE_MS / 1000 + "s 复查还原");
    }

    public static synchronized void stop() {
        Thread t = worker;
        worker = null;
        if (t != null)
            t.interrupt();
    }

    public static boolean running() {
        return worker != null;
    }


    public static void kick() {
        fullPassPending = true;
    }

    public static String status() {
        ArTransformer t = ar;
        RevertSentinel s = sentinel;
        return "哨兵 running=" + running() + " 轮次=" + sweeps + " 已插类=" + (t == null ? 0 : t.gatedOnce().size())
                + " gate完好=" + (s == null ? 0 : s.intact()) + " 被冲掉=" + (s == null ? 0 : s.stripped())
                + " 拒于回调=" + refused;
    }

    private static void loop() {
        Thread self = Thread.currentThread();
        try {
            Thread.sleep(FIRST_DELAY_MS);
        } catch (InterruptedException e) {
            return;
        }
        while (worker == self) {
            try {
                sweep(fullPassPending);
            } catch (Throwable t) {
                ArOrigin.report("哨兵一轮失败：" + t);
            }
            fullPassPending = false;
            try {
                Thread.sleep(CYCLE_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }


    public static void sweep(boolean full) {
        Instrumentation i = inst;
        ArTransformer t = ar;
        RevertSentinel s = sentinel;
        if (i == null || t == null)
            return;
        if (ThreadGuard.busy()) {
            refused++;
            ArOrigin.report("拒绝在 transform 回调里发起 retransform（会自锁），等哨兵线程自己跑");
            return;
        }
        toTail(i, s);
        Set<String> ours = t.gatedOnce();
        long begin = System.currentTimeMillis();
        long intact0 = s == null ? 0 : s.intact();
        long strip0 = s == null ? 0 : s.stripped();
        int submitted = 0, skipped = 0;
        for (Class<?> c : i.getAllLoadedClasses()) {
            if (c == null || c.isHidden() || !i.isModifiableClass(c))
                continue;
            String name = c.getName().replace('.', '/');
            ClassLoader loader = c.getClassLoader();
            if (full) {

                if (loader == null || !ArOrigin.isInstrumentable(name, loader, pd(c), c))
                    continue;
            } else if (!ours.contains(ArTransformer.key(loader, name)))
                continue;
            try {
                i.retransformClasses(c);
                submitted++;
                if (submitted % CHUNK == 0)
                    Thread.sleep(CHUNK_PAUSE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable ignored) {
                skipped++;
            }
        }
        sweeps++;
        ArOrigin.report("哨兵" + (full ? "补插" : "复查") + "：提交 " + submitted + " 个类"
                + (skipped == 0 ? "" : "，跳过 " + skipped)
                + (s == null ? "" : "，gate 完好 " + (s.intact() - intact0) + "，被冲掉 " + (s.stripped() - strip0))
                + "，用时 " + (System.currentTimeMillis() - begin) + "ms");
    }


    private static void toTail(Instrumentation i, RevertSentinel s) {
        if (s == null)
            return;
        try {
            i.removeTransformer(s);
            i.addTransformer(s, true);
        } catch (Throwable t) {
            ArOrigin.report("重挂链尾哨兵失败（下一轮再试）：" + t);
        }
    }

    private static ProtectionDomain pd(Class<?> c) {
        try {
            return c.getProtectionDomain();
        } catch (Throwable t) {
            return null;
        }
    }
}
