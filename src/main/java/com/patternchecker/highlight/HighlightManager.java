package com.patternchecker.highlight;

import com.patternchecker.PatternCheckerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import com.patternchecker.network.HighlightPayload;
import com.patternchecker.network.HighlightPayload.HighlightData;
import com.patternchecker.network.NetworkHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the latest scan results per player so /patterncheck highlight <index>
 * can look up a pattern, and renders a temporary in-world highlight (particles)
 * at the block that holds a broken pattern.
 */
public final class HighlightManager {

    private static final long HIGHLIGHT_DURATION_TICKS = 20L * 15; // 15 seconds
    private static final int PARTICLE_INTERVAL_TICKS = 8;
    private static final int MAX_HIGHLIGHTS_PER_PLAYER = 5;

    public record ScanEntry(int index, Component label, BlockPos pos, ResourceKey<Level> dimension) {
    }

    /**
     * One broken pattern shown in the tool panel, with enough data to extract
     * or upload it (provider position + pattern slot).
     */
    public record ToolEntry(int index, Component label, String location, BlockPos pos,
                            ResourceKey<Level> dimension, int slot, Component summary, boolean error, String itemId,
                            Component outputDesc, Component inputDesc, int category) {
    }

    public record ActiveHighlight(BlockPos pos, ResourceKey<Level> dimension, long expiryTick,
                                  List<BlockPos> connections) {
    }

    private static final Map<UUID, List<ScanEntry>> LAST_SCANS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<ToolEntry>> TOOL_LIST = new ConcurrentHashMap<>();
    private static final Map<UUID, List<ActiveHighlight>> ACTIVE_HIGHLIGHTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Component> NOTICES = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SHOW_INPUT_ISSUES = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SHOW_DUPLICATE_ISSUES = new ConcurrentHashMap<>();

    private HighlightManager() {
    }

    public static void storeScan(UUID player, List<ScanEntry> entries) {
        LAST_SCANS.put(player, new ArrayList<>(entries));
    }

    public static void storeToolList(UUID player, List<ToolEntry> entries) {
        TOOL_LIST.put(player, new ArrayList<>(entries));
    }

    public static void setNotice(UUID player, Component notice) {
        NOTICES.put(player, notice);
    }

    /** Returns and clears the pending panel notice for the player. */
    public static Component takeNotice(UUID player) {
        return NOTICES.remove(player);
    }

    public static void toggleInputIssues(ServerPlayer player) {
        boolean next = !showInputIssues(player.getUUID());
        SHOW_INPUT_ISSUES.put(player.getUUID(), next);
    }

    public static boolean showInputIssues(UUID player) {
        return SHOW_INPUT_ISSUES.getOrDefault(player, false);
    }

    public static void toggleDuplicateIssues(ServerPlayer player) {
        boolean next = !showDuplicateIssues(player.getUUID());
        SHOW_DUPLICATE_ISSUES.put(player.getUUID(), next);
    }

    public static boolean showDuplicateIssues(UUID player) {
        return SHOW_DUPLICATE_ISSUES.getOrDefault(player, true);
    }

    public static List<ToolEntry> getToolList(UUID player) {
        return TOOL_LIST.getOrDefault(player, List.of());
    }

    public static ToolEntry getToolEntry(UUID player, int index) {
        List<ToolEntry> entries = TOOL_LIST.get(player);
        if (entries == null) {
            return null;
        }
        for (ToolEntry entry : entries) {
            if (entry.index() == index) {
                return entry;
            }
        }
        return null;
    }

    public static ScanEntry getEntry(UUID player, int index) {
        List<ScanEntry> entries = LAST_SCANS.get(player);
        if (entries == null) {
            return null;
        }
        for (ScanEntry entry : entries) {
            if (entry.index() == index) {
                return entry;
            }
        }
        return null;
    }

    public static void highlight(UUID player, ScanEntry entry, long currentTick) {
        highlight(player, entry.pos(), entry.dimension(), currentTick);
    }

    public static void highlight(UUID player, BlockPos pos, ResourceKey<Level> dimension, long currentTick) {
        highlight(player, pos, dimension, List.of(), currentTick);
    }

