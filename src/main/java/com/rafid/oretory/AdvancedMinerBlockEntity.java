package com.rafid.oretory;

import com.rafid.oretory.client.MinerSoundInstance;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BrassFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdvancedMinerBlockEntity extends SmartBlockEntity
        implements MenuProvider, IHaveGoggleInformation {

    // -------------------------------------------------------------------------
    // Status constants (same as MinerBlockEntity)
    // -------------------------------------------------------------------------
    public static final int STATUS_OFF         = 0;
    public static final int STATUS_IDLE        = 1;
    public static final int STATUS_MINING      = 2;
    public static final int STATUS_OUTPUT_FULL = 3;
    public static final int STATUS_NO_ORE      = 4;

    // -------------------------------------------------------------------------
    // Speed multiplier over the basic Miner (1.5x faster = 0.667x ticks)
    // -------------------------------------------------------------------------
    private static final float SPEED_MULTIPLIER = 1.5f;

    // -------------------------------------------------------------------------
    // Ore color palette
    // -------------------------------------------------------------------------
    private static final TagKey<Block> ORES_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));

    private static final Map<String, Integer> ORE_COLORS = new HashMap<>();
    static {
        ORE_COLORS.put("iron_ore",               0xC0C0C0);
        ORE_COLORS.put("deepslate_iron_ore",      0xA0A0A0);
        ORE_COLORS.put("gold_ore",               0xFFD700);
        ORE_COLORS.put("deepslate_gold_ore",      0xCCAA00);
        ORE_COLORS.put("diamond_ore",            0x00FFFF);
        ORE_COLORS.put("deepslate_diamond_ore",   0x00CCCC);
        ORE_COLORS.put("emerald_ore",            0x00CC44);
        ORE_COLORS.put("deepslate_emerald_ore",   0x009933);
        ORE_COLORS.put("redstone_ore",           0xFF0000);
        ORE_COLORS.put("deepslate_redstone_ore",  0xCC0000);
        ORE_COLORS.put("lapis_ore",              0x1155CC);
        ORE_COLORS.put("deepslate_lapis_ore",     0x0033AA);
        ORE_COLORS.put("coal_ore",               0x333333);
        ORE_COLORS.put("deepslate_coal_ore",      0x222222);
        ORE_COLORS.put("copper_ore",             0xCC6633);
        ORE_COLORS.put("deepslate_copper_ore",    0xAA5522);
        ORE_COLORS.put("nether_gold_ore",         0xFFAA00);
        ORE_COLORS.put("nether_quartz_ore",       0xFFEECC);
        ORE_COLORS.put("ancient_debris",          0x8B4513);
    }

    private static final Direction[] HORIZONTAL_SIDES = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    // -------------------------------------------------------------------------
    // Animation states (client-only)
    // -------------------------------------------------------------------------
    public final AnimationState idleAnimationState   = new AnimationState();
    public final AnimationState miningAnimationState = new AnimationState();

    @Nullable private MinerSoundInstance idleSoundInstance;
    @Nullable private MinerSoundInstance miningSoundInstance;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private int status         = STATUS_OFF;
    private int miningProgress = 0;
    private int noOreTicks     = 0;
    MinerRedstoneMode redstoneMode = MinerRedstoneMode.IGNORED;

    private int outputThresholdBottom = 1;
    private int outputThresholdTop    = 1;

    private boolean ponderForceMining = false;

    @Nullable private transient FuelTier cachedFuelTier = null;

    private int roundRobinIndexBottom = 0;
    private int roundRobinIndexTop    = 0;

    @Nullable private Direction filterFace = Direction.NORTH;
    public FilteringBehaviour filter;

    // =========================================================================
    // FILTER SLOT TRANSFORM
    // =========================================================================
    public static class AdvancedMinerFilterSlot extends CenteredSideValueBoxTransform {
        private final AdvancedMinerBlockEntity owner;

        public AdvancedMinerFilterSlot(AdvancedMinerBlockEntity owner) {
            super((state, dir) -> true);
            this.owner = owner;
        }

        @Override
        public Direction getSide() {
            return owner.filterFace != null ? owner.filterFace : Direction.NORTH;
        }

        @Override
        public boolean shouldRender(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state) {
            return owner.filterFace != null;
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction == owner.filterFace;
        }
    }

    // =========================================================================
    // INVENTORY
    // Slot 0 = fuel, Slot 1 = bottom ore output, Slot 2 = top ore output
    // =========================================================================
    public final ItemStackHandler inventory = new ItemStackHandler(3) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == 0) cachedFuelTier = null;
        }
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == 0) {
                if (!FuelTier.isValidFuel(stack)) return false;
                return passesFuelFilter(stack);
            }
            return slot == 1 || slot == 2;
        }
    };

    private final IItemHandlerModifiable fuelHandler =
            new ItemStackHandlerWrapper(inventory, 0, true, false);

    private final IItemHandlerModifiable outputHandlerBottom = new IItemHandlerModifiable() {
        @Override public int  getSlots()                                      { return 1; }
        @Override public int  getSlotLimit(int s)                             { return inventory.getSlotLimit(1); }
        @Override public boolean isItemValid(int s, @NotNull ItemStack stack) { return false; }
        @Override public @NotNull ItemStack getStackInSlot(int s)             { return inventory.getStackInSlot(1); }
        @Override public void setStackInSlot(int s, @NotNull ItemStack stack) { inventory.setStackInSlot(1, stack); }
        @Override public @NotNull ItemStack insertItem(int s, @NotNull ItemStack stack, boolean sim) { return stack; }
        @Override public @NotNull ItemStack extractItem(int s, int amount, boolean sim) {
            ItemStack current = inventory.getStackInSlot(1);
            if (current.isEmpty() || current.getCount() < outputThresholdBottom) return ItemStack.EMPTY;
            return inventory.extractItem(1, amount, sim);
        }
    };

    private final IItemHandlerModifiable outputHandlerTop = new IItemHandlerModifiable() {
        @Override public int  getSlots()                                      { return 1; }
        @Override public int  getSlotLimit(int s)                             { return inventory.getSlotLimit(2); }
        @Override public boolean isItemValid(int s, @NotNull ItemStack stack) { return false; }
        @Override public @NotNull ItemStack getStackInSlot(int s)             { return inventory.getStackInSlot(2); }
        @Override public void setStackInSlot(int s, @NotNull ItemStack stack) { inventory.setStackInSlot(2, stack); }
        @Override public @NotNull ItemStack insertItem(int s, @NotNull ItemStack stack, boolean sim) { return stack; }
        @Override public @NotNull ItemStack extractItem(int s, int amount, boolean sim) {
            ItemStack current = inventory.getStackInSlot(2);
            if (current.isEmpty() || current.getCount() < outputThresholdTop) return ItemStack.EMPTY;
            return inventory.extractItem(2, amount, sim);
        }
    };

    private final CombinedInvWrapper combinedHandler =
            new CombinedInvWrapper(fuelHandler, outputHandlerBottom, outputHandlerTop);

    // =========================================================================
    // ContainerData — synced to AdvancedMinerMenu
    // =========================================================================
    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case AdvancedMinerMenu.DATA_STATUS          -> AdvancedMinerBlockEntity.this.status;
                case AdvancedMinerMenu.DATA_PROGRESS        -> AdvancedMinerBlockEntity.this.miningProgress;
                case AdvancedMinerMenu.DATA_RSMODE          -> AdvancedMinerBlockEntity.this.redstoneMode.ordinal();
                case AdvancedMinerMenu.DATA_SPEED           -> {
                    FuelTier t = AdvancedMinerBlockEntity.this.cachedFuelTier;
                    yield t == null ? 0 : (int)(t.speedMultiplier * 100);
                }
                case AdvancedMinerMenu.DATA_THRESHOLD_BOT   -> AdvancedMinerBlockEntity.this.outputThresholdBottom;
                case AdvancedMinerMenu.DATA_THRESHOLD_TOP   -> AdvancedMinerBlockEntity.this.outputThresholdTop;
                default -> 0;
            };
        }
        @Override
        public void set(int index, int value) {
            if (index == AdvancedMinerMenu.DATA_STATUS)   AdvancedMinerBlockEntity.this.status         = value;
            if (index == AdvancedMinerMenu.DATA_PROGRESS) AdvancedMinerBlockEntity.this.miningProgress = value;
            if (index == AdvancedMinerMenu.DATA_RSMODE)
                AdvancedMinerBlockEntity.this.redstoneMode =
                        MinerRedstoneMode.values()[Math.max(0, Math.min(value, MinerRedstoneMode.values().length - 1))];
        }
        @Override
        public int getCount() { return AdvancedMinerMenu.DATA_COUNT; }
    };

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    public AdvancedMinerBlockEntity(BlockPos pos, BlockState state) {
        super(Oretory.ADVANCED_MINER_BE.get(), pos, state);
        this.redstoneMode = state.getValue(AdvancedMinerBlock.REDSTONE_MODE);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        filter = new FilteringBehaviour(this, new AdvancedMinerFilterSlot(this))
                .withCallback(stack -> setChanged());
        behaviours.add(filter);
    }

    // =========================================================================
    // DYNAMIC FILTER FACE
    // =========================================================================
    public boolean recalculateFilterFace() {
        Level level = getLevel();
        if (level == null) return false;
        Direction bestFace = null;
        for (Direction side : HORIZONTAL_SIDES) {
            if (isFaceFreeForFilter(level, worldPosition, side)) { bestFace = side; break; }
        }
        if (bestFace == filterFace) return false;
        filterFace = bestFace;
        setChanged();
        return true;
    }

    private boolean isFaceFreeForFilter(Level level, BlockPos pos, Direction side) {
        BlockPos   neighborPos   = pos.relative(side);
        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.isAir()) return true;
        if (neighborState.getBlock() instanceof FunnelBlock) return false;
        if (neighborState.isRedstoneConductor(level, neighborPos)) return false;
        if (!neighborState.getBlockSupportShape(level, neighborPos).isEmpty()) return false;
        return true;
    }

    // =========================================================================
    // SERVER TICK
    // =========================================================================
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) { handleClientTick(level, state); return; }
        if (!(level instanceof ServerLevel serverLevel)) return;

        // --- Fuel (needs 2 per cycle) ---
        ItemStack fuelStack = inventory.getStackInSlot(0);
        boolean hasFuel = !fuelStack.isEmpty() && FuelTier.isValidFuel(fuelStack) && fuelStack.getCount() >= 2;
        if (hasFuel && cachedFuelTier == null) cachedFuelTier = FuelTier.getTier(fuelStack);
        else if (!hasFuel) cachedFuelTier = null;

        // --- Redstone gate ---
        boolean hasSignal         = level.hasNeighborSignal(pos);
        boolean allowedByRedstone = switch (redstoneMode) {
            case IGNORED  -> true;
            case ENABLED  -> hasSignal;
            case DISABLED -> !hasSignal;
        };

        // --- Ore detection ---
        BlockPos   abovePos   = pos.above();
        BlockPos   belowPos   = pos.below();
        BlockState aboveState = level.getBlockState(abovePos);
        BlockState belowState = level.getBlockState(belowPos);
        boolean oreAbove = aboveState.is(ORES_TAG);
        boolean oreBelow = belowState.is(ORES_TAG);
        boolean hasOre   = oreAbove || oreBelow;

        // --- No-ore timeout ---
        int noOreTimeout = OretoryConfig.NO_ORE_TIMEOUT_TICKS.get();
        if (!hasOre && hasFuel && allowedByRedstone && noOreTimeout > 0) noOreTicks++;
        else noOreTicks = 0;
        boolean timedOut = noOreTimeout > 0 && noOreTicks >= noOreTimeout;

        // --- Output-full guard: blocked only if the relevant output slot(s) are full ---
        ItemStack outputBottom = inventory.getStackInSlot(1);
        ItemStack outputTop    = inventory.getStackInSlot(2);
        boolean bottomFull = !outputBottom.isEmpty() && outputBottom.getCount() >= outputBottom.getMaxStackSize();
        boolean topFull    = !outputTop.isEmpty()    && outputTop.getCount()    >= outputTop.getMaxStackSize();
        boolean outputFull;
        if (oreAbove && oreBelow) {
            outputFull = bottomFull && topFull;
        } else if (oreBelow) {
            outputFull = bottomFull;
        } else if (oreAbove) {
            outputFull = topFull;
        } else {
            outputFull = false;
        }

        // --- Main processing ---
        boolean canProcess        = hasFuel && allowedByRedstone && hasOre && !outputFull;
        boolean isCurrentlyMining = false;

        if (canProcess) {
            isCurrentlyMining = true;
            this.status       = STATUS_MINING;
            noOreTicks        = 0;

            FuelTier tier = cachedFuelTier;
            int baseTicksPerCycle = OretoryConfig.BASE_TICKS_PER_CYCLE.get();
            int ticksPerCycle = tier != null
                    ? Math.max(20, (int)(tier.getTicksPerCycle() / SPEED_MULTIPLIER))
                    : Math.max(20, (int)(baseTicksPerCycle / SPEED_MULTIPLIER));

            if (level.getGameTime() % 2 == 0 && OretoryConfig.ENABLE_ENTITY_DAMAGE.get())
                checkDrillCollision(level, pos, tier);

            miningProgress++;

            if (miningProgress >= ticksPerCycle) {
                miningProgress = 0;

                if (oreAbove && oreBelow) {
                    List<ItemStack> dropsBelow = getPotentialDrops(serverLevel, belowPos, belowState);
                    List<ItemStack> dropsAbove = getPotentialDrops(serverLevel, abovePos, aboveState);
                    if (tier != null && tier.doubleDropChance > 0f && level.random.nextFloat() < tier.doubleDropChance) {
                        dropsBelow.addAll(new ArrayList<>(dropsBelow));
                        dropsAbove.addAll(new ArrayList<>(dropsAbove));
                    }
                    insertDrops(dropsBelow, 1, level, pos);
                    insertDrops(dropsAbove, 2, level, pos);

                } else if (oreBelow) {
                    List<ItemStack> drops  = getPotentialDrops(serverLevel, belowPos, belowState);
                    List<ItemStack> drops2 = getPotentialDrops(serverLevel, belowPos, belowState);
                    if (tier != null && tier.doubleDropChance > 0f && level.random.nextFloat() < tier.doubleDropChance) {
                        drops.addAll(new ArrayList<>(drops));
                        drops2.addAll(new ArrayList<>(drops2));
                    }
                    insertDrops(drops,  1, level, pos);
                    insertDrops(drops2, 1, level, pos);

                } else { // oreAbove only
                    List<ItemStack> drops  = getPotentialDrops(serverLevel, abovePos, aboveState);
                    List<ItemStack> drops2 = getPotentialDrops(serverLevel, abovePos, aboveState);
                    if (tier != null && tier.doubleDropChance > 0f && level.random.nextFloat() < tier.doubleDropChance) {
                        drops.addAll(new ArrayList<>(drops));
                        drops2.addAll(new ArrayList<>(drops2));
                    }
                    insertDrops(drops,  2, level, pos);
                    insertDrops(drops2, 2, level, pos);
                }

                tryPushToFunnels(level, pos);

                consumeFuel(level, pos, fuelStack, tier);
                // Re-fetch after first consume and consume again (2 fuel per cycle)
                consumeFuel(level, pos, inventory.getStackInSlot(0), tier);
                setChanged();

                if (OretoryConfig.ENABLE_COMPLETION_PARTICLES.get())
                    spawnCompletionBurst(serverLevel,
                            oreAbove ? abovePos : null,
                            oreBelow ? belowPos : null);

                level.playSound(null, pos, Oretory.MINER_DEPOSIT.get(), SoundSource.BLOCKS,
                        0.5f, 0.85f + level.random.nextFloat() * 0.3f);
            }
        } else {
            miningProgress = 0;
            if (!hasFuel)        this.status = STATUS_OFF;
            else if (outputFull) this.status = STATUS_OUTPUT_FULL;
            else if (timedOut)   this.status = STATUS_NO_ORE;
            else                 this.status = STATUS_IDLE;
        }

        // --- Sync block state (no LIT property on Advanced Miner) ---
        if (state.getValue(AdvancedMinerBlock.MINING) != isCurrentlyMining
                || state.getValue(AdvancedMinerBlock.REDSTONE_MODE) != this.redstoneMode) {
            level.setBlock(pos, state
                    .setValue(AdvancedMinerBlock.MINING, isCurrentlyMining)
                    .setValue(AdvancedMinerBlock.REDSTONE_MODE, this.redstoneMode), 3);
            level.sendBlockUpdated(pos, state, level.getBlockState(pos), 3);
        }
    }

    private void insertDrops(List<ItemStack> drops, int slot, Level level, BlockPos pos) {
        for (ItemStack drop : drops) {
            ItemStack remainder = inventory.insertItem(slot, drop.copy(), false);
            if (!remainder.isEmpty())
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
        }
    }

    // =========================================================================
    // FUNNEL PUSH
    // =========================================================================
    private void tryPushToFunnels(Level level, BlockPos pos) {
        List<FunnelEntry> bottomFunnels = detectExtractingFunnels(level, pos, Direction.DOWN);
        List<FunnelEntry> topFunnels    = detectExtractingFunnels(level, pos, Direction.UP);
        List<FunnelEntry> sideFunnels   = detectExtractingFunnels(level, pos, null);
        sideFunnels.removeIf(f -> f.side() == Direction.DOWN || f.side() == Direction.UP);

        pushFromSlot(level, pos, bottomFunnels, 1, true);
        pushFromSlot(level, pos, topFunnels,    2, false);

        if (!sideFunnels.isEmpty()) {
            ItemStack outBottom = inventory.getStackInSlot(1);
            ItemStack outTop    = inventory.getStackInSlot(2);
            boolean canBottom = !outBottom.isEmpty() && outBottom.getCount() >= outputThresholdBottom;
            boolean canTop    = !outTop.isEmpty()    && outTop.getCount()    >= outputThresholdTop;
            if (canBottom)      pushFromSlot(level, pos, sideFunnels, 1, true);
            else if (canTop)    pushFromSlot(level, pos, sideFunnels, 2, false);
        }
    }

    private void pushFromSlot(Level level, BlockPos pos, List<FunnelEntry> funnels, int slot, boolean isBottom) {
        if (funnels.isEmpty()) return;
        int threshold    = isBottom ? outputThresholdBottom : outputThresholdTop;
        ItemStack output = inventory.getStackInSlot(slot);
        if (output.isEmpty() || output.getCount() < threshold) return;

        int[] rrIndex = isBottom ? new int[]{roundRobinIndexBottom} : new int[]{roundRobinIndexTop};
        if (rrIndex[0] >= funnels.size()) rrIndex[0] = 0;

        int startIndex = rrIndex[0];
        int attempts   = 0;

        while (attempts < funnels.size()) {
            output = inventory.getStackInSlot(slot);
            if (output.isEmpty() || output.getCount() < threshold) break;

            int         currentIndex = (startIndex + attempts) % funnels.size();
            FunnelEntry funnel       = funnels.get(currentIndex);
            int         batchSize    = funnel.isBrass() ? 16 : 1;
            int         toSend       = Math.min(batchSize, output.getCount());
            ItemStack   batch        = output.copyWithCount(toSend);
            ItemStack   notAccepted  = tryInsertIntoInventory(funnel.targetInventory(), batch);
            int         accepted     = toSend - notAccepted.getCount();

            rrIndex[0] = (currentIndex + 1) % funnels.size();

            if (accepted > 0) {
                inventory.extractItem(slot, accepted, false);
                setChanged();
                break;
            }
            attempts++;
        }

        if (isBottom) roundRobinIndexBottom = rrIndex[0];
        else          roundRobinIndexTop    = rrIndex[0];
    }

    private record FunnelEntry(Direction side, boolean isBrass, IItemHandler targetInventory) {}

    private List<FunnelEntry> detectExtractingFunnels(Level level, BlockPos pos, @Nullable Direction filterSide) {
        List<FunnelEntry> result = new ArrayList<>();
        Direction[] sides = filterSide != null
                ? new Direction[]{ filterSide }
                : new Direction[]{ Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN, Direction.UP };

        for (Direction side : sides) {
            BlockPos   funnelPos   = pos.relative(side);
            BlockState funnelState = level.getBlockState(funnelPos);
            if (!(funnelState.getBlock() instanceof FunnelBlock)) continue;
            if (!funnelState.getValue(FunnelBlock.EXTRACTING))    continue;
            if (funnelState.getValue(AbstractFunnelBlock.POWERED)) continue;
            Direction funnelFacing = AbstractFunnelBlock.getFunnelFacing(funnelState);
            if (funnelFacing != side) continue;
            BlockPos     targetPos = funnelPos.relative(side);
            IItemHandler targetInv = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, targetPos, side.getOpposite());
            if (targetInv == null) continue;
            boolean isBrass = funnelState.getBlock() instanceof BrassFunnelBlock;
            result.add(new FunnelEntry(side, isBrass, targetInv));
        }
        return result;
    }

    private static ItemStack tryInsertIntoInventory(IItemHandler inv, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < inv.getSlots() && !remaining.isEmpty(); slot++)
            remaining = inv.insertItem(slot, remaining, false);
        return remaining;
    }

    // =========================================================================
    // FUEL CONSUMPTION
    // =========================================================================
    private void consumeFuel(Level level, BlockPos pos, ItemStack fuelStack, @Nullable FuelTier tier) {
        if (fuelStack.isEmpty()) return;
        if (tier != null && tier.consumeAction == FuelTier.ConsumeAction.RETURN_BUCKET) {
            fuelStack.shrink(1);
            ItemStack bucket   = new ItemStack(Items.BUCKET);
            ItemStack leftover = inventory.insertItem(0, bucket, false);
            if (!leftover.isEmpty())
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), leftover);
        } else {
            fuelStack.shrink(1);
        }
    }

    // =========================================================================
    // DRILL DAMAGE
    // =========================================================================
    private void checkDrillCollision(Level level, BlockPos pos, @Nullable FuelTier tier) {
        AABB topDrill    = new AABB(pos.getX(), pos.getY() + 1.0, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.1, pos.getZ() + 1.0);
        AABB bottomDrill = new AABB(pos.getX(), pos.getY() - 0.1, pos.getZ(), pos.getX() + 1.0, pos.getY(),       pos.getZ() + 1.0);
        DamageSource drillDamage = level.damageSources().generic();
        float baseDmg    = (float) OretoryConfig.BASE_DAMAGE_AMOUNT.get().doubleValue();
        float speedScale = 1.0f;
        if (OretoryConfig.DAMAGE_SCALES_WITH_SPEED.get() && tier != null)
            speedScale = Math.min(2.0f, 1.0f / Math.max(0.1f, tier.speedMultiplier));
        float damage = baseDmg * speedScale;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, topDrill)) {
            if (entity instanceof Player p && p.isCrouching()) continue;
            entity.hurt(drillDamage, damage);
            Vec3 away = entity.position().subtract(Vec3.atCenterOf(pos)).normalize().scale(0.3);
            entity.push(away.x, 0.2, away.z);
        }
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bottomDrill)) {
            if (entity instanceof Player p && p.isCrouching()) continue;
            entity.hurt(drillDamage, damage);
            Vec3 away = entity.position().subtract(Vec3.atCenterOf(pos)).normalize().scale(0.3);
            entity.push(away.x, -0.1, away.z);
        }
    }

    // =========================================================================
    // LOOT / DROPS
    // =========================================================================
    private List<ItemStack> getPotentialDrops(ServerLevel level, BlockPos pos, BlockState state) {
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL,   new ItemStack(Items.NETHERITE_PICKAXE));
        return state.getDrops(builder);
    }

    // =========================================================================
    // PARTICLES
    // =========================================================================
    private void spawnCompletionBurst(ServerLevel level, @Nullable BlockPos above, @Nullable BlockPos below) {
        if (above != null) doCompletionBurst(level, above);
        if (below != null) doCompletionBurst(level, below);
    }

    private void doCompletionBurst(ServerLevel level, BlockPos targetPos) {
        BlockState targetState = level.getBlockState(targetPos);
        int color = getOreColor(targetState);
        if (color == 0) color = 0xFFFFFF;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >>  8) & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(r, g, b), 1.2f);
        for (int i = 0; i < 12; i++) {
            double px = targetPos.getX() + 0.1 + level.random.nextDouble() * 0.8;
            double py = targetPos.getY() + (targetPos.getY() > worldPosition.getY() ? 0.0 : 1.0);
            double pz = targetPos.getZ() + 0.1 + level.random.nextDouble() * 0.8;
            level.sendParticles(dust, px, py, pz, 1,
                    (level.random.nextDouble() - 0.5) * 0.5,
                    level.random.nextDouble() * 0.4,
                    (level.random.nextDouble() - 0.5) * 0.5, 0.0);
        }
    }

    // =========================================================================
    // CLIENT TICK
    // =========================================================================
    private void handleClientTick(Level level, BlockState state) {
        int     tickAge  = (int) level.getGameTime();
        boolean isMining = ponderForceMining || state.getValue(AdvancedMinerBlock.MINING);

        if (isMining) {
            if (!miningAnimationState.isStarted()) miningAnimationState.start(tickAge);
            idleAnimationState.stop();
            spawnMiningParticles(level, worldPosition.above());
            spawnMiningParticles(level, worldPosition.below());
        } else {
            idleAnimationState.stop();
            miningAnimationState.stop();
        }

        // Idle sound: play whenever the block entity is loaded (no LIT check)
        if (idleSoundInstance == null || idleSoundInstance.isStopped()) {
            idleSoundInstance = new MinerSoundInstance(this, Oretory.MINER_IDLE.get(), false, 1.0f);
            Minecraft.getInstance().getSoundManager().play(idleSoundInstance);
        }

        if (isMining) {
            float pitch = getMiningPitch();
            if (miningSoundInstance == null || miningSoundInstance.isStopped()) {
                miningSoundInstance = new MinerSoundInstance(this, Oretory.MINER_MINING.get(), true, pitch);
                Minecraft.getInstance().getSoundManager().play(miningSoundInstance);
            } else {
                miningSoundInstance.setTargetPitch(pitch);
            }
        } else {
            if (miningSoundInstance != null && !miningSoundInstance.isStopped()) miningSoundInstance.fadeOut();
        }
    }

    private float getMiningPitch() {
        int encoded = data.get(AdvancedMinerMenu.DATA_SPEED);
        if (encoded == 0) return 1.0f;
        float speedMult = encoded / 100f;
        float t = Math.clamp((speedMult - 0.10f) / 1.30f, 0.0f, 1.0f);
        return 1.6f - t * 0.8f;
    }

    private void spawnMiningParticles(Level level, BlockPos targetPos) {
        BlockState targetState = level.getBlockState(targetPos);
        if (!targetState.is(ORES_TAG)) return;
        int density = OretoryConfig.PARTICLE_DENSITY.get();
        int color   = getOreColor(targetState);
        for (int i = 0; i < density; i++) {
            if (level.random.nextFloat() < 0.7f) {
                double px = targetPos.getX() + 0.05 + level.random.nextDouble() * 0.9;
                double py = targetPos.getY() + (targetPos.getY() > worldPosition.getY() ? 0.0 : 1.0);
                double pz = targetPos.getZ() + 0.05 + level.random.nextDouble() * 0.9;
                ParticleOptions particle;
                if (color != 0) {
                    float r = ((color >> 16) & 0xFF) / 255f;
                    float g = ((color >>  8) & 0xFF) / 255f;
                    float b = ( color        & 0xFF) / 255f;
                    particle = new DustParticleOptions(new Vector3f(r, g, b), 0.8f);
                } else {
                    particle = new BlockParticleOption(ParticleTypes.BLOCK, targetState);
                }
                level.addParticle(particle, px, py, pz,
                        (level.random.nextDouble() - 0.5) * 0.2, -0.15,
                        (level.random.nextDouble() - 0.5) * 0.2);
            }
        }
    }

    private int getOreColor(BlockState state) {
        String path = state.getBlock().builtInRegistryHolder().key().location().getPath();
        return ORE_COLORS.getOrDefault(path, 0);
    }

    // =========================================================================
    // GOGGLES OVERLAY
    // =========================================================================
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!OretoryConfig.ENABLE_GOGGLES_OVERLAY.get()) return false;

        ItemStack fuelStack = inventory.getStackInSlot(0);
        FuelTier  tier      = FuelTier.getTier(fuelStack);

        CreateLang.builder().text(ChatFormatting.WHITE, "Advanced Ore Miner").forGoggles(tooltip, 1);

        String statusStr = switch (status) {
            case STATUS_OFF         -> "No Fuel (needs 2)";
            case STATUS_IDLE        -> "Idle";
            case STATUS_MINING      -> "Mining";
            case STATUS_OUTPUT_FULL -> "Output Full";
            case STATUS_NO_ORE      -> "No Ore";
            default                 -> "Unknown";
        };
        ChatFormatting statusColor = switch (status) {
            case STATUS_MINING      -> ChatFormatting.GREEN;
            case STATUS_IDLE        -> ChatFormatting.YELLOW;
            case STATUS_OUTPUT_FULL -> ChatFormatting.RED;
            case STATUS_NO_ORE      -> ChatFormatting.GOLD;
            default                 -> ChatFormatting.GRAY;
        };
        CreateLang.builder().text(ChatFormatting.GRAY, "Status: ").text(statusColor, statusStr).forGoggles(tooltip, 1);

        if (!fuelStack.isEmpty() && tier != null) {
            CreateLang.builder().text(ChatFormatting.GRAY, "Fuel: ").text(ChatFormatting.GOLD, tier.displayName + " x2").forGoggles(tooltip, 1);
            CreateLang.builder().text(ChatFormatting.GRAY, "Speed: ").text(ChatFormatting.AQUA, tier.getSpeedLabel() + " (+50%)").forGoggles(tooltip, 1);
        } else {
            CreateLang.builder().text(ChatFormatting.GRAY, "Fuel: ").text(ChatFormatting.DARK_GRAY, "None (needs 2 per cycle)").forGoggles(tooltip, 1);
        }

        CreateLang.builder().text(ChatFormatting.GRAY, "Release Bottom at: ")
                .text(ChatFormatting.AQUA, outputThresholdBottom + " item(s)").forGoggles(tooltip, 1);
        CreateLang.builder().text(ChatFormatting.GRAY, "Release Top at: ")
                .text(ChatFormatting.AQUA, outputThresholdTop + " item(s)").forGoggles(tooltip, 1);

        Level level = getLevel();
        if (level != null) {
            BlockPos abovePos = worldPosition.above();
            BlockPos belowPos = worldPosition.below();
            boolean  oreAbove = level.getBlockState(abovePos).is(ORES_TAG);
            boolean  oreBelow = level.getBlockState(belowPos).is(ORES_TAG);
            String   aboveName = oreAbove ? level.getBlockState(abovePos).getBlock().getName().getString() : "None";
            String   belowName = oreBelow ? level.getBlockState(belowPos).getBlock().getName().getString() : "None";
            CreateLang.builder().text(ChatFormatting.GRAY, "Above \u2192 Slot 2: ")
                    .text(oreAbove ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY, aboveName).forGoggles(tooltip, 1);
            CreateLang.builder().text(ChatFormatting.GRAY, "Below \u2192 Slot 1: ")
                    .text(oreBelow ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY, belowName).forGoggles(tooltip, 1);
        }

        CreateLang.builder().text(ChatFormatting.GRAY, "Redstone: ")
                .text(ChatFormatting.YELLOW, redstoneMode.getDisplayName()).forGoggles(tooltip, 1);
        return true;
    }

    // =========================================================================
    // ITEM HANDLER CAPABILITY (sided)
    // =========================================================================
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null)           return inventory;
        if (side == Direction.UP)   return fuelHandler;
        if (side == Direction.DOWN) return outputHandlerBottom;
        return combinedHandler;
    }

    // =========================================================================
    // GETTERS / SETTERS
    // =========================================================================
    public int  getOutputThresholdBottom() { return outputThresholdBottom; }
    public int  getOutputThresholdTop()    { return outputThresholdTop; }

    public void setOutputThresholdBottom(int value) {
        outputThresholdBottom = Math.max(1, Math.min(64, value));
        setChanged();
    }

    public void setOutputThresholdTop(int value) {
        outputThresholdTop = Math.max(1, Math.min(64, value));
        setChanged();
    }

    public void setPonderMining(boolean mining) { this.ponderForceMining = mining; }

    @Nullable public Direction getFilterFace() { return filterFace; }

    private boolean passesFuelFilter(ItemStack stack) {
        if (filter == null) return true;
        ItemStack filterItem = filter.getFilter();
        if (filterItem.isEmpty()) return true;
        return filter.test(stack);
    }

    // =========================================================================
    // NBT
    // =========================================================================
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("inventory",              inventory.serializeNBT(registries));
        tag.putInt("miningProgress",      miningProgress);
        tag.putInt("minerStatus",         status);
        tag.putInt("noOreTicks",          noOreTicks);
        tag.putInt("redstoneMode",        redstoneMode.ordinal());
        tag.putInt("roundRobinBottom",    roundRobinIndexBottom);
        tag.putInt("roundRobinTop",       roundRobinIndexTop);
        tag.putInt("thresholdBottom",     outputThresholdBottom);
        tag.putInt("thresholdTop",        outputThresholdTop);
        tag.putInt("filterFace",          filterFace == null ? -1 : filterFace.ordinal());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        miningProgress        = tag.getInt("miningProgress");
        status                = tag.getInt("minerStatus");
        noOreTicks            = tag.getInt("noOreTicks");
        int savedMode         = tag.getInt("redstoneMode");
        redstoneMode          = MinerRedstoneMode.values()[Math.min(savedMode, MinerRedstoneMode.values().length - 1)];
        roundRobinIndexBottom = tag.getInt("roundRobinBottom");
        roundRobinIndexTop    = tag.getInt("roundRobinTop");
        outputThresholdBottom = tag.contains("thresholdBottom") ? Math.max(1, Math.min(64, tag.getInt("thresholdBottom"))) : 1;
        outputThresholdTop    = tag.contains("thresholdTop")    ? Math.max(1, Math.min(64, tag.getInt("thresholdTop")))    : 1;
        if (tag.contains("filterFace")) {
            int faceOrdinal = tag.getInt("filterFace");
            if (faceOrdinal < 0) filterFace = null;
            else {
                Direction[] dirs = Direction.values();
                filterFace = (faceOrdinal < dirs.length) ? dirs[faceOrdinal] : Direction.NORTH;
            }
        } else {
            filterFace = Direction.NORTH;
        }
        cachedFuelTier = null;
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.oretory.advanced_miner");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player p) {
        AdvancedMinerMenu menu = new AdvancedMinerMenu(id, inv, this.inventory, this.data);
        menu.setBlockPos(this.worldPosition);
        return menu;
    }

    // =========================================================================
    // Single-slot handler wrapper
    // =========================================================================
    private record ItemStackHandlerWrapper(
            ItemStackHandler handler, int slot,
            boolean canInsert, boolean canExtract
    ) implements IItemHandlerModifiable {
        @Override public int getSlots()                                                               { return 1; }
        @Override public @NotNull ItemStack getStackInSlot(int s)                                    { return handler.getStackInSlot(slot); }
        @Override public int getSlotLimit(int s)                                                     { return handler.getSlotLimit(slot); }
        @Override public boolean isItemValid(int s, @NotNull ItemStack stack)                        { return canInsert && handler.isItemValid(slot, stack); }
        @Override public void setStackInSlot(int s, @NotNull ItemStack stack)                        { handler.setStackInSlot(slot, stack); }
        @Override public @NotNull ItemStack insertItem(int s, @NotNull ItemStack stack, boolean sim) { return canInsert  ? handler.insertItem(slot, stack, sim)  : stack; }
        @Override public @NotNull ItemStack extractItem(int s, int amount, boolean sim)              { return canExtract ? handler.extractItem(slot, amount, sim) : ItemStack.EMPTY; }
    }
}