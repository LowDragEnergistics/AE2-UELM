/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.crafting.cycle;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.api.stacks.AEKey;

/**
 * Detects self-loops in a set of cycle firings by strongly connected component analysis of the firing dependency graph
 * (firing A depends on firing B if B produces one of A's inputs).
 *
 * <p>
 * A single firing whose outputs intersect its own inputs is a self-loop; a group of firings that mutually depend on
 * each other is a multi-step cycle. This mirrors the strongly-connected-component treatment of self-loop recipes in
 * DataEnergistics' Trinity planner.
 */
public final class LoopDetector {

    private LoopDetector() {
    }

    /**
     * One detected production cycle.
     *
     * @param order         the firings of the cycle in a stable order (usable as a one-cycle order)
     * @param firingIndices indices into the scanned firing list (for provider binding)
     * @param netChange     exact signed effect of one full cycle
     */
    public record DetectedLoop(
            List<LoopFiring> order,
            List<Integer> firingIndices,
            Map<AEKey, BigInteger> netChange) {

        public DetectedLoop {
            order = List.copyOf(order);
            firingIndices = List.copyOf(firingIndices);
            netChange = copySigned(netChange);
        }

        /**
         * @return the keys this cycle produces net-positive; each is a candidate for takeover.
         */
        public List<AEKey> productiveTargets() {
            var targets = new ArrayList<AEKey>();
            netChange.forEach((key, amount) -> {
                if (amount.signum() > 0) {
                    targets.add(key);
                }
            });
            return Collections.unmodifiableList(targets);
        }

        private static Map<AEKey, BigInteger> copySigned(Map<AEKey, BigInteger> source) {
            LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
            source.forEach((key, amount) -> {
                if (key == null || amount == null || amount.signum() == 0) {
                    throw new IllegalArgumentException("A detected loop net change must be non-zero");
                }
                copied.put(key, amount);
            });
            return Collections.unmodifiableMap(copied);
        }
    }

    /**
     * Detects all production cycles in the given firings.
     *
     * @param firings the scanned cycle firings (each derived from one network pattern)
     * @return detected loops, in a deterministic order
     */
    public static List<DetectedLoop> detect(List<LoopFiring> firings) {
        if (firings == null) {
            throw new IllegalArgumentException("A loop scan requires a firing list");
        }
        List<LoopFiring> fixed = List.copyOf(firings);
        int n = fixed.size();
        if (n == 0) {
            return List.of();
        }

        // Dependency edges: i depends on j if j produces one of i's inputs.
        var dependencies = new ArrayList<Set<Integer>>(n);
        for (int i = 0; i < n; i++) {
            var inputKeys = fixed.get(i).inputs().keySet();
            var deps = new java.util.HashSet<Integer>();
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                if (fixed.get(j).outputs().keySet().stream().anyMatch(inputKeys::contains)) {
                    deps.add(j);
                }
            }
            dependencies.add(deps);
        }

        var sccs = tarjan(fixed, dependencies);
        var loops = new ArrayList<DetectedLoop>();
        for (var scc : sccs) {
            boolean selfLoop = scc.size() > 1
                    || intersects(fixed.get(scc.get(0)).inputs().keySet(),
                            fixed.get(scc.get(0)).outputs().keySet());
            if (!selfLoop) {
                continue;
            }
            var order = new ArrayList<LoopFiring>();
            var indices = new ArrayList<Integer>();
            var net = new LinkedHashMap<AEKey, BigInteger>();
            for (int index : scc) {
                order.add(fixed.get(index));
                indices.add(index);
                fixed.get(index).netChange().forEach(
                        (key, amount) -> net.merge(key, amount, BigInteger::add));
            }
            net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
            loops.add(new DetectedLoop(order, indices, net));
        }
        return Collections.unmodifiableList(loops);
    }

    private static boolean intersects(Set<AEKey> first, Set<AEKey> second) {
        return first.stream().anyMatch(second::contains);
    }

    /**
     * Tarjan's strongly connected components algorithm (iterative, deterministic order).
     *
     * @return the SCCs as lists of firing indices
     */
    private static List<List<Integer>> tarjan(List<LoopFiring> firings, List<Set<Integer>> dependencies) {
        int n = firings.size();
        int[] index = new int[n];
        int[] lowlink = new int[n];
        boolean[] onStack = new boolean[n];
        java.util.Arrays.fill(index, -1);
        var stack = new java.util.ArrayDeque<Integer>();
        var components = new ArrayList<List<Integer>>();
        var indexCounter = new int[1];

        for (int root = 0; root < n; root++) {
            if (index[root] != -1) {
                continue;
            }
            // Iterative DFS frames: (node, iterator over dependencies)
            var frames = new java.util.ArrayDeque<java.util.Iterator<Integer>>();
            var path = new java.util.ArrayDeque<Integer>();
            index[root] = indexCounter[0];
            lowlink[root] = indexCounter[0]++;
            stack.push(root);
            onStack[root] = true;
            path.push(root);
            frames.push(dependencies.get(root).iterator());

            while (!frames.isEmpty()) {
                var it = frames.peek();
                if (it.hasNext()) {
                    int next = it.next();
                    if (index[next] == -1) {
                        index[next] = indexCounter[0];
                        lowlink[next] = indexCounter[0]++;
                        stack.push(next);
                        onStack[next] = true;
                        path.push(next);
                        frames.push(dependencies.get(next).iterator());
                    } else if (onStack[next]) {
                        lowlink[path.peek()] = Math.min(lowlink[path.peek()], index[next]);
                    }
                } else {
                    frames.pop();
                    int node = path.pop();
                    if (lowlink[node] == index[node]) {
                        var component = new ArrayList<Integer>();
                        int member;
                        do {
                            member = stack.pop();
                            onStack[member] = false;
                            component.add(member);
                        } while (member != node);
                        Collections.reverse(component);
                        components.add(component);
                    }
                    if (!path.isEmpty() && !frames.isEmpty()) {
                        int parent = path.peek();
                        lowlink[parent] = Math.min(lowlink[parent], lowlink[node]);
                    }
                }
            }
        }
        return components;
    }
}
