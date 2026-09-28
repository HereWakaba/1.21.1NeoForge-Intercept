package com.ryjs.intercept.service;

import com.ryjs.intercept.service.agent.AgentChain;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;

public class GBService implements GraphicsBootstrapper {

    static{
        AgentChain.start();
    }

    @Override
    public String name() {
        return " ";
    }

    @Override
    public void bootstrap(String[] arguments) {

    }
}
