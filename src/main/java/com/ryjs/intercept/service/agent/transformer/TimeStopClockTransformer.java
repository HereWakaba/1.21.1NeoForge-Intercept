package com.ryjs.intercept.service.agent.transformer;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import com.ryjs.intercept.service.agent.ArOrigin;
import com.ryjs.intercept.service.agent.CoexGate;
import com.ryjs.intercept.service.agent.ThreadGuard;
import com.ryjs.intercept.service.agent.TypeIndex;
import com.ryjs.intercept.service.agent.asm.ArClassWriter;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class TimeStopClockTransformer implements ClassFileTransformer {

    private static final String SYS_OWNER = "java/lang/System";
    private static final String SYS_NAME = "currentTimeMillis";
    private static final String SYS_DESC = "()J";

    private static final String REPL_OWNER = "com/ryjs/intercept/util/timestop/TimeStopState";
    private static final String REPL_NAME = "nowMs";

    private static final String MODS_SEGMENT = "/mods/";

    private static final String[] CLIENT_HINTS = {
            "/client/", "/render/", "/renderer/", "/gui/", "/screens/", "/screen/",
            "/effect/", "/particle/", "/hud/", "/model/", "/shader/", "/animation/",
            "/visual/", "/view/"
    };

    private final TypeIndex index = new TypeIndex();

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> beingRedefined,
                            ProtectionDomain pd, byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null) return null;

        if (!looksLikeClientRender(className)) return null;

        if (!containsAscii(classfileBuffer, SYS_OWNER)) return null;
        if (!ThreadGuard.enter()) return null;
        try {

            if (!ArOrigin.isInstrumentable(className, loader, pd, beingRedefined)) return null;

            if (!ArOrigin.gateVisible(loader)) return null;

            String src = ArOrigin.sourceUrl(className, loader, pd, beingRedefined);
            if (!isUnderMods(src)) return null;

            ClassReader cr = new ClassReader(classfileBuffer);
            ArClassWriter cw = new ArClassWriter(cr, loader, index);
            int[] rewritten = new int[1];
            cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                  String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName,
                                                    String mDescriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKESTATIC && SYS_OWNER.equals(owner)
                                    && SYS_NAME.equals(mName) && SYS_DESC.equals(mDescriptor)) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, REPL_OWNER,
                                        REPL_NAME, SYS_DESC, false);
                                rewritten[0]++;
                            } else {
                                super.visitMethodInsn(opcode, owner, mName, mDescriptor, isInterface);
                            }
                        }
                    };
                }
            }, 0);
            if (rewritten[0] == 0) return null;
            ArOrigin.report("TimeStopClock 改写 " + className + "：System.currentTimeMillis() × " + rewritten[0]
                    + " ← " + CoexGate.norm(src));
            return cw.toByteArray();
        } catch (Throwable t) {
            ArOrigin.report("TimeStopClock 跳过 " + className + ": " + t);
            return null;
        } finally {
            ThreadGuard.leave();
        }
    }

    public static boolean shouldHandle(String internalName) {
        return looksLikeClientRender(internalName);
    }

    private static boolean looksLikeClientRender(String name) {
        if (name == null) return false;
        for (String h : CLIENT_HINTS) {
            if (name.contains(h)) return true;
        }
        return false;
    }

    private static boolean isUnderMods(String sourceUrl) {
        if (sourceUrl == null) return false;
        try {
            String norm = CoexGate.norm(sourceUrl);
            return norm != null && norm.contains(MODS_SEGMENT);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean containsAscii(byte[] bytes, String needle) {
        return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains(needle);
    }
}
