package com.patternchecker.check;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternScannerTest {
    @Test
    void providerLocationDistinguishesDimensionsAtTheSameCoordinates() {
        BlockPos pos = new BlockPos(43, 68, 24);
        String overworld = PatternScanner.formatContainerLocation("Pattern Provider",
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), pos);
        String nether = PatternScanner.formatContainerLocation("Pattern Provider",
                ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"), pos);

        assertNotEquals(overworld, nether);
        assertEquals(overworld, PatternScanner.formatContainerLocation("Pattern Provider",
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), pos));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0",
            "5, 7, 12",
            "1, 9223372036854775807, 2147483647",
            "2147483645, 2, 2147483647",
            "2147483645, 3, 2147483647",
            "2147483647, 1, 2147483647",
            "12, -1, 12"
    })
    void patternCountsSaturateWithoutOverflow(int value, long amount, int expected) throws Exception {
        var method = PatternScanner.class.getDeclaredMethod("saturatedAdd", int.class, long.class);
        method.setAccessible(true);
        assertEquals(expected, method.invoke(null, value, amount));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0",
            "5, 7, 12",
            "1, 9223372036854775807, 9223372036854775807",
            "9223372036854775805, 2, 9223372036854775807",
            "9223372036854775805, 3, 9223372036854775807",
            "12, -1, 12",
            "-9223372036854775808, 9223372036854775807, -1"
    })
    void recipeAmountsSaturateWithoutOverflow(long value, long amount, long expected) throws Exception {
        var method = PatternScanner.class.getDeclaredMethod("saturatedAdd", long.class, long.class);
        method.setAccessible(true);
        assertEquals(expected, method.invoke(null, value, amount));
    }

    /** Exercises the compiled scanner's empty entry path, without starting a game. */
    @Test
    void looseContainerWithoutInventoryReturnsEmptyResult() {
        var result = assertDoesNotThrow(() -> PatternScanner.scanLooseContainer(null, null));

        assertAll(
                () -> assertEquals(0, result.totalPatterns()),
                () -> assertEquals(0, result.providerPatterns()),
                () -> assertEquals(0, result.containerPatterns()),
                () -> assertEquals(0, result.storagePatterns()),
                () -> assertEquals(0, result.virtualCraftingPatterns()),
                () -> assertTrue(result.patterns().isEmpty()),
                () -> assertTrue(result.issues().isEmpty()),
                () -> assertTrue(result.verdicts().isEmpty()));
    }
}
