# Intercept

一个运行在 NeoForge 启动期 / 运行期的**字节码拦截层**：通过自研的 `GBService` SPI 在极早期拿到控制权，自附加 javaagent，然后用 ASM 对第三方 mod 的类做**条件插入**与**定点掏空**。

不是普通意义上"加内容"的 mod —— 它加的东西有限，主要能力是**改别人的字节码**。

## 环境

| | |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x（FML loader 4） |
| JDK | 21（编译与运行都是 21，`compatibilityLevel: JAVA_21`） |
| mod id | `intercept` |
| 许可 | GPL-3.0 |

## 启动链

不需要在启动参数里写 `-javaagent`。链路是：

```
META-INF/services/net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper
        └─ GBService.<clinit> → AgentChain.start()
              ├─ 造一个临时 agent jar（Manifest: Agent-Class / Can-Retransform-Classes）
              ├─ 用 JNA 调 JNI_GetCreatedJavaVMs → Agent_OnAttach   ← 自附加
              ├─ AgentEntry 把 Instrumentation 交回 AgentChain
              ├─ 用 ClassLoader.defineClass1 把闸口类定义进 bootstrap loader
              │   （AgentEntry / CoexGate / MixinGate / ScriptGate / PluginGate）
              └─ addTransformer × 6 + 链尾哨兵，并对附加前已加载的类补 retransform
```

闸口类必须落在 bootstrap，因为被插桩的类分散在各个加载层，`INVOKESTATIC` 目标得在哪一层都解析得到。

## AR（AllReturn）：头部条件插入

`ArTransformer` 对**目标类**的每个方法，在方法体第一条真指令之前插一段前缀，整段被两个 catch-all 罩住：

```
Lgate:      LDC owner / LDC name / LDC desc
            INVOKESTATIC AR.shouldAR(L,String,String)Z
            IFEQ Lbody
Lattempt:   <掏空产物>            // return super.m(args)  或  该类型的空实例
LattemptEnd:
Lgateless:  POP; GOTO Lbody       // handler for [Lgate, Lattempt)
Lrethrow:   ASTORE v; LDC×3; ALOAD v; INVOKESTATIC AR.caught; ATHROW
                                // handler for [Lattempt, LattemptEnd)
Lbody:      <原方法体，一行没动>
```

两个 handler 的分工是刻意的：

- `[Lgate, Lattempt)` → `POP; GOTO Lbody`：这一段只做"读开关 + 判分档"，炸了说明**这一层加载器根本看不见我们的 jar**（`NoClassDefFoundError`）。于 fail-open 成"当 AR 关着，原方法照跑"，而不是把启动打死。handler 里不引用任何符号。
- `[Lattempt, LattemptEnd)` → 包成带方法名的 `RuntimeException` 再 `athrow`，**绝不吞**。super 回退的副作用可能已经跑了一半，吞掉就等于把我们改过的行为伪装成 mod 自己的正常报错。

开关是运行期的：`AR.enabled` 一个 `volatile boolean`，翻开关不需要重启，已经插过桩的类当场生效。

### 掏成什么：三档去向

对每个方法先选去向，再产字节：

| 档 | 触发条件 | 产物 |
| --- | --- | --- |
| `super` 回退 | 类链上最近的声明是**具体**实现（且可访问、协变签名能对上） | `INVOKESPECIAL super`（接口 default 走 `itf`），必要时 `CHECKCAST` |
| 空实例 | 静态 / 私有 / 自创 / 只剩抽象声明 / default 不可 `invokespecial` | 查一张**空实例表**：`void`、基本类型、`String→""`、`List/Set/Map/Optional/Stream→空集合`、`EnumSet.noneOf(元素类型从 generic signature 取)`、若干登记过的静态空常量 |
| 压根不插 | 表里给不出该类型的空实例 | 原方法体留着 |

第三档不是偷懒：给不出空实例硬要掏，只能给 `null`，而调用方通常是宿主本身 —— 实测代价是渲染和注册表当场崩。所以规则统一成一句：**给得出空实例才掏，给不出就不插**。

### 整体跳过的东西

- `<init>`、`<clinit>`、abstract / native（native 单独处理，见下）
- **接口类与枚举类整类**：枚举的 `values()/valueOf` 是编译器生成的自创方法，掏成 `null` 会炸 `Enum.valueOf` / `getEnumConstants()`，而它们只是元数据不是能力；接口的 default 一体被 gate 掉，所有实现类继承到的默认行为一起没了
- **外观层**（继承链能走到渲染 / 模型 / 纹理锚点的类）与**协议层**（`StreamCodec` 实现）：只保留 `super` 那一档，其余一律不插。协议档是因为匿名 `StreamCodec` 的"特化签名 + 擦除桥"两套房子被掏一半，线格式就错位，直接断连
- **来源不是目标**：判据只有一条 —— codeSource 归一后的路径根里有没有 `libraries` 段。有就整个跳过（NeoForge / mixin / 自己的 jar 都在里面）。不硬编码 `net.minecraft.*` 这类包名前缀，否则改个包名就能钻空子。反过来，字节不落盘的动态生成类 / 隐藏类 / 代理类因为"没有来源"会被拉进来，除非它所在加载层解析不到闸口类

### native 方法

