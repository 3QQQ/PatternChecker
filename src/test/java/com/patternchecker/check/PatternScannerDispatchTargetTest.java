package com.patternchecker.check;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises real scanner early target rejection using a cached machine state.
 * Null details/level deliberately prove that rejection precedes recipe lookup;
 * these fixtures do not represent an in-game recipe or verify mod dispatch.
 */
class PatternScannerDispatchTargetTest {
    @ParameterizedTest
    @CsvSource({
            "false, false, NO_TARGET",
            "true, true, WRONG_MACHINE"
    })
    void processingRejectsInvalidTargetsBeforeRecipeMatching(
            boolean hasTarget, boolean craftingOnly, String expected) throws Exception {
        Fixture fixture = fixture(hasTarget, craftingOnly);
        var method = Arrays.stream(PatternScanner.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("checkProcessingMachine"))
                .findFirst().orElseThrow();
        method.setAccessible(true);

        var result = (Enum<?>) method.invoke(null,
                fixture.host(), null, new BlockPos(0, 64, 0), null, fixture.context());

        assertEquals(expected, result.name());
    }

    @Test
    void virtualProcessingCanDispatchToAUniversalExecutorBesideAnAssembler() throws Exception {
        Fixture fixture = fixture(true, true, true);
        var method = Arrays.stream(PatternScanner.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("checkVirtualDispatchTarget"))
                .findFirst().orElseThrow();
        method.setAccessible(true);

        var result = (Enum<?>) method.invoke(null,
                fixture.host(), null, new BlockPos(0, 64, 0), null, true, fixture.context());

        assertEquals("UNKNOWN", result.name());
    }

    @ParameterizedTest
    @CsvSource({
            "false, false, true, NO_TARGET",
            "false, false, false, NO_TARGET",
            "true, true, true, WRONG_MACHINE",
            "true, true, false, UNKNOWN",
            "true, false, true, UNKNOWN"
    })
    void virtualCompletionStillRejectsInvalidDispatchTargets(
            boolean hasTarget, boolean craftingOnly, boolean processing, String expected) throws Exception {
        Fixture fixture = fixture(hasTarget, craftingOnly);
        var method = Arrays.stream(PatternScanner.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("checkVirtualDispatchTarget"))
                .findFirst().orElseThrow();
        method.setAccessible(true);

        var result = (Enum<?>) method.invoke(null,
                fixture.host(), null, new BlockPos(0, 64, 0), null, processing, fixture.context());

        assertEquals(expected, result.name());
    }

    private static Fixture fixture(boolean hasTarget, boolean craftingOnly) throws Exception {
        return fixture(hasTarget, craftingOnly, false);
    }

    private static Fixture fixture(boolean hasTarget, boolean craftingOnly,
                                   boolean universalRecipeExecutor) throws Exception {
        var contextClass = Class.forName(PatternScanner.class.getName() + "$ScanContext");
        var contextConstructor = contextClass.getDeclaredConstructors()[0];
        contextConstructor.setAccessible(true);
        Object context = contextConstructor.newInstance(new Object[]{null});

        var machineClass = Class.forName(PatternScanner.class.getName() + "$MachineState");
        var machineConstructor = machineClass.getDeclaredConstructors()[0];
        machineConstructor.setAccessible(true);
        Object machine = machineConstructor.newInstance(
                Set.of(), hasTarget, craftingOnly, craftingOnly,
                false, universalRecipeExecutor, false, false, false, Set.of(), Set.of());

        var machineStatesField = contextClass.getDeclaredField("machineStates");
        machineStatesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var machineStates = (Map<Object, Object>) machineStatesField.get(context);
        Object host = new Object();
        machineStates.put(host, machine);
        return new Fixture(host, context);
    }

    private record Fixture(Object host, Object context) {
    }
}
