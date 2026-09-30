package com.patternchecker.check;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Invokes the compiled matcher with identifier-only records; no game or copied matcher is used. */
class PatternScannerInputMatcherTest {
    private static final String DIAMOND = "minecraft:diamond";
    private static final String EMERALD = "minecraft:emerald";

    @Test
    void alternativeCandidatesInOneSlotCannotSatisfyTwoDifferentIngredients() throws Exception {
        assertFalse(matches(
                List.of(requirement(1, DIAMOND), requirement(1, EMERALD)),
                List.of(slot(candidate(DIAMOND, 1), candidate(EMERALD, 1))), 1));
    }

    @Test
    void independentSlotsSatisfyIndependentIngredients() throws Exception {
        assertTrue(matches(
                List.of(requirement(1, DIAMOND), requirement(1, EMERALD)),
                List.of(slot(candidate(DIAMOND, 1)), slot(candidate(EMERALD, 1))), 1));
    }

    @Test
    void twoRequirementsCanShareTheQuantityOfOneChosenCandidate() throws Exception {
        assertTrue(matches(
                List.of(requirement(1, DIAMOND), requirement(1, DIAMOND)),
                List.of(slot(candidate(DIAMOND, 2))), 1));
    }

    @Test
    void broadIngredientAndSpecificIngredientFindAValidAssignment() throws Exception {
        assertTrue(matches(
                List.of(requirement(1, DIAMOND, EMERALD), requirement(1, DIAMOND)),
                List.of(slot(candidate(DIAMOND, 1)), slot(candidate(EMERALD, 1))), 1));
    }

    @Test
    void inputCountsMustMatchTheMainOutputScaleExactly() throws Exception {
        var requirements = List.of(requirement(2, DIAMOND));
        assertTrue(matches(requirements, List.of(slot(candidate(DIAMOND, 4))), 2));
        assertFalse(matches(requirements, List.of(slot(candidate(DIAMOND, 3))), 2));
        assertFalse(matches(requirements, List.of(slot(candidate(DIAMOND, 5))), 2));
    }

    @Test
    void scaledQuantityOverflowCannotMatch() throws Exception {
        assertFalse(matches(List.of(requirement(Long.MAX_VALUE, DIAMOND)),
                List.of(slot(candidate(DIAMOND, Long.MAX_VALUE))), 2));
    }

    @Test
    void emptyOrNonpositiveInputCandidateCannotSupplyARequirement() throws Exception {
        var requirements = List.of(requirement(1, DIAMOND));
        assertFalse(matches(requirements, List.of(slot()), 1));
        assertFalse(matches(requirements, List.of(slot(candidate(DIAMOND, 0))), 1));
        assertFalse(matches(requirements, List.of(slot(candidate(DIAMOND, -1))), 1));
    }

    private static boolean matches(List<?> requirements, List<?> slots, long scale) throws Exception {
        var method = PatternScanner.class.getDeclaredMethod(
                "matchesRecipeInputsExactly", List.class, List.class, long.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, requirements, slots, scale);
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
