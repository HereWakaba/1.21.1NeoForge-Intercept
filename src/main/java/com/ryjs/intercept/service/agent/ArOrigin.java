package com.ryjs.intercept.service.agent;

import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public final class ArOrigin {


    static final String[] OWNED_PREFIXES = {"com/ryjs/intercept/", "org/objectweb/asm/"};
    static final String[] JDK_PREFIXES = {"java/", "javax/", "jdk/", "sun/", "com/sun/"};
    private static final String LIBRARIES_SEGMENT = "/libraries/";

    private static final String GATE_RESOURCE = "com/ryjs/intercept/util/AR.class";
    private static final Map<Integer, Boolean> GATE_VISIBLE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LIBRARY_BY_ROOT = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Intercept-AR");

    private ArOrigin() {}

    public static boolean isInstrumentable(String name, ClassLoader loader, ProtectionDomain pd) {
        return isInstrumentable(name, loader, pd, null);
    }

    public static boolean isInstrumentable(String name, ClassLoader loader, ProtectionDomain pd, Class<?> beingRedefined) {
        if (isExcludedName(name))
            return false;
        String url = sourceOf(name, loader, pd, beingRedefined);
        if (url == null)
            return isGenerated(loader);
        String root = rootOf(url);
        if (root == null)
            return false;
        reportOnce(root);
        return !LIBRARY_BY_ROOT.computeIfAbsent(root, r -> Boolean.valueOf(r.contains(LIBRARIES_SEGMENT)));
    }


    private static boolean isGenerated(ClassLoader loader) {
        if (loader == null)
            return false;
        reportOnce("<no-source>/" + loader.getClass().getName());
        return true;
    }


    public static boolean gateVisible(ClassLoader loader) {
        int id = id(loader);
        Boolean known = GATE_VISIBLE.get(id);
        if (known != null)
            return known.booleanValue();
        boolean ok = probe(loader);
        GATE_VISIBLE.put(id, Boolean.valueOf(ok));
        return ok;
    }

    private static boolean probe(ClassLoader loader) {
        ClassLoader probe = loader == null ? ClassLoader.getPlatformClassLoader() : loader;
        boolean ok;
        try {
            ok = probe.getResource(GATE_RESOURCE) != null;
        } catch (Throwable t) {
            ok = false;
        }
        report("gate " + (ok ? "可解析" : "★不可解析 ⇒ 这一层的类一律不插") + " loader=" + loader);
        return ok;
    }

    static boolean isExcludedName(String name) {
        if (name == null || name.equals("module-info"))
            return true;
        for (String prefix : OWNED_PREFIXES)
            if (name.startsWith(prefix))
                return true;



        if (CoexGate.trustedName(name) || CoexGate.trustedName(strip(name)))
            return true;
        for (String prefix : JDK_PREFIXES)
            if (name.startsWith(prefix))
                return true;
        return false;
    }

    private static String strip(String name) {
        int lambda = name.indexOf("$$Lambda");
        if (lambda > 0)
            return name.substring(0, lambda);
        int slash = name.lastIndexOf('/');
        return slash > 0 && name.startsWith("0x", slash + 1) ? name.substring(0, slash) : name;
    }

    public static int id(ClassLoader loader) {
        return loader == null ? 0 : System.identityHashCode(loader);
    }


    public static String sourceUrl(String name, ClassLoader loader, ProtectionDomain pd, Class<?> beingRedefined) {
        return sourceOf(name, loader, pd, beingRedefined);
    }


    private static String sourceOf(String name, ClassLoader loader, ProtectionDomain pd, Class<?> beingRedefined) {
        if (beingRedefined != null && pd == null)
            pd = beingRedefined.getProtectionDomain();
        String url = locate(name, loader, pd);
        if (url != null)
            return url;

        String host = hostOf(name);
        if (host != null && !host.equals(name) && (url = locate(host, loader, null)) != null)
            return url;
        if (beingRedefined != null) {
            Class<?> nest = beingRedefined.getNestHost();
            if (nest != null && nest != beingRedefined) {
                String internal = nest.getName().replace('.', '/');
                if ((url = locate(internal, nest.getClassLoader(), nest.getProtectionDomain())) != null)
                    return url;
            }
        }
        return null;
    }


    private static String hostOf(String name) {
        int lambda = name.indexOf("$$Lambda");
        if (lambda > 0)
            return name.substring(0, lambda);
        int mark = name.indexOf("/0x");
        if (mark > 0)
            return name.substring(0, mark);
        int plus = name.indexOf("+0x");
        return plus > 0 ? name.substring(0, plus) : name;
    }

    private static String locate(String name, ClassLoader loader, ProtectionDomain pd) {
        if (pd != null) {
            CodeSource cs = pd.getCodeSource();
            if (cs != null && cs.getLocation() != null)
                return cs.getLocation().toString();
        }
        String file = name + ".class";
        for (ClassLoader l : new ClassLoader[] {loader, ClassLoader.getPlatformClassLoader(), ClassLoader.getSystemClassLoader()}) {
            if (l == null)
                continue;
            URL url = l.getResource(file);
            if (url != null)
                return url.toString();
        }
        return null;
    }


    private static String rootOf(String url) {



        String s = url.replace('\\', '/').toLowerCase().replace("%23", "#");
        while (s.startsWith("jar:") || s.startsWith("union:") || s.startsWith("file:")) {
            int colon = s.indexOf(':');
            s = s.substring(colon + 1);
        }
        boolean fileBacked = s.startsWith("/") || (s.length() > 2 && s.charAt(1) == ':' && s.charAt(2) == '/');
        if (!fileBacked)
            return null;
        int bang = s.indexOf("!/");
        if (bang >= 0)
            s = s.substring(0, bang);
        int hash = s.indexOf('#');
        if (hash >= 0)
            s = s.substring(0, hash);
        int jar = s.lastIndexOf(".jar");
        if (jar >= 0)
            return s.substring(0, jar + 4);
        int cls = s.lastIndexOf('/');
        int colon = s.lastIndexOf(':');
        return cls > colon ? s.substring(0, cls) : null;
    }

    private static void reportOnce(String root) {
        if (REPORTED.add(root)) {
            String verdict = root.contains(LIBRARIES_SEGMENT) ? "SKIP(libraries)" : "TARGET";
            report("source=" + root + " -> " + verdict);
            try {
                LOGGER.info("source={} -> {}", root, verdict);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void report(String msg) {
        System.out.println("[Intercept-AR] " + msg);
    }
}
