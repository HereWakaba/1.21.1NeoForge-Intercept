package com.ryjs.intercept.util.kp;

import com.ryjs.intercept.util.kp.getter.*;
import com.ryjs.intercept.util.kp.level.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class EntityUtil {

    public static final List<LivingEntity> protectList = new ArrayList<>();
    public static final Map<
            Integer, EntityInstance<? extends LivingEntity>> INSTANCES = new ConcurrentHashMap<>();

    public static final Object LOOKUP = getLookup();

    private static final HashSet<String> DEATH_SET = new HashSet<>();
    private static final HashSet<String> LIVING_SET = new HashSet<>();
    public static boolean fuckEntity = true;

    private static Object getUnsafe() {
        try {
            Constructor<?> c = Class.forName("sun.misc.Unsafe").getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("获取 sun.misc.Unsafe构造器失败Class.forName/getDeclaredConstructor/setAccessible/newInstance 其中一步被拦", e);
        }
    }

    private static Object getLookup() {
        try {
            Class<?> rfClass = Class.forName("sun.reflect.ReflectionFactory");
            Class<?> lookupClass = Class.forName("java.lang.invoke.MethodHandles$Lookup");
            Object factory = rfClass.getMethod("getReflectionFactory").invoke(null);
            Object serialCtor = rfClass.getMethod("newConstructorForSerialization",
                            Class.class, Constructor.class)
                    .invoke(factory, lookupClass,
                            lookupClass.getDeclaredConstructor(Class.class, Class.class, int.class));
            return ((Constructor<?>) serialCtor).newInstance(Object.class, null, -1);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("获取RUSTEDLookup失败ReflectionFactory.getReflectionFactory/newConstructorForSerialization/newInstance其中一步被拦", e);
        }
    }

    static final Object UNSAFE;

    private static final Method U_ALLOCATE_INSTANCE;
    private static final Method U_GET_INT;
    private static final Method U_GET_LONG;
    private static final Method U_GET_LONG_ADDR;
    private static final Method U_ARRAY_BASE_OFFSET;
    private static final Method U_COPY_MEMORY;
    private static final Method U_STATIC_FIELD_BASE;
    private static final Method U_STATIC_FIELD_OFFSET;
    private static final Method U_OBJECT_FIELD_OFFSET;
    private static final Method U_GET_INT_VOLATILE;
    private static final Method U_PUT_INT_VOLATILE;
    private static final Method U_GET_LONG_VOLATILE;
    private static final Method U_PUT_LONG_VOLATILE;
    private static final Method U_GET_BOOLEAN_VOLATILE;
    private static final Method U_PUT_BOOLEAN_VOLATILE;
    private static final Method U_GET_BYTE_VOLATILE;
    private static final Method U_PUT_BYTE_VOLATILE;
    private static final Method U_GET_CHAR_VOLATILE;
    private static final Method U_PUT_CHAR_VOLATILE;
    private static final Method U_GET_SHORT_VOLATILE;
    private static final Method U_PUT_SHORT_VOLATILE;
    private static final Method U_GET_FLOAT_VOLATILE;
    private static final Method U_PUT_FLOAT_VOLATILE;
    private static final Method U_GET_DOUBLE_VOLATILE;
    private static final Method U_PUT_DOUBLE_VOLATILE;
    private static final Method U_GET_OBJECT_VOLATILE;
    private static final Method U_PUT_OBJECT_VOLATILE;
    private static final Method L_UNREFLECT_GETTER;
    private static final Method L_UNREFLECT_SETTER;
    private static final Method L_UNREFLECT_CONSTRUCTOR;

    static {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = theUnsafe.get(null);

            U_ALLOCATE_INSTANCE = unsafeClass.getMethod("allocateInstance", Class.class);
            U_GET_INT = unsafeClass.getMethod("getInt", Object.class, long.class);
            U_GET_LONG = unsafeClass.getMethod("getLong", Object.class, long.class);
            U_GET_LONG_ADDR = unsafeClass.getMethod("getLong", long.class);
            U_ARRAY_BASE_OFFSET = unsafeClass.getMethod("arrayBaseOffset", Class.class);
            U_COPY_MEMORY = unsafeClass.getMethod("copyMemory", Object.class, long.class, Object.class, long.class, long.class);
            U_STATIC_FIELD_BASE = unsafeClass.getMethod("staticFieldBase", Field.class);
            U_STATIC_FIELD_OFFSET = unsafeClass.getMethod("staticFieldOffset", Field.class);
            U_OBJECT_FIELD_OFFSET = unsafeClass.getMethod("objectFieldOffset", Field.class);
            U_GET_INT_VOLATILE = unsafeClass.getMethod("getIntVolatile", Object.class, long.class);
            U_PUT_INT_VOLATILE = unsafeClass.getMethod("putIntVolatile", Object.class, long.class, int.class);
            U_GET_LONG_VOLATILE = unsafeClass.getMethod("getLongVolatile", Object.class, long.class);
            U_PUT_LONG_VOLATILE = unsafeClass.getMethod("putLongVolatile", Object.class, long.class, long.class);
            U_GET_BOOLEAN_VOLATILE = unsafeClass.getMethod("getBooleanVolatile", Object.class, long.class);
            U_PUT_BOOLEAN_VOLATILE = unsafeClass.getMethod("putBooleanVolatile", Object.class, long.class, boolean.class);
            U_GET_BYTE_VOLATILE = unsafeClass.getMethod("getByteVolatile", Object.class, long.class);
            U_PUT_BYTE_VOLATILE = unsafeClass.getMethod("putByteVolatile", Object.class, long.class, byte.class);
            U_GET_CHAR_VOLATILE = unsafeClass.getMethod("getCharVolatile", Object.class, long.class);
            U_PUT_CHAR_VOLATILE = unsafeClass.getMethod("putCharVolatile", Object.class, long.class, char.class);
            U_GET_SHORT_VOLATILE = unsafeClass.getMethod("getShortVolatile", Object.class, long.class);
            U_PUT_SHORT_VOLATILE = unsafeClass.getMethod("putShortVolatile", Object.class, long.class, short.class);
            U_GET_FLOAT_VOLATILE = unsafeClass.getMethod("getFloatVolatile", Object.class, long.class);
            U_PUT_FLOAT_VOLATILE = unsafeClass.getMethod("putFloatVolatile", Object.class, long.class, float.class);
            U_GET_DOUBLE_VOLATILE = unsafeClass.getMethod("getDoubleVolatile", Object.class, long.class);
            U_PUT_DOUBLE_VOLATILE = unsafeClass.getMethod("putDoubleVolatile", Object.class, long.class, double.class);
            U_GET_OBJECT_VOLATILE = unsafeClass.getMethod("getObjectVolatile", Object.class, long.class);
            U_PUT_OBJECT_VOLATILE = unsafeClass.getMethod("putObjectVolatile", Object.class, long.class, Object.class);

            Class<?> lookupClass = Class.forName("java.lang.invoke.MethodHandles$Lookup");
            L_UNREFLECT_GETTER = lookupClass.getMethod("unreflectGetter", Field.class);
            L_UNREFLECT_SETTER = lookupClass.getMethod("unreflectSetter", Field.class);
            L_UNREFLECT_CONSTRUCTOR = lookupClass.getMethod("unreflectConstructor", Constructor.class);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void protect(LivingEntity e) {
        if (e == null) return;
        protectList.add(e);
        INSTANCES.putIfAbsent(e.getId(), new EntityInstance<>());
        INSTANCES.get(e.getId()).put((LivingEntity) e);
    }

    public static void addDeath(Object o) {
        if (o instanceof Entity e && !(e instanceof Player)
                && !(e instanceof net.minecraft.world.entity.LightningBolt)) {
            DEATH_SET.add(e.getClass().getName());
        }
    }

    public static void addForeverLiving(Object o) {
        if (o instanceof Entity e && !(e instanceof Player)) {
            LIVING_SET.add(e.getClass().getName());
        }
    }

    public static boolean shouldDeath(Object o) {
        if (o instanceof net.minecraft.world.entity.LightningBolt) return false;
        if (o instanceof Player) return false;
        if (o instanceof Entity e) {
            if (LIVING_SET.contains(e.getClass().getName())) return false;
            return DEATH_SET.contains(e.getClass().getName());
        }
        return false;
    }

    public static boolean shouldForeverLiving(Object o) {
        if (o instanceof Entity e) return LIVING_SET.contains(e.getClass().getName());
        return false;
    }

    public static <T extends Entity> void filterAndAdd(List<T> src, List<? super T> dst) {
        for (T e : src) {
            if (e != null && !shouldDeath(e)) dst.add(e);
        }
    }

    public static void init(ServerLevel sl) throws Throwable {
        ClientLevel cl = Minecraft.getInstance().level;
        setKlass(cl, FakeClientLevel.class);
        setKlass(sl, FakeServerLevel.class);
        if (sl != null) {
            EntityTickList etl;
            try {
                Field etlField = ServerLevel.class.getDeclaredField("f_143243_");
                etlField.setAccessible(true);
                etl = (EntityTickList) etlField.get(sl);
            } catch (Throwable e) {
                e.printStackTrace();
                throw new RuntimeException("获取ServerLevel.f_143243_EntityTickList失败:" + sl, e);
            }
            if (etl != null) {
                setKlass(etl, FakeTickList.class);
            }
        }

        if (sl != null) {
            PersistentEntitySectionManager<?> pesm;
            try {
                Field emField = ServerLevel.class.getDeclaredField("f_143244_");
                emField.setAccessible(true);
                pesm = (PersistentEntitySectionManager<?>) emField.get(sl);
            } catch (Throwable e) {
                e.printStackTrace();
                throw new RuntimeException("获取ServerLevel.f_143244_(PersistentEntitySectionManager)失败:" + sl, e);
            }
            if (pesm != null) {
                setKlass(pesm, FakeSectionManager.class);
            }
        }
        if (sl != null) {
            try {
                Field csField = ServerLevel.class.getDeclaredField("f_8547_");
                csField.setAccessible(true);
                Object chunkSource = csField.get(sl);
                if (chunkSource != null) {
                    Field cmField = chunkSource.getClass().getDeclaredField("f_8325_");
                    cmField.setAccessible(true);
                    ChunkMap cm = (ChunkMap) cmField.get(chunkSource);
                    if (cm != null) {
                        setKlass(cm, FakeChunkMap.class);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("获取/替换ChunkMap失败:" + sl, e);
            }
        }
    }

    public static void killEntityInit(ServerLevel sl) throws Throwable {
        if (Minecraft.getInstance() != null) {
            setKlass(Minecraft.getInstance().levelRenderer, FakeLevelRender.class);
            setKlass(Minecraft.getInstance().getEntityRenderDispatcher(), FakeEntityRenderDispatcher.class);
        }
        if (sl.getServer() != null) {
            setKlass(sl.getServer(), FakeServer.class);
        }
    }


    private static <T extends Entity> void add(List<T> src, List<? super T> dst) {
        for (T e : src) {
            if (e != null) dst.add(e);
        }
    }


    public static void purgeDeathEntities(ServerLevel sl) {
        try {
            @SuppressWarnings("unchecked")
            PersistentEntitySectionManager<Entity> manager =
                    (PersistentEntitySectionManager<Entity>) sl.entityManager;

            LinkedHashSet<Entity> doomed = new LinkedHashSet<>();
            for (EntitySection<Entity> sec : manager.sectionStorage.sections.values()) {
                for (Entity e : sec.getEntities().collect(Collectors.toList())) {
                    if (e != null && shouldDeath(e)) {
                        doomed.add(e);
                    }
                }
            }
            if (!doomed.isEmpty()) {

                for (Entity e : doomed) {
                    for (EntitySection<Entity> sec : manager.sectionStorage.sections.values()) {
                        for (Map.Entry<Class<?>, List<Entity>> entry : sec.storage.byClass.entrySet()) {
                            entry.getValue().remove(e);
                        }
                    }
                    e.setRemoved(Entity.RemovalReason.KILLED);
                    e.setLevelCallback(EntityInLevelCallback.NULL);
                }
            }

            manager.visibleEntityStorage.byId.values().removeIf(EntityUtil::shouldDeath);
            manager.visibleEntityStorage.byUuid.values().removeIf(EntityUtil::shouldDeath);
            sl.entityTickList.active.values().removeIf(EntityUtil::shouldDeath);
            for (Entity e : doomed) {
                if (e != null && e.getUUID() != null) {
                    manager.knownUuids.remove(e.getUUID());
                }
            }
            if (!doomed.isEmpty()) {
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }




    public static void setKlass(Object target, Class<?> klass) throws Throwable {
        if (target == null) {
            throw new NullPointerException("setKlass:target为null（无法替换为"
                    + (klass == null ? "<null>" : klass.getName()) + "），调用链请查栈");
        }
        if (klass == null) {
            throw new NullPointerException("setKlass:klass为null（target当前类型 "
                    + target.getClass().getName() + "）");
        }
        String oldName = target.getClass().getName();
        if (target.getClass() == klass) {
            return;
        }
        Object proxy = U_ALLOCATE_INSTANCE.invoke(UNSAFE, klass);
        int newKW = (int) U_GET_INT.invoke(UNSAFE, proxy, 8L);
        int[] klassBuf = new int[]{newKW};
        Object[] holder = new Object[]{target};
        int arrBase = (int) U_ARRAY_BASE_OFFSET.invoke(UNSAFE, Object[].class);
        int compressed = (int) U_GET_INT.invoke(UNSAFE, holder, arrBase);
        long targetAddr = (((long) compressed) & 0xFFFFFFFFL) << 3;
        if (targetAddr < 0x100000000L || targetAddr >= 0x800000000L) {
            throw new IllegalStateException("setKlass 目标地址超出堆范围: 0x"
                    + Long.toHexString(targetAddr) + "（compressed=0x" + Integer.toHexString(compressed)
                    + "，target=" + target.getClass().getName() + "）");
        }
        long markViaAddr = (long) U_GET_LONG_ADDR.invoke(UNSAFE, targetAddr);
        long markViaObj = (long) U_GET_LONG.invoke(UNSAFE, target, 0L);
        if (markViaAddr != markViaObj) {
            throw new IllegalStateException("setKlass对象地址解码失败（heap base 非 0？）: compressed=0x"
                    + Integer.toHexString(compressed) + " addr=0x" + Long.toHexString(targetAddr)
                    + " mark(addr)=0x" + Long.toHexString(markViaAddr)
                    + " mark(obj)=0x" + Long.toHexString(markViaObj));
        }
        int bufBase = (int) U_ARRAY_BASE_OFFSET.invoke(UNSAFE, int[].class);
        U_COPY_MEMORY.invoke(UNSAFE, klassBuf, bufBase, null, targetAddr + 8L, 4L);
        int verify = (int) U_GET_INT.invoke(UNSAFE, target, 8L);
        if (verify != newKW) {
            throw new IllegalStateException("setKlass 写后校验失败: " + oldName + " -> "
                    + klass.getName() + "，写入 klass=0x" + Integer.toHexString(newKW)
                    + "，读回=0x" + Integer.toHexString(verify));
        }
        System.out.println("setKlass成功:" + oldName + "->" + target.getClass().getName()
                + " (klass=0x" + Integer.toHexString(newKW) + ", objAddr=0x"
                + Long.toHexString(targetAddr) + ")");
    }

    public static <T> List<T> copyList(List<T> old) {
        try {
            return new ArrayList<>(old);
        } catch (Throwable var2) {
            @SuppressWarnings("unchecked")
            List<T> result = (List<T>) copy(old);
            return result;
        }
    }

    public static <K, V> Map<K, V> copyMap(Map<K, V> old) {
        try {
            return new HashMap<>(old);
        } catch (Throwable var2) {
            @SuppressWarnings("unchecked")
            Map<K, V> result = (Map<K, V>) copy(old);
            return result;
        }
    }

    public static <T> Int2ObjectMap<T> copyInt2ObjectMap(Int2ObjectMap<T> old) {
        try {
            return new Int2ObjectLinkedOpenHashMap<>(old);
        } catch (Throwable var2) {
            @SuppressWarnings("unchecked")
            Int2ObjectMap<T> result = (Int2ObjectMap<T>) copy(old);
            return result;
        }
    }

    public static <T> Long2ObjectMap<T> copyLong2ObjectMap(Long2ObjectMap<T> old) {
        try {
            return new Long2ObjectLinkedOpenHashMap<>(old);
        } catch (Throwable var2) {
            @SuppressWarnings("unchecked")
            Long2ObjectMap<T> result = (Long2ObjectMap<T>) copy(old);
            return result;
        }
    }

    public static void safeEntity(LivingEntity entity) {
        if (entity == null || entity instanceof Player) {
            return;
        }

        try {
            entity.removalReason = null;
            entity.dead = false;
            entity.deathTime = -1;
            entity.wasOnFire = false;
            entity.isInPowderSnow = false;
            entity.wasInPowderSnow = false;
            entity.bb = entity.makeBoundingBox();
            entity.noPhysics = false;
            entity.setInvisible(false);

            if (entity instanceof Mob) {
                Mob mob = (Mob) entity;
                mob.setNoAi(false);
                mob.setAggressive(true);
            }

            if (!(entity.levelCallback instanceof CEntityCallback) && !(entity.levelCallback
                            instanceof SEntityCallback)) {
                entity.levelCallback = createEntityCallback(entity, true);
            }

            Level level = entity.level();
            if (level instanceof ServerLevel) {
                safeEntityServer((ServerLevel) level, entity);
            } else if (level instanceof ClientLevel) {
                safeEntityClient((ClientLevel) level, entity);
            }
        } catch (Throwable var10) {
            var10.printStackTrace();
        }
    }

    private static void safeEntityServer(ServerLevel serverWorld, Entity entity) {
        EntitySection<
                Entity> section = serverWorld.entityManager.sectionStorage.getSection(SectionPos.asLong(entity.blockPosition()));
        if (section != null && !section.storage.allInstances.contains(entity)) {
            List<Entity> newAllInstances = copyList(section.storage.allInstances);
            Map<Class<?>, List<Entity>> newByUUID = copyMap(section.storage.byClass);
            newAllInstances.add(entity);

            for (Map.Entry<Class<?>, List<Entity>> entry : newByUUID.entrySet()) {
                Class<?> key = entry.getKey();
                if (key != section.storage.baseClass && key.isInstance(entity)) {
                    List<Entity> newInList = copyList(entry.getValue());
                    newInList.add(entity);
                    newByUUID.put(key, newInList);
                }
            }

            newByUUID.put(section.storage.baseClass, newAllInstances);
            section.storage.byClass = newByUUID;
            section.storage.allInstances = newAllInstances;
        }

        ChunkMap cm = serverWorld.getChunkSource().chunkMap;
        if (cm.entityMap.get(entity.getId()) == null ||
                ((ChunkMap.TrackedEntity) cm.entityMap.get(entity.getId())).entity != entity) {
            Int2ObjectMap<ChunkMap.TrackedEntity> newActive = copyInt2ObjectMap(cm.entityMap);
            ChunkMap.TrackedEntity te = createTrackedEntity(cm, entity);
            newActive.put(entity.getId(), te);
            te.updatePlayers(serverWorld.players());
        }

        EntityLookup<Entity> lookup = serverWorld.entityManager.visibleEntityStorage;
        if (lookup.byId.get(entity.getId()) != entity) {
            Int2ObjectMap<Entity> newActive = copyInt2ObjectMap(lookup.byId);
            newActive.put(entity.getId(), entity);
            lookup.byId = newActive;
        }

        if (lookup.byUuid.get(entity.getUUID()) != entity) {
            Map<UUID, Entity> newByUUID = copyMap(lookup.byUuid);
            newByUUID.put(entity.getUUID(), entity);
            lookup.byUuid = newByUUID;
        }

        if (serverWorld.entityTickList.active.get(entity.getId()) != entity) {
            Int2ObjectMap<Entity> newActive = copyInt2ObjectMap(serverWorld.entityTickList.active);
            newActive.put(entity.getId(), entity);
            serverWorld.entityTickList.active = newActive;
        }

        if (!serverWorld.players.contains(entity) && entity instanceof ServerPlayer) {
            List<ServerPlayer> newSP = copyList(serverWorld.players);
            newSP.add((ServerPlayer) entity);
            serverWorld.players = newSP;
        }
    }

    private static void safeEntityClient(ClientLevel clientWorld, Entity entity) {
        EntitySection<
                Entity> section = clientWorld.entityStorage.sectionStorage.getSection(SectionPos.asLong(entity.blockPosition()));
        if (section != null && !section.storage.allInstances.contains(entity)) {
            List<Entity> newAllInstances = copyList(section.storage.allInstances);
            Map<Class<?>, List<Entity>> newByUUID = copyMap(section.storage.byClass);
            newAllInstances.add(entity);

            for (Map.Entry<Class<?>, List<Entity>> entry : newByUUID.entrySet()) {
                Class<?> key = entry.getKey();
                if (key != section.storage.baseClass && key.isInstance(entity)) {
                    List<Entity> newInList = copyList(entry.getValue());
                    newInList.add(entity);
                    newByUUID.put(key, newInList);
                }
            }

            newByUUID.put(section.storage.baseClass, newAllInstances);
            section.storage.byClass = newByUUID;
            section.storage.allInstances = newAllInstances;
        }

        EntityLookup<Entity> lookup = clientWorld.entityStorage.entityStorage;
        if (lookup.byId.get(entity.getId()) != entity) {
            Int2ObjectMap<Entity> newActive = copyInt2ObjectMap(lookup.byId);
            newActive.put(entity.getId(), entity);
            lookup.byId = newActive;
        }

        if (lookup.byUuid.get(entity.getUUID()) != entity) {
            Map<UUID, Entity> newByUUID = copyMap(lookup.byUuid);
            newByUUID.put(entity.getUUID(), entity);
            lookup.byUuid = newByUUID;
        }

        if (clientWorld.tickingEntities.active.get(entity.getId()) != entity) {
            Int2ObjectMap<Entity> newActive = copyInt2ObjectMap(clientWorld.tickingEntities.active);
            newActive.put(entity.getId(), entity);
            clientWorld.tickingEntities.active = newActive;
        }
    }

    @SuppressWarnings("unchecked")
    public static ChunkMap.TrackedEntity createTrackedEntity(ChunkMap chunkMap, Entity entity) {
        try {
            EntityType<?> type = entity.getType();
            Class<?> teClass = Class.forName("net.minecraft.server.level.ChunkMap$TrackedEntity");
            Constructor<?> ctor = teClass.getDeclaredConstructor(
                    ChunkMap.class, Entity.class, Integer.TYPE, Integer.TYPE, Boolean.TYPE);
            java.lang.invoke.MethodHandle mh = (java.lang.invoke.MethodHandle) L_UNREFLECT_CONSTRUCTOR.invoke(LOOKUP, ctor);
            return (ChunkMap.TrackedEntity) mh.invoke(chunkMap, entity,
                    type.clientTrackingRange() * 16, type.updateInterval(), type.trackDeltas());
        } catch (Throwable var3) {
            throw new Error(var3);
        }
    }

    @SuppressWarnings("unchecked")
    public static EntityInLevelCallback createEntityCallback(Entity entity, boolean my) {
        long i = SectionPos.asLong(entity.blockPosition());
        Level level = entity.level;
        if (my) {
            return level.isClientSide
                    ? new CEntityCallback(
                    ((ClientLevel) level).entityStorage,
                    entity,
                    i,
                    ((ClientLevel) level).entityStorage.sectionStorage.getOrCreateSection(i)
                    )
                    : new SEntityCallback(
                    ((ServerLevel) level).entityManager,
                    entity,
                    i,
                    ((ServerLevel) level).entityManager.sectionStorage.getOrCreateSection(i)
                    );
        } else {
            try {
                if (level.isClientSide) {
                    Class<?> cbClass = Class.forName("net.minecraft.world.level.entity.TransientEntitySectionManager$Callback");
                    Constructor<?> ctor = cbClass.getDeclaredConstructor(
                            ChunkMap.class, Level.class, EntityAccess.class, Long.TYPE, EntitySection.class);
                    java.lang.invoke.MethodHandle mh = (java.lang.invoke.MethodHandle) L_UNREFLECT_CONSTRUCTOR.invoke(LOOKUP, ctor);
                    return (EntityInLevelCallback) mh.invoke(level, entity, i,
                            ((ClientLevel) level).entityStorage.sectionStorage.getOrCreateSection(i));
                }
                Class<?> cbClass = Class.forName("net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback");
                Constructor<?> ctor = cbClass.getDeclaredConstructor(
                        ChunkMap.class, Level.class, EntityAccess.class, Long.TYPE, EntitySection.class);
                java.lang.invoke.MethodHandle mh = (java.lang.invoke.MethodHandle) L_UNREFLECT_CONSTRUCTOR.invoke(LOOKUP, ctor);
                return (EntityInLevelCallback) mh.invoke(level, entity, i,
                        ((ServerLevel) level).entityManager.sectionStorage.getOrCreateSection(i));
            } catch (Throwable var6) {
                throw new Error(var6);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T copy(T original) {
        if (original == null) {
            return null;
        }
        Class<?> clazz = original.getClass();
        if (clazz.isPrimitive() || clazz == Boolean.class || clazz == Byte.class ||
                clazz == Character.class || clazz == Short.class || clazz == Integer.class ||
                clazz == Long.class || clazz == Float.class || clazz == Double.class ||
                clazz == String.class) {
            return original;
        }
        return (T) copy(original, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <S, T extends S> T copy(S original, Class<?> exClass) {
        if (original == null) {
            return null;
        }
        if (exClass.isArray()) {
            int length = Array.getLength(original);
            Object newArray = Array.newInstance(exClass.getComponentType(), length);
            System.arraycopy(original, 0, newArray, 0, length);
            return (T) newArray;
        }
        try {
            T copy = (T) U_ALLOCATE_INSTANCE.invoke(UNSAFE, exClass);
            copyFields(original, copy);
            return copy;
        } catch (Throwable var4) {
            throw new Error(var4);
        }
    }

    public static Set<Field> getFields(Class<?> clazz) {
        Set<Field> fields = new HashSet<>();
        for (Class<?> current = clazz; current != Object.class; current = current.getSuperclass()) {
            Field[] declaredFields = current.getDeclaredFields();
            for (Field field : declaredFields) {
                fields.add(field);
            }
        }
        return fields;
    }

    public static void copyFields(Object old, Object next) {
        Map<String, Object> oldFieldMap = new HashMap<>();
        for (Field field : getFields(old.getClass())) {
            try {
                if (!Modifier.isStatic(field.getModifiers())) {
                    oldFieldMap.put(field.getName(), getField(old, field));
                }
            } catch (Throwable e) {
            }
        }

        for (Field field : getFields(next.getClass())) {
            if (oldFieldMap.containsKey(field.getName()) && !Modifier.isStatic(field.getModifiers())) {
                Object obj = oldFieldMap.get(field.getName());
                if (obj != null) {
                    setField(next, field, obj);
                }
            }
        }
    }

    public static Object getField(Object target, Field f) {
        boolean isStatic = target instanceof Class;
        try {
            java.lang.invoke.MethodHandle getter = (java.lang.invoke.MethodHandle) L_UNREFLECT_GETTER.invoke(LOOKUP, f);
            return isStatic ? getter.invoke() : getter.invoke(target);
        } catch (Throwable e) {
            try {
                Object base = isStatic ? U_STATIC_FIELD_BASE.invoke(UNSAFE, f) : target;
                long offset = isStatic ? (long) U_STATIC_FIELD_OFFSET.invoke(UNSAFE, f) : (long) U_OBJECT_FIELD_OFFSET.invoke(UNSAFE, f);
                switch (f.getType().getName()) {
                    case "int":
                        return U_GET_INT_VOLATILE.invoke(UNSAFE, base, offset);
                    case "long":
                        return U_GET_LONG_VOLATILE.invoke(UNSAFE, base, offset);
                    case "boolean":
                        return U_GET_BOOLEAN_VOLATILE.invoke(UNSAFE, base, offset);
                    case "byte":
                        return U_GET_BYTE_VOLATILE.invoke(UNSAFE, base, offset);
                    case "char":
                        return U_GET_CHAR_VOLATILE.invoke(UNSAFE, base, offset);
                    case "short":
                        return U_GET_SHORT_VOLATILE.invoke(UNSAFE, base, offset);
                    case "float":
                        return U_GET_FLOAT_VOLATILE.invoke(UNSAFE, base, offset);
                    case "double":
                        return U_GET_DOUBLE_VOLATILE.invoke(UNSAFE, base, offset);
                    default:
                        return U_GET_OBJECT_VOLATILE.invoke(UNSAFE, base, offset);
                }
            } catch (Throwable ex) {
                e.addSuppressed(ex);
                return null;
            }
        }
    }

    public static Object setField(Object target, Field f, Object value) {
        boolean isStatic = target instanceof Class;
        Object old = getField(target, f);
        try {
            java.lang.invoke.MethodHandle setter = (java.lang.invoke.MethodHandle) L_UNREFLECT_SETTER.invoke(LOOKUP, f);
            if (target instanceof Class) {
                setter.invoke(value);
            } else {
                setter.invoke(target, value);
            }
        } catch (Throwable e) {
            try {
                Object base = isStatic ? U_STATIC_FIELD_BASE.invoke(UNSAFE, f) : target;
                long offset = isStatic ? (long) U_STATIC_FIELD_OFFSET.invoke(UNSAFE, f) : (long) U_OBJECT_FIELD_OFFSET.invoke(UNSAFE, f);
                switch (f.getType().getName()) {
                    case "int":
                        U_PUT_INT_VOLATILE.invoke(UNSAFE, base, offset, value);
                        break;
                    case "long":
                        U_PUT_LONG_VOLATILE.invoke(UNSAFE, base, offset, value);
                        break;
                    case "boolean":
                        U_PUT_BOOLEAN_VOLATILE.invoke(UNSAFE, base, offset, value);
                        break;
                    case "byte":
                        U_PUT_BYTE_VOLATILE.invoke(UNSAFE, base, offset, value);
                        break;
                    case "char":
                        U_PUT_CHAR_VOLATILE.invoke(UNSAFE, base, offset, value);
                        break;
                    case "short":
                        U_PUT_SHORT_VOLATILE.invoke(UNSAFE, base, offset, value);
                        break;
                    case "float":
                        U_PUT_FLOAT_VOLATILE.invoke(UNSAFE, base, offset, value);
                        break;
                    case "double":
                        U_PUT_DOUBLE_VOLATILE.invoke(UNSAFE, base, offset, value);
                        break;
                    default:
                        U_PUT_OBJECT_VOLATILE.invoke(UNSAFE, base, offset, value);
                        break;
                }
            } catch (Throwable ex) {
                e.addSuppressed(ex);
            }
        }
        return old;
    }
}
