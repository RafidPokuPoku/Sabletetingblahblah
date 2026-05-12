package com.rafid.oretory;

import com.rafid.oretory.client.MinerSoundInstance;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MinerBlockEntity extends BlockEntity implements MenuProvider, IHaveGoggleInformation {

    // -------------------------------------------------------------------------
    // Status constants (also used in MinerMenu)
    // -------------------------------------------------------------------------
    public static final int STATUS_OFF         = 0; // no fuel
    public static final int STATUS_IDLE        = 1; // fuel present, no ore or redstone-blocked
    public static final int STATUS_MINING      = 2; // actively mining
    public static final int STATUS_OUTPUT_FULL = 3; // output slot full
    public static final int STATUS_NO_ORE      = 4; // has fuel, not blocked, but no ore nearby

    // -------------------------------------------------------------------------
    // Ore color palette for dust particles
    // -------------------------------------------------------------------------
    private static final TagKey<Block> ORES_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));

    private static final Map<String, Integer> ORE_COLORS = new HashMap<>();
    static {
        ORE_COLORS.put("iron_ore",                  0xC0C0C0);
        ORE_COLORS.put("deepslate_iron_ore",         0xA0A0A0);
        ORE_COLORS.put("gold_ore",                  0xFFD700);
        ORE_COLORS.put("deepslate_gold_ore",         0xCCAA00);
        ORE_COLORS.put("diamond_ore",               0x00FFFF);
        ORE_COLORS.put("deepslate_diamond_ore",      0x00CCCC);
        ORE_COLORS.put("emerald_ore",               0x00CC44);
        ORE_COLORS.put("deepslate_emerald_ore",      0x009933);
        ORE_COLORS.put("redstone_ore",              0xFF0000);
        ORE_COLORS.put("deepslate_redstone_ore",     0xCC0000);
        ORE_COLORS.put("lapis_ore",                 0x1155CC);
        ORE_COLORS.put("deepslate_lapis_ore",        0x0033AA);
        ORE_COLORS.put("coal_ore",                  0x333333);
        ORE_COLORS.put("deepslate_coal_ore",         0x222222);
        ORE_COLORS.put("copper_ore",                0xCC6633);
        ORE_COLORS.put("deepslate_copper_ore",       0xAA5522);
        ORE_COLORS.put("nether_gold_ore",            0xFFAA00);
        ORE_COLORS.put("nether_quartz_ore",          0xFFEECC);
        ORE_COLORS.put("ancient_debris",             0x8B4513);
    }

    // -------------------------------------------------------------------------
    // Animation states (used by MinerRenderer)
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
    private int status        = STATUS_OFF;
    private int miningProgress = 0;
    /** Ticks elapsed with no ore present (for no-ore timeout). */
    private int noOreTicks    = 0;

    /** Ponder scene overrides */
    private boolean ponderForceMining = false;
    private boolean ponderForceLit    = false;

    /** Cached fuel tier — re-resolved whenever fuel slot changes. */
    @Nullable
    private transient FuelTier cachedFuelTier = null;

    // -------------------------------------------------------------------------
    // Inventory
    // -------------------------------------------------------------------------
    public final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == 0) cachedFuelTier = null; // invalidate on fuel change
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == 0) return FuelTier.isValidFuel(stack);
            return slot == 1; // output accepts anything (pipes/hoppers insert here)
        }
    };

    // Sided item handler wrappers
    private final IItemHandlerModifiable fuelHandler     = new ItemStackHandlerWrapper(inventory, 0, true,  false);
    private final IItemHandlerModifiable outputHandler   = new ItemStackHandlerWrapper(inventory, 1, false, true);
    private final CombinedInvWrapper     combinedHandler = new CombinedInvWrapper(fuelHandler, outputHandler);

    // -------------------------------------------------------------------------
    // ContainerData (synced to client via menu)
    // -------------------------------------------------------------------------
    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case MinerMenu.DATA_STATUS   -> MinerBlockEntity.this.status;
                case MinerMenu.DATA_PROGRESS -> MinerBlockEntity.this.miningProgress;
                case MinerMenu.DATA_RSMODE   -> {
                    BlockState s = MinerBlockEntity.this.getBlockState();
                    yield s.hasProperty(MinerBlock.REDSTONE_MODE)
                            ? s.getValue(MinerBlock.REDSTONE_MODE).ordinal() : 0;
                }
                case MinerMenu.DATA_SPEED    -> {
                    FuelTier t = MinerBlockEntity.this.cachedFuelTier;
                    yield t == null ? 0 : (int) (t.speedMultiplier * 100);
                }
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == MinerMenu.DATA_STATUS)   MinerBlockEntity.this.status        = value;
            if (index == MinerMenu.DATA_PROGRESS) MinerBlockEntity.this.miningProgress = value;
        }

        @Override
        public int getCount() { return MinerMenu.DATA_COUNT; }
    };

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    public MinerBlockEntity(BlockPos pos, BlockState state) {
        super(Oretory.MINER_BE.get(), pos, state);
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

        // --- Resolve fuel ---
        ItemStack fuelStack = inventory.getStackInSlot(0);
        boolean hasFuel = !fuelStack.isEmpty() && FuelTier.isValidFuel(fuelStack);

        if (hasFuel && cachedFuelTier == null) {
            cachedFuelTier = FuelTier.getTier(fuelStack);
        } else if (!hasFuel) {
            cachedFuelTier = null;
        }

        // --- Redstone gate ---
        MinerRedstoneMode mode = state.getValue(MinerBlock.REDSTONE_MODE);
        boolean hasSignal = level.hasNeighborSignal(pos);
        boolean allowedByRedstone = switch (mode) {
            case IGNORED  -> true;
            case ENABLED  -> hasSignal;
            case DISABLED -> !hasSignal;
        };

        // --- Ore detection ---
        BlockPos abovePos = pos.above();
        BlockPos belowPos = pos.below();
        boolean oreAbove = level.getBlockState(abovePos).is(ORES_TAG);
        boolean oreBelow = level.getBlockState(belowPos).is(ORES_TAG);
        boolean hasOre   = oreAbove || oreBelow;

        // --- No-ore timeout ---
        int noOreTimeout = OretoryConfig.NO_ORE_TIMEOUT_TICKS.get();
        if (!hasOre && hasFuel && allowedByRedstone && noOreTimeout > 0) {
            noOreTicks++;
        } else {
            noOreTicks = 0;
        }
        boolean timedOut = noOreTimeout > 0 && noOreTicks >= noOreTimeout;

        // --- Output full check ---
        ItemStack outputStack = inventory.getStackInSlot(1);
        boolean outputFull = !outputStack.isEmpty()
                && outputStack.getCount() >= outputStack.getMaxStackSize();

        boolean canProcess = hasFuel && allowedByRedstone && hasOre && !outputFull;
        boolean isCurrentlyMining = false;

        if (canProcess) {
            isCurrentlyMining = true;
            this.status       = STATUS_MINING;
            noOreTicks        = 0;

            FuelTier tier         = cachedFuelTier;
            int      ticksPerCycle = tier != null ? tier.getTicksPerCycle()
                    : OretoryConfig.BASE_TICKS_PER_CYCLE.get();

            // Drill collision damage every 2 ticks
            if (level.getGameTime() % 2 == 0 && OretoryConfig.ENABLE_ENTITY_DAMAGE.get()) {
                checkDrillCollision(level, pos, tier);
            }

            miningProgress++;

            if (miningProgress >= ticksPerCycle) {
                miningProgress = 0;

                // Collect loot drops
                List<ItemStack> drops = new ArrayList<>();
                if (oreAbove) drops.addAll(getPotentialDrops(serverLevel, abovePos, level.getBlockState(abovePos)));
                if (oreBelow) drops.addAll(getPotentialDrops(serverLevel, belowPos, level.getBlockState(belowPos)));

                // Double-drop bonus
                if (tier != null && tier.doubleDropChance > 0f
                        && level.random.nextFloat() < tier.doubleDropChance) {
                    drops.addAll(new ArrayList<>(drops));
                }

                // Insert into output; spill excess to world (no silent loss)
                for (ItemStack drop : drops) {
                    ItemStack remainder = inventory.insertItem(1, drop.copy(), false);
                    if (!remainder.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
                    }
                }

                // Consume 1 fuel per completed cycle (1 fuel = 1 output)
                consumeFuel(level, pos, fuelStack, tier);
                setChanged();

                // Completion burst particles
                if (OretoryConfig.ENABLE_COMPLETION_PARTICLES.get()) {
                    spawnCompletionBurst(serverLevel,
                            oreAbove ? abovePos : null,
                            oreBelow ? belowPos : null);
                }

                // Deposit sound (pitch varies slightly per-cycle for variety)
                level.playSound(null, pos, Oretory.MINER_DEPOSIT.get(), SoundSource.BLOCKS,
                        0.5f, 0.85f + level.random.nextFloat() * 0.3f);
            }

        } else {
            miningProgress = 0;
            if (!hasFuel) {
                this.status = STATUS_OFF;
            } else if (outputFull) {
                this.status = STATUS_OUTPUT_FULL;
            } else if (timedOut) {
                this.status = STATUS_NO_ORE;
            } else if (!hasOre) {
                this.status = STATUS_IDLE; // waiting for ore, not yet timed out
            } else {
                this.status = STATUS_IDLE; // redstone-blocked
            }
        }

        // Update block state visuals (LIT = has fuel; MINING = actively mining)
        boolean shouldBeLit = hasFuel;
        if (state.getValue(MinerBlock.MINING) != isCurrentlyMining
                || state.getValue(MinerBlock.LIT) != shouldBeLit) {
            level.setBlock(pos, state
                    .setValue(MinerBlock.MINING, isCurrentlyMining)
                    .setValue(MinerBlock.LIT, shouldBeLit), 3);
            level.sendBlockUpdated(pos, state, level.getBlockState(pos), 3);
        }
    }

    // -------------------------------------------------------------------------
    // Fuel consumption
    // -------------------------------------------------------------------------
    private void consumeFuel(Level level, BlockPos pos, ItemStack fuelStack, @Nullable FuelTier tier) {
        if (tier != null && tier.consumeAction == FuelTier.ConsumeAction.RETURN_BUCKET) {
            fuelStack.shrink(1);
            ItemStack bucket   = new ItemStack(Items.BUCKET);
            ItemStack leftover = inventory.insertItem(0, bucket, false);
            if (!leftover.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), leftover);
            }
        } else {
            fuelStack.shrink(1);
        }
    }

    // -------------------------------------------------------------------------
    // Drill damage — crouching players are always immune
    // -------------------------------------------------------------------------
    private void checkDrillCollision(Level level, BlockPos pos, @Nullable FuelTier tier) {
        AABB topDrill = new AABB(
                pos.getX(), pos.getY() + 1.0, pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.1, pos.getZ() + 1.0);
        AABB bottomDrill = new AABB(
                pos.getX(), pos.getY() - 0.1, pos.getZ(),
                pos.getX() + 1.0, pos.getY(),   pos.getZ() + 1.0);

        DamageSource drillDamage = level.damageSources().generic();
        float baseDmg = (float) OretoryConfig.BASE_DAMAGE_AMOUNT.get().doubleValue();

        float speedScale = 1.0f;
        if (OretoryConfig.DAMAGE_SCALES_WITH_SPEED.get() && tier != null) {
            speedScale = Math.min(2.0f, 1.0f / Math.max(0.1f, tier.speedMultiplier));
        }
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
    // Loot drops (simulated netherite pickaxe — best drops)
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
                                      @Nullable BlockPos above,
                                      @Nullable BlockPos below) {
        if (above != null) doCompletionBurst(level, above);
        if (below != null) doCompletionBurst(level, below);
    }

    private void doCompletionBurst(ServerLevel level, BlockPos targetPos) {
        BlockState targetState = level.getBlockState(targetPos);
        int color = getOreColor(targetState);
        // Fallback color: white
        if (color == 0) color = 0xFFFFFF;

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >>  8) & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(r, g, b), 1.2f);

        for (int i = 0; i < 12; i++) {
            double px = targetPos.getX() + 0.1 + level.random.nextDouble() * 0.8;
            double py = targetPos.getY() + (targetPos.getY() > worldPosition.getY() ? 0.0 : 1.0);
            double pz = targetPos.getZ() + 0.1 + level.random.nextDouble() * 0.8;
            double vx = (level.random.nextDouble() - 0.5) * 0.5;
            double vy = level.random.nextDouble() * 0.4;
            double vz = (level.random.nextDouble() - 0.5) * 0.5;
            level.sendParticles(dust, px, py, pz, 1, vx, vy, vz, 0.0);
        }
    }

    // =========================================================================
    // CLIENT TICK
    // =========================================================================
    private void handleClientTick(Level level, BlockState state) {
        int tickAge = (int) level.getGameTime();

        boolean isMining = ponderForceMining || state.getValue(MinerBlock.MINING);
        boolean isLit    = ponderForceLit    || state.getValue(MinerBlock.LIT);

        // Animation states
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

        // --- Sound management ---

        // Idle hum: plays whenever lit (has fuel)
        if (isLit) {
            if (idleSoundInstance == null || idleSoundInstance.isStopped()) {
                idleSoundInstance = new MinerSoundInstance(
                        this, Oretory.MINER_IDLE.get(), false, 1.0f);
                Minecraft.getInstance().getSoundManager().play(idleSoundInstance);
            }
        } else {
            if (idleSoundInstance != null && !idleSoundInstance.isStopped()) {
                idleSoundInstance.fadeOut();
            }
        }

        // Mining loop: only while mining, pitch-shifted by fuel speed
        if (isMining) {
            float pitch = getMiningPitch();
            if (miningSoundInstance == null || miningSoundInstance.isStopped()) {
                miningSoundInstance = new MinerSoundInstance(
                        this, Oretory.MINER_MINING.get(), true, pitch);
                Minecraft.getInstance().getSoundManager().play(miningSoundInstance);
            } else {
                miningSoundInstance.setTargetPitch(pitch);
            }
        } else {
            if (miningSoundInstance != null && !miningSoundInstance.isStopped()) {
                miningSoundInstance.fadeOut();
            }
        }
    }

    /**
     * Pitch for mining sound based on fuel speed tier.
     * Faster fuel (lower speedMultiplier) → higher pitch.
     * Range: 0.8 (very slow) to 1.6 (insane/nether star).
     */
    private float getMiningPitch() {
        int encoded = data.get(MinerMenu.DATA_SPEED);
        if (encoded == 0) return 1.0f;
        float speedMult = encoded / 100f;
        float t = Math.clamp((speedMult - 0.10f) / 1.30f, 0.0f, 1.0f);
        return 1.6f - t * 0.8f;
    }

    // -------------------------------------------------------------------------
    // Ore-specific dust particles (client)
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
                        (level.random.nextDouble() - 0.5) * 0.2,
                        -0.15,
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

        BlockState state = getBlockState();
        ItemStack  fuelStack = inventory.getStackInSlot(0);
        FuelTier   tier      = FuelTier.getTier(fuelStack);
        MinerRedstoneMode mode = state.hasProperty(MinerBlock.REDSTONE_MODE)
                ? state.getValue(MinerBlock.REDSTONE_MODE)
                : MinerRedstoneMode.IGNORED;

        // Header
        CreateLang.translate("gui.goggles.block_entity_type")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        // Status
        String statusStr = switch (status) {
            case STATUS_OFF         -> "No Fuel";
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
        CreateLang.builder()
                .text(ChatFormatting.GRAY, "Status: ")
                .text(statusColor, statusStr)
                .forGoggles(tooltip, 1);

        // Fuel + speed
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

        // Ore targets
        Level level = getLevel();
        if (level != null) {
            BlockPos abovePos = worldPosition.above();
            BlockPos belowPos = worldPosition.below();
            boolean oreAbove  = level.getBlockState(abovePos).is(ORES_TAG);
            boolean oreBelow  = level.getBlockState(belowPos).is(ORES_TAG);

            String aboveName = oreAbove
                    ? level.getBlockState(abovePos).getBlock().getName().getString() : "None";
            String belowName = oreBelow
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

        // Redstone mode
        CreateLang.builder()
                .text(ChatFormatting.GRAY, "Redstone: ")
                .text(ChatFormatting.YELLOW, mode.getDisplayName())
                .forGoggles(tooltip, 1);

        return true;
    }

    // =========================================================================
    // ITEM HANDLER CAPABILITY (sided)
    // =========================================================================
    /**
     * Sided item handler:
     *   TOP    → fuel input only
     *   BOTTOM → output extraction only
     *   SIDES  → combined (pipes can push fuel and pull output)
     *   null   → full inventory (GUI)
     */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null)            return inventory;
        if (side == Direction.UP)    return fuelHandler;
        if (side == Direction.DOWN)  return outputHandler;
        return combinedHandler;
    }

    // =========================================================================
    // NBT
    // =========================================================================
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory",     inventory.serializeNBT(registries));
        tag.putInt("miningProgress", miningProgress);
        tag.putInt("minerStatus",    status);
        tag.putInt("noOreTicks",     noOreTicks);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        miningProgress  = tag.getInt("miningProgress");
        status          = tag.getInt("minerStatus");
        noOreTicks      = tag.getInt("noOreTicks");
        cachedFuelTier  = null; // re-resolve from inventory on next tick
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
    // INNER: single-slot ItemStackHandler wrapper
    // =========================================================================
    private record ItemStackHandlerWrapper(
            ItemStackHandler handler, int slot,
            boolean canInsert, boolean canExtract
    ) implements IItemHandlerModifiable {

        @Override public int getSlots()                                                        { return 1; }
        @Override public @NotNull ItemStack getStackInSlot(int s)                              { return handler.getStackInSlot(slot); }
        @Override public int getSlotLimit(int s)                                               { return handler.getSlotLimit(slot); }
        @Override public boolean isItemValid(int s, @NotNull ItemStack stack)                  { return canInsert && handler.isItemValid(slot, stack); }
        @Override public void setStackInSlot(int s, @NotNull ItemStack stack)                  { handler.setStackInSlot(slot, stack); }
        @Override public @NotNull ItemStack insertItem(int s, @NotNull ItemStack stack, boolean sim)  { return canInsert  ? handler.insertItem(slot, stack, sim)   : stack; }
        @Override public @NotNull ItemStack extractItem(int s, int amount, boolean sim)        { return canExtract ? handler.extractItem(slot, amount, sim) : ItemStack.EMPTY; }
    }
}