package com.patternchecker.menu;

import com.patternchecker.PatternCheckerMod;
import com.patternchecker.action.PatternActions;
import com.patternchecker.highlight.HighlightManager.ToolEntry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import appeng.core.definitions.AEItems;

/**
 * Container menu for the pattern editor. The input/output grid is a
 * {@link SimpleContainer} with real slots, so all item interactions (click,
 * shift-click, right-click, drag) use Minecraft's standard container logic.
 */
public class PatternEditMenu extends AbstractContainerMenu {

    public static final int BUTTON_ENCODE_UPLOAD = 1;
    public static final int BUTTON_CANCEL = 2;
    // Amount actions: id = BASE + slotIndex * 1000 + amount (1..999)
    public static final int BUTTON_SET_AMOUNT = 20000;
    // Increment/decrement: id = BASE + slotIndex
    public static final int BUTTON_DECREMENT = 30000;
    public static final int BUTTON_INCREMENT = 40000;
    // Tool actions: id = BASE + entryIndex (highlight / extract / upload)
    public static final int BUTTON_TOOL_HIGHLIGHT = 50000;
    public static final int BUTTON_TOOL_EXTRACT = 60000;
    public static final int BUTTON_TOOL_UPLOAD = 70000;

    public static final int INPUT_SLOTS = 81;
    public static final int OUTPUT_SLOTS = 27;
    public static final int GRID_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS;
    public static final int BLANK_SLOT = GRID_SLOTS;

    private static final int PLAYER_INV_START = GRID_SLOTS + 1;
    private static final int PLAYER_MAIN_START = PLAYER_INV_START + 27;
    private static final int PLAYER_HOTBAR_START = PLAYER_MAIN_START + 27;
    private static final int PLAYER_INV_END = PLAYER_HOTBAR_START + 9;
    private static final int GRID_CONTAINER_SIZE = GRID_SLOTS + 1; // + blank pattern slot

    private final SimpleContainer grid;
    private ToolEntry entry; // server-side only
    private boolean completed;

    public PatternEditMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null, new SimpleContainer(GRID_CONTAINER_SIZE));
    }

    public PatternEditMenu(int containerId, Inventory playerInventory, ToolEntry entry, SimpleContainer grid) {
        super(PatternCheckerMod.PATTERN_EDIT_MENU.get(), containerId);
        this.grid = grid;
        this.entry = entry;
        if (grid.getContainerSize() < GRID_CONTAINER_SIZE) {
            throw new IllegalArgumentException("Pattern editor grid too small");
        }
        grid.startOpen(playerInventory.player);

        // Input grid (3 columns x 27 rows), slots 0-80. Positioned like AE2's
        // processing panel (left 24, bottom 158), shifted right of the tool
        // panel.
        for (int row = 0; row < 27; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(grid, row * 3 + col, 152 + col * 20, 30 + row * 20));
            }
        }
        // Output column (1 x 27), slots 81-107.
        for (int row = 0; row < 27; row++) {
            addSlot(new Slot(grid, INPUT_SLOTS + row, 237, 30 + row * 20));
        }

        // Blank pattern slot, slot 108. A blank pattern must be present here
        // before the encoded pattern can be uploaded back into the provider.
        addSlot(new Slot(grid, BLANK_SLOT, 152, 116));

        // Player inventory (main 3 rows + hotbar), 36 slots.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 145 + col * 19, 134 + row * 19));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 145 + col * 19, 191));
        }
    }

    public static PatternEditMenu fromNetwork(int containerId, Inventory playerInventory) {
        return new PatternEditMenu(containerId, playerInventory);
    }

    public Container getGrid() {
        return grid;
    }

    public ToolEntry getEntry() {
        return entry;
    }

    public void markCompleted() {
        completed = true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // The grid is a marker-only editor: shift-clicking must never move
        // real items between the grid and the player inventory.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide) {
            return true;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        if (id == BUTTON_ENCODE_UPLOAD) {
            PatternActions.encodeFromEditMenu(serverPlayer, this);
            return true;
        }
        if (id == BUTTON_CANCEL) {
            serverPlayer.closeContainer();
            return true;
        }
        if (id >= BUTTON_TOOL_HIGHLIGHT && id < BUTTON_TOOL_HIGHLIGHT + 10000) {
            ToolEntry toolEntry = com.patternchecker.highlight.HighlightManager
                    .getToolEntry(player.getUUID(), id - BUTTON_TOOL_HIGHLIGHT);
            if (toolEntry != null) {
                PatternActions.highlight(serverPlayer, toolEntry);
            }
            return true;
        }
        if (id >= BUTTON_TOOL_EXTRACT && id < BUTTON_TOOL_EXTRACT + 10000) {
            ToolEntry toolEntry = com.patternchecker.highlight.HighlightManager
                    .getToolEntry(player.getUUID(), id - BUTTON_TOOL_EXTRACT);
            if (toolEntry != null) {
                PatternActions.extract(serverPlayer, toolEntry);
            }
            return true;
        }
        if (id >= BUTTON_TOOL_UPLOAD && id < BUTTON_TOOL_UPLOAD + 10000) {
            ToolEntry toolEntry = com.patternchecker.highlight.HighlightManager
                    .getToolEntry(player.getUUID(), id - BUTTON_TOOL_UPLOAD);
            if (toolEntry != null) {
                PatternActions.upload(serverPlayer, toolEntry);
            }
            return true;
        }
        if (id >= BUTTON_DECREMENT && id < BUTTON_DECREMENT + GRID_SLOTS) {
            changeAmount(id - BUTTON_DECREMENT, -1);
            return true;
        }
        if (id >= BUTTON_INCREMENT && id < BUTTON_INCREMENT + GRID_SLOTS) {
            changeAmount(id - BUTTON_INCREMENT, 1);
            return true;
        }
        if (id >= BUTTON_SET_AMOUNT) {
            int slotIndex = (id - BUTTON_SET_AMOUNT) / 1000;
            int amount = (id - BUTTON_SET_AMOUNT) % 1000;
            if (slotIndex >= 0 && slotIndex < GRID_SLOTS) {
                if (amount == 0) {
                    grid.setItem(slotIndex, ItemStack.EMPTY);
                } else if (amount >= 1 && amount <= 999) {
                    ItemStack stack = grid.getItem(slotIndex);
                    if (!stack.isEmpty()) {
                    stack.setCount(amount);
                    grid.setItem(slotIndex, stack);
                    }
                }
            }
            return true;
        }
        return true;
    }

    private void changeAmount(int slotIndex, int delta) {
        if (slotIndex < 0 || slotIndex >= GRID_SLOTS) {
            return;
        }
        ItemStack stack = grid.getItem(slotIndex);
        if (stack.isEmpty()) {
            return;
        }
        int next = Math.max(1, Math.min(999, stack.getCount() + delta));
        stack.setCount(next);
        grid.setItem(slotIndex, stack);
    }

    /** True when a blank pattern sits in the blank pattern slot. */
    public boolean hasBlankPattern() {
        ItemStack stack = grid.getItem(BLANK_SLOT);
        return !stack.isEmpty() && AEItems.BLANK_PATTERN.isSameAs(stack);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!completed) {
            ItemStack blank = grid.getItem(BLANK_SLOT);
            if (!blank.isEmpty()) {
                grid.setItem(BLANK_SLOT, ItemStack.EMPTY);
                if (!player.getInventory().add(blank)) {
                    player.drop(blank, false);
                }
            }
        }
        grid.stopOpen(player);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
