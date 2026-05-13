package com.rafid.oretory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rafid.oretory.MinerBlockEntity;
import com.rafid.oretory.Oretory;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public class MinerRenderer implements BlockEntityRenderer<MinerBlockEntity> {

    private final miner<Entity> model;
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Oretory.MODID, "textures/block/miner.png");

    public MinerRenderer(BlockEntityRendererProvider.Context context) {
        // Bakes with the standard miner layer — uses miner.png UVs
        this.model = new miner<>(context.bakeLayer(miner.LAYER_LOCATION));
    }

    @Override
    public void render(@NotNull MinerBlockEntity blockEntity, float partialTick,
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

        // Filter item render — must be outside pushPose/popPose
        FilteringRenderer.renderOnBlockEntity(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }
}