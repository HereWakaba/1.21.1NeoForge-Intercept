package com.ryjs.intercept.util.agent;

import com.ryjs.intercept.service.agent.AgentChain;

import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;


public final class AgentBackend {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Intercept-Agent");


    private static final String CARRIER = "com.ryjs.intercept.util.agent.AgentCarrier";

    private static volatile Instrumentation instrumentation;
    private static volatile String note = "尚未附加";
    private static volatile boolean attaching;

    private AgentBackend() {
    }


    public static Instrumentation instrumentation() {
        Instrumentation inst = instrumentation;
        if (inst != null) {
            return inst;
        }
        return AgentChain.instrumentation();
    }

    public static String note() {
        return note;
    }


    public static synchronized boolean attach() {
        Instrumentation have = instrumentation();
        if (have != null) {
            instrumentation = have;
            return true;
        }
        if (attaching) {
            return false;
        }
        attaching = true;
        try {
            instrumentation = doAttach();
            return instrumentation != null;
        } catch (Throwable t) {
            note = "附加失败:" + t.getClass().getSimpleName() + " " + t.getMessage();
            LOGGER.warn(note, t);
            return false;
        } finally {
            attaching = false;
        }
    }

    private static Instrumentation doAttach() throws Throwable {
        Class<?> carrier = carrier();
        Path jar = agentJar(carrier.getName());
        note = "正在附加 agent jar=" + jar.getFileName();
        Object getFunction = function("com.sun.jna.Function");
        Object javaVM = currentJavaVM(getFunction);
        long begin = System.currentTimeMillis();
        int rc = attachAgent(getFunction, javaVM, jar.toAbsolutePath().toString());
        Instrumentation inst = waitInst(jar, 6000);
        note = "Agent_OnAttach rc=" + rc + " 用时=" + (System.currentTimeMillis() - begin) + "ms inst=" + inst;
        LOGGER.info(note);
        return inst;
    }




    public static synchronized boolean addTransformer(ClassFileTransformer transformer) {
        Instrumentation inst = instrumentation();
        if (inst == null && !attach()) {
            return false;
        }
        instrumentation().addTransformer(transformer, true);
        return true;
    }


    public static boolean retransform(Class<?>... classes) {
        Instrumentation inst = instrumentation();
        if (inst == null && !attach()) {
            return false;
        }
        try {
            instrumentation().retransformClasses(classes);
            return true;
        } catch (Throwable t) {
            note = "retransform 失败:" + t.getClass().getSimpleName() + " " + t.getMessage();
            LOGGER.warn(note, t);
            return false;
        }
    }




    private static Class<?> carrier() throws Throwable {
        String name = CARRIER;
        Class<?> early = findLoaded(name);
        if (early != null) {
            return early;
        }
        MethodHandles.Lookup trusted = trusted();
        MethodHandle define = trusted.findStatic(ClassLoader.class, "defineClass1", MethodType.methodType(
                Class.class, ClassLoader.class, String.class, byte[].class, int.class, int.class,
                java.security.ProtectionDomain.class, String.class));
        byte[] bytes = resourceBytes(name);

        return (Class<?>) define.invokeWithArguments(java.util.Arrays.asList(
                null, name.replace('.', '/'), bytes, 0, bytes.length, null, null));
    }


