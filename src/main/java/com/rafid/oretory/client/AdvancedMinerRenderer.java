package com.rafid.oretory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rafid.oretory.AdvancedMinerBlockEntity;
import com.rafid.oretory.Oretory;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public class AdvancedMinerRenderer implements BlockEntityRenderer<AdvancedMinerBlockEntity> {

    // Reuses the same 'miner' model class as MinerRenderer.
    // Swap to a dedicated model later if you want different geometry.
    private final miner<Entity> model;
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Oretory.MODID, "textures/block/advanced_miner.png");

    public AdvancedMinerRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new miner<>(context.bakeLayer(miner.LAYER_LOCATION));
    }

    @Override
    public void render(@NotNull AdvancedMinerBlockEntity blockEntity, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {

        poseStack.pushPose();
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        float ageInTicks = 0;
        if (blockEntity.getLevel() != null)
            ageInTicks = blockEntity.getLevel().getGameTime() + partialTick;

        this.model.setupBlockEntityAnim(blockEntity, ageInTicks);

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, -1);

        poseStack.popPose();

        FilteringRenderer.renderOnBlockEntity(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }
}