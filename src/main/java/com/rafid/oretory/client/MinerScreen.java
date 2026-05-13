package com.rafid.oretory.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.rafid.oretory.MinerBlockEntity;
import com.rafid.oretory.MinerMenu;
import com.rafid.oretory.MinerPackets;
import com.rafid.oretory.MinerRedstoneMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MinerScreen extends AbstractSimiContainerScreen<MinerMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("oretory", "textures/gui/miner_gui.png");

    private static final int GUI_WIDTH  = 176;
    private static final int GUI_HEIGHT = 166;

    private static final int FUEL_SLOT_X   = 44;
    private static final int FUEL_SLOT_Y   = 35;
    private static final int OUTPUT_SLOT_X = 116;
    private static final int OUTPUT_SLOT_Y = 35;

    private static final int THRESHOLD_BOX_X = 68;
    private static final int THRESHOLD_BOX_Y = 56;
    private static final int THRESHOLD_BOX_W = 40;
    private static final int THRESHOLD_BOX_H = 12;

    private EditBox thresholdBox;

    // True once the box has been seeded from the server on first open.
    private boolean initialised = false;

    // The value last sent to the server.
    private int committedThreshold = 1;

    public MinerScreen(MinerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        setWindowSize(GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();

        thresholdBox = new EditBox(
                font,
                leftPos + THRESHOLD_BOX_X,
                topPos  + THRESHOLD_BOX_Y,
                THRESHOLD_BOX_W,
                THRESHOLD_BOX_H,
                Component.literal("1"));

        thresholdBox.setMaxLength(2);
        thresholdBox.setFilter(s -> s.matches("\\d*"));
        thresholdBox.setBordered(true);
        thresholdBox.setTextColor(0xFFFFFF);

        // Reset so containerTick will seed from the server again on (re)open.
        initialised = false;

        addWidget(thresholdBox);
        addRenderableWidget(thresholdBox);
    }

    // Parse, clamp, display, and send to server if changed.
    private void commitThreshold() {
        String text = thresholdBox.getValue();
        int value = committedThreshold;
        if (!text.isEmpty()) {
            try { value = Integer.parseInt(text); } catch (NumberFormatException ignored) {}
        }
        value = Math.max(1, Math.min(64, value));
        thresholdBox.setValue(String.valueOf(value));

        if (value != committedThreshold) {
            committedThreshold = value;
            MinerPackets.sendSetThreshold(value);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        if (!initialised) {
            int serverValue = menu.getOutputThreshold();
            // ContainerData starts at 0 client-side before the server syncs; wait for a real value.
            if (serverValue >= 1) {
                committedThreshold = serverValue;
                thresholdBox.setValue(String.valueOf(serverValue));
                initialised = true;
            }
        }
        // After initialisation the player's input is the source of truth — never overwrite again.
    }

    // Commit when the player closes the screen so the final value is always saved.
    @Override
    public void onClose() {
        commitThreshold();
        super.onClose();
    }

    // Commit when focus moves away from the box by mouse click.
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean boxWasFocused = thresholdBox.isFocused();
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        if (boxWasFocused && !thresholdBox.isFocused()) {
            commitThreshold();
        }
        return result;
    }

    // Commit on Enter (257) or Tab (258).
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (thresholdBox.isFocused() && (keyCode == 257 || keyCode == 258)) {
            commitThreshold();
            thresholdBox.setFocused(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        AllGuiTextures.SCHEMATIC_SLOT.render(graphics, leftPos + FUEL_SLOT_X,   topPos + FUEL_SLOT_Y);
        AllGuiTextures.SCHEMATIC_SLOT.render(graphics, leftPos + OUTPUT_SLOT_X, topPos + OUTPUT_SLOT_Y);

        renderMiningProgress(graphics);
        renderStatusIndicator(graphics);
    }

    private void renderMiningProgress(GuiGraphics graphics) {
        if (menu.getStatus() != MinerBlockEntity.STATUS_MINING) return;
        int speedEncoded = menu.getSpeedEncoded();
        if (speedEncoded == 0) return;

        float speedMult  = speedEncoded / 100f;
        int   totalTicks = Math.max(20, (int) (200 * speedMult));
        int   progress   = menu.getMiningProgress();

        int barX   = leftPos + 68;
        int barY   = topPos  + 39;
        int barW   = 40;
        int barH   = 4;
        int filled = (int) (barW * Math.min(1f, (float) progress / totalTicks));

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
        if (filled > 0)
            graphics.fill(barX, barY, barX + filled, barY + barH, 0xFF44DDFF);
        graphics.renderOutline(barX - 1, barY - 1, barW + 2, barH + 2, 0xFF888888);
    }

    private void renderStatusIndicator(GuiGraphics graphics) {
        int status = menu.getStatus();
        AllGuiTextures indicator = switch (status) {
            case MinerBlockEntity.STATUS_MINING      -> AllGuiTextures.INDICATOR_GREEN;
            case MinerBlockEntity.STATUS_IDLE        -> AllGuiTextures.INDICATOR_YELLOW;
            case MinerBlockEntity.STATUS_OUTPUT_FULL -> AllGuiTextures.INDICATOR_RED;
            case MinerBlockEntity.STATUS_NO_ORE      -> AllGuiTextures.INDICATOR_YELLOW;
            case MinerBlockEntity.STATUS_MIXED_ORES  -> AllGuiTextures.INDICATOR_RED;
            default                                  -> AllGuiTextures.INDICATOR;
        };
        indicator.render(graphics, leftPos + 7, topPos + 60);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderForeground(graphics, mouseX, mouseY, partialTicks);

        graphics.drawString(font,
                Component.translatable("block.oretory.miner"),
                leftPos + 8, topPos + 6, 0xFFFFFF, false);

        int status = menu.getStatus();
        String statusStr = switch (status) {
            case MinerBlockEntity.STATUS_MINING      -> "Mining";
            case MinerBlockEntity.STATUS_IDLE        -> "Idle";
            case MinerBlockEntity.STATUS_OUTPUT_FULL -> "Full";
            case MinerBlockEntity.STATUS_NO_ORE      -> "No Ore";
            case MinerBlockEntity.STATUS_MIXED_ORES  -> "Mixed Ores";
            default                                  -> "No Fuel";
        };
        int statusColor = switch (status) {
            case MinerBlockEntity.STATUS_MINING      -> 0x44FF44;
            case MinerBlockEntity.STATUS_IDLE        -> 0xFFCC00;
            case MinerBlockEntity.STATUS_OUTPUT_FULL -> 0xFF4444;
            case MinerBlockEntity.STATUS_NO_ORE      -> 0xFFAA00;
            case MinerBlockEntity.STATUS_MIXED_ORES  -> 0xDD44FF;
            default                                  -> 0x888888;
        };
        graphics.drawString(font, statusStr, leftPos + 30, topPos + 60, statusColor, false);

        MinerRedstoneMode mode = MinerRedstoneMode.values()[
                Math.min(menu.getRsMode(), MinerRedstoneMode.values().length - 1)];
        String rsHint = "RS: " + mode.getDisplayName();
        int rsColor = switch (mode) {
            case IGNORED  -> 0x888888;
            case ENABLED  -> 0x44FF44;
            case DISABLED -> 0xFF4444;
        };
        graphics.drawString(font, rsHint,
                leftPos + GUI_WIDTH - font.width(rsHint) - 8,
                topPos + 60, rsColor, false);

        graphics.drawString(font, "Fuel",
                leftPos + FUEL_SLOT_X - 2,   topPos + FUEL_SLOT_Y   - 14, 0xFFFFFF, false);
        graphics.drawString(font, "Output",
                leftPos + OUTPUT_SLOT_X - 8, topPos + OUTPUT_SLOT_Y - 14, 0xFFFFFF, false);

        graphics.drawString(font, playerInventoryTitle,
                leftPos + 8, topPos + GUI_HEIGHT - 96 + 2, 0xFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}