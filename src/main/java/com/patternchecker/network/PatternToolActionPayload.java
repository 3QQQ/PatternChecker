package com.patternchecker.network;

import net.minecraft.network.FriendlyByteBuf;

/** Client-to-server tool action. */
public record PatternToolActionPayload(int action, int entryIndex) {
    public static final int ACTION_HIGHLIGHT = 0;
    public static final int ACTION_EXTRACT = 1;
    public static final int ACTION_UPLOAD = 2;
    public static final int ACTION_SCAN = 3;
    public static final int ACTION_UNBIND = 4;
    public static final int ACTION_TOGGLE_INPUT = 5;
    public static final int ACTION_SELECT = 6;
    public static final int ACTION_WRITE = 7;
    public static final int ACTION_SYNC = 8;
    public static final int ACTION_TOGGLE_DUPLICATE = 9;
    public static final int ACTION_EDIT = 10;

    public static void encode(PatternToolActionPayload payload, FriendlyByteBuf buffer) {
        buffer.writeInt(payload.action());
        buffer.writeInt(payload.entryIndex());
    }

    public static PatternToolActionPayload decode(FriendlyByteBuf buffer) {
        return new PatternToolActionPayload(buffer.readInt(), buffer.readInt());
    }
}
