package com.patternchecker.check;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Discovers ME networks by probing every loaded block for an exposed AE2 grid
 * node. This covers networks with controllers, cables, interfaces, pattern
 * providers, drives, etc. - not just networks that contain pattern providers.
 */
public final class GridDiscovery {

    private GridDiscovery() {
    }

    /** All distinct grids that have at least one loaded grid block. */
    public static List<IGrid> findAllGrids(ServerLevel level) {
        Set<IGrid> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<IGrid> grids = new ArrayList<>();
        for (var holder : level.getChunkSource().chunkMap.getChunks()) {
            LevelChunk chunk = holder.getTickingChunk();
            if (chunk == null) {
                continue;
            }
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                IGrid grid = gridAt(level, be.getBlockPos());
                if (grid != null && seen.add(grid)) {
                    grids.add(grid);
                }
            }
        }
        return grids;
    }

    /** Distinct grids sorted by distance to center, filtered by max distance. */
    public static List<Map.Entry<IGrid, Double>> findGridsNear(ServerLevel level, BlockPos center, double maxDistance) {
        Map<IGrid, Double> distances = new IdentityHashMap<>();
        double maxSq = maxDistance * maxDistance;
        for (var holder : level.getChunkSource().chunkMap.getChunks()) {
            LevelChunk chunk = holder.getTickingChunk();
            if (chunk == null) {
                continue;
            }
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                BlockPos pos = be.getBlockPos();
                double d = center.distSqr(pos);
                if (d > maxSq) {
                    continue;
                }
                IGrid grid = gridAt(level, pos);
                if (grid != null) {
                    distances.putIfAbsent(grid, d);
                }
            }
        }
        List<Map.Entry<IGrid, Double>> list = new ArrayList<>(distances.entrySet());
        list.sort(Comparator.comparingDouble(Map.Entry::getValue));
        return list;
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
}
