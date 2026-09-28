package com.ryjs.intercept.service.agent;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;


public final class MixinGate {


    private static final List<String> MINE = List.of("org/spongepowered/asm/", "com/ryjs/intercept/service/agent/", "java.", "jdk.", "sun.", "com/mojang/logging/");

    private MixinGate() {}


    public static boolean dropByOwner(String config, String modId, Object owner) {
        if (modId == null)
            return drop(config);
        String path = pathOf(owner, modId);
        if (path == null) {

            report("!! 归属解析失败，已放行 " + config + "（modId=" + modId + "）—— 闸口在这台环境上没生效，需要修");
            return false;
        }
        boolean keep = keep(path) || CoexGate.trustedMixinConfig(path, config);
        report((keep ? "放行 mixin 配置 " : "已吞掉 mixin 配置 ") + config + "（modId=" + modId + " ← " + path + "）");
        return !keep;
    }


    public static boolean drop(String config) {
        String path = requester();
        if (path == null) {
            report("放行 mixin 配置 " + config + "（归属不明）");
            return false;
        }
        boolean keep = keep(path) || CoexGate.trustedMixinConfig(path, config);
        report((keep ? "放行 mixin 配置 " : "已吞掉 mixin 配置 ") + config + "（来源 " + path + "）");
        return !keep;
    }


    public static boolean keep(String normalizedPath) {
        return CoexGate.keep(normalizedPath);
    }


    private static String pathOf(Object owner, String modId) {
        if (!(owner instanceof Class) || modId == null)
            return null;
        try {
            ClassLoader loader = ((Class<?>) owner).getClassLoader();
            Class<?> list = Class.forName("net.neoforged.fml.loading.LoadingModList", true, loader);
            Object instance = list.getMethod("get").invoke(null);
            java.lang.reflect.Method byId = list.getMethod("getModFileById", String.class);
            Object info = byId.invoke(instance, modId);
            if (info == null)
                return null;

            java.lang.reflect.Method getFile = byId.getReturnType().getMethod("getFile");
            Object file = getFile.invoke(info);
            if (file == null)
                return null;
            Object path = getFile.getReturnType().getMethod("getFilePath").invoke(file);
            return path instanceof Path ? CoexGate.norm(((Path) path).toString()) : CoexGate.norm(String.valueOf(path));
        } catch (Throwable t) {
            report("  modId→路径 失败（" + modId + "）：" + t);
            return null;
        }
    }


    private static String requester() {
        try {
            List<Class<?>> chain = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                            .filter(c -> !isFramework(c.getName()))
                            .collect(Collectors.toList()));
            for (Class<?> c : chain) {
                String url = sourceOf(c);
                if (url != null) {
                    String norm = CoexGate.norm(url);
                    if (norm != null)
                        return norm;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String sourceOf(Class<?> c) {
        try {
            java.security.ProtectionDomain pd = c.getProtectionDomain();
            java.security.CodeSource cs = pd == null ? null : pd.getCodeSource();
            URL url = cs == null ? null : cs.getLocation();
            return url == null ? null : url.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isFramework(String name) {
        for (String prefix : MINE)
            if (name.startsWith(prefix))
                return true;
        return false;
    }

    static void report(String msg) {
        System.out.println("[Intercept-Mixin] " + msg);
    }
}
