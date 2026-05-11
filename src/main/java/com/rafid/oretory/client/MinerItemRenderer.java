package com.rafid.oretory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rafid.oretory.MinerBlockEntity;
import com.rafid.oretory.Oretory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class MinerItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final MinerItemRenderer INSTANCE = new MinerItemRenderer();

    public MinerItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext displayContext, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // We render a dummy Block Entity that holds the iron texture
        Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(
                new MinerBlockEntity(BlockPos.ZERO, Oretory.MINER_BLOCK.get().defaultBlockState()),
                poseStack, buffer, packedLight, packedOverlay
        );
    }
}