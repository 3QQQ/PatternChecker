package com.patternchecker.network;

import com.patternchecker.PatternCheckerMod;
import com.patternchecker.action.PatternActions;
import com.patternchecker.check.BoundNetwork;
import com.patternchecker.command.PatternCheckCommand;
import com.patternchecker.highlight.HighlightManager;
import com.patternchecker.menu.PatternEditMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Common-side payload handler. The received list is parked in a shared buffer
 * that the client screen polls on its tick, so no client classes are touched
 * on the server side.
 */
public final class NetworkHandler {

    private static volatile ToolListPayload pending;
    private static volatile HighlightPayload pendingHighlights;
    private static volatile PatternEditPayload pendingEdit;

    private NetworkHandler() {
    }

    public static void handleToolList(ToolListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> pending = payload);
    }

    /**
     * Returns the latest received list, if any, and clears the buffer.
     */
    public static ToolListPayload poll() {
        ToolListPayload payload = pending;
        pending = null;
        return payload;
    }

    public static void clearPendingToolList() {
        pending = null;
    }

    public static void handleHighlights(HighlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            pendingHighlights = payload;
            PatternCheckerMod.LOGGER.info("Client received highlight payload: {} entries", payload.highlights().size());
        });
    }

    public static HighlightPayload pollHighlights() {
        HighlightPayload payload = pendingHighlights;
        pendingHighlights = null;
        return payload;
    }

    public static void handleEdit(PatternEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> pendingEdit = payload);
    }

    public static PatternEditPayload pollEdit() {
        PatternEditPayload payload = pendingEdit;
        pendingEdit = null;
        return payload;
    }

    public static void handleEncode(PatternEncodePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> PatternActions.encodeAndUpload(player, payload));
        }
    }

    public static void handleSlot(PatternSlotPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> {
                if (!(player.containerMenu instanceof PatternEditMenu menu)) {
                    return;
                }
                if (payload.slotIndex() < 0 || payload.slotIndex() >= PatternEditMenu.GRID_SLOTS) {
                    return;
                }
                if (payload.count() <= 0 || payload.itemId().isEmpty()) {
                    menu.getGrid().setItem(payload.slotIndex(), ItemStack.EMPTY);
                    menu.broadcastChanges();
                    return;
                }
                try {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(payload.itemId()));
                    if (item == null) {
                        return;
                    }
                    menu.getGrid().setItem(payload.slotIndex(),
                            new ItemStack(item, Math.min(Math.max(1, payload.count()), 999)));
                    menu.broadcastChanges();
                } catch (Exception e) {
                    com.patternchecker.PatternCheckerMod.LOGGER.warn("handleSlot failed", e);
                }
            });
        }
    }

    public static void handleToolAction(PatternToolActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> {
                if (payload.action() == PatternToolActionPayload.ACTION_SYNC) {
                    PatternActions.beginTerminalSession(player);
                    PatternActions.sendToolList(player);
                    return;
                }
                if (BoundNetwork.findTool(player).isEmpty()) {
                    return;
                }
                switch (payload.action()) {
                    case PatternToolActionPayload.ACTION_SCAN -> {
                        PatternCheckCommand.scanTargeted(player, true);
                        return;
                    }
                    case PatternToolActionPayload.ACTION_UNBIND -> {
                        ItemStack tool = BoundNetwork.findTool(player);
                        if (!tool.isEmpty()) {
                            BoundNetwork.clear(tool);
                        }
                        HighlightManager.setNotice(player.getUUID(),
                                Component.translatable("patternchecker.bound.unbound"));
                        PatternActions.sendToolList(player);
                        return;
                    }
                    case PatternToolActionPayload.ACTION_TOGGLE_INPUT -> {
                        HighlightManager.toggleInputIssues(player);
                        PatternCheckCommand.scanTargeted(player, true);
                        return;
                    }
                    case PatternToolActionPayload.ACTION_TOGGLE_DUPLICATE -> {
                        HighlightManager.toggleDuplicateIssues(player);
                        PatternCheckCommand.scanTargeted(player, true);
                        return;
                    }
                    case PatternToolActionPayload.ACTION_WRITE -> {
                        PatternActions.uploadCurrentTerminal(player);
                        return;
                    }
                    default -> {
                    }
                }
                var entry = HighlightManager.getToolEntry(player.getUUID(), payload.entryIndex());
                if (entry == null) {
                    return;
                }
                switch (payload.action()) {
                    case PatternToolActionPayload.ACTION_HIGHLIGHT -> PatternActions.highlight(player, entry);
                    case PatternToolActionPayload.ACTION_EXTRACT -> PatternActions.extract(player, entry);
                    case PatternToolActionPayload.ACTION_UPLOAD -> PatternActions.upload(player, entry);
                    case PatternToolActionPayload.ACTION_SELECT -> PatternActions.loadEntry(player, entry);
                    default -> {
                    }
                }
            });
        }
    }
}
