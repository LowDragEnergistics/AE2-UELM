---
navigation:
  parent: ae2-mechanics/ae2-mechanics-index.md
  title: 隧道样板
  icon: tunnel_pattern
---

# 隧道样板

<ItemImage id="tunnel_pattern" scale="4" />

隧道样板是一种特殊的处理样板：它只有输入、没有输出，并以 UUID 标识。它的用途是作为**输入**，
在编码其他处理样板时被引用。

当 ME 系统合成引用了隧道样板的样板时，隧道样板的输入会被内联进合成计算中，并乘以引用的隧道样板
数量。例如：隧道样板包含 8 个铁锭，某处理样板引用了 3 个该隧道样板，则该处理样板每次合成消耗
24 个铁锭。

创建方法：在 <ItemLink id="pattern_encoding_terminal" /> 的处理模式下清空所有输出槽并编码。
修改内容：将隧道样板放入编码槽，修改输入后再次编码——UUID 会被复用，引用它的样板无需任何更新
即可继续生效。

隧道样板必须存放在 ME 存储中（例如驱动器内），合成系统才能按 UUID 找到它。不要将其放入样板供应器
——它本身永远不会作为作业执行。
