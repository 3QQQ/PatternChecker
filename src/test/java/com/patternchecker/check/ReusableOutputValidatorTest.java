package com.patternchecker.check;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests full input allocation and ingredient returns; main output scales are validated separately. */
class ReusableOutputValidatorTest {
    private static final String MAIN = "ars_nouveau:source_gem";
    private static final String CENTER = "minecraft:lapis_lazuli";
    private static final String DIAMOND = "minecraft:diamond";
    private static final String EMERALD = "minecraft:emerald";
    private static final String UNRELATED = "minecraft:netherite_ingot";
    private static final Set<String> DECLARED_OUTPUTS = Set.of(MAIN);

    @Test
    void declaredRecipeOutputNeedsNoReturnedIngredients() {
        assertTrue(allows(Map.of(MAIN, 1L), Map.of(CENTER, 1L), List.of()));
    }

    @Test
    void allowsAReusablePedestalIngredientToBeReturned() {
        assertTrue(allows(Map.of(MAIN, 1L, DIAMOND, 1L),
                Map.of(CENTER, 1L, DIAMOND, 1L), List.of(reusable(1, DIAMOND))));
    }

    @Test
    void rejectsAnArbitraryExtraOutputEvenIfItWasEncodedAsAnInput() {
        assertFalse(allows(Map.of(MAIN, 1L, UNRELATED, 64L),
                Map.of(CENTER, 1L, DIAMOND, 1L, UNRELATED, 64L),
                List.of(reusable(1, DIAMOND))));
    }

    @Test
    void rejectsReturningTheConsumedCenterIngredient() {
        assertFalse(allows(Map.of(MAIN, 1L, CENTER, 1L),
                Map.of(CENTER, 1L, DIAMOND, 1L), List.of(reusable(1, DIAMOND))));
    }

    @Test
    void rejectsReturningMoreThanTheEncodedInput() {
        assertFalse(allows(Map.of(MAIN, 1L, DIAMOND, 2L),
                Map.of(CENTER, 1L, DIAMOND, 1L), List.of(reusable(2, DIAMOND))));
    }

    @Test
    void rejectsReturningAnAcceptedIngredientAbsentFromTheEncodedInputs() {
        assertFalse(allows(Map.of(MAIN, 1L, DIAMOND, 1L),
                Map.of(CENTER, 1L), List.of(reusable(1, DIAMOND))));
    }

    @Test
    void rejectsReturnsThatExceedTheRecipePedestalCapacity() {
        assertFalse(allows(Map.of(MAIN, 1L, DIAMOND, 2L),
                Map.of(CENTER, 1L, DIAMOND, 2L),
                List.of(reusable(1, CENTER), reusable(1, DIAMOND)),
                List.of(reusable(1, DIAMOND))));
    }

    @Test
    void allowsTheScaledReusableQuantityProvidedByTheScanner() {
        assertTrue(allows(Map.of(MAIN, 3L, DIAMOND, 3L),
                Map.of(CENTER, 3L, DIAMOND, 3L),
                List.of(reusable(3, CENTER)), List.of(reusable(3, DIAMOND))));
    }

    @Test
    void allowsReturningOnlySomeOfTheReusableIngredients() {
        assertTrue(allows(Map.of(MAIN, 1L, DIAMOND, 1L),
                Map.of(CENTER, 1L, DIAMOND, 1L, EMERALD, 1L),
                List.of(reusable(1, DIAMOND), reusable(1, EMERALD))));
    }

    @Test
    void overlappingIngredientTagsCannotSpendOnePedestalTwice() {
        assertFalse(allows(Map.of(MAIN, 1L, DIAMOND, 1L, EMERALD, 1L),
                Map.of(DIAMOND, 1L, EMERALD, 1L),
                List.of(reusable(1, DIAMOND)),
                List.of(reusable(1, DIAMOND, EMERALD))));
    }

    @Test
    void findsAValidAssignmentWhenAnEarlyBroadTagNeedsToBeReassigned() {
        Map<String, Long> outputs = new LinkedHashMap<>();
        outputs.put(MAIN, 1L);
        outputs.put(DIAMOND, 1L);
        outputs.put(EMERALD, 1L);

        assertTrue(allows(outputs,
                Map.of(CENTER, 1L, DIAMOND, 1L, EMERALD, 1L),
                List.of(reusable(1, DIAMOND, EMERALD), reusable(1, DIAMOND))));
    }

    @Test
    void combinesIndependentPedestalsWithTheSameIngredient() {
        assertTrue(allows(Map.of(MAIN, 1L, DIAMOND, 2L),
                Map.of(CENTER, 1L, DIAMOND, 2L),
                List.of(reusable(1, DIAMOND), reusable(1, DIAMOND))));
    }

    private static boolean allows(Map<String, Long> outputs, Map<String, Long> inputs,
                                  List<ReusableOutputValidator.ReusableInput> reusableInputs) {
        return allows(outputs, inputs, List.of(reusable(1, CENTER)), reusableInputs);
    }

    private static boolean allows(Map<String, Long> outputs, Map<String, Long> inputs,
                                  List<ReusableOutputValidator.ReusableInput> consumedInputs,
                                  List<ReusableOutputValidator.ReusableInput> reusableInputs) {
        return ReusableOutputValidator.allowsAdditionalOutputs(
                outputs, DECLARED_OUTPUTS, inputs, consumedInputs, reusableInputs);
    }

    private static ReusableOutputValidator.ReusableInput reusable(long amount, String... acceptedIds) {
        return new ReusableOutputValidator.ReusableInput(Set.of(acceptedIds), amount);
    }
}
