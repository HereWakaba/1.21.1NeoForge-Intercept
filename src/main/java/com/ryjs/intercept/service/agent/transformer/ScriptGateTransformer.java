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
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;


public class ScriptGateTransformer implements ClassFileTransformer {

    static final String PARSER = "net/neoforged/fml/loading/moddiscovery/ModFileParser";
    static final String GATE = "com/ryjs/intercept/service/agent/ScriptGate";

    private final TypeIndex index = new TypeIndex();

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {
        if (className == null || !PARSER.equals(className) || !ThreadGuard.enter())
            return null;
        try {
            return patch(loader, classfileBuffer);
        } catch (Throwable t) {
            ArOrigin.report("JS coremod 闸口补丁失败，放弃：" + t);
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
            if ("getCoreMods".equals(mn.name) && mn.desc.endsWith(")Ljava/util/List;") && mn.instructions != null && mn.instructions.size() > 0) {
                target = mn;
                break;
            }
        }
        if (target == null) {
            ArOrigin.report("  没找到 ModFileParser.getCoreMods(ModFile)Ljava/util/List;，不硬猜签名");
            return null;
        }
        if (alreadyPatched(target)) {
            ArOrigin.report("  getCoreMods 已经带闸口，跳过");
            return null;
        }
        LabelNode keep = new LabelNode();
        InsnList prefix = new InsnList();
        prefix.add(new VarInsnNode(ALOAD, 0));
        prefix.add(new MethodInsnNode(INVOKESTATIC, GATE, "drop", "(Ljava/lang/Object;)Z", false));
        prefix.add(new JumpInsnNode(IFEQ, keep));
        prefix.add(new MethodInsnNode(INVOKESTATIC, "java/util/Collections", "emptyList", "()Ljava/util/List;", false));
        prefix.add(new InsnNode(ARETURN));
        prefix.add(keep);
        target.instructions.insert(prefix);
        ArClassWriter cw = new ArClassWriter(reader, loader, index);
        cn.accept(cw);
        byte[] out = cw.toByteArray();
        ArOrigin.report("✓ 已插桩 ModFileParser.getCoreMods" + target.desc + " → ScriptGate.drop（" + bytes.length + "B → " + out.length + "B）");
        return out;
    }

    private static boolean alreadyPatched(MethodNode mn) {
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext())
            if (insn instanceof MethodInsnNode mi && GATE.equals(mi.owner))
                return true;
        return false;
    }
}
