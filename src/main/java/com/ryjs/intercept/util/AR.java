package com.ryjs.intercept.util;

public final class AR {

    public static volatile boolean enabled;

    private AR() {}

    public static boolean shouldAR(String owner, String name, String desc) {
        return enabled;
    }


    public static Throwable caught(String owner, String name, String desc, Throwable thrown) {
        return new RuntimeException("[Intercept-AR] allreturn 的 super 回退炸了 " + owner + "." + name + desc
                + " ← " + thrown, thrown);
    }
}
