package com.ryjs.intercept.service.agent.transformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import com.ryjs.intercept.service.agent.asm.ArClassWriter;
import com.ryjs.intercept.service.agent.asm.EmptyValue;
import com.ryjs.intercept.service.agent.ArOrigin;
import com.ryjs.intercept.service.agent.ThreadGuard;
import com.ryjs.intercept.service.agent.TypeIndex;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_BRIDGE;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_NATIVE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.NEWARRAY;
import static org.objectweb.asm.Opcodes.RETURN;


public class CoremodTransformer implements ClassFileTransformer {


    static final String[] SERVICE_TYPES = {
            "cpw/mods/modlauncher/api/ITransformationService",
            "cpw/mods/modlauncher/serviceapi/ILaunchPluginService",
            "net/neoforged/neoforgespi/locating/IModFileCandidateLocator",
            "net/neoforged/neoforgespi/locating/IModFileReader",
            "net/neoforged/neoforgespi/locating/IDependencyLocator",
            "net/neoforged/neoforgespi/earlywindow/GraphicsBootstrapper",
            "net/neoforged/neoforgespi/earlywindow/ImmediateWindowProvider",
            "net/neoforged/neoforgespi/coremod/ICoreMod"};


    private static final Set<String> OBJECT_METHODS = Set.of(
            "equals(Ljava/lang/Object;)Z", "hashCode()I", "toString()Ljava/lang/String;",
            "clone()Ljava/lang/Object;", "finalize()V");

    private final TypeIndex index = new TypeIndex();


    private final Set<String> gutted = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile Set<String> sink;


    public synchronized void releaseInto(Set<String> bootstrapReleased) {
        this.sink = bootstrapReleased;
        bootstrapReleased.addAll(gutted);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {
        if (className == null || !ThreadGuard.enter())
            return null;
        try {
            if (!ArOrigin.isInstrumentable(className, loader, protectionDomain, classBeingRedefined))
                return null;
            if (!isCoremodService(loader, className))
                return null;
            return patch(loader, className, ArOrigin.sourceUrl(className, loader, protectionDomain, classBeingRedefined), classfileBuffer);
        } catch (Throwable t) {
            ArOrigin.report("coremod 掏空失败，放弃 " + className + ": " + t);
            return null;
        } finally {
            ThreadGuard.leave();
        }
    }


    public boolean isCoremodService(ClassLoader loader, String className) {
        for (String service : SERVICE_TYPES) {

            if (!service.equals(className) && index.isSuperType(service, className, loader))
                return true;
        }
        return false;
    }

    private byte[] patch(ClassLoader loader, String className, String source, byte[] buffer) {
        ClassReader cr = new ClassReader(buffer);
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.EXPAND_FRAMES);
        if ((cn.access & ACC_INTERFACE) != 0)
            return null;

        index.put(cn, loader);
        int touched = 0;
        for (MethodNode mn : cn.methods) {
            if ((mn.access & (ACC_ABSTRACT | ACC_NATIVE | ACC_BRIDGE)) != 0)
                continue;
            if (mn.instructions == null || mn.instructions.size() == 0)
                continue;
            if (mn.name.equals("<init>")) {
                touched += neutralizeCtor(cn, mn) ? 1 : 0;
                continue;
            }
            if (OBJECT_METHODS.contains(mn.name + mn.desc))
                continue;
            if (alreadyEmpty(mn) || pureRelay(mn))
                continue;

            if (mn.name.equals("<clinit>") || !relayToDefault(loader, cn, mn)) {
                emptyToDefault(mn);
                touched++;
            }
        }
        if (touched == 0)
            return null;
        ArClassWriter cw = new ArClassWriter(cr, loader, index);
        cn.accept(cw);
        byte[] out = cw.toByteArray();


        if (source != null) {
            gutted.add(source);
            Set<String> s = sink;
            if (s != null)
                s.add(source);
        }
        ArOrigin.report("✓ coremod 服务已掏空 " + className + "（" + touched + " 个方法） " + buffer.length + "B → " + out.length + "B");
        return out;
    }


