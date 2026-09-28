package com.ryjs.intercept.service.agent.transformer;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.ryjs.intercept.service.agent.ArOrigin;


public class RevertSentinel implements ClassFileTransformer {

    private final Set<String> gated;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private final AtomicLong intact = new AtomicLong();
    private final AtomicLong stripped = new AtomicLong();

    public RevertSentinel(ArTransformer ar) {
        this.gated = ar.gatedOnce();
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || !gated.contains(ArTransformer.key(loader, className)))
            return null;
        if (ArTransformer.hasGate(classfileBuffer)) {
            intact.incrementAndGet();
            return null;
        }
        long n = stripped.incrementAndGet();
        ArOrigin.report("★gate 被冲掉 " + className + "（第 " + n + " 次，交给哨兵线程补插）");
        if (reported.add(className + '@' + System.identityHashCode(loader)))
            ArOrigin.report("第一次见：" + className + " ← loader=" + loader);
        return null;
    }


    public long intact() {
        return intact.get();
    }


    public long stripped() {
        return stripped.get();
    }
}
