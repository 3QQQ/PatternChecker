package com.patternchecker.command;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.patternchecker.PatternCheckerMod;
import com.patternchecker.action.PatternActions;
import com.patternchecker.highlight.HighlightManager;
import com.patternchecker.highlight.HighlightManager.ScanEntry;
import com.patternchecker.highlight.HighlightManager.ToolEntry;
import com.patternchecker.check.GridDiscovery;
import com.patternchecker.check.BoundNetwork;
import com.patternchecker.check.PatternInventoryHelper;
import com.patternchecker.check.PatternIssue;
import com.patternchecker.check.PatternScanner.ScannedPattern;
import com.patternchecker.check.PatternScanner;
import com.patternchecker.check.PatternScanner.ScanResult;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /patterncheck scan        - scans the ME network the player is looking at
 * /patterncheck scan all    - scans every loaded ME network in the dimension
 */
public final class PatternCheckCommand {

    private static final Map<UUID, Integer> DEFERRED_SCANS =
            Collections.synchronizedMap(new HashMap<>());

    private PatternCheckCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("patterncheck")
                .then(Commands.literal("scan")
                        .executes(ctx -> scanTargeted(ctx.getSource())))
                .then(Commands.literal("highlight")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> highlight(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "index")))))
                .then(Commands.literal("debug")
                        .executes(ctx -> debug(ctx.getSource())))
                .executes(ctx -> help(ctx.getSource())));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("patternchecker.msg.help"), false);
        return 1;
    }

    private static int debug(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("patternchecker.msg.playersOnly"));
            return 0;
        }
        ServerLevel level = player.serverLevel();

        int chunkCount = 0;
        for (var holder : level.getChunkSource().chunkMap.getChunks()) {
            if (holder.getTickingChunk() != null) {
                chunkCount++;
            }
        }
        int fChunkCount = chunkCount;
        source.sendSuccess(() -> Component.literal("[Debug] 已加载区块: " + fChunkCount), false);

        List<IGrid> grids = GridDiscovery.findAllGrids(level);
        source.sendSuccess(() -> Component.literal("[Debug] 发现 ME 网络: " + grids.size()), false);
        for (IGrid grid : grids) {
            int hosts = 0;
            int slots = 0;
            int nonEmpty = 0;
            for (IGridNode node : grid.getNodes()) {
                var inv = PatternInventoryHelper.patternInventoryOf(node.getOwner());
                if (inv != null) {
                    hosts++;
                    slots += inv.size();
                    for (int i = 0; i < inv.size(); i++) {
                        if (!inv.getStackInSlot(i).isEmpty()) {
                            nonEmpty++;
                        }
                    }
                }
            }
            int fHosts = hosts;
            int fSlots = slots;
            int fNonEmpty = nonEmpty;
            source.sendSuccess(() -> Component.literal(String.format(
                    "[Debug] 网络节点=%d, 供应器宿主=%d, 样板槽=%d, 非空槽=%d",
                    grid.size(), fHosts, fSlots, fNonEmpty)), false);
        }
        return 1;
    }

    private static int scanTargeted(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("patternchecker.msg.playersOnly"));
            return 0;
        }
        return scanTargeted(player, false) ? 1 : 0;
    }

    public static boolean scanTargeted(ServerPlayer player) {
        return scanTargeted(player, false);
    }

    public static boolean scanTargeted(ServerPlayer player, boolean silent) {
        CommandSourceStack source = player.createCommandSourceStack();
        ServerLevel level = player.serverLevel();

        ItemStack tool = BoundNetwork.findTool(player);
        IGrid bound = BoundNetwork.resolve(player, tool);
        if (bound == null) {
            if (silent) {
                HighlightManager.setNotice(player.getUUID(),
                        Component.translatable("patternchecker.bound.required"));
            } else {
                source.sendFailure(Component.translatable("patternchecker.bound.required"));
            }
            return false;
        }
        if (!silent) {
            source.sendSuccess(() -> Component.translatable("patternchecker.bound.scanning"), false);
        }
        reportAndStore(source, level, List.of(PatternScanner.scanGrid(bound, level)), silent);
        return true;
    }

    /**
     * Queues the initial scan so opening the tool menu is not blocked by a
     * large recipe registry. Explicit Scan button/command actions remain
     * synchronous and therefore still provide an immediate refresh request.
     */
    public static void scheduleTargetedScan(ServerPlayer player) {
        if (player != null) {
            // Leave a couple of client frames for the menu to open before
            // starting the expensive network/recipe scan.
            DEFERRED_SCANS.put(player.getUUID(), player.server.getTickCount() + 2);
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DEFERRED_SCANS.isEmpty()) {
            return;
        }
        int tick = event.getServer().getTickCount();
        List<UUID> pending = new ArrayList<>();
        synchronized (DEFERRED_SCANS) {
            DEFERRED_SCANS.entrySet().removeIf(entry -> {
                if (entry.getValue() <= tick) {
                    pending.add(entry.getKey());
                    return true;
                }
                return false;
            });
        }
        for (UUID playerId : pending) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && player.connection != null) {
                scanTargeted(player, true);
            }
        }
    }

    public static List<ScanEntry> buildEntries(ServerLevel level, List<ScanResult> results,
                                               boolean showInput, boolean showDuplicates) {
        List<ScanEntry> entries = new ArrayList<>();
        int issueIndex = 1;
        for (ScanResult result : results) {
            for (PatternIssue issue : PatternScanner.filterIssues(
                    result.issues(), showInput, showDuplicates)) {
                entries.add(new ScanEntry(issueIndex, issue.message(), issue.pos(), level.dimension()));
                issueIndex++;
            }
        }
        return entries;
    }

    /** Builds the deduplicated per-pattern list shown in the tool panel. */
    public static List<ToolEntry> buildToolList(ServerLevel level, List<ScanResult> results,
                                                boolean showInput, boolean showDuplicates) {
        List<ToolEntry> entries = new ArrayList<>();
        int index = 1;
        for (ScanResult result : results) {
            for (ScannedPattern pattern : result.patterns()) {
                var visible = PatternScanner.filterIssues(
                        pattern.issues(), showInput, showDuplicates);
                if (visible.isEmpty()) {
                    continue;
                }
                PatternIssue first = visible.get(0);
                boolean error = first.type() == PatternIssue.Type.ERROR;
                entries.add(new ToolEntry(index, pattern.name(), pattern.location(), pattern.pos(),
                        level.dimension(), pattern.slot(), first.message(), error,
                        pattern.itemId(), pattern.outputDesc(), pattern.inputDesc(),
                        first.category().ordinal()));
                index++;
            }
        }
        return entries;
    }

    private static void reportAndStore(CommandSourceStack source, ServerLevel level, List<ScanResult> results,
                                       boolean silent) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("patternchecker.msg.playersOnly"));
            return;
        }

        boolean showInput = HighlightManager.showInputIssues(player.getUUID());
        boolean showDuplicates = HighlightManager.showDuplicateIssues(player.getUUID());
        List<ScanEntry> entries = buildEntries(level, results, showInput, showDuplicates);
        int totalPatterns = 0;
        int totalProviderPatterns = 0;
        int totalContainerPatterns = 0;
        int totalStoragePatterns = 0;
        int totalErrors = 0;
        int totalWarnings = 0;

        boolean multiple = results.size() > 1;
        int networkIndex = 1;
        int issueIndex = 1;
        for (ScanResult result : results) {
            totalPatterns += result.totalPatterns();
            totalProviderPatterns += result.providerPatterns();
            totalContainerPatterns += result.containerPatterns();
            totalStoragePatterns += result.storagePatterns();
            int[] counts = PatternScanner.visibleCounts(result, showInput, showDuplicates);
            totalErrors += counts[0];
            totalWarnings += counts[1];
            if (multiple && !silent) {
                int currentNetwork = networkIndex;
                source.sendSuccess(() -> Component.translatable("patternchecker.msg.networkHeader",
                        currentNetwork, result.totalPatterns(), result.providerPatterns(),
                        result.containerPatterns(), result.storagePatterns(),
                        counts[0], counts[1]), false);
            }
            if (!silent) {
                for (Component verdict : result.verdicts()) {
                    source.sendSuccess(() -> verdict, false);
                }
            }
            if (!silent) {
                for (PatternIssue issue : PatternScanner.filterIssues(
                        result.issues(), showInput, showDuplicates)) {
                    int currentIndex = issueIndex;
                    source.sendSuccess(() -> issue.toChatLine(currentIndex), false);
                    issueIndex++;
                }
            }
            networkIndex++;
        }

        HighlightManager.storeScan(player.getUUID(), entries);
        HighlightManager.storeToolList(player.getUUID(),
                buildToolList(level, results, showInput, showDuplicates));
        PatternActions.sendToolList(player);
        int patterns = totalPatterns;
        int providerPatterns = totalProviderPatterns;
        int containerPatterns = totalContainerPatterns;
        int storagePatterns = totalStoragePatterns;
        int errors = totalErrors;
        int warnings = totalWarnings;
        PatternCheckerMod.LOGGER.info(
                "Pattern scan complete for {}: total={}, providers={}, containers={}, storage={}, errors={}, warnings={}",
                player.getGameProfile().getName(), patterns, providerPatterns,
                containerPatterns, storagePatterns, errors, warnings);
        if (silent) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.msg.scanDone", patterns, errors, warnings));
        } else {
            source.sendSuccess(() -> Component.translatable("patternchecker.msg.header",
                    patterns, providerPatterns, containerPatterns, storagePatterns, errors, warnings), false);
            if (patterns == 0 && errors + warnings == 0) {
                source.sendSuccess(() -> Component.translatable("patternchecker.msg.noPatternsHint"), false);
            }
            if (entries.isEmpty()) {
                source.sendSuccess(() -> Component.translatable("patternchecker.msg.clean"), false);
            }
        }
    }

    private static int highlight(CommandSourceStack source, int index) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("patternchecker.msg.playersOnly"));
            return 0;
        }
        ScanEntry entry = HighlightManager.getEntry(player.getUUID(), index);
        if (entry == null) {
            source.sendFailure(Component.translatable("patternchecker.msg.highlight.noScan"));
            return 0;
        }
        if (entry.pos() == null) {
            source.sendFailure(Component.translatable("patternchecker.msg.highlight.noPos"));
            return 0;
        }
        HighlightManager.highlight(player.getUUID(), entry, player.serverLevel().getServer().getTickCount());
        HighlightManager.sendHighlights(player);
        source.sendSuccess(() -> Component.translatable("patternchecker.msg.highlighted", entry.label()), false);
        return 1;
    }

}
