package com.ryjs.intercept.util.kp.level;

import com.google.common.collect.Iterables;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.ryjs.intercept.util.kp.EntityUtil.*;
import static org.openjdk.nashorn.internal.objects.NativeWeakSet.add;

public class FakeClientLevel extends ClientLevel {
    public FakeClientLevel(ClientPacketListener p1, ClientLevelData p2,
                           ResourceKey<Level> p3, Holder<DimensionType> p4,
                           int p5, int p6, Supplier<ProfilerFiller> p7,
                           LevelRenderer p8, boolean p9, long p10) {
        super(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10);
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
    public Iterable<Entity> entitiesForRendering() {
        List<Entity> entities = new ArrayList<>();
        for (Entity e : super.entitiesForRendering()) {
            if (e != null && (!shouldDeath(e))) entities.add(e);
        }
        for (Entity e : protectList) {
            if (e != null) entities.add(e);
        }
        return Iterables.unmodifiableIterable(entities);
    }
}
