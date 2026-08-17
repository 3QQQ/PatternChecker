package com.patternchecker.client.widget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Small compatibility button used by the checker UI. AE2 15.x does not expose
 * the AE2Button class used by the 1.21 branch, so the Forge port uses the
 * vanilla button implementation while preserving the same constructor shape.
 */
public class PatternButton extends Button {
    public PatternButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }
}
