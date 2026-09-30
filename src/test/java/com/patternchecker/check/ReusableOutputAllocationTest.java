package com.patternchecker.check;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Supplies full encoded inputs and recipe requirements to the production joint allocator. */
class ReusableOutputAllocationTest {
    private static final String MAIN = "ars_nouveau:source_gem";
    private static final String A = "minecraft:diamond";
    private static final String B = "minecraft:emerald";

    @Test
    void returningTheOnlyCenterIngredientCannotUseAnAlternativePedestalAssignment() {
        assertFalse(allows(Map.of(MAIN, 1L, B, 1L), Map.of(A, 1L, B, 1L),
                List.of(requirement(1, B)), List.of(requirement(1, A, B))));
    }

    @Test
    void returningTheActualPedestalIngredientLeavesTheCenterConsumptionAvailable() {
        assertTrue(allows(Map.of(MAIN, 1L, A, 1L), Map.of(A, 1L, B, 1L),
                List.of(requirement(1, B)), List.of(requirement(1, A, B))));
    }

    @Test
    void oneIdentityCanSupplyCenterAndPedestalWithoutReturningTheConsumedPortion() {
        assertTrue(allows(Map.of(MAIN, 1L, B, 1L), Map.of(B, 2L),
                List.of(requirement(1, B)), List.of(requirement(1, B))));
        assertFalse(allows(Map.of(MAIN, 1L, B, 2L), Map.of(B, 2L),
                List.of(requirement(1, B)), List.of(requirement(1, B))));
    }

    @Test
    void ambiguousIngredientsUseAConsistentAssignmentForConsumptionAndReturns() {
        assertTrue(allows(Map.of(MAIN, 1L, A, 1L), Map.of(A, 1L, B, 1L),
                List.of(requirement(1, A, B)), List.of(requirement(1, A, B))));
    }

    @Test
    void returningBothIdentitiesCannotLeaveTheCenterUnfunded() {
        assertFalse(allows(Map.of(MAIN, 1L, A, 1L, B, 1L), Map.of(A, 1L, B, 1L),
                List.of(requirement(1, A, B)), List.of(requirement(1, A, B))));
    }

    private static boolean allows(Map<String, Long> outputs, Map<String, Long> encodedInputs,
                                  List<ReusableOutputValidator.ReusableInput> consumed,
                                  List<ReusableOutputValidator.ReusableInput> reusable) {
        return ReusableOutputValidator.allowsAdditionalOutputs(
                outputs, Set.of(MAIN), encodedInputs, consumed, reusable);
    }

    private static ReusableOutputValidator.ReusableInput requirement(long amount, String... identifiers) {
        return new ReusableOutputValidator.ReusableInput(Set.of(identifiers), amount);
    }
}
