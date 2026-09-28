package com.ryjs.intercept.service.agent;

import com.ryjs.intercept.util.agent.AgentBackend;
import com.ryjs.intercept.util.kp.EntityUtil;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;


public final class HiddenRetrans {

    private HiddenRetrans() {
    }

    private static volatile boolean booted;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Intercept-Agent");
    private static volatile boolean bootedUnsafe;
    private static String note = "未初始化";
    private static String probeNote = "未探测";

    private static Object unsafe;
    private static Method uGetLongObj;
    private static Method uGetIntObj;
    private static Method uGetIntAddr;
    private static Method uPutIntAddr;
    private static Method uAllocate;
    private static MethodHandle mhRetransform0;
    private static VarHandle vhNativeAgent;

    private static int klassOffset = -1;
    private static int klassShift = -1;
    private static long classSpaceBase = -1;
    private static volatile boolean calibrated;

    private static int flagsOffset = -1;
    private static int hiddenBit = -1;


    public static synchronized boolean unsafeReady() {
        if (bootedUnsafe) {
            return calibrated;
        }
        bootedUnsafe = true;
        try {
            bootUnsafe();
            calibrate();
        } catch (Throwable t) {
            note = "Unsafe 初始化异常:" + t.getClass().getSimpleName() + " " + t.getMessage();
            calibrated = false;
        }
        return calibrated;
    }

    public static synchronized boolean available() {
        if (!unsafeReady()) {
            return false;
        }
        if (booted) {
            return flagsOffset >= 0 && mhRetransform0 != null && hasNativeAgentHandle();
        }
        if (AgentBackend.instrumentation() == null) {
            AgentBackend.attach();
        }
        if (AgentBackend.instrumentation() == null) {
            note = "自附加没成功(" + AgentBackend.note() + ")，拿不到 Instrumentation（类级补丁/retrans 不可用，换头仍可用）";
            return false;
        }
        booted = true;
        try {
            bootInstrumentation();
            probeAccessFlags();
        } catch (Throwable t) {
            note = "retrans 通道初始化异常:" + t.getClass().getSimpleName() + " " + t.getMessage();
            flagsOffset = -1;
        }
        return flagsOffset >= 0 && mhRetransform0 != null && hasNativeAgentHandle();
    }

    private static boolean hasNativeAgentHandle() {
        return vhNativeAgent != null || nativeAgentField != null;
    }

    public static String note() {
        return probeNote + " | 通道:" + note + " | Class.klass偏移=" + klassOffset
                + " access_flags偏移=" + flagsOffset + " hidden位=0x" + Integer.toHexString(hiddenBit);
    }

    public static List<String> retransform(Class<?>... classes) {
        List<String> lines = new ArrayList<>();
        AgentBackend.attach();
        Instrumentation inst = AgentBackend.instrumentation();
        if (inst == null) {
            lines.add("没有 Instrumentation，retransform 不可用");
            return lines;
        }
        for (Class<?> c : classes) {
            if (c == null || c.isPrimitive() || c.isArray()) {
                continue;
            }
            String name = hiddenName(c);
            try {
                if (!c.isHidden()) {
                    if (!inst.isModifiableClass(c)) {
                        lines.add(name + " 不可修改，跳过");
                        continue;
                    }
                    inst.retransformClasses(c);
                    lines.add(name + " ok");
                    continue;
                }
                if (!available()) {
                    lines.add(name + " 是隐藏类，但通道不齐（" + note() + "）");
                    continue;
                }
                boolean done = retransformHidden(c);
                lines.add(name + (done ? " 隐藏类 ok" : " 隐藏类失败（hidden 位对不上）"));
            } catch (Throwable t) {
                lines.add(name + " 失败:" + t.getClass().getSimpleName() + " " + t.getMessage());
            }
        }
        return lines;
    }

