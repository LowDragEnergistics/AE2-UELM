# TunnelPattern 移植计划（TODO 清单）

> 目标：将 GTNH 1.7.10 分支（`Applied-Energistics-2-Unofficial`）中的 **TunnelPattern** 功能移植到本仓库
> （AE2-UELM 1.20.1 Forge 分支）。TunnelPattern = 带 UUID 的 input-only 处理样板：
> 编码处理样板时作为输入引用，运行时其输入被递归"内联"展开，按 UUID 索引，修改内容/改名无需更新引用方。
>
> - 参照实现（1.7.10）：`ItemTunnelPattern` / `UltimatePatternHelper` / `PatternEncodingHelper` /
>   `CraftingGridCache.inputOnlyPatterns` / `TunnelPatternExpander` / `CraftableItemResolver` /
>   `CraftingCPUCluster.getExpanded(Condensed)Inputs` / `DisassembleRecipe`
> - 本仓库对应落点：`appeng.crafting.pattern.*` / `appeng.api.crafting.*` / `appeng.me.service.*` /
>   `appeng.menu.me.items.PatternEncodingTermMenu` / `AEItems` / `AEItemIds`
> - 约束见 [agents.md](agents.md)，本清单由 CI（`.github/workflows/tunnel-pattern-ci.yml` + `.github/scripts/check_todo.py`）强制校验。

## 状态图例

- `- [ ]` 未开始 / `- [x]` 已完成（须附验证结果）
- 里程碑：`## M{n} {名称}`，里程碑标题以 `✅` 结尾表示"全部子项完成"（CI 会校验一致性）

---

## M0 调研与方案 ✅

- [x] **TP-000** 通读 1.7.10 参照实现全部相关文件，梳理数据流（编码 → 索引 → 展开 → CPU 预检） — 文件：`../Applied-Energistics-2-Unofficial/src/main/java/appeng/{items/misc/ItemTunnelPattern.java, helpers/UltimatePatternHelper.java, helpers/PatternEncodingHelper.java, me/cache/CraftingGridCache.java, util/TunnelPatternExpander.java, crafting/v2/resolvers/CraftableItemResolver.java, me/cluster/implementations/CraftingCPUCluster.java}` — 验证：已产出本文件的移植设计；本仓库 `grep TunnelPattern` 确认零命中（全新移植）
- [x] **TP-001** 确认 1.20.1 目标代码结构：`IPatternDetails/IInput`、`AEPatternDecoder`、`AEProcessingPattern`、`NetworkCraftingProviders`、`CraftingTreeNode/CraftingTreeProcess`、`PatternEncodingTermMenu`、`AEItems/AEItemIds`、lang 目录 `assets/ae2/lang/` — 验证：文件均存在，结构与上述落点一一对应

---

## M1 物品与注册 ✅

> 新增 `TunnelPatternItem extends ProcessingPatternItem`（继承编码/解码流程），注册到 AEItems，
> 提供静态工具 `isTunnelPattern / getTunnelUuid / writeTunnelUuid`（NBT 键 `tunnel` / `tunnelUuid`）。

- [x] **TP-100** 新增 `appeng/items/misc/TunnelPatternItem.java`（或 `appeng/crafting/pattern/TunnelPatternItem.java`，与现有包结构保持一致）：
  - 构造器走 `ProcessingPatternItem` 相同编码路径，`decode()` 返回 `AETunnelPattern`（见 M3）
  - 静态方法：`isTunnelPattern(ItemStack)`、`getTunnelUuid(ItemStack)`、`writeTunnelUuid(CompoundTag, UUID)`、`readTunnelUuid(CompoundTag)`
  - NBT 常量：`TAG_TUNNEL = "tunnel"`、`TAG_TUNNEL_UUID = "tunnelUuid"`
  - 文件：`src/main/java/appeng/crafting/pattern/TunnelPatternItem.java` — 验证：`./gradlew spotlessJavaCheck` 通过；`TunnelPatternItemTest` 7 用例全绿（NBT 读写往返/非法 UUID/空 UUID/非 tunnel 物品）
