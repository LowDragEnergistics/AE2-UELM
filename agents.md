# agents.md — TunnelPattern 移植代理约束

> 本文件为参与 **TunnelPattern 移植**（本仓库 AE2-UELM 1.20.1 Forge 分支）的所有 AI 代理/协作者定义
> 角色、硬性规则、文件所有权与完成定义（DOD）。任务清单见 [todo.md](todo.md)，
> 两者由 CI（`.github/workflows/tunnel-pattern-ci.yml`）强制校验。

---

## 1. 角色与职责

| 角色 | 职责 | 关键约束 |
|---|---|---|
| **Planner（规划者）** | 维护 `todo.md`/`agents.md`，划分里程碑，定义验收标准 | 不得在未更新 todo.md 的情况下变更范围 |
| **Implementer（实现者）** | 按 `todo.md` 条目逐项实现，提交信息标注条目 ID（`TP-xxx:`） | 只改该条目声明涉及的文件；一次只推进一个里程碑 |
| **Reviewer（评审者）** | 逐条核对 DOD，检查向后兼容、防环/防溢出、错误处理 | 只读评审，不直接改代码；发现问题回退给 Implementer |
| **Tester（测试者）** | 为 TP-700~702 编写单元/仿真测试并保证 `./gradlew test` 全绿 | 测试先行；仿真场景必须覆盖失败回滚 |
| **CI（流程守护）** | 执行 `tunnel-pattern-ci.yml`：plan-check + 构建门禁 | 任何提交必须通过 plan-check 才能进入构建阶段 |

## 2. 硬性规则（违规即打回）

1. **范围纪律**：只能修改 `todo.md` 中对应条目声明涉及的文件；未列入清单的改动（含"顺手重构"）一律禁止。
2. **分支纪律**：全部工作在 `TunnelPattern` 分支进行；禁止直接向 `forge/1.20.1` 提交（合并走评审 PR）。
3. **向后兼容**：所有既有模式（crafting/processing/smithing/stonecutting）的编解码语义不得改变；
   `encodeProcessingPattern` 原有"无输出即异常"行为在非 Tunnel 路径保持不变；`IPatternDetails` 只允许新增**默认方法**。
4. **NBT 规范**：Tunnel 标记固定为 `tunnel`（bool）与 `tunnelUuid`（String，UUID 格式）；
   任何地方不得另造键名；UUID 解析失败必须走"拒绝该样板"路径（InvalidPattern 语义），不得静默忽略。
5. **防环与防溢出**：运行时展开必须同时具备——展开栈防环、父模式链防环、`Math.multiplyExact` 防溢出；
   三者缺一不可，评审重点核对。
6. **样式**：遵循仓库 Spotless 配置（`codeformat/`），提交前必须 `./gradlew spotlessApply`；
   禁止通配符 import；文件须带 LGPL 许可头（与同包文件一致）。
7. **编码前校验**：编码路径（TP-200~203）必须覆盖——零输出时仅当编码槽为 TunnelPattern 才允许；
   载入（load-from-item）路径必须同步支持 Tunnel 样板。
8. **测试纪律**：每个里程碑至少包含一条可执行验证命令；仿真测试必须包含"失败回滚"断言，不得只测成功路径。

## 3. 文件所有权映射

> 各条目锁定的文件范围（超出即违反规则 1）：

| 条目 | 归属文件（主） | 归属文件（按需） |
|---|---|---|
| TP-100/101/102 | `appeng/items/misc/TunnelPatternItem.java`(新)、`appeng/api/ids/AEItemIds.java`、`appeng/core/definitions/AEItems.java`、`assets/ae2/models/item/`、`assets/ae2/lang/*.json` | `appeng/api/ids/*`、`appeng/core/definitions/*` |
| TP-200~203 | `appeng/crafting/pattern/ProcessingPatternEncoding.java`、`appeng/api/crafting/PatternDetailsHelper.java`、`appeng/menu/me/items/PatternEncodingTermMenu.java` | `appeng/parts/encoding/PatternEncodingLogic.java` |
| TP-300/301 | `appeng/crafting/pattern/AETunnelPattern.java`(新)、`appeng/api/crafting/IPatternDetails.java`、`TunnelPatternItem.decode` | `appeng/crafting/pattern/AEPatternDecoder.java` |
| TP-400~403 | `appeng/me/service/helpers/NetworkCraftingProviders.java`、`appeng/me/service/CraftingService.java`、`appeng/crafting/pattern/TunnelPatternExpander.java`(新)、`appeng/crafting/CraftingTreeProcess.java`、`appeng/crafting/CraftingTreeNode.java` | `appeng/crafting/inv/*` |
| TP-500/501 | `appeng/crafting/execution/CraftingCpuHelper.java`、`appeng/blockentity/crafting/PatternProviderBlockEntity.java`（及配套 logic） | `appeng/crafting/execution/CraftingCpuLogic.java` |
| TP-600/601 | `appeng/crafting/pattern/EncodedPatternItem.java`（或 TunnelPatternItem）、`assets/ae2/lang/en_gb.json`、`zh_cn.json` | — |
| TP-700~702 | `src/test/java/appeng/crafting/pattern/*Test.java`(新)、`src/test/java/appeng/crafting/simulation/CraftingSimulationTest.java`、`src/test/java/appeng/crafting/simulation/helpers/` | 现有 test 基建 |
| TP-800~803 | `CHANGES.md`、`guidebook/`、`API.md`、提交/PR | — |

**锁定机制**：CI 的 plan-check 仅做结构校验；范围执行由 Reviewer 按本表逐条核对 diff。

## 4. 完成定义（DOD）

**条目级 DOD**（每勾选一个 `- [x]` 前必须全部满足）：
- [ ] 实现与 `todo.md` 条目描述一致，未越界改动其他文件
- [ ] 对应验证命令已执行且通过（测试 / datagen / spotless）
- [ ] 新代码包含防环、防溢出、null/非法输入处理（适用时）
- [ ] 提交信息格式：`TP-xxx: 简述`

**里程碑级 DOD**：该里程碑所有条目勾选且各自验证通过；`./gradlew build` 全绿（含 check/validateResources/test）。

**整体 DOD**（TP-803 评审通过后）：
- [ ] M1~M8 全部 `[x]`，CI（plan-check + build + spotless + datagen）全绿
- [ ] 既有模式编解码行为零回归（对照 `forge/1.20.1` 的测试结果）
- [ ] `CHANGES.md`、`guidebook/`（如适用）已更新
- [ ] 评审清单（Reviewer）：向后兼容 ✔ / 防环防溢出 ✔ / NBT 规范 ✔ / 错误路径 ✔ / 测试覆盖 ✔

## 5. CI/CD 说明

| 工作流 | 触发 | 内容 |
|---|---|---|
| `.github/workflows/tunnel-pattern-ci.yml` | push/PR 目标为 `TunnelPattern`，或手动 dispatch | `plan-check`（校验 agents.md/todo.md 结构）→ `build`（gradle-setup → runData → datagen 新鲜度 → spotlessCheck → build/test → 产物上传） |
| `.github/scripts/check_todo.py` | 由 plan-check 调用 | 校验：agents.md 必备章节齐全；todo.md 里程碑/条目格式合法；条目 ID 唯一；已完结（✅）里程碑内不允许存在 `- [ ]` |

**门禁语义**：plan-check 失败 ⇒ 构建不启动；条目 ID 重复或已完结里程碑残留未完成项 ⇒ 直接红灯。
分支保护建议（仓库管理员设置）：`TunnelPattern` 分支要求 `TunnelPattern CI` 通过后方可合并。
