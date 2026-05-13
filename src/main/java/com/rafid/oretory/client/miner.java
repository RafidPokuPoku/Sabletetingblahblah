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

public class miner<T extends Entity> extends HierarchicalModel<T> {

	// -------------------------------------------------------------------------
	// Layer locations — one per block type so each gets its own UV bake
	// -------------------------------------------------------------------------
	public static final ModelLayerLocation LAYER_LOCATION =
			new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("oretory", "miner"), "main");

	public static final ModelLayerLocation ADVANCED_LAYER_LOCATION =
			new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("oretory", "advanced_miner"), "main");

	// -------------------------------------------------------------------------
	// Model root
	// -------------------------------------------------------------------------
	private final ModelPart root;

	public miner(ModelPart root) {
		this.root = root;
	}

	// -------------------------------------------------------------------------
	// Layer definition — Miner (uses miner.png UVs from your Blockbench export)
	// -------------------------------------------------------------------------
	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition minerPart = partdefinition.addOrReplaceChild(
				"Miner", CubeListBuilder.create(), PartPose.offset(0.75F, 24.0F, 0.0F));

		minerPart.addOrReplaceChild("Body",
				CubeListBuilder.create()
						.texOffs(0, 32)
						.addBox(-8.0F, -9.0F, -8.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F)),
				PartPose.offset(-0.75F, -7.0F, 0.0F));

		PartDefinition drillPart = minerPart.addOrReplaceChild(
				"Drill", CubeListBuilder.create(), PartPose.offset(-0.75F, -7.0F, 0.0F));

		drillPart.addOrReplaceChild("BotB_r1",
				CubeListBuilder.create()
						.texOffs(30, -9)
						.addBox(0.0F, -5.0F, -5.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.75F, 7.0F, 0.0F, 0.0F, 1.5708F, -0.7854F));

		drillPart.addOrReplaceChild("BotA_r1",
				CubeListBuilder.create()
						.texOffs(30, -9)
						.addBox(0.0F, -5.0F, -5.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 7.75F, 0.0F, -0.7854F, 0.0F, 0.0F));

		drillPart.addOrReplaceChild("TopB_r1",
				CubeListBuilder.create()
						.texOffs(30, -9).mirror()
						.addBox(0.0F, -5.0F, -5.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F))
						.mirror(false),
				PartPose.offsetAndRotation(0.0F, -8.25F, 0.0F, -0.7854F, 0.0F, 0.0F));

		drillPart.addOrReplaceChild("TopA_r1",
				CubeListBuilder.create()
						.texOffs(30, -9)
						.addBox(0.0F, -5.0F, -5.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.75F, -9.0F, 0.0F, 0.0F, 1.5708F, -0.7854F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	// -------------------------------------------------------------------------
	// Layer definition — Advanced Miner (uses advanced_miner.png UVs)
	// If your advanced miner texture has different UV offsets, change the
	// texOffs(...) values below to match what Blockbench exported for it.
	// Right now they mirror the standard miner export since both files were
	// identical — update them once you have a proper advanced export.
	// -------------------------------------------------------------------------
	public static LayerDefinition createAdvancedBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition minerPart = partdefinition.addOrReplaceChild(
				"Miner", CubeListBuilder.create(), PartPose.offset(0.75F, 24.0F, 0.0F));

		minerPart.addOrReplaceChild("Body",
				CubeListBuilder.create()
						.texOffs(0, 32)
						.addBox(-8.0F, -9.0F, -8.0F, 16.0F, 16.0F, 16.0F, new CubeDeformation(0.0F)),
				PartPose.offset(-0.75F, -7.0F, 0.0F));

		PartDefinition drillPart = minerPart.addOrReplaceChild(
				"Drill", CubeListBuilder.create(), PartPose.offset(-0.75F, -7.0F, 0.0F));

		drillPart.addOrReplaceChild("BotB_r1",
				CubeListBuilder.create()
						.texOffs(30, -9)
						.addBox(0.0F, -5.0F, -5.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.75F, 7.0F, 0.0F, 0.0F, 1.5708F, -0.7854F));

		drillPart.addOrReplaceChild("BotA_r1",
				CubeListBuilder.create()
						.texOffs(30, -9)
						.addBox(0.0F, -5.0F, -5.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 7.75F, 0.0F, -0.7854F, 0.0F, 0.0F));

		drillPart.addOrReplaceChild("TopB_r1",
				CubeListBuilder.create()
						.texOffs(30, -9).mirror()
						.addBox(0.0F, -5.0F, -5.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F))
						.mirror(false),
				PartPose.offsetAndRotation(0.0F, -8.25F, 0.0F, -0.7854F, 0.0F, 0.0F));

		drillPart.addOrReplaceChild("TopA_r1",
				CubeListBuilder.create()
						.texOffs(30, -9)
						.addBox(0.0F, -5.0F, -5.0F, 0.0F, 9.0F, 9.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.75F, -9.0F, 0.0F, 0.0F, 1.5708F, -0.7854F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	// -------------------------------------------------------------------------
	// Animation setup — called from renderers
	// -------------------------------------------------------------------------

	/** Called from MinerRenderer. */
	public void setupBlockEntityAnim(MinerBlockEntity bean, float ageInTicks) {
		this.root().getAllParts().forEach(ModelPart::resetPose);
		this.animate(bean.idleAnimationState,   minerAnimationIdle.idle,     ageInTicks);
		this.animate(bean.miningAnimationState, minerAnimationMining.mining, ageInTicks);
	}

	/** Called from AdvancedMinerRenderer — same animations, different block entity type. */
	public void setupBlockEntityAnim(AdvancedMinerBlockEntity bean, float ageInTicks) {
		this.root().getAllParts().forEach(ModelPart::resetPose);
		this.animate(bean.idleAnimationState,   minerAnimationIdle.idle,     ageInTicks);
		this.animate(bean.miningAnimationState, minerAnimationMining.mining, ageInTicks);
	}

	// -------------------------------------------------------------------------
	// HierarchicalModel overrides
	// -------------------------------------------------------------------------

	@Override
	public void setupAnim(@NotNull T entity, float limbSwing, float limbSwingAmount,
	                      float ageInTicks, float netHeadYaw, float headPitch) {
		// Intentionally empty — driven by block entity animation states, not entity motion
	}

	@Override
	public void renderToBuffer(@NotNull PoseStack poseStack, @NotNull VertexConsumer vertexConsumer,
	                           int packedLight, int packedOverlay, int color) {
		this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}

	@Override
	public @NotNull ModelPart root() {
		return this.root;
	}
}