    private boolean relayToDefault(ClassLoader loader, ClassNode cn, MethodNode mn) {

        if ((mn.access & (ACC_STATIC | ACC_PRIVATE)) != 0)
            return false;
        TypeIndex.IfaceDecl decl = index.ifaceDecl(loader, cn.name, mn.name, mn.desc);
        if (decl == null || !decl.concrete() || !decl.callable() || ArOrigin.isInstrumentable(decl.owner(), loader, null))
            return false;
        TypeIndex.Struct owner = index.of(decl.owner(), loader);

        if (owner != null && (owner.access() & ACC_PUBLIC) == 0 && !inSamePackage(owner.name(), cn.name))
            return false;
        emitSuper(mn, decl.owner());
        return true;
    }

    private static boolean inSamePackage(String a, String b) {
        return a.substring(0, a.lastIndexOf('/') + 1).equals(b.substring(0, b.lastIndexOf('/') + 1));
    }

    private static void emitSuper(MethodNode mn, String owner) {
        Type ret = Type.getReturnType(mn.desc);
        InsnList body = new InsnList();
        body.add(new LabelNode());
        body.add(new VarInsnNode(ALOAD, 0));
        int slot = 1;
        for (Type arg : Type.getArgumentTypes(mn.desc)) {
            body.add(new VarInsnNode(arg.getOpcode(ILOAD), slot));
            slot += arg.getSize();
        }
        body.add(new MethodInsnNode(INVOKESPECIAL, owner, mn.name, mn.desc, true));


        body.add(new InsnNode(ret.getSort() == Type.VOID ? RETURN : ret.getOpcode(IRETURN)));
        replace(mn, body);
    }

    private static void emptyToDefault(MethodNode mn) {
        InsnList body = new InsnList();
        body.add(new LabelNode());
        appendReturn(body, mn);
        replace(mn, body);
    }


