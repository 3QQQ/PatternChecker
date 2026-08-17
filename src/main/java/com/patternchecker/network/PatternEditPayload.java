package com.patternchecker.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Server-to-client raw inputs/outputs of a processing pattern. */
public record PatternEditPayload(int index, String dimension, BlockPos pos, int slot,
                                 List<Slot> inputs, List<Slot> outputs) {
    public record Slot(String itemId, int count, boolean filled) {
        public static final Slot EMPTY = new Slot("", 0, false);

        public boolean isEmpty() {
            return !filled;
        }

        public static void encode(FriendlyByteBuf buffer, Slot slot) {
            buffer.writeBoolean(slot.filled());
            buffer.writeUtf(slot.itemId());
            buffer.writeInt(slot.count());
        }

        public static Slot decode(FriendlyByteBuf buffer) {
            return new Slot(buffer.readUtf(), buffer.readInt(), buffer.readBoolean());
        }
    }

    public static void encode(PatternEditPayload payload, FriendlyByteBuf buffer) {
        buffer.writeInt(payload.index());
        buffer.writeUtf(payload.dimension());
        buffer.writeBlockPos(payload.pos());
        buffer.writeInt(payload.slot());
        buffer.writeCollection(payload.inputs(), (buf, slot) -> Slot.encode(buf, slot));
        buffer.writeCollection(payload.outputs(), (buf, slot) -> Slot.encode(buf, slot));
    }

    public static PatternEditPayload decode(FriendlyByteBuf buffer) {
        int index = buffer.readInt();
        String dimension = buffer.readUtf();
        BlockPos pos = buffer.readBlockPos();
        int slot = buffer.readInt();
        List<Slot> inputs = buffer.readCollection(ArrayList::new, PatternEditPayload.Slot::decode);
        List<Slot> outputs = buffer.readCollection(ArrayList::new, PatternEditPayload.Slot::decode);
        return new PatternEditPayload(index, dimension, pos, slot, inputs, outputs);
    }
}
