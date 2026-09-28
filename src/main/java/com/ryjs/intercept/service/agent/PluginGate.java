package com.ryjs.intercept.service.agent;

import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.stream.Stream;


public final class PluginGate {

    private PluginGate() {}

    public static Stream<?> filter(Stream<?> plugins) {
        return plugins.filter(PluginGate::keep);
    }

    static boolean keep(Object plugin) {
        if (plugin == null)
            return false;
        if (CoexGate.trustedName(plugin.getClass().getName())) {
            report("保留插件服务 " + type(plugin) + "（自己人命名空间）");
            return true;
        }
        String path = source(plugin);
        if (path == null) {
            report("保留插件服务（取不到来源，不猜）" + type(plugin));
            return true;
        }
        boolean keep = CoexGate.keep(path);
        report((keep ? "保留 " : "拦下 ") + "ILaunchPluginService " + type(plugin) + "（来源 " + path + "）");
        return keep;
    }

    private static String type(Object plugin) {
        return plugin == null ? "null" : plugin.getClass().getName();
    }

    private static String source(Object plugin) {
        try {
            ProtectionDomain pd = plugin == null ? null : plugin.getClass().getProtectionDomain();
            CodeSource cs = pd == null ? null : pd.getCodeSource();
            return cs == null || cs.getLocation() == null ? null : CoexGate.norm(cs.getLocation().toString());
        } catch (Throwable t) {
            return null;
        }
    }

    static void report(String msg) {
        System.out.println("[Intercept-Plugin] " + msg);
    }
}
