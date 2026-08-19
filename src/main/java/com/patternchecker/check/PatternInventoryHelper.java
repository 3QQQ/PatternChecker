package com.patternchecker.check;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import net.minecraft.world.item.ItemStack;

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
        // GTCEu/GTL custom ME pattern containers do not all implement AE2's
        // PatternContainer interface. They expose their storage through
        // getPatternInventory(), so adapt that stable shape reflectively.
        if (owner != null) {
            Object inventory = invokeNoArg(owner,
                    "getTerminalPatternInventory", "getPatternInventory");
            if (inventory instanceof InternalInventory internal) {
                return internal;
            }
            if (inventory != null) {
                return new ReflectiveInventory(inventory);
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String... names) {
        for (String name : names) {
            try {
                return target.getClass().getMethod(name).invoke(target);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        return null;
    }

    private static final class ReflectiveInventory implements InternalInventory {
        private final Object delegate;

        private ReflectiveInventory(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public int size() {
            Object value = invokeNoArg(delegate, "size", "getSlots", "getSlotCount");
            return value instanceof Number number ? Math.max(0, number.intValue()) : 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            for (String name : new String[]{"getStackInSlot", "getStack", "getItemStack"}) {
                try {
                    Method method = delegate.getClass().getMethod(name, int.class);
                    Object value = method.invoke(delegate, slot);
                    if (value instanceof ItemStack stack) {
                        return stack;
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            for (String name : new String[]{"setItemDirect", "setStackInSlot", "setStack", "setItemStack"}) {
                try {
                    Method method = delegate.getClass().getMethod(name, int.class, ItemStack.class);
                    method.invoke(delegate, slot, stack);
                    return;
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                }
            }
        }
    }
}
