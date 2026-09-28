package com.ryjs.intercept.service.agent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;


public final class TypeIndex {

    public record Struct(String name, int access, String superName, List<String> interfaces, Map<String, Integer> methods) {

        boolean isInterface() {
            return (access & Opcodes.ACC_INTERFACE) != 0;
        }

        Integer accessOf(String name, String desc) {
            return methods.get(name + ":" + desc);
        }
    }

    private static final Struct ABSENT = new Struct("", 0, null, List.of(), Map.of());
    private final Map<String, Struct> structs = new ConcurrentHashMap<>();
    private final Map<String, byte[]> resourceCache = new ConcurrentHashMap<>();

    public Struct of(String name, ClassLoader loader) {
        if (name == null || name.startsWith("["))
            return null;
        String key = ArOrigin.id(loader) + "#" + name;
        Struct s = structs.get(key);
        if (s != null)
            return s == ABSENT ? null : s;
        s = read(name, loader);
        structs.put(key, s == null ? ABSENT : s);
        return s;
    }

    public Struct superOf(Struct self, ClassLoader loader) {
        return self == null ? null : of(self.superName(), loader);
    }


    public void put(ClassNode cn, ClassLoader loader) {
        Map<String, Integer> methods = new HashMap<>();
        for (MethodNode mn : cn.methods)
            methods.put(mn.name + ":" + mn.desc, mn.access);
        structs.put(ArOrigin.id(loader) + "#" + cn.name,
                new Struct(cn.name, cn.access, cn.superName, List.copyOf(cn.interfaces), Map.copyOf(methods)));
    }


    public String declaredDesc(Struct cur, String name, String desc, Integer[] accessOut) {
        Integer exact = cur.accessOf(name, desc);
        if (exact != null) {
            accessOut[0] = exact;
            return desc;
        }
        String args = desc.substring(0, desc.indexOf(')') + 1);
        String prefix = name + ":" + args;
        for (Map.Entry<String, Integer> e : cur.methods().entrySet()) {
            String k = e.getKey();
            if (!k.startsWith(prefix) || k.length() == prefix.length())
                continue;
            String other = k.substring(name.length() + 1);
            if (returnsReference(other) && returnsReference(desc)) {
                accessOut[0] = e.getValue();
                return other;
            }
        }
        return null;
    }

    private static boolean returnsReference(String desc) {
        int i = desc.indexOf(')');
        return i >= 0 && i + 1 < desc.length() && (desc.charAt(i + 1) == 'L' || desc.charAt(i + 1) == '[');
    }


    public record IfaceDecl(String owner, boolean concrete, boolean callable) {}




    public IfaceDecl ifaceDecl(ClassLoader loader, String selfName, String name, String desc) {
        Struct self = of(selfName, loader);
        if (self == null)
            return null;
        for (String n : self.interfaces()) {
            Struct iface = of(n, loader);
            Integer access = iface == null ? null : iface.accessOf(name, desc);
            if (access != null)
                return new IfaceDecl(iface.name(), (access & Opcodes.ACC_ABSTRACT) == 0, true);
        }
        Set<String> seen = new HashSet<>();
        List<String> parentIfaces = new ArrayList<>();
        for (String n : self.interfaces()) {
            seen.add(n);
            Struct iface = of(n, loader);
            if (iface != null)
                parentIfaces.addAll(iface.interfaces());
        }
        IfaceDecl parentLevel = searchInterfaces(loader, parentIfaces, seen, name, desc, false);
        if (parentLevel != null)
            return parentLevel;
        for (Struct cur = of(self.superName(), loader); cur != null; cur = superOf(cur, loader)) {
            if (!seen.add(cur.name()))
                break;
            IfaceDecl d = searchInterfaces(loader, cur.interfaces(), seen, name, desc, false);
            if (d != null)
                return d;
        }
        return null;
    }

