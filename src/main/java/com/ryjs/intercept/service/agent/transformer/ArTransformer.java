package com.ryjs.intercept.service.agent.transformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ryjs.intercept.service.agent.asm.ArClassWriter;
import com.ryjs.intercept.service.agent.asm.EmptyValue;
import com.ryjs.intercept.service.agent.ArOrigin;
import com.ryjs.intercept.service.agent.ThreadGuard;
import com.ryjs.intercept.service.agent.TypeIndex;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_ENUM;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_NATIVE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.RETURN;


public class ArTransformer implements ClassFileTransformer {

    public static final String GATE_OWNER = "com/ryjs/intercept/util/AR";
    public static final String GATE_METHOD = "shouldAR";
    public static final String GATE_DESC = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z";
    static final String CAUGHT_DESC = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)Ljava/lang/Throwable;";

    private static final int MODE_DEFAULT = 0;
    private static final int MODE_SUPER = 1;
    private static final int MODE_SKIP = 2;


    private record Decision(int mode, String owner, String ownerDesc, boolean itf) {

        static final Decision DEFAULT = new Decision(MODE_DEFAULT, null, null, false);
        static final Decision SKIP = new Decision(MODE_SKIP, null, null, false);

        static Decision superTo(String owner, String ownerDesc) {
            return new Decision(MODE_SUPER, owner, ownerDesc, false);
        }

        static Decision defaultOf(String owner, String ownerDesc) {
            return new Decision(MODE_SUPER, owner, ownerDesc, true);
        }

        boolean isSuper() {
            return mode == MODE_SUPER;
        }

        boolean isSkip() {
            return mode == MODE_SKIP;
        }

        boolean isDefault() {
            return mode == MODE_DEFAULT;
        }
    }


    static final String APPEARANCE_TAG = "外观层（继承自渲染/模型锚点）";
    static final String PROTOCOL_TAG = "协议层（StreamCodec：掏一半就是线格式错位）";

    public static final Set<String> APPEARANCE = ConcurrentHashMap.newKeySet();

    static {
        APPEARANCE.add("net/minecraft/client/renderer/entity/EntityRenderer");
        APPEARANCE.add("net/minecraft/client/model/Model");
        APPEARANCE.add("net/minecraft/client/model/HierarchicalModel");
        APPEARANCE.add("net/minecraft/client/renderer/entity/model/EntityModel");
        APPEARANCE.add("net/minecraft/client/renderer/blockentity/BlockEntityRenderer");
        APPEARANCE.add("net/minecraft/client/resources/model/BakedModel");
        APPEARANCE.add("net/minecraft/client/renderer/block/model/ItemOverrides");
        APPEARANCE.add("net/minecraft/client/renderer/texture/AbstractTexture");
        APPEARANCE.add("net/minecraft/client/renderer/texture/TextureAtlas");
        APPEARANCE.add("net/minecraft/client/renderer/texture/SpriteContents");
        APPEARANCE.add("net/neoforged/neoforge/client/model/IModelBuilder");
        APPEARANCE.add("net/neoforged/neoforge/client/model/IQuadTransformer");
        APPEARANCE.add("net/neoforged/neoforge/client/model/IDynamicBakedModel");
        APPEARANCE.add("net/neoforged/neoforge/client/model/geometry/IUnbakedGeometry");
        APPEARANCE.add("net/neoforged/neoforge/client/model/geometry/IGeometryLoader");
        APPEARANCE.add("net/neoforged/neoforge/client/model/geometry/IGeometryBakingContext");
        APPEARANCE.add("net/neoforged/neoforge/client/model/lighting/QuadLighter");
    }


    public static final Set<String> PROTOCOL = ConcurrentHashMap.newKeySet();

    static {
        PROTOCOL.add("net/minecraft/network/codec/StreamCodec");
    }

    private final TypeIndex index = new TypeIndex();
    private final Map<String, Decision> decisions = new ConcurrentHashMap<>();
    private final Map<String, String> handsOff = new ConcurrentHashMap<>();

    final Set<String> gatedOnce = ConcurrentHashMap.newKeySet();


    public Set<String> gatedOnce() {
        return gatedOnce;
    }

    public static String key(ClassLoader loader, String className) {
        return ArOrigin.id(loader) + "#" + className;
    }


    public static boolean hasGate(byte[] classFile) {
        if (classFile == null)
            return false;
        return new String(classFile, java.nio.charset.StandardCharsets.ISO_8859_1).indexOf(GATE_OWNER) >= 0;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {
        if (className == null)
            return null;
        if (!ThreadGuard.enter())
            return null;
        try {
            if (!ArOrigin.isInstrumentable(className, loader, protectionDomain, classBeingRedefined))
                return null;


            if (!ArOrigin.gateVisible(loader))
                return null;
            return patch(loader, className, classfileBuffer);
        } catch (Throwable t) {
            ArOrigin.report("skip " + className + ": " + t);
            return null;
        } finally {
            ThreadGuard.leave();
        }
    }

    private byte[] patch(ClassLoader loader, String className, byte[] buffer) {
        ClassReader cr = new ClassReader(buffer);
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.EXPAND_FRAMES);
        if (skipClass(cn))
            return null;
        int gated = 0;
        List<String> natives = new ArrayList<>();
        for (MethodNode mn : cn.methods) {
            if (neutralizeNative(mn, natives))
                continue;
            if (!gatable(mn) || alreadyGated(mn))
                continue;
            String hands = handsOff(loader, cn);
            Decision d = decide(loader, cn, mn, hands);
            if (d.isSkip())
                continue;
            insert(cn, mn, d);
            gated++;
        }
        if (!natives.isEmpty()) {
            gated += natives.size();
            ArOrigin.report("掏空 native " + cn.name + "（shouldAR 管不到它们，见 neutralizeNative）: "
                    + (natives.size() <= 8 ? natives : natives.subList(0, 8) + "…共 " + natives.size() + " 条"));
        }
        if (gated == 0)
            return null;
        gatedOnce.add(key(loader, className));
        ArClassWriter cw = new ArClassWriter(cr, loader, index);
        cn.accept(cw);
        return cw.toByteArray();
    }


    private static boolean neutralizeNative(MethodNode mn, List<String> killed) {
        if ((mn.access & ACC_NATIVE) == 0 || mn.name.equals("<init>") || mn.name.equals("<clinit>"))
            return false;
        InsnList body = new InsnList();
        emitDefault(body, mn);
        mn.instructions = body;
        mn.access &= ~ACC_NATIVE;
        killed.add(mn.name + mn.desc);
        return true;
    }


    private static boolean skipClass(ClassNode cn) {
        if ((cn.access & (ACC_INTERFACE | ACC_ENUM)) != 0)
            return true;

        return "java/lang/Enum".equals(cn.superName);
    }

    private static boolean gatable(MethodNode mn) {
        if (mn.instructions == null || mn.instructions.size() == 0)
            return false;
        if (mn.name.equals("<init>") || mn.name.equals("<clinit>"))
            return false;
        return (mn.access & (ACC_ABSTRACT | ACC_NATIVE)) == 0;
    }

    private static boolean alreadyGated(MethodNode mn) {
        for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode mi && mi.owner.equals(GATE_OWNER) && mi.name.equals(GATE_METHOD))
                return true;
        }
        return false;
    }

    private Decision decide(ClassLoader loader, ClassNode cn, MethodNode mn, String handsOff) {
        Decision d = pick(loader, cn, mn);

        if (handsOff != null)
            return d.isSuper() && canHandoff(mn, d) ? d : Decision.SKIP;


        return d.isDefault() && !EmptyValue.emptyable(Type.getReturnType(mn.desc), mn.signature) ? Decision.SKIP : d;
    }

    private Decision pick(ClassLoader loader, ClassNode cn, MethodNode mn) {
        if ((mn.access & (ACC_STATIC | ACC_PRIVATE)) != 0 || cn.superName == null)
            return Decision.DEFAULT;
        return chain(loader, cn.name, cn.superName, mn.name, mn.desc);
    }


    private String handsOff(ClassLoader loader, ClassNode cn) {
        String key = key(loader, cn.name);
        String cached = handsOff.get(key);
        if (cached != null)
            return cached.isEmpty() ? null : cached;
        List<String> roots = new ArrayList<>();
        if (cn.superName != null)
            roots.add(cn.superName);
        roots.addAll(cn.interfaces);
        String why = index.reachesAny(loader, roots, APPEARANCE) ? APPEARANCE_TAG
                : index.reachesAny(loader, roots, PROTOCOL) ? PROTOCOL_TAG : "";
        handsOff.put(key, why);
        if (!why.isEmpty())
            ArOrigin.report(why + "：" + cn.name + " ⇒ 能 super 的才 super，其余不动");
        return why.isEmpty() ? null : why;
    }

    private Decision chain(ClassLoader loader, String selfName, String startSuper, String name, String desc) {
        String key = ArOrigin.id(loader) + "#" + selfName + "#" + name + desc;
        Decision cached = decisions.get(key);
        if (cached != null)
            return cached;
        Decision out = compute(loader, selfName, startSuper, name, desc);
        decisions.put(key, out);
        return out;
    }

    private Decision compute(ClassLoader loader, String selfName, String startSuper, String name, String desc) {
        Integer[] accessOut = new Integer[1];
        boolean abstractSuperDecl = false;
        for (TypeIndex.Struct cur = index.of(startSuper, loader); cur != null; cur = index.superOf(cur, loader)) {
            String declared = index.declaredDesc(cur, name, desc, accessOut);
            if (declared == null)
                continue;
            int access = accessOut[0];


            if ((access & ACC_PRIVATE) != 0 || !handoffAccessible(cur, access, selfName))
                return Decision.DEFAULT;
            if ((access & ACC_ABSTRACT) != 0) {
                abstractSuperDecl = true;
                break;
            }
            return relay(loader, cur.name(), declared, name, desc);
        }
        TypeIndex.IfaceDecl iface = index.ifaceDecl(loader, selfName, name, desc);
        if (iface != null) {


            if (!iface.concrete() || !iface.callable())
                return Decision.DEFAULT;

            return ArOrigin.isInstrumentable(iface.owner(), loader, null) ? Decision.DEFAULT : Decision.defaultOf(iface.owner(), desc);
        }

        return Decision.DEFAULT;
    }



    private Decision relay(ClassLoader loader, String owner, String ownerDesc, String name, String desc) {
        if (!ArOrigin.isInstrumentable(owner, loader, null))
            return Decision.superTo(owner, ownerDesc);
        TypeIndex.Struct os = index.of(owner, loader);
        if (os == null)
            return Decision.DEFAULT;
        return chain(loader, owner, os.superName(), name, ownerDesc).isSuper() ? Decision.superTo(owner, ownerDesc) : Decision.DEFAULT;
    }

    private static boolean handoffAccessible(TypeIndex.Struct owner, int methodAccess, String selfName) {
        if (samePackage(owner.name(), selfName))
            return true;
        return (methodAccess & (ACC_PUBLIC | ACC_PROTECTED)) != 0 && (owner.access() & ACC_PUBLIC) != 0;
    }

    private static boolean samePackage(String a, String b) {
        return a.substring(0, a.lastIndexOf('/') + 1).equals(b.substring(0, b.lastIndexOf('/') + 1));
    }


    private static void insert(ClassNode cn, MethodNode mn, Decision d) {
        AbstractInsnNode at = mn.instructions.getFirst();
        while (at != null && (at instanceof LabelNode || at instanceof FrameNode || at instanceof LineNumberNode))
            at = at.getNext();
        if (at == null)
            return;
        LabelNode gate = new LabelNode();
        LabelNode attempt = new LabelNode();
        LabelNode attemptEnd = new LabelNode();
        LabelNode gateless = new LabelNode();
        LabelNode rethrow = new LabelNode();
        LabelNode body = new LabelNode();
        InsnList prefix = new InsnList();
        prefix.add(gate);
        prefix.add(new LdcInsnNode(cn.name));
        prefix.add(new LdcInsnNode(mn.name));
        prefix.add(new LdcInsnNode(mn.desc));
        prefix.add(new MethodInsnNode(INVOKESTATIC, GATE_OWNER, GATE_METHOD, GATE_DESC, false));
        prefix.add(new JumpInsnNode(IFEQ, body));
        prefix.add(attempt);
        if (d.isSuper() && canHandoff(mn, d))
            emitSuper(prefix, mn, d);
        else
            emitDefault(prefix, mn);
        prefix.add(attemptEnd);
        prefix.add(gateless);
        prefix.add(new InsnNode(POP));
        prefix.add(new JumpInsnNode(GOTO, body));
        prefix.add(rethrow);
        int slot = throwableSlot(mn);
        prefix.add(new VarInsnNode(ASTORE, slot));
        prefix.add(new LdcInsnNode(cn.name));
        prefix.add(new LdcInsnNode(mn.name));
        prefix.add(new LdcInsnNode(mn.desc));
        prefix.add(new VarInsnNode(ALOAD, slot));
        prefix.add(new MethodInsnNode(INVOKESTATIC, GATE_OWNER, "caught", CAUGHT_DESC, false));
        prefix.add(new InsnNode(ATHROW));
        prefix.add(body);
        mn.instructions.insertBefore(at, prefix);
        mn.tryCatchBlocks.add(new TryCatchBlockNode(gate, attempt, gateless, null));
        mn.tryCatchBlocks.add(new TryCatchBlockNode(attempt, attemptEnd, rethrow, null));
    }


    private static int throwableSlot(MethodNode mn) {
        int slot = (mn.access & ACC_STATIC) == 0 ? 1 : 0;
        for (Type arg : Type.getArgumentTypes(mn.desc))
            slot += arg.getSize();
        return slot;
    }

    private static boolean canHandoff(MethodNode mn, Decision d) {
        if (mn.desc.equals(d.ownerDesc()))
            return true;
        Type ours = Type.getReturnType(mn.desc);
        Type theirs = Type.getReturnType(d.ownerDesc());
        boolean reference = (ours.getSort() == Type.OBJECT || ours.getSort() == Type.ARRAY)
                && (theirs.getSort() == Type.OBJECT || theirs.getSort() == Type.ARRAY);
        return reference && args(mn.desc).equals(args(d.ownerDesc()));
    }

    private static String args(String desc) {
        return desc.substring(0, desc.indexOf(')') + 1);
    }

    private static void emitSuper(InsnList gate, MethodNode mn, Decision d) {
        Type ours = Type.getReturnType(mn.desc);
        gate.add(new VarInsnNode(ALOAD, 0));
        int slot = 1;
        for (Type arg : Type.getArgumentTypes(mn.desc)) {
            gate.add(new VarInsnNode(arg.getOpcode(ILOAD), slot));
            slot += arg.getSize();
        }
        gate.add(new MethodInsnNode(INVOKESPECIAL, d.owner(), mn.name, d.ownerDesc(), d.itf()));
        if (ours.getSort() == Type.VOID) {
            gate.add(new InsnNode(RETURN));
            return;
        }
        if (!mn.desc.equals(d.ownerDesc()))
            gate.add(new TypeInsnNode(CHECKCAST, ours.getInternalName()));
        gate.add(new InsnNode(ours.getOpcode(IRETURN)));
    }


    private static void emitDefault(InsnList gate, MethodNode mn) {
        EmptyValue.emit(gate, Type.getReturnType(mn.desc), mn.signature);
    }


    private static Decision guttable(MethodNode mn) {
        return EmptyValue.emptyable(Type.getReturnType(mn.desc), mn.signature) ? Decision.DEFAULT : Decision.SKIP;
    }
}
