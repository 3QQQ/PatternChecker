package com.patternchecker.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: a tool action (highlight/extract/upload) triggered from
 * the tool side panel attached to the AE2 encoding terminal.
 */
public record PatternToolActionPayload(int action, int entryIndex) implements CustomPacketPayload {

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

    public static final Type<PatternToolActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("patternchecker", "tool_action"));

    public static final StreamCodec<ByteBuf, PatternToolActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, PatternToolActionPayload::action,
            ByteBufCodecs.INT, PatternToolActionPayload::entryIndex,
            PatternToolActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
