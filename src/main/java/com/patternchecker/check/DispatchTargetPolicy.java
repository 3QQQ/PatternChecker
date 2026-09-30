package com.patternchecker.check;

/** Rejects only target failures that do not depend on recipe output matching. */
final class DispatchTargetPolicy {
    private DispatchTargetPolicy() {
    }

    record Target(boolean hasTarget, boolean craftingOnly, boolean acceptsPlans,
                  boolean hasRecipeTypes, boolean knownNonProcessing,
                  boolean unknownAddon, boolean contextualMagic,
                  boolean universalRecipeExecutor) {
    }

    enum Rejection {
        NONE, NO_TARGET, WRONG_MACHINE
    }

    static Rejection check(Target target, boolean processing) {
        if (!target.hasTarget()) {
            return Rejection.NO_TARGET;
        }
        boolean uncertainTarget = target.unknownAddon() || target.contextualMagic();
        if (processing) {
            if (!target.hasRecipeTypes() && !target.universalRecipeExecutor() && !uncertainTarget
                    && (target.craftingOnly() || target.knownNonProcessing())) {
                return Rejection.WRONG_MACHINE;
            }
        } else if (!target.acceptsPlans() && !uncertainTarget) {
            return Rejection.WRONG_MACHINE;
        }
        return Rejection.NONE;
    }
}
