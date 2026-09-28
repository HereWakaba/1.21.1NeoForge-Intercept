package com.ryjs.intercept.service.agent;

import java.io.File;
import java.io.InputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public final class CoexGate {

    public static volatile Object unsafe;
    public static volatile ModuleLayer serviceLayer;
    public static volatile String selfSource;
    public static volatile String selfModule;


    public static final Set<String> released = ConcurrentHashMap.newKeySet();


    public static final Set<String> trusted = ConcurrentHashMap.newKeySet();

    static {
        trusted.add("net.byAqua3/thetitansneo/");
    }

    public static int purged;
    public static int releasedCount;


    private CoexGate() {}


    public static boolean trustedName(String name) {
        if (name == null || trusted.isEmpty())
            return false;
        String slash = name.replace('.', '/');
        for (String prefix : trusted)
            if (slash.startsWith(prefix.replace('.', '/')))
                return true;
        return false;
    }


    public static boolean trustedMixinConfig(String normalizedJarPath, String config) {
        String pkg = declaredPackage(normalizedJarPath, config);
        if (pkg == null || !trustedName(pkg))
            return false;
        report("自己人的 mixin 配置放行 " + config + "（package=" + pkg + "）");
        return true;
    }


    private static String declaredPackage(String jarPath, String config) {
        if (jarPath == null || config == null || config.isEmpty())
            return null;
        try (ZipFile jar = new ZipFile(new File(jarPath))) {
            ZipEntry e = jar.getEntry(config);
            if (e == null) {
                var all = jar.entries();
                while (e == null && all.hasMoreElements()) {
                    ZipEntry n = all.nextElement();
                    if (n.getName().endsWith("/" + config))
                        e = n;
                }
            }
            if (e == null)
                return null;
            String text;
            try (InputStream is = jar.getInputStream(e)) {
                text = new String(is.readAllBytes(), "UTF-8");
            }
            int at = text.indexOf("\"package\"");
            if (at < 0)
                return null;
            int open = text.indexOf('"', text.indexOf(':', at + 8) + 1);
            int close = open < 0 ? -1 : text.indexOf('"', open + 1);
            return close < 0 ? null : text.substring(open + 1, close);
        } catch (Throwable t) {
            return null;
        }
    }


    private static Set<String> freed() {
        Set<String> out = new LinkedHashSet<>();
        for (String s : released) {
            String n = norm(s);
            if (n != null)
                out.add(n);
        }
        return out;
    }


    public static boolean keep(String normalizedPath) {
        if (normalizedPath == null)
            return true;
        if (normalizedPath.contains("/libraries/"))
            return true;
        String self = norm(selfSource);
        return self != null && normalizedPath.equalsIgnoreCase(self);
    }


    public static void purge(Object completedLayers, Object layer) {
        if (!"GAME".equals(String.valueOf(layer)))
            return;
        ModuleLayer service = serviceLayer;
        if (service == null) {
            report("purge 放弃：SERVICE 层没注入");
            return;
        }
        String self = norm(selfSource);
        Set<String> freed = freed();
        if (self == null && selfModule == null && freed.isEmpty()) {
            report("purge 放弃：self 未注入、也没有已掏空的 coremod");
            return;
        }
        try {
            Configuration cfg = service.configuration();
            java.lang.reflect.Field f = cfg.getClass().getDeclaredField("modules");
            Object current = read(cfg, f);
            if (!(current instanceof Set))
                return;
            Set<Object> kept = new LinkedHashSet<>();
            int removed = 0;
            for (Object rm : (Set<?>) current) {
                String name = (String) ResolvedModule.class.getMethod("name").invoke(rm);
                String location = moduleLocation(rm);
                if (isSelf(name, location, self)) {
                    removed++;
                    report("摘除 SERVICE 层模块 " + name + "（我们自己） ← " + location);
                    continue;
                }

                if (location != null && freed.contains(norm(location))) {
                    removed++;
                    report("摘除 SERVICE 层模块 " + name + "（coremod 已掏空） ← " + location);
                    continue;
                }
                kept.add(rm);
            }
            if (removed == 0) {
                report("SERVICE 层 Configuration 里没命中（self=" + self + " module=" + selfModule + " released=" + freed.size() + "）");
                return;
            }
            write(cfg, f, kept);
            purged += removed;
            report("✓ 已摘掉 " + removed + " 个模块（nameToModule 未动，SERVICE 层照常定义类）");
        } catch (Throwable t) {
            report("purge 异常（不干预启动）：" + t);
        }
    }


    public static void stripLocated(Object launchContext) {
        String self = norm(selfSource);
        Set<String> freed = freed();
        if (self == null && freed.isEmpty()) {
            report("strip 放弃：self 未注入、也没有已掏空的 coremod");
            return;
        }
        try {
            java.lang.reflect.Field f = launchContext.getClass().getDeclaredField("locatedPaths");
            f.setAccessible(true);
            Object value = f.get(launchContext);
            if (!(value instanceof Set))
                return;
            Set<?> paths = (Set<?>) value;
            int removed = 0;
            for (java.util.Iterator<?> it = paths.iterator(); it.hasNext(); ) {
                String p = norm(String.valueOf(it.next()));
                if (p == null)
                    continue;
                boolean ours = p.equalsIgnoreCase(self);
                if (!ours && !freed.contains(p))
                    continue;
                it.remove();
                removed++;
                releasedCount++;
                report("放出 locatedPaths 条目（" + (ours ? "我们自己" : "coremod 已掏空") + "）" + p);
            }
            report("✓ 已从 locatedPaths 清掉 " + removed + " 条，剩 " + paths.size() + " 条");
        } catch (Throwable t) {
            report("strip 异常（不干预启动）：" + t);
        }
    }

    private static boolean isSelf(String name, String location, String normalizedSelf) {
        if (selfModule != null && selfModule.equals(name))
            return true;
        return normalizedSelf != null && norm(location) != null && norm(location).equalsIgnoreCase(normalizedSelf);
    }

    private static String moduleLocation(Object resolvedModule) throws Exception {
        Object ref = ResolvedModule.class.getMethod("reference").invoke(resolvedModule);
        Optional<?> uri = (Optional<?>) ModuleReference.class.getMethod("location").invoke(ref);
        return uri.isPresent() ? uri.get().toString() : null;
    }


    public static String norm(String location) {
        try {
            String s = java.net.URLDecoder.decode(location, "UTF-8").replace('\\', '/');
            String lower = s.toLowerCase(Locale.ROOT);
            while (lower.startsWith("jar:") || lower.startsWith("union:") || lower.startsWith("file:")) {
                int colon = s.indexOf(':');
                s = s.substring(colon + 1);
                lower = s.toLowerCase(Locale.ROOT);
            }
            if (s.indexOf(':') >= 0 && !(s.length() > 2 && s.charAt(1) == ':' && s.charAt(2) == '/') && !s.startsWith("/"))
                return null;
            int bang = s.indexOf("!/");
            if (bang >= 0)
                s = s.substring(0, bang);
            int hash = s.indexOf('#');
            if (hash >= 0)
                s = s.substring(0, hash);
            while (s.startsWith("/"))
                s = s.substring(1);
            if (s.isEmpty())
                return null;
            return new File(s).getCanonicalPath().replace('\\', '/').toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
    }



    private static Object read(Object target, java.lang.reflect.Field f) throws Exception {
        Object u = unsafe;
        if (u == null) {
            f.setAccessible(true);
            return f.get(target);
        }
        return u.getClass().getMethod("getObject", Object.class, long.class).invoke(u, target, offset(f));
    }

    private static void write(Object target, java.lang.reflect.Field f, Object value) throws Exception {
        Object u = unsafe;
        if (u == null) {
            f.setAccessible(true);
            f.set(target, value);
            return;
        }
        u.getClass().getMethod("putObject", Object.class, long.class, Object.class).invoke(u, target, offset(f), value);
    }

    private static long offset(java.lang.reflect.Field f) throws Exception {
        Object u = unsafe;
        if (u == null)
            throw new IllegalStateException("Unsafe 未注入");
        return ((Long) u.getClass().getMethod("objectFieldOffset", java.lang.reflect.Field.class).invoke(u, f)).longValue();
    }

    public static void report(String msg) {
        System.out.println("[Intercept-Coex] " + msg);
    }
}
