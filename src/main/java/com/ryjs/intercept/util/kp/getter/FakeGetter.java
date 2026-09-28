package com.ryjs.intercept.util.kp.getter;

import com.google.common.collect.Iterables;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.*;

import java.util.ArrayList;
import java.util.List;

import static com.ryjs.intercept.util.kp.EntityUtil.protectList;
import static com.ryjs.intercept.util.kp.EntityUtil.shouldDeath;

public class FakeGetter<T extends EntityAccess> extends LevelEntityGetterAdapter<T> {
    public FakeGetter(EntityLookup<T> p1, EntitySectionStorage<T> p2) {
        super(p1, p2);
    }

    @Override
    public Iterable<T> getAll() {
        List<T> entities = new ArrayList<>();
        for (LivingEntity le : protectList) {
            try {
                @SuppressWarnings("unchecked")
                T e = (T) le;
                if (e != null) entities.add(e);
            } catch (ClassCastException ignored) {
            }
        }
        for (T e : super.getAll()) {
            if (e != null && (!shouldDeath(e))) entities.add(e);
        }
        return Iterables.unmodifiableIterable(entities);
    }

    @Override
    public <U extends T> void get(EntityTypeTest<T, U> test,
                                  net.minecraft.util.AbortableIterationConsumer<U> consumer) {
        for (LivingEntity le : protectList) {
            if (le != null) {
                try {
                    @SuppressWarnings("unchecked")
                    U casted = (U) le;
                    consumer.accept(casted);
                } catch (ClassCastException ignored) {
                }
            }
        }
        super.get(test, u -> {
            if (shouldDeath(u)) {
                return net.minecraft.util.AbortableIterationConsumer.Continuation.CONTINUE;
            }
            return consumer.accept(u);
        });
    }
}
