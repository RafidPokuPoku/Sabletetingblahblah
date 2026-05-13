package com.rafid.oretory;

import com.rafid.oretory.client.MinerSoundInstance;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BrassFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
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
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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

public class MinerBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements MenuProvider, IHaveGoggleInformation {

    // -------------------------------------------------------------------------
    // Status constants
    // -------------------------------------------------------------------------
    public static final int STATUS_OFF         = 0;
    public static final int STATUS_IDLE        = 1;
    public static final int STATUS_MINING      = 2;
    public static final int STATUS_OUTPUT_FULL = 3;
    public static final int STATUS_NO_ORE      = 4;
    public static final int STATUS_MIXED_ORES  = 5;

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

    // -------------------------------------------------------------------------
    // Animation states
    // -------------------------------------------------------------------------
    public final AnimationState idleAnimationState   = new AnimationState();
    public final AnimationState miningAnimationState = new AnimationState();

    // -------------------------------------------------------------------------
    // Sound instances (client-only)
    // -------------------------------------------------------------------------
    @Nullable private MinerSoundInstance idleSoundInstance;
    @Nullable private MinerSoundInstance miningSoundInstance;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private int status         = STATUS_OFF;
    private int miningProgress = 0;
    private int noOreTicks     = 0;
    MinerRedstoneMode redstoneMode = MinerRedstoneMode.IGNORED;

    /**
     * Output threshold: external automation can only extract from the output
     * slot once its count reaches this value. Default 1 = immediate release.
     *
     * This is the AUTHORITATIVE value on the server. The client box reads it
     * back via ContainerData. It is saved to NBT so it survives GUI close/open.
     */
    private int outputThreshold = 1;

    private boolean ponderForceMining = false;
    private boolean ponderForceLit    = false;

    @Nullable private transient FuelTier cachedFuelTier = null;

    // -------------------------------------------------------------------------
    // Round-robin funnel tracking
    // -------------------------------------------------------------------------
    private int roundRobinIndex = 0;

