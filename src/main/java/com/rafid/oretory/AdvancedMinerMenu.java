package com.rafid.oretory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class AdvancedMinerMenu extends AbstractContainerMenu {

    // ContainerData indices
    public static final int DATA_STATUS        = 0;
    public static final int DATA_PROGRESS      = 1;
    public static final int DATA_RSMODE        = 2;
    public static final int DATA_SPEED         = 3;
    public static final int DATA_THRESHOLD_BOT = 4;
    public static final int DATA_THRESHOLD_TOP = 5;
    public static final int DATA_COUNT         = 6;

    // Layout:
    //   Fuel slot  — left side, vertically centred
    //   Bot output — right side, lower
    //   Top output — right side, directly above Bot (18px gap = standard slot spacing)
    public static final int FUEL_SLOT_X          = 44;
    public static final int FUEL_SLOT_Y          = 35;

    // Two output slots stacked: Top directly above Bot with 1px gap between borders
    public static final int OUTPUT_BOTTOM_SLOT_X = 116;
    public static final int OUTPUT_BOTTOM_SLOT_Y = 44;   // lower slot
    public static final int OUTPUT_TOP_SLOT_X    = 116;
    public static final int OUTPUT_TOP_SLOT_Y    = 22;   // upper slot (22 px above = 18 slot + 4 label gap)

    private final ContainerData data;
    private BlockPos blockPos = BlockPos.ZERO;

    // Client-side constructor
    public AdvancedMinerMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new ItemStackHandler(3), new SimpleContainerData(DATA_COUNT));
        this.blockPos = buf.readBlockPos();
    }

    // Server-side constructor
    public AdvancedMinerMenu(int containerId, Inventory playerInv, IItemHandler itemHandler, ContainerData data) {
        super(Oretory.ADVANCED_MINER_MENU.get(), containerId);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;

        addDataSlots(data);

        // Slot 0: fuel
        addSlot(new SlotItemHandler(itemHandler, 0, FUEL_SLOT_X, FUEL_SLOT_Y) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return FuelTier.isValidFuel(stack);
            }
        });

        // Slot 1: bottom ore output — output-only
        addSlot(new SlotItemHandler(itemHandler, 1, OUTPUT_BOTTOM_SLOT_X, OUTPUT_BOTTOM_SLOT_Y) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) { return false; }
        });

        // Slot 2: top ore output — output-only
        addSlot(new SlotItemHandler(itemHandler, 2, OUTPUT_TOP_SLOT_X, OUTPUT_TOP_SLOT_Y) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) { return false; }
        });

        // Player inventory (3 rows)
        for (int row = 0; row < 3; ++row)
            for (int col = 0; col < 9; ++col)
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));

        // Player hotbar
        for (int k = 0; k < 9; ++k)
            addSlot(new Slot(playerInv, k, 8 + k * 18, 142));
    }

    public void setBlockPos(BlockPos pos) { this.blockPos = pos; }
    public BlockPos getBlockPos()         { return blockPos; }

    public int getStatus()               { return data.get(DATA_STATUS);        }
    public int getMiningProgress()       { return data.get(DATA_PROGRESS);      }
    public int getRsMode()               { return data.get(DATA_RSMODE);        }
    public int getSpeedEncoded()         { return data.get(DATA_SPEED);         }
    public int getOutputThresholdBot()   { return Math.max(1, data.get(DATA_THRESHOLD_BOT)); }
    public int getOutputThresholdTop()   { return Math.max(1, data.get(DATA_THRESHOLD_TOP)); }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < 3) {
            if (!moveItemStackTo(stack, 3, 39, true)) return ItemStack.EMPTY;
        } else {
            if (FuelTier.isValidFuel(stack)) {
                if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();

        return result;
    }

    @Override
    public boolean stillValid(@NotNull Player player) { return true; }
}