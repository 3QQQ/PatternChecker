package com.patternchecker.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: mark a pattern editor grid slot with an item. This is a
 * marker only - nothing is consumed from or returned to the player inventory.
 */
public record PatternSlotPayload(int slotIndex, String itemId, int count) implements CustomPacketPayload {

    public static final Type<PatternSlotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("patternchecker", "pattern_slot"));

    public static final StreamCodec<ByteBuf, PatternSlotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, PatternSlotPayload::slotIndex,
            ByteBufCodecs.STRING_UTF8, PatternSlotPayload::itemId,
            ByteBufCodecs.INT, PatternSlotPayload::count,
            PatternSlotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
