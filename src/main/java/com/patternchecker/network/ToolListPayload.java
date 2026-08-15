package com.patternchecker.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends the list of broken patterns (for the tool panel) from the server to
 * the client, together with the current auto-detection state.
 */
public record ToolListPayload(List<Entry> entries, boolean available, boolean inputIssues,
                              boolean duplicateIssues, boolean bound, String boundLabel, Component notice)
        implements CustomPacketPayload {

    public record Entry(int index, String name, String location, String summary, boolean hasProvider, boolean error,
                        String itemId, Component output, Component input, int category, int slot) {
    }

    public static final Type<ToolListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("patternchecker", "tool_list"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = new StreamCodec<>() {
        @Override
        public Entry decode(RegistryFriendlyByteBuf buffer) {
            int index = ByteBufCodecs.INT.decode(buffer);
            String name = ByteBufCodecs.STRING_UTF8.decode(buffer);
            String location = ByteBufCodecs.STRING_UTF8.decode(buffer);
            String summary = ByteBufCodecs.STRING_UTF8.decode(buffer);
            boolean hasProvider = ByteBufCodecs.BOOL.decode(buffer);
            boolean error = ByteBufCodecs.BOOL.decode(buffer);
            String itemId = ByteBufCodecs.STRING_UTF8.decode(buffer);
            Component output = ComponentSerialization.STREAM_CODEC.decode(buffer);
            Component input = ComponentSerialization.STREAM_CODEC.decode(buffer);
            int category = ByteBufCodecs.INT.decode(buffer);
            int slot = ByteBufCodecs.INT.decode(buffer);
            return new Entry(index, name, location, summary, hasProvider, error, itemId, output, input, category, slot);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, Entry entry) {
            ByteBufCodecs.INT.encode(buffer, entry.index());
            ByteBufCodecs.STRING_UTF8.encode(buffer, entry.name());
            ByteBufCodecs.STRING_UTF8.encode(buffer, entry.location());
            ByteBufCodecs.STRING_UTF8.encode(buffer, entry.summary());
            ByteBufCodecs.BOOL.encode(buffer, entry.hasProvider());
            ByteBufCodecs.BOOL.encode(buffer, entry.error());
            ByteBufCodecs.STRING_UTF8.encode(buffer, entry.itemId());
            ComponentSerialization.STREAM_CODEC.encode(buffer, entry.output());
            ComponentSerialization.STREAM_CODEC.encode(buffer, entry.input());
            ByteBufCodecs.INT.encode(buffer, entry.category());
            ByteBufCodecs.INT.encode(buffer, entry.slot());
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, ToolListPayload> STREAM_CODEC = new StreamCodec<>() {
        private final StreamCodec<RegistryFriendlyByteBuf, List<Entry>> entriesCodec =
                ByteBufCodecs.collection(ArrayList::new, ENTRY_CODEC);

        @Override
        public ToolListPayload decode(RegistryFriendlyByteBuf buffer) {
            List<Entry> entries = entriesCodec.decode(buffer);
            boolean available = ByteBufCodecs.BOOL.decode(buffer);
            boolean inputIssues = ByteBufCodecs.BOOL.decode(buffer);
            boolean duplicateIssues = ByteBufCodecs.BOOL.decode(buffer);
            boolean bound = ByteBufCodecs.BOOL.decode(buffer);
            String boundLabel = ByteBufCodecs.STRING_UTF8.decode(buffer);
            Component notice = ComponentSerialization.STREAM_CODEC.decode(buffer);
            return new ToolListPayload(
                    entries, available, inputIssues, duplicateIssues, bound, boundLabel, notice);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ToolListPayload payload) {
            entriesCodec.encode(buffer, payload.entries());
            ByteBufCodecs.BOOL.encode(buffer, payload.available());
            ByteBufCodecs.BOOL.encode(buffer, payload.inputIssues());
            ByteBufCodecs.BOOL.encode(buffer, payload.duplicateIssues());
            ByteBufCodecs.BOOL.encode(buffer, payload.bound());
            ByteBufCodecs.STRING_UTF8.encode(buffer, payload.boundLabel());
            ComponentSerialization.STREAM_CODEC.encode(buffer, payload.notice());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