    private IfaceDecl searchInterfaces(ClassLoader loader, List<String> start, Set<String> seen, String name, String desc, boolean callable) {
        ArrayDeque<String> queue = new ArrayDeque<>(start);
        while (!queue.isEmpty()) {
            String n = queue.poll();
            if (n == null || !seen.add(n))
                continue;
            Struct iface = of(n, loader);
            if (iface == null)
                continue;
            Integer access = iface.accessOf(name, desc);
            if (access != null)
                return new IfaceDecl(iface.name(), (access & Opcodes.ACC_ABSTRACT) == 0, callable);
            queue.addAll(iface.interfaces());
        }
        return null;
    }



    public boolean reachesAny(ClassLoader loader, List<String> roots, Set<String> anchors) {
        for (String root : roots) {
            if (root == null)
                continue;
            for (String anchor : anchors)
                if (root.equals(anchor) || isSuperType(anchor, root, loader))
                    return true;
        }
        return false;
    }

    public boolean isSuperType(String a, String b, ClassLoader loader) {
        if (a == null || b == null)
            return false;
        if (a.equals(b))
            return true;
        if (b.startsWith("[") || a.startsWith("["))
            return false;
        Set<String> seen = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(b);
        while (!queue.isEmpty()) {
            String n = queue.poll();
            if (n == null || n.startsWith("[") || !seen.add(n))
                continue;
            if (n.equals(a))
                return true;
            Struct s = of(n, loader);
            if (s == null)
                continue;
            if (s.superName() != null && !s.isInterface())
                queue.add(s.superName());
            queue.addAll(s.interfaces());
        }
        return false;
    }

    public String commonSuperClass(String t1, String t2, ClassLoader loader) {
        try {
            if (t1.equals(t2))
                return t1;
            if (isSuperType(t1, t2, loader))
                return t1;
            if (isSuperType(t2, t1, loader))
                return t2;
            Struct s1 = of(t1, loader);
            if (s1 != null) {
                for (String cand : supertypes(s1, loader)) {
                    if (isSuperType(cand, t2, loader))
                        return cand;
                }
            }
        } catch (Throwable ignored) {
        }
        return "java/lang/Object";
    }

    private List<String> supertypes(Struct s, ClassLoader loader) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String cur = s.superName();
        while (cur != null) {
            if (!seen.add(cur))
                break;
            Struct cs = of(cur, loader);
            if (cs == null)
                break;
            if (!cs.isInterface())
                out.add(cur);
            for (String i : cs.interfaces())
                if (seen.add(i))
                    out.add(i);
            cur = cs.superName();
        }
        for (String i : s.interfaces())
            if (seen.add(i))
                out.add(i);
        out.add("java/lang/Object");
        return out;
    }

    private Struct read(String name, ClassLoader loader) {
        byte[] bytes = bytes(name, loader);
        if (bytes == null)
            return null;
        try {
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            Map<String, Integer> methods = new HashMap<>();
            for (MethodNode mn : cn.methods)
                methods.put(mn.name + ":" + mn.desc, mn.access);
            return new Struct(cn.name, cn.access, cn.superName, List.copyOf(cn.interfaces), Map.copyOf(methods));
        } catch (Throwable e) {
            return null;
        }
    }

    private byte[] bytes(String name, ClassLoader loader) {
        String key = ArOrigin.id(loader) + "#" + name;
        byte[] cached = resourceCache.get(key);
        if (cached != null)
            return cached.length == 0 ? null : cached;
        byte[] buf = readResource(name, loader);
        resourceCache.put(key, buf == null ? new byte[0] : buf);
        return buf;
    }

    private byte[] readResource(String name, ClassLoader loader) {
        String file = name + ".class";
        for (ClassLoader l : new ClassLoader[] {loader, ClassLoader.getPlatformClassLoader(), ClassLoader.getSystemClassLoader()}) {
            if (l == null)
                continue;
            try (InputStream is = l.getResourceAsStream(file)) {
                if (is == null)
                    continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = is.read(chunk)) > 0)
                    out.write(chunk, 0, n);
                if (out.size() > 0)
                    return out.toByteArray();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
