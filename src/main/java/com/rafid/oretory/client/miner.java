package com.rafid.oretory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rafid.oretory.AdvancedMinerBlockEntity;
import com.rafid.oretory.MinerBlockEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

// Fix: We use Entity as the base but pass MinerBlockEntity to the methods
public class miner<T extends Entity> extends HierarchicalModel<T> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("oretory", "miner"), "main");

	private final ModelPart root;

	public miner(ModelPart root) {
		this.root = root;
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition minerPart = partdefinition.addOrReplaceChild("Miner", CubeListBuilder.create(), PartPose.offset(0.75F, 24.0F, 0.0F));

		// Use names exactly as they appear in your animation files: "Body" and "Drill"
		minerPart.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(0, 20).addBox(-8.0F, -9.0F, -8.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.75F, -7.0F, 0.0F));

		PartDefinition drillPart = minerPart.addOrReplaceChild("Drill", CubeListBuilder.create(), PartPose.offset(-0.75F, -7.0F, 0.0F));

		drillPart.addOrReplaceChild("BotB_r1", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, -5.0F, -5.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.75F, 7.0F, 0.0F, 0.0F, 1.5708F, -0.7854F));
		drillPart.addOrReplaceChild("BotA_r1", CubeListBuilder.create().texOffs(0, -9).addBox(0.0F, -5.0F, -5.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 7.75F, 0.0F, -0.7854F, 0.0F, 0.0F));
		drillPart.addOrReplaceChild("TopB_r1", CubeListBuilder.create().texOffs(18, 0).addBox(0.0F, -5.0F, -5.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -8.25F, 0.0F, -0.7854F, 0.0F, 0.0F));
		drillPart.addOrReplaceChild("TopA_r1", CubeListBuilder.create().texOffs(18, -9).addBox(0.0F, -5.0F, -5.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.75F, -9.0F, 0.0F, 0.0F, 1.5708F, -0.7854F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	/** Called from MinerRenderer. */
	public void setupBlockEntityAnim(MinerBlockEntity bean, float ageInTicks) {
		this.root().getAllParts().forEach(ModelPart::resetPose);
		this.animate(bean.idleAnimationState, minerAnimationIdle.idle, ageInTicks);
		this.animate(bean.miningAnimationState, minerAnimationMining.mining, ageInTicks);
	}

	/** Called from AdvancedMinerRenderer — same animations, different block entity type. */
	public void setupBlockEntityAnim(AdvancedMinerBlockEntity bean, float ageInTicks) {
		this.root().getAllParts().forEach(ModelPart::resetPose);
		this.animate(bean.idleAnimationState, minerAnimationIdle.idle, ageInTicks);
		this.animate(bean.miningAnimationState, minerAnimationMining.mining, ageInTicks);
	}

	@Override
	public void setupAnim(@NotNull T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		// We leave this empty because we aren't an Entity
	}

	@Override
	public void renderToBuffer(@NotNull PoseStack poseStack, @NotNull VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}

	@Override
	public @NotNull ModelPart root() {
		return this.root;
	}
}