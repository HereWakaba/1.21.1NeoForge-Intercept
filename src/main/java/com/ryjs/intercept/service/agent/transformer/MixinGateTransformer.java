package com.ryjs.intercept.service.agent.transformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import com.ryjs.intercept.service.agent.ArOrigin;
import com.ryjs.intercept.service.agent.ThreadGuard;
import com.ryjs.intercept.service.agent.TypeIndex;
import com.ryjs.intercept.service.agent.asm.ArClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.RETURN;


public class MixinGateTransformer implements ClassFileTransformer {

    static final String MIXINS = "org/spongepowered/asm/mixin/Mixins";
    static final String FML_REGISTRY = "net/neoforged/fml/loading/mixin/DeferredMixinConfigRegistration";
    static final String GATE = "com/ryjs/intercept/service/agent/MixinGate";

    private final TypeIndex index = new TypeIndex();

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {
        if (className == null || !ThreadGuard.enter())
            return null;
        try {
            if (MIXINS.equals(className))
                return patch(loader, classfileBuffer, "createConfiguration", null, 1);
            if (FML_REGISTRY.equals(className))
                return patch(loader, classfileBuffer, "addMixinConfig", "(Ljava/lang/String;Ljava/lang/String;)V", 3);
            return null;
        } catch (Throwable t) {
            ArOrigin.report("mixin 闸口补丁失败，放弃 " + className + "：" + t);
            return null;
        } finally {
            ThreadGuard.leave();
        }
    }


    private byte[] patch(ClassLoader loader, byte[] bytes, String method, String desc, int slots) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        reader.accept(cn, 0);
        MethodNode target = null;
        for (MethodNode mn : cn.methods) {
            if (!method.equals(mn.name) || !mn.desc.endsWith(")V") || mn.instructions == null || mn.instructions.size() == 0)
                continue;
            if (desc != null && !desc.equals(mn.desc))
                continue;
            target = mn;
            break;
        }
        if (target == null) {
            ArOrigin.report("  没找到 " + cn.name + "." + method + (desc == null ? "" : desc) + "，不硬猜签名");
            return null;
        }
        if (alreadyPatched(target)) {
            ArOrigin.report("  " + cn.name + "." + method + " 已经带闸口，跳过");
            return null;
        }
        boolean owner = slots == 3;
        InsnList prefix = new InsnList();
        prefix.add(new VarInsnNode(ALOAD, 0));
        if (slots > 1)
            prefix.add(new VarInsnNode(ALOAD, 1));
        if (owner)
            prefix.add(new LdcInsnNode(Type.getObjectType(cn.name)));
        String gateDesc = owner ? "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)Z" : "(Ljava/lang/String;)Z";
        LabelNode keep = new LabelNode();
        prefix.add(new MethodInsnNode(INVOKESTATIC, GATE, owner ? "dropByOwner" : "drop", gateDesc, false));
        prefix.add(new JumpInsnNode(IFEQ, keep));
        prefix.add(new InsnNode(RETURN));
        prefix.add(keep);
        target.instructions.insert(prefix);
        ArClassWriter cw = new ArClassWriter(reader, loader, index);
        cn.accept(cw);
        byte[] out = cw.toByteArray();
        ArOrigin.report("✓ 已插桩 " + cn.name.substring(cn.name.lastIndexOf('/') + 1) + "." + method + target.desc
                + " → MixinGate." + (owner ? "dropByOwner" : "drop") + "（" + bytes.length + "B → " + out.length + "B）");
        return out;
    }

    private static boolean alreadyPatched(MethodNode mn) {
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext())
            if (insn instanceof MethodInsnNode mi && GATE.equals(mi.owner))
                return true;
        return false;
    }
}
