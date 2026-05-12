package com.rafid.oretory.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.rafid.oretory.MinerBlockEntity;
import com.rafid.oretory.MinerMenu;
import com.rafid.oretory.MinerPackets;
import com.rafid.oretory.MinerRedstoneMode;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MinerScreen extends AbstractSimiContainerScreen<MinerMenu> {

    // Our own GUI background texture (you need to create this PNG)
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("oretory", "textures/gui/miner.png");

    // GUI size
    private static final int GUI_WIDTH  = 176;
    private static final int GUI_HEIGHT = 166;

    // Slot positions (match MinerMenu)
    private static final int FUEL_SLOT_X   = 44;
    private static final int FUEL_SLOT_Y   = 35;
    private static final int OUTPUT_SLOT_X = 116;
    private static final int OUTPUT_SLOT_Y = 35;

    // Redstone mode cycle button
    private IconButton rsModeButton;

    public MinerScreen(MinerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        setWindowSize(GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();

        // Redstone mode toggle button — positioned top-right area of the GUI
        // Uses Create's I_ACTIVE / I_PASSIVE icons to indicate enabled/disabled
        rsModeButton = new IconButton(leftPos + 152, topPos + 10, getRedstoneIcon()) {
            @Override
            public boolean mouseClicked(double mx, double my, int btn) {
                if (!isActive() || !isHovered()) return false;
                // Send a packet to the server to cycle the redstone mode
                MinerPackets.sendCycleRedstoneMode();
                playDownSound(minecraft.getSoundManager());
                updateTooltipAndIcon();
                return true;
            }
        };
        rsModeButton.setToolTip(buildRsModeTooltip());
        addRenderableWidget(rsModeButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Keep the button icon in sync with the mode reported by the server
        if (rsModeButton != null) {
            rsModeButton.setIcon(getRedstoneIcon());
            rsModeButton.setToolTip(buildRsModeTooltip());
        }
    }

    // -------------------------------------------------------------------------
    // Background rendering
    // -------------------------------------------------------------------------
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // Draw GUI background texture
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Draw fuel slot highlight using Create's SCHEMATIC_SLOT widget element
        AllGuiTextures.SCHEMATIC_SLOT.render(graphics, leftPos + FUEL_SLOT_X - 1, topPos + FUEL_SLOT_Y - 1);

        // Draw a simple mining progress indicator
        renderMiningProgress(graphics);

        // Draw status indicator (colored dot using Create's Indicator widget texture)
        renderStatusIndicator(graphics);
    }

    // -------------------------------------------------------------------------
    // Mining progress bar
    // -------------------------------------------------------------------------
    private void renderMiningProgress(GuiGraphics graphics) {
        int status = menu.getStatus();
        if (status != MinerBlockEntity.STATUS_MINING) return;

        int speedEncoded = menu.getSpeedEncoded();
        if (speedEncoded == 0) return;

        float speedMult = speedEncoded / 100f;
        int baseTicks   = 200; // approximate; real value comes from config on server
        int totalTicks  = Math.max(20, (int) (baseTicks * speedMult));
        int progress    = menu.getMiningProgress();

        // Simple progress bar: 60px wide, 4px tall, centered between slots
        int barX  = leftPos + 68;
        int barY  = topPos  + 39;
        int barW  = 40;
        int barH  = 4;
        float pct = Math.min(1f, (float) progress / totalTicks);
        int filled = (int) (barW * pct);

        // Background (dark)
        graphics.fill(barX,          barY, barX + barW,     barY + barH, 0xFF333333);
        // Fill (aqua → matches Create's style)
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + barH, 0xFF44DDFF);
        }
        // Border
        graphics.renderOutline(barX - 1, barY - 1, barW + 2, barH + 2, 0xFF888888);
    }

    // -------------------------------------------------------------------------
    // Status indicator dot (uses Create's INDICATOR widget textures)
    // -------------------------------------------------------------------------
    private void renderStatusIndicator(GuiGraphics graphics) {
        int status = menu.getStatus();
        AllGuiTextures indicator = switch (status) {
            case MinerBlockEntity.STATUS_MINING      -> AllGuiTextures.INDICATOR_GREEN;
            case MinerBlockEntity.STATUS_IDLE        -> AllGuiTextures.INDICATOR_YELLOW;
            case MinerBlockEntity.STATUS_OUTPUT_FULL -> AllGuiTextures.INDICATOR_RED;
            case MinerBlockEntity.STATUS_NO_ORE      -> AllGuiTextures.INDICATOR_YELLOW;
            default                                  -> AllGuiTextures.INDICATOR;      // grey / off
        };
        // Draw indicator strip just above the fuel slot label
        indicator.render(graphics, leftPos + 7, topPos + 60);
    }

    // -------------------------------------------------------------------------
    // Foreground labels
    // -------------------------------------------------------------------------
    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderForeground(graphics, mouseX, mouseY, partialTicks);

        // Title
        graphics.drawString(font,
                Component.translatable("block.oretory.miner"),
                leftPos + 8, topPos + 6, 0x404040, false);

        // Status text
        int status = menu.getStatus();
        String statusStr = switch (status) {
            case MinerBlockEntity.STATUS_MINING      -> "Mining";
            case MinerBlockEntity.STATUS_IDLE        -> "Idle";
            case MinerBlockEntity.STATUS_OUTPUT_FULL -> "Full";
            case MinerBlockEntity.STATUS_NO_ORE      -> "No Ore";
            default                                  -> "No Fuel";
        };
        int statusColor = switch (status) {
            case MinerBlockEntity.STATUS_MINING      -> 0x44FF44;
            case MinerBlockEntity.STATUS_IDLE        -> 0xFFCC00;
            case MinerBlockEntity.STATUS_OUTPUT_FULL -> 0xFF4444;
            case MinerBlockEntity.STATUS_NO_ORE      -> 0xFFAA00;
            default                                  -> 0x888888;
        };
        graphics.drawString(font, statusStr, leftPos + 26, topPos + 60, statusColor, false);

        // Slot labels
        graphics.drawString(font, "Fuel",   leftPos + FUEL_SLOT_X   - 2, topPos + FUEL_SLOT_Y   - 10, 0x666666, false);
        graphics.drawString(font, "Output", leftPos + OUTPUT_SLOT_X - 8, topPos + OUTPUT_SLOT_Y - 10, 0x666666, false);

        // Player inventory title
        graphics.drawString(font, playerInventoryTitle,
                leftPos + 8, topPos + GUI_HEIGHT - 96 + 2, 0x404040, false);
    }

    // -------------------------------------------------------------------------
    // Redstone mode button helpers
    // -------------------------------------------------------------------------
    private ScreenElement getRedstoneIcon() {
        int rsMode = menu.getRsMode();
        MinerRedstoneMode mode = MinerRedstoneMode.values()[
                Math.min(rsMode, MinerRedstoneMode.values().length - 1)];
        return switch (mode) {
            case IGNORED  -> AllIcons.I_PASSIVE;   // grey  — ignored
            case ENABLED  -> AllIcons.I_ACTIVE;    // green — runs with signal
            case DISABLED -> AllIcons.I_DISABLE;   // red   — stops with signal
        };
    }

    private Component buildRsModeTooltip() {
        int rsMode = menu.getRsMode();
        MinerRedstoneMode mode = MinerRedstoneMode.values()[
                Math.min(rsMode, MinerRedstoneMode.values().length - 1)];
        return Component.literal("Redstone: ")
                .append(Component.literal(mode.getDisplayName())
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  (" + mode.getDescription() + ")")
                        .withStyle(ChatFormatting.GRAY));
    }

    private void updateTooltipAndIcon() {
        if (rsModeButton != null) {
            rsModeButton.setIcon(getRedstoneIcon());
            rsModeButton.setToolTip(buildRsModeTooltip());
        }
    }
}