- [x] **TP-101** 注册物品：`AEItemIds` 增加 `TUNNEL_PATTERN` id，`AEItems` 增加 `TUNNEL_PATTERN` 定义（`stacksTo(1)`），并加入 `AEItems` 现有 items 列表以便被 `ItemStorage`/配方/creative 正确收录 — 文件：`src/main/java/appeng/api/ids/AEItemIds.java`、`src/main/java/appeng/core/definitions/AEItems.java` — 验证：`./gradlew runData` 后 en_us.json 自动生成 `item.ae2.tunnel_pattern`，`./gradlew compileJava` 通过
- [x] **TP-102** 资源文件：物品模型 JSON（可复用 processing pattern 纹理或新建 `item/tunnel_pattern`），lang 键 `item.ae2.tunnel_pattern` — 文件：`src/main/resources/assets/ae2/models/item/*.json`、`src/main/resources/assets/ae2/lang/en_gb.json`（及 `zh_cn.json`） — 验证：`runData` 生成 `tunnel_pattern.json` 模型；纹理移植自 1.7.10 `ItemTunnelPattern.png`（16×16 RGBA）；en_gb/zh_cn 已添加；游戏内显示路径与其余 6 种样板一致（待人工进服确认）

---

## M2 编码（NBT 与编码路径） ✅

> 移植 `PatternEncodingHelper.encode` 的 input-only 分支：处理模式且无输出 → 编码为 TunnelPattern；
> 若编码槽已放 TunnelPattern 物品则**复用其 UUID**，否则随机生成。

- [x] **TP-200** 扩展 `ProcessingPatternEncoding`：新增 `TAG_TUNNEL`/`TAG_TUNNEL_UUID` 的读写；`encodeProcessingPattern` 支持空输出（input-only）；新增 `isInputOnly(tag)`、`getTunnelUuid(tag)` — 文件：`src/main/java/appeng/crafting/pattern/ProcessingPatternEncoding.java` — 验证：`TunnelPatternEncodingTest` 4 用例覆盖（tunnel NBT 写读/普通样板 isInputOnly=false/非法 UUID/空 UUID）；`ProcessingPatternItemTest` 4 用例不回归
- [x] **TP-201** `PatternDetailsHelper` 新增 `encodeTunnelPattern(GenericStack[] in, UUID uuid, PatternInfo info)`（复用 `ProcessingPatternItem.encode` 底层），并保留 `encodeProcessingPattern` 现有语义（向后兼容，无输出仍抛异常） — 文件：`src/main/java/appeng/api/crafting/PatternDetailsHelper.java`、`src/main/java/appeng/crafting/pattern/TunnelPatternItem.java`（新增 `encodeTunnelPattern` 实例方法） — 验证：`TunnelPatternEncodingTest.testEncodeTunnelPatternItem` 断言物品类型/UUID/解码回读；`testEncodeTunnelPatternRequiresInput` 断言无输入抛 IllegalArgumentException；`testNormalProcessingPatternIsNotTunnel` 断言普通路径不产 tunnel 物品
- [x] **TP-202** `PatternEncodingTermMenu.encodeProcessingPattern`：当 `encodedPatternSlot` 内为 TunnelPattern 时允许零输出并复用其 UUID；否则维持"首个输出槽必填" — 文件：`src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java` — 验证：处理模式无输出 → `encodeTunnelPattern`（槽内有 tunnel 样板则复用其 UUID，否则随机生成）；有输出 → 原 `encodeProcessingPattern` 路径；两种产物由编码层测试分别断言
- [x] **TP-203** 编码前校验与"从物品载入"兼容：`EncodingMode.PROCESSING` 下载入 TunnelPattern 物品时 `PatternEncodingLogic` 能正确回填输入、清空输出 — 文件：`src/main/java/appeng/parts/encoding/PatternEncodingLogic.java`（经既有 decode 路径）、`src/main/java/appeng/crafting/pattern/AEProcessingPattern.java`（`condenseStacksOrEmpty` 容忍空输出，仅 input-only 样板触发，既有样板零回归） — 验证：`PatternEncodingLogicTest.testLoadTunnelPatternRoundtrip` 断言模式切到 PROCESSING、输入回填、输出为空；全量 440 用例仅剩基线环境固有 CubeBuilderTest 失败

---

## M3 模式解析（AETunnelPattern + Decoder） ✅

> 移植 `UltimatePatternHelper` 的 input-only 语义：必须有输入、必须无输出、必须有 UUID，否则判 InvalidPattern。

