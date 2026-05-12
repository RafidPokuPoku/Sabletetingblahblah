package com.rafid.oretory;

import net.minecraft.util.StringRepresentable;

/**
 * Controls how the Miner responds to redstone signals.
 * Cycle through modes by right-clicking with a Create Wrench,
 * or via the button in the GUI.
 */
public enum MinerRedstoneMode implements StringRepresentable {
    /** Runs freely, ignores all redstone signals. (Default) */
    IGNORED("ignored"),
    /** Only runs when receiving a redstone signal. */
    ENABLED("enabled"),
    /** Runs unless receiving a redstone signal. */
    DISABLED("disabled");

    private final String serializedName;

    MinerRedstoneMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public MinerRedstoneMode next() {
        MinerRedstoneMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public String getDisplayName() {
        return switch (this) {
            case IGNORED  -> "Ignored";
            case ENABLED  -> "Enabled";
            case DISABLED -> "Disabled";
        };
    }

    public String getDescription() {
        return switch (this) {
            case IGNORED  -> "Runs freely";
            case ENABLED  -> "Runs with signal";
            case DISABLED -> "Stops with signal";
        };
    }
}