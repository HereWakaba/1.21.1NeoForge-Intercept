package com.ryjs.intercept.util.kp.level;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;

import java.lang.reflect.Field;
import java.util.function.Consumer;

import static com.ryjs.intercept.util.kp.EntityUtil.protectList;
import static com.ryjs.intercept.util.kp.EntityUtil.shouldDeath;

public class FakeTickList extends EntityTickList {
    @Override
    public void forEach(Consumer<Entity> ce) {
        try {
            Field f = EntityTickList.class.getDeclaredField("f_156903_");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Int2ObjectMap<Entity> map = (Int2ObjectMap<Entity>) f.get(this);
            for (Entity e : protectList) {
                if (e != null) {
                    ce.accept(e);
                }
            }

            if (map != null) {
                for (Entity e : map.values()) {
                    if (e != null && (!shouldDeath(e))) ce.accept(e);
                }
            }
        } catch (Exception e) {
            System.err.println("FakeTickList.forEach报错:" + e);
        }
    }
}
