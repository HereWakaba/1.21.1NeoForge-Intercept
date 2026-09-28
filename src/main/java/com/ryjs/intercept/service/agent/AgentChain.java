package com.ryjs.intercept.service.agent;

import com.ryjs.intercept.service.agent.transformer.ArTransformer;
import com.ryjs.intercept.service.agent.transformer.CoexTransformer;
import com.ryjs.intercept.service.agent.transformer.CoremodTransformer;
import com.ryjs.intercept.service.agent.transformer.MixinGateTransformer;
import com.ryjs.intercept.service.agent.transformer.PluginGateTransformer;
import com.ryjs.intercept.service.agent.transformer.RevertSentinel;
import com.ryjs.intercept.service.agent.transformer.ScriptGateTransformer;

import java.io.FileOutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;


public final class AgentChain {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Intercept-AR");

    public static final Instrumentation[] holders = new Instrumentation[1];
    private static final String ENTRY = "com.ryjs.intercept.service.agent.AgentEntry";
    private static final String GATE = "com.ryjs.intercept.service.agent.CoexGate";
    private static final String MIXIN_GATE = "com.ryjs.intercept.service.agent.MixinGate";
    private static final String SCRIPT_GATE = "com.ryjs.intercept.service.agent.ScriptGate";
    private static final String PLUGIN_GATE = "com.ryjs.intercept.service.agent.PluginGate";
    private static volatile boolean started;

    private AgentChain() {}

    public static Instrumentation instrumentation() {
        return holders[0];
    }

    public static synchronized void start() {
        if (started)
            return;
        started = true;
        try {
            attach();
        } catch (Throwable t) {
            report("自附加失败，AR 插桩不可用：" + t);
            t.printStackTrace();
        }
    }

    private static void attach() throws Throwable {
        MethodHandles.Lookup trusted = trusted();
        Class<?> entry = inBootstrap(trusted, ENTRY, bytes(ENTRY));
        report("AgentEntry loader=" + entry.getClassLoader());
        Path jar = agentJar(entry.getName());
        Object fn = function("com.sun.jna.Function");
        Object javaVM = currentJavaVM(fn);
        long begin = System.currentTimeMillis();
        int rc = attachAgent(fn, javaVM, jar.toAbsolutePath().toString());
        Instrumentation inst = wait(jar, 5000);
        report("Agent_OnAttach rc=" + rc + " 用时=" + (System.currentTimeMillis() - begin) + "ms inst=" + inst);
        if (inst == null)
            return;
        ArTransformer ar = new ArTransformer();
        inst.addTransformer(ar, true);
        CoremodTransformer coremod = new CoremodTransformer();
        inst.addTransformer(coremod, true);
        report("已注册 ArTransformer + CoremodTransformer，retransform 支持=" + inst.isRetransformClassesSupported()
                + " 已加载类=" + inst.getAllLoadedClasses().length);
        coex(trusted, inst, coremod);
        mixin(trusted, inst);
        scripts(trusted, inst);
        plugins(trusted, inst);
        gateCopies(inst);
        RevertSentinel sentinel = new RevertSentinel(ar);
        inst.addTransformer(sentinel, true);

        catchUpLoaded(coremod, inst);




        ArWatchdog.arm(inst, ar, sentinel);

    }


    private static void mixin(MethodHandles.Lookup trusted, Instrumentation inst) throws Throwable {
        Class<?> gate = inBootstrap(trusted, MIXIN_GATE, bytes(MIXIN_GATE));
        report("MixinGate loader=" + gate.getClassLoader() + "（归属判定共用 CoexGate.selfSource=" + selfLocation() + "）");
        inst.addTransformer(new MixinGateTransformer(), true);
        for (Class<?> c : inst.getAllLoadedClasses()) {
            String name = c == null ? null : c.getName();
            if ("org.spongepowered.asm.mixin.Mixins".equals(name) || "net.neoforged.fml.loading.mixin.DeferredMixinConfigRegistration".equals(name)) {
                inst.retransformClasses(c);
                report("已提交 retransform: " + name);
            }
        }
    }


