package com.patternchecker.client.screen;

import com.patternchecker.client.PatternCheckClient;
import com.patternchecker.menu.PatternEditMenu;
import com.patternchecker.network.PatternSlotPayload;
import com.patternchecker.network.ToolListPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Integrated editor: right side replicates the AE2 pattern encoding terminal
 * layout (mode tabs, processing grid, blank pattern slot, player inventory);
 * left side is the tool's broken-pattern list with highlight/extract/upload.
 */
public class PatternEditScreen extends AbstractContainerScreen<PatternEditMenu> {

    private static final int TOOL_WIDTH = 118;
    private static final int PANEL_WIDTH = 195 + TOOL_WIDTH;
    private static final int PANEL_HEIGHT = 246;

    private static final int COLOR_BORDER = 0xFF413F54;
    private static final int COLOR_BG = 0xFFCBCDD4;
    private static final int COLOR_INNER = 0xFFDDE0E6;
    private static final int COLOR_SLOT_BORDER = 0xFF373737;
    private static final int COLOR_SLOT_INNER = 0xFF8B8B8B;
    private static final int COLOR_TEXT = 0xFF413F54;
    private static final int COLOR_TEXT_DIM = 0xFF6E7080;
    private static final int COLOR_ACCENT = 0xFF2F5FAF;
    private static final int COLOR_SELECT = 0xFFBBD4FF;

    private static final int LIST_TOP = 38;
    private static final int LIST_BOTTOM = 188;
    private static final int ROW_HEIGHT = 22;

    // Encoding area offsets (relative to panel, matching AE2 terminal layout).
    private static final int ENC = TOOL_WIDTH; // encoding area starts after tool panel
    private static final int INPUT_X = ENC + 24;
    private static final int OUTPUT_X = ENC + 109;
    private static final int GRID_Y = 30;

    private EditBox amountField;
    private int amountDraft = -1;
    private int selectedSlot = -1;
    private int inputScroll;
    private int outputScroll;

    private int listScroll;
    private int toolSelected = -1;

    // AE2-like mode tabs: 0 craft, 1 processing, 2 smithing, 3 stonecutting.
    private int mode = 1;

    public PatternEditScreen(PatternEditMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int left = this.leftPos;
        int top = this.topPos;

        amountField = new EditBox(this.font, left + ENC + 38, top + 96, 46, 15,
                Component.translatable("patternchecker.encode.amount"));
        amountField.setMaxLength(3);
        amountField.setFilter(s -> s.chars().allMatch(Character::isDigit));
        amountField.setResponder(this::onAmountTyped);
        addRenderableWidget(amountField);

        addRenderableWidget(new Ae2Button(left + ENC + 88, top + 95, 18, 16, Component.literal("-"),
                b -> sendButton(PatternEditMenu.BUTTON_DECREMENT + selectedSlot)));
        addRenderableWidget(new Ae2Button(left + ENC + 108, top + 95, 18, 16, Component.literal("+"),
                b -> sendButton(PatternEditMenu.BUTTON_INCREMENT + selectedSlot)));
        addRenderableWidget(new Ae2Button(left + ENC + 130, top + 95, 36, 16,
                Component.translatable("patternchecker.encode.clear"),
                b -> clearSelected()));

        addRenderableWidget(new Ae2Button(left + ENC + 14, top + 212, 96, 18,
                Component.translatable("patternchecker.encode.upload"),
                b -> sendButton(PatternEditMenu.BUTTON_ENCODE_UPLOAD)));
        addRenderableWidget(new Ae2Button(left + ENC + 114, top + 212, 48, 18,
                Component.translatable("patternchecker.encode.cancel"),
                b -> sendButton(PatternEditMenu.BUTTON_CANCEL)));

        // Mode tabs (AE2-like, right edge).
        String[] modes = {"patternchecker.encode.mode.craft", "patternchecker.encode.mode.process",
                "patternchecker.encode.mode.smith", "patternchecker.encode.mode.cut"};
        for (int i = 0; i < modes.length; i++) {
            final int idx = i;
            addRenderableWidget(new Ae2Button(left + ENC + 173, top + 26 + i * 21, 18, 18,
                    Component.translatable(modes[i]),
                    b -> setMode(idx)));
        }

        // Tool panel buttons.
        addRenderableWidget(new Ae2Button(left + 4, top + 194, 36, 16,
                Component.translatable("patternchecker.menu.highlight"),
                b -> sendToolButton(PatternEditMenu.BUTTON_TOOL_HIGHLIGHT)));
        addRenderableWidget(new Ae2Button(left + 41, top + 194, 36, 16,
                Component.translatable("patternchecker.menu.extract"),
                b -> sendToolButton(PatternEditMenu.BUTTON_TOOL_EXTRACT)));
        addRenderableWidget(new Ae2Button(left + 78, top + 194, 36, 16,
                Component.translatable("patternchecker.menu.upload"),
                b -> sendToolButton(PatternEditMenu.BUTTON_TOOL_UPLOAD)));
    }

