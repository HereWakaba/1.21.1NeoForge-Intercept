package com.ryjs.intercept.util.agent;

import java.lang.instrument.Instrumentation;


public class AgentCarrier {


    public static volatile Instrumentation inst;


    public static void agentmain(String args, Instrumentation instrumentation) {
        inst = instrumentation;
    }


    public static void premain(String args, Instrumentation instrumentation) {
        inst = instrumentation;
    }
}
