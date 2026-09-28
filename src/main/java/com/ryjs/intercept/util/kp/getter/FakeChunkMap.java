package com.ryjs.intercept.util.kp.getter;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class FakeChunkMap extends ChunkMap {
    public FakeChunkMap(ServerLevel p1,
                        LevelStorageSource.LevelStorageAccess p2,
                        com.mojang.datafixers.DataFixer p3,
                        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager p4,
                        Executor p5,
                        net.minecraft.util.thread.BlockableEventLoop p6,
                        net.minecraft.world.level.chunk.LightChunkGetter p7,
                        net.minecraft.world.level.chunk.ChunkGenerator p8,
                        ChunkProgressListener p9,
                        ChunkStatusUpdateListener p10,
                        Supplier p11,
                        int p12,
                        boolean p13) {
        super(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13);
    }

    @Override
    public void move(ServerPlayer player) {

        for (Entity e : ((ServerLevel) this.level).getAllEntities()) {

        }

        super.move(player);
    }
}