    private void setMode(int idx) {
        mode = idx;
        repositionSlots();
    }

    private void sendToolButton(int base) {
        if (toolSelected >= 0) {
            sendButton(base + toolSelected);
        }
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.send(
                    new ServerboundContainerButtonClickPacket(this.menu.containerId, id));
        }
    }

    /** Moves grid slots outside the visible 3-row window off-screen. */
    private void repositionSlots() {
        int top = GRID_Y;
        for (int i = 0; i < PatternEditMenu.INPUT_SLOTS; i++) {
            int row = i / 3;
            int col = i % 3;
            int visibleRow = row - inputScroll;
            Slot slot = this.menu.getSlot(i);
            slot.x = INPUT_X + col * 20;
            slot.y = (visibleRow >= 0 && visibleRow < 3) ? top + visibleRow * 20 : -1000;
        }
        for (int i = 0; i < PatternEditMenu.OUTPUT_SLOTS; i++) {
            int visibleRow = i - outputScroll;
            Slot slot = this.menu.getSlot(PatternEditMenu.INPUT_SLOTS + i);
            slot.x = OUTPUT_X;
            slot.y = (visibleRow >= 0 && visibleRow < 3) ? top + visibleRow * 20 : -1000;
        }
        Slot blankSlot = this.menu.getSlot(PatternEditMenu.BLANK_SLOT);
        blankSlot.x = ENC + 10;
        blankSlot.y = 116;
    }

    private int maxInputScroll() {
        return Math.max(0, 27 - 3);
    }

    private int maxOutputScroll() {
        return Math.max(0, 27 - 3);
    }

    private void onAmountTyped(String text) {
        if (text.isEmpty()) {
            amountDraft = -1;
            return;
        }
        try {
            amountDraft = Math.max(1, Math.min(999, Integer.parseInt(text)));
        } catch (NumberFormatException e) {
            amountDraft = -1;
        }
    }

    private void applyAmount() {
        if (amountDraft > 0 && selectedSlot >= 0 && selectedSlot < PatternEditMenu.GRID_SLOTS) {
            sendButton(PatternEditMenu.BUTTON_SET_AMOUNT + selectedSlot * 1000 + amountDraft);
        }
    }

    private void clearSelected() {
        if (selectedSlot >= 0 && selectedSlot < PatternEditMenu.GRID_SLOTS) {
            sendButton(PatternEditMenu.BUTTON_SET_AMOUNT + selectedSlot * 1000);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (amountField.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            onAmountTyped(amountField.getValue());
            applyAmount();
            amountField.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= this.leftPos + 2 && mouseX < this.leftPos + TOOL_WIDTH
                && mouseY >= this.topPos + LIST_TOP && mouseY < this.topPos + LIST_BOTTOM) {
            int row = (int) ((mouseY - (this.topPos + LIST_TOP)) / ROW_HEIGHT) + listScroll;
            List<ToolListPayload.Entry> entries = toolEntries();
            if (row >= 0 && row < entries.size()) {
                toolSelected = entries.get(row).index();
            } else {
                toolSelected = -1;
            }
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        syncAmountField();
        return handled;
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ClickType clickType) {
        if (slot == null) {
            return;
        }
        if (slotId == PatternEditMenu.BLANK_SLOT) {
            super.slotClicked(slot, slotId, button, clickType);
            return;
        }
        if (slotId < PatternEditMenu.GRID_SLOTS) {
            selectedSlot = slotId;
            if (button == 1) {
                sendButton(PatternEditMenu.BUTTON_SET_AMOUNT + slotId * 1000);
            }
            syncAmountField();
            return;
        }
        int invIndex = slotId - (PatternEditMenu.GRID_SLOTS + 1);
        if (invIndex >= 0 && invIndex < 36 && this.minecraft != null && this.minecraft.player != null) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(invIndex);
            if (!stack.isEmpty()) {
                int target = (selectedSlot >= 0 && selectedSlot < PatternEditMenu.GRID_SLOTS)
                        ? selectedSlot
                        : firstEmptyInput();
                if (target >= 0) {
                    int count = (clickType == ClickType.QUICK_MOVE || hasShiftDown())
                            ? Math.min(Math.max(1, stack.getMaxStackSize()), 999)
                            : 1;
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    PacketDistributor.sendToServer(new PatternSlotPayload(target, itemId, count));
                    selectedSlot = target;
                    syncAmountField();
                }
            }
            return;
        }
        super.slotClicked(slot, slotId, button, clickType);
    }

    private int firstEmptyInput() {
        for (int i = 0; i < PatternEditMenu.INPUT_SLOTS; i++) {
            if (this.menu.getGrid().getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private List<ToolListPayload.Entry> toolEntries() {
        List<ToolListPayload.Entry> all = new ArrayList<>(PatternCheckClient.getToolList().entries());
        all.sort(Comparator.comparingInt(ToolListPayload.Entry::category)
                .thenComparingInt(ToolListPayload.Entry::index));
        return all;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int dir = (int) -verticalAmount;
        int left = this.leftPos;
        int top = this.topPos;
        if (mouseX >= left + 2 && mouseX < left + TOOL_WIDTH
                && mouseY >= top + LIST_TOP && mouseY < top + LIST_BOTTOM) {
            int maxOffset = Math.max(0, toolEntries().size() - 6);
            listScroll = Math.max(0, Math.min(maxOffset, listScroll + dir));
            return true;
        }
        boolean overInput = mouseX >= left + INPUT_X && mouseX < left + INPUT_X + 58
                && mouseY >= top + GRID_Y && mouseY < top + GRID_Y + 58;
        boolean overOutput = mouseX >= left + OUTPUT_X && mouseX < left + OUTPUT_X + 18
                && mouseY >= top + GRID_Y && mouseY < top + GRID_Y + 58;
        if (overInput || overOutput) {
            inputScroll = Math.max(0, Math.min(maxInputScroll(), inputScroll + dir));
            outputScroll = Math.max(0, Math.min(maxOutputScroll(), outputScroll + dir));
            repositionSlots();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void syncAmountField() {
        if (amountField.isFocused()) {
            return;
        }
        if (selectedSlot >= 0 && selectedSlot < PatternEditMenu.GRID_SLOTS) {
            var stack = this.menu.getGrid().getItem(selectedSlot);
            if (!stack.isEmpty()) {
                amountField.setValue(String.valueOf(stack.getCount()));
                amountField.setEditable(true);
                return;
            }
        }
        amountField.setValue("");
        amountField.setEditable(false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncAmountField();
        repositionSlots();
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        renderTransparentBackground(gui);
        int left = this.leftPos;
        int top = this.topPos;

        gui.fill(left, top, left + this.imageWidth, top + this.imageHeight, COLOR_BORDER);
        gui.fill(left + 1, top + 1, left + this.imageWidth - 1, top + this.imageHeight - 1, COLOR_BG);
        gui.fill(left + 2, top + 2, left + this.imageWidth - 2, top + 12, COLOR_INNER);
        gui.fill(left + 2, top + this.imageHeight - 2, left + this.imageWidth - 2, top + this.imageHeight - 1,
                COLOR_INNER);

        gui.fill(left + TOOL_WIDTH, top + 2, left + TOOL_WIDTH + 1, top + this.imageHeight - 2, COLOR_BORDER);

        gui.fill(left + 2, top + LIST_TOP - 1, left + TOOL_WIDTH, top + LIST_BOTTOM + 1, COLOR_BORDER);
        gui.fill(left + 3, top + LIST_TOP, left + TOOL_WIDTH - 1, top + LIST_BOTTOM, COLOR_INNER);

        for (Slot slot : this.menu.slots) {
            if (slot.y < 0) {
                continue;
            }
            int x = left + slot.x;
            int y = top + slot.y;
            gui.fill(x, y, x + 18, y + 18, COLOR_SLOT_BORDER);
            gui.fill(x + 1, y + 1, x + 17, y + 17, COLOR_SLOT_INNER);
        }

        drawCorners(gui, left, top, this.imageWidth, this.imageHeight, COLOR_BORDER);
    }

    private void drawCorners(GuiGraphics gui, int x, int y, int w, int h, int color) {
        int len = 4;
        gui.fill(x, y, x + len, y + 1, color);
        gui.fill(x, y, x + 1, y + len, color);
        gui.fill(x + w - len, y, x + w, y + 1, color);
        gui.fill(x + w - 1, y, x + w, y + len, color);
        gui.fill(x, y + h - 1, x + len, y + h, color);
        gui.fill(x, y + h - len, x + 1, y + h, color);
        gui.fill(x + w - len, y + h - 1, x + w, y + h, color);
        gui.fill(x + w - 1, y + h - len, x + w, y + h, color);
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        gui.drawString(this.font, this.title, 8, 4, COLOR_TEXT, false);
        gui.drawString(this.font, Component.translatable("patternchecker.menu.problem"),
                6, 26, COLOR_TEXT_DIM, false);

        gui.drawString(this.font, Component.translatable("patternchecker.encode.input"),
                ENC + 24, 20, COLOR_TEXT_DIM, false);
        gui.drawString(this.font, Component.translatable("patternchecker.encode.output"),
                ENC + 109, 20, COLOR_TEXT_DIM, false);
        gui.drawString(this.font, Component.translatable("patternchecker.encode.amount"),
                ENC + 10, 97, COLOR_TEXT_DIM, false);
        gui.drawString(this.font, Component.translatable("patternchecker.encode.blank"),
                ENC + 32, 118, COLOR_TEXT_DIM, false);

        // Mode tab labels.
        String[] modes = {"合成", "处理", "锻造", "切石"};
        for (int i = 0; i < modes.length; i++) {
            int color = i == mode ? COLOR_ACCENT : COLOR_TEXT_DIM;
            gui.drawString(this.font, Component.literal(modes[i]),
                    ENC + 176, 30 + i * 21, color, false);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);

        renderToolList(gui);

        if (selectedSlot >= 0 && selectedSlot < PatternEditMenu.GRID_SLOTS) {
            Slot slot = this.menu.getSlot(selectedSlot);
            gui.fill(this.leftPos + slot.x, this.topPos + slot.y,
                    this.leftPos + slot.x + 18, this.topPos + slot.y + 18, 0x402F5FAF);
        }
    }

    private void renderToolList(GuiGraphics gui) {
        List<ToolListPayload.Entry> entries = toolEntries();
        int left = this.leftPos;
        int top = this.topPos;
        gui.enableScissor(left + 3, top + LIST_TOP, left + TOOL_WIDTH - 1, top + LIST_BOTTOM);
        if (entries.isEmpty()) {
            gui.drawString(this.font, Component.translatable("patternchecker.menu.none"),
                    left + 6, top + LIST_TOP + 4, COLOR_TEXT_DIM, false);
        } else {
            for (int i = listScroll; i < Math.min(entries.size(), listScroll + 6); i++) {
                ToolListPayload.Entry entry = entries.get(i);
                int y = top + LIST_TOP + (i - listScroll) * ROW_HEIGHT;
                if (entry.index() == toolSelected) {
                    gui.fill(left + 3, y, left + TOOL_WIDTH - 1, y + ROW_HEIGHT, COLOR_SELECT);
                }
                int barColor = entry.error() ? 0xFFD13B3B : 0xFFC9931F;
                gui.fill(left + 4, y + 4, left + 6, y + ROW_HEIGHT - 4, barColor);
                String line1 = entry.index() + ". " + entry.output().getString();
                gui.drawString(this.font, this.font.plainSubstrByWidth(line1, TOOL_WIDTH - 16),
                        left + 8, y + 1, COLOR_TEXT, false);
                String line2 = entry.location();
                gui.drawString(this.font, this.font.plainSubstrByWidth(line2, TOOL_WIDTH - 16),
                        left + 8, y + 10, COLOR_TEXT_DIM, false);
                String line3 = entry.summary();
                gui.drawString(this.font, this.font.plainSubstrByWidth(line3, TOOL_WIDTH - 16),
                        left + 8, y + 18, entry.error() ? 0xFFB03A3A : 0xFF8A6D1E, false);
            }
        }
        gui.disableScissor();
    }
}
