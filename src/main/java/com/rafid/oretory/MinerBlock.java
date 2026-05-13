package com.rafid.oretory;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MinerBlock extends BaseEntityBlock implements IWrenchable {

    public static final MapCodec<MinerBlock> CODEC = simpleCodec(MinerBlock::new);

    public static final BooleanProperty LIT    = BooleanProperty.create("lit");
    public static final BooleanProperty MINING = BooleanProperty.create("mining");
    public static final EnumProperty<MinerRedstoneMode> REDSTONE_MODE =
            EnumProperty.create("redstone_mode", MinerRedstoneMode.class);

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    public MinerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(LIT,           false)
                .setValue(MINING,        false)
                .setValue(REDSTONE_MODE, MinerRedstoneMode.IGNORED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, MINING, REDSTONE_MODE);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!level.isClientSide) {
            MinerRedstoneMode next = state.getValue(REDSTONE_MODE).next();
            level.setBlock(pos, state.setValue(REDSTONE_MODE, next), 3);
            level.sendBlockUpdated(pos, state, level.getBlockState(pos), 3);

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinerBlockEntity minerBe) {
                minerBe.redstoneMode = next;
                minerBe.setChanged();
            }

            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(
            @NotNull ItemStack stack, @NotNull BlockState state, @NotNull Level level,
            @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
            @NotNull BlockHitResult hitResult) {

        if (stack.getItem() instanceof WrenchItem) {
            InteractionResult result = onWrenched(state, new UseOnContext(player, hand, hitResult));
            return result.consumesAction()
                    ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(
            @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull Player player, @NotNull BlockHitResult hitResult) {

        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinerBlockEntity minerBe) {
                // Write the BlockPos into the extra buf so the client menu knows where it is.
                player.openMenu(minerBe, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void neighborChanged(
            @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull Block neighborBlock, @NotNull BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinerBlockEntity) be.setChanged();
        }
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public void onRemove(
            @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinerBlockEntity miner) {
                for (int i = 0; i < miner.inventory.getSlots(); i++)
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                            miner.inventory.getStackInSlot(i));
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public void appendHoverText(
            @NotNull ItemStack stack, @NotNull Item.TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.literal("Drills ores above and below").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("1 fuel \u2192 1 output (instant burn)").withStyle(ChatFormatting.GRAY));

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Fuel quality controls speed & bonuses").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Premium fuels grant double-drop chances").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("TOP face: fuel input  |  BOTTOM: output").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Wrench (right-click): cycle Redstone mode").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Crouching players immune to drill damage").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Pauses automatically when output is full").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("[Hold Shift for details]").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new MinerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return createTickerHelper(type, Oretory.MINER_BE.get(),
                (lvl, pos, st, be) -> be.tick(lvl, pos, st));
    }
}