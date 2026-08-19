package com.patternchecker.client.screen;

import appeng.client.gui.widgets.AE2Button;
import com.patternchecker.client.PatternCheckClient;
import com.patternchecker.menu.PatternCheckMenu;
import com.patternchecker.network.NetworkHandler;
import com.patternchecker.network.ToolListPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Standalone pattern-checker screen. Its visual language and interactions
 * intentionally mirror the floating checker module in AE2 encoding terminals.
 */
public class PatternCheckScreen extends AbstractContainerScreen<PatternCheckMenu> {

    private static final ResourceLocation PATTERN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/pattern.png");

    private static final int PANEL_WIDTH = 158;
    private static final int PANEL_HEIGHT = 218;
    private static final int TEXTURE_SIZE = 256;
    private static final int SOURCE_WIDTH = 195;
    private static final int HEADER_HEIGHT = 17;

    private static final int BORDER_COLOR = 0xFF413F54;
    private static final int PANEL_COLOR = 0xFFCBCCD4;
    private static final int PANEL_HIGHLIGHT_COLOR = 0xFFF2F2F2;
    private static final int RECESS_COLOR = 0xFFADB0C4;
    private static final int TEXT_COLOR = 0xFF413F54;
    private static final int DIM_TEXT_COLOR = 0xFF6E7080;
    private static final int ACCENT_COLOR = 0xFF2F5FAF;
    private static final int SELECT_COLOR = 0xFFBBD4FF;
    private static final int SELECT_BORDER_COLOR = 0xFF6D8FC4;
    private static final int ERROR_COLOR = 0xFFD13B3B;
    private static final int WARNING_COLOR = 0xFFC9931F;

    private static final int OUTER_PADDING = 4;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 2;
    private static final int TOP_BUTTON_Y = 19;
    private static final int TOGGLE_BUTTON_Y = 39;
    private static final int LIST_TOP = 61;
    private static final int LIST_SIDE = 5;
    private static final int LIST_BOTTOM_RESERVED = 58;
    private static final int ROW_HEIGHT = 22;
    private static final int ACTION_BUTTONS = 4;
    private static final int TOOLTIP_WIDTH = 240;

    private AE2Button scanButton;
    private AE2Button inputButton;
    private AE2Button duplicateButton;
    private AE2Button unbindButton;
    private AE2Button highlightButton;
    private AE2Button extractButton;
    private AE2Button uploadButton;
    private AE2Button editButton;

    private int scroll;
    private int selected = -1;

    public PatternCheckScreen(PatternCheckMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int left = this.leftPos;
        int top = this.topPos;
        int splitWidth = (this.imageWidth - OUTER_PADDING * 2 - BUTTON_GAP) / 2;

        scanButton = addRenderableWidget(new AE2Button(
                left + OUTER_PADDING, top + TOP_BUTTON_Y, splitWidth, BUTTON_HEIGHT,
                Component.translatable("patternchecker.menu.scan"),
                button -> sendButton(PatternCheckMenu.BUTTON_SCAN)));
        unbindButton = addRenderableWidget(new AE2Button(
                left + OUTER_PADDING + splitWidth + BUTTON_GAP, top + TOP_BUTTON_Y,
                this.imageWidth - OUTER_PADDING * 2 - BUTTON_GAP - splitWidth, BUTTON_HEIGHT,
                Component.translatable("patternchecker.menu.unbind"),
                button -> sendButton(PatternCheckMenu.BUTTON_UNBIND)));
        inputButton = addRenderableWidget(new AE2Button(
                left + OUTER_PADDING, top + TOGGLE_BUTTON_Y,
                splitWidth, BUTTON_HEIGHT,
                Component.empty(),
                button -> sendButton(PatternCheckMenu.BUTTON_TOGGLE_INPUT)));
        duplicateButton = addRenderableWidget(new AE2Button(
                left + OUTER_PADDING + splitWidth + BUTTON_GAP, top + TOGGLE_BUTTON_Y,
                this.imageWidth - OUTER_PADDING * 2 - BUTTON_GAP - splitWidth, BUTTON_HEIGHT,
                Component.empty(),
                button -> sendButton(PatternCheckMenu.BUTTON_TOGGLE_DUPLICATE)));

        int actionY = top + actionButtonY();
        int available = this.imageWidth - OUTER_PADDING * 2 - BUTTON_GAP * (ACTION_BUTTONS - 1);
        int actionWidth = available / ACTION_BUTTONS;
        int remainder = available % ACTION_BUTTONS;
        highlightButton = actionButton(0, actionY, actionWidth, remainder,
                "patternchecker.menu.highlight", PatternCheckMenu.BUTTON_HIGHLIGHT);
        editButton = actionButton(1, actionY, actionWidth, remainder,
                "patternchecker.menu.edit", PatternCheckMenu.BUTTON_EDIT);
        extractButton = actionButton(2, actionY, actionWidth, remainder,
                "patternchecker.menu.extract", PatternCheckMenu.BUTTON_EXTRACT);
        uploadButton = actionButton(3, actionY, actionWidth, remainder,
                "patternchecker.menu.upload", PatternCheckMenu.BUTTON_UPLOAD);
    }

