package com.ryjs.intercept.service.agent;

import java.lang.instrument.Instrumentation;


public class AgentEntry {

    public static volatile Instrumentation inst;

    public static void agentmain(String args, Instrumentation instrumentation) {
        give(instrumentation);
    }

    public static void premain(String args, Instrumentation instrumentation) {
        give(instrumentation);
    }

    private static void give(Instrumentation instrumentation) {
        inst = instrumentation;
        try {


            for (Class<?> c : instrumentation.getAllLoadedClasses()) {
                if (c == null || !"com.ryjs.intercept.service.agent.AgentChain".equals(c.getName()))
                    continue;
                java.lang.reflect.Field f = c.getDeclaredField("holders");
                f.setAccessible(true);
                ((Instrumentation[]) f.get(null))[0] = instrumentation;
                return;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
