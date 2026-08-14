package com.patternchecker.item;

import com.patternchecker.PatternCheckerMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Finds the checker in the vanilla inventory or in an optional accessory
 * inventory. Third-party APIs are accessed reflectively so neither Curios nor
 * Accessories is required to run the mod.
 */
public final class PatternCheckerPresence {

    private static boolean curiosUnavailable;
    private static boolean accessoriesUnavailable;

    private PatternCheckerPresence() {
    }

    public static boolean hasChecker(Player player) {
        return !findChecker(player).isEmpty();
    }

    public static ItemStack findChecker(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isChecker(stack)) {
                return stack;
            }
        }

        ItemStack accessories = findInAccessories(player);
        if (!accessories.isEmpty()) {
            return accessories;
        }
        return findInCurios(player);
    }

    private static boolean isChecker(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PatternCheckerToolItem;
    }

    private static ItemStack findInAccessories(Player player) {
        if (accessoriesUnavailable) {
            return ItemStack.EMPTY;
        }
        try {
            Class<?> capabilityClass =
                    Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
            Method get = capabilityClass.getMethod("get", LivingEntity.class);
            Object capability = get.invoke(null, player);
            if (capability == null) {
                return ItemStack.EMPTY;
            }

            Method firstEquipped = capabilityClass.getMethod("getFirstEquipped", Item.class);
            Object reference = firstEquipped.invoke(capability, PatternCheckerMod.PATTERN_CHECKER_TOOL.get());
            return stackFromReference(reference);
        } catch (ClassNotFoundException e) {
            accessoriesUnavailable = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            accessoriesUnavailable = true;
            PatternCheckerMod.LOGGER.debug("Accessories compatibility is unavailable", e);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findInCurios(Player player) {
        if (curiosUnavailable) {
            return ItemStack.EMPTY;
        }
        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getInventory = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            Object handler = unwrap(getInventory.invoke(null, player));
            if (handler == null) {
                return ItemStack.EMPTY;
            }

            Predicate<ItemStack> predicate = PatternCheckerPresence::isChecker;
            for (Method method : handler.getClass().getMethods()) {
                if (!method.getName().equals("findFirstCurio") || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameter = method.getParameterTypes()[0];
                Object argument;
                if (Predicate.class.isAssignableFrom(parameter)) {
                    argument = predicate;
                } else if (Item.class.isAssignableFrom(parameter)) {
                    argument = PatternCheckerMod.PATTERN_CHECKER_TOOL.get();
                } else {
                    continue;
                }
                ItemStack result = stackFromReference(unwrap(method.invoke(handler, argument)));
                if (!result.isEmpty()) {
                    return result;
                }
            }

            ItemStack direct = stackFromItemHandler(invokeNoArgs(handler, "getEquippedCurios"));
            if (!direct.isEmpty()) {
                return direct;
            }
            Object curios = invokeNoArgs(handler, "getCurios");
            if (curios instanceof Map<?, ?> map) {
                for (Object container : map.values()) {
                    ItemStack result = stackFromItemHandler(invokeNoArgs(container, "getStacks"));
                    if (!result.isEmpty()) {
                        return result;
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            curiosUnavailable = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            curiosUnavailable = true;
            PatternCheckerMod.LOGGER.debug("Curios compatibility is unavailable", e);
        }
        return ItemStack.EMPTY;
    }

    private static Object unwrap(Object value) throws ReflectiveOperationException {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        if (value == null) {
            return null;
        }
        if (value.getClass().getName().equals("net.neoforged.neoforge.common.util.LazyOptional")) {
            Object resolved = value.getClass().getMethod("resolve").invoke(value);
            return resolved instanceof Optional<?> optional ? optional.orElse(null) : resolved;
        }
        return value;
    }

    private static Object invokeNoArgs(Object target, String name) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        return unwrap(target.getClass().getMethod(name).invoke(target));
    }

    private static ItemStack stackFromReference(Object reference) throws ReflectiveOperationException {
        reference = unwrap(reference);
        if (reference instanceof ItemStack stack) {
            return isChecker(stack) ? stack : ItemStack.EMPTY;
        }
        if (reference == null) {
            return ItemStack.EMPTY;
        }
        for (String accessor : new String[]{"stack", "getStack"}) {
            try {
                Object value = reference.getClass().getMethod(accessor).invoke(reference);
                if (value instanceof ItemStack stack && isChecker(stack)) {
                    return stack;
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next commonly-used accessor name.
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack stackFromItemHandler(Object handler) throws ReflectiveOperationException {
        handler = unwrap(handler);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        Method slotsMethod = handler.getClass().getMethod("getSlots");
        Method stackMethod = handler.getClass().getMethod("getStackInSlot", int.class);
        int slots = (int) slotsMethod.invoke(handler);
        for (int i = 0; i < slots; i++) {
            Object value = stackMethod.invoke(handler, i);
            if (value instanceof ItemStack stack && isChecker(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