    private AE2Button actionButton(int index, int y, int baseWidth, int remainder,
                                   String labelKey, int action) {
        int widthBefore = index * baseWidth + Math.min(index, remainder);
        int buttonWidth = baseWidth + (index < remainder ? 1 : 0);
        int x = this.leftPos + OUTER_PADDING + index * BUTTON_GAP + widthBefore;
        return addRenderableWidget(new AE2Button(
                x, y, buttonWidth, BUTTON_HEIGHT, Component.translatable(labelKey),
                button -> {
                    ToolListPayload.Entry entry = selectedEntry();
                    if (entry != null && entry.hasProvider()) {
                        sendButton(action + selected);
                    }
                }));
    }

    private List<ToolListPayload.Entry> entries() {
        List<ToolListPayload.Entry> entries =
                new ArrayList<>(PatternCheckClient.getToolList().entries());
        entries.sort(Comparator.comparingInt(ToolListPayload.Entry::category)
                .thenComparingInt(ToolListPayload.Entry::index));
        return entries;
    }

    private ToolListPayload.Entry selectedEntry() {
        for (ToolListPayload.Entry entry : entries()) {
            if (entry.index() == selected) {
                return entry;
            }
        }
        return null;
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.send(new ServerboundContainerButtonClickPacket(this.menu.containerId, id));
        }
    }

    private int listBottom() {
        return this.imageHeight - LIST_BOTTOM_RESERVED;
    }

    private int actionButtonY() {
        return this.imageHeight - 54;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - LIST_TOP) / ROW_HEIGHT);
    }

    private boolean isInsidePanel(double mouseX, double mouseY) {
        return mouseX >= this.leftPos && mouseX < this.leftPos + this.imageWidth
                && mouseY >= this.topPos && mouseY < this.topPos + this.imageHeight;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isInsidePanel(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int maxScroll = Math.max(0, entries().size() - visibleRows());
        int direction = verticalAmount > 0 ? -1 : verticalAmount < 0 ? 1 : 0;
        scroll = Math.max(0, Math.min(maxScroll, scroll + direction));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) {
            return handled;
        }

        ToolListPayload.Entry entry = entryAt(mouseX, mouseY);
        if (entry != null) {
            selected = entry.index();
            return true;
        }

        int listLeft = this.leftPos + LIST_SIDE;
        int listRight = this.leftPos + this.imageWidth - LIST_SIDE;
        int listTop = this.topPos + LIST_TOP;
        int listBottom = this.topPos + listBottom();
        if (mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom) {
            selected = -1;
            return true;
        }
        return handled;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ToolListPayload polled = NetworkHandler.poll();
        if (polled != null) {
            PatternCheckClient.setToolList(polled);
            selected = -1;
            scroll = 0;
        }

        ToolListPayload payload = PatternCheckClient.getToolList();
        ToolListPayload.Entry entry = selectedEntry();
        boolean canAct = entry != null && entry.hasProvider();
        highlightButton.active = canAct;
        editButton.active = canAct;
        extractButton.active = canAct;
        uploadButton.active = canAct;
        scanButton.active = payload.bound();
        unbindButton.active = payload.bound();
        inputButton.setMessage(Component.translatable(
                payload.inputIssues()
                        ? "patternchecker.menu.input.on"
                        : "patternchecker.menu.input.off"));
        duplicateButton.setMessage(Component.translatable(
                payload.duplicateIssues()
                        ? "patternchecker.menu.duplicate.on"
                        : "patternchecker.menu.duplicate.off"));
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        renderTransparentBackground(gui);
        drawAePanel(gui, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    private void drawAePanel(GuiGraphics gui, int x, int y, int width, int height) {
        blitHorizontalSlice(gui, x, y, width, 0, HEADER_HEIGHT);
        gui.fill(x, y + HEADER_HEIGHT, x + width, y + height, PANEL_COLOR);
        gui.fill(x, y + HEADER_HEIGHT, x + 1, y + height, BORDER_COLOR);
        gui.fill(x + 1, y + HEADER_HEIGHT, x + 2, y + height - 1, PANEL_HIGHLIGHT_COLOR);
        gui.fill(x + width - 1, y + HEADER_HEIGHT, x + width, y + height, BORDER_COLOR);
        gui.fill(x, y + height - 1, x + width, y + height, BORDER_COLOR);
    }

    private void blitHorizontalSlice(GuiGraphics gui, int x, int y, int width,
                                     int sourceY, int height) {
        int leftCap = Math.min(4, width);
        int rightCap = Math.min(4, Math.max(0, width - leftCap));
        int centerWidth = Math.max(0, width - leftCap - rightCap);

        if (leftCap > 0) {
            gui.blit(PATTERN_TEXTURE, x, y, 0, sourceY,
                    leftCap, height, TEXTURE_SIZE, TEXTURE_SIZE);
        }
        if (centerWidth > 0) {
            gui.blit(PATTERN_TEXTURE, x + leftCap, y, centerWidth, height,
                    leftCap, sourceY, SOURCE_WIDTH - leftCap - rightCap, height,
                    TEXTURE_SIZE, TEXTURE_SIZE);
        }
        if (rightCap > 0) {
            gui.blit(PATTERN_TEXTURE, x + width - rightCap, y, SOURCE_WIDTH - rightCap, sourceY,
                    rightCap, height, TEXTURE_SIZE, TEXTURE_SIZE);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        gui.drawString(this.font, Component.translatable("patternchecker.menu.title"),
                8, 6, TEXT_COLOR, false);

        ToolListPayload payload = PatternCheckClient.getToolList();
        int maxWidth = this.imageWidth - OUTER_PADDING * 2;
        int statusY = actionButtonY() + BUTTON_HEIGHT + 3;
        Component status = payload.bound()
                ? Component.translatable("patternchecker.bound.status", payload.boundLabel())
                : Component.translatable("patternchecker.bound.notBound");
        gui.drawString(this.font, this.font.plainSubstrByWidth(status.getString(), maxWidth),
                OUTER_PADDING, statusY, payload.bound() ? ACCENT_COLOR : DIM_TEXT_COLOR, false);

        String secondLine;
        int secondLineColor;
        if (!payload.notice().getString().isEmpty()) {
            secondLine = payload.notice().getString();
            secondLineColor = ACCENT_COLOR;
        } else {
            secondLine = selectedEntry() != null
                    ? Component.translatable("patternchecker.menu.hoverHint").getString()
                    : Component.translatable("patternchecker.menu.selectHint").getString();
            secondLineColor = DIM_TEXT_COLOR;
        }
        gui.drawString(this.font, this.font.plainSubstrByWidth(secondLine, maxWidth),
                OUTER_PADDING, statusY + 10, secondLineColor, false);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        renderList(gui, mouseX, mouseY);
        renderSelectedIssueTooltip(gui, mouseX, mouseY);
    }

    private void renderList(GuiGraphics gui, int mouseX, int mouseY) {
        int listLeft = this.leftPos + LIST_SIDE;
        int listRight = this.leftPos + this.imageWidth - LIST_SIDE;
        int listTop = this.topPos + LIST_TOP;
        int listBottom = this.topPos + listBottom();
        int contentRight = listRight - 5;

        gui.fill(listLeft - 1, listTop - 1, listRight + 1, listBottom + 1, BORDER_COLOR);
        gui.fill(listLeft, listTop, listRight, listBottom, RECESS_COLOR);
        gui.enableScissor(listLeft, listTop, listRight, listBottom);

        List<ToolListPayload.Entry> entries = entries();
        int rows = visibleRows();
        if (entries.isEmpty()) {
            gui.drawString(this.font, Component.translatable("patternchecker.menu.none"),
                    listLeft + 4, listTop + 5, DIM_TEXT_COLOR, false);
        } else {
            for (int i = scroll; i < Math.min(entries.size(), scroll + rows); i++) {
                ToolListPayload.Entry entry = entries.get(i);
                int rowY = listTop + (i - scroll) * ROW_HEIGHT;
                boolean hovered = mouseX >= listLeft && mouseX < listRight
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                renderEntry(gui, entry, listLeft, contentRight, rowY, hovered);
            }
        }
        gui.disableScissor();

        renderScrollbar(gui, entries.size(), listRight, listTop, listBottom);
    }

    private void renderEntry(GuiGraphics gui, ToolListPayload.Entry entry,
                             int left, int right, int y, boolean hovered) {
        boolean isSelected = entry.index() == selected;
        if (isSelected) {
            gui.fill(left, y, right + 4, y + ROW_HEIGHT, SELECT_BORDER_COLOR);
            gui.fill(left + 1, y + 1, right + 3, y + ROW_HEIGHT - 1, SELECT_COLOR);
        } else if (hovered) {
            gui.fill(left, y, right + 4, y + ROW_HEIGHT, 0xFFE0E1E6);
        }

        int severityColor = entry.error() ? ERROR_COLOR : WARNING_COLOR;
        gui.fill(left + 2, y + 2, left + 4, y + ROW_HEIGHT - 2, severityColor);

        ItemStack icon = iconFor(entry.itemId());
        int textX = left + 6;
        if (!icon.isEmpty()) {
            gui.renderItem(icon, left + 6, y + 3);
            textX = left + 25;
        }

        int textWidth = Math.max(12, right - textX);
        String output = entry.index() + ". " + entry.output().getString();
        gui.drawString(this.font, this.font.plainSubstrByWidth(output, textWidth),
                textX, y + 3, TEXT_COLOR, false);
        gui.drawString(this.font, this.font.plainSubstrByWidth(entry.location(), textWidth),
                textX, y + 13, DIM_TEXT_COLOR, false);
    }

    private void renderSelectedIssueTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        ToolListPayload.Entry entry = entryAt(mouseX, mouseY);
        if (entry == null || entry.index() != selected) {
            return;
        }

        List<FormattedCharSequence> lines = new ArrayList<>();
        String output = entry.output().getString();
        String title = output.isEmpty() ? entry.name() : output;
        lines.add(Component.literal(entry.index() + ". " + title)
                .withStyle(ChatFormatting.WHITE)
                .getVisualOrderText());

        ChatFormatting severity = entry.error() ? ChatFormatting.RED : ChatFormatting.GOLD;
        Component issue = Component.translatable(
                        entry.error() ? "patternchecker.chat.error" : "patternchecker.chat.warning")
                .append(Component.literal(" "))
                .append(Component.literal(entry.summary()))
                .withStyle(severity);
        lines.addAll(this.font.split(issue, TOOLTIP_WIDTH));

        if (!entry.input().getString().isEmpty()) {
            lines.addAll(this.font.split(
                    Component.translatable("patternchecker.menu.input", entry.input()),
                    TOOLTIP_WIDTH));
        }
        lines.addAll(this.font.split(
                Component.literal(entry.location()).withStyle(ChatFormatting.DARK_GRAY),
                TOOLTIP_WIDTH));
        gui.renderTooltip(this.font, lines, mouseX, mouseY);
    }

    private ToolListPayload.Entry entryAt(double mouseX, double mouseY) {
        int listLeft = this.leftPos + LIST_SIDE;
        int listRight = this.leftPos + this.imageWidth - LIST_SIDE;
        int listTop = this.topPos + LIST_TOP;
        int listBottom = this.topPos + listBottom();
        if (mouseX < listLeft || mouseX >= listRight || mouseY < listTop || mouseY >= listBottom) {
            return null;
        }
        List<ToolListPayload.Entry> entries = entries();
        int row = ((int) mouseY - listTop) / ROW_HEIGHT + scroll;
        return row >= 0 && row < entries.size() ? entries.get(row) : null;
    }

    private void renderScrollbar(GuiGraphics gui, int entryCount,
                                 int right, int top, int bottom) {
        int visibleRows = visibleRows();
        if (entryCount <= visibleRows) {
            return;
        }
        int trackTop = top + 2;
        int trackBottom = bottom - 2;
        int trackHeight = trackBottom - trackTop;
        int thumbHeight = Math.max(8, trackHeight * visibleRows / entryCount);
        int maxScroll = entryCount - visibleRows;
        int thumbY = trackTop + scroll * (trackHeight - thumbHeight) / maxScroll;

        gui.fill(right - 4, trackTop, right - 2, trackBottom, 0xFF85879A);
        gui.fill(right - 5, thumbY, right - 1, thumbY + thumbHeight, BORDER_COLOR);
        gui.fill(right - 4, thumbY + 1, right - 2, thumbY + thumbHeight - 1, PANEL_COLOR);
    }

    private static ItemStack iconFor(String itemId) {
        try {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }
}
