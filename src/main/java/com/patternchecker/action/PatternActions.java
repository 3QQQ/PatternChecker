package com.patternchecker.action;

import com.patternchecker.PatternCheckerMod;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.patternchecker.check.PatternInventoryHelper;
import com.patternchecker.check.BoundNetwork;
import com.patternchecker.command.PatternCheckCommand;
import com.patternchecker.highlight.HighlightManager;
import com.patternchecker.highlight.HighlightManager.ToolEntry;
import com.patternchecker.item.PatternCheckerToolItem;
import com.patternchecker.menu.PatternEditMenu;
import com.patternchecker.network.ToolListPayload;
import com.patternchecker.network.PatternEditPayload;
import com.patternchecker.network.PatternEditPayload.Slot;
import com.patternchecker.network.PatternEncodePayload;
import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side actions behind the tool panel: pushing the broken-pattern list
 * to the client, highlighting, extracting and uploading patterns.
 */
public final class PatternActions {

    /**
     * Legacy pending edits used by the old item-hosted terminal.
     */
    private static final Map<UUID, PendingEdit> PENDING_EDITS = new HashMap<>();
    /**
     * Pattern selected in a normal AE2 encoding terminal for write-back.
     */
    private static final Map<UUID, TerminalEdit> TERMINAL_EDITS = new HashMap<>();
    private static final Method AE2_ENCODE_PATTERN = findAe2EncodePattern();

    public record PendingEdit(ToolEntry entry, ItemStack pattern) {
    }

    private record TerminalEdit(ToolEntry entry, ItemStack originalPattern) {
    }

    private static Method findAe2EncodePattern() {
        try {
            Method method = PatternEncodingTermMenu.class.getDeclaredMethod("encodePattern");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException e) {
            PatternCheckerMod.LOGGER.error("Cannot access AE2 pattern encoder", e);
            return null;
        }
    }

    private static ItemStack encodeCurrentTerminalPattern(PatternEncodingTermMenu menu)
            throws ReflectiveOperationException {
        if (AE2_ENCODE_PATTERN == null) {
            throw new NoSuchMethodException("AE2 PatternEncodingTermMenu.encodePattern");
        }
        return (ItemStack) AE2_ENCODE_PATTERN.invoke(menu);
    }

    private PatternActions() {
    }

    public static PendingEdit takePendingEdit(UUID playerId) {
        return PENDING_EDITS.remove(playerId);
    }

    public static void beginTerminalSession(ServerPlayer player) {
        TERMINAL_EDITS.remove(player.getUUID());
    }

