package com.ryjs.intercept.util.timestop;

public final class TimeStopState {

    public static volatile boolean enabled = false;

    private static volatile long frozenAtMs = 0L;

    private static volatile java.util.function.Consumer<Boolean> toggleListener;

    public static void setToggleListener(java.util.function.Consumer<Boolean> l) {
        toggleListener = l;
    }

    public static void setEnabled(boolean on) {
        if (enabled != on) {
            if (on) {
                frozenAtMs = System.currentTimeMillis();
            }
            enabled = on;
            AnimatedTextCache.clear();
            java.util.function.Consumer<Boolean> l = toggleListener;
            if (l != null) {
                try {
                    l.accept(on);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static long nowMs() {
        return enabled ? frozenAtMs : System.currentTimeMillis();
    }

    private TimeStopState() {
    }
}
