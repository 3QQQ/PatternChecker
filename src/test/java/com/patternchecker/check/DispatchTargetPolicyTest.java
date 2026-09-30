package com.patternchecker.check;

import org.junit.jupiter.api.Test;

import static com.patternchecker.check.DispatchTargetPolicy.Rejection.NONE;
import static com.patternchecker.check.DispatchTargetPolicy.Rejection.NO_TARGET;
import static com.patternchecker.check.DispatchTargetPolicy.Rejection.WRONG_MACHINE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** NONE permits further checking; it does not certify a recipe or dispatch as valid. */
class DispatchTargetPolicyTest {
    @Test
    void missingTargetsRejectBothKindsOfDispatch() {
        var target = new DispatchTargetPolicy.Target(false, false, false, false, false, false, false, false);
        assertEquals(NO_TARGET, DispatchTargetPolicy.check(target, true));
        assertEquals(NO_TARGET, DispatchTargetPolicy.check(target, false));
    }

    @Test
    void molecularAssemblerAcceptsCraftingPlansButRejectsProcessing() {
        var target = new DispatchTargetPolicy.Target(true, true, true, false, false, false, false, false);
        assertEquals(WRONG_MACHINE, DispatchTargetPolicy.check(target, true));
        assertEquals(NONE, DispatchTargetPolicy.check(target, false));
    }

    @Test
    void knownNonProcessingTargetCannotBeUsedForProcessingOrCraftingPlans() {
        var target = new DispatchTargetPolicy.Target(true, false, false, false, true, false, false, false);
        assertEquals(WRONG_MACHINE, DispatchTargetPolicy.check(target, true));
        assertEquals(WRONG_MACHINE, DispatchTargetPolicy.check(target, false));
    }

    @Test
    void knownRecipeTypesDeferProcessingToExactRecipeValidation() {
        var target = new DispatchTargetPolicy.Target(true, false, false, true, false, false, false, false);
        assertEquals(NONE, DispatchTargetPolicy.check(target, true));
        assertEquals(WRONG_MACHINE, DispatchTargetPolicy.check(target, false));
    }

    @Test
    void secondTypedTargetIsNotRejectedBecauseAnotherTargetIsAnAssembler() {
        var target = new DispatchTargetPolicy.Target(true, true, true, true, false, false, false, false);
        assertEquals(NONE, DispatchTargetPolicy.check(target, true));
    }

    @Test
    void unknownAddonTargetIsNotRejectedBecauseAnotherTargetIsAnAssembler() {
        var target = new DispatchTargetPolicy.Target(true, true, true, false, false, true, false, false);
        assertEquals(NONE, DispatchTargetPolicy.check(target, true));
    }

    @Test
    void contextualTargetIsNotRejectedBecauseAnotherTargetIsKnownNonProcessing() {
        var target = new DispatchTargetPolicy.Target(true, false, false, false, true, false, true, false);
        assertEquals(NONE, DispatchTargetPolicy.check(target, true));
        assertEquals(NONE, DispatchTargetPolicy.check(target, false));
    }

    @Test
    void unknownCraftingPlanAcceptanceRemainsUncertain() {
        var target = new DispatchTargetPolicy.Target(true, false, false, false, false, true, false, false);
        assertEquals(NONE, DispatchTargetPolicy.check(target, false));
    }

    @Test
    void universalExecutorPreventsProcessingRejectionButDoesNotProveCraftingPlanAcceptance() {
        var target = new DispatchTargetPolicy.Target(true, true, false, false, false, false, false, true);
        assertEquals(NONE, DispatchTargetPolicy.check(target, true));
        assertEquals(WRONG_MACHINE, DispatchTargetPolicy.check(target, false));
    }
}
