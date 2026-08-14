package com.patternchecker.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** AE2-style button: light grey gradient, dark blue-grey border. */
public class Ae2Button extends Button {

    public Ae2Button(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int topColor;
        int bottomColor;
        int textColor;
        if (!this.active) {
            topColor = 0xFFC5C7CE;
            bottomColor = 0xFFBABCC4;
            textColor = 0xFF83858F;
        } else if (this.isHovered()) {
            topColor = 0xFFE8EAEF;
            bottomColor = 0xFFCBCED7;
            textColor = 0xFF1F1F2A;
        } else {
            topColor = 0xFFD8DAE0;
            bottomColor = 0xFFC0C3CB;
            textColor = 0xFF2E2D3B;
        }
        gui.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(),
                0xFF413F54);
        gui.fill(this.getX() + 1, this.getY() + 1,
                this.getX() + this.getWidth() - 1, this.getY() + this.getHeight() / 2, topColor);
        gui.fill(this.getX() + 1, this.getY() + this.getHeight() / 2,
                this.getX() + this.getWidth() - 1, this.getY() + this.getHeight() - 1, bottomColor);
        gui.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                this.getX() + this.getWidth() / 2, this.getY() + (this.getHeight() - 8) / 2, textColor);
    }
}
