package com.rafid.oretory.client;

import com.rafid.oretory.MinerMenu;
import com.rafid.oretory.Oretory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class MinerScreen extends AbstractContainerScreen<MinerMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Oretory.MODID, "textures/gui/miner_gui.png");

    public MinerScreen(MinerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;

        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Render the main Title
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);

        // --- NEW STATUS TEXT ---
        Component statusComponent = getStatusComponent();
        int statusColor = getStatusColor();

        // We render it to the right of the "Miner" title
        // 8 (titleLabelX) + title width + 5 pixels of padding
        int statusX = this.titleLabelX + this.font.width(this.title) + 5;
        guiGraphics.drawString(this.font, statusComponent, statusX, this.titleLabelY, statusColor, false);
        // -----------------------

        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    private Component getStatusComponent() {
        return switch (this.menu.getStatus()) {
            case 2 -> Component.literal("- ACTIVE");
            case 1 -> Component.literal("- IDLE");
            default -> Component.literal("- OFF");
        };
    }

    private int getStatusColor() {
        return switch (this.menu.getStatus()) {
            case 2 -> 0x00FF00; // Green
            case 1 -> 0xFFFF00; // Yellow
            default -> 0xAAAAAA; // Gray
        };
    }
}