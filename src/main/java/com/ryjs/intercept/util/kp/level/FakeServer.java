package com.ryjs.intercept.util.kp.level;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;

public class FakeServer extends IntegratedServer {
    public FakeServer(Thread serverThread, Minecraft minecraft, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, Services services, ChunkProgressListenerFactory progressListenerFactory) {
        super(serverThread, minecraft, storageSource, packRepository, worldStem, services, progressListenerFactory);
    }

    @Override
    public void tickServer(@NotNull BooleanSupplier p_129871_) {
        super.tickServer(p_129871_);
    }


}
