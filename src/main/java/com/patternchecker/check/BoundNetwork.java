package com.patternchecker.check;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import com.patternchecker.item.PatternCheckerPresence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Binds the pattern checker tool to one specific ME network by remembering the
 * position of a block on that network. While bound, all scans only check this
 * network.
 */
public final class BoundNetwork {

    private static final String TAG_DIM = "bound_dim";
    private static final String TAG_X = "bound_x";
    private static final String TAG_Y = "bound_y";
    private static final String TAG_Z = "bound_z";

    private BoundNetwork() {
    }

    public static ItemStack findTool(ServerPlayer player) {
        return PatternCheckerPresence.findChecker(player);
    }

    public static void bind(ItemStack tool, ResourceKey<Level> dimension, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIM, dimension.location().toString());
        tag.putInt(TAG_X, pos.getX());
        tag.putInt(TAG_Y, pos.getY());
        tag.putInt(TAG_Z, pos.getZ());
        CompoundTag target = tool.getOrCreateTag();
        target.putString(TAG_DIM, tag.getString(TAG_DIM));
        target.putInt(TAG_X, tag.getInt(TAG_X));
        target.putInt(TAG_Y, tag.getInt(TAG_Y));
        target.putInt(TAG_Z, tag.getInt(TAG_Z));
    }

    public static void clear(ItemStack tool) {
        if (tool.hasTag()) {
            tool.getTag().remove(TAG_DIM);
            tool.getTag().remove(TAG_X);
            tool.getTag().remove(TAG_Y);
            tool.getTag().remove(TAG_Z);
        }
    }

    public static boolean isBound(ItemStack tool) {
        return tool.hasTag() && tool.getTag().contains(TAG_DIM);
    }

    public static String describe(ItemStack tool) {
        if (!isBound(tool)) {
            return "";
        }
        CompoundTag tag = tool.getTag();
        int x = tag.getInt(TAG_X);
        int y = tag.getInt(TAG_Y);
        int z = tag.getInt(TAG_Z);
        String dim = tag.getString(TAG_DIM);
        return x + ", " + y + ", " + z + " (" + dim + ")";
    }

    /**
     * Resolves the bound grid, or null if the tool is unbound/unavailable.
     */
    public static IGrid resolve(ServerPlayer player, ItemStack tool) {
        if (tool.isEmpty() || !isBound(tool)) {
            return null;
        }
        CompoundTag tag = tool.getTag();
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse(tag.getString(TAG_DIM)));
        BlockPos pos = new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
        ServerLevel level = player.server.getLevel(dimension);
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            IGridNode node = GridHelper.getExposedNode(level, pos, direction);
            if (node != null && node.getGrid() != null) {
                return node.getGrid();
            }
        }
        return null;
    }
}
