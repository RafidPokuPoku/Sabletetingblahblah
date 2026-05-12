package com.rafid.oretory;

import net.neoforged.neoforge.common.ModConfigSpec;

public class OretoryConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // -------------------------------------------------------------------------
    // Mining
    // -------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue BASE_TICKS_PER_CYCLE;
    public static final ModConfigSpec.IntValue NO_ORE_TIMEOUT_TICKS;

    // -------------------------------------------------------------------------
    // Damage
    // -------------------------------------------------------------------------
    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_DAMAGE;
    public static final ModConfigSpec.DoubleValue  BASE_DAMAGE_AMOUNT;
    public static final ModConfigSpec.BooleanValue DAMAGE_SCALES_WITH_SPEED;

    // -------------------------------------------------------------------------
    // Particles
    // -------------------------------------------------------------------------
    public static final ModConfigSpec.BooleanValue ENABLE_COMPLETION_PARTICLES;
    public static final ModConfigSpec.IntValue     PARTICLE_DENSITY;

    // -------------------------------------------------------------------------
    // Goggles
    // -------------------------------------------------------------------------
    public static final ModConfigSpec.BooleanValue ENABLE_GOGGLES_OVERLAY;

    static final ModConfigSpec SPEC;

    static {
        BUILDER.push("mining");
        BASE_TICKS_PER_CYCLE = BUILDER
                .comment("Base ticks per mining cycle when using Coal/Charcoal (speed multiplier 1.0).",
                        "Lower = faster. Minimum enforced value is 20 ticks (1 second).")
                .defineInRange("base_ticks_per_cycle", 200, 20, 2000);
        NO_ORE_TIMEOUT_TICKS = BUILDER
                .comment("Ticks with no ore present before the miner plays an 'idle click' and shows NO_ORE status.",
                        "Set to 0 to disable the timeout entirely.")
                .defineInRange("no_ore_timeout_ticks", 60, 0, 1200);
        BUILDER.pop();

        BUILDER.push("damage");
        ENABLE_ENTITY_DAMAGE = BUILDER
                .comment("Whether the miner's drills damage entities directly above or below while mining.",
                        "Crouching players are always immune regardless of this setting.")
                .define("enable_entity_damage", true);
        BASE_DAMAGE_AMOUNT = BUILDER
                .comment("Base damage dealt per hit (damage units; 2 = 1 heart).")
                .defineInRange("base_damage_amount", 4.0, 0.0, 40.0);
        DAMAGE_SCALES_WITH_SPEED = BUILDER
                .comment("If true, faster fuels deal slightly more damage (up to 2x base).")
                .define("damage_scales_with_speed", true);
        BUILDER.pop();

        BUILDER.push("particles");
        ENABLE_COMPLETION_PARTICLES = BUILDER
                .comment("Burst of colored particles when a mining cycle completes.")
                .define("enable_completion_particles", true);
        PARTICLE_DENSITY = BUILDER
                .comment("Ore-colored dust particles spawned per tick while mining (per active face).")
                .defineInRange("particle_density", 3, 0, 20);
        BUILDER.pop();

        BUILDER.push("goggles");
        ENABLE_GOGGLES_OVERLAY = BUILDER
                .comment("Show miner information when wearing Create Engineer's Goggles.")
                .define("enable_goggles_overlay", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}