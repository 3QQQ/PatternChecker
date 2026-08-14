package com.patternchecker.check;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the real ME network behind wireless pattern containers (AE2
 * Lightning Tech overloaded pattern providers). Accessed reflectively so the
 * mod stays compatible with packs that do not have AE2LT installed.
 */
public final class WirelessHelper {

    private static final String WIRELESS_PROVIDER_HOST =
            "com.moakiee.ae2lt.api.patternprovider.WirelessPatternProviderHost";
    private static final String FREQUENCY_API = "com.moakiee.ae2lt.api.frequency.FrequencyApi";

    private WirelessHelper() {
    }

    public static boolean isWirelessProvider(Object owner) {
        return owner != null && implementsInterface(owner.getClass(), WIRELESS_PROVIDER_HOST, new HashSet<>());
    }

    /**
     * Finds the main network grid a wireless container connects to, via its
     * wireless connection refs first, then via its bound frequency transmitter.
     */
    public static IGrid resolveGrid(ServerLevel level, Object owner) {
        if (!isWirelessProvider(owner)) {
            return null;
        }
        MinecraftServer server = level.getServer();

        // 1) Wireless connections point at receivers/transmitters on the main network.
        try {
            Object list = owner.getClass().getMethod("getConnections").invoke(owner);
            if (list instanceof List<?> connections) {
                for (Object ref : connections) {
                    IGrid grid = gridFromTarget(server, ref);
                    if (grid != null) {
                        return grid;
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }

        // 2) Fall back to the bound frequency's transmitter.
        try {
            Method getFrequencyId = owner.getClass().getMethod("getFrequencyId");
            int frequency = (Integer) getFrequencyId.invoke(owner);
            Class<?> api = Class.forName(FREQUENCY_API);
            Method getTransmitter = api.getMethod("getTransmitter", MinecraftServer.class, int.class);
            Optional<?> transmitter = (Optional<?>) getTransmitter.invoke(null, server, frequency);
            if (transmitter.isPresent()) {
                IGrid grid = gridFromTarget(server, transmitter.get());
                if (grid != null) {
                    return grid;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    /** Returns the remote receiver/transmitter positions of a wireless container in this dimension. */
    public static List<BlockPos> resolveConnectionTargets(ServerLevel level, Object owner) {
        List<BlockPos> targets = new ArrayList<>();
        if (!isWirelessProvider(owner)) {
            return targets;
        }
        try {
            Object list = owner.getClass().getMethod("getConnections").invoke(owner);
            if (list instanceof List<?> connections) {
                for (Object ref : connections) {
                    try {
                        Object dim = ref.getClass().getMethod("dimension").invoke(ref);
                        Object pos = ref.getClass().getMethod("pos").invoke(ref);
                        if (dim instanceof ResourceKey<?> key && key.equals(level.dimension())
                                && pos instanceof BlockPos blockPos) {
                            targets.add(blockPos);
                        }
                    } catch (ReflectiveOperationException ignored) {
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return targets;
    }

    private static IGrid gridFromTarget(MinecraftServer server, Object target) {
        if (target == null) {
            return null;
        }
        try {
            Object dim = target.getClass().getMethod("dimension").invoke(target);
            Object pos = target.getClass().getMethod("pos").invoke(target);
            if (dim instanceof ResourceKey<?> key && pos instanceof BlockPos blockPos) {
                ServerLevel targetLevel = server.getLevel((ResourceKey<Level>) key);
                if (targetLevel != null) {
                    return gridAt(targetLevel, blockPos);
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static IGrid gridAt(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            IGridNode node = GridHelper.getExposedNode(level, pos, direction);
            if (node != null && node.getGrid() != null) {
                return node.getGrid();
            }
        }
        return null;
    }

    private static boolean implementsInterface(Class<?> cls, String interfaceName, Set<Class<?>> visited) {
        if (cls == null || !visited.add(cls)) {
            return false;
        }
        for (Class<?> iface : cls.getInterfaces()) {
            if (iface.getName().equals(interfaceName) || implementsInterface(iface, interfaceName, visited)) {
                return true;
            }
        }
        return implementsInterface(cls.getSuperclass(), interfaceName, visited);
    }
}
