package com.patternchecker.check;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PatternScannerTest {
    @Test
    void containerLocationDistinguishesDimensionsAtTheSamePositionAndSlot() {
        BlockPos pos = new BlockPos(7, 64, 9);
        ResourceLocation overworldId = ResourceLocation.parse("minecraft:overworld");
        String overworld = PatternScanner.formatContainerLocation("Provider", overworldId, pos);
        String nether = PatternScanner.formatContainerLocation(
                "Provider", ResourceLocation.parse("minecraft:the_nether"), pos);

        assertNotEquals(overworld, nether);
        assertEquals(overworld, PatternScanner.formatContainerLocation("Provider", overworldId, pos));
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
}
