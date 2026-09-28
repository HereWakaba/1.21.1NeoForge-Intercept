package com.ryjs.intercept.service.agent;


public final class ThreadGuard {

    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ThreadGuard() {}

    public static boolean enter() {
        if (BUSY.get().booleanValue())
            return false;
        BUSY.set(Boolean.TRUE);
        return true;
    }

    public static void leave() {
        BUSY.set(Boolean.FALSE);
    }


    public static boolean busy() {
        return BUSY.get().booleanValue();
    }
}
