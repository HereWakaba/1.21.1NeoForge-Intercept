package com.ryjs.intercept.util.timestop;


public final class TimeStopState {


    public static volatile boolean enabled = false;


    public static void setEnabled(boolean on) {
        if (enabled != on) {
            enabled = on;
            AnimatedTextCache.clear();
        }
    }

    private TimeStopState() {
    }
}
