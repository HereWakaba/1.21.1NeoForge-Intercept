package com.ryjs.intercept.service.agent.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.NEWARRAY;
import static org.objectweb.asm.Opcodes.RETURN;


public final class EmptyValue {

    private EmptyValue() {}


    private record Factory(String owner, String name, String desc, boolean itf) {}


    private static final java.util.Map<String, Factory> FACTORY = java.util.Map.ofEntries(
            java.util.Map.entry("java/util/List", new Factory("java/util/List", "of", "()Ljava/util/List;", true)),
            java.util.Map.entry("java/util/Collection", new Factory("java/util/List", "of", "()Ljava/util/List;", true)),
            java.util.Map.entry("java/lang/Iterable", new Factory("java/util/List", "of", "()Ljava/util/List;", true)),
            java.util.Map.entry("java/util/Set", new Factory("java/util/Set", "of", "()Ljava/util/Set;", true)),
            java.util.Map.entry("java/util/Map", new Factory("java/util/Map", "of", "()Ljava/util/Map;", true)),
            java.util.Map.entry("java/util/Iterator", new Factory("java/util/Collections", "emptyIterator", "()Ljava/util/Iterator;", false)),
            java.util.Map.entry("java/util/ListIterator", new Factory("java/util/Collections", "emptyListIterator", "()Ljava/util/ListIterator;", false)),
            java.util.Map.entry("java/util/stream/Stream", new Factory("java/util/stream/Stream", "empty", "()Ljava/util/stream/Stream;", true)),
            java.util.Map.entry("java/util/stream/BaseStream", new Factory("java/util/stream/Stream", "empty", "()Ljava/util/stream/Stream;", true)),
            java.util.Map.entry("java/util/Optional", new Factory("java/util/Optional", "empty", "()Ljava/util/Optional;", false)),
            java.util.Map.entry("java/util/OptionalInt", new Factory("java/util/OptionalInt", "empty", "()Ljava/util/OptionalInt;", false)),
            java.util.Map.entry("java/util/OptionalLong", new Factory("java/util/OptionalLong", "empty", "()Ljava/util/OptionalLong;", false)),
            java.util.Map.entry("java/util/OptionalDouble", new Factory("java/util/OptionalDouble", "empty", "()Ljava/util/OptionalDouble;", false)));


    public static final java.util.Map<String, String> EMPTY_FIELD = new java.util.concurrent.ConcurrentHashMap<>(
            java.util.Map.of("net/minecraft/world/item/ItemStack", "EMPTY"));


    public static boolean emptyable(Type ret, String signature) {
        switch (ret.getSort()) {
            case Type.VOID:
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
            case Type.LONG:
            case Type.FLOAT:
            case Type.DOUBLE:
            case Type.ARRAY:
                return true;
            default:
                break;
        }
        String internal = ret.getInternalName();
        return internal.equals("java/lang/String") || FACTORY.containsKey(internal) || EMPTY_FIELD.containsKey(internal)
                || enumSetElement(signature) != null;
    }


    public static void emit(InsnList body, Type ret, String signature) {
        switch (ret.getSort()) {
            case Type.VOID:
                body.add(new InsnNode(RETURN));
                return;
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                body.add(new InsnNode(ICONST_0));
                body.add(new InsnNode(IRETURN));
                return;
            case Type.FLOAT:
                body.add(new InsnNode(FCONST_0));
                body.add(new InsnNode(FRETURN));
                return;
            case Type.LONG:
                body.add(new InsnNode(LCONST_0));
                body.add(new InsnNode(LRETURN));
                return;
            case Type.DOUBLE:
                body.add(new InsnNode(DCONST_0));
                body.add(new InsnNode(DRETURN));
                return;
            case Type.ARRAY:
                emitEmptyArray(body, ret);
                return;
            default:
                break;
        }
        String internal = ret.getInternalName();
        String enumOf = enumSetElement(signature);
        if (enumOf != null) {
            body.add(new LdcInsnNode(Type.getObjectType(enumOf)));
            body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/EnumSet", "noneOf", "(Ljava/lang/Class;)Ljava/util/EnumSet;", false));
            body.add(new InsnNode(ARETURN));
            return;
        }
        if (internal.equals("java/lang/String")) {
            body.add(new LdcInsnNode(""));
        } else {
            String field = EMPTY_FIELD.get(internal);
            Factory factory = FACTORY.get(internal);
            if (field != null)
                body.add(new org.objectweb.asm.tree.FieldInsnNode(GETSTATIC, internal, field, "L" + internal + ";"));
            else if (factory != null)
                body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, factory.owner(), factory.name(), factory.desc(), factory.itf()));
            else
                body.add(new InsnNode(ACONST_NULL));
        }
        body.add(new InsnNode(ARETURN));
    }


    public static String enumSetElement(String signature) {
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
        if (dims == 1 && element.getSort() != Type.OBJECT)
            body.add(new IntInsnNode(NEWARRAY, atypeOf(element.getSort())));
        else if (dims == 1)
            body.add(new TypeInsnNode(ANEWARRAY, element.getInternalName()));
        else
            body.add(new TypeInsnNode(ANEWARRAY, "[".repeat(dims - 1) + element.getDescriptor()));
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
}