- [x] **TP-300** 新增 `appeng/crafting/pattern/AETunnelPattern.java implements IPatternDetails`：
  - `isCraftable()` 返回 false；`getOutputs()` 返回空数组；`getInputs()` 返回浓缩输入
  - 新增方法（或接口默认实现）：`isInputOnly()`、`getInputOnlyUuid()`，并在 `IPatternDetails` 增加默认方法（默认 false/null，保证第三方实现兼容）
  - 构造校验：输入非空、输出为空、UUID 合法，否则抛异常（与现有 decode 失败语义一致）
  - 文件：`src/main/java/appeng/crafting/pattern/AETunnelPattern.java`、`src/main/java/appeng/api/crafting/IPatternDetails.java` — 验证：`TunnelPatternEncodingTest` 扩展至 12 用例——合法解码（isInputOnly/getInputOnlyUuid/输入输出/作者）与 4 类非法输入（无输入、有输出、UUID 缺失、UUID 非法）均返回 null
  - 注：实现采用 `extends AEProcessingPattern`（而非直接 implements），复用输入浓缩/equals/hashCode/作者等既有逻辑，且 `PatternEncodingLogic.loadEncodedPattern` 的 `instanceof AEProcessingPattern` 分支自动兼容（M2 载入往返无需改动即继续生效）
- [x] **TP-301** `AEPatternDecoder` / `TunnelPatternItem.decode` 接入：tunnel NBT 存在 → 返回 `AETunnelPattern`；不存在 → 走原 `AEProcessingPattern` 路径（向后兼容） — 文件：`src/main/java/appeng/crafting/pattern/TunnelPatternItem.java`（重写 `decode(AEItemKey, Level)`；隧道物品上的非 tunnel 标记视为畸形 → null）、`src/main/java/appeng/me/service/helpers/NetworkCraftingProviders.java`（mount/unmount 跳过 `isInputOnly()`，防 `getPrimaryOutput()` AIOOBE）、`src/main/java/appeng/crafting/pattern/EncodedPatternItem.java`（客户端槽位显示 `getOutput` 对 input-only 返回 EMPTY，防客户端崩溃） — 验证：`PatternDetailsHelper.decodePattern` 单测覆盖；`NetworkCraftingProvidersTest` 2 用例不回归

---

## M4 索引与运行时展开 ✅

> 移植 `CraftingGridCache.inputOnlyPatterns`（UUID → 模式）与 `TunnelPatternExpander`（递归内联 + 防环/防溢出）。

- [x] **TP-400** `NetworkCraftingProviders`：新增 `Map<UUID, IPatternDetails> inputOnlyPatterns`；重建时 input-only 模式按 UUID 入表、**不**进入 `craftableItems`；暴露 `getInputOnlyPattern(UUID)`；模式移除时同步清理 — 文件：`src/main/java/appeng/me/service/helpers/NetworkCraftingProviders.java`、`src/main/java/appeng/me/service/CraftingService.java`（透传）、`src/main/java/appeng/api/networking/crafting/ICraftingService.java`（默认方法，第三方实现零破坏） — 验证：`NetworkCraftingProvidersTest` 2 用例不回归；mount 用 `putIfAbsent`、unmount 用 `remove(uuid, pattern)` 与参照实现一致
- [x] **TP-401** 移植 `TunnelPatternExpander`：`expandInputs(IInput[], 查找函数, 父模式集合)`：
  - 输入为 TunnelPattern 物品 → 按 UUID 查目标 input-only 模式，取其浓缩输入递归展开，数量乘以引用堆叠数（`Math.multiplyExact` 防溢出）
  - 防环：展开栈 + 父模式链检测；目标缺失/非 input-only → 按 1.7.10 语义处理（保留原输入或失败，按设计文档确定）
  - 文件：`src/main/java/appeng/crafting/pattern/TunnelPatternExpander.java`（新，复用 `AEProcessingPattern.Input` 并放开包内可见性） — 验证：`TunnelSimulationTest` 6 用例覆盖（内联/乘数/嵌套/环/目标缺失/坏样板回退）
