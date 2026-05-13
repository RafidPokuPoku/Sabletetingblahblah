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

public class MinerPackets {

    public record CycleRsModePayload(BlockPos pos) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<CycleRsModePayload> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(Oretory.MODID, "cycle_rs_mode"));

        public static final StreamCodec<FriendlyByteBuf, CycleRsModePayload> CODEC =
                StreamCodec.of(
                        (buf, pkt) -> buf.writeBlockPos(pkt.pos),
                        buf -> new CycleRsModePayload(buf.readBlockPos()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SetThresholdPayload(BlockPos pos, int threshold) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SetThresholdPayload> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(Oretory.MODID, "set_threshold"));

        public static final StreamCodec<FriendlyByteBuf, SetThresholdPayload> CODEC =
                StreamCodec.of(
                        (buf, pkt) -> { buf.writeBlockPos(pkt.pos); buf.writeVarInt(pkt.threshold); },
                        buf -> new SetThresholdPayload(buf.readBlockPos(), buf.readVarInt()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Oretory.MODID).versioned("1");

        registrar.playToServer(
                CycleRsModePayload.TYPE,
                CycleRsModePayload.CODEC,
                MinerPackets::handleCycleRsMode);

        registrar.playToServer(
                SetThresholdPayload.TYPE,
                SetThresholdPayload.CODEC,
                MinerPackets::handleSetThreshold);
    }

    private static void handleCycleRsMode(CycleRsModePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) return;

            BlockPos pos = payload.pos();
            var level = serverPlayer.serverLevel();
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof MinerBlock)) return;
            // No containerMenu check here — wrench can be used outside the GUI
            if (!(serverPlayer.containerMenu instanceof MinerMenu)) return;

            MinerRedstoneMode next = state.getValue(MinerBlock.REDSTONE_MODE).next();
            level.setBlock(pos, state.setValue(MinerBlock.REDSTONE_MODE, next), 3);
            level.sendBlockUpdated(pos, state, level.getBlockState(pos), 3);

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinerBlockEntity minerBe) {
                minerBe.redstoneMode = next;
                minerBe.setChanged();
            }
        });
    }

    private static void handleSetThreshold(SetThresholdPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) return;
            // No containerMenu check — GUI may already be closed when packet arrives.
            BlockPos pos = payload.pos();
            var level = serverPlayer.serverLevel();
            if (serverPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 * 64) return;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinerBlockEntity minerBe) {
                minerBe.setOutputThreshold(payload.threshold());
            }
        });
    }

    public static void sendCycleRedstoneMode() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.player.containerMenu instanceof MinerMenu menu)) return;
        PacketDistributor.sendToServer(new CycleRsModePayload(menu.getBlockPos()));
    }

    public static void sendSetThreshold(int threshold) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.player.containerMenu instanceof MinerMenu menu)) return;
        PacketDistributor.sendToServer(new SetThresholdPayload(menu.getBlockPos(), threshold));
    }
}