    private static boolean retransformHidden(Class<?> hidden) throws Throwable {
        long klass = klassAddr(hidden);
        int flags = (int) uGetIntAddr.invoke(unsafe, klass + flagsOffset);
        if ((flags & hiddenBit) == 0) {
            LOGGER.info("hidden 位不在 0x{} 上:{}", Integer.toHexString(hiddenBit), hidden.getName());
            return false;
        }
        uPutIntAddr.invoke(unsafe, klass + flagsOffset, flags & ~hiddenBit);
        try {
            mhRetransform0.invoke(AgentBackend.instrumentation(), nativeAgent(), new Class<?>[]{hidden});
            return true;
        } finally {
            uPutIntAddr.invoke(unsafe, klass + flagsOffset, flags);
        }
    }

    public static List<Class<?>> hierarchyOf(Class<?> base) {
        AgentBackend.attach();
        LinkedHashSet<Class<?>> out = new LinkedHashSet<>();
        for (Class<?> k = base; k != null && k != Object.class; k = k.getSuperclass()) {
            out.add(k);
        }
        Instrumentation inst = AgentBackend.instrumentation();
        if (inst != null) {
            for (Class<?> c : inst.getAllLoadedClasses()) {
                if (c != null && c != base && !c.isInterface() && base.isAssignableFrom(c)) {
                    out.add(c);
                }
            }
        }
        return new ArrayList<>(out);
    }

    public static String hiddenName(Class<?> c) {
        String n = c.getName();
        int slash = n.lastIndexOf('/');
        return slash > 0 ? n.substring(0, slash) : n;
    }

    public static long classMetaAddr(Class<?> c) throws Throwable {
        if (!unsafeReady()) {
            throw new IllegalStateException("Unsafe/偏移没就绪:" + note());
        }
        return (long) uGetLongObj.invoke(unsafe, c, (long) klassOffset);
    }


    public static int narrowKlass(Class<?> shell, Object anchor) throws Throwable {
        if (!unsafeReady()) {
            throw new IllegalStateException("class space 未标定:" + note());
        }
        long anchorMeta = classMetaAddr(anchor.getClass());
        long anchorNarrow = ((long) (int) uGetIntObj.invoke(unsafe, anchor, 8L)) & 0xFFFFFFFFL;
        if (anchorMeta - (anchorNarrow << klassShift) != classSpaceBase) {
            throw new IllegalStateException("锚点 " + anchor.getClass().getName() + " 不在标定出的 class space 里: meta=0x"
                    + Long.toHexString(anchorMeta) + " narrow=0x" + Long.toHexString(anchorNarrow));
        }
        long meta = classMetaAddr(shell);
        long delta = meta - classSpaceBase;
        long align = (1L << klassShift) - 1;
        if (delta <= 0 || (delta & align) != 0 || (delta >> klassShift) > 0xFFFFFFFFL) {
            throw new IllegalStateException("shell 不在 class space 里: meta=0x" + Long.toHexString(meta)
                    + " base=0x" + Long.toHexString(classSpaceBase));
        }
        return (int) (delta >> klassShift);
    }


    private static void bootUnsafe() throws Throwable {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        unsafe = theUnsafe.get(null);
        uGetLongObj = unsafeClass.getMethod("getLong", Object.class, long.class);
        uGetIntObj = unsafeClass.getMethod("getInt", Object.class, long.class);
        uGetIntAddr = unsafeClass.getMethod("getInt", long.class);
        uPutIntAddr = unsafeClass.getMethod("putInt", long.class, int.class);
        uAllocate = unsafeClass.getMethod("allocateInstance", Class.class);
    }