    private static boolean neutralizeCtor(ClassNode cn, MethodNode mn) {
        String self = cn.name;
        String parent = cn.superName == null ? "java/lang/Object" : cn.superName;
        InsnList prefix = new InsnList();
        AbstractInsnNode afterCall = null;
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == -1)
                continue;
            AbstractInsnNode copy = copy(insn);
            if (copy == null)
                return false;
            prefix.add(copy);
            if (insn instanceof MethodInsnNode mi && mi.name.equals("<init>")
                    && mi.getOpcode() == INVOKESPECIAL && (mi.owner.equals(self) || mi.owner.equals(parent))) {
                afterCall = insn;
                break;
            }
        }
        if (afterCall == null || prefix.size() < 2 || alreadyTruncated(afterCall))
            return false;
        prefix.add(new LabelNode());
        prefix.add(new InsnNode(RETURN));
        replace(mn, prefix);
        return true;
    }


    private static AbstractInsnNode copy(AbstractInsnNode insn) {
        if (insn instanceof InsnNode in)
            return new InsnNode(in.getOpcode());
        if (insn instanceof VarInsnNode v)
            return new VarInsnNode(v.getOpcode(), v.var);
        if (insn instanceof IntInsnNode i)
            return new IntInsnNode(i.getOpcode(), i.operand);
        if (insn instanceof LdcInsnNode l)
            return new LdcInsnNode(l.cst);
        if (insn instanceof TypeInsnNode t)
            return new TypeInsnNode(t.getOpcode(), t.desc);
        if (insn instanceof FieldInsnNode f)
            return new FieldInsnNode(f.getOpcode(), f.owner, f.name, f.desc);
        if (insn instanceof MethodInsnNode m)
            return new MethodInsnNode(m.getOpcode(), m.owner, m.name, m.desc, m.itf);
        if (insn instanceof InvokeDynamicInsnNode d)
            return new InvokeDynamicInsnNode(d.name, d.desc, d.bsm, d.bsmArgs);
        if (insn instanceof MultiANewArrayInsnNode a)
            return new MultiANewArrayInsnNode(a.desc, a.dims);
        return null;
    }


    private static boolean alreadyTruncated(AbstractInsnNode superCall) {
        boolean sawReturn = false;
        for (AbstractInsnNode insn = superCall.getNext(); insn != null; insn = insn.getNext()) {
            int op = insn.getOpcode();
            if (op == -1)
                continue;
            if (sawReturn || op != RETURN)
                return false;
            sawReturn = true;
        }
        return sawReturn;
    }


    private static boolean pureRelay(MethodNode mn) {
        int real = 0;
        int specials = 0;
        AbstractInsnNode first = null;
        AbstractInsnNode last = null;
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int op = insn.getOpcode();
            if (op == -1)
                continue;
            if (real++ == 0)
                first = insn;
            last = insn;
            if (op == INVOKESPECIAL)
                specials++;
            else if (!(insn instanceof VarInsnNode) && !isReturn(op))
                return false;
        }
        return real >= 3 && real <= 4 && specials == 1 && first.getOpcode() == ALOAD && ((VarInsnNode) first).var == 0
                && last instanceof InsnNode && isReturn(last.getOpcode());
    }


    private static boolean alreadyEmpty(MethodNode mn) {
        InsnList want = new InsnList();
        appendReturn(want, mn);
        AbstractInsnNode a = real(mn.instructions.getFirst());
        AbstractInsnNode b = real(want.getFirst());
        while (a != null && b != null) {
            if (!sameNode(a, b))
                return false;
            a = real(a.getNext());
            b = real(b.getNext());
        }
        return a == null && b == null;
    }

    private static AbstractInsnNode real(AbstractInsnNode insn) {
        while (insn != null && insn.getOpcode() == -1)
            insn = insn.getNext();
        return insn;
    }

    private static boolean sameNode(AbstractInsnNode a, AbstractInsnNode b) {
        if (a.getOpcode() != b.getOpcode() || a.getType() != b.getType())
            return false;
        if (a instanceof IntInsnNode x)
            return x.operand == ((IntInsnNode) b).operand;
        if (a instanceof TypeInsnNode x)
            return x.desc.equals(((TypeInsnNode) b).desc);
        if (a instanceof LdcInsnNode x)
            return java.util.Objects.equals(x.cst, ((LdcInsnNode) b).cst);
        if (a instanceof MethodInsnNode x) {
            MethodInsnNode y = (MethodInsnNode) b;
            return x.owner.equals(y.owner) && x.name.equals(y.name) && x.desc.equals(y.desc) && x.itf == y.itf;
        }
        return true;
    }

    private static boolean isReturn(int op) {
        return op == RETURN || op == IRETURN || op == FRETURN || op == LRETURN || op == DRETURN || op == ARETURN;
    }

    private static void replace(MethodNode mn, InsnList body) {
        mn.instructions.clear();
        mn.localVariables = null;
        mn.tryCatchBlocks = new ArrayList<>();
        mn.visibleLocalVariableAnnotations = null;
        mn.invisibleLocalVariableAnnotations = null;
        mn.instructions.insert(body);
    }



    private static String enumSetElement(String signature) {
        if (signature == null)
            return null;
        String mark = "Ljava/util/EnumSet<L";
        int at = signature.indexOf(mark);
        if (at < 0)
            return null;
        int from = at + mark.length();
        int end = signature.indexOf(';', from);
        return end < 0 ? null : signature.substring(from, end);
    }


    private static void emitEmptyArray(InsnList body, Type ret) {
        body.add(new InsnNode(ICONST_0));
        Type element = ret.getElementType();
        int dims = ret.getDimensions();
        if (dims == 1 && element.getSort() != Type.OBJECT) {
            body.add(new IntInsnNode(NEWARRAY, atypeOf(element.getSort())));
        } else if (dims == 1) {
            body.add(new TypeInsnNode(ANEWARRAY, element.getInternalName()));
        } else {
            body.add(new TypeInsnNode(ANEWARRAY, "[".repeat(dims - 1) + element.getDescriptor()));
        }
        body.add(new InsnNode(ARETURN));
    }

    private static int atypeOf(int sort) {
        switch (sort) {
            case Type.BOOLEAN:
                return Opcodes.T_BOOLEAN;
            case Type.CHAR:
                return Opcodes.T_CHAR;
            case Type.FLOAT:
                return Opcodes.T_FLOAT;
            case Type.DOUBLE:
                return Opcodes.T_DOUBLE;
            case Type.BYTE:
                return Opcodes.T_BYTE;
            case Type.SHORT:
                return Opcodes.T_SHORT;
            case Type.INT:
                return Opcodes.T_INT;
            case Type.LONG:
                return Opcodes.T_LONG;
            default:
                return -1;
        }
    }


    private static void appendReturn(InsnList body, MethodNode mn) {
        EmptyValue.emit(body, Type.getReturnType(mn.desc), mn.signature);
    }

}