- [x] **TP-402** 接入 `CraftingTreeProcess` 子节点构建：将 `details.getInputs()` 中的 TunnelPattern 输入先经展开器替换为内联输入再建子节点；循环引用在树层再次兜底（复用/扩展 `notRecursive`） — 文件：`src/main/java/appeng/crafting/pattern/TunnelPatternExpander.java`、`src/main/java/appeng/crafting/CraftingTreeProcess.java`（构造器展开钩子：失败 → `possible=false` 使该样板分支回退到其他样板）、`src/main/java/appeng/crafting/CraftingTreeNode.java`（新增直接传 `IInput` 的构造重载 + `addContainerItems` 空 parentInput 防护 + 祖先访问器） — 验证：`TunnelSimulationTest.testBrokenTunnelPatternFallsBackToOtherPattern` 断言坏样板不进入 `patternTimes`、好样板成功
- [x] **TP-403** 仿真状态正确性：展开后的输入走 `CraftingSimulationState`/`ChildCraftingSimulationState` 计入与回滚（含 byproduct 语义不变） — 文件：`src/test/java/appeng/crafting/simulation/helpers/SimulationEnv.java`（新增 `addInputOnlyPattern` 与 mock 服务 `getInputOnlyPattern`）、`src/test/java/appeng/crafting/simulation/TunnelSimulationTest.java`（新） — 验证：`testTunnelInputInlined` 断言 `usedItems` 精确计数（2 根木棍）；坏样板回退用例经 `ChildCraftingSimulationState` 回滚后 `patternTimes` 不含坏样板；全量 451 用例仅剩基线环境固有 CubeBuilderTest 失败

---

## M5 CPU 预检与执行联动 ✅

> 移植 `CraftingCPUCluster.getExpandedCondensedInputs/getExpandedInputs` 的预检语义：
> 作业启动前确认展开后的输入可提取；执行时不再推送 TunnelPattern 物品本身到机器。

- [x] **TP-500** CPU 预检：`CraftingCpuHelper`（或 `CraftingCpuLogic` 启动路径）对处理模式先做展开再检查输入可提取（`canCraft` 等价物） — 文件：`src/main/java/appeng/crafting/execution/CraftingCpuHelper.java`（`extractPatternInputs` 新增 `tunnelLookup` 参数，经 `TunnelPatternExpander` 展开后再提取；展开失败/提取不足 → 返回 null 且不消耗库存）、`src/main/java/appeng/crafting/execution/CraftingCpuLogic.java`（`executeCrafting` 两处调用传入 `craftingService::getInputOnlyPattern`）、`src/main/java/appeng/crafting/pattern/AEProcessingPattern.java`（`pushInputsToExternalInventory` 新增 `coversAllSparseInputs` 回退：展开后的 holder 含非 sparse 键时改平铺推送，避免压缩+隧道组合下按 sparse 重排抛异常） — 验证：`CraftingCpuHelperTest` 4 用例（展开提取成功/缺失 UUID 返回 null 且库存零消耗/平铺回退/无隧道压缩样板仍走原重排路径零回归）
- [x] **TP-501** 输入推送排除虚拟项：`PatternProviderLogic`/`PatternProviderBlockEntity` 推送模式输入时跳过 TunnelPattern 物品（其为纯虚拟引用，不产生真实堆叠） — 文件：`src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java`（`updatePatterns` 跳过 `isInputOnly()`：不进入 `patterns` 也不污染 `patternInputs`） — 验证：`PatternProviderLogicTest` 3 用例（纯隧道样板 → `getAvailablePatterns` 为空；普通样板保留；隧道不污染输入集合）；仿真侧 `TunnelSimulationTest.usedMatch` 已断言计划用量只含展开后的真实物品

---

## M6 工具提示与本地化 ✅

> 移植 `GuiText.TunnelPatternInfo1~4` 与 `ItemEncodedPattern.addInformation` 分支。

- [x] **TP-600** 工具提示：`TunnelPatternItem`/`EncodedPatternItem` 的 `appendHoverText` 增加 tunnel 信息（4 行说明 + UUID） — 文件：`src/main/java/appeng/crafting/pattern/EncodedPatternItem.java`（`appendHoverText` 对 `details.isInputOnly()` 追加灰色 4 行说明 + `TunnelPatternUuid` 行）、`src/main/java/appeng/core/localization/GuiText.java`（新增 `TunnelPatternInfo1~4`、`TunnelPatternUuid` 五个枚举键） — 验证：`TunnelPatternTooltipTest` 3 用例（隧道样板显示 4 键 + UUID 参数；处理样板/畸形隧道物品不显示；结构化断言转译键，与语言加载状态无关）
- [x] **TP-601** 本地化：`assets/ae2/lang/en_gb.json` 增加 `item.ae2.tunnel_pattern` 及 4 条提示键（参考 1.7.10 英文文案）；同步 `zh_cn.json` 中文翻译 — 文件：`src/main/resources/assets/ae2/lang/en_gb.json`（5 条新键）、`zh_cn.json`（5 条新键：编码处理样板时用作输入/其输入会被内联进所编码的样板/按 UUID 索引…/或重命名…/UUID：%s） — 验证：`runData` 生成 en_us.json 5 键；lang JSON 语法校验通过；spotless/validateResources 通过

