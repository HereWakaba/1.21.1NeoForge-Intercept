package com.ryjs.intercept.util.kp.getter;

import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

public class FakeLevelEntityGetter implements LevelEntityGetter<Entity> {

    @Override
    public @Nullable Entity get(int i) {
        return null;
    }

    @Override
    public @Nullable Entity get(UUID uuid) {
        return null;
    }

    @Override
    public Iterable<Entity> getAll() {
        return null;
    }

    @Override
    public <U extends Entity> void get(EntityTypeTest<Entity, U> entityTypeTest, AbortableIterationConsumer<U> abortableIterationConsumer) {

    }

    @Override
    public void get(AABB aabb, Consumer<Entity> consumer) {

    }

    @Override
    public <U extends Entity> void get(EntityTypeTest<Entity, U> entityTypeTest, AABB aabb, AbortableIterationConsumer<U> abortableIterationConsumer) {

    }
}
