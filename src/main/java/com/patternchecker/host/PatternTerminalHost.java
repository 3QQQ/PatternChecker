package com.patternchecker.host;

import appeng.api.config.Actionable;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.MEStorage;
import appeng.api.util.IConfigManager;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.ISubMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.util.ConfigManager;
import com.patternchecker.PatternCheckerMod;
import com.patternchecker.highlight.HighlightManager.ToolEntry;
import com.patternchecker.item.PatternCheckerToolItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Makes the pattern checker tool act as an AE2 pattern encoding terminal host.
 * The original broken pattern is loaded into the terminal, and when the player
 * encodes a replacement the new pattern is written back into the provider slot
 * that held the original.
 */
public class PatternTerminalHost extends ItemMenuHost<PatternCheckerToolItem>
        implements IPatternTerminalMenuHost, IPatternTerminalLogicHost {

    private final PatternEncodingLogic logic;
    private ToolEntry entry;
    private ItemStack originalPattern = ItemStack.EMPTY;
    private boolean wroteBack;

    public PatternTerminalHost(PatternCheckerToolItem item, Player player, ItemMenuHostLocator locator) {
        super(item, player, locator);
        this.logic = new PatternEncodingLogic(this);
    }

    /**
     * Server-side: load the pattern to edit into the terminal grid. May be
     * called repeatedly; every call replaces the pattern currently loaded.
     */
    public void loadPattern(ToolEntry entry, ItemStack pattern) {
        this.entry = entry;
        this.originalPattern = pattern.copy();
        this.wroteBack = false;
        if (!pattern.isEmpty()) {
            // Load the decoded grid without leaving an extractable copied item
            // in the encoded-pattern slot.
            try {
                logic.getEncodedPatternInv().setItemDirect(0, pattern.copy());
            } finally {
                logic.getEncodedPatternInv().setItemDirect(0, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public PatternEncodingLogic getLogic() {
        return logic;
    }

    @Override
    public Level getLevel() {
        return getPlayer().level();
    }

    @Override
    public void markForSave() {
        // The player explicitly triggers the upload via the button; nothing to do here.
    }

    /**
     * Called when the player presses the "upload" button in the terminal.
     * Encodes the current grid contents directly (same as AE2's own encode
     * button) and writes the result back into the original provider slot.
     * This edits the existing provider pattern in place and never consumes
     * items from the terminal's physical pattern slots.
     */
    public void uploadEncoded() {
        if (wroteBack || getPlayer().level().isClientSide || entry == null) {
            return;
        }
        if (logic.getMode() != EncodingMode.PROCESSING) {
            if (getPlayer() instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("patternchecker.encode.unsupported"), true);
            }
            return;
        }
        var inputInv = logic.getEncodedInputInv();
        var outputInv = logic.getEncodedOutputInv();
        List<GenericStack> inputs = new java.util.ArrayList<>();
        boolean hasInput = false;
        for (int i = 0; i < inputInv.size(); i++) {
            GenericStack stack = inputInv.getStack(i);
            if (stack == null) {
                inputs.add(null);
                continue;
            }
            inputs.add(stack);
            hasInput = true;
        }
        List<GenericStack> outputs = new java.util.ArrayList<>();
        for (int i = 0; i < outputInv.size(); i++) {
            outputs.add(outputInv.getStack(i));
        }
        if (!hasInput || outputs.isEmpty() || outputs.get(0) == null) {
            return;
        }
        ItemStack encoded;
        try {
            encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
            Component customName = originalPattern.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                encoded.set(DataComponents.CUSTOM_NAME, customName);
            }
        } catch (Exception e) {
            PatternCheckerMod.LOGGER.error("Failed to encode pattern for upload", e);
            return;
        }
        wroteBack = true;
        try {
            if (!com.patternchecker.action.PatternActions.uploadEncodedPattern(
                    getPlayer(), entry, encoded, originalPattern)) {
                wroteBack = false;
                return;
            }
            inputInv.clear();
            outputInv.clear();
        } catch (Exception e) {
            PatternCheckerMod.LOGGER.error("Failed to write back encoded pattern", e);
            wroteBack = false;
        }
    }

    // ---- ITerminalHost ----

    @Override
    public MEStorage getInventory() {
        // Do NOT expose the bound network's storage. The ME terminal performs
        // a full inventory sync every tick; with many item types in storage
        // that freezes the game. Items are still marked by clicking them in the
        // player inventory, so the network panel is not needed for editing.
        return EMPTY_STORAGE;
    }

    @Override
    public ILinkStatus getLinkStatus() {
        // Disconnected keeps MEStorageMenu from syncing any inventory.
        return ILinkStatus.ofDisconnected();
    }

    @Override
    public IConfigManager getConfigManager() {
        return new ConfigManager(() -> {
        });
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        // no parent menu
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return getItemStack();
    }

    private static final MEStorage EMPTY_STORAGE = new MEStorage() {
        @Override
        public Component getDescription() {
            return Component.empty();
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
        }
    };
}
