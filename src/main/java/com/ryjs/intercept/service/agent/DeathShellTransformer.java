package com.ryjs.intercept.service.agent;

import com.ryjs.intercept.util.kp.entity.DeathShellFactory;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class DeathShellTransformer implements ClassFileTransformer {

    private static final Map<String, DeathShellFactory.Depth> WANTED = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> ORIGINALS = new ConcurrentHashMap<>();

    private static volatile boolean restoring;
    private static volatile boolean installed;

    private DeathShellTransformer() {
    }

    public static synchronized void install(Instrumentation inst) {
        if (installed || inst == null) {
            return;
        }
        inst.addTransformer(new DeathShellTransformer(), true);
        installed = true;
        AgentChain.report("DeathShellTransformer 已注册（可 retransform）");
    }

    public static boolean installed() {
        return installed;
    }

    public static void want(Class<?> c, DeathShellFactory.Depth depth) {
        WANTED.put(c.getName().replace('.', '/'), depth);
    }

    public static List<String> patchNow(Instrumentation inst, Class<?>... classes) {
        List<String> lines = new ArrayList<>();
        restoring = false;
        try {
            inst.retransformClasses(classes);
            for (Class<?> c : classes) {
                String internal = c.getName().replace('.', '/');
                lines.add(c.getName() + (ORIGINALS.containsKey(internal) ? " 已打补丁" : " 无匹配方法，未改"));
            }
        } catch (Throwable t) {
            for (Class<?> c : classes) {
                lines.add(c.getName() + " 补丁失败:" + t.getClass().getSimpleName() + " " + t.getMessage());
            }
        } finally {
            WANTED.clear();
        }
        return lines;
    }


    public static List<String> patchHierarchy(Instrumentation inst, List<Class<?>> hierarchy,
                                              DeathShellFactory.Depth depth) {
        List<String> lines = new ArrayList<>();
        if (inst == null) {
            lines.add("没有 Instrumentation，类级补丁不可用");
            return lines;
        }
        install(inst);
        List<Class<?>> patched = new ArrayList<>();
        List<Class<?>> plain = new ArrayList<>();
        for (Class<?> c : hierarchy) {
            if (c == null || c.isPrimitive() || c.isArray() || c.isHidden()) {
                continue;
            }
            if (java.lang.reflect.Modifier.isAbstract(c.getModifiers()) || c.isInterface()) {
                plain.add(c);
                continue;
            }
            if (!inst.isModifiableClass(c)) {
                lines.add(c.getName() + " 不可重定义，跳过");
                continue;
            }
            want(c, depth);
            patched.add(c);
        }
        if (!patched.isEmpty()) {
            lines.addAll(patchNow(inst, patched.toArray(new Class<?>[0])));
        }
        for (Class<?> c : plain) {
            try {
                inst.retransformClasses(c);
                lines.add(c.getName() + " 抽象层，仅 retransform");
            } catch (Throwable t) {
                lines.add(c.getName() + " retransform 失败:" + t.getClass().getSimpleName() + " " + t.getMessage());
            }
        }
        return lines;
    }

    public static boolean isPatched(Class<?> c) {
        return c != null && ORIGINALS.containsKey(c.getName().replace('.', '/'));
    }

    public static List<String> restoreAll(Instrumentation inst) {
        List<String> lines = new ArrayList<>();
        List<Class<?>> back = new ArrayList<>();
        try {
            for (Class<?> c : inst.getAllLoadedClasses()) {
                if (c != null && ORIGINALS.containsKey(c.getName().replace('.', '/'))) {
                    back.add(c);
                }
            }
            if (back.isEmpty()) {
                lines.add("没有被打过补丁的类");
                return lines;
            }
            restoring = true;
            inst.retransformClasses(back.toArray(new Class<?>[0]));
            for (Class<?> c : back) {
                lines.add(c.getName() + " 已还原");
                ORIGINALS.remove(c.getName().replace('.', '/'));
            }
        } catch (Throwable t) {
            lines.add("还原失败:" + t.getClass().getSimpleName() + " " + t.getMessage());
        } finally {
            restoring = false;
        }
        return lines;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }
        try {
            if (restoring) {
                byte[] original = ORIGINALS.get(className);
                return original == null ? null : original.clone();
            }
            DeathShellFactory.Depth depth = WANTED.get(className);
            if (depth == null) {
                return null;
            }
            byte[] patched = DeathShellFactory.patch(classfileBuffer, depth);
            if (patched == null) {
                return null;
            }
            ORIGINALS.putIfAbsent(className, classfileBuffer.clone());
            return patched;
        } catch (Throwable t) {
            ORIGINALS.remove(className);
            AgentChain.report("死亡壳补丁失败:" + className + " " + t);
            return null;
        }
    }
}
