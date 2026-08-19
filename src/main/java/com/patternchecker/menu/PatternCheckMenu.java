package com.patternchecker.menu;

import com.patternchecker.PatternCheckerMod;
import com.patternchecker.action.PatternActions;
import com.patternchecker.check.BoundNetwork;
import com.patternchecker.command.PatternCheckCommand;
import com.patternchecker.highlight.HighlightManager;
import com.patternchecker.highlight.HighlightManager.ToolEntry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Slot-less menu whose buttons map directly to tool actions. Button clicks are
 * sent by the client screen and handled server-side in {@link #clickMenuButton}.
 */
public class PatternCheckMenu extends AbstractContainerMenu {

    public static final int BUTTON_SCAN = 0;
    public static final int BUTTON_CLEAR_HIGHLIGHTS = 3;
    public static final int BUTTON_UNBIND = 4;
    public static final int BUTTON_TOGGLE_INPUT = 5;
    public static final int BUTTON_TOGGLE_DUPLICATE = 6;
    // List actions are encoded as action * 10000 + entryIndex so the ranges
    // can never collide with each other or with the small fixed button ids.
    public static final int BUTTON_HIGHLIGHT = 10000;
    public static final int BUTTON_EXTRACT = 20000;
    public static final int BUTTON_UPLOAD = 30000;
    public static final int BUTTON_EDIT = 40000;

    public PatternCheckMenu(int containerId, Inventory playerInventory) {
        super(PatternCheckerMod.PATTERN_CHECK_MENU.get(), containerId);
    }

    public static PatternCheckMenu fromNetwork(int containerId, Inventory playerInventory) {
        return new PatternCheckMenu(containerId, playerInventory);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (BoundNetwork.findTool(serverPlayer).isEmpty()) {
                return false;
            }
            if (id >= 10000) {
                int action = id / 10000;
                int index = id % 10000;
                ToolEntry entry = HighlightManager.getToolEntry(serverPlayer.getUUID(), index);
                if (entry != null) {
                    switch (action) {
                        case 1 -> PatternActions.highlight(serverPlayer, entry);
                        case 2 -> PatternActions.extract(serverPlayer, entry);
                        case 3 -> PatternActions.upload(serverPlayer, entry);
                        case 4 -> PatternActions.openEdit(serverPlayer, entry);
                        default -> {
                        }
                    }
                }
                return true;
            }
            switch (id) {
                case BUTTON_SCAN -> PatternCheckCommand.scanTargeted(serverPlayer, true);
                case BUTTON_CLEAR_HIGHLIGHTS -> HighlightManager.clear(serverPlayer);
                case BUTTON_UNBIND -> {
                    var tool = BoundNetwork.findTool(serverPlayer);
                    if (!tool.isEmpty()) {
                        BoundNetwork.clear(tool);
                    }
                    HighlightManager.setNotice(serverPlayer.getUUID(),
                            net.minecraft.network.chat.Component.translatable("patternchecker.bound.unbound"));
                }
                case BUTTON_TOGGLE_INPUT -> {
                    HighlightManager.toggleInputIssues(serverPlayer);
                    PatternCheckCommand.scanTargeted(serverPlayer, true);
                }
                case BUTTON_TOGGLE_DUPLICATE -> {
                    HighlightManager.toggleDuplicateIssues(serverPlayer);
                    PatternCheckCommand.scanTargeted(serverPlayer, true);
                }
                default -> {
                }
            }
            PatternActions.sendToolList(serverPlayer);
        }
        return true;
    }
}