    /**
     * Opens only the checker interface. The item itself is not an encoder.
     */
    public static void openToolPanel(ServerPlayer player) {
        ItemStack tool = BoundNetwork.findTool(player);
        if (tool.isEmpty()) {
            return;
        }
        PatternCheckCommand.scanTargeted(player, true);
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new com.patternchecker.menu.PatternCheckMenu(
                        containerId, inventory),
                Component.translatable("patternchecker.menu.title")));
    }

    /**
     * Loads the pattern from a provider slot into the encoding terminal that is
     * currently open on the server (triggered by clicking a list entry).
     */
    public static void loadEntry(ServerPlayer player, ToolEntry entry) {
        Object host = findPatternHost(player, entry);
        if (host == null) {
            return;
        }
        InternalInventory inv = PatternInventoryHelper.patternInventoryOf(host);
        if (inv == null || entry.slot() < 0 || entry.slot() >= inv.size()) {
            return;
        }
        ItemStack stack = inv.getStackInSlot(entry.slot());
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return;
        }
        if (player.containerMenu instanceof PatternEncodingTermMenu menu
                && menu.getHost() instanceof IPatternTerminalMenuHost terminalHost) {
            InternalInventory encodedPatternInv = terminalHost.getLogic().getEncodedPatternInv();
            if (!encodedPatternInv.getStackInSlot(0).isEmpty()) {
                HighlightManager.setNotice(player.getUUID(),
                        Component.translatable("patternchecker.encode.clearEncodedSlot"));
                sendToolList(player);
                return;
            }

            // Let AE2 decode the selected pattern and populate its fake input/output
            // grid, but never leave the copied pattern in the real, extractable
            // encoded-pattern slot. An empty stack does not clear the decoded grid.
            try {
                encodedPatternInv.setItemDirect(0, stack.copy());
            } finally {
                encodedPatternInv.setItemDirect(0, ItemStack.EMPTY);
            }
            TERMINAL_EDITS.put(player.getUUID(), new TerminalEdit(entry, stack.copy()));
            menu.broadcastChanges();
        }
    }

    /**
     * Uses AE2's own mode-specific encoder and replaces the selected provider
     * pattern in place. This supports crafting, processing, smithing and
     * stonecutting patterns without touching the terminal's physical pattern
     * slots.
     */
    public static void uploadCurrentTerminal(ServerPlayer player) {
        TerminalEdit edit = TERMINAL_EDITS.get(player.getUUID());
        if (edit == null) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.selectFirst"));
            sendToolList(player);
            return;
        }
        ToolEntry entry = edit.entry();
        if (!(player.containerMenu instanceof PatternEncodingTermMenu menu)) {
            return;
        }

        try {
            ItemStack encoded = encodeCurrentTerminalPattern(menu);
            if (encoded == null || encoded.isEmpty()) {
                player.displayClientMessage(Component.translatable("patternchecker.encode.failed"), true);
                return;
            }
            Component customName = edit.originalPattern().get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                encoded.set(DataComponents.CUSTOM_NAME, customName);
            }
            if (!uploadEncodedPattern(player, entry, encoded, edit.originalPattern(), false)) {
                TERMINAL_EDITS.remove(player.getUUID());
                menu.clear();
                sendToolList(player);
                return;
            }
            TERMINAL_EDITS.remove(player.getUUID());
            menu.clear();
            if (PatternCheckCommand.scanTargeted(player, true)) {
                // scanTargeted already sent the refreshed list with the write
                // success notice. Discard its deferred generic scan notice.
                HighlightManager.takeNotice(player.getUUID());
            } else {
                sendToolList(player);
            }
        } catch (Exception e) {
            PatternCheckerMod.LOGGER.error("Failed to encode pattern from terminal module", e);
            player.displayClientMessage(Component.translatable("patternchecker.encode.failed"), true);
        }
    }

    public static void sendToolList(ServerPlayer player) {
        List<ToolListPayload.Entry> entries = new ArrayList<>();
        for (ToolEntry entry : HighlightManager.getToolList(player.getUUID())) {
            entries.add(new ToolListPayload.Entry(
                    entry.index(),
                    entry.label().getString(),
                    entry.location(),
                    entry.summary().getString(),
                    entry.pos() != null && entry.slot() >= 0,
                    entry.error(),
                    entry.itemId(),
                    entry.outputDesc(),
                    entry.inputDesc(),
                    entry.category()));
        }
        ItemStack tool = BoundNetwork.findTool(player);
        boolean bound = BoundNetwork.isBound(tool);
        String boundLabel = bound ? BoundNetwork.describe(tool) : "";
        Component notice = HighlightManager.takeNotice(player.getUUID());
        if (notice == null) {
            notice = Component.empty();
        }
        PacketDistributor.sendToPlayer(player,
                new ToolListPayload(entries, !tool.isEmpty(), HighlightManager.showInputIssues(player.getUUID()),
                        HighlightManager.showDuplicateIssues(player.getUUID()), bound, boundLabel, notice));
    }

    public static void highlight(ServerPlayer player, ToolEntry entry) {
        if (entry.pos() == null) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.msg.highlight.noPos"));
            return;
        }
        HighlightManager.highlightEntry(player, entry);
        HighlightManager.setNotice(player.getUUID(),
                Component.translatable("patternchecker.msg.highlighted", entry.label()));
    }

    /**
     * Takes the broken pattern out of its provider slot and delivers it to the
     * player's inventory (drops it if the inventory is full).
     */
    public static void extract(ServerPlayer player, ToolEntry entry) {
        Object host = findPatternHost(player, entry);
        if (host == null) {
            return;
        }
        InternalInventory inv = PatternInventoryHelper.patternInventoryOf(host);
        if (inv == null || entry.slot() < 0 || entry.slot() >= inv.size()) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.badSlot"));
            return;
        }
        ItemStack stack = inv.getStackInSlot(entry.slot());
        if (stack.isEmpty()) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.badSlot"));
            return;
        }
        Component name = stack.getHoverName();
        try {
            inv.setItemDirect(entry.slot(), ItemStack.EMPTY);
            saveChanges(host);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.extracted", name));
        } catch (Exception e) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.failed", name));
        }
    }

    /**
     * Takes the first encoded pattern from the player's inventory and puts it
     * back into the pattern's original slot (or the first empty slot if the
     * original one is occupied).
     */
    public static void upload(ServerPlayer player, ToolEntry entry) {
        ItemStack toUpload = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && !(stack.getItem() instanceof PatternCheckerToolItem)
                    && PatternDetailsHelper.isEncodedPattern(stack)) {
                toUpload = stack;
                break;
            }
        }
        if (toUpload.isEmpty()) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.noPattern"));
            return;
        }

        Object host = findPatternHost(player, entry);
        if (host == null) {
            return;
        }
        InternalInventory inv = PatternInventoryHelper.patternInventoryOf(host);
        if (inv == null) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.noProvider"));
            return;
        }

        // Prefer the pattern's original slot; fall back to the first empty slot.
        int targetSlot = -1;
        boolean originalSlot = entry.slot() >= 0 && entry.slot() < inv.size()
                && inv.getStackInSlot(entry.slot()).isEmpty();
        if (originalSlot) {
            targetSlot = entry.slot();
        } else {
            for (int i = 0; i < inv.size(); i++) {
                if (inv.getStackInSlot(i).isEmpty()) {
                    targetSlot = i;
                    break;
                }
            }
        }
        if (targetSlot < 0) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.noEmptySlot"));
            return;
        }

        Component name = toUpload.getHoverName();
        try {
            inv.setItemDirect(targetSlot, toUpload.copy());
            saveChanges(host);
            toUpload.shrink(toUpload.getCount());
            if (originalSlot) {
                HighlightManager.setNotice(player.getUUID(),
                        Component.translatable("patternchecker.action.uploaded", name));
            } else {
                HighlightManager.setNotice(player.getUUID(),
                        Component.translatable("patternchecker.action.uploaded.otherSlot", name));
            }
        } catch (Exception e) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.failed", name));
        }
    }

    /**
     * Opens the encoding terminal with the selected pattern pre-loaded.
     */
    public static void openEdit(ServerPlayer player, ToolEntry entry) {
        HighlightManager.setNotice(player.getUUID(),
                Component.translatable("patternchecker.encode.useTerminal"));
        sendToolList(player);
    }

    /**
     * Decodes the pattern in the entry's provider slot and registers it as the
     * pending edit for the next terminal open. Returns false when the pattern
     * cannot be read (a notice is shown to the player).
     */
    private static boolean preparePendingEdit(ServerPlayer player, ToolEntry entry) {
        PatternCheckerMod.LOGGER.info("openEdit called for index {}", entry != null ? entry.index() : -1);
        Object host = findPatternHost(player, entry);
        if (host == null) {
            PatternCheckerMod.LOGGER.warn("openEdit: no pattern host found");
            return false;
        }
        InternalInventory inv = PatternInventoryHelper.patternInventoryOf(host);
        if (inv == null || entry.slot() < 0 || entry.slot() >= inv.size()) {
            PatternCheckerMod.LOGGER.warn("openEdit: bad slot {}", entry.slot());
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.badSlot"));
            return false;
        }
        ItemStack stack = inv.getStackInSlot(entry.slot());
        if (stack.isEmpty()) {
            PatternCheckerMod.LOGGER.warn("openEdit: empty slot");
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.badSlot"));
            return false;
        }
        IPatternDetails details;
        try {
            details = PatternDetailsHelper.decodePattern(stack, player.serverLevel());
        } catch (Exception e) {
            PatternCheckerMod.LOGGER.warn("openEdit: decode failed", e);
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.failed"));
            return false;
        }
        if (details == null) {
            PatternCheckerMod.LOGGER.warn("openEdit: null details");
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.failed"));
            return false;
        }
        PENDING_EDITS.put(player.getUUID(), new PendingEdit(entry, stack.copy()));
        return true;
    }

    /**
     * Writes an encoded pattern back into the provider slot that held the original.
     */
    public static boolean uploadEncodedPattern(Player player, ToolEntry entry, ItemStack encoded) {
        return uploadEncodedPattern(player, entry, encoded, ItemStack.EMPTY, true);
    }

    public static boolean uploadEncodedPattern(Player player, ToolEntry entry, ItemStack encoded,
                                               ItemStack expectedOriginal) {
        return uploadEncodedPattern(player, entry, encoded, expectedOriginal, true);
    }

    private static boolean uploadEncodedPattern(Player player, ToolEntry entry, ItemStack encoded,
                                                ItemStack expectedOriginal, boolean sendUpdate) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        Object host = findPatternHost(serverPlayer, entry);
        if (host == null) {
            return false;
        }
        InternalInventory inv = PatternInventoryHelper.patternInventoryOf(host);
        if (inv == null) {
            HighlightManager.setNotice(serverPlayer.getUUID(),
                    Component.translatable("patternchecker.action.noProvider"));
            return false;
        }
        int target = entry.slot();
        if (target < 0 || target >= inv.size()) {
            target = -1;
            for (int i = 0; i < inv.size(); i++) {
                if (inv.getStackInSlot(i).isEmpty()) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) {
            HighlightManager.setNotice(serverPlayer.getUUID(),
                    Component.translatable("patternchecker.action.noEmptySlot"));
            return false;
        }
        if (!expectedOriginal.isEmpty()) {
            ItemStack current = inv.getStackInSlot(target);
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, expectedOriginal)) {
                HighlightManager.setNotice(serverPlayer.getUUID(),
                        Component.translatable("patternchecker.encode.originalChanged"));
                if (sendUpdate) {
                    sendToolList(serverPlayer);
                }
                return false;
            }
        }
        boolean success = false;
        try {
            inv.setItemDirect(target, encoded.copy());
            saveChanges(host);
            HighlightManager.setNotice(serverPlayer.getUUID(),
                    Component.translatable("patternchecker.encode.done", encoded.getHoverName()));
            success = true;
        } catch (Exception e) {
            HighlightManager.setNotice(serverPlayer.getUUID(),
                    Component.translatable("patternchecker.encode.failed"));
        }
        if (sendUpdate) {
            sendToolList(serverPlayer);
        }
        return success;
    }

    /**
     * Encodes the edited grid back into a processing pattern and uploads it.
     */
    public static void encodeFromEditMenu(ServerPlayer player, PatternEditMenu menu) {
        ToolEntry entry = menu.getEntry();
        if (entry == null) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.failed"));
            return;
        }
        if (!menu.hasBlankPattern()) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.needBlank"));
            return;
        }

        List<GenericStack> inputs = new ArrayList<>();
        boolean hasInput = false;
        for (int i = 0; i < PatternEditMenu.INPUT_SLOTS; i++) {
            ItemStack s = menu.getGrid().getItem(i);
            if (s.isEmpty()) {
                inputs.add(null);
                continue;
            }
            inputs.add(new GenericStack(AEItemKey.of(s), s.getCount()));
            hasInput = true;
        }
        List<GenericStack> outputs = new ArrayList<>();
        for (int i = 0; i < PatternEditMenu.OUTPUT_SLOTS; i++) {
            ItemStack s = menu.getGrid().getItem(PatternEditMenu.INPUT_SLOTS + i);
            if (s.isEmpty()) {
                outputs.add(null);
                continue;
            }
            outputs.add(new GenericStack(AEItemKey.of(s), s.getCount()));
        }
        if (!hasInput || outputs.isEmpty() || outputs.get(0) == null) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.failed"));
            return;
        }

        ItemStack encoded;
        try {
            encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
        } catch (Exception e) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.failed"));
            return;
        }

        Object host = findPatternHost(player, entry);
        if (host == null) {
            return;
        }
        InternalInventory inv = PatternInventoryHelper.patternInventoryOf(host);
        if (inv == null) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.noProvider"));
            return;
        }
        int target = entry.slot();
        if (target < 0 || target >= inv.size()) {
            target = -1;
            for (int i = 0; i < inv.size(); i++) {
                if (inv.getStackInSlot(i).isEmpty()) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.noEmptySlot"));
            return;
        }

        try {
            inv.setItemDirect(target, encoded);
            saveChanges(host);
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.done", encoded.getHoverName()));
            // Return the blank pattern to the player instead of consuming it.
            ItemStack blank = menu.getGrid().getItem(PatternEditMenu.BLANK_SLOT).copy();
            menu.getGrid().setItem(PatternEditMenu.BLANK_SLOT, ItemStack.EMPTY);
            if (!player.getInventory().add(blank)) {
                player.drop(blank, false);
            }
        } catch (Exception e) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.failed"));
        }
        player.closeContainer();
        sendToolList(player);
    }

    /**
     * Encodes a new processing pattern from the edited slots and puts it back.
     */
    public static void encodeAndUpload(ServerPlayer player, PatternEncodePayload payload) {
        ToolEntry entry = HighlightManager.getToolEntry(player.getUUID(), payload.index());
        if (entry == null) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.failed"));
            return;
        }

        List<GenericStack> inputs = new ArrayList<>();
        boolean hasInput = false;
        for (Slot slot : payload.inputs()) {
            if (slot.isEmpty()) {
                inputs.add(null);
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(slot.itemId()));
            if (item == null || slot.count() <= 0) {
                HighlightManager.setNotice(player.getUUID(),
                        Component.translatable("patternchecker.encode.failed"));
                return;
            }
            inputs.add(new GenericStack(AEItemKey.of(item), slot.count()));
            hasInput = true;
        }
        List<GenericStack> outputs = new ArrayList<>();
        for (Slot slot : payload.outputs()) {
            if (slot.isEmpty()) {
                outputs.add(null);
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(slot.itemId()));
            if (item == null || slot.count() <= 0) {
                HighlightManager.setNotice(player.getUUID(),
                        Component.translatable("patternchecker.encode.failed"));
                return;
            }
            outputs.add(new GenericStack(AEItemKey.of(item), slot.count()));
        }
        if (!hasInput || outputs.isEmpty() || outputs.get(0) == null) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.failed"));
            return;
        }

        ItemStack encoded;
        try {
            encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
        } catch (Exception e) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.failed"));
            return;
        }

        Object host = findPatternHost(player, entry);
        if (host == null) {
            return;
        }
        InternalInventory inv = PatternInventoryHelper.patternInventoryOf(host);
        if (inv == null) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.noProvider"));
            return;
        }
        int target = entry.slot() >= 0 && entry.slot() < inv.size()
                && inv.getStackInSlot(entry.slot()).isEmpty() ? entry.slot() : -1;
        if (target < 0) {
            for (int i = 0; i < inv.size(); i++) {
                if (inv.getStackInSlot(i).isEmpty()) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.noEmptySlot"));
            return;
        }

        try {
            inv.setItemDirect(target, encoded);
            saveChanges(host);
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.done", encoded.getHoverName()));
        } catch (Exception e) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.encode.failed"));
        }
        sendToolList(player);
    }

    private static Object findPatternHost(ServerPlayer player, ToolEntry entry) {
        if (entry.pos() == null) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.noProvider"));
            return null;
        }
        ServerLevel level = player.server.getLevel(entry.dimension());
        BlockPos pos = entry.pos();
        if (level == null || !level.isLoaded(pos)) {
            HighlightManager.setNotice(player.getUUID(),
                    Component.translatable("patternchecker.action.noProvider"));
            return null;
        }
        // Block-based pattern containers (AE2 providers, AE2LT machines).
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null && PatternInventoryHelper.patternInventoryOf(be) != null) {
            return be;
        }
        // Cable part pattern providers.
        var nodeHost = GridHelper.getNodeHost(level, pos);
        if (nodeHost instanceof IPartHost partHost) {
            for (Direction direction : Direction.values()) {
                IPart part = partHost.getPart(direction);
                if (PatternInventoryHelper.patternInventoryOf(part) != null) {
                    return part;
                }
            }
        }
        HighlightManager.setNotice(player.getUUID(),
                Component.translatable("patternchecker.action.noProvider"));
        return null;
    }

    private static void saveChanges(Object host) {
        if (host instanceof PatternProviderLogicHost providerHost) {
            providerHost.getLogic().saveChanges();
        } else if (host instanceof BlockEntity be) {
            be.setChanged();
        }
    }
}
