package com.rafid.oretory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class AdvancedMinerPackets {

    // -------------------------------------------------------------------------
    // Cycle redstone mode
    // -------------------------------------------------------------------------
    public record CycleRsModePayload(BlockPos pos) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CycleRsModePayload> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(Oretory.MODID, "adv_cycle_rs_mode"));

        public static final StreamCodec<FriendlyByteBuf, CycleRsModePayload> CODEC =
                StreamCodec.of(
                        (buf, pkt) -> buf.writeBlockPos(pkt.pos),
                        buf -> new CycleRsModePayload(buf.readBlockPos()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // -------------------------------------------------------------------------
    // Set bottom output threshold
    // -------------------------------------------------------------------------
    public record SetThresholdBotPayload(BlockPos pos, int threshold) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetThresholdBotPayload> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(Oretory.MODID, "adv_set_threshold_bot"));

        public static final StreamCodec<FriendlyByteBuf, SetThresholdBotPayload> CODEC =
                StreamCodec.of(
                        (buf, pkt) -> { buf.writeBlockPos(pkt.pos); buf.writeVarInt(pkt.threshold); },
                        buf -> new SetThresholdBotPayload(buf.readBlockPos(), buf.readVarInt()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // -------------------------------------------------------------------------
    // Set top output threshold
    // -------------------------------------------------------------------------
    public record SetThresholdTopPayload(BlockPos pos, int threshold) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetThresholdTopPayload> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(Oretory.MODID, "adv_set_threshold_top"));

        public static final StreamCodec<FriendlyByteBuf, SetThresholdTopPayload> CODEC =
                StreamCodec.of(
                        (buf, pkt) -> { buf.writeBlockPos(pkt.pos); buf.writeVarInt(pkt.threshold); },
                        buf -> new SetThresholdTopPayload(buf.readBlockPos(), buf.readVarInt()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Oretory.MODID).versioned("1");

        registrar.playToServer(
                CycleRsModePayload.TYPE,
                CycleRsModePayload.CODEC,
                AdvancedMinerPackets::handleCycleRsMode);

        registrar.playToServer(
                SetThresholdBotPayload.TYPE,
                SetThresholdBotPayload.CODEC,
                AdvancedMinerPackets::handleSetThresholdBot);

        registrar.playToServer(
                SetThresholdTopPayload.TYPE,
                SetThresholdTopPayload.CODEC,
                AdvancedMinerPackets::handleSetThresholdTop);
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------
    private static void handleCycleRsMode(CycleRsModePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) return;
            if (!(serverPlayer.containerMenu instanceof AdvancedMinerMenu)) return;

            BlockPos pos = payload.pos();
            var level    = serverPlayer.serverLevel();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof AdvancedMinerBlock)) return;

            MinerRedstoneMode next = state.getValue(AdvancedMinerBlock.REDSTONE_MODE).next();
            level.setBlock(pos, state.setValue(AdvancedMinerBlock.REDSTONE_MODE, next), 3);
            level.sendBlockUpdated(pos, state, level.getBlockState(pos), 3);

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AdvancedMinerBlockEntity minerBe) {
                minerBe.redstoneMode = next;
                minerBe.setChanged();
            }
        });
    }

    private static void handleSetThresholdBot(SetThresholdBotPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) return;
            BlockPos pos = payload.pos();
            var level    = serverPlayer.serverLevel();
            if (serverPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) return;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AdvancedMinerBlockEntity minerBe)
                minerBe.setOutputThresholdBottom(payload.threshold());
        });
    }

    private static void handleSetThresholdTop(SetThresholdTopPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) return;
            BlockPos pos = payload.pos();
            var level    = serverPlayer.serverLevel();
            if (serverPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) return;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AdvancedMinerBlockEntity minerBe)
                minerBe.setOutputThresholdTop(payload.threshold());
        });
    }

    // -------------------------------------------------------------------------
    // Client-side send helpers
    // -------------------------------------------------------------------------
    public static void sendCycleRedstoneMode() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.player.containerMenu instanceof AdvancedMinerMenu menu)) return;
        PacketDistributor.sendToServer(new CycleRsModePayload(menu.getBlockPos()));
    }

    public static void sendSetThresholdBot(int threshold) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.player.containerMenu instanceof AdvancedMinerMenu menu)) return;
        PacketDistributor.sendToServer(new SetThresholdBotPayload(menu.getBlockPos(), threshold));
    }

    public static void sendSetThresholdTop(int threshold) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.player.containerMenu instanceof AdvancedMinerMenu menu)) return;
        PacketDistributor.sendToServer(new SetThresholdTopPayload(menu.getBlockPos(), threshold));
    }
}