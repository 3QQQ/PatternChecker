package com.patternchecker.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

    /**
     * Draw without vanilla's nine-sliced button sprite. Several 1.20.1 UI
     * resource packs provide invalid/zero slice metadata, which makes
     * GuiGraphics.blitRepeating divide by zero when the movable checker is
     * expanded near a screen edge.
     */
    @Override
    protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        int topColor;
        int bottomColor;
        int textColor;
        if (!active) {
            topColor = 0xFFC5C7CE;
            bottomColor = 0xFFBABCC4;
            textColor = 0xFF83858F;
        } else if (isHovered()) {
            topColor = 0xFFE8EAEF;
            bottomColor = 0xFFCBCED7;
            textColor = 0xFF1F1F2A;
        } else {
            topColor = 0xFFD8DAE0;
            bottomColor = 0xFFC0C3CB;
            textColor = 0xFF2E2D3B;
        }

        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        int middle = y + Math.max(1, height / 2);
        gui.fill(x, y, x + width, y + height, 0xFF413F54);
        if (width > 2 && height > 2) {
            gui.fill(x + 1, y + 1, x + width - 1, middle, topColor);
            gui.fill(x + 1, middle, x + width - 1, y + height - 1, bottomColor);
        }
        gui.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                x + width / 2, y + (height - 8) / 2, textColor);
    }
}
