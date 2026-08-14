package com.patternchecker.item;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import com.patternchecker.check.BoundNetwork;
import com.patternchecker.highlight.HighlightManager;
import com.patternchecker.action.PatternActions;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * An in-game tool that opens the pattern checker panel. All scanning and
 * toggles are handled through the menu, so no chat commands are required.
 */
public class PatternCheckerToolItem extends Item {

    public PatternCheckerToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            PatternActions.openToolPanel(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /**
     * Right-clicking a block that belongs to an ME network binds the tool to
     * that network (and prevents the block's own GUI from opening).
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || !(context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        var pos = context.getClickedPos();
        for (Direction direction : Direction.values()) {
            IGridNode node = GridHelper.getExposedNode(level, pos, direction);
            if (node != null && node.getGrid() != null) {
                BoundNetwork.bind(context.getItemInHand(), level.dimension(), pos);
                HighlightManager.setNotice(player.getUUID(),
                        Component.translatable("patternchecker.bound.done",
                                pos.toShortString(), level.dimension().location().toString()));
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.patternchecker.pattern_checker_tool.tooltip"));
    }
}
