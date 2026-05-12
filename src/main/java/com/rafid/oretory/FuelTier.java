package com.rafid.oretory;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Defines fuel tiers for the Miner.
 *
 * Speed: ticksPerCycle = max(20, (int)(baseTicks * speedMultiplier))
 * Lower multiplier = faster mining. 1 fuel consumed per 1 output item.
 */
public class FuelTier {

    private static final List<FuelTier> TIERS = new ArrayList<>();

    // --- Tier Definitions (checked in order, first match wins) ---

    /** Nether Star — insanely fast, massive double-drop bonus */
    public static final FuelTier NETHER_STAR = register(
            stack -> stack.is(Items.NETHER_STAR),
            0.10f, 0.50f, ConsumeAction.NONE, "Nether Star", 0xFFFFAA
    );

    /** Create Blaze Cake — very fast, strong bonus */
    public static final FuelTier BLAZE_CAKE = register(
            stack -> isCreateItem(stack, "blaze_cake"),
            0.40f, 0.25f, ConsumeAction.NONE, "Blaze Cake", 0xFF6600
    );

    /** Blaze Rod — fast, double-drop bonus */
    public static final FuelTier BLAZE_ROD = register(
            stack -> stack.is(Items.BLAZE_ROD),
            0.60f, 0.15f, ConsumeAction.NONE, "Blaze Rod", 0xFFAA00
    );

    /** Lava Bucket — fast, returns empty bucket */
    public static final FuelTier LAVA_BUCKET = register(
            stack -> stack.is(Items.LAVA_BUCKET),
            0.65f, 0.0f, ConsumeAction.RETURN_BUCKET, "Lava Bucket", 0xFF4400
    );

    /** Blaze Powder — medium-fast, small bonus */
    public static final FuelTier BLAZE_POWDER = register(
            stack -> stack.is(Items.BLAZE_POWDER),
            0.75f, 0.08f, ConsumeAction.NONE, "Blaze Powder", 0xFFBB44
    );

    /** Create Cinder Flour — medium */
    public static final FuelTier CINDER_FLOUR = register(
            stack -> isCreateItem(stack, "cinder_flour"),
            0.85f, 0.0f, ConsumeAction.NONE, "Cinder Flour", 0xCCAA88
    );

    /** Coal Block — baseline speed */
    public static final FuelTier COAL_BLOCK = register(
            stack -> stack.is(Items.COAL_BLOCK),
            1.0f, 0.0f, ConsumeAction.NONE, "Coal Block", 0x444444
    );

    /** Coal / Charcoal — baseline */
    public static final FuelTier COAL = register(
            stack -> stack.is(Items.COAL) || stack.is(Items.CHARCOAL),
            1.0f, 0.0f, ConsumeAction.NONE, "Coal", 0x555555
    );

    /** Dried Kelp Block — slow */
    public static final FuelTier DRIED_KELP_BLOCK = register(
            stack -> stack.is(Items.DRIED_KELP_BLOCK),
            1.40f, 0.0f, ConsumeAction.NONE, "Dried Kelp Block", 0x88AA44
    );

    /** Generic fallback for any other valid vanilla fuel (wood, planks, etc.) */
    public static final FuelTier GENERIC = register(
            stack -> stack.getBurnTime(null) > 0,
            1.25f, 0.0f, ConsumeAction.NONE, "Fuel", 0x886644
    );

    // --- Fields ---

    public final Predicate<ItemStack> matcher;
    /** Multiplier applied to base ticks/cycle. < 1.0 = faster. */
    public final float speedMultiplier;
    /** 0.0 to 1.0 chance of outputting an extra copy of the drop. */
    public final float doubleDropChance;
    public final ConsumeAction consumeAction;
    /** Display name for goggles/GUI. */
    public final String displayName;
    /** Color hint for particles/GUI (RGB). */
    public final int color;

    private FuelTier(Predicate<ItemStack> matcher, float speedMultiplier, float doubleDropChance,
                     ConsumeAction consumeAction, String displayName, int color) {
        this.matcher = matcher;
        this.speedMultiplier = speedMultiplier;
        this.doubleDropChance = doubleDropChance;
        this.consumeAction = consumeAction;
        this.displayName = displayName;
        this.color = color;
    }

    private static FuelTier register(Predicate<ItemStack> matcher, float speedMult, float doubleDropChance,
                                     ConsumeAction action, String name, int color) {
        FuelTier tier = new FuelTier(matcher, speedMult, doubleDropChance, action, name, color);
        TIERS.add(tier);
        return tier;
    }

    /**
     * Returns the FuelTier for the given ItemStack, or null if the item is not a valid fuel.
     */
    @Nullable
    public static FuelTier getTier(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (FuelTier tier : TIERS) {
            if (tier.matcher.test(stack)) return tier;
        }
        return null;
    }

    /** Returns true if the given stack is a valid fuel for the Miner. */
    public static boolean isValidFuel(ItemStack stack) {
        return getTier(stack) != null;
    }

    /** Returns how many ticks one cycle takes for this tier given the config base speed. */
    public int getTicksPerCycle() {
        int base = OretoryConfig.BASE_TICKS_PER_CYCLE.get();
        return Math.max(20, (int) (base * speedMultiplier));
    }

    /** Returns a human-readable speed label. */
    public String getSpeedLabel() {
        float m = speedMultiplier;
        if (m <= 0.15f) return "Insane";
        if (m <= 0.45f) return "Blazing";
        if (m <= 0.65f) return "Fast";
        if (m <= 0.80f) return "Medium-Fast";
        if (m <= 0.95f) return "Medium";
        if (m <= 1.05f) return "Normal";
        if (m <= 1.30f) return "Slow";
        return "Very Slow";
    }

    private static boolean isCreateItem(ItemStack stack, String id) {
        var key = stack.getItem().builtInRegistryHolder().key();
        return key.location().getNamespace().equals("create")
                && key.location().getPath().equals(id);
    }

    public enum ConsumeAction {
        NONE,
        /** Returns an empty bucket when a Lava Bucket is consumed. */
        RETURN_BUCKET
    }
}