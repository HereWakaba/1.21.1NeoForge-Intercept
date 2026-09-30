package com.ryjs.intercept.util.timestop;

public final class ObfuscationSeed {

    public static final long FROZEN_SEED = 0L;

    private static volatile long frame = 0L;

    private static long lastReseedFrame = -1L;

    private ObfuscationSeed() {
    }

    public static void nextFrame() {
        frame++;
    }

    public static synchronized boolean claimReseed() {
        if (lastReseedFrame == frame) {
            return false;
        }
        lastReseedFrame = frame;
        return true;
    }
}
