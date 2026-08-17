package com.patternchecker.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Client-to-server edited pattern contents. */
public record PatternEncodePayload(int index, List<PatternEditPayload.Slot> inputs,
                                   List<PatternEditPayload.Slot> outputs) {
    public static void encode(PatternEncodePayload payload, FriendlyByteBuf buffer) {
        buffer.writeInt(payload.index());
        buffer.writeCollection(payload.inputs(), PatternEditPayload.Slot::encode);
        buffer.writeCollection(payload.outputs(), PatternEditPayload.Slot::encode);
    }

    public static PatternEncodePayload decode(FriendlyByteBuf buffer) {
        int index = buffer.readInt();
        List<PatternEditPayload.Slot> inputs =
                buffer.readCollection(ArrayList::new, PatternEditPayload.Slot::decode);
        List<PatternEditPayload.Slot> outputs =
                buffer.readCollection(ArrayList::new, PatternEditPayload.Slot::decode);
        return new PatternEncodePayload(index, inputs, outputs);
    }
}
