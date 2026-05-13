package com.rafid.oretory.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.rafid.oretory.AdvancedMinerBlockEntity;
import com.rafid.oretory.AdvancedMinerMenu;
import com.rafid.oretory.AdvancedMinerPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AdvancedMinerScreen extends AbstractSimiContainerScreen<AdvancedMinerMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("oretory", "textures/gui/advanced_miner_gui.png");

    private static final int GUI_WIDTH  = 176;
    private static final int GUI_HEIGHT = 166;

    // Progress bar — 2px lower than before, clear of fuel slot
    private static final int BAR_X = 72; // Adjusted from 73
    private static final int BAR_Y = 40;
    private static final int BAR_W = 32;
    private static final int BAR_H = 4;

    // Status indicator + text — fixed position, not overlapping anything
    private static final int INDICATOR_X   = 7;
    private static final int INDICATOR_Y   = 60;
    private static final int STATUS_TEXT_X = 26; // Adjusted from 22
    private static final int STATUS_TEXT_Y = 59; // Adjusted from 61

    // Slot visual offset — slots + arrows + boxes all 1px lower as a group
    private static final int SLOT_GROUP_OFFSET = 1;

    // Top threshold box: above the top slot
    private static final int TOP_THRESHOLD_X = AdvancedMinerMenu.OUTPUT_TOP_SLOT_X - 2; // Moved left by 2 pixels
    private static final int TOP_THRESHOLD_Y = AdvancedMinerMenu.OUTPUT_TOP_SLOT_Y - 13 + SLOT_GROUP_OFFSET;
    // Bot threshold box: below the bottom slot
    private static final int BOT_THRESHOLD_X = AdvancedMinerMenu.OUTPUT_BOTTOM_SLOT_X - 2; // Moved left by 2 pixels
    private static final int BOT_THRESHOLD_Y = AdvancedMinerMenu.OUTPUT_BOTTOM_SLOT_Y + 21 + SLOT_GROUP_OFFSET;
    private static final int THRESHOLD_W = 26;
    private static final int THRESHOLD_H = 12;

    private EditBox botThresholdBox;
    private EditBox topThresholdBox;

    private boolean initialised           = false;
    private int     committedThresholdBot = 1;
    private int     committedThresholdTop = 1;

    public AdvancedMinerScreen(AdvancedMinerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        setWindowSize(GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        botThresholdBox = makeThresholdBox(leftPos + BOT_THRESHOLD_X, topPos + BOT_THRESHOLD_Y);
        topThresholdBox = makeThresholdBox(leftPos + TOP_THRESHOLD_X, topPos + TOP_THRESHOLD_Y);
        initialised = false;
        addWidget(botThresholdBox);
        addRenderableWidget(botThresholdBox);
        addWidget(topThresholdBox);
        addRenderableWidget(topThresholdBox);
    }

    private EditBox makeThresholdBox(int x, int y) {
        EditBox box = new EditBox(font, x, y, THRESHOLD_W, THRESHOLD_H, Component.literal("1"));
        box.setMaxLength(2);
        box.setFilter(s -> s.matches("\\d*"));
        box.setBordered(true);
        box.setTextColor(0xFFFFFF);
        return box;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (!initialised) {
            int bot = menu.getOutputThresholdBot();
            int top = menu.getOutputThresholdTop();
            if (bot >= 1 && top >= 1) {
                committedThresholdBot = bot;
                committedThresholdTop = top;
                botThresholdBox.setValue(String.valueOf(bot));
                topThresholdBox.setValue(String.valueOf(top));
                initialised = true;
            }
        }
    }

    private void commitBotThreshold() {
        int value = parseAndClamp(botThresholdBox.getValue(), committedThresholdBot);
        botThresholdBox.setValue(String.valueOf(value));
        if (value != committedThresholdBot) {
            committedThresholdBot = value;
            AdvancedMinerPackets.sendSetThresholdBot(value);
        }
    }

    private void commitTopThreshold() {
        int value = parseAndClamp(topThresholdBox.getValue(), committedThresholdTop);
        topThresholdBox.setValue(String.valueOf(value));
        if (value != committedThresholdTop) {
            committedThresholdTop = value;
            AdvancedMinerPackets.sendSetThresholdTop(value);
        }
    }

    private int parseAndClamp(String text, int fallback) {
        if (text.isEmpty()) return fallback;
        try { return Math.max(1, Math.min(64, Integer.parseInt(text))); }
        catch (NumberFormatException e) { return fallback; }
    }

    @Override
    public void onClose() {
        commitBotThreshold();
        commitTopThreshold();
        super.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean botWasFocused = botThresholdBox.isFocused();
        boolean topWasFocused = topThresholdBox.isFocused();
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        if (botWasFocused && !botThresholdBox.isFocused()) commitBotThreshold();
        if (topWasFocused && !topThresholdBox.isFocused()) commitTopThreshold();
        return result;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (botThresholdBox.isFocused() && (keyCode == 257 || keyCode == 258)) {
            commitBotThreshold();
            botThresholdBox.setFocused(false);
            return true;
        }
        if (topThresholdBox.isFocused() && (keyCode == 257 || keyCode == 258)) {
            commitTopThreshold();
            topThresholdBox.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Fuel slot (unchanged position)
        AllGuiTextures.SCHEMATIC_SLOT.render(graphics,
                leftPos + AdvancedMinerMenu.FUEL_SLOT_X,
                topPos  + AdvancedMinerMenu.FUEL_SLOT_Y);

        // Output slots shifted down by SLOT_GROUP_OFFSET
        AllGuiTextures.SCHEMATIC_SLOT.render(graphics,
                leftPos + AdvancedMinerMenu.OUTPUT_TOP_SLOT_X,
                topPos  + AdvancedMinerMenu.OUTPUT_TOP_SLOT_Y    + SLOT_GROUP_OFFSET);
        AllGuiTextures.SCHEMATIC_SLOT.render(graphics,
                leftPos + AdvancedMinerMenu.OUTPUT_BOTTOM_SLOT_X,
                topPos  + AdvancedMinerMenu.OUTPUT_BOTTOM_SLOT_Y + SLOT_GROUP_OFFSET);

        renderMiningProgress(graphics);
        renderStatusIndicator(graphics);
    }

    private void renderMiningProgress(GuiGraphics graphics) {
        if (menu.getStatus() != AdvancedMinerBlockEntity.STATUS_MINING) return;
        int speedEncoded = menu.getSpeedEncoded();
        if (speedEncoded == 0) return;

        float speedMult  = speedEncoded / 100f;
        int   totalTicks = Math.max(20, (int)(200 * speedMult / 1.5f));
        int   progress   = menu.getMiningProgress();

        int barX   = leftPos + BAR_X;
        int barY   = topPos  + BAR_Y;
        int filled = (int)(BAR_W * Math.min(1f, (float) progress / totalTicks));

        graphics.fill(barX, barY, barX + BAR_W, barY + BAR_H, 0xFF333333);
        if (filled > 0)
            graphics.fill(barX, barY, barX + filled, barY + BAR_H, 0xFFFF8800);
        graphics.renderOutline(barX - 1, barY - 1, BAR_W + 2, BAR_H + 2, 0xFF888888);
    }

    private void renderStatusIndicator(GuiGraphics graphics) {
        int status = menu.getStatus();
        AllGuiTextures indicator = switch (status) {
            case AdvancedMinerBlockEntity.STATUS_MINING      -> AllGuiTextures.INDICATOR_GREEN;
            case AdvancedMinerBlockEntity.STATUS_IDLE        -> AllGuiTextures.INDICATOR_YELLOW;
            case AdvancedMinerBlockEntity.STATUS_OUTPUT_FULL -> AllGuiTextures.INDICATOR_RED;
            case AdvancedMinerBlockEntity.STATUS_NO_ORE      -> AllGuiTextures.INDICATOR_YELLOW;
            default                                          -> AllGuiTextures.INDICATOR;
        };
        indicator.render(graphics, leftPos + INDICATOR_X, topPos + INDICATOR_Y);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderForeground(graphics, mouseX, mouseY, partialTicks);

        // Title
        graphics.drawString(font,
                Component.translatable("block.oretory.advanced_miner"),
                leftPos + 8, topPos + 6, 0xFFFFFF, false);

        // Status text (right of indicator dot)
        int status = menu.getStatus();
        String statusStr = switch (status) {
            case AdvancedMinerBlockEntity.STATUS_MINING      -> "Mining";
            case AdvancedMinerBlockEntity.STATUS_IDLE        -> "Idle";
            case AdvancedMinerBlockEntity.STATUS_OUTPUT_FULL -> "Full";
            case AdvancedMinerBlockEntity.STATUS_NO_ORE      -> "No Ore";
            default                                          -> "No Fuel";
        };
        int statusColor = switch (status) {
            case AdvancedMinerBlockEntity.STATUS_MINING      -> 0x44FF44;
            case AdvancedMinerBlockEntity.STATUS_IDLE        -> 0xFFCC00;
            case AdvancedMinerBlockEntity.STATUS_OUTPUT_FULL -> 0xFF4444;
            case AdvancedMinerBlockEntity.STATUS_NO_ORE      -> 0xFFAA00;
            default                                          -> 0x888888;
        };
        graphics.drawString(font, statusStr,
                leftPos + STATUS_TEXT_X, topPos + STATUS_TEXT_Y, statusColor, false);

        // Fuel label
        graphics.drawString(font, "Fuel",
                leftPos + AdvancedMinerMenu.FUEL_SLOT_X - 2,
                topPos  + AdvancedMinerMenu.FUEL_SLOT_Y - 10, 0xFFFFFF, false);

        // Arrow indicators for threshold boxes, shifted down 1px with the group
        graphics.drawString(font, "\u25B2",
                leftPos + TOP_THRESHOLD_X + THRESHOLD_W + 2,
                topPos  + TOP_THRESHOLD_Y  + 1, 0x888888, false);
        graphics.drawString(font, "\u25BC",
                leftPos + BOT_THRESHOLD_X + THRESHOLD_W + 2,
                topPos  + BOT_THRESHOLD_Y  + 1, 0x888888, false);

        // Player inventory title
        graphics.drawString(font, playerInventoryTitle,
                leftPos + 8, topPos + GUI_HEIGHT - 96 + 2, 0xFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}