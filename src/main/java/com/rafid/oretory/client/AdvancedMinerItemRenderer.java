package com.rafid.oretory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rafid.oretory.AdvancedMinerBlockEntity;
import com.rafid.oretory.Oretory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class AdvancedMinerItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final AdvancedMinerItemRenderer INSTANCE = new AdvancedMinerItemRenderer();

    public AdvancedMinerItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack,
                             @NotNull ItemDisplayContext displayContext,
                             @NotNull PoseStack poseStack,
                             @NotNull MultiBufferSource buffer,
                             int packedLight,
                             int packedOverlay) {
        // Renders using the AdvancedMinerBlockEntity so it picks up
        // the ADVANCED_LAYER_LOCATION bake and advanced_miner.png texture
        Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(
                new AdvancedMinerBlockEntity(BlockPos.ZERO,
                        Oretory.ADVANCED_MINER_BLOCK.get().defaultBlockState()),
                poseStack, buffer, packedLight, packedOverlay
        );
    }
}