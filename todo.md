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

## M2 编码（NBT 与编码路径）

> 移植 `PatternEncodingHelper.encode` 的 input-only 分支：处理模式且无输出 → 编码为 TunnelPattern；
> 若编码槽已放 TunnelPattern 物品则**复用其 UUID**，否则随机生成。

- [ ] **TP-200** 扩展 `ProcessingPatternEncoding`：新增 `TAG_TUNNEL`/`TAG_TUNNEL_UUID` 的读写；`encodeProcessingPattern` 支持空输出（input-only）；新增 `isInputOnly(tag)`、`getTunnelUuid(tag)` — 文件：`src/main/java/appeng/crafting/pattern/ProcessingPatternEncoding.java` — 验证：`ProcessingPatternItemTest` 扩展覆盖
- [ ] **TP-201** `PatternDetailsHelper` 新增 `encodeTunnelPattern(GenericStack[] in, UUID uuid, PatternInfo info)`（复用 `ProcessingPatternItem.encode` 底层），并保留 `encodeProcessingPattern` 现有语义（向后兼容，无输出仍抛异常） — 文件：`src/main/java/appeng/api/crafting/PatternDetailsHelper.java` — 验证：既有 `ProcessingPatternItemTest` 不回归
- [ ] **TP-202** `PatternEncodingTermMenu.encodeProcessingPattern`：当 `encodedPatternSlot` 内为 TunnelPattern 时允许零输出并复用其 UUID；否则维持"首个输出槽必填" — 文件：`src/main/java/appeng/menu/me/items/PatternEncodingTermMenu.java` — 验证：仿真/单测覆盖两种分支；既有行为不回归
- [ ] **TP-203** 编码前校验与"从物品载入"兼容：`EncodingMode.PROCESSING` 下载入 TunnelPattern 物品时 `PatternEncodingLogic` 能正确回填输入、清空输出 — 文件：`src/main/java/appeng/parts/encoding/PatternEncodingLogic.java`（按需） — 验证：单测断言载入往返

---

## M3 模式解析（AETunnelPattern + Decoder）

> 移植 `UltimatePatternHelper` 的 input-only 语义：必须有输入、必须无输出、必须有 UUID，否则判 InvalidPattern。

- [ ] **TP-300** 新增 `appeng/crafting/pattern/AETunnelPattern.java implements IPatternDetails`：
  - `isCraftable()` 返回 false；`getOutputs()` 返回空数组；`getInputs()` 返回浓缩输入
  - 新增方法（或接口默认实现）：`isInputOnly()`、`getInputOnlyUuid()`，并在 `IPatternDetails` 增加默认方法（默认 false/null，保证第三方实现兼容）
  - 构造校验：输入非空、输出为空、UUID 合法，否则抛异常（与现有 decode 失败语义一致）
  - 文件：`src/main/java/appeng/crafting/pattern/AETunnelPattern.java`、`src/main/java/appeng/api/crafting/IPatternDetails.java` — 验证：decode 单测覆盖合法/非法三种输入（无输入、有输出、UUID 缺失/非法）
- [ ] **TP-301** `AEPatternDecoder` / `TunnelPatternItem.decode` 接入：tunnel NBT 存在 → 返回 `AETunnelPattern`；不存在 → 走原 `AEProcessingPattern` 路径（向后兼容） — 文件：`src/main/java/appeng/crafting/pattern/AEPatternDecoder.java`（如需要）、`src/main/java/appeng/items/misc/TunnelPatternItem.java` — 验证：`PatternDetailsHelper.decodePattern` 单测

---

## M4 索引与运行时展开

> 移植 `CraftingGridCache.inputOnlyPatterns`（UUID → 模式）与 `TunnelPatternExpander`（递归内联 + 防环/防溢出）。

- [ ] **TP-400** `NetworkCraftingProviders`：新增 `Map<UUID, IPatternDetails> inputOnlyPatterns`；重建时 input-only 模式按 UUID 入表、**不**进入 `craftableItems`；暴露 `getInputOnlyPattern(UUID)`；模式移除时同步清理 — 文件：`src/main/java/appeng/me/service/helpers/NetworkCraftingProviders.java`、`src/main/java/appeng/me/service/CraftingService.java`（透传） — 验证：`NetworkCraftingProvidersTest` 扩展
- [ ] **TP-401** 移植 `TunnelPatternExpander`：`expandInputs(IAEStack/GenericStack 列表, 查找函数, 父模式集合)`：
  - 输入为 TunnelPattern 物品 → 按 UUID 查目标 input-only 模式，取其浓缩输入递归展开，数量乘以引用堆叠数（`Math.multiplyExact` 防溢出）
  - 防环：展开栈 + 父模式链检测；目标缺失/非 input-only → 按 1.7.10 语义处理（保留原输入或失败，按设计文档确定）
  - 文件：`src/main/java/appeng/crafting/pattern/TunnelPatternExpander.java`（新） — 验证：纯函数单测（多级引用、循环引用、乘数溢出、UUID 缺失）
