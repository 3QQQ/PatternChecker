package com.patternchecker.check;

import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * One detected problem with a pattern. The message is a translatable component
 * so it is localized on the client side.
 */
public record PatternIssue(Type type, Category category, Component message, BlockPos pos, String location) {

    public enum Type {
        ERROR,
        WARNING
    }

    public enum Category {
        /** The pattern itself is broken / cannot be used. */
        BROKEN,
        /** Machine or recipe related problems. */
        MACHINE,
        /** Inputs cannot be supplied by the network (often external supply). */
        INPUT,
        /** The same encoded operation exists more than once on the network. */
        DUPLICATE
    }

    public MutableComponent toChatLine(int index) {
        MutableComponent prefix = Component.translatable(
                type == Type.ERROR ? "patternchecker.chat.error" : "patternchecker.chat.warning");
        MutableComponent line = prefix.append(Component.literal(" ")).append(message);
        if (pos != null) {
            line.append(Component.literal(" "))
                    .append(Component.translatable("patternchecker.chat.highlight")
                            .withStyle(Style.EMPTY
                                    .withColor(ChatFormatting.GREEN)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                            "/patterncheck highlight " + index))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.translatable("patternchecker.chat.highlight.hover")))));
        }
        return line;
    }
}
