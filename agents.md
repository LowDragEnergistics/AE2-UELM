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
6. **样式**：遵循仓库 Spotless 配置（`codeformat/`）与第 6 节开发规范（整合自 GTNH Code Style），提交前必须 `./gradlew spotlessApply`；
   禁止通配符 import；文件须带 LGPL 许可头（与同包文件一致）。
7. **编码前校验**：编码路径（TP-200~203）必须覆盖——处理模式且无输出 → 编码为 TunnelPattern（编码槽内已有 TunnelPattern 则复用其 UUID，否则随机生成）；
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

## 6. 开发规范（整合自 GTNH Code Style）

> 来源：[gtnh.huijiwiki.com/wiki/代码风格](https://gtnh.huijiwiki.com/wiki/%E4%BB%A3%E7%A0%81%E9%A3%8E%E6%A0%BC)（GTNH 官方 Code Style 中文版，2026-08-01 译自 [gtnh.miraheze.org/wiki/Code_Style](https://gtnh.miraheze.org/wiki/Code_Style)）。
> 适用范围：本仓库全部 Java 代码。与第 2 节硬性规则共同构成代理的代码行为约束。
> 冲突仲裁：与仓库 Spotless 配置 / CI 门禁冲突时，以本仓库配置为准。

### 6.1 总原则

- 代码被阅读的次数远多于被编写的次数；维护成本占软件生命周期成本的绝大部分；一致风格让协作更轻松、评审更快。
- 本规范鼓励写出好代码；**始终运用良好判断力**：若遵循指南导致不必要的繁琐或降低可读性，可读性优先；
  但若“更可读”的变体存在隐患或陷阱，可读性可以让位于安全性与正确性。
- 主要参考：Oracle《Java 编程语言代码规范》、Google Java 风格指南；推荐阅读《Clean Code》《Effective Java》。

### 6.2 工具链

- 仓库使用 **Spotless** 检查换行、空格与缩进（见 `build.gradle` 的 spotless 配置，JSON 由 biome 步骤校验）；
  提交前必须 `./gradlew spotlessApply`（本机无法执行 spotlessJson 时，至少保证 `spotlessJavaCheck` 通过并由 CI 兜底）。

### 6.3 文件组织

- 单文件尽量避免超过 **2000 行**。
- 类/接口内部声明顺序：
  1. 类/接口文档注释（`/** */`，可选）
  2. `class`/`interface` 语句
  3. 类级实现注释（`/* */`，可选）
  4. static 变量（顺序：public → protected → 包级 → private）
  5. 实例变量（顺序：public → protected → 包级 → private）
  6. 构造方法
  7. 方法——**按功能分组**而非按作用域/可访问性分组（私有辅助方法可置于两个公有方法之间），以利阅读。

### 6.4 注释

- 不要注释掉代码——直接删除；需要时从 Git 历史找回。
- 避免易随代码演变而过时的注释；注释频率高可能反映代码质量不佳——考虑重写使代码自明。
- 注释不要用星号等字符围成方框。
- 风格：单行用 `//`，跨行用块注释：
  ```java
  if (foo > 1) {
      // Do a double-flip.
      return bar.performDoubleFlip();
  }

  /*
   * Here is a block comment.
   */
  ```
- `@author` 标签不强制也不禁止（版本控制系统已记录作者信息）。

### 6.5 声明

- 一行多变量仅限紧密关联者（如 3D 坐标 `int x, y, z;`）；**禁止同一行声明不同类型**（如 `int foo, fooarray[];`）。
- 尽量在声明处初始化；唯一例外是初始值依赖先前的计算。
- 只在块（`{...}` 包围的代码）的开头声明变量，不延迟到首次使用处。

### 6.6 语句

- `switch` 推荐现代箭头形式（本仓库 Java 17 支持）：
  ```java
  switch (condition) {
      case ABC, DEF, KLM -> { statements; }
      case XYZ -> { statements; }
      default -> { statements; }
  }
  ```

### 6.7 命名

| 标识符 | 规则 | 示例 |
|---|---|---|
| 类 | 名词、UpperCamelCase、简洁描述性、用完整单词避免缩写 | `class Raster`、`class ImageSprite` |
| 接口 | 与类相同大写风格；**禁止 “I” 前缀** | `interface Storing`（而非 `IStoring`） |
| 方法 | 动词、lowerCamelCase | `run()`、`getBackgroundColor()` |
| 变量 | lowerCamelCase、以字母开头（禁 `_`/`$` 开头）、简短但能一眼看出用途 | `String currentAccountKey;` |

### 6.8 日志格式（GTNH 特定）

- 大多数日志消息每行一次写入调用；单句消息不需要大写首字母；多句消息首字母大写。

### 6.9 编程实践

- **访问控制**：无充分理由不得将实例/类变量设为 `public`；通常经方法调用副作用读写。
- **常量**：禁止硬编码魔法值，使用命名良好的常量：
  ```java
  int CONSTANT_NAME = 16281;
  methodName(CONSTANT_NAME);
  ```
- **赋值**：避免单语句给多个变量赋相同值（`fooBar.fChar = barFoo.lchar = 'c';` → AVOID）。
- **废弃 API**：`@Deprecated` 必须注释替代方案；`@Deprecated` 注解与 `@deprecated` javadoc 标签必须成对存在：
  ```java
  /**
   * @deprecated use {@link DBHelper#update(java.lang.String, java.util.Map)}
   */
  @Deprecated(forRemoval = true)
  public int insert(String request, Map<String, ?> params) { ... }
  ```

### 6.10 运算符与括号

- 混合运算符表达式中多用括号消除优先级歧义：
  ```java
  if ((a == b) && (c == d)) // OK
  if (a == b && c == d)     // AVOID!
  ```

## 7. 版本管理（语义化版本 + git tag）

> 每次功能/修复合并后，必须依据语义化版本（SemVer）调整版本号并打 git tag。
> 版本**继承上游**（AE2-Unofficial-Extended-Life-Modern/AE2-UELM）使用的版本号，并带 `-uelm` 后缀（SemVer 预发布标识）。

1. **基准**：以上游最近发布的版本为基准（默认分支 `forge/1.20.1` 对应的上游 tag，如 `forge/v15.5.0-uelm`），
   在其之上进行 SemVer 增量；不以本仓库 `gradle.properties` 的 `0.0.0` 占位符为基准。
2. **提升规则**（SemVer）：
   - 向后**不兼容**的 API 变更 → **major** 提升
   - 向后**兼容**的新特性 → **minor** 提升
   - 向后**兼容**的缺陷修复 → **patch** 提升
   - 例：上游 `15.5.0-uelm` + 新特性 → `15.6.0-uelm`；+ 缺陷修复 → `15.5.1-uelm`
3. **版本格式**（上游约定）：`<semver>-uelm`（如 `15.5.1-uelm`）——`-uelm` 为 SemVer 预发布后缀。
4. **修改位置**：`gradle.properties`（`version=<semver>-uelm`）与 `src/main/resources/META-INF/mods.toml`
   （`version="<semver>-uelm"`——构建时由 processResources 以工程版本替换，源文件需保持一致）。
5. **git tag**：上游约定为 `forge/v<semver>-uelm`（如 `forge/v15.5.1-uelm`）；tag 打在版本提升提交上，随分支一起推送。
6. **构建产物命名**：`appliedenergistics2-forge-<semver>-uelm[-type].jar`
   （如 `appliedenergistics2-forge-15.5.1-uelm.jar`、`-api.jar`、`-javadoc.jar`，`type` 为 `api`/`javadoc` 等）。
7. **CI 一致性**：版本提升提交后，`TunnelPattern CI` 与 `Build and Test` 须全绿；版本不影响 datagen（`runData` 后 generated 无 diff）。
8. **默认分支同步**：版本提升提交应通过 PR 合入 `forge/1.20.1`，保持默认分支版本与功能/修复一致。
