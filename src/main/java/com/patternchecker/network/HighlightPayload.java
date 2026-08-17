package com.patternchecker.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Server-to-client in-world highlight boxes. */
public record HighlightPayload(List<HighlightData> highlights) {
    public record HighlightData(String dimension, BlockPos pos, List<BlockPos> connections) {
    }

    public static void encode(HighlightPayload payload, FriendlyByteBuf buffer) {
        buffer.writeCollection(payload.highlights(), (buf, data) -> {
            buf.writeUtf(data.dimension());
            buf.writeBlockPos(data.pos());
            buf.writeCollection(data.connections(), FriendlyByteBuf::writeBlockPos);
        });
    }

    public static HighlightPayload decode(FriendlyByteBuf buffer) {
        List<HighlightData> highlights = buffer.readCollection(ArrayList::new, buf -> {
            String dimension = buf.readUtf();
            BlockPos pos = buf.readBlockPos();
            List<BlockPos> connections = buf.readCollection(ArrayList::new, FriendlyByteBuf::readBlockPos);
            return new HighlightData(dimension, pos, connections);
        });
        return new HighlightPayload(highlights);
    }
}
