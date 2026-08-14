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
 * Server -> client: the raw inputs/outputs of a processing pattern, so the
 * tool panel can offer editing and re-encoding.
 */
public record PatternEditPayload(int index, String dimension, BlockPos pos, int slot,
                                 List<Slot> inputs, List<Slot> outputs) implements CustomPacketPayload {

    /**
     * One cell of the encoding grid. Empty cells (holes) are preserved so the
     * editor behaves like the AE2 encoding terminal.
     */
    public record Slot(String itemId, int count, boolean filled) {
        public static final Slot EMPTY = new Slot("", 0, false);

        public boolean isEmpty() {
            return !filled;
        }
    }

    public static final Type<PatternEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("patternchecker", "pattern_edit"));

    public static final StreamCodec<ByteBuf, Slot> SLOT_CODEC = new StreamCodec<>() {
        @Override
        public Slot decode(ByteBuf buffer) {
            boolean filled = ByteBufCodecs.BOOL.decode(buffer);
            String itemId = ByteBufCodecs.STRING_UTF8.decode(buffer);
            int count = ByteBufCodecs.INT.decode(buffer);
            return new Slot(itemId, count, filled);
        }

        @Override
        public void encode(ByteBuf buffer, Slot slot) {
            ByteBufCodecs.BOOL.encode(buffer, slot.filled());
            ByteBufCodecs.STRING_UTF8.encode(buffer, slot.itemId());
            ByteBufCodecs.INT.encode(buffer, slot.count());
        }
    };

    public static final StreamCodec<ByteBuf, PatternEditPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, PatternEditPayload::index,
            ByteBufCodecs.STRING_UTF8, PatternEditPayload::dimension,
            BlockPos.STREAM_CODEC, PatternEditPayload::pos,
            ByteBufCodecs.INT, PatternEditPayload::slot,
            ByteBufCodecs.collection(ArrayList::new, SLOT_CODEC), PatternEditPayload::inputs,
            ByteBufCodecs.collection(ArrayList::new, SLOT_CODEC), PatternEditPayload::outputs,
            PatternEditPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
