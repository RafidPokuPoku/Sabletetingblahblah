package com.rafid.oretory;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
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

        if (hand == InteractionHand.MAIN_HAND && stack.getItem() instanceof WrenchItem) {
            InteractionResult result = onWrenched(state, new UseOnContext(player, hand, hitResult));
            return result.consumesAction()
                    ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (hand == InteractionHand.MAIN_HAND && isBrassIngot(stack)) {
            if (!player.isShiftKeyDown()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                upgradeToAdvancedMiner(level, pos, state, stack, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private void upgradeToAdvancedMiner(Level level, BlockPos pos, BlockState oldState,
                                        ItemStack heldStack, Player player) {
        BlockEntity oldBe = level.getBlockEntity(pos);
        if (!(oldBe instanceof MinerBlockEntity minerBe)) return;

        MinerRedstoneMode savedMode   = minerBe.redstoneMode;
        ItemStack         savedFuel   = minerBe.inventory.getStackInSlot(0).copy();
        ItemStack         savedOutput = minerBe.inventory.getStackInSlot(1).copy();
        ItemStack         savedFilter = minerBe.filter != null
                ? minerBe.filter.getFilter().copy()
                : ItemStack.EMPTY;

        BlockState newState = Oretory.ADVANCED_MINER_BLOCK.get().defaultBlockState()
                .setValue(AdvancedMinerBlock.REDSTONE_MODE, savedMode)
                .setValue(AdvancedMinerBlock.MINING, false);

        level.setBlock(pos, newState, 3);
        level.sendBlockUpdated(pos, oldState, newState, 3);

        BlockEntity newBe = level.getBlockEntity(pos);
        if (newBe instanceof AdvancedMinerBlockEntity advBe) {
            advBe.redstoneMode = savedMode;

            if (!savedFuel.isEmpty()) {
                ItemStack rem = advBe.inventory.insertItem(0, savedFuel, false);
                if (!rem.isEmpty())
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), rem);
            }
            if (!savedOutput.isEmpty()) {
                ItemStack rem = advBe.inventory.insertItem(1, savedOutput, false);
                if (!rem.isEmpty())
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), rem);
            }

            advBe.recalculateFilterFace();
            advBe.setChanged();
        }

        if (!savedFilter.isEmpty())
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), savedFilter);

        if (!player.getAbilities().instabuild)
            heldStack.shrink(1);

        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
        player.displayClientMessage(
                Component.literal("Upgraded to Brass Miner!").withStyle(ChatFormatting.GOLD), true);
    }

    private static boolean isBrassIngot(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ResourceLocation.fromNamespaceAndPath("create", "brass_ingot").equals(key);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(
            @NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull Player player, @NotNull BlockHitResult hitResult) {

        if (player.isShiftKeyDown()) return InteractionResult.PASS;

        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinerBlockEntity minerBe) {
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
            if (be instanceof MinerBlockEntity minerBe) {
                boolean faceChanged = minerBe.recalculateFilterFace();
                if (faceChanged)
                    level.sendBlockUpdated(pos, state, level.getBlockState(pos), 3);
                minerBe.setChanged();
            }
        }
    }

    @Override
    public void setPlacedBy(
            @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinerBlockEntity minerBe) {
                minerBe.recalculateFilterFace();
                minerBe.setChanged();
            }
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
                if (miner.filter != null) {
                    ItemStack filterItem = miner.filter.getFilter();
                    if (!filterItem.isEmpty())
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), filterItem);
                }
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
        tooltip.add(Component.literal("Shift + Right-click with Brass Ingot to upgrade").withStyle(ChatFormatting.YELLOW));
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Fuel quality controls speed & bonuses").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Premium fuels grant double-drop chances").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("TOP face: fuel input  |  BOTTOM: output").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Wrench (right-click): cycle Redstone mode").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Fuel filter: auto-placed on a free side face").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Filter moves if the side is blocked by a block/funnel").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.literal("Crouching players immune to drill damage").withStyle(ChatFormatting.DARK_GRAY));
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