package com.ryjs.intercept.util.kp.entity;

import com.ryjs.intercept.service.agent.AgentChain;
import com.ryjs.intercept.service.agent.DeathShellTransformer;
import com.ryjs.intercept.service.agent.HiddenRetrans;
import com.ryjs.intercept.util.agent.AgentBackend;
import com.ryjs.intercept.util.kp.EntityUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public final class DeathShellFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeathShellFactory.class);


    public enum Depth {
        ENTITY, LIVING, MOB, MONSTER, PLAYER
    }


    private enum Act {

        NOTHING,

        DOOM,

        DOOM_TRUE,

        SET_REMOVED,

        SUPER,
        TRUE,
        FALSE,
        ZERO_F,
        ZERO_I,
        NULL,
        EMPTY_ITERABLE,
        EMPTY_COLLECTION,
        ITEMSTACK_EMPTY,
        ARM_RIGHT,
        PASS,

        ARG1,

        ARG4
    }

    private record Row(String name, String desc, Act act, Depth min) {
    }

    private record Cached(@Nullable Class<? extends Entity> shell, boolean hidden, int overrides, String note) {

        Cached(@Nullable Class<? extends Entity> shell, int overrides, String note) {
            this(shell, false, overrides, note);
        }
    }

    private static final String OURS = "com/ryjs/intercept/util/kp/entity/DeathShellFactory";
    private static final String SHELLS_PKG = "com/ryjs/intercept/util/kp/entity/";
    private static final String ENTITY_DESC = "Lnet/minecraft/world/entity/Entity;";
    private static final String REMOVAL_DESC = "Lnet/minecraft/world/entity/Entity$RemovalReason;";


    private static final Row[] ROWS = {

            new Row("tick", "()V", Act.DOOM, Depth.ENTITY),
            new Row("baseTick", "()V", Act.DOOM, Depth.ENTITY),
            new Row("rideTick", "()V", Act.DOOM, Depth.ENTITY),
            new Row("kill", "()V", Act.DOOM, Depth.ENTITY),
            new Row("revive", "()V", Act.DOOM, Depth.ENTITY),
            new Row("remove", "(" + REMOVAL_DESC + ")V", Act.SET_REMOVED, Depth.ENTITY),
            new Row("setRemoved", "(" + REMOVAL_DESC + ")V", Act.SET_REMOVED, Depth.ENTITY),
            new Row("isRemoved", "()Z", Act.TRUE, Depth.ENTITY),
            new Row("isAddedToLevel", "()Z", Act.FALSE, Depth.ENTITY),
            new Row("onAddedToLevel", "()V", Act.NOTHING, Depth.ENTITY),
            new Row("onRemovedFromLevel", "()V", Act.NOTHING, Depth.ENTITY),
            new Row("unsetRemoved", "()V", Act.NOTHING, Depth.ENTITY),
            new Row("hurt", "(Lnet/minecraft/world/damagesource/DamageSource;F)Z", Act.DOOM_TRUE, Depth.ENTITY),
            new Row("playerTouch", "(Lnet/minecraft/world/entity/player/Player;)V", Act.NOTHING, Depth.ENTITY),
            new Row("push", "(" + ENTITY_DESC + ")V", Act.NOTHING, Depth.ENTITY),
            new Row("push", "(Lnet/minecraft/world/phys/Vec3;)V", Act.NOTHING, Depth.ENTITY),
            new Row("push", "(DDD)V", Act.NOTHING, Depth.ENTITY),
            new Row("setDeltaMovement", "(Lnet/minecraft/world/phys/Vec3;)V", Act.NOTHING, Depth.ENTITY),
            new Row("setDeltaMovement", "(DDD)V", Act.NOTHING, Depth.ENTITY),
            new Row("setPos", "(DDD)V", Act.NOTHING, Depth.ENTITY),
            new Row("setPosRaw", "(DDD)V", Act.NOTHING, Depth.ENTITY),
            new Row("absMoveTo", "(DDDFF)V", Act.NOTHING, Depth.ENTITY),
            new Row("moveTo", "(DDDFF)V", Act.NOTHING, Depth.ENTITY),
            new Row("lerpTo", "(DDDFFI)V", Act.NOTHING, Depth.ENTITY),
            new Row("setPose", "(Lnet/minecraft/world/entity/Pose;)V", Act.NOTHING, Depth.ENTITY),
            new Row("animateHurt", "(F)V", Act.NOTHING, Depth.ENTITY),
            new Row("handleEntityEvent", "(B)V", Act.NOTHING, Depth.ENTITY),
            new Row("playSound", "(Lnet/minecraft/sounds/SoundEvent;FF)V", Act.NOTHING, Depth.ENTITY),
            new Row("playSound", "(Lnet/minecraft/sounds/SoundEvent;)V", Act.NOTHING, Depth.ENTITY),
            new Row("gameEvent", "(Lnet/minecraft/core/Holder;)V", Act.NOTHING, Depth.ENTITY),
            new Row("gameEvent", "(Lnet/minecraft/core/Holder;" + ENTITY_DESC + ")V", Act.NOTHING, Depth.ENTITY),
            new Row("checkBelowWorld", "()V", Act.NOTHING, Depth.ENTITY),
            new Row("handlePortal", "()V", Act.NOTHING, Depth.ENTITY),
            new Row("onClientRemoval", "()V", Act.NOTHING, Depth.ENTITY),
            new Row("igniteForTicks", "(I)V", Act.NOTHING, Depth.ENTITY),
            new Row("startRiding", "(" + ENTITY_DESC + "Z)Z", Act.FALSE, Depth.ENTITY),
            new Row("interact", "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;", Act.PASS, Depth.ENTITY),
            new Row("interactAt", "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;", Act.PASS, Depth.ENTITY),
            new Row("causeFallDamage", "(FFLnet/minecraft/world/damagesource/DamageSource;)Z", Act.FALSE, Depth.ENTITY),
            new Row("isPushable", "()Z", Act.FALSE, Depth.ENTITY),
            new Row("isPickable", "()Z", Act.FALSE, Depth.ENTITY),
            new Row("isAlwaysTicking", "()Z", Act.FALSE, Depth.ENTITY),
            new Row("shouldBeSaved", "()Z", Act.FALSE, Depth.ENTITY),
            new Row("saveWithoutId", "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", Act.ARG1, Depth.ENTITY),
            new Row("load", "(Lnet/minecraft/nbt/CompoundTag;)V", Act.NOTHING, Depth.ENTITY),
            new Row("addAdditionalSaveData", "(Lnet/minecraft/nbt/CompoundTag;)V", Act.NOTHING, Depth.ENTITY),
            new Row("readAdditionalSaveData", "(Lnet/minecraft/nbt/CompoundTag;)V", Act.NOTHING, Depth.ENTITY),
            new Row("restoreFrom", "(" + ENTITY_DESC + ")V", Act.NOTHING, Depth.ENTITY),
            new Row("awardKillScore", "(" + ENTITY_DESC + "ILnet/minecraft/world/damagesource/DamageSource;)V", Act.NOTHING, Depth.ENTITY),
            new Row("markHurt", "()V", Act.NOTHING, Depth.ENTITY),


            new Row("die", "(Lnet/minecraft/world/damagesource/DamageSource;)V", Act.DOOM, Depth.LIVING),
            new Row("actuallyHurt", "(Lnet/minecraft/world/damagesource/DamageSource;F)V", Act.DOOM, Depth.LIVING),
            new Row("tickDeath", "()V", Act.DOOM, Depth.LIVING),
            new Row("aiStep", "()V", Act.DOOM, Depth.LIVING),
            new Row("isAlive", "()Z", Act.FALSE, Depth.LIVING),
            new Row("isDeadOrDying", "()Z", Act.TRUE, Depth.LIVING),
            new Row("getHealth", "()F", Act.ZERO_F, Depth.LIVING),
            new Row("setHealth", "(F)V", Act.NOTHING, Depth.LIVING),
            new Row("getMaxHealth", "()F", Act.ZERO_F, Depth.LIVING),
            new Row("heal", "(F)V", Act.NOTHING, Depth.LIVING),
            new Row("travel", "(Lnet/minecraft/world/phys/Vec3;)V", Act.NOTHING, Depth.LIVING),
            new Row("jumpFromGround", "()V", Act.NOTHING, Depth.LIVING),
            new Row("knockback", "(DDD)V", Act.NOTHING, Depth.LIVING),
            new Row("swing", "(Lnet/minecraft/world/InteractionHand;Z)V", Act.NOTHING, Depth.LIVING),
            new Row("checkFallDamage", "(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V", Act.NOTHING, Depth.LIVING),
            new Row("hurtCurrentlyUsedShield", "(F)V", Act.NOTHING, Depth.LIVING),
            new Row("playHurtSound", "(Lnet/minecraft/world/damagesource/DamageSource;)V", Act.NOTHING, Depth.LIVING),
            new Row("getHurtSound", "(Lnet/minecraft/world/damagesource/DamageSource;)Lnet/minecraft/sounds/SoundEvent;", Act.NULL, Depth.LIVING),
            new Row("getDeathSound", "()Lnet/minecraft/sounds/SoundEvent;", Act.NULL, Depth.LIVING),
            new Row("makeSound", "(Lnet/minecraft/sounds/SoundEvent;)V", Act.NOTHING, Depth.LIVING),
            new Row("dropAllDeathLoot", "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", Act.NOTHING, Depth.LIVING),
            new Row("dropCustomDeathLoot", "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V", Act.NOTHING, Depth.LIVING),
            new Row("dropEquipment", "()V", Act.NOTHING, Depth.LIVING),
            new Row("dropExperience", "(" + ENTITY_DESC + ")V", Act.NOTHING, Depth.LIVING),
            new Row("shouldDropLoot", "()Z", Act.FALSE, Depth.LIVING),
            new Row("shouldDropExperience", "()Z", Act.FALSE, Depth.LIVING),
            new Row("getKillCredit", "()Lnet/minecraft/world/entity/LivingEntity;", Act.NULL, Depth.LIVING),
            new Row("getActiveEffects", "()Ljava/util/Collection;", Act.EMPTY_COLLECTION, Depth.LIVING),
            new Row("hasEffect", "(Lnet/minecraft/core/Holder;)Z", Act.FALSE, Depth.LIVING),
            new Row("getEffect", "(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/effect/MobEffectInstance;", Act.NULL, Depth.LIVING),
            new Row("addEffect", "(Lnet/minecraft/world/effect/MobEffectInstance;" + ENTITY_DESC + ")Z", Act.FALSE, Depth.LIVING),
            new Row("removeAllEffects", "()Z", Act.FALSE, Depth.LIVING),
            new Row("doHurtTarget", "(" + ENTITY_DESC + ")Z", Act.FALSE, Depth.LIVING),
            new Row("getArmorSlots", "()Ljava/lang/Iterable;", Act.EMPTY_ITERABLE, Depth.LIVING),
            new Row("getItemBySlot", "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;", Act.ITEMSTACK_EMPTY, Depth.LIVING),
            new Row("setItemSlot", "(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V", Act.NOTHING, Depth.LIVING),
            new Row("getMainArm", "()Lnet/minecraft/world/entity/HumanoidArm;", Act.ARM_RIGHT, Depth.LIVING),


            new Row("setTarget", "(Lnet/minecraft/world/entity/LivingEntity;)V", Act.NOTHING, Depth.MOB),
            new Row("getTarget", "()Lnet/minecraft/world/entity/LivingEntity;", Act.NULL, Depth.MOB),
            new Row("isNoAi", "()Z", Act.TRUE, Depth.MOB),
            new Row("setNoAi", "(Z)V", Act.NOTHING, Depth.MOB),
            new Row("isAggressive", "()Z", Act.FALSE, Depth.MOB),
            new Row("setAggressive", "(Z)V", Act.NOTHING, Depth.MOB),
            new Row("customServerAiStep", "()V", Act.NOTHING, Depth.MOB),
            new Row("updateControlFlags", "()V", Act.NOTHING, Depth.MOB),
            new Row("tickHeadTurn", "(FF)F", Act.ZERO_F, Depth.MOB),
            new Row("removeAllGoals", "(Ljava/util/function/Predicate;)V", Act.NOTHING, Depth.MOB),
            new Row("removeFreeWill", "()V", Act.NOTHING, Depth.MOB),
            new Row("onPathfindingStart", "()V", Act.NOTHING, Depth.MOB),
            new Row("onPathfindingDone", "()V", Act.NOTHING, Depth.MOB),
            new Row("playAmbientSound", "()V", Act.NOTHING, Depth.MOB),
            new Row("getAmbientSound", "()Lnet/minecraft/sounds/SoundEvent;", Act.NULL, Depth.MOB),
            new Row("mobInteract", "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;", Act.PASS, Depth.MOB),
            new Row("canBeLeashed", "()Z", Act.FALSE, Depth.MOB),
            new Row("isLeashed", "()Z", Act.FALSE, Depth.MOB),
            new Row("dropLeash", "(ZZ)V", Act.NOTHING, Depth.MOB),
            new Row("handleLeashAtDistance", "(" + ENTITY_DESC + "F)Z", Act.FALSE, Depth.MOB),
            new Row("convertTo", "(Lnet/minecraft/world/entity/EntityType;Z)Lnet/minecraft/world/entity/Mob;", Act.NULL, Depth.MOB),
            new Row("removeWhenFarAway", "(D)Z", Act.TRUE, Depth.MOB),
            new Row("shouldDespawnInPeaceful", "()Z", Act.TRUE, Depth.MOB),
            new Row("checkSpawnRules", "(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/MobSpawnType;)Z", Act.FALSE, Depth.MOB),
            new Row("checkSpawnObstruction", "(Lnet/minecraft/world/level/LevelReader;)Z", Act.FALSE, Depth.MOB),
            new Row("finalizeSpawn", "(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;", Act.ARG4, Depth.MOB),
            new Row("canPickUpLoot", "()Z", Act.FALSE, Depth.MOB),
            new Row("setCanPickUpLoot", "(Z)V", Act.NOTHING, Depth.MOB),
            new Row("isPersistenceRequired", "()Z", Act.FALSE, Depth.MOB),
            new Row("getControllingPassenger", "()Lnet/minecraft/world/entity/LivingEntity;", Act.NULL, Depth.MOB),
            new Row("canAttackType", "(Lnet/minecraft/world/entity/EntityType;)Z", Act.FALSE, Depth.MOB),
            new Row("getPickResult", "()Lnet/minecraft/world/item/ItemStack;", Act.NULL, Depth.MOB),
            new Row("ate", "()V", Act.NOTHING, Depth.MOB),
            new Row("spawnAnim", "()V", Act.NOTHING, Depth.MOB),


            new Row("getWalkTargetValue", "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/LevelReader;)F", Act.ZERO_F, Depth.MONSTER),
            new Row("getProjectile", "(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", Act.ITEMSTACK_EMPTY, Depth.MONSTER),
            new Row("isPreventingPlayerRest", "(Lnet/minecraft/world/entity/player/Player;)Z", Act.FALSE, Depth.MONSTER),
            new Row("canFireProjectileWeapon", "(Lnet/minecraft/world/item/ProjectileWeaponItem;)Z", Act.FALSE, Depth.MONSTER),
            new Row("updateNoActionTime", "()V", Act.NOTHING, Depth.MONSTER),
    };

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();


    private static final Row[] PLAYER_ROWS = {
            new Row("getHealth", "()F", Act.ZERO_F, Depth.PLAYER),
            new Row("getMaxHealth", "()F", Act.ZERO_F, Depth.PLAYER),
            new Row("setHealth", "(F)V", Act.NOTHING, Depth.PLAYER),
            new Row("heal", "(F)V", Act.NOTHING, Depth.PLAYER),
            new Row("isAlive", "()Z", Act.FALSE, Depth.PLAYER),
            new Row("isDeadOrDying", "()Z", Act.TRUE, Depth.PLAYER),
            new Row("tickDeath", "()V", Act.NOTHING, Depth.PLAYER),
            new Row("hurt", "(Lnet/minecraft/world/damagesource/DamageSource;F)Z", Act.TRUE, Depth.PLAYER),
            new Row("actuallyHurt", "(Lnet/minecraft/world/damagesource/DamageSource;F)V", Act.NOTHING, Depth.PLAYER),
            new Row("die", "(Lnet/minecraft/world/damagesource/DamageSource;)V", Act.SUPER, Depth.PLAYER),
            new Row("causeFallDamage", "(FFLnet/minecraft/world/damagesource/DamageSource;)Z", Act.FALSE, Depth.PLAYER),
            new Row("knockback", "(DDD)V", Act.NOTHING, Depth.PLAYER),
            new Row("igniteForTicks", "(I)V", Act.NOTHING, Depth.PLAYER),
            new Row("revive", "()V", Act.NOTHING, Depth.PLAYER),
            new Row("unsetRemoved", "()V", Act.NOTHING, Depth.PLAYER),
            new Row("awardKillScore", "(" + ENTITY_DESC + "ILnet/minecraft/world/damagesource/DamageSource;)V", Act.NOTHING, Depth.PLAYER),
    };

    private static Row[] rows(Depth depth) {
        return depth == Depth.PLAYER ? PLAYER_ROWS : ROWS;
    }

    private DeathShellFactory() {
    }


    public static Cached shellFor(Class<?> target, Depth depth) {
        String key = target.getName() + "#" + depth;
        Cached hit = CACHE.get(key);
        if (hit != null) {
            return hit;
        }
        Cached made = build(target, depth);
        CACHE.put(key, made);
        return made;
    }


    public static String applyTo(Entity entity, Depth depth) throws Throwable {
        return applyTo(entity, depth, true);
    }


    public static String applyTo(Entity entity, Depth depth, boolean patchClass) throws Throwable {
        if (entity instanceof Player && depth != Depth.PLAYER) {
            throw new IllegalStateException(entity.getClass().getName() + " 是玩家，只能用 PLAYER 层");
        }
        Class<?> target = entity.getClass();
        String oldName = target.getName();

        String patchLine = patchClass ? patchClassInPlace(target, depth) : "类级补丁:由调用方完成";

        Cached cached = shellFor(target, depth);
        if (cached.shell() == null) {
            throw new IllegalStateException(patchLine + " | 生成失败：" + cached.note());
        }
        Class<? extends Entity> shell = cached.shell();
        swapKlass(entity, shell, cached.hidden());
        if (depth != Depth.PLAYER) {
            EmptyEntity.removeNow(entity);
        }
        return patchLine + " | " + oldName + " -> " + HiddenRetrans.hiddenName(shell)
                + (cached.hidden() ? "(隐藏类)" : "(普通类)")
                + " 覆盖 " + cached.overrides() + " 个方法"
                + (depth == Depth.PLAYER
                ? " health=" + ((LivingEntity) entity).getHealth() + " dying=" + ((LivingEntity) entity).isDeadOrDying()
                : " removalReason=" + entity.getRemovalReason());
    }


    private static String patchClassInPlace(Class<?> target, Depth depth) {

        if (AgentBackend.instrumentation() == null) {
            AgentBackend.attach();
        }
        java.lang.instrument.Instrumentation inst = AgentBackend.instrumentation();
        if (inst == null) {
            return "类级补丁跳过(" + AgentBackend.note() + ")";
        }
        if (!inst.isModifiableClass(target)) {
            return "类级补丁跳过(" + target.getName() + " 不可重定义)";
        }
        DeathShellTransformer.install(inst);
        if (DeathShellTransformer.isPatched(target)) {

            return "类级补丁已完成:" + target.getName();
        }
        DeathShellTransformer.want(target, depth);
        return String.join(";", DeathShellTransformer.patchNow(inst, target));
    }



    private static Cached build(Class<?> target, Depth depth) {
        if (!Entity.class.isAssignableFrom(target) || target == Entity.class) {
            return new Cached(null, 0, target.getName() + " 不是可替换的具体 Entity 子类");
        }
        if (Modifier.isFinal(target.getModifiers())) {
            return new Cached(null, 0, target.getName() + " 是 final 类，无法继承");
        }
        if (Modifier.isAbstract(target.getModifiers())) {
            return new Cached(null, 0, target.getName() + " 是 abstract 类，不会有这个 klass 的实例");
        }

        Map<String, Method> picked = new LinkedHashMap<>();
        for (Row row : activeRows(depth)) {
            Method m = findOverridable(target, row.name(), row.desc());
            if (m != null) {
                picked.put(row.name() + row.desc(), m);
            }
        }
        if (picked.isEmpty()) {
            return new Cached(null, 0, target.getName() + " 在 " + depth + " 层级上没匹配到任何可覆盖方法");
        }

        String internal = target.getName().replace('.', '/');
        String name = SHELLS_PKG + "DeathShell$" + target.getSimpleName() + "$" + depth;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    ClassLoader cl = target.getClassLoader() == null
                            ? ClassLoader.getSystemClassLoader() : target.getClassLoader();
                    Class<?> c1 = Class.forName(type1.replace('/', '.'), false, cl);
                    Class<?> c2 = Class.forName(type2.replace('/', '.'), false, cl);
                    if (c1.isAssignableFrom(c2)) {
                        return type1;
                    }
                    if (c2.isAssignableFrom(c1)) {
                        return type2;
                    }
                } catch (Throwable ignored) {

                }
                return super.getCommonSuperClass(type1, type2);
            }
        };
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, internal, null);



        if (hasEntityCtor(target)) {
            emitCtor(cw, internal);
        }

        for (Row row : activeRows(depth)) {
            Method m = picked.get(row.name() + row.desc());
            if (m != null) {
                emitOverride(cw, row, m, internal);
            }
        }
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        try {
            Class<?> shell = MethodHandles.lookup()
                    .defineHiddenClass(bytes, true, MethodHandles.Lookup.ClassOption.STRONG)
                    .lookupClass();
            if (!Entity.class.isAssignableFrom(shell)) {
                return new Cached(null, 0, "生成类不是 Entity 子类");
            }
            LOGGER.info("生成隐藏死亡壳:{} extends {} 覆盖 {} 个方法", name, target.getName(), picked.size());
            return new Cached(asShell(shell), true, picked.size(), "ok");
        } catch (Throwable hiddenFailed) {


            try {
                Class<?> shell = SHELL_LOADER.load(name.replace('/', '.'), bytes);
                if (!Entity.class.isAssignableFrom(shell)) {
                    return new Cached(null, 0, "生成类不是 Entity 子类");
                }
                LOGGER.info("生成普通死亡壳(非隐藏):{} extends {} 覆盖 {} 个方法；隐藏类失败:{}",
                        name, target.getName(), picked.size(), String.valueOf(hiddenFailed));
                return new Cached(asShell(shell), false, picked.size(), "普通类回退:" + hiddenFailed);
            } catch (Throwable normalFailed) {
                LOGGER.warn("生成死亡壳失败:{} # {}", target.getName(), depth, normalFailed);
                return new Cached(null, 0, hiddenFailed + " / " + normalFailed);
            }
        }
    }


    private static final class ShellLoader extends ClassLoader {
        ShellLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> load(String binaryName, byte[] bytes) {
            return defineClass(binaryName, bytes, 0, bytes.length);
        }
    }

    private static final ShellLoader SHELL_LOADER = new ShellLoader(DeathShellFactory.class.getClassLoader());

    @SuppressWarnings("unchecked")
    private static Class<? extends Entity> asShell(Class<?> c) {
        return (Class<? extends Entity>) c;
    }


    private static boolean hasEntityCtor(Class<?> target) {
        try {
            target.getDeclaredConstructor(EntityType.class, Level.class);
            return true;
        } catch (Throwable noSuchCtor) {
            return false;
        }
    }

    private static void emitCtor(ClassWriter cw, String internal) {
        String desc = "(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V";
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", desc, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, internal, "<init>", desc, false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }


    @Nullable
    private static Method findOverridable(Class<?> target, String name, String desc) {
        for (Class<?> k = target; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals(name) || !descriptor(m).equals(desc)) {
                    continue;
                }
                int acc = m.getModifiers();
                if (Modifier.isStatic(acc) || Modifier.isFinal(acc)
                        || !(Modifier.isPublic(acc) || Modifier.isProtected(acc))
                        || m.isSynthetic() || m.isBridge()) {
                    return null;
                }
                return m;
            }
        }
        return null;
    }

    private static String descriptor(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) {
            sb.append(type(p));
        }
        return sb.append(')').append(type(m.getReturnType())).toString();
    }

    private static String type(Class<?> c) {
        if (c == void.class) return "V";
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        if (c.isArray()) return "[" + type(c.getComponentType());
        return "L" + c.getName().replace('.', '/') + ";";
    }

    private static void emitOverride(ClassWriter cw, Row row, Method parent, String parentInternal) {
        int access = Modifier.isPublic(parent.getModifiers()) ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PROTECTED;
        MethodVisitor mv = cw.visitMethod(access, row.name(), row.desc(), null, null);
        mv.visitCode();
        emitBody(mv, row, parentInternal, row.name());
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }


    private static void emitBody(MethodVisitor mv, Row row, String superOwner, String superName) {
        char ret = row.desc().charAt(row.desc().indexOf(')') + 1);
        switch (row.act()) {
            case SUPER -> {

                mv.visitVarInsn(Opcodes.ALOAD, 0);
                emitArgLoads(mv, row.desc());
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superOwner, superName, row.desc(), false);
                retZero(mv, ret);
            }
            case SET_REMOVED -> {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, OURS, "setRemovedNow",
                        "(" + ENTITY_DESC + REMOVAL_DESC + ")V", false);
                mv.visitInsn(Opcodes.RETURN);
            }
            case DOOM -> {
                callRemoveNow(mv);
                retZero(mv, ret);
            }
            case DOOM_TRUE -> {
                callRemoveNow(mv);
                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitInsn(Opcodes.IRETURN);
            }
            case NOTHING -> retZero(mv, ret);
            case TRUE -> {
                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitInsn(Opcodes.IRETURN);
            }
            case FALSE -> {
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitInsn(Opcodes.IRETURN);
            }
            case ZERO_I -> {
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitInsn(Opcodes.IRETURN);
            }
            case ZERO_F -> {
                mv.visitInsn(Opcodes.FCONST_0);
                mv.visitInsn(Opcodes.FRETURN);
            }
            case NULL -> {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
            }
            case EMPTY_ITERABLE, EMPTY_COLLECTION -> {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/List", "of", "()Ljava/util/List;", false);
                mv.visitInsn(Opcodes.ARETURN);
            }
            case ITEMSTACK_EMPTY -> {
                mv.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/item/ItemStack", "EMPTY",
                        "Lnet/minecraft/world/item/ItemStack;");
                mv.visitInsn(Opcodes.ARETURN);
            }
            case ARM_RIGHT -> {
                mv.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/entity/HumanoidArm", "RIGHT",
                        "Lnet/minecraft/world/entity/HumanoidArm;");
                mv.visitInsn(Opcodes.ARETURN);
            }
            case PASS -> {
                mv.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/InteractionResult", "PASS",
                        "Lnet/minecraft/world/InteractionResult;");
                mv.visitInsn(Opcodes.ARETURN);
            }
            case ARG1 -> {
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitInsn(Opcodes.ARETURN);
            }
            case ARG4 -> {
                mv.visitVarInsn(Opcodes.ALOAD, 4);
                mv.visitInsn(Opcodes.ARETURN);
            }
        }
    }

    private static void callRemoveNow(MethodVisitor mv) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/ryjs/intercept/util/kp/entity/EmptyEntity", "removeNow",
                "(" + ENTITY_DESC + ")V", false);
    }


    private static void emitArgLoads(MethodVisitor mv, String desc) {
        int slot = 1;
        for (int i = 1, end = desc.indexOf(')'); i < end; ) {
            char c = desc.charAt(i);
            if (c == 'L') {
                mv.visitVarInsn(Opcodes.ALOAD, slot++);
                while (desc.charAt(i++) != ';') {

                }
            } else if (c == 'J' || c == 'D') {
                mv.visitVarInsn(c == 'J' ? Opcodes.LLOAD : Opcodes.DLOAD, slot);
                slot += 2;
                i++;
            } else {
                mv.visitVarInsn(Opcodes.ILOAD, slot++);
                i++;
            }
        }
    }


    private static void retZero(MethodVisitor mv, char ret) {
        switch (ret) {
            case 'V' -> mv.visitInsn(Opcodes.RETURN);
            case 'Z', 'B', 'C', 'S', 'I' -> {
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitInsn(Opcodes.IRETURN);
            }
            case 'F' -> {
                mv.visitInsn(Opcodes.FCONST_0);
                mv.visitInsn(Opcodes.FRETURN);
            }
            case 'D' -> {
                mv.visitInsn(Opcodes.DCONST_0);
                mv.visitInsn(Opcodes.DRETURN);
            }
            case 'J' -> {
                mv.visitInsn(Opcodes.LCONST_0);
                mv.visitInsn(Opcodes.LRETURN);
            }
            default -> {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
            }
        }
    }




    private static List<Row> activeRows(Depth depth) {
        LinkedHashMap<String, Row> out = new LinkedHashMap<>();
        for (Row row : rows(depth)) {
            if (depth != Depth.PLAYER && row.min().ordinal() > depth.ordinal()) {
                continue;
            }
            out.putIfAbsent(row.name() + row.desc(), row);
        }
        return new ArrayList<>(out.values());
    }


    public static byte[] patch(byte[] input, Depth depth) {
        List<Row> active = activeRows(depth);
        if (active.isEmpty()) {
            return null;
        }
        ClassReader cr = new ClassReader(input);
        String superInternal = cr.getSuperName();
        Map<String, Row> byKey = new HashMap<>();
        for (Row row : active) {
            byKey.put(row.name() + row.desc(), row);
        }
        Set<String> replaceable = new HashSet<>();
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {

                if (byKey.containsKey(name + desc) && !Modifier.isStatic(access)
                        && !Modifier.isAbstract(access) && !Modifier.isNative(access)) {
                    replaceable.add(name + desc);
                }
                return null;
            }
        }, 0);
        if (replaceable.isEmpty()) {
            return null;
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                Row row = byKey.get(name + desc);
                if (row == null || !replaceable.contains(name + desc)) {
                    return super.visitMethod(access, name, desc, sig, exceptions);
                }
                MethodVisitor mine = super.visitMethod(access, name, desc, sig, exceptions);
                mine.visitCode();

                emitBody(mine, row, superInternal == null ? "java/lang/Object" : superInternal, name);
                mine.visitMaxs(0, 0);
                mine.visitEnd();





                return new MethodVisitor(Opcodes.ASM9) {
                };
            }
        }, 0);
        return cw.toByteArray();
    }


    public static int replaceableCount(Class<?> target, Depth depth) {
        Map<String, Row> byKey = new HashMap<>();
        for (Row row : activeRows(depth)) {
            byKey.put(row.name() + row.desc(), row);
        }
        int n = 0;
        for (Class<?> k = target; k != null && k != Object.class; k = k.getSuperclass()) {
            if (k != target) {
                break;
            }
            for (Method m : k.getDeclaredMethods()) {
                int mod = m.getModifiers();
                if (!Modifier.isStatic(mod) && !Modifier.isAbstract(mod) && !Modifier.isNative(mod)
                        && byKey.containsKey(m.getName() + descriptor(m))) {
                    n++;
                }
            }
        }
        return n;
    }




    private static void swapKlass(Entity target, Class<?> shell, boolean hidden) throws Throwable {
        if (target.getClass() == shell) {
            return;
        }
        if (!hidden) {


            EntityUtil.setKlass(target, shell);
            return;
        }
        int narrowK = HiddenRetrans.narrowKlass(shell, target);
        Object[] holder = new Object[]{target};
        int arrBase = (int) unsafeCall("arrayBaseOffset", new Class<?>[]{Class.class}, new Object[]{Object[].class});
        int compressed = (int) unsafeCall("getInt", new Class<?>[]{Object.class, long.class},
                new Object[]{holder, (long) arrBase});
        long addr = (((long) compressed) & 0xFFFFFFFFL) << 3;
        if (addr < 0x100000000L || addr >= 0x800000000L) {
            throw new IllegalStateException("目标地址超出堆范围: 0x" + Long.toHexString(addr));
        }
        long markByAddr = (long) unsafeCall("getLong", new Class<?>[]{long.class}, new Object[]{addr});
        long markByObj = (long) unsafeCall("getLong", new Class<?>[]{Object.class, long.class},
                new Object[]{target, 0L});
        if (markByAddr != markByObj) {
            throw new IllegalStateException("对象地址解码失败（heap base 非 0？）");
        }
        int[] buf = new int[]{narrowK};
        int bufBase = (int) unsafeCall("arrayBaseOffset", new Class<?>[]{Class.class}, new Object[]{int[].class});
        unsafeCall("copyMemory", new Class<?>[]{Object.class, long.class, Object.class, long.class, long.class},
                new Object[]{buf, (long) bufBase, null, addr + 8L, 4L});
        int verify = (int) unsafeCall("getInt", new Class<?>[]{Object.class, long.class}, new Object[]{target, 8L});
        if (verify != narrowK) {
            throw new IllegalStateException("写后校验失败: 写入=0x" + Integer.toHexString(narrowK)
                    + " 读回=0x" + Integer.toHexString(verify));
        }
    }




    public static void setRemovedNow(Entity self, Entity.RemovalReason reason) {
        Entity.RemovalReason effective = reason == null ? Entity.RemovalReason.KILLED : reason;
        if (self.removalReason == null) {
            self.removalReason = effective;
        }
        if (self.removalReason.shouldDestroy()) {
            self.stopRiding();
        }
        self.getPassengers().forEach(Entity::stopRiding);
        self.levelCallback.onRemove(effective);
    }



    private static volatile Object UNSAFE;

    private static Object unsafe() throws Throwable {
        Object u = UNSAFE;
        if (u != null) {
            return u;
        }
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        u = theUnsafe.get(null);
        UNSAFE = u;
        return u;
    }

    private static Object unsafeCall(String name, Class<?>[] paramTypes, Object[] args) throws Throwable {
        return Class.forName("sun.misc.Unsafe").getMethod(name, paramTypes).invoke(unsafe(), args);
    }
}
