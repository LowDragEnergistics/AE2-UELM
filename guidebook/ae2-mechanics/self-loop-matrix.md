---
navigation:
  parent: ae2-mechanics/ae2-mechanics-index.md
  title: ME Self-Loop Matrix
  icon: self_loop_matrix
---

# ME Self-Loop Matrix

<ItemImage id="self_loop_matrix" scale="4" />

The ME Self-Loop Matrix is a computing device attached to an ME network. It computes exact production
plans for **self-loop (self-multiplying) crafting recipes** — recipes whose outputs feed back into their
own inputs, such as `1 A + 1 B -> 2 A`.

## Usage

Place up to nine processing patterns that form a cycle into the matrix, place the item you want to
produce into the target slot, and set the requested amount. The matrix computes:

- **Per-cycle net change**: how much of each item one full cycle produces or consumes.
- **Minimum seed**: the inventory that must be present before the loop can start (the maximum prefix
  deficit, growing with the number of repetitions for net-consumed items).
- **Repetitions**: how many complete cycles are needed for the request.
- **Compressed schedule**: the exact batch plan (batches grow geometrically, so a huge loop schedules
  in very few batches).
- **Shortages**: any input missing from the network's storage.

The quantity mode selects between *net new* (deliver N items on top of the stock) and *final total*
(leave N items in stock), mirroring the semantics used by large-scale crafting planners.

## Tunnel patterns

Loop patterns may reference [Tunnel Patterns](tunnel-pattern.md) as inputs. The matrix expands tunnel
references into their concrete contents before computing, so you can change a tunnel pattern's contents
without touching the loop patterns that reference it.

## Notes

The matrix is a computer, not an executor: it reports the plan (seed, repetitions and schedule) so you
can set up the machines and storage accordingly. A cycle that does not produce the target net-positive
is rejected as non-productive.