    private static Class<?> findLoaded(String binaryName) {
        Instrumentation inst = AgentChain.instrumentation();
        if (inst == null) {
            try {
                Class<?> early = Class.forName(binaryName, false, null);
                return early.getClassLoader() == null ? early : null;
            } catch (Throwable ignored) {
                return null;
            }
        }
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c != null && binaryName.equals(c.getName()) && c.getClassLoader() == null) {
                return c;
            }
        }
        return null;
    }


    private static MethodHandles.Lookup trusted() throws Throwable {
        Class<?> rf = Class.forName("sun.reflect.ReflectionFactory");
        Object factory = rf.getMethod("getReflectionFactory").invoke(null);
        Method newCtor = rf.getMethod("newConstructorForSerialization", Class.class, java.lang.reflect.Constructor.class);
        java.lang.reflect.Constructor<?> declared = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class);
        return (MethodHandles.Lookup) ((java.lang.reflect.Constructor<?>) newCtor.invoke(factory, MethodHandles.Lookup.class, declared)).newInstance(Object.class, null, -1);
    }

    private static byte[] resourceBytes(String name) throws Throwable {
        String file = name.replace('.', '/') + ".class";
        for (ClassLoader l : new ClassLoader[] {AgentBackend.class.getClassLoader(), ClassLoader.getSystemClassLoader()}) {
            if (l == null) {
                continue;
            }
            try (var is = l.getResourceAsStream(file)) {
                if (is != null) {
                    return is.readAllBytes();
                }
            }
        }
        throw new IllegalStateException("读不到载体字节码 " + file);
    }



    private static Path agentJar(String agentClass) throws Exception {
        Manifest mf = new Manifest();
        Attributes a = mf.getMainAttributes();
        a.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        a.put(new Attributes.Name("Agent-Class"), agentClass);
        a.put(new Attributes.Name("Can-Redefine-Classes"), "true");
        a.put(new Attributes.Name("Can-Retransform-Classes"), "true");
        a.put(new Attributes.Name("Can-Set-Native-Method-Prefix"), "true");
        Path jar = Files.createTempFile("intercept-post", ".jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar.toFile()), mf)) {
            jos.flush();
        }
        return jar;
    }

    private static Instrumentation waitInst(Path jar, long timeout) {
        long end = System.currentTimeMillis() + timeout;
        Instrumentation inst = null;
        while ((inst = readCarrierInst()) == null && System.currentTimeMillis() < end) {
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
        return inst;
    }


    private static Instrumentation readCarrierInst() {
        try {
            Class<?> carrier = Class.forName(CARRIER, false, null);
            if (carrier.getClassLoader() != null) {
                return null;
            }
            Field f = carrier.getDeclaredField("inst");
            Object value = f.get(null);
            return value instanceof Instrumentation i ? i : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object function(String className) throws Exception {
        Class<?> fn = null;
        for (ClassLoader l : new ClassLoader[] {AgentBackend.class.getClassLoader(), ClassLoader.getSystemClassLoader(),
                Thread.currentThread().getContextClassLoader()}) {
            if (l == null) {
                continue;
            }
            try {
                fn = Class.forName(className, true, l);
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }
        if (fn == null) {
            throw new IllegalStateException("JNA 不可见：" + className + "（检查 libraries 里有没有 jna）");
        }
        return fn.getMethod("getFunction", String.class, String.class);
    }

    private static Object currentJavaVM(Object getFunction) throws Exception {
        Class<?> pointer = Class.forName("com.sun.jna.Pointer");
        Class<?> intByRef = Class.forName("com.sun.jna.ptr.IntByReference");
        Object jvm = ((Method) getFunction).invoke(null, "jvm", "JNI_GetCreatedJavaVMs");
        Object buf = java.lang.reflect.Array.newInstance(pointer, 1);
        Object count = intByRef.getConstructor(int.class).newInstance(1);
        int rc = invokeInt(jvm, new Object[] {buf, 1, intByRef.getMethod("getPointer").invoke(count)});
        if (rc != 0 || java.lang.reflect.Array.get(buf, 0) == null) {
            throw new IllegalStateException("JNI_GetCreatedJavaVMs rc=" + rc);
        }
        return java.lang.reflect.Array.get(buf, 0);
    }

    private static int attachAgent(Object getFunction, Object javaVM, String jarPath) throws Exception {
        Object attach = ((Method) getFunction).invoke(null, "instrument", "Agent_OnAttach");
        return invokeInt(attach, new Object[] {javaVM, jarPath, null});
    }


    private static int invokeInt(Object function, Object[] jnaArgs) throws Exception {
        Method m = function.getClass().getMethod("invokeInt", Object[].class);
        return (int) m.invoke(function, new Object[] {jnaArgs});
    }
}
