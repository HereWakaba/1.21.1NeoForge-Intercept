package com.ryjs.intercept.service.agent.transformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import com.ryjs.intercept.service.agent.ArOrigin;
import com.ryjs.intercept.service.agent.ThreadGuard;
import com.ryjs.intercept.service.agent.TypeIndex;
import com.ryjs.intercept.service.agent.asm.ArClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;


public class PluginGateTransformer implements ClassFileTransformer {

    static final String HANDLER = "cpw/mods/modlauncher/LaunchPluginHandler";
    static final String GATE = "com/ryjs/intercept/service/agent/PluginGate";
    static final String FILTER_DESC = "(Ljava/util/stream/Stream;)Ljava/util/stream/Stream;";

    private final TypeIndex index = new TypeIndex();

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {
        if (className == null || !HANDLER.equals(className) || !ThreadGuard.enter())
            return null;
        try {
            return patch(loader, classfileBuffer);
        } catch (Throwable t) {
            ArOrigin.report("插件服务闸口补丁失败，放弃：" + t);
            return null;
        } finally {
            ThreadGuard.leave();
        }
    }

    private byte[] patch(ClassLoader loader, byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        reader.accept(cn, 0);
        MethodNode target = null;
        for (MethodNode mn : cn.methods) {
            if ("<init>".equals(mn.name) && mn.desc.contains("Ljava/util/stream/Stream;") && mn.instructions != null && mn.instructions.size() > 0) {
                target = mn;
                break;
            }
        }
        if (target == null) {
            ArOrigin.report("  没找到 LaunchPluginHandler.<init>(Stream)，不硬猜签名");
            return null;
        }
        if (alreadyPatched(target)) {
            ArOrigin.report("  LaunchPluginHandler.<init>(Stream) 已经带闸口，跳过");
            return null;
        }
        InsnList prefix = new InsnList();
        prefix.add(new VarInsnNode(ALOAD, 1));
        prefix.add(new MethodInsnNode(INVOKESTATIC, GATE, "filter", FILTER_DESC, false));
        prefix.add(new VarInsnNode(ASTORE, 1));
        target.instructions.insert(prefix);
        ArClassWriter cw = new ArClassWriter(reader, loader, index);
        cn.accept(cw);
        byte[] out = cw.toByteArray();
        ArOrigin.report("✓ 已插桩 LaunchPluginHandler.<init>" + target.desc + " → PluginGate.filter（" + bytes.length + "B → " + out.length + "B）");
        return out;
    }

    private static boolean alreadyPatched(MethodNode mn) {
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext())
            if (insn instanceof MethodInsnNode mi && GATE.equals(mi.owner))
                return true;
        return false;
    }
}
