package com.ryjs.intercept.util.kp.getter;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.*;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.ryjs.intercept.util.kp.EntityUtil.setKlass;
import static com.ryjs.intercept.util.kp.EntityUtil.shouldDeath;

public class FakeSectionManager<T extends EntityAccess>
        extends PersistentEntitySectionManager<T> {
    public FakeSectionManager(Class<T> p1,
                              LevelCallback<T> p2, EntityPersistentStorage<T> p3) {
        super(p1, p2, p3);
    }

    @Override
    public boolean storeChunkSections(long chunkPosValue, Consumer<T> consumer) {
        ChunkLoadStatus status = this.chunkLoadStatuses.get(chunkPosValue);
        if (status == ChunkLoadStatus.PENDING) {
            return false;
        }
        List<T> list = this.sectionStorage
                .getExistingSectionsInChunk(chunkPosValue)
                .flatMap(section -> section.getEntities().filter(EntityAccess::shouldBeSaved))
                .filter(e -> !(e instanceof Entity en && shouldDeath(en)))
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            if (status == ChunkLoadStatus.LOADED) {
                this.permanentStorage.storeEntities(
                        new ChunkEntities<>(new ChunkPos(chunkPosValue), ImmutableList.of()));
            }
            return true;
        } else if (status == ChunkLoadStatus.FRESH) {
            if (this.chunkVisibility.get(chunkPosValue) == Visibility.HIDDEN) {
                return true;
            }
            this.requestChunkLoad(chunkPosValue);
            return false;
        } else {
            this.permanentStorage.storeEntities(
                    new ChunkEntities<>(new ChunkPos(chunkPosValue), list));
            list.forEach(consumer);
            return true;
        }
    }

    @Override
    public LevelEntityGetter<T> getEntityGetter() {
        LevelEntityGetter<T> original = super.getEntityGetter();
        try {
            setKlass(original, FakeGetter.class);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("SafeGetter替换失败", e);
        }
        return original;
    }

    @Override
    public boolean addEntity(T entity, boolean b) {
        if (!(entity instanceof Player) && shouldDeath(entity)) {
            return false;
        }
        return super.addEntity(entity, b);
    }

    @Override
    public boolean addEntityUuid(T entity) {
        if (!(entity instanceof Player) && shouldDeath(entity)) {
            return false;
        }
        return super.addEntityUuid(entity);
    }

    @Override
    public boolean addNewEntity(T entity) {
        if (!(entity instanceof Player) && shouldDeath(entity)) {
            return false;
        }
        return super.addNewEntity(entity);
    }

    @Override
    public void startTicking(T entity) {
        if (!(entity instanceof Player) && shouldDeath(entity)) {
            return;
        }
        super.startTicking(entity);
    }

    @Override
    public void startTracking(T entity) {
        if (!(entity instanceof Player) && shouldDeath(entity)) {
            return;
        }
        super.startTracking(entity);
    }
}
