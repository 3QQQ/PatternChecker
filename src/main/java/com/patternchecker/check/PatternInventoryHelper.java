package com.patternchecker.check;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import java.lang.reflect.Method;

/**
 * Resolves the pattern inventory of any pattern-holding block: vanilla AE2
 * pattern providers, AE2LT overloaded/piglin providers and matrix ports.
 */
public final class PatternInventoryHelper {

    private static final String MATRIX_PORT_CLASS = "com.moakiee.ae2lt.blockentity.MatrixPortBlockEntity";

    private PatternInventoryHelper() {
    }

    public static InternalInventory patternInventoryOf(Object owner) {
        if (owner instanceof PatternProviderLogicHost host) {
            return host.getLogic().getPatternInv();
        }
        if (owner instanceof PatternContainer container) {
            return container.getTerminalPatternInventory();
        }
        // AE2LT matrix port - optional mod, accessed reflectively so this mod
        // also works in packs without AE2 Lightning Tech.
        if (owner != null && owner.getClass().getName().equals(MATRIX_PORT_CLASS)) {
            try {
                Method method = owner.getClass().getMethod("getTerminalPatternInventory");
                return (InternalInventory) method.invoke(owner);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return null;
    }
}
