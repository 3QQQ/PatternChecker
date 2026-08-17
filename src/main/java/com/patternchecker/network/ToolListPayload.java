package com.patternchecker.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Server-to-client tool panel state. */
public record ToolListPayload(List<Entry> entries, boolean available, boolean inputIssues,
                              boolean duplicateIssues, boolean bound, String boundLabel, Component notice) {
    public record Entry(int index, String name, String location, String summary, boolean hasProvider, boolean error,
                        String itemId, Component output, Component input, int category, int slot) {
    }

    public static void encode(ToolListPayload payload, FriendlyByteBuf buffer) {
        buffer.writeCollection(payload.entries(), (buf, entry) -> {
            buf.writeInt(entry.index());
            buf.writeUtf(entry.name());
            buf.writeUtf(entry.location());
            buf.writeUtf(entry.summary());
            buf.writeBoolean(entry.hasProvider());
            buf.writeBoolean(entry.error());
            buf.writeUtf(entry.itemId());
            buf.writeComponent(entry.output());
            buf.writeComponent(entry.input());
            buf.writeInt(entry.category());
            buf.writeInt(entry.slot());
        });
        buffer.writeBoolean(payload.available());
        buffer.writeBoolean(payload.inputIssues());
        buffer.writeBoolean(payload.duplicateIssues());
        buffer.writeBoolean(payload.bound());
        buffer.writeUtf(payload.boundLabel());
        buffer.writeComponent(payload.notice());
    }

    public static ToolListPayload decode(FriendlyByteBuf buffer) {
        List<Entry> entries = buffer.readCollection(ArrayList::new, buf -> new Entry(
                buf.readInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readInt(),
                buf.readInt()));
        return new ToolListPayload(entries, buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(), buffer.readComponent());
    }
}
