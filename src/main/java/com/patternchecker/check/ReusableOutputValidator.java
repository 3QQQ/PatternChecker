package com.patternchecker.check;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks additional returned ingredients without treating them as recipe products. */
final class ReusableOutputValidator {
    private ReusableOutputValidator() {
    }

    record ReusableInput(Set<String> acceptedIds, long amount) {
        ReusableInput {
            acceptedIds = Set.copyOf(acceptedIds);
        }
    }


    /** Requires one allocation to satisfy consumed inputs and returned inputs together. */
    static boolean allowsAdditionalOutputs(Map<String, Long> patternOutputs,
                                           Set<String> declaredOutputs,
                                           Map<String, Long> encodedInputs,
                                           List<ReusableInput> consumableRequirements,
                                           List<ReusableInput> reusableRequirements) {
        List<Map.Entry<String, Long>> inputs = new ArrayList<>(encodedInputs.entrySet());
        List<ReusableInput> requirements = new ArrayList<>(consumableRequirements);
        requirements.addAll(reusableRequirements);
        long inputTotal = 0;
        long requiredTotal = 0;
        try {
            for (var output : patternOutputs.entrySet()) {
                if (output.getValue() == null || output.getValue() <= 0) {
                    return false;
                }
                if (!declaredOutputs.contains(output.getKey())) {
                    Long encoded = encodedInputs.get(output.getKey());
                    if (encoded == null || output.getValue() > encoded) {
                        return false;
                    }
                }
            }
            for (var input : inputs) {
                if (input.getValue() == null || input.getValue() <= 0) {
                    return false;
                }
                inputTotal = Math.addExact(inputTotal, input.getValue());
            }
            for (ReusableInput requirement : requirements) {
                if (requirement.amount() <= 0) {
                    return false;
                }
                requiredTotal = Math.addExact(requiredTotal, requirement.amount());
            }
        } catch (ArithmeticException ignored) {
            return false;
        }
        if (inputTotal != requiredTotal) {
            return false;
        }

        int source = 0;
        int firstInput = 1;
        int firstRequirement = firstInput + 2 * inputs.size();
        int sink = firstRequirement + requirements.size();
        long[][] residual = new long[sink + 1][sink + 1];
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            long returned = declaredOutputs.contains(input.getKey())
                    ? 0 : patternOutputs.getOrDefault(input.getKey(), 0L);
            long remaining = input.getValue() - returned;
            int remainingNode = firstInput + 2 * i;
            int returnedNode = remainingNode + 1;
            residual[source][remainingNode] = remaining;
            residual[source][returnedNode] = returned;
            for (int j = 0; j < requirements.size(); j++) {
                if (requirements.get(j).acceptedIds().contains(input.getKey())) {
                    residual[remainingNode][firstRequirement + j] = remaining;
                    if (j >= consumableRequirements.size()) {
                        // Reserved returns cannot simultaneously fulfill the
                        // consumed central ingredient, even when tags overlap.
                        residual[returnedNode][firstRequirement + j] = returned;
                    }
                }
            }
        }
        for (int j = 0; j < requirements.size(); j++) {
            residual[firstRequirement + j][sink] = requirements.get(j).amount();
        }
        return canAllocate(residual, source, sink, inputTotal);
    }

    private static boolean canAllocate(long[][] residual, int source, int sink, long total) {
        long allocated = 0;
        int[] parent = new int[residual.length];
        while (allocated < total) {
            Arrays.fill(parent, -1);
            parent[source] = source;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(source);
            while (!queue.isEmpty() && parent[sink] < 0) {
                int current = queue.removeFirst();
                for (int next = 0; next < residual.length; next++) {
                    if (parent[next] < 0 && residual[current][next] > 0) {
                        parent[next] = current;
                        queue.addLast(next);
                    }
                }
            }
            if (parent[sink] < 0) {
                return false;
            }
            long amount = total - allocated;
            for (int node = sink; node != source; node = parent[node]) {
                amount = Math.min(amount, residual[parent[node]][node]);
            }
            for (int node = sink; node != source; node = parent[node]) {
                residual[parent[node]][node] -= amount;
                residual[node][parent[node]] += amount;
            }
            allocated += amount;
        }
        return true;
    }
}