没有 `Code` 属性，前缀插不进去；改名再转调也走不通 —— JNI 按 `Java_<类>_<方法>` mangled 符号绑 DLL，改名连"开关关掉"那条路都会 `UnsatisfiedLinkError`。所以只有一档：脱掉 `ACC_NATIVE`，换成返回类型默认值的方法体，**不挂开关**（`shouldAR` 管不到它们），插桩时必打一行日志，不静默。

## 其它闸口

| 闸口 | 落点 | 行为 |
| --- | --- | --- |
| `CoremodTransformer` | 非 libraries 来源、且**传递实现** 8 个启动期 SPI 之一的类 | 不挂开关，无条件掏：`<clinit>` 掏空、普通方法按上表三档、**构造器截到 `super/this` 调用为止**（比自己只掏它调的方法更狠，内联副作用一起断） |
| `MixinGate*` | `DeferredMixinConfigRegistration.addMixinConfig` 与 `Mixins.createConfiguration`（所有注册路径的最终漏斗） | 按**归属 jar 的路径**判定要不要吞掉这个 mixin 配置。反射链解析失败 ⇒ 放行 + 打一行"需要修"，宁可不拦也不崩 |
| `ScriptGate*` | `ModFileParser.getCoreMods(ModFile)`（`META-INF/coremods.json` 的唯一出口） | 直接返回空列表，脚本引擎根本不被 new |
| `PluginGate*` | `LaunchPluginHandler.<init>(Stream)`（唯一收集口） | 换掉构造参数，按 codeSource 筛掉非 libraries 来源的 launch plugin 实例 |
| `Coex*` | `LaunchContext.<init>` 出口 + `ModuleLayerHandler.buildLayer` 入口 | 共存自清理：把自己从 `locatedPaths` 摘掉、把 SERVICE 层里该摘的模块摘掉，让被掏空的第三方 jar 仍能以普通 mod 身份加载。**放开与摘模块必须配对**，只放不开就会 duplicate module 炸启动 |

`AT`（accesstransformer）刻意**不过滤** —— 大家都靠它正常干活，滤了会炸。

## 补插与还原检测

后注册的 agent 会把我们插进去的那段冲掉，而在 `ArTransformer` 内部永远看不出来（`retransformClasses` 先把类还原成磁盘原始字节再重跑整条链）。所以：

- 链尾挂一个只读哨兵 `RevertSentinel`，站在**链的最终产物**上看还有没有闸口引用
- `ArWatchdog` 负责全表补插（解决"附加前就加载完的类"）与周期性复查，线程纪律：常驻 daemon 串行、分片 + 短睡、**绝不在 transform 回调里发起 retransform**（JVMTI 会自锁）
- 每轮开跑前把哨兵重新挂回链尾（晚于我们附加的 agent 会排在哨兵后面）

后台复查线程目前**默认不自启**（一轮要 retransform 上千个类，会把渲染线程卡在类加载锁上，真机表现是一阵一阵地卡）；接线仍然挂着，可以手动驱动。

## 命令

```
/ryjs allreturn            # 回显当前开关 + 补插/哨兵状态
/ryjs allreturn <true|false>
/ryjs timestop [true|false]
/ryjs setklass <entity|living|mob|monster> [目标]   # 需要 OP 2
/ryjs retrans player                                # 需要 OP 2
```

日志前缀 `[Intercept-AR]` / `[Intercept-Coex]`，stdout 与 slf4j 双通道（早期加载器还没有日志配置时 stdout 那份不会丢）。

## 自身内容

拦截层之外，mod 自己也带了一小层内容，主要靠自己的 mixin 配置挂进宿主渲染管线：

- **时停**：`TickRateManager` / `GameRenderer` / 粒子 / 声音 / 后处理链 / `UtilMillis` 一组冻结 mixin，开关走 `/ryjs timestop`。
- **外观**：自注册的 shader（core + post）、太极 / 宇宙风格的 HUD、tooltip、地面法阵与方块模型加载器，附带字体与图集。
- **死亡壳**：`/ryjs setklass` 按 entity / living / mob / monster 四档深度对目标生效；agent 侧另有一个按需注册的 `DeathShellTransformer`，记下目标类的原始字节再 retransform 换掉它，撤销时还原。
- **玩家保护 / 死亡记录**：`util.kp` 下的一套客户端-服务端成对状态。

## 构建

```
./gradlew build
```

## 参考代码与说明

- 自附加那一段（JNA 取 `JavaVM*` 再调 `Agent_OnAttach`，不占启动参数）参考了 **superstevemod** 的做法。
- 加载层、模块层与 `ClassFileTransformer` 的行为细节对照了 **OpenJDK 21** 与 **NeoForge** 的源码。
- 掏空的分档规则（返回类型分档、异常不吞、接口/枚举整体跳过）参考了另一处 C++ 侧 `allsuper` 实现的做法，本仓库不含那部分代码。
- 本项目在 NeoForge 1.21.1 上开发，落点行号 / 方法签名对 loader 4.0.4x 之外的版本没有做过验证，升级版本请重新核对了再改。
- 字节码改造与问题定位由 AI 助手 **Qwen3.8-flash** 协助完成。
- 只针对本地环境与自己的实例做实验，请遵守你所在服务器的规则。

## 许可证

GPL-3.0。