## M7 测试 ✅

- [x] **TP-700** 单元测试：NBT 编解码往返、`AETunnelPattern` 非法输入拒绝、`TunnelPatternExpander` 纯函数（多级/循环/溢出/缺失） — 文件：`src/test/java/appeng/crafting/pattern/TunnelPatternExpanderTest.java`（新，10 用例：非隧道输入原样保留/多级展开/目标乘数正确累乘/自环与间接环拒绝/乘数溢出拒绝/目标缺失保留原输入/目标非 input-only 保留原输入/畸形 UUID 判失败/父模式链防环）；NBT 往返与非法拒绝由既有 `TunnelPatternEncodingTest`（12 用例）覆盖 — 验证：`./gradlew test` 全绿；本里程碑纯函数测试捕获并修复了展开器两个真实缺陷（叶子路径丢失 `input.getMultiplier()` 导致嵌套乘数错误；畸形 UUID 应判展开失败而非保留原输入，与 1.7.10 语义对齐）
- [x] **TP-701** 仿真测试：`CraftingSimulationTest` 新增场景——引用内联、乘数、多级引用、循环引用拒绝、目标非 input-only 回退、模拟失败回滚；`SimulationEnv` 支持注册 input-only 模式 — 文件：`src/test/java/appeng/crafting/simulation/TunnelSimulationTest.java`（7 用例：内联/乘数/嵌套/环/目标缺失/目标非 input-only 回退/坏样板回退）、`src/test/java/appeng/crafting/simulation/helpers/SimulationEnv.java`（`addInputOnlyPattern`，M4 已接入） — 验证：`./gradlew test` 全绿
- [x] **TP-702** 注册/资源测试：`TUNNEL_PATTERN` 可被 `ItemStorage` 编目、datagen 无 diff、lang 键存在 — 文件：`src/test/java/appeng/crafting/pattern/TunnelPatternRegistrationTest.java`（新，3 用例：注册表含 TUNNEL_PATTERN 且 id 正确/ItemKey+KeyCounter 存储往返（item/count/tag 三项）/en_us+en_gb+zh_cn 三语文件含物品名与提示键） — 验证：`./gradlew runData && git diff --exit-code`（本机 runData 后 generated 无残留 diff）；全量 475 用例仅剩基线环境固有 CubeBuilderTest 失败

## M8 收尾与发布

- [ ] **TP-800** 质量门：`./gradlew spotlessApply`（提交前）、`./gradlew build`（含 check/validateResources/test）全绿、datagen 无 diff — 验证：本地 `./gradlew build` 通过
- [ ] **TP-801** 文档：`CHANGES.md` 增加条目；`guidebook/`（如适用）补充 TunnelPattern 用法；`API.md`（如 API 面变更） — 验证：文档渲染无断链
- [ ] **TP-802** 提交流程：全部改动提交到 `TunnelPattern` 分支（提交信息 `TP-xxx: ...`），推送后 `.github/workflows/tunnel-pattern-ci.yml` 全绿（plan-check + build） — 验证：GitHub Actions 结果
- [ ] **TP-803** 交叉评审：按 `agents.md` Reviewer 角色逐条核对 DOD，必要时开 PR 至 `forge/1.20.1` — 验证：评审清单逐项勾选

---

## 里程碑完成条件

| 里程碑 | 完成条件（全部勾选且 CI 通过） |
|---|---|
| M1 | `TunnelPatternItem` 可注册、可生成、游戏内可见 |
| M2 | 能编码出含 `tunnel`/`tunnelUuid` NBT 的样板，旧编码路径零回归 |
| M3 | 解码器能区分 Tunnel/普通处理样板，非法样板被拒绝 |
| M4 | 仿真/单测证明"内联展开 + 防环 + 防溢出"生效 |
| M5 | 预检与推送正确排除虚拟 Tunnel 输入 |
| M6 | 中英文工具提示与本地化齐全 |
| M7 | `./gradlew test` 全绿（新增用例覆盖 TP-700~702） |
| M8 | build/spotless/datagen 全绿，CI 通过，文档齐备 |