- [ ] **TP-402** 接入 `CraftingTreeProcess` 子节点构建：将 `details.getInputs()` 中的 TunnelPattern 输入先经展开器替换为内联输入再建子节点；循环引用在树层再次兜底（复用/扩展 `notRecursive`） — 文件：`src/main/java/appeng/crafting/CraftingTreeProcess.java`、必要时 `CraftingTreeNode.java` — 验证：`CraftingSimulationTest` 新增场景（见 M7）
- [ ] **TP-403** 仿真状态正确性：展开后的输入走 `CraftingSimulationState`/`ChildCraftingSimulationState` 计入与回滚（含 byproduct 语义不变） — 文件：`src/main/java/appeng/crafting/inv/*`（如涉及） — 验证：仿真测试断言计数与回滚

---

## M5 CPU 预检与执行联动

> 移植 `CraftingCPUCluster.getExpandedCondensedInputs/getExpandedInputs` 的预检语义：
> 作业启动前确认展开后的输入可提取；执行时不再推送 TunnelPattern 物品本身到机器。

- [ ] **TP-500** CPU 预检：`CraftingCpuHelper`（或 `CraftingCpuLogic` 启动路径）对处理模式先做展开再检查输入可提取（`canCraft` 等价物） — 文件：`src/main/java/appeng/crafting/execution/CraftingCpuHelper.java`（如涉及） — 验证：单测/仿真断言"引用缺失 UUID 时作业拒绝启动"
- [ ] **TP-501** 输入推送排除虚拟项：`PatternProviderLogic`/`PatternProviderBlockEntity` 推送模式输入时跳过 TunnelPattern 物品（其为纯虚拟引用，不产生真实堆叠） — 文件：`src/main/java/appeng/blockentity/crafting/PatternProviderBlockEntity.java`（及配套 logic 类） — 验证：仿真断言机器收到的输入不含 tunnel 物品

---

## M6 工具提示与本地化

> 移植 `GuiText.TunnelPatternInfo1~4` 与 `ItemEncodedPattern.addInformation` 分支。

- [ ] **TP-600** 工具提示：`TunnelPatternItem`/`EncodedPatternItem` 的 `appendHoverText` 增加 tunnel 信息（4 行说明 + UUID） — 文件：`src/main/java/appeng/crafting/pattern/EncodedPatternItem.java`（或 TunnelPatternItem） — 验证：截图/单测断言 hover 行
- [ ] **TP-601** 本地化：`assets/ae2/lang/en_gb.json` 增加 `item.ae2.tunnel_pattern` 及 4 条提示键（参考 1.7.10 英文文案）；同步 `zh_cn.json` 中文翻译 — 文件：`src/main/resources/assets/ae2/lang/en_gb.json`、`zh_cn.json` — 验证：datagen/spotless json 通过；游戏内中文显示正确

---

## M7 测试

- [ ] **TP-700** 单元测试：NBT 编解码往返、`AETunnelPattern` 非法输入拒绝、`TunnelPatternExpander` 纯函数（多级/循环/溢出/缺失） — 文件：`src/test/java/appeng/crafting/pattern/`（新增 `TunnelPatternItemTest.java`、`TunnelPatternExpanderTest.java`） — 验证：`./gradlew test` 全绿
- [ ] **TP-701** 仿真测试：`CraftingSimulationTest` 新增场景——引用内联、乘数、多级引用、循环引用拒绝、目标非 input-only 回退、模拟失败回滚；`SimulationEnv` 支持注册 input-only 模式 — 文件：`src/test/java/appeng/crafting/simulation/CraftingSimulationTest.java`、`src/test/java/appeng/crafting/simulation/helpers/`（新增 `TunnelPatternBuilder` 或扩展 `ProcessingPatternBuilder`） — 验证：`./gradlew test` 全绿
- [ ] **TP-702** 注册/资源测试：`TUNNEL_PATTERN` 可被 `ItemStorage` 编目、datagen 无 diff、lang 键存在 — 文件：对应现有 registration/lang 测试 — 验证：`./gradlew runData && git diff --exit-code`

---

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
