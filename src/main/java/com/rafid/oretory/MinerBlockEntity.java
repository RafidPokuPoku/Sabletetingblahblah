package com.rafid.oretory;

import com.rafid.oretory.client.MinerSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MinerBlockEntity extends BlockEntity implements MenuProvider {
    private static final TagKey<Block> ORES_TAG = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState miningAnimationState = new AnimationState();

    @Nullable private MinerSoundInstance idleSoundInstance;
    @Nullable private MinerSoundInstance miningSoundInstance;

    protected final ContainerData data;
    private int status = 0;

    private boolean ponderForceMining = false;
    private boolean ponderForceLit = false;

    public final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == 0) return stack.getBurnTime(null) > 0;
            return slot == 1;
        }
    };

    private final IItemHandlerModifiable fuelHandler = new ItemStackHandlerWrapper(inventory, 0, true, false);
    private final IItemHandlerModifiable outputHandler = new ItemStackHandlerWrapper(inventory, 1, false, true);
    private final CombinedInvWrapper combinedHandler = new CombinedInvWrapper(fuelHandler, outputHandler);

    private int miningProgress = 0;

    public MinerBlockEntity(BlockPos pos, BlockState state) {
        super(Oretory.MINER_BE.get(), pos, state);
        this.data = new ContainerData() {
            @Override public int get(int index) { return MinerBlockEntity.this.status; }
            @Override public void set(int index, int value) { MinerBlockEntity.this.status = value; }
            @Override public int getCount() { return 1; }
        };
    }

    public void setPonderMining(boolean mining) { this.ponderForceMining = mining; }
    public void setPonderLit(boolean lit) { this.ponderForceLit = lit; }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            handleClientTick(level, state);
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) return;

        ItemStack fuelStack = inventory.getStackInSlot(0);
        ItemStack outputStack = inventory.getStackInSlot(1);
        boolean hasFuel = !fuelStack.isEmpty();
        boolean isRedstonePowered = level.hasNeighborSignal(pos);

        BlockPos abovePos = pos.above();
        BlockPos belowPos = pos.below();
        boolean oreAbove = level.getBlockState(abovePos).is(ORES_TAG);
        boolean oreBelow = level.getBlockState(belowPos).is(ORES_TAG);

        List<ItemStack> totalDrops = new ArrayList<>();
        boolean canProcess = false;
        int activeDrills = 0;

        if (hasFuel && !isRedstonePowered) {
            if (oreAbove) {
                List<ItemStack> drops = getPotentialDrops(serverLevel, abovePos, level.getBlockState(abovePos));
                if (canFitInOutput(outputStack, drops)) {
                    totalDrops.addAll(drops);
                    activeDrills++;
                }
            }
            if (oreBelow) {
                List<ItemStack> drops = getPotentialDrops(serverLevel, belowPos, level.getBlockState(belowPos));
                ItemStack currentOutputOrFirstDrop = totalDrops.isEmpty() ? outputStack : totalDrops.get(0);
                if (canFitInOutput(currentOutputOrFirstDrop, drops)) {
                    totalDrops.addAll(drops);
                    activeDrills++;
                }
            }
            canProcess = activeDrills > 0;
        }

        boolean isCurrentlyMining = false;

        if (canProcess) {
            isCurrentlyMining = true;
            this.status = 2;

            if (level.getGameTime() % 2 == 0) {
                checkDrillCollision(level, pos);
            }

            int fuelValue = fuelStack.getBurnTime(null);
            int speedBoost = Math.max(1, (fuelValue / 100) * activeDrills);
            miningProgress += speedBoost;

            if (miningProgress >= 200) {
                for (ItemStack drop : totalDrops) {
                    this.inventory.insertItem(1, drop.copy(), false);
                }
                fuelStack.shrink(activeDrills);
                miningProgress = 0;
                setChanged();
            }
        } else {
            miningProgress = 0;
            this.status = hasFuel ? 1 : 0;
        }

        if (state.getValue(MinerBlock.MINING) != isCurrentlyMining || state.getValue(MinerBlock.LIT) != hasFuel) {
            level.setBlock(pos, state.setValue(MinerBlock.MINING, isCurrentlyMining).setValue(MinerBlock.LIT, hasFuel), 3);
            level.sendBlockUpdated(pos, state, level.getBlockState(pos), 3);
        }
    }

    private boolean canFitInOutput(ItemStack currentOutput, List<ItemStack> newDrops) {
        if (newDrops.isEmpty()) return false;
        ItemStack drop = newDrops.get(0);
        if (currentOutput.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(currentOutput, drop) &&
                currentOutput.getCount() + drop.getCount() <= currentOutput.getMaxStackSize();
    }

    private void checkDrillCollision(Level level, BlockPos pos) {
        AABB topDrill = new AABB(pos.getX(), pos.getY() + 1.0, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.1, pos.getZ() + 1.0);
        AABB bottomDrill = new AABB(pos.getX(), pos.getY() - 0.1, pos.getZ(), pos.getX() + 1.0, pos.getY(), pos.getZ() + 1.0);
        DamageSource drillDamage = level.damageSources().generic();

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, topDrill)) {
            entity.hurt(drillDamage, 1.0F);
        }
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bottomDrill)) {
            entity.hurt(drillDamage, 1.0F);
        }
    }

    private List<ItemStack> getPotentialDrops(ServerLevel level, BlockPos pos, BlockState state) {
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.NETHERITE_PICKAXE));
        return state.getDrops(builder);
    }

    private void handleClientTick(Level level, BlockState state) {
        int tickAge = (int) level.getGameTime();

        boolean isMining = ponderForceMining || state.getValue(MinerBlock.MINING);
        boolean isLit = ponderForceLit || state.getValue(MinerBlock.LIT);

        if (isMining) {
            if (!miningAnimationState.isStarted()) miningAnimationState.start(tickAge);
            idleAnimationState.stop();
            spawnParticles(level, worldPosition.above());
            spawnParticles(level, worldPosition.below());
        } else if (isLit) {
            if (!idleAnimationState.isStarted()) idleAnimationState.start(tickAge);
            miningAnimationState.stop();
        } else {
            idleAnimationState.stop();
            miningAnimationState.stop();
        }

        if (isLit && (idleSoundInstance == null || idleSoundInstance.isStopped())) {
            idleSoundInstance = new MinerSoundInstance(this, Oretory.MINER_IDLE.get(), false);
            Minecraft.getInstance().getSoundManager().play(idleSoundInstance);
        }
        if (isMining && (miningSoundInstance == null || miningSoundInstance.isStopped())) {
            miningSoundInstance = new MinerSoundInstance(this, Oretory.MINER_MINING.get(), true);
            Minecraft.getInstance().getSoundManager().play(miningSoundInstance);
        }
    }

    private void spawnParticles(Level level, BlockPos targetPos) {
        BlockState targetState = level.getBlockState(targetPos);
        if (!targetState.is(ORES_TAG)) return;
        for (int i = 0; i < 3; i++) {
            if (level.random.nextFloat() < 0.7f) {
                double px = targetPos.getX() + 0.05 + (level.random.nextDouble() * 0.9);
                double py = targetPos.getY() + (targetPos.getY() > worldPosition.getY() ? 0.0 : 1.0);
                double pz = targetPos.getZ() + 0.05 + (level.random.nextDouble() * 0.9);

                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, targetState), px, py, pz, (level.random.nextDouble() - 0.5) * 0.2, -0.15, (level.random.nextDouble() - 0.5) * 0.2);
            }
        }
    }

    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null) return inventory;
        if (side == Direction.UP) return fuelHandler;
        if (side == Direction.DOWN) return outputHandler;
        return combinedHandler;
    }

    @Override protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", this.inventory.serializeNBT(registries));
        tag.putInt("miningProgress", this.miningProgress);
        tag.putInt("minerStatus", this.status);
    }

    @Override protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        this.miningProgress = tag.getInt("miningProgress");
        this.status = tag.getInt("minerStatus");
    }

    @Override public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public @NotNull Component getDisplayName() { return Component.translatable("block.oretory.miner"); }

    @Nullable @Override public AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player p) {
        return new MinerMenu(id, inv, this.inventory, this.data);
    }

    private record ItemStackHandlerWrapper(ItemStackHandler handler, int slot, boolean canInsert, boolean canExtract) implements IItemHandlerModifiable {
        @Override public int getSlots() { return 1; }
        @Override @NotNull public ItemStack getStackInSlot(int s) { return handler.getStackInSlot(slot); }
        @Override public int getSlotLimit(int s) { return handler.getSlotLimit(slot); }
        @Override public boolean isItemValid(int s, @NotNull ItemStack stack) { return canInsert && handler.isItemValid(slot, stack); }
        @Override public void setStackInSlot(int s, @NotNull ItemStack stack) { handler.setStackInSlot(slot, stack); }
        @Override @NotNull public ItemStack insertItem(int s, @NotNull ItemStack stack, boolean simulate) { return canInsert ? handler.insertItem(slot, stack, simulate) : stack; }
        @Override @NotNull public ItemStack extractItem(int s, int amount, boolean simulate) { return canExtract ? handler.extractItem(slot, amount, simulate) : ItemStack.EMPTY; }
    }
}