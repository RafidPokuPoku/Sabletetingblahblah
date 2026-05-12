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

/**
 * Packets for Oretory.
 *
 * CycleRsModePayload: sent by the client when the player clicks the
 * redstone-mode button in the Miner GUI. The server resolves the block pos
 * from the player's open container and cycles the mode on the BlockState.
 */
public class MinerPackets {

    // -------------------------------------------------------------------------
    // Packet: cycle redstone mode
    // -------------------------------------------------------------------------
    public record CycleRsModePayload(BlockPos pos) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<CycleRsModePayload> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(Oretory.MODID, "cycle_rs_mode"));

        public static final StreamCodec<FriendlyByteBuf, CycleRsModePayload> CODEC =
                StreamCodec.of(
                        (buf, pkt) -> buf.writeBlockPos(pkt.pos),
                        buf        -> new CycleRsModePayload(buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Oretory.MODID).versioned("1");

        registrar.playToServer(
                CycleRsModePayload.TYPE,
                CycleRsModePayload.CODEC,
                MinerPackets::handleCycleRsMode);
    }

    // -------------------------------------------------------------------------
    // Handler (runs on the server's main thread)
    // -------------------------------------------------------------------------
    private static void handleCycleRsMode(CycleRsModePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer serverPlayer)) return;

            BlockPos   pos   = payload.pos();
            var        level = serverPlayer.serverLevel();
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof MinerBlock)) return;

            // Security: only allow if this player actually has this Miner open
            if (!(serverPlayer.containerMenu instanceof MinerMenu)) return;

            MinerRedstoneMode next = state.getValue(MinerBlock.REDSTONE_MODE).next();
            level.setBlock(pos, state.setValue(MinerBlock.REDSTONE_MODE, next), 3);
            level.sendBlockUpdated(pos, state, level.getBlockState(pos), 3);

            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) be.setChanged();
        });
    }

    // -------------------------------------------------------------------------
    // Client-side helper — called from MinerScreen button click
    // -------------------------------------------------------------------------
    public static void sendCycleRedstoneMode() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        if (!(mc.player.containerMenu instanceof MinerMenu menu)) return;
        PacketDistributor.sendToServer(new CycleRsModePayload(menu.getBlockPos()));
    }
}