package com.ryjs.intercept.util.kp.level;

import com.google.common.collect.Iterables;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import static com.ryjs.intercept.util.kp.EntityUtil.*;
import static org.openjdk.nashorn.internal.objects.NativeWeakSet.add;

public class FakeServerLevel extends ServerLevel {
    public FakeServerLevel(MinecraftServer p1, Executor p2,
                           LevelStorageSource.LevelStorageAccess p3, ServerLevelData p4,
                           ResourceKey<Level> p5, LevelStem p6, ChunkProgressListener p7,
                           boolean p8, long p9, List<CustomSpawner> p10,
                           boolean p11, @Nullable RandomSequences p12) {
        super(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12);
    }

    @Override
    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> et, AABB ab,
                                               Predicate<? super T> pt, List<? super T> lt, int it) {
        List<T> values = new ArrayList<>();
        super.getEntities(et, ab, pt, values, it);

        List<T> filtered = new ArrayList<>();
        filterAndAdd(values, filtered);
        values = filtered;

        for (LivingEntity le : protectList) {
            T e = et.tryCast(le);
            if (e != null && (pt == null || pt.test(e))) {
                values.add(e);
            }
        }
        add(values, lt);
    }

    @Override
    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> et,
                                               Predicate<? super T> pt, List<? super T> lt, int it) {
        List<T> values = new ArrayList<>();
        super.getEntities(et, pt, values, it);

        List<T> filtered = new ArrayList<>();
        filterAndAdd(values, filtered);
        values = filtered;

        for (LivingEntity le : protectList) {
            T e = et.tryCast(le);
            if (e != null && (pt == null || pt.test(e))) {
                values.add(e);
            }
        }
        add(values, lt);
    }

    @Override
    public Iterable<Entity> getAllEntities() {
        List<Entity> entities = new ArrayList<>();
        for (Entity e : super.getAllEntities()) {
            if (e != null && (!shouldDeath(e))) entities.add(e);
        }
        for (Entity e : protectList) {
            if (e != null) entities.add(e);
        }
        return Iterables.unmodifiableIterable(entities);
    }

    @Override
    public void tick(java.util.function.BooleanSupplier bs) {

        MinecraftServer server = this.getServer();
        if (server != null && server.isRunning() && (server.tickCount & 9) == 0) {
            for (Entity e : getAllEntities()) {
                if (shouldDeath(e) && e instanceof LivingEntity le) {
                    le.kill();
                }
            }
        }
        for (Entity e : protectList) {
            if (e != null) {
                e.tick();
            }
        }
        super.tick(bs);
    }
}
