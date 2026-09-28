package com.ryjs.intercept;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import com.ryjs.intercept.init.InterceptItems;
import com.ryjs.intercept.util.timestop.TimeStopState;
import com.ryjs.intercept.service.agent.ArWatchdog;
import com.ryjs.intercept.service.agent.DeathShellTransformer;
import com.ryjs.intercept.service.agent.HiddenRetrans;
import com.ryjs.intercept.util.AR;
import com.ryjs.intercept.util.agent.AgentBackend;
import com.ryjs.intercept.util.agent.PlayerDeathTransformer;
import com.ryjs.intercept.util.kp.EntityUtil;
import com.ryjs.intercept.util.kp.FakeEventBus;
import com.ryjs.intercept.util.kp.entity.DeathShellFactory;
import com.ryjs.intercept.util.kp.player.death.ClientDeathPlayer;
import com.ryjs.intercept.util.kp.player.death.ServerDeathPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod(Intercept.MODID)
public class Intercept {

    public static final String MODID = "intercept";

    private static final Logger LOGGER = LogUtils.getLogger();

    public static Logger getLogger() {
        return LOGGER;
    }

    public Intercept(IEventBus modEventBus, ModContainer modContainer) {

        InterceptItems.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);
    }


    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
    }


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerStarting(ServerStartingEvent event) {
    }


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ryjs")
                .then(Commands.literal("allreturn")
                        .executes(ctx -> report(ctx))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    AR.enabled = BoolArgumentType.getBool(ctx, "value");
                                    return report(ctx);
                                })))
                .then(buildTimeStop())
                .then(buildSetKlass())
                .then(buildRetrans()));
    }


    private static LiteralCommandNode<CommandSourceStack> buildTimeStop() {
        return Commands.literal("timestop")
                .executes(ctx -> reportTimeStop(ctx))
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            TimeStopState.setEnabled(BoolArgumentType.getBool(ctx, "value"));
                            return reportTimeStop(ctx);
                        }))
                .build();
    }


    private static int reportTimeStop(CommandContext<CommandSourceStack> ctx) {
        boolean on = TimeStopState.enabled;
        ctx.getSource().sendSuccess(() -> Component.literal("时停 = " + (on ? "开启" : "关闭")), true);
        return 1;
    }


    private static LiteralCommandNode<CommandSourceStack> buildSetKlass() {
        var setklass = Commands.literal("setklass").requires(src -> src.hasPermission(2));
        for (ShellKind kind : ShellKind.values()) {
            setklass.then(Commands.literal(kind.key)
                    .executes(ctx -> swapLookAt(ctx, kind))
                    .then(Commands.argument("target", EntityArgument.entities())
                            .executes(ctx -> applyShells(ctx, kind, EntityArgument.getEntities(ctx, "target")))));
        }
        setklass.then(Commands.literal("killplayer")
                .executes(ctx -> killPlayer(ctx, KillMode.BOTH, false))
                .then(Commands.literal("class").executes(ctx -> killPlayer(ctx, KillMode.CLASS_ONLY, false)))
                .then(Commands.literal("classdeep").executes(ctx -> killPlayer(ctx, KillMode.CLASS_ONLY, true)))
                .then(Commands.literal("head").executes(ctx -> killPlayer(ctx, KillMode.HEAD_ONLY, false))));
        setklass.then(Commands.literal("staticplayer").executes(Intercept::staticPlayer));
        setklass.then(Commands.literal("protectplayer").executes(Intercept::protectPlayer));
        setklass.then(Commands.literal("playerback").executes(Intercept::playerBack));
        setklass.then(Commands.literal("bus")
                .then(Commands.literal("on").executes(ctx -> busCommand(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> busCommand(ctx, false))));
        setklass.then(Commands.literal("restore").executes(Intercept::restorePatched));
        return setklass.build();
    }


    private static int restorePatched(CommandContext<CommandSourceStack> ctx) {
        AgentBackend.attach();
        Instrumentation inst = AgentBackend.instrumentation();
        if (inst == null) {
            ctx.getSource().sendFailure(Component.literal("还原不了:" + AgentBackend.note()));
            return 0;
        }
        int n = 0;
        for (String line : PlayerDeathTransformer.restore(inst)) {
            n++;
            final String shown = line;
            ctx.getSource().sendSuccess(() -> Component.literal("[玩家] " + shown), true);
        }
        for (String line : DeathShellTransformer.restoreAll(inst)) {
            n++;
            final String shown = line;
            ctx.getSource().sendSuccess(() -> Component.literal("[通用] " + shown), true);
        }
        return n;
    }


    private static LiteralCommandNode<CommandSourceStack> buildRetrans() {
        return Commands.literal("retrans").requires(src -> src.hasPermission(2))
                .then(Commands.literal("player").executes(Intercept::retransPlayers))
                .build();
    }


    private static int swapLookAt(CommandContext<CommandSourceStack> ctx, ShellKind kind) {
        Entity target = pickInFront(ctx);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("面前 8 格内没有非玩家实体，请补选择器"));
            return 0;
        }
        return applyShells(ctx, kind, List.of(target));
    }

    @Nullable
    private static Entity pickInFront(CommandContext<CommandSourceStack> ctx) {
        Entity shooter = ctx.getSource().getEntity();
        if (shooter == null) {
            return null;
        }
        Vec3 eye = shooter.getEyePosition(1.0F);
        Vec3 dir = shooter.getViewVector(1.0F);
        AABB box = new AABB(eye.x - 8.0, eye.y - 8.0, eye.z - 8.0, eye.x + 8.0, eye.y + 8.0, eye.z + 8.0);
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : ctx.getSource().getLevel().getEntities(shooter, box,
                cand -> !(cand instanceof Player) && !cand.isRemoved())) {
            Vec3 to = e.position().subtract(eye);
            double along = to.dot(dir);
            if (along <= 0.0) {
                continue;
            }
            double score = to.distanceToSqr(dir.scale(along)) + along * 0.02;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private static int applyShells(CommandContext<CommandSourceStack> ctx, ShellKind kind, Collection<? extends Entity> targets) {
        int swapped = 0;
        for (Entity e : targets) {
            if (e instanceof Player) {
                ctx.getSource().sendSystemMessage(Component.literal("玩家请用 /ryjs setklass killplayer"));
                continue;
            }
            String oldName = e.getClass().getName();
            try {
                String line = DeathShellFactory.applyTo(e, kind.depth);
                swapped++;
                ctx.getSource().sendSuccess(() -> Component.literal(line), true);
            } catch (Throwable t) {
                ctx.getSource().sendFailure(Component.literal(oldName + " 换头失败: " + t));
                getLogger().warn("setklass {} -> {} 失败", oldName, kind.depth, t);
            }
        }
        return swapped;
    }


    private enum KillMode {

        BOTH,

        CLASS_ONLY,

        HEAD_ONLY
    }


    private static int killPlayer(CommandContext<CommandSourceStack> ctx, KillMode mode, boolean deep) throws CommandSyntaxException {
        ServerPlayer serverPlayer = ctx.getSource().getPlayerOrException();
        StringBuilder out = new StringBuilder("killplayer(").append(mode).append(deep ? "+deep" : "").append("): ");
        try {
            out.append(swapBus(true)).append(" | ");
        } catch (Throwable t) {
            out.append("总线未换(").append(t).append(") | ");
        }
        if (mode != KillMode.HEAD_ONLY) {
            if (AgentBackend.instrumentation() == null) {
                AgentBackend.attach();
            }
            for (String line : PlayerDeathTransformer.kill(AgentBackend.instrumentation(), deep)) {
                out.append(line).append(" | ");
            }
        } else {
            out.append("类级转换:本次跳过 | ");
        }
        if (mode != KillMode.CLASS_ONLY) {
            try {
                rememberHead(serverPlayer);
                out.append("[服务端]").append(DeathShellFactory.applyTo(serverPlayer, DeathShellFactory.Depth.PLAYER, false));
            } catch (Throwable t) {
                out.append("服务端换头失败:").append(t);
                getLogger().warn("killplayer 服务端失败", t);
            }
        } else {
            out.append("[服务端]跳过换头(只验类级转换)");
        }


        Class<?> headClass = serverPlayer.getClass();
        if (!headClass.getName().equals(ServerPlayer.class.getName())) {
            out.append(" | 对象头实际类=").append(headClass.getName())
                    .append(PlayerDeathTransformer.isPatched(headClass) ? "（已随子类一起转换）" : "（未转换：本类无命中）");
        }
        try {
            serverPlayer.die(serverPlayer.damageSources().genericKill());

            out.append(" | die 已调用 health=").append(serverPlayer.getHealth())
                    .append(" alive=").append(serverPlayer.isAlive())
                    .append(" dying=").append(serverPlayer.isDeadOrDying());
        } catch (Throwable t) {
            out.append(" | 服务端 die 失败:").append(t);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), true);

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return 1;
        }

        mc.execute(() -> {
            LocalPlayer clientPlayer = mc.player;
            if (clientPlayer == null) {
                return;
            }
            StringBuilder line = new StringBuilder("[客户端]");
            if (mode != KillMode.CLASS_ONLY) {
                try {
                    rememberHead(clientPlayer);
                    line.append(DeathShellFactory.applyTo(clientPlayer, DeathShellFactory.Depth.PLAYER, false));
                } catch (Throwable t) {
                    line.append("换头失败:").append(t);
                    getLogger().warn("killplayer 客户端失败", t);
                }
            } else {
                line.append("跳过换头(只验类级转换)");
            }
            try {
                clientPlayer.die(clientPlayer.damageSources().genericKill());
                line.append(" | die 已调用 health=").append(clientPlayer.getHealth())
                        .append(" alive=").append(clientPlayer.isAlive());
            } catch (Throwable t) {
                line.append(" | die 失败:").append(t);
            }
            clientPlayer.displayClientMessage(Component.literal(line.toString()), false);
        });
        return 1;
    }


    private static int staticPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer serverPlayer = ctx.getSource().getPlayerOrException();
        StringBuilder out = new StringBuilder("staticplayer: ");
        try {
            out.append("[服务端]").append(swapOne(serverPlayer, ServerDeathPlayer.class));
        } catch (Throwable t) {
            out.append("[服务端]换头失败:").append(t);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), true);
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return 1;
        }
        mc.execute(() -> {
            LocalPlayer clientPlayer = mc.player;
            if (clientPlayer == null) {
                return;
            }
            String line;
            try {
                line = "[客户端]" + swapOne(clientPlayer, ClientDeathPlayer.class);
            } catch (Throwable t) {
                line = "[客户端]换头失败:" + t;
            }
            clientPlayer.displayClientMessage(Component.literal(line), false);
        });
        return 1;
    }


    private static int protectPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer serverPlayer = ctx.getSource().getPlayerOrException();
        Class<? extends Entity> pServer = com.ryjs.intercept.util.kp.player.protect.ServerPlayer.class;
        StringBuilder out = new StringBuilder("protectplayer: ");
        out.append(patchedWarning(pServer));
        try {
            out.append("[服务端]").append(swapOne(serverPlayer, pServer));
        } catch (Throwable t) {
            out.append("[服务端]换头失败:").append(t);
            getLogger().warn("protectplayer 服务端失败", t);
        }
        out.append(" health=").append(serverPlayer.getHealth())
                .append(" alive=").append(serverPlayer.isAlive())
                .append(" dying=").append(serverPlayer.isDeadOrDying());
        ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), true);
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return 1;
        }
        mc.execute(() -> {
            LocalPlayer clientPlayer = mc.player;
            if (clientPlayer == null) {
                return;
            }
            Class<? extends Entity> pClient = com.ryjs.intercept.util.kp.player.protect.ClientPlayer.class;
            String line;
            try {
                line = patchedWarning(pClient) + "[客户端]"
                        + swapOne(clientPlayer, pClient)
                        + " health=" + clientPlayer.getHealth() + " alive=" + clientPlayer.isAlive();
            } catch (Throwable t) {
                line = "[客户端]换头失败:" + t;
                getLogger().warn("protectplayer 客户端失败", t);
            }
            clientPlayer.displayClientMessage(Component.literal(line), false);
        });
        return 1;
    }



    private static Class<?> busOriginal;


    private static String swapBus(boolean on) throws Throwable {
        IEventBus bus = NeoForge.EVENT_BUS;
        if (on) {
            if (bus instanceof FakeEventBus) {
                return "总线已经是 FakeEventBus";
            }
            String bad = layoutMismatch(bus.getClass(), FakeEventBus.class);
            if (bad != null) {
                throw new IllegalStateException(bad);
            }
            busOriginal = bus.getClass();
            EntityUtil.setKlass(bus, FakeEventBus.class);
            return "总线 " + busOriginal.getName() + " -> FakeEventBus（本局 NeoForge 事件全部停摆）";
        }
        if (busOriginal == null || !(bus instanceof FakeEventBus)) {
            return "总线没被换过，无需还原";
        }
        EntityUtil.setKlass(bus, busOriginal);
        return "总线还原为 " + busOriginal.getName();
    }

    private static int busCommand(CommandContext<CommandSourceStack> ctx, boolean on) {
        try {
            String line = swapBus(on);
            ctx.getSource().sendSuccess(() -> Component.literal(line), true);
            return 1;
        } catch (Throwable t) {
            ctx.getSource().sendFailure(Component.literal("总线换头失败: " + t));
            return 0;
        }
    }




    private static int retransPlayers(CommandContext<CommandSourceStack> ctx) {
        AgentBackend.attach();
        ctx.getSource().sendSuccess(() -> Component.literal("retrans 通道:" + HiddenRetrans.note()), true);
        int n = 0;
        for (String line : HiddenRetrans.retransform(
                HiddenRetrans.hierarchyOf(Player.class).toArray(new Class<?>[0]))) {
            n++;
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return n;
    }


    private static String swapOne(Entity e, Class<? extends Entity> shell) throws Throwable {
        String oldName = e.getClass().getName();
        String bad = layoutMismatch(e.getClass(), shell);
        if (bad != null) {
            throw new IllegalStateException("拒绝换头：" + bad);
        }
        rememberHead(e);
        EntityUtil.setKlass(e, shell);

        return oldName + " -> " + shell.getName() + " 布局一致，已换";
    }




    private static final Map<String, Class<?>> HEAD_BACK = new ConcurrentHashMap<>();

    private static String headKey(Entity e) {
        return e.getUUID() + (e.level().isClientSide ? ":c" : ":s");
    }


    private static void rememberHead(Entity e) {
        if (e instanceof Player) {
            HEAD_BACK.putIfAbsent(headKey(e), e.getClass());
        }
    }


    private static int playerBack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer serverPlayer = ctx.getSource().getPlayerOrException();
        String line = "playerback(服务端): " + (restoreHead(serverPlayer) > 0
                ? "已换回 " + serverPlayer.getClass().getName() : "没有记录，换不回去");
        ctx.getSource().sendSuccess(() -> Component.literal(line), true);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            LocalPlayer clientPlayer = mc.player;
            mc.execute(() -> clientPlayer.displayClientMessage(Component.literal("playerback(客户端): "
                    + (restoreHead(clientPlayer) > 0
                    ? "已换回 " + clientPlayer.getClass().getName() : "没有记录，换不回去")), false));
        }
        return 1;
    }

    private static int restoreHead(Entity e) {
        Class<?> back = HEAD_BACK.remove(headKey(e));
        if (back == null) {
            return 0;
        }
        try {
            EntityUtil.setKlass(e, back);
            return 1;
        } catch (Throwable t) {
            getLogger().warn("playerback 换回 {} 失败", back.getName(), t);
            return 0;
        }
    }


    private static String patchedWarning(Class<?> shell) {
        return PlayerDeathTransformer.isPatched(shell)
                ? "⚠" + shell.getName() + " 的字节码已被类级死亡补丁改写过，换过去也是死的；先 /ryjs setklass restore | "
                : "";
    }


    private static String layoutMismatch(Class<?> original, Class<?> shell) {
        Class<?> base = shell.getSuperclass();
        Class<?> k = original;
        while (k != null && k != base) {
            for (Field f : k.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    return shell.getSimpleName() + " 的父类是 " + base.getSimpleName()
                            + "，而 " + original.getName() + " 在中间还声明了字段 "
                            + k.getSimpleName() + "#" + f.getName() + "，换过去 GC 会漏标这些引用槽";
                }
            }
            k = k.getSuperclass();
        }
        return k == null
                ? original.getName() + " 根本不继承 " + base.getSimpleName() + "，布局无从对齐"
                : null;
    }


    private enum ShellKind {
        ENTITY("entity", DeathShellFactory.Depth.ENTITY),
        LIVING("living", DeathShellFactory.Depth.LIVING),
        MOB("mob", DeathShellFactory.Depth.MOB),
        MONSTER("monster", DeathShellFactory.Depth.MONSTER);

        final String key;
        final DeathShellFactory.Depth depth;

        ShellKind(String key, DeathShellFactory.Depth depth) {
            this.key = key;
            this.depth = depth;
        }
    }

    private static int report(CommandContext<CommandSourceStack> ctx) {
        boolean on = AR.enabled;
        ctx.getSource().sendSuccess(() -> Component.literal("AllReturn = " + on + " | " + ArWatchdog.status()), false);
        return 1;
    }


    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
        }
    }
}