    private static void calibrate() throws Throwable {
        Object[] probes = {
                uAllocate.invoke(unsafe, com.ryjs.intercept.util.kp.entity.EmptyEntity.class),
                uAllocate.invoke(unsafe, com.ryjs.intercept.util.kp.FakeEventBus.class),
                uAllocate.invoke(unsafe, com.ryjs.intercept.util.AR.class),
        };
        for (int off = 8; off <= 32; off += 4) {
            for (int shift = 0; shift <= 4; shift++) {
                long base = 0;
                int hits = 0;
                boolean ok = true;
                for (Object p : probes) {
                    long meta = (long) uGetLongObj.invoke(unsafe, p.getClass(), (long) off);
                    long narrow = ((long) (int) uGetIntObj.invoke(unsafe, p, 8L)) & 0xFFFFFFFFL;
                    long b = meta - (narrow << shift);
                    if (!inMetadataRange(b) || (hits > 0 && b != base)) {
                        ok = false;
                        break;
                    }
                    base = b;
                    hits++;
                }
                if (ok && hits == probes.length) {
                    klassOffset = off;
                    klassShift = shift;
                    classSpaceBase = base;
                    calibrated = true;
                    probeNote = "标定成功 offset=" + off + " shift=" + shift
                            + " base=0x" + Long.toHexString(base);
                    return;
                }
            }
        }
        klassOffset = -1;
        klassShift = -1;
        calibrated = false;
        probeNote = "标定失败：offset 8..32 × shift 0..4 里找不到让 3 个动态探针基址一致的组合";
    }

    private static void bootInstrumentation() throws Throwable {
        Class<?> impl = Class.forName("sun.instrument.InstrumentationImpl");
        MethodHandles.Lookup trusted = (MethodHandles.Lookup) EntityUtil.LOOKUP;
        mhRetransform0 = trusted.findVirtual(impl, "retransformClasses0",
                MethodType.methodType(void.class, long.class, Class[].class));
        try {
            vhNativeAgent = trusted.findVarHandle(impl, "mNativeAgent", long.class);
        } catch (Throwable varHandleRejected) {
            Field f = impl.getDeclaredField("mNativeAgent");
            vhNativeAgent = trusted.unreflectVarHandle(f);
            nativeAgentField = null;
        }
    }

    private static Field nativeAgentField;

    private static long nativeAgent() throws Throwable {
        if (vhNativeAgent != null) {
            return (long) vhNativeAgent.get(AgentBackend.instrumentation());
        }
        return nativeAgentField.getLong(AgentBackend.instrumentation());
    }


    private static void probeAccessFlags() throws Throwable {
        long shellKlass = klassAddr(com.ryjs.intercept.util.kp.entity.EmptyEntity.class);
        List<Integer> candidates = new ArrayList<>();
        for (int off = 0; off <= 512; off += 4) {
            if ((int) uGetIntAddr.invoke(unsafe, shellKlass + off) == 0x0021) {
                candidates.add(off);
            }
        }
        if (candidates.isEmpty()) {
            flagsOffset = -1;
            probeNote = "没扫到 access_flags 偏移";
            return;
        }
        long hiddenKlass = klassAddr(makeProbeHiddenClass());
        for (int off : candidates) {
            int flags = (int) uGetIntAddr.invoke(unsafe, hiddenKlass + off);
            int extra = flags & ~0x0021;
            if ((flags & 0x0021) == 0x0021 && extra != 0 && Integer.bitCount(extra) == 1) {
                flagsOffset = off;
                hiddenBit = extra;
                probeNote = "ok（Agt 那套写死的 164/0x04000000 在这台 JDK 上是 "
                        + off + "/0x" + Integer.toHexString(hiddenBit) + "）";
                return;
            }
        }
        flagsOffset = -1;
        probeNote = "候选偏移 " + candidates + " 都对不出唯一 hidden 位";
    }

    private static boolean inMetadataRange(long v) {
        return v > 0x1_0000_0000L && v < 0x8_0000_0000_0000L;
    }


    private static Class<?> makeProbeHiddenClass() throws Throwable {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                "com/ryjs/intercept/service/agent/HiddenProbe", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        return ((MethodHandles.Lookup) EntityUtil.LOOKUP)
                .defineHiddenClass(cw.toByteArray(), true, MethodHandles.Lookup.ClassOption.STRONG)
                .lookupClass();
    }

    private static long klassAddr(Class<?> c) throws Throwable {
        return (long) uGetLongObj.invoke(unsafe, c, (long) klassOffset);
    }
}
