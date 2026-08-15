---
navigation:
  parent: ae2-mechanics/ae2-mechanics-index.md
  title: Tunnel Patterns
  icon: tunnel_pattern
---

# Tunnel Patterns

<ItemImage id="tunnel_pattern" scale="4" />

Tunnel patterns are a special kind of processing pattern: they have inputs but no outputs, and are
identified by a UUID. They are meant to be used as an **input** when encoding other processing patterns.

When the ME system crafts a pattern that references a tunnel pattern, the tunnel pattern's inputs are
inlined into the crafting calculation, multiplied by the number of referenced tunnel patterns. For
example, if a tunnel pattern contains 8 iron ingots and a processing pattern references 3 of them, the
processing pattern consumes 24 iron ingots per craft.

To create a tunnel pattern, clear all output slots of the <ItemLink id="pattern_encoding_terminal" />
while in processing mode and encode. To change its contents, place the tunnel pattern in the encoded
pattern slot, modify the inputs and encode again - the UUID is reused, so the patterns that reference
it keep working without any updates.

Tunnel patterns must be stored in ME storage (for example in a drive) so that the crafting system can
find them by UUID. Do not place them in pattern providers - they never run as a job by themselves.
