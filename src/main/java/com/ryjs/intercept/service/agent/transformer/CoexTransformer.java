package com.ryjs.intercept.service.agent.transformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;

import com.ryjs.intercept.service.agent.CoexGate;
import com.ryjs.intercept.service.agent.ThreadGuard;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;


public class CoexTransformer implements ClassFileTransformer {

    private static final String LAYER_HANDLER = "cpw/mods/modlauncher/ModuleLayerHandler";
    private static final String LAUNCH_CONTEXT = "net/neoforged/fml/loading/LaunchContext";
    private static final String GATE = "com/ryjs/intercept/service/agent/CoexGate";

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {
        if (className == null)
            return null;
        if (!ThreadGuard.enter())
            return null;
        try {
            if (LAYER_HANDLER.equals(className))
                return patchBuildLayer(classfileBuffer);
            if (LAUNCH_CONTEXT.equals(className))
                return patchLaunchContextInit(classfileBuffer);
        } catch (Throwable t) {
            CoexGate.report("补丁 " + className + " 失败，放弃：" + t);
        } finally {
            ThreadGuard.leave();
        }
        return null;
    }


    private byte[] patchBuildLayer(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        reader.accept(cn, 0);
        FieldNode field = null;
        for (FieldNode fn : cn.fields)
            if ("completedLayers".equals(fn.name))
                field = fn;
        if (field == null) {
            CoexGate.report("  没有 completedLayers 字段，不插桩");
            return null;
        }
        MethodNode target = null;
        for (MethodNode mn : cn.methods) {
            if ("buildLayer".equals(mn.name) && mn.desc.startsWith("(Lcpw/mods/modlauncher/api/IModuleLayerManager$Layer;Ljava/util/function/BiFunction;")) {
                target = mn;
                break;
            }
        }
        if (target == null || target.instructions.size() == 0) {
            CoexGate.report("  没找到 buildLayer(Layer, BiFunction)，不硬猜签名");
            return null;
        }
        if (alreadyPatched(target))
            return null;
        InsnList prefix = new InsnList();
        prefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
        prefix.add(new FieldInsnNode(Opcodes.GETFIELD, cn.name, field.name, field.desc));
        prefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
        prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GATE, "purge", "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
        target.instructions.insert(prefix);
        return write(reader, cn, "buildLayer" + target.desc + " → CoexGate.purge", bytes);
    }


    private byte[] patchLaunchContextInit(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        reader.accept(cn, 0);
        MethodNode target = null;
        for (MethodNode mn : cn.methods)
            if ("<init>".equals(mn.name) && mn.desc.endsWith(")V") && mn.instructions.size() > 0)
                target = mn;
        if (target == null) {
            CoexGate.report("  " + cn.name + " 没有可插的 <init>，放弃");
            return null;
        }
        java.util.List<AbstractInsnNode> returns = new ArrayList<>();
        for (AbstractInsnNode insn = target.instructions.getFirst(); insn != null; insn = insn.getNext())
            if (insn instanceof InsnNode in && in.getOpcode() == Opcodes.RETURN)
                returns.add(insn);
        if (returns.isEmpty()) {
            CoexGate.report("  <init> 里没有 RETURN，放弃");
            return null;
        }
        if (alreadyPatched(target))
            return null;
        for (AbstractInsnNode ret : returns) {
            InsnList suffix = new InsnList();
            suffix.add(new VarInsnNode(Opcodes.ALOAD, 0));
            suffix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GATE, "stripLocated", "(Ljava/lang/Object;)V", false));
            target.instructions.insertBefore(ret, suffix);
        }
        return write(reader, cn, "LaunchContext.<init> 出口 ×" + returns.size() + " → CoexGate.stripLocated(this)", bytes);
    }

    private static boolean alreadyPatched(MethodNode mn) {
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext())
            if (insn instanceof MethodInsnNode mi && GATE.equals(mi.owner))
                return true;
        return false;
    }

    private byte[] write(ClassReader reader, ClassNode cn, String what, byte[] bytes) {
        ClassWriter cw = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        byte[] out = cw.toByteArray();
        CoexGate.report("✓ 已插桩 " + what + "（" + bytes.length + "B → " + out.length + "B）");
        return out;
    }
}