    // -------------------------------------------------------------------------
    // Inventory
    // -------------------------------------------------------------------------
    public final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == 0) cachedFuelTier = null;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == 0) return FuelTier.isValidFuel(stack);
            return slot == 1;
        }
    };

    // Fuel side: insert only, no extract (UP face)
    private final IItemHandlerModifiable fuelHandler =
            new ItemStackHandlerWrapper(inventory, 0, true, false);

    /**
     * Output handler exposed to ALL external automation (hoppers, pipes,
     * funnels via capability).  Extraction is gated: nothing comes out until
     * the output slot holds at least outputThreshold items.
     *
     * Insert is always blocked — nothing external should put items IN here.
     */
    private final IItemHandlerModifiable outputHandler = new IItemHandlerModifiable() {
        @Override public int  getSlots()                                          { return 1; }
        @Override public int  getSlotLimit(int s)                                 { return inventory.getSlotLimit(1); }
        @Override public boolean isItemValid(int s, @NotNull ItemStack stack)     { return false; }
        @Override public @NotNull ItemStack getStackInSlot(int s)                 { return inventory.getStackInSlot(1); }
        @Override public void setStackInSlot(int s, @NotNull ItemStack stack)     { inventory.setStackInSlot(1, stack); }

        @Override
        public @NotNull ItemStack insertItem(int s, @NotNull ItemStack stack, boolean sim) {
            return stack; // always block external inserts
        }

        @Override
        public @NotNull ItemStack extractItem(int s, int amount, boolean sim) {
            ItemStack current = inventory.getStackInSlot(1);
            // Gate: must have at least outputThreshold items before anything leaves
            if (current.isEmpty() || current.getCount() < outputThreshold)
                return ItemStack.EMPTY;
            return inventory.extractItem(1, amount, sim);
        }
    };

    /**
     * Combined handler for lateral sides: fuel insert + threshold-gated output.
     * Using a real CombinedInvWrapper means slot 0 = fuel, slot 1 = output,
     * and the output slot still goes through the gated outputHandler above.
     */
    private final CombinedInvWrapper combinedHandler =
            new CombinedInvWrapper(fuelHandler, outputHandler);

    // -------------------------------------------------------------------------
    // ContainerData — synced to client every tick by the vanilla container
    // -------------------------------------------------------------------------
    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case MinerMenu.DATA_STATUS    -> MinerBlockEntity.this.status;
                case MinerMenu.DATA_PROGRESS  -> MinerBlockEntity.this.miningProgress;
                case MinerMenu.DATA_RSMODE    -> MinerBlockEntity.this.redstoneMode.ordinal();
                case MinerMenu.DATA_SPEED     -> {
                    FuelTier t = MinerBlockEntity.this.cachedFuelTier;
                    yield t == null ? 0 : (int) (t.speedMultiplier * 100);
                }
                case MinerMenu.DATA_THRESHOLD -> MinerBlockEntity.this.outputThreshold;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // NOTE: the server NEVER calls set() on ContainerData from outside;
            // set() is only invoked client-side by the vanilla sync mechanism.
            // We intentionally do NOT update outputThreshold here — the server
            // field is the truth, updated only via setOutputThreshold().
            if (index == MinerMenu.DATA_STATUS)   MinerBlockEntity.this.status         = value;
            if (index == MinerMenu.DATA_PROGRESS) MinerBlockEntity.this.miningProgress = value;
            if (index == MinerMenu.DATA_RSMODE)
                MinerBlockEntity.this.redstoneMode =
                        MinerRedstoneMode.values()[Math.max(0,
                                Math.min(value, MinerRedstoneMode.values().length - 1))];
            // DATA_THRESHOLD is read-only from the client's perspective;
            // changes come in via the SetThresholdPayload packet -> setOutputThreshold().
        }

        @Override
        public int getCount() { return MinerMenu.DATA_COUNT; }
    };

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    public MinerBlockEntity(BlockPos pos, BlockState state) {
        super(Oretory.MINER_BE.get(), pos, state);
        this.redstoneMode = state.getValue(MinerBlock.REDSTONE_MODE);
    }

    public void setPonderMining(boolean mining) { this.ponderForceMining = mining; }
    public void setPonderLit(boolean lit)       { this.ponderForceLit    = lit;    }

    // =========================================================================
    // SERVER TICK
    // =========================================================================
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) {
            handleClientTick(level, state);
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) return;

        // --- Fuel ---
        ItemStack fuelStack = inventory.getStackInSlot(0);
        boolean hasFuel = !fuelStack.isEmpty() && FuelTier.isValidFuel(fuelStack);
        if (hasFuel && cachedFuelTier == null) cachedFuelTier = FuelTier.getTier(fuelStack);
        else if (!hasFuel) cachedFuelTier = null;

        // --- Redstone gate ---
        boolean hasSignal        = level.hasNeighborSignal(pos);
        boolean allowedByRedstone = switch (redstoneMode) {
            case IGNORED  -> true;
            case ENABLED  -> hasSignal;
            case DISABLED -> !hasSignal;
        };

        // --- Ore detection ---
        BlockPos  abovePos   = pos.above();
        BlockPos  belowPos   = pos.below();
        BlockState aboveState = level.getBlockState(abovePos);
        BlockState belowState = level.getBlockState(belowPos);
        boolean oreAbove = aboveState.is(ORES_TAG);
        boolean oreBelow = belowState.is(ORES_TAG);
        boolean hasOre   = oreAbove || oreBelow;

        // --- Mixed ore check ---
        boolean mixedOres = false;
        if (oreAbove && oreBelow) {
            String aboveKey  = aboveState.getBlock().builtInRegistryHolder().key().location().getPath();
            String belowKey  = belowState.getBlock().builtInRegistryHolder().key().location().getPath();
            String aboveBase = aboveKey.replace("deepslate_", "");
            String belowBase = belowKey.replace("deepslate_", "");
            mixedOres = !aboveBase.equals(belowBase);
        }

        // --- No-ore timeout ---
        int noOreTimeout = OretoryConfig.NO_ORE_TIMEOUT_TICKS.get();
        if (!hasOre && hasFuel && allowedByRedstone && noOreTimeout > 0) noOreTicks++;
        else noOreTicks = 0;
        boolean timedOut = noOreTimeout > 0 && noOreTicks >= noOreTimeout;

        // --- Output full check ---
        ItemStack outputStack = inventory.getStackInSlot(1);
        boolean outputFull = !outputStack.isEmpty()
                && outputStack.getCount() >= outputStack.getMaxStackSize();

        boolean canProcess       = hasFuel && allowedByRedstone && hasOre && !outputFull && !mixedOres;
        boolean isCurrentlyMining = false;

        if (canProcess) {
            isCurrentlyMining = true;
            this.status       = STATUS_MINING;
            noOreTicks        = 0;

            FuelTier tier        = cachedFuelTier;
            int ticksPerCycle    = tier != null
                    ? tier.getTicksPerCycle()
                    : OretoryConfig.BASE_TICKS_PER_CYCLE.get();

            if (level.getGameTime() % 2 == 0 && OretoryConfig.ENABLE_ENTITY_DAMAGE.get())
                checkDrillCollision(level, pos, tier);

            miningProgress++;

            if (miningProgress >= ticksPerCycle) {
                miningProgress = 0;

                List<ItemStack> drops = new ArrayList<>();
                if (oreAbove) drops.addAll(getPotentialDrops(serverLevel, abovePos, aboveState));
                if (oreBelow) drops.addAll(getPotentialDrops(serverLevel, belowPos, belowState));

                if (tier != null && tier.doubleDropChance > 0f
                        && level.random.nextFloat() < tier.doubleDropChance)
                    drops.addAll(new ArrayList<>(drops));

                for (ItemStack drop : drops) {
                    ItemStack remainder = inventory.insertItem(1, drop.copy(), false);
                    if (!remainder.isEmpty())
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
                }

                // Push to funnels (threshold-gated, round-robin)
                tryPushToFunnels(level, pos);

                consumeFuel(level, pos, fuelStack, tier);
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
            else if (mixedOres)  this.status = STATUS_MIXED_ORES;
            else if (outputFull) this.status = STATUS_OUTPUT_FULL;
            else if (timedOut)   this.status = STATUS_NO_ORE;
            else                 this.status = STATUS_IDLE;
        }

        boolean shouldBeLit = hasFuel;
        if (state.getValue(MinerBlock.MINING) != isCurrentlyMining
                || state.getValue(MinerBlock.LIT) != shouldBeLit
                || state.getValue(MinerBlock.REDSTONE_MODE) != this.redstoneMode) {
            level.setBlock(pos, state
                    .setValue(MinerBlock.MINING, isCurrentlyMining)
                    .setValue(MinerBlock.LIT, shouldBeLit)
                    .setValue(MinerBlock.REDSTONE_MODE, this.redstoneMode), 3);
            level.sendBlockUpdated(pos, state, level.getBlockState(pos), 3);
        }
    }

    // =========================================================================
    // FUNNEL PUSH (threshold-gated, round-robin)
    // =========================================================================
    private void tryPushToFunnels(Level level, BlockPos pos) {
        ItemStack outputStack = inventory.getStackInSlot(1);
        if (outputStack.isEmpty()) return;
        if (outputStack.getCount() < outputThreshold) return;

        List<FunnelEntry> funnels = detectExtractingFunnels(level, pos);
        if (funnels.isEmpty()) return;

        if (roundRobinIndex >= funnels.size()) roundRobinIndex = 0;

        int startIndex = roundRobinIndex;
        int attempts   = 0;

        while (attempts < funnels.size()) {
            outputStack = inventory.getStackInSlot(1);
            if (outputStack.isEmpty() || outputStack.getCount() < outputThreshold) break;

            int         currentIndex = (startIndex + attempts) % funnels.size();
            FunnelEntry funnel       = funnels.get(currentIndex);

            int       batchSize    = funnel.isBrass() ? 16 : 1;
            int       toSend       = Math.min(batchSize, outputStack.getCount());
            ItemStack batch        = outputStack.copyWithCount(toSend);
            ItemStack notAccepted  = tryInsertIntoInventory(funnel.targetInventory(), batch);
            int       accepted     = toSend - notAccepted.getCount();

            roundRobinIndex = (currentIndex + 1) % funnels.size();

            if (accepted > 0) {
                inventory.extractItem(1, accepted, false);
                setChanged();
                break;
            }
            attempts++;
        }
    }

    private record FunnelEntry(Direction side, boolean isBrass, IItemHandler targetInventory) {}

    private List<FunnelEntry> detectExtractingFunnels(Level level, BlockPos pos) {
        List<FunnelEntry> result = new ArrayList<>();
        Direction[] outputSides  = {
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
        };
        for (Direction side : outputSides) {
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

    // -------------------------------------------------------------------------
    // Fuel consumption
    // -------------------------------------------------------------------------
    private void consumeFuel(Level level, BlockPos pos, ItemStack fuelStack, @Nullable FuelTier tier) {
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

    // -------------------------------------------------------------------------
    // Drill damage
    // -------------------------------------------------------------------------
    private void checkDrillCollision(Level level, BlockPos pos, @Nullable FuelTier tier) {
        AABB topDrill = new AABB(
                pos.getX(), pos.getY() + 1.0, pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.1, pos.getZ() + 1.0);
        AABB bottomDrill = new AABB(
                pos.getX(), pos.getY() - 0.1, pos.getZ(),
                pos.getX() + 1.0, pos.getY(), pos.getZ() + 1.0);

        DamageSource drillDamage = level.damageSources().generic();
        float baseDmg   = (float) OretoryConfig.BASE_DAMAGE_AMOUNT.get().doubleValue();
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

    // -------------------------------------------------------------------------
    // Loot drops
    // -------------------------------------------------------------------------
    private List<ItemStack> getPotentialDrops(ServerLevel level, BlockPos pos, BlockState state) {
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL,   new ItemStack(Items.NETHERITE_PICKAXE));
        return state.getDrops(builder);
    }

    // -------------------------------------------------------------------------
    // Completion burst particles
    // -------------------------------------------------------------------------
    private void spawnCompletionBurst(ServerLevel level,
                                      @Nullable BlockPos above, @Nullable BlockPos below) {
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
        boolean isMining = ponderForceMining || state.getValue(MinerBlock.MINING);
        boolean isLit    = ponderForceLit    || state.getValue(MinerBlock.LIT);

        if (isMining) {
            if (!miningAnimationState.isStarted()) miningAnimationState.start(tickAge);
            idleAnimationState.stop();
            spawnMiningParticles(level, worldPosition.above());
            spawnMiningParticles(level, worldPosition.below());
        } else if (isLit) {
            if (!idleAnimationState.isStarted()) idleAnimationState.start(tickAge);
            miningAnimationState.stop();
        } else {
            idleAnimationState.stop();
            miningAnimationState.stop();
        }

        if (isLit) {
            if (idleSoundInstance == null || idleSoundInstance.isStopped()) {
                idleSoundInstance = new MinerSoundInstance(this, Oretory.MINER_IDLE.get(), false, 1.0f);
                Minecraft.getInstance().getSoundManager().play(idleSoundInstance);
            }
        } else {
            if (idleSoundInstance != null && !idleSoundInstance.isStopped()) idleSoundInstance.fadeOut();
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
        int encoded = data.get(MinerMenu.DATA_SPEED);
        if (encoded == 0) return 1.0f;
        float speedMult = encoded / 100f;
        float t = Math.clamp((speedMult - 0.10f) / 1.30f, 0.0f, 1.0f);
        return 1.6f - t * 0.8f;
    }

    // -------------------------------------------------------------------------
    // Mining particles (client)
    // -------------------------------------------------------------------------
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

        ItemStack         fuelStack = inventory.getStackInSlot(0);
        FuelTier          tier      = FuelTier.getTier(fuelStack);
        MinerRedstoneMode mode      = this.redstoneMode;

        CreateLang.builder()
                .text(ChatFormatting.WHITE, "Ore Miner")
                .forGoggles(tooltip, 1);

        String statusStr = switch (status) {
            case STATUS_OFF         -> "No Fuel";
            case STATUS_IDLE        -> "Idle";
            case STATUS_MINING      -> "Mining";
            case STATUS_OUTPUT_FULL -> "Output Full";
            case STATUS_NO_ORE      -> "No Ore";
            case STATUS_MIXED_ORES  -> "Mixed Ores";
            default                 -> "Unknown";
        };
        ChatFormatting statusColor = switch (status) {
            case STATUS_MINING      -> ChatFormatting.GREEN;
            case STATUS_IDLE        -> ChatFormatting.YELLOW;
            case STATUS_OUTPUT_FULL -> ChatFormatting.RED;
            case STATUS_NO_ORE      -> ChatFormatting.GOLD;
            case STATUS_MIXED_ORES  -> ChatFormatting.LIGHT_PURPLE;
            default                 -> ChatFormatting.GRAY;
        };
        CreateLang.builder()
                .text(ChatFormatting.GRAY, "Status: ")
                .text(statusColor, statusStr)
                .forGoggles(tooltip, 1);

        if (!fuelStack.isEmpty() && tier != null) {
            CreateLang.builder()
                    .text(ChatFormatting.GRAY, "Fuel: ")
                    .text(ChatFormatting.GOLD, tier.displayName)
                    .forGoggles(tooltip, 1);
            CreateLang.builder()
                    .text(ChatFormatting.GRAY, "Speed: ")
                    .text(ChatFormatting.AQUA, tier.getSpeedLabel())
                    .forGoggles(tooltip, 1);
            if (tier.doubleDropChance > 0f) {
                CreateLang.builder()
                        .text(ChatFormatting.GRAY, "Bonus: ")
                        .text(ChatFormatting.LIGHT_PURPLE,
                                "+" + (int) (tier.doubleDropChance * 100) + "% double drop")
                        .forGoggles(tooltip, 1);
            }
        } else {
            CreateLang.builder()
                    .text(ChatFormatting.GRAY, "Fuel: ")
                    .text(ChatFormatting.DARK_GRAY, "None")
                    .forGoggles(tooltip, 1);
        }

        CreateLang.builder()
                .text(ChatFormatting.GRAY, "Release at: ")
                .text(ChatFormatting.AQUA, outputThreshold + " item(s)")
                .forGoggles(tooltip, 1);

        Level level = getLevel();
        if (level != null) {
            BlockPos abovePos = worldPosition.above();
            BlockPos belowPos = worldPosition.below();
            boolean  oreAbove = level.getBlockState(abovePos).is(ORES_TAG);
            boolean  oreBelow = level.getBlockState(belowPos).is(ORES_TAG);
            String   aboveName = oreAbove
                    ? level.getBlockState(abovePos).getBlock().getName().getString() : "None";
            String   belowName = oreBelow
                    ? level.getBlockState(belowPos).getBlock().getName().getString() : "None";
            CreateLang.builder()
                    .text(ChatFormatting.GRAY, "Above: ")
                    .text(oreAbove ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY, aboveName)
                    .forGoggles(tooltip, 1);
            CreateLang.builder()
                    .text(ChatFormatting.GRAY, "Below: ")
                    .text(oreBelow ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY, belowName)
                    .forGoggles(tooltip, 1);
        }

        CreateLang.builder()
                .text(ChatFormatting.GRAY, "Redstone: ")
                .text(ChatFormatting.YELLOW, mode.getDisplayName())
                .forGoggles(tooltip, 1);

        return true;
    }

    // =========================================================================
    // ITEM HANDLER CAPABILITY (sided)
    //
    // UP    -> fuel only (insert)
    // DOWN  -> output only (threshold-gated extract)
    // sides -> combined (fuel insert OR threshold-gated output extract)
    // null  -> raw inventory (internal use / Ponder)
    // =========================================================================
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null)           return inventory;
        if (side == Direction.UP)   return fuelHandler;
        if (side == Direction.DOWN) return outputHandler;
        return combinedHandler;     // NORTH / SOUTH / EAST / WEST
    }

    // =========================================================================
    // Threshold accessor — called by packet handler on server thread
    // =========================================================================
    public int  getOutputThreshold() { return outputThreshold; }

    /**
     * The ONLY place outputThreshold is mutated on the server.
     * Called from the packet handler; marks the BE dirty so NBT is saved.
     */
    public void setOutputThreshold(int value) {
        outputThreshold = Math.max(1, Math.min(64, value));
        setChanged();
        // No need to call sendBlockUpdated — ContainerData sync pushes the
        // new value to the open menu on the next tick automatically.
    }

    // =========================================================================
    // NBT — outputThreshold is persisted here; this is why it survives close/open
    // =========================================================================
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory",          inventory.serializeNBT(registries));
        tag.putInt("miningProgress",  miningProgress);
        tag.putInt("minerStatus",     status);
        tag.putInt("noOreTicks",      noOreTicks);
        tag.putInt("redstoneMode",    redstoneMode.ordinal());
        tag.putInt("roundRobinIndex", roundRobinIndex);
        tag.putInt("outputThreshold", outputThreshold);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        miningProgress  = tag.getInt("miningProgress");
        status          = tag.getInt("minerStatus");
        noOreTicks      = tag.getInt("noOreTicks");
        int savedMode   = tag.getInt("redstoneMode");
        redstoneMode    = MinerRedstoneMode.values()[
                Math.min(savedMode, MinerRedstoneMode.values().length - 1)];
        roundRobinIndex = tag.getInt("roundRobinIndex");
        outputThreshold = tag.contains("outputThreshold")
                ? Math.max(1, Math.min(64, tag.getInt("outputThreshold")))
                : 1;
        cachedFuelTier  = null;
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.oretory.miner");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player p) {
        MinerMenu menu = new MinerMenu(id, inv, this.inventory, this.data);
        menu.setBlockPos(this.worldPosition);
        return menu;
    }

    // =========================================================================
    // INNER: single-slot wrapper
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