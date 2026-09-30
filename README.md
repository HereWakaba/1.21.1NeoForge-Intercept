# TimeStop（时停）特性 —— 代码包

这是 Intercept 模组里"时停"这一整套特性的**去注释源码包**，目录结构镜像 `src/main/...`，便于单独审阅/上传。
开关命令：`/ryjs timestop <true|false>`（不带参数只回显当前状态）。

> 说明：为"主角模式" —— 时停期间整个世界冻结，本地玩家仍可移动；渲染/音效/粒子/文本一并停。

## 一、本包内文件（新增，去注释）

### mixin（`mixin/TimeStop/`）
| 文件 | 注入点 | 作用 |
| --- | --- | --- |
| `TickRateManagerMixin` | `TickRateManager.runsNormally()` → false | **模拟层骨架**：一次挡下服务端世界 tick、实体（玩家豁免）、客户端 tick、粒子、材质滚动等 |
| `UtilMillisFreezeMixin` | `Util.getMillis()` → 常量（仅渲染帧内） | 停附魔光泽(`setupGlintTexturing`读它)、`iTime` 类特效；限帧内避免污染自动保存/网络/日志 |
| `LevelTimeFreezeMixin` | `Level.getGameTime/getDayTime` → 常量（帧内） | 天空/昼夜/随刻方块模型定格 |
| `GameRendererRenderPassMixin` | `GameRenderer.render` HEAD/RETURN | 打开/关闭 `IS_GLOBAL_RENDER_PASS`，给上面两个"帧内钉死"限定作用域 |
| `GameRendererFovFreezeMixin` | `GameRenderer.tickFov` | FOV 缓动定格 |
| `LightTextureFreezeMixin` | `LightTexture.tick` | 光照过渡（`runsNormally` 挡不到，单独停）|
| `TextureManagerFreezeMixin` | `TextureManager.tick` | 一切动画材质 / 物品 `.mcmeta` 逐帧 |
| `ParticleFreezeMixin` / `ParticleEngineFreezeMixin` | `Particle.tick/move`、`ParticleEngine.tick` | 空中粒子定格 |
| `SoundManagerFreezeMixin` / `MusicManagerFreezeMixin` | `SoundManager.play`、`MusicManager.tick` | 不起新音、背景音乐停 |
| `LevelRendererFreezeMixin` | `LevelRenderer.tickRain`、`renderSnowAndRain` 插值 | 雨雪定格 |
| `PostChainFreezeMixin` | `PostChain.process(partialTicks)` → 0 | 后处理链 shader 时间冻结 |
| `ItemStackFreezeMixin` | `ItemStack.getHoverName` / `getTooltipLines` | 通用"彩字"冻结（含第三方如永爱之刃），按特征不点名 |
| `FontSetObfuscationFreezeMixin` | `FontSet.getRandomGlyph` | 停原版 `§k` 魔术字（每帧重掷 `RANDOM` 种子）|
| `MinecraftFrameCounterMixin` | `Minecraft.runTick` | 递增帧号，供上一项"每帧只重掷一次"判定 |

### 共享状态 / 工具（`util/timestop/`）—— 必须在非 mixin 包
`TimeStopState`（总闸 + `nowMs()` 冻结时钟 + 切换监听）、`TimeStopRenderState`（渲染帧 ThreadLocal）、`AnimatedTextCache`（彩字缓存，按 `ItemStack` 实例，可被 `TimestopTextExempt` 豁免）、`TimestopTextExempt`（自家物品名"放行"标记接口）、`ObfuscationSeed`（`§k` 每帧重掷协调）。

### Agent + ASM（`service/agent/transformer/TimeStopClockTransformer.java`）
把**第三方 mod 客户端代码**里的 `System.currentTimeMillis()` 调用点在类加载期改写为 `TimeStopState.nowMs()`。过滤只用正向特征：类名含客户端暗示（`/client/`、`/render(er)/`、`/gui/`、`/effect/` 等）+ 来源落在 `mods/` 目录下 + 复用 `ArOrigin.isInstrumentable`/`gateVisible`（JDK / 自家 / ASM / 已信任 mod / libraries 由它们统一排除，不硬编码包名）。未开启时停时 `nowMs()` 透传实时，行为无差异。

### shader（资源）
- `assets/minecraft/shaders/program/intercept_taichi_charge.fsh`：复用现有蓄力 post 链，新增 `TimeStopAmount` 通道（扫描线 + 横向故障撕裂 + RGB 撕裂 + 去色/冷调 + 暗角脉动，**不反色**）。
- `intercept_taichi_charge.json`：声明 `TimeStopAmount` uniform。
- `intercept.mixins.json`：上面所有 mixin 的注册（common 放 `mixins`，其余在 `client`）。

## 二、被修改的既有文件（不在本包内，仅列改动点）

| 文件 | 改动 |
| --- | --- |
| `Intercept.java` | `registerCommands` 加 `buildTimeStop()`；`ClientModEvents.onClientSetup` 注册切换监听器做 `SoundManager.pause()/resume()`；`setEnabled` 统一写入口 |
| `client/effect/TaiChiChargeEffect.java` | 新增 `timeStopI` 权重，由 `TimeStopState.enabled` 缓入缓出，写 `TimeStopAmount` uniform（与物品蓄力/全反独立，指示层本身继续用真实时间滚动）|
| `client/effect/TaiChiHudOverlay.java`、`TaiChiTooltipRenderer.java`、`TaiChiCubeRenderer.java`、`TaiChiGroundArray.java`、`client/model/CosmicBakeModel.java` | `System.currentTimeMillis()` → `TimeStopState.nowMs()`，时停期间自家视觉定格 |
| `item/EndOfTaiChi.java` | `implements TimestopTextExempt`（自家剑名走彩字豁免，时停期间仍流动）|
| `service/agent/AgentChain.java` | 注册 `TimeStopClockTransformer` + `catchUpClockRewrite`（对附加前已加载的 mod 客户端类补 retransform）|

## 三、已知边界

- 第三方若用"自增 counter + tick 事件"驱动动画（非墙钟、非 `Util.getMillis`），本包不覆盖。
- 多人专用服：`enabled` 静态位不跨进程同步，远端客户端拿不到（单人/局域网开服在同一 JVM 内无碍）。
- 后处理指示 shader 只作用于 `GameRenderer.render` 帧内（含 held item / HUD / 已打开界面），自绘在帧外的东西不受影响。
