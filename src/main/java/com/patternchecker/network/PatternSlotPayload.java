package com.patternchecker.network;

import net.minecraft.network.FriendlyByteBuf;

/** Client-to-server marker for an editor grid slot. */
public record PatternSlotPayload(int slotIndex, String itemId, int count) {
    public static void encode(PatternSlotPayload payload, FriendlyByteBuf buffer) {
        buffer.writeInt(payload.slotIndex());
        buffer.writeUtf(payload.itemId());
        buffer.writeInt(payload.count());
    }

    public static PatternSlotPayload decode(FriendlyByteBuf buffer) {
        return new PatternSlotPayload(buffer.readInt(), buffer.readUtf(), buffer.readInt());
    }
}
