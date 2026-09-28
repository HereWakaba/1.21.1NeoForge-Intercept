package com.ryjs.intercept.util.kp.player.death;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.function.Supplier;

public class ClientDeathLevel extends ClientLevel {
    public ClientDeathLevel(ClientPacketListener connection, ClientLevelData clientLevelData, ResourceKey<Level> dimension, Holder<DimensionType> dimensionType, int viewDistance, int serverSimulationDistance, Supplier<ProfilerFiller> profiler, LevelRenderer levelRenderer, boolean isDebug, long biomeZoomSeed) {
        super(connection, clientLevelData, dimension, dimensionType, viewDistance, serverSimulationDistance, profiler, levelRenderer, isDebug, biomeZoomSeed);
    }
}
