# Loop-Crafting 计划（TODO 清单）

> 目标：研读 `../DataEnergistics/`（1.21.1 NeoForge，AE2 数据处理/大型合成扩展）对**自循环配方**等特殊
> 处理的数学方法，并在本仓库（AE2-UELM 1.20.1 Forge）创建一个 **ME 自循环矩阵**（ME Self-Loop Matrix）
> 作为**计算设备**：挂载于 ME 网络，对插入的循环样板执行确定性循环规划（净变化/最小种子/重复次数/
> 仿射压缩调度），在界面展示完整可执行计划。
>
> - 参照实现（DataEnergistics）：`common/crafting/trinity/planning/algorithm/cycle/deterministic/`
>   （`TrinityDeterministicCyclePlanner` / `TrinityDeterministicFiringMath`）+
>   `algorithm/schedule/`（`AffineTrinityDeterministicRepeatScheduler` / `TrinityCompressedSchedule`）+
>   `graph/TrinityPatternVariant` + `execution/runtime/TrinityRemainingPlanCalculation`
> - 本仓库落点：`appeng.crafting.cycle.*`（新包）/ `appeng.block.*` / `appeng.blockentity.*` /
>   `appeng.menu.*` / `appeng.core.definitions.AEBlocks|AEItems|AEBlockEntities` /
>   `appeng.init.*` / `appeng.datagen.*`
> - 约束见 [agents.md](agents.md)，本清单由 CI（`.github/workflows/tunnel-pattern-ci.yml` plan-check +
>   `.github/scripts/check_todo.py`）强制校验；条目 ID 前缀 `LC-`（check_todo.py 已泛化为 `[A-Z]+-\\d+`）。

## 状态图例

- `- [ ]` 未开始 / `- [x]` 已完成（须附验证结果）
- 里程碑：`## M{n} {名称}`，里程碑标题以 `✅` 结尾表示"全部子项完成"（CI 会校验一致性）

---

## M0 调研与方案 ✅

- [x] **LC-000** 通读 DataEnergistics 自循环配方特殊处理：Trinity 合成系统的 cycle 规划体系
  （deterministic / joint / macro / MIP 四类算法）、SCC firing 向量、单循环净变化（netChange）、
  最小种子（minimumSeed = 最大前缀赤字）、重复次数（repetitions = ceil(requiredNet/targetEffect)）、
  仿射压缩调度（按 rotation 批量 + slope/margin 二分）、输入不足诊断分类
  （target cycle seed / net-consumed external input / cycle working seed）、运行期剩余计划重算
  （exponential backoff 防 busy loop） — 验证：本清单设计即产物；关键数学见下
- [x] **LC-001** 确认 1.20.1 落点结构：`AEBlocks/AEBlockEntities/AEItems/AEItemIds`、
  `InitBlocks/InitBlockEntities/InitItems/InitMenuTypes`、`AEBaseEntityBlock/AEBaseBlockEntity/
  AEBaseInvBlockEntity`、`AEBaseMenu`、lang `assets/ae2/lang/`、datagen（模型/语言/配方）、
  guidebook `guidebook/` — 验证：文件均存在，结构与落点一一对应
- [x] **LC-002** 确定功能范围与命名：**ME 自循环矩阵 = 计算设备**——不做自主执行/不注册合成服务，
  对插入的循环样板（processing pattern，输出包含于输入，或组成有向环）计算并展示计划；
  术语：firing（一次执行）、variant（模式变体：inputs/outputs/netChange）、cycle（一个完整生产环）、
  plan（repetitions + aggregateFirings + minimumSeed + initialInputs + netChange + schedule）

## M1 核心循环计算引擎（纯 Java + 单测） 

- [ ] **LC-010** 引擎数据结构：`LoopFiring`（variant + count）、`LoopVariant`（inputs/outputs/netChange，
  守恒校验 outputs-inputs）、`CycleBalance`（minimumSeed + netChange）、`CyclePlan`（不可变记录，
  构造时全量守恒校验：aggregateFirings=期望、netChange=精确效果、finalBalance 非负） — 验证：单测
- [ ] **LC-011** 确定性循环规划器 `DeterministicCyclePlanner`：cycleBalance（前缀赤字 → minimumSeed）、
  repetitions=ceilDivide(requiredNet,targetEffect)（FINAL_TOTAL 时 max(1) 且补 target contribution）、
  repeatedMinimumSeed（负净变化 × (N-1)）、库存/可生产输入校验 → CyclePlan 或输入不足诊断 — 验证：单测
