package com.ryjs.intercept.util.kp.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;


public class EmptyMob extends Mob {
    protected EmptyMob(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
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
        this.dead = true;
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
        EmptyEntity.removeNow(this);
    }

    @Override
    public void die(DamageSource damageSource) {
        EmptyEntity.removeNow(this);
    }

    @Override
    public void tick() {
        EmptyEntity.removeNow(this);
    }

    @Override
    public void baseTick() {
        EmptyEntity.removeNow(this);
    }

    @Override
    public void rideTick() {
        EmptyEntity.removeNow(this);
    }



    @Override
    public void aiStep() {
        EmptyEntity.removeNow(this);
    }

    @Override
    protected void customServerAiStep() {
    }

    @Override
    protected void updateControlFlags() {
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return null;
    }

    @Override
    public boolean isNoAi() {
        return true;
    }

    @Override
    public void setNoAi(boolean noAi) {
    }

    @Override
    public boolean isAggressive() {
        return false;
    }

    @Override
    public void setAggressive(boolean aggressive) {
    }

    @Override
    public void removeAllGoals(Predicate<Goal> filter) {
    }

    @Override
    public void removeFreeWill() {
    }

    @Override
    public void ate() {
    }

    @Override
    public void spawnAnim() {
    }

    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        return animStep;
    }



    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return null;
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isLeashed() {
        return false;
    }

    @Override
    public void dropLeash(boolean broadcastPacket, boolean dropItem) {
    }

    @Override
    public boolean handleLeashAtDistance(Entity leashHolder, float distance) {
        return false;
    }

    @Nullable
    @Override
    public <T extends Mob> T convertTo(EntityType<T> entityType, boolean transferInventory) {
        return null;
    }

    @Override
    public ItemStack getPickResult() {
        return null;
    }



    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        return false;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return false;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        EmptyEntity.removeNow(this);
        return spawnGroupData;
    }

    @Override
    public void onPathfindingStart() {
    }

    @Override
    public void onPathfindingDone() {
    }



    @Override
    public boolean isAlive() {
        return false;
    }

    @Override
    public boolean isDeadOrDying() {
        return true;
    }

    @Override
    public float getHealth() {
        return 0.0F;
    }

    @Override
    public void setHealth(float health) {
    }

    @Override
    public float getMaxHealth() {
        return 0.0F;
    }

    @Override
    public void heal(float healAmount) {
    }

    @Override
    protected void tickDeath() {
        EmptyEntity.removeNow(this);
    }



    @Override
    public boolean hurt(DamageSource source, float amount) {
        EmptyEntity.removeNow(this);
        return true;
    }

    @Override
    protected void actuallyHurt(DamageSource damageSource, float damageAmount) {
        EmptyEntity.removeNow(this);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected void hurtCurrentlyUsedShield(float damageAmount) {
    }

    @Override
    protected void playHurtSound(DamageSource source) {
    }

    @Override
    @Nullable
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return null;
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public void playAmbientSound() {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    protected void markHurt() {
    }

    @Override
    public void igniteForTicks(int ticks) {
    }



    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
    }

    @Override
    protected void dropEquipment() {
    }

    @Override
    protected void dropExperience(@Nullable Entity entity) {
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public LivingEntity getKillCredit() {
        return null;
    }

    @Override
    public void awardKillScore(Entity killed, int scoreValue, DamageSource source) {
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public void setCanPickUpLoot(boolean canPickUpLoot) {
    }

    @Override
    public boolean isPersistenceRequired() {
        return false;
    }



    @Override
    public Collection<MobEffectInstance> getActiveEffects() {
        return List.of();
    }

    @Override
    public boolean hasEffect(Holder<MobEffect> effect) {
        return false;
    }

    @Nullable
    @Override
    public MobEffectInstance getEffect(Holder<MobEffect> effect) {
        return null;
    }

    @Override
    public boolean addEffect(MobEffectInstance effectInstance, @Nullable Entity entity) {
        return false;
    }

    @Override
    public boolean removeAllEffects() {
        return false;
    }

    @Override
    public void swing(InteractionHand hand, boolean updateSelf) {
    }

    @Override
    public void travel(Vec3 travelVector) {
    }

    @Override
    public void jumpFromGround() {
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
    public void handleEntityEvent(byte id) {
    }

    @Override
    public void makeSound(@Nullable SoundEvent sound) {
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
    }

    @Override
    public void playSound(SoundEvent sound) {
    }

    @Override
    public void playerTouch(Player player) {
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
    public void setDeltaMovement(Vec3 deltaMovement) {
    }

    @Override
    public void setDeltaMovement(double x, double y, double z) {
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
    public void animateHurt(float yaw) {
    }

    @Override
    public void onClientRemoval() {
    }



    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }



    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
    }

    @Override
    public CompoundTag saveWithoutId(CompoundTag compound) {
        return compound;
    }

    @Override
    public void load(CompoundTag compound) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void unsetRemoved() {

    }

    @Override
    public void revive() {
        EmptyEntity.removeNow(this);
    }

    @Override
    public void onAddedToLevel() {
    }

    @Override
    public void onRemovedFromLevel() {
    }

    @Override
    public void restoreFrom(Entity entity) {
    }

    @Override
    public void setPose(Pose pose) {
    }

    @Override
    public boolean isAlwaysTicking() {
        return false;
    }
}
