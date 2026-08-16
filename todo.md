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

## M1 核心循环计算引擎（纯 Java + 单测） ✅

- [x] **LC-010** 引擎数据结构：`LoopFiring`（inputs/outputs/count + netChange）、`CycleBalance`
  （minimumSeed + netChange）、`CyclePlan`（不可变记录，构造时全量守恒校验：aggregateFirings=期望、
  netChange=精确效果、initialInputs 覆盖 seed、schedule 模拟 finalBalance 非负） —
  验证：`appeng/crafting/cycle/LoopFiring.java`/`CyclePlan.java`；单测 `testAccountingValidation`
- [x] **LC-011** 确定性循环规划器 `DeterministicCyclePlanner`：cycleBalance（前缀赤字 → minimumSeed）、
  repetitions=ceilDivide(requiredNet,targetEffect)（FINAL_TOTAL 时 max(1) 且补 target contribution）、
  repeatedMinimumSeed（负净变化 × (N-1)）、库存/可生产输入校验 → CyclePlan 或输入不足诊断 —
  验证：`DeterministicCyclePlannerTest` 13 用例全绿（NET_NEW/FINAL_TOTAL、双步环、可生产输入覆盖）
- [x] **LC-012** 仿射压缩调度器 `AffineRepeatScheduler`：rotation 轮转选择最大可执行批
  （maximumExecutableCycles 用 slope/margin 区间收缩）、批量应用守恒、状态上限中止、相邻同类 batch 合并 —
  验证：`testScheduleIsLogarithmicInRepetitions`（10^12 次重复 <128 批）
- [x] **LC-013** 引擎单元测试（JUnit）：单自环（A+B→2A）、多步环（A→B, B+C→2A）、
  FINAL_TOTAL vs NET_NEW 语义、非生产性/收缩循环拒绝、输入不足诊断、BigInteger 10^60 无溢出 —
  验证：13/13 全绿
- [x] **LC-014** 引擎与 AE 类型解耦：仅依赖 `AEKey`/`GenericStack`/`AEItemKey`，不引用 Block/Menu/GUI 类 —
  验证：编译依赖检查通过
- [x] **LC-015** 隧道样板兼容（额外目标）：`CyclePatterns` 将处理样板转为 firing，输入中的隧道引用经
  `TunnelPatternExpander` 展开为具体内容（改隧道内容无需改引用样板）；未解析引用/隧道环/畸形 UUID 拒绝；
  隧道参与多步自环（1 stick+1 torch→2 torch）端到端计划 — 验证：`CyclePatternsTest` 7 用例全绿

## M2 设备注册与网络接入 ✅

- [x] **LC-020** `SelfLoopMatrixBlock extends AEBaseEntityBlock`（metalProps，右键开菜单）+
  `SelfLoopMatrixBlockEntity extends AENetworkBlockEntity`（GridNode 挂载 ME 网络、
  `ServerTickingBlockEntity` 变更重算、idle power 1.0） — 验证：`SelfLoopMatrixRegistrationTest`
- [x] **LC-021** 注册链路：`AEBlockIds.SELF_LOOP_MATRIX`、`AEBlocks`（block()）、
  `AEBlockEntities`（create）→ `InitItems/InitBlockEntities/InitMenuTypes/InitScreens` 接入；
  方块状态/物品模型/战利品表/配方/advancement datagen 产物齐备 — 验证：runData + 注册测试
- [x] **LC-022** 内部库存：9 槽循环样板（`ProcessingPatternItem` 含 TunnelPatternItem）+ 1 目标槽，
  `AppEngInternalInventory` + NBT 持久化；内容变更触发重算 — 验证：`SelfLoopMatrixBlockEntityTest`
  testInventoryRoundTrip

## M3 菜单与界面（计算设备交互） ✅

- [x] **LC-030** `SelfLoopMatrixMenu extends AEBaseMenu`：9 样板槽 + 目标槽 + 数量（client action）
  + NET_NEW/FINAL_TOTAL 切换；@GuiSync 同步 planStatus/planSummary/requestedAmount/quantityMode；
  `InitMenuTypes` 注册 — 验证：菜单类型注册测试
- [x] **LC-031** `SelfLoopMatrixScreen`：显示服务器构建的计划摘要（净变化/种子/重复次数/调度/缺失）、
  数量输入框、模式切换按钮 — 验证：Screen 注册进 InitScreens、样式 JSON 校验
- [x] **LC-032** 交互同步：client action 走 `registerClientAction`/`sendClientAction`（AE2 既有通道，
  无需新增包）；服务器 tick 重算 + `broadcastChanges` 推送 — 验证：菜单广播逻辑
- [x] **LC-033** 本地化：`GuiText.SelfLoopMatrix/Target/Amount` + en_us（datagen 生成）+ zh_cn 手写；
  屏幕标签 — 验证：`testLangKeysPresent`（en_us/zh_cn）

## M4 数据生成、文档与收尾 ✅

- [x] **LC-040** datagen：方块状态/物品模型/语言/合成配方（计算/工程/逻辑处理器 + 处理样板）；
  guidebook 页面（`guidebook/ae2-mechanics/self-loop-matrix.md` + `_zh_cn`） —
  验证：runData 无 diff、产物齐备（blockstate/models/loot/recipe/advancement/en_us）
- [x] **LC-041** 文档：CHANGES.md 记录本功能 — 验证：lint
- [x] **LC-042** 全量验证：`./gradlew test runData validateResources spotlessJavaCheck -x spotlessJson`
  （507 用例仅剩基线 CubeBuilderTest 环境失败）；CI `TunnelPattern CI` + `Build and Test` 全绿、
  PR #9 检查通过 — 验证：CI 徽章
- [x] **LC-043** 版本管理：功能 → MINOR `15.5.3 → 15.6.0`；tag `forge/v15.6.0-uelm` 已推送；
  PR #9 已并入 `forge/1.20.1`；Release v15.6.0-uelm 已发布（产物
  `appliedenergistics2-forge-15.6.0-uelm[-api|-javadoc].jar`，实测 jar 内 mods.toml `version="15.6.0"`、
  含 8 个 self_loop_matrix 资源） — 验证：发布页 + 产物实测
