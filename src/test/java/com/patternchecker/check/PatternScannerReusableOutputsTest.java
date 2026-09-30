package com.patternchecker.check;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Invokes the production scanner's return validation with identifier-only records. */
class PatternScannerReusableOutputsTest {
    private static final String MAIN = "ars_nouveau:source_gem";
    private static final String DIAMOND = "minecraft:diamond";
    private static final String EMERALD = "minecraft:emerald";

    @Test
    void scannerScalesPedestalReturnCapacityByTheMainOutputScale() throws Exception {
        assertTrue(allows(Map.of(MAIN, 2L, DIAMOND, 2L),
                List.of(requirement(1, DIAMOND)),
                List.of(slot(candidate(DIAMOND, 2))), 2));
    }

    @Test
    void scannerRejectsReturnsAboveTheMainOutputScaleEvenWithEnoughEncodedInputs() throws Exception {
        assertFalse(allows(Map.of(MAIN, 2L, DIAMOND, 3L),
                List.of(requirement(1, DIAMOND)),
                List.of(slot(candidate(DIAMOND, 3))), 2));
    }

    @Test
    void scannerCannotPromiseReturnsFromMutuallyExclusiveCandidates() throws Exception {
        assertFalse(allows(Map.of(MAIN, 1L, DIAMOND, 1L, EMERALD, 1L),
                List.of(requirement(2, DIAMOND, EMERALD)),
                List.of(slot(candidate(DIAMOND, 1), candidate(EMERALD, 1))), 1));
    }

    @Test
    void substitutableSlotsRemainAllowedWhenNoIngredientReturnIsPromised() throws Exception {
        assertTrue(allows(Map.of(MAIN, 1L),
                List.of(requirement(1, DIAMOND, EMERALD)),
                List.of(slot(candidate(DIAMOND, 1), candidate(EMERALD, 1))), 1));
    }

    @Test
    void overflowWhileScalingThePedestalCapacityCannotMatch() throws Exception {
        assertFalse(allows(Map.of(MAIN, 2L, DIAMOND, 1L),
                List.of(requirement(Long.MAX_VALUE, DIAMOND)),
                List.of(slot(candidate(DIAMOND, 1))), 2));
    }

    @Test
    void aCenterIngredientCannotBePromisedAsAPedestalReturn() throws Exception {
        assertFalse(allows(Map.of(MAIN, 1L, EMERALD, 1L),
                List.of(requirement(1, EMERALD)),
                List.of(requirement(1, DIAMOND, EMERALD)),
                List.of(slot(candidate(DIAMOND, 1)), slot(candidate(EMERALD, 1))), 1));
    }

    @Test
    void theActualPedestalIngredientCanBeReturnedBesideAConsumedCenterIngredient() throws Exception {
        assertTrue(allows(Map.of(MAIN, 1L, DIAMOND, 1L),
                List.of(requirement(1, EMERALD)),
                List.of(requirement(1, DIAMOND, EMERALD)),
                List.of(slot(candidate(DIAMOND, 1)), slot(candidate(EMERALD, 1))), 1));
    }

    @Test
    void sameIdentityCanSupplyBothCenterAndPedestalIfThereIsEnoughQuantity() throws Exception {
        assertTrue(allows(Map.of(MAIN, 1L, EMERALD, 1L),
                List.of(requirement(1, EMERALD)),
                List.of(requirement(1, EMERALD)),
                List.of(slot(candidate(EMERALD, 2))), 1));
    }

    @Test
    void sharedIdentityCannotReturnTheQuantityConsumedByTheCenter() throws Exception {
        assertFalse(allows(Map.of(MAIN, 1L, EMERALD, 2L),
                List.of(requirement(1, EMERALD)),
                List.of(requirement(1, EMERALD)),
                List.of(slot(candidate(EMERALD, 2))), 1));
    }

    @Test
    void bothCenterAndPedestalConsumptionUseTheMainOutputScale() throws Exception {
        var consumed = List.of(requirement(1, EMERALD));
        var reusable = List.of(requirement(1, DIAMOND, EMERALD));
        var inputs = List.of(slot(candidate(DIAMOND, 2)), slot(candidate(EMERALD, 2)));

        assertFalse(allows(Map.of(MAIN, 2L, EMERALD, 2L), consumed, reusable, inputs, 2));
        assertTrue(allows(Map.of(MAIN, 2L, DIAMOND, 2L), consumed, reusable, inputs, 2));
    }

    @Test
    void ambiguousCenterAndPedestalTagsFindOneJointValidAssignment() throws Exception {
        assertTrue(allows(Map.of(MAIN, 1L, DIAMOND, 1L),
                List.of(requirement(1, DIAMOND, EMERALD)),
                List.of(requirement(1, DIAMOND, EMERALD)),
                List.of(slot(candidate(DIAMOND, 1)), slot(candidate(EMERALD, 1))), 1));
    }

    private static boolean allows(Map<String, Long> outputs, List<?> reusableRequirements,
                                  List<?> inputs, long scale) throws Exception {
        return allows(outputs, List.of(), reusableRequirements, inputs, scale);
    }

    private static boolean allows(Map<String, Long> outputs, List<?> consumableRequirements,
                                  List<?> reusableRequirements, List<?> inputs, long scale) throws Exception {
        var method = PatternScanner.class.getDeclaredMethod("allowsReusableRecipeOutputs",
                Map.class, Set.class, List.class, List.class, List.class, long.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, outputs, Set.of(MAIN),
                consumableRequirements, reusableRequirements, inputs, scale);
    }

    private static Object requirement(long amount, String... identifiers) throws Exception {
        return record("RecipeRequirement", null, Set.of(identifiers), amount);
    }

    private static Object candidate(String identifier, long amount) throws Exception {
        return record("PatternInputCandidate", identifier, null, amount);
    }

    private static Object slot(Object... candidates) throws Exception {
        return record("PatternInputSlot", List.of(candidates));
    }

    private static Object record(String name, Object... arguments) throws Exception {
        var recordClass = Class.forName(PatternScanner.class.getName() + "$" + name);
        var constructor = recordClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }
}
