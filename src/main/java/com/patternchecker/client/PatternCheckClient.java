package com.patternchecker.client;

import com.patternchecker.PatternCheckerMod;
import com.patternchecker.menu.PatternEditMenu;
import com.patternchecker.client.screen.PatternCheckScreen;
import com.patternchecker.client.screen.PatternEditScreen;
import com.patternchecker.network.ToolListPayload;
import com.patternchecker.network.HighlightPayload;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = PatternCheckerMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PatternCheckClient {

    private static ToolListPayload lastToolList =
            new ToolListPayload(java.util.List.of(), false, false, true, false, "", Component.empty());
    private static HighlightState lastHighlights = new HighlightState(
            new HighlightPayload(java.util.List.of()), 0L);

    public record HighlightState(HighlightPayload payload, long receivedAt) {
    }

    private PatternCheckClient() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(PatternCheckerMod.PATTERN_CHECK_MENU.get(), PatternCheckScreen::new);
        event.register(PatternCheckerMod.PATTERN_EDIT_MENU.get(), PatternEditScreen::new);
    }

    public static void setToolList(ToolListPayload payload) {
        lastToolList = payload;
    }

    public static ToolListPayload getToolList() {
        return lastToolList;
    }

    public static void resetToolList() {
        lastToolList = new ToolListPayload(java.util.List.of(), false, false, true, false, "", Component.empty());
    }

    public static void setHighlights(HighlightPayload payload) {
        lastHighlights = new HighlightState(payload, System.currentTimeMillis());
    }

    public static HighlightState getHighlights() {
        return lastHighlights;
    }
}
