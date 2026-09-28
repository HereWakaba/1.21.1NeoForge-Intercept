package com.ryjs.intercept.util.agent;

import com.ryjs.intercept.util.kp.entity.DeathShellFactory;
import net.minecraft.world.entity.player.Player;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public final class PlayerDeathTransformer implements ClassFileTransformer {

    private static final PlayerDeathTransformer INSTANCE = new PlayerDeathTransformer();


    private static final Set<String> TARGETS = ConcurrentHashMap.newKeySet();

    private static final Map<String, byte[]> ORIGINALS = new ConcurrentHashMap<>();

    private static final Map<String, String> ERRORS = new ConcurrentHashMap<>();

    private static volatile boolean armed;
    private static volatile boolean restoring;
    private static volatile boolean installed;

    private PlayerDeathTransformer() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        if (!AgentBackend.addTransformer(INSTANCE)) {
            return;
        }
        installed = true;
        LOGGER("PlayerDeathTransformer 已注册");
    }


    public static List<String> kill(Instrumentation inst, boolean deep) {
        List<String> lines = new ArrayList<>();
        if (inst == null) {
            lines.add("玩家类补丁跳过(" + AgentBackend.note() + ")");
            return lines;
        }
        install();
        if (!installed) {
            lines.add("玩家类补丁跳过(transformer 注册失败:" + AgentBackend.note() + ")");
            return lines;
        }
        TARGETS.clear();




        List<Class<?>> playerOnly = new ArrayList<>();
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c == null || !Player.class.isAssignableFrom(c)) {
                continue;
            }
            if (c.isInterface() || c.isHidden() || c.isPrimitive() || c.isArray()) {
                continue;
            }
            playerOnly.add(c);
        }


        List<Class<?>> shared = new ArrayList<>();
        if (deep) {
            lines.add("⚠ deep：将改写 LivingEntity/Entity 的方法体，影响全部生物，不只玩家");
            for (Class<?> k = Player.class.getSuperclass(); k != null && k != Object.class; k = k.getSuperclass()) {
                shared.add(k);
            }
        }
        if (playerOnly.isEmpty() && shared.isEmpty()) {
            lines.add("没有可处理的玩家类");
            return lines;
        }
        restoring = false;

        for (Class<?> c : playerOnly) {
            patchOne(inst, c, false, lines);
        }
        for (Class<?> c : shared) {
            patchOne(inst, c, true, lines);
        }
        TARGETS.clear();
        return lines;
    }


    private static void patchOne(Instrumentation inst, Class<?> c, boolean isShared, List<String> lines) {
        String internal = c.getName().replace('.', '/');
        int hits = DeathShellFactory.replaceableCount(c, DeathShellFactory.Depth.PLAYER);
        if (hits == 0) {

            lines.add(c.getName() + (isShared ? "[共享父类]" : "") + " 无命中（本类没声明这些方法），不动");
            return;
        }
        TARGETS.clear();
        TARGETS.add(internal);
        ERRORS.remove(internal);
        armed = true;
        boolean changed;
        try {
            inst.retransformClasses(c);
            changed = ORIGINALS.containsKey(internal);
        } catch (Throwable t) {
            lines.add(c.getName() + " 命中 " + hits + " 个，retransform 被拒:"
                    + t.getClass().getSimpleName() + " " + t.getMessage());
            return;
        } finally {
            armed = false;
        }
        String err = ERRORS.remove(internal);
        if (changed) {
            lines.add(c.getName() + (isShared ? "[共享父类]" : "") + " 已就地改写 " + hits + " 个方法");
        } else if (err != null) {
            lines.add(c.getName() + " 命中 " + hits + " 个，但补丁生成失败:" + err);
        } else {
            lines.add(c.getName() + " 命中 " + hits + " 个，但字节码没变化（可能已是补丁版）");
        }
    }


    public static List<String> restore(Instrumentation inst) {
        List<String> lines = new ArrayList<>();
        if (inst == null || ORIGINALS.isEmpty()) {
            lines.add("没有打过补丁的玩家类");
            return lines;
        }
        Set<String> names = new LinkedHashSet<>(ORIGINALS.keySet());
        List<Class<?>> back = new ArrayList<>();
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c != null && names.contains(c.getName().replace('.', '/'))) {
                back.add(c);
            }
        }
        restoring = true;
        try {
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

    public static boolean isPatched(Class<?> c) {
        return c != null && ORIGINALS.containsKey(c.getName().replace('.', '/'));
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
            if (!armed || !TARGETS.contains(className)) {
                return null;
            }
            byte[] patched = DeathShellFactory.patch(classfileBuffer, DeathShellFactory.Depth.PLAYER);
            if (patched == null) {
                return null;
            }


            ORIGINALS.putIfAbsent(className, classfileBuffer.clone());
            return patched;
        } catch (Throwable t) {
            ORIGINALS.remove(className);
            ERRORS.put(className, t.getClass().getSimpleName() + " " + t.getMessage());
            LOGGER("玩家类死亡壳补丁失败:" + className + " " + t);
            return null;
        }
    }

    private static void LOGGER(String msg) {
        System.out.println("[Intercept-Agent] " + msg);
        org.slf4j.LoggerFactory.getLogger("Intercept-Agent").info(msg);
    }
}
