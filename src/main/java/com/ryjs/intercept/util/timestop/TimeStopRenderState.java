package com.ryjs.intercept.util.timestop;

public final class TimeStopRenderState {

    public static final ThreadLocal<Boolean> IS_GLOBAL_RENDER_PASS = ThreadLocal.withInitial(() -> false);

    public static final long FROZEN_MILLIS = 10_000L;
    public static final long FROZEN_GAME_TIME = 24_000L;

    public static boolean freezeTimeInRender() {
        return TimeStopState.enabled && IS_GLOBAL_RENDER_PASS.get();
    }

    private TimeStopRenderState() {
    }
}