    private static void scripts(MethodHandles.Lookup trusted, Instrumentation inst) throws Throwable {
        Class<?> gate = inBootstrap(trusted, SCRIPT_GATE, bytes(SCRIPT_GATE));
        report("ScriptGate loader=" + gate.getClassLoader());
        inst.addTransformer(new ScriptGateTransformer(), true);
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c != null && "net.neoforged.fml.loading.moddiscovery.ModFileParser".equals(c.getName())) {
                inst.retransformClasses(c);
                report("已提交 retransform: " + c.getName());
            }
        }
    }


    private static void plugins(MethodHandles.Lookup trusted, Instrumentation inst) throws Throwable {
        Class<?> gate = inBootstrap(trusted, PLUGIN_GATE, bytes(PLUGIN_GATE));
        report("PluginGate loader=" + gate.getClassLoader());
        inst.addTransformer(new PluginGateTransformer(), true);
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c != null && "cpw.mods.modlauncher.LaunchPluginHandler".equals(c.getName())) {
                inst.retransformClasses(c);
                report("已提交 retransform: " + c.getName());
            }
        }
    }


    private static void catchUpLoaded(CoremodTransformer coremod, Instrumentation inst) {
        int done = 0;
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c == null || !inst.isModifiableClass(c))
                continue;
            String name = c.getName().replace('.', '/');
            try {
                if (!ArOrigin.isInstrumentable(name, c.getClassLoader(), c.getProtectionDomain(), c))
                    continue;
                if (!coremod.isCoremodService(c.getClassLoader(), name))
                    continue;
                inst.retransformClasses(c);
                done++;
            } catch (Throwable t) {
                report("补 retransform " + name + " 失败: " + t);
            }
        }
        report("已加载 coremod 服务补刀 " + done + " 个");
    }


    private static void coex(MethodHandles.Lookup trusted, Instrumentation inst, CoremodTransformer coremod) throws Throwable {
        Class<?> gate = inBootstrap(trusted, GATE, bytes(GATE));
        gate.getField("unsafe").set(null, unsafe(trusted));
        gate.getField("serviceLayer").set(null, AgentChain.class.getModule().getLayer());
        gate.getField("selfSource").set(null, selfLocation());
        String module = AgentChain.class.getModule().getName();
        gate.getField("selfModule").set(null, module == null || module.isBlank() ? null : module);
        @SuppressWarnings("unchecked")
        java.util.Set<String> released = (java.util.Set<String>) gate.getField("released").get(null);
        coremod.releaseInto(released);
        report("CoexGate loader=" + gate.getClassLoader() + " SERVICE=" + AgentChain.class.getModule().getLayer()
                + " self=" + selfLocation() + " module=" + module);
        inst.addTransformer(new CoexTransformer(), true);
        for (Class<?> c : inst.getAllLoadedClasses()) {
            String n = c == null ? null : c.getName();
            if ("cpw.mods.modlauncher.ModuleLayerHandler".equals(n) || "net.neoforged.fml.loading.LaunchContext".equals(n)) {
                inst.retransformClasses(c);
                report("已提交 retransform: " + n);
            }
        }
    }


    private static void gateCopies(Instrumentation inst) {
        int n = 0;
        StringBuilder sb = new StringBuilder();
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c == null || !"com.ryjs.intercept.util.AR".equals(c.getName()))
                continue;
            n++;
            sb.append(' ').append(Integer.toHexString(System.identityHashCode(c))).append("@").append(c.getClassLoader());
        }
        report("gate 类副本 " + n + " 份:" + sb);
    }


    private static String selfLocation() {
        try {
            java.security.CodeSource cs = AgentChain.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null)
                return cs.getLocation().toString();
        } catch (Throwable ignored) {
        }
        String file = AgentChain.class.getName().replace('.', '/') + ".class";
        for (ClassLoader l : new ClassLoader[] {AgentChain.class.getClassLoader(), ClassLoader.getSystemClassLoader()}) {
            if (l == null)
                continue;
            java.net.URL u = l.getResource(file);
            if (u != null)
                return u.toString();
        }
        return null;
    }


    private static Object unsafe(MethodHandles.Lookup trusted) throws Throwable {
        Class<?> u = Class.forName("sun.misc.Unsafe");
        try {
            return trusted.unreflectGetter(u.getDeclaredField("theUnsafe")).invoke();
        } catch (Throwable t) {
            java.lang.reflect.Field f = u.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return f.get(null);
        }
    }



    private static MethodHandles.Lookup trusted() throws Exception {
        Constructor<?> c = sun.reflect.ReflectionFactory.getReflectionFactory().newConstructorForSerialization(MethodHandles.Lookup.class,
                MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class));
        return (MethodHandles.Lookup) c.newInstance(Object.class, null, -1);
    }

    private static Class<?> inBootstrap(MethodHandles.Lookup lookup, String name, byte[] bytes) throws Throwable {



        try {
            Class<?> already = Class.forName(name, false, null);
            if (already.getClassLoader() == null) {
                return already;
            }
        } catch (ClassNotFoundException ignored) {

        }
        MethodHandle define = lookup.findStatic(ClassLoader.class, "defineClass1",
                MethodType.methodType(Class.class, ClassLoader.class, String.class, byte[].class, int.class, int.class, ProtectionDomain.class, String.class));

        return (Class<?>) define.invokeWithArguments(Arrays.asList(null, name.replace('.', '/'), bytes, 0, bytes.length, null, null));
    }

    private static byte[] bytes(String name) throws Exception {
        String file = name.replace('.', '/') + ".class";
        try (var is = AgentChain.class.getClassLoader().getResourceAsStream(file)) {
            if (is == null)
                throw new IllegalStateException("读不到自身资源 " + file + " loader=" + AgentChain.class.getClassLoader());
            return is.readAllBytes();
        }
    }

    private static Path agentJar(String agentClass) throws Exception {
        Manifest mf = new Manifest();
        Attributes a = mf.getMainAttributes();
        a.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        a.put(new Attributes.Name("Agent-Class"), agentClass);
        a.put(new Attributes.Name("Can-Redefine-Classes"), "true");
        a.put(new Attributes.Name("Can-Retransform-Classes"), "true");
        Path jar = Files.createTempFile("intercept-ar", ".jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar.toFile()), mf)) {
            jos.flush();
        }
        return jar;
    }

    private static Instrumentation wait(Path jar, long timeout) {
        long end = System.currentTimeMillis() + timeout;
        while (holders[0] == null && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        try {
            Files.deleteIfExists(jar);
        } catch (Exception ignored) {
        }
        return holders[0];
    }



    private static Object function(String className) throws Exception {
        Class<?> fn = null;
        for (ClassLoader l : new ClassLoader[] {AgentChain.class.getClassLoader(), ClassLoader.getSystemClassLoader(),
                Thread.currentThread().getContextClassLoader()}) {
            if (l == null)
                continue;
            try {
                fn = Class.forName(className, true, l);
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }
        if (fn == null)
            throw new IllegalStateException("JNA 不可见：" + className);
        return fn.getMethod("getFunction", String.class, String.class);
    }

    private static Object currentJavaVM(Object getFunction) throws Exception {
        Class<?> pointer = Class.forName("com.sun.jna.Pointer");
        Class<?> intByRef = Class.forName("com.sun.jna.ptr.IntByReference");
        Object jvm = ((Method) getFunction).invoke(null, "jvm", "JNI_GetCreatedJavaVMs");
        Object buf = Array.newInstance(pointer, 1);
        Object count = intByRef.getConstructor(int.class).newInstance(1);
        int rc = invokeInt(jvm, new Object[] {buf, 1, intByRef.getMethod("getPointer").invoke(count)});
        if (rc != 0 || Array.get(buf, 0) == null)
            throw new IllegalStateException("JNI_GetCreatedJavaVMs rc=" + rc);
        report("JNA " + jvm.getClass() + " JavaVM*=" + Array.get(buf, 0));
        return Array.get(buf, 0);
    }

    private static int attachAgent(Object getFunction, Object javaVM, String jarPath) throws Exception {
        Object attach = ((Method) getFunction).invoke(null, "instrument", "Agent_OnAttach");
        return invokeInt(attach, new Object[] {javaVM, jarPath, null});
    }


    private static int invokeInt(Object function, Object[] jnaArgs) throws Exception {
        Method m = function.getClass().getMethod("invokeInt", Object[].class);
        return (int) m.invoke(function, new Object[] {jnaArgs});
    }

    static void report(String msg) {
        System.out.println("[Intercept-AR] " + msg);
        try {
            LOGGER.info(msg);
        } catch (Throwable ignored) {
        }
    }
}
