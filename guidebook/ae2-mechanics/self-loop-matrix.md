---
navigation:
  parent: ae2-mechanics/ae2-mechanics-index.md
  title: ME Self-Loop Matrix
  icon: self_loop_matrix
---

# ME Self-Loop Matrix

<ItemImage id="self_loop_matrix" scale="4" />

The ME Self-Loop Matrix is a computing device attached to an ME network. It automatically identifies
**self-loop (self-multiplying) crafting recipes** from the network's pattern providers and takes them
over: the loop's outputs become craftable through the matrix, which plans and executes the loop.

The matrix has **no GUI** — place it on the network and it starts working.

## How it works

- **Identification**: every processing pattern offered by the network's pattern providers is read
  (tunnel pattern references are expanded). Strongly-connected component analysis finds production
  cycles — a single pattern whose output is also its input, or a group of patterns that feed each
  other — and their net-positive outputs.
- **Takeover**: for each detected cycle target the matrix plans against the current network storage
  and registers the target as craftable with a high priority, ahead of the raw loop patterns.
- **Execution**: when the crafting CPU pushes a request, the matrix re-plans from the provided seed
  amounts and forwards the compressed schedule batches to the providers owning the loop patterns; the
  network machines behind those providers produce the goods as usual.

## Notes

A cycle needs its **seed** (the minimum inventory before the loop can start) in storage; otherwise it
is not advertised as craftable. A cycle that does not produce the target net-positive is not taken
over. The matrix is a single compute device: it executes one loop takeover at a time.
