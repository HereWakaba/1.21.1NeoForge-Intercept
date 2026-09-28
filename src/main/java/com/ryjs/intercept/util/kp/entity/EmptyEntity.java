package com.ryjs.intercept.util.kp.entity;

import com.ryjs.intercept.util.kp.EntityUtil;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;


public class EmptyEntity extends Entity {
    public EmptyEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }




    public static boolean isEmpty(Entity entity) {
        return entity instanceof EmptyEntity
                || entity instanceof EmptyLivingEntity
                || entity instanceof EmptyMob
                || entity instanceof EmptyMonster;
    }


    @Nullable
    public static Class<? extends Entity> shellFor(Entity entity) {
        if (entity == null || entity instanceof Player) {
            return null;
        }
        if (entity instanceof Monster) {
            return EmptyMonster.class;
        }
        if (entity instanceof Mob) {
            return EmptyMob.class;
        }
        if (entity instanceof LivingEntity) {
            return EmptyLivingEntity.class;
        }
        return EmptyEntity.class;
    }


    public static void swapTo(Entity entity) throws Throwable {
        Class<? extends Entity> shell = shellFor(entity);
        if (shell == null) {
            return;
        }
        Class<?> base = shell.getSuperclass();
        for (Class<?> k = entity.getClass(); k != null && k != base; k = k.getSuperclass()) {
            for (Field field : k.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    System.err.println("swapTo 拒绝:" + entity.getClass().getName() + " -> " + shell.getName()
                            + " 布局不一致，中间类 " + k.getSimpleName() + " 声明了 " + field.getName());
                    return;
                }
            }
        }
        EntityUtil.setKlass(entity, shell);
        removeNow(entity);
    }


    public static void removeNow(Entity entity) {
        if (entity == null || entity.getRemovalReason() != null) {
            return;
        }
        entity.remove(Entity.RemovalReason.KILLED);
    }



    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {

    }




    @Override
    public boolean isRemoved() {
        return true;
    }

    @Override
    public boolean isAddedToLevel() {
        return false;
    }


    @Override
    public void setRemoved(Entity.RemovalReason removalReason) {
        Entity.RemovalReason reason = removalReason == null ? Entity.RemovalReason.KILLED : removalReason;
        if (this.removalReason == null) {
            this.removalReason = reason;
        }
        if (this.removalReason.shouldDestroy()) {
            this.stopRiding();
        }
        this.getPassengers().forEach(Entity::stopRiding);
        this.levelCallback.onRemove(reason);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.removalReason == null) {
            this.setRemoved(reason);
        }
    }

    @Override
    public void kill() {
        removeNow(this);
    }

    @Override
    public void tick() {
        removeNow(this);
    }

    @Override
    public void baseTick() {
        removeNow(this);
    }

    @Override
    public void rideTick() {
        removeNow(this);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        removeNow(this);
        return true;
    }

    @Override
    protected void unsetRemoved() {

    }

    @Override
    public void revive() {
        removeNow(this);
    }



    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void playerTouch(Player player) {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(Vec3 vector) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void setDeltaMovement(Vec3 deltaMovement) {
    }

    @Override
    public void setDeltaMovement(double x, double y, double z) {
    }

    @Override
    public void setPos(double x, double y, double z) {
    }

    @Override
    public void absMoveTo(double x, double y, double z, float yRot, float xRot) {
    }

    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
    }


    @Override
    public void setPosRaw(double x, double y, double z) {
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
    }

    @Override
    public void setPose(Pose pose) {
    }

    @Override
    public void animateHurt(float yaw) {
    }

    @Override
    public void handleEntityEvent(byte id) {
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
    }

    @Override
    public void playSound(SoundEvent sound) {
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent) {
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, @Nullable Entity source) {
    }

    @Override
    public void checkBelowWorld() {
    }

    @Override
    protected void handlePortal() {
    }

    @Override
    public void onClientRemoval() {
    }

    @Override
    public void onAddedToLevel() {
    }

    @Override
    public void onRemovedFromLevel() {
    }



    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public CompoundTag saveWithoutId(CompoundTag compound) {
        return compound;
    }

    @Override
    public void load(CompoundTag compound) {
    }

    @Override
    public void restoreFrom(Entity entity) {
    }

    @Override
    public void awardKillScore(Entity killed, int scoreValue, DamageSource source) {
    }

    @Override
    public boolean isAlwaysTicking() {
        return false;
    }
}
