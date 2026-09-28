package com.ryjs.intercept.service.agent;


public final class ScriptGate {

    private ScriptGate() {}


    public static boolean drop(Object modFile) {
        report("已吞掉 JS coremod 脚本清单（无条件）" + modFile);
        return true;
    }


    static void report(String msg) {
        System.out.println("[Intercept-JS] " + msg);
    }
}