- [ ] **LC-012** 仿射压缩调度器 `AffineRepeatScheduler`：rotation 轮转选择最大可执行批
  （maximumExecutableCycles 用 slope/margin 区间收缩）、批量应用守恒、状态上限/取消/超时中止、
  相邻同类 batch 合并 — 验证：单测（对数级 batch 数）
- [ ] **LC-013** 引擎单元测试（JUnit）：单自环（A+B→2A）、多步环（A→B, B→2A+废物）、
  FINAL_TOTAL vs NET_NEW 语义、非生产性循环拒绝（targetEffect≤0）、输入不足三分类诊断、
  计划/调度守恒反例、BigInteger 大数无溢出 — 验证：全绿
- [ ] **LC-014** 引擎与 AE 类型解耦：仅依赖 `AEKey`/`GenericStack`/`AEItemKey`，不引用 Block/Menu/
  GUI 类；供方块实体与测试共用 — 验证：编译依赖检查

## M2 设备注册与网络接入 

- [ ] **LC-020** `SelfLoopMatrixBlock extends AEBaseEntityBlock`（网格方块，8 向，无灯）+
  `SelfLoopMatrixBlockEntity extends AEBaseBlockEntity`（`AEGridBlock`/`GridNode` 挂载 ME 网络、
  `ServerTickingBlockEntity` 每 tick 计算刷新） — 验证：注册测试 + 手测网格状态
- [ ] **LC-021** 注册链路：`AEItemIds.SELF_LOOP_MATRIX`、`AEBlocks`（block()）+ `AEItems`（item）+
  `AEBlockEntities`（create）→ `InitBlocks/InitItems/InitBlockEntities` 接入；方块状态/物品模型
  datagen（`ItemModelProvider`） — 验证：runData 产物 + 注册测试
- [ ] **LC-022** 内部库存：9 槽循环样板槽（`ProcessingPatternItem` 及其子类如 TunnelPatternItem），
  `AEBaseInvBlockEntity` 库存适配 + 槽位语义；内容变更触发重算 — 验证：库存测试

## M3 菜单与界面（计算设备交互） 

- [ ] **LC-030** `SelfLoopMatrixMenu extends AEBaseMenu`：9 模式槽 + 目标请求输入（物品 + 数量）
  + 计算按钮 + 结果同步（guisync）；`InitMenuTypes` 注册 — 验证：菜单测试
- [ ] **LC-031** `SelfLoopMatrixScreen`：显示每周期净变化表、最小种子、重复次数、聚合 firing、
  调度批次（rotation × cycles）、缺失输入（含分类）、productive/non-productive 状态 —
  验证：Screen 加载测试
- [ ] **LC-032** 网络包：C2S 计算请求（目标/数量）、S2C 计划结果推送；Server 线程计算引擎调用 —
  验证：包注册测试
- [ ] **LC-033** 本地化：`GuiText`/lang en_gb/en_us/zh_cn + 状态文本 — 验证：语言文件 lint

## M4 数据生成、文档与收尾 

- [ ] **LC-040** datagen：方块状态/物品模型/语言/合成配方（本体 + 基础原料）；guidebook 页面
  （`guidebook/ae2-mechanics/self-loop-matrix.md` + `_zh_cn`，并入 index） — 验证：runData 无 diff
- [ ] **LC-041** 文档：`agents.md` §5 注明 `LC-` 前缀与 ID 泛化；CHANGES.md 记录本功能 —
  验证：plan-check + lint
- [ ] **LC-042** 全量验证：`./gradlew test runData validateResources spotlessJavaCheck -x spotlessJson`
  （本地 479+ 用例仅剩基线 CubeBuilderTest 环境失败）；CI `TunnelPattern CI` + `Build and Test` 全绿 —
  验证：CI 徽章
- [ ] **LC-043** 版本管理：功能 → MINOR `15.5.3 → 15.6.0`；tag `forge/v15.6.0-uelm`；PR 并入
  `forge/1.20.1`；发布 Release（产物 `appliedenergistics2-forge-15.6.0-uelm[-type].jar`，
  mods.toml 纯 `15.6.0`） — 验证：产物名 + 发布页
