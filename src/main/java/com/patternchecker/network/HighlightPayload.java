package com.patternchecker.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends the player's active in-world highlight boxes (pattern provider + its
 * wireless connection targets) to the client for see-through rendering.
 */
public record HighlightPayload(List<HighlightData> highlights) implements CustomPacketPayload {

    public record HighlightData(String dimension, BlockPos pos, List<BlockPos> connections) {
    }

    public static final Type<HighlightPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("patternchecker", "highlights"));

    private static final StreamCodec<ByteBuf, HighlightData> DATA_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, HighlightData::dimension,
            BlockPos.STREAM_CODEC, HighlightData::pos,
            ByteBufCodecs.collection(ArrayList::new, BlockPos.STREAM_CODEC), HighlightData::connections,
            HighlightData::new);

    public static final StreamCodec<ByteBuf, HighlightPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, DATA_CODEC), HighlightPayload::highlights,
            HighlightPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
