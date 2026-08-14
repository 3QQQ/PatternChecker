package com.patternchecker.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> server: the edited inputs/outputs to encode and upload back into
 * the pattern's original slot.
 */
public record PatternEncodePayload(int index, List<PatternEditPayload.Slot> inputs,
                                  List<PatternEditPayload.Slot> outputs) implements CustomPacketPayload {

    public static final Type<PatternEncodePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("patternchecker", "pattern_encode"));

    public static final StreamCodec<ByteBuf, PatternEncodePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, PatternEncodePayload::index,
            ByteBufCodecs.collection(ArrayList::new, PatternEditPayload.SLOT_CODEC), PatternEncodePayload::inputs,
            ByteBufCodecs.collection(ArrayList::new, PatternEditPayload.SLOT_CODEC), PatternEncodePayload::outputs,
            PatternEncodePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