    public static void highlight(UUID player, BlockPos pos, ResourceKey<Level> dimension,
                                 List<BlockPos> connections, long currentTick) {
        ACTIVE_HIGHLIGHTS.compute(player, (uuid, list) -> {
            List<ActiveHighlight> highlights = list == null ? new ArrayList<>() : new ArrayList<>(list);
            highlights.removeIf(h -> h.pos().equals(pos) && h.dimension().equals(dimension));
            highlights.add(new ActiveHighlight(pos, dimension, currentTick + HIGHLIGHT_DURATION_TICKS, connections));
            while (highlights.size() > MAX_HIGHLIGHTS_PER_PLAYER) {
                highlights.remove(0);
            }
            return highlights;
        });
    }

    /** Highlights only the pattern provider itself. */
    public static void highlightEntry(ServerPlayer player, ToolEntry entry) {
        if (entry.pos() == null) {
            return;
        }
        highlight(player.getUUID(), entry.pos(), entry.dimension(), List.of(), player.server.getTickCount());
        sendHighlights(player);
    }

    public static void clear(net.minecraft.server.level.ServerPlayer player) {
        ACTIVE_HIGHLIGHTS.remove(player.getUUID());
        sendHighlights(player);
        setNotice(player.getUUID(), Component.translatable("patternchecker.msg.highlight.cleared"));
    }

    /** Pushes the player's active highlights to their client for rendering. */
    public static void sendHighlights(ServerPlayer player) {
        List<HighlightData> data = new ArrayList<>();
        for (ActiveHighlight highlight : ACTIVE_HIGHLIGHTS.getOrDefault(player.getUUID(), List.of())) {
            data.add(new HighlightData(highlight.dimension().location().toString(),
                    highlight.pos(), highlight.connections()));
        }
        PatternCheckerMod.LOGGER.info("sendHighlights: {} boxes to {}", data.size(), player.getGameProfile().getName());
        NetworkHandler.sendToPlayer(player, new HighlightPayload(data));
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ACTIVE_HIGHLIGHTS.isEmpty()) {
            return;
        }
        long tick = event.getServer().getTickCount();
        if (tick % PARTICLE_INTERVAL_TICKS == 0) {
            for (var playerEntry : ACTIVE_HIGHLIGHTS.entrySet()) {
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerEntry.getKey());
                if (player == null) {
                    continue;
                }
                for (ActiveHighlight highlight : playerEntry.getValue()) {
                    ServerLevel level = event.getServer().getLevel(highlight.dimension());
                    if (level != null && level.hasChunkAt(highlight.pos())) {
                        spawnHighlightParticles(level, player, highlight.pos());
                    }
                }
            }
        }
        boolean changed = false;
        for (var entry : ACTIVE_HIGHLIGHTS.entrySet()) {
            changed |= entry.getValue().removeIf(h -> tick >= h.expiryTick());
        }
        ACTIVE_HIGHLIGHTS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (!changed) {
            return;
        }
        // Refresh the client rendering after highlights expire.
        for (var entry : ACTIVE_HIGHLIGHTS.entrySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                sendHighlights(player);
            }
        }
    }

    private static void spawnHighlightParticles(
            ServerLevel level, ServerPlayer player, BlockPos pos) {
        double minX = pos.getX() + 0.05D;
        double minY = pos.getY() + 0.05D;
        double minZ = pos.getZ() + 0.05D;
        double maxX = pos.getX() + 0.95D;
        double maxY = pos.getY() + 0.95D;
        double maxZ = pos.getZ() + 0.95D;
        double[][] points = {
                {minX, minY, minZ}, {maxX, minY, minZ},
                {minX, maxY, minZ}, {maxX, maxY, minZ},
                {minX, minY, maxZ}, {maxX, minY, maxZ},
                {minX, maxY, maxZ}, {maxX, maxY, maxZ},
                {pos.getX() + 0.5D, maxY + 0.12D, pos.getZ() + 0.5D}
        };
        for (double[] point : points) {
            level.sendParticles(player, ParticleTypes.END_ROD, true,
                    point[0], point[1], point[2], 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
