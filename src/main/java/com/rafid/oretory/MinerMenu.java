package com.rafid.oretory;

import net.minecraft.core.BlockPos;
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

public class MinerMenu extends AbstractContainerMenu {

    // ContainerData indices (must match MinerBlockEntity.data):
    // 0 = status  (0=off, 1=idle, 2=mining, 3=output_full, 4=no_ore)
    // 1 = miningProgress
    // 2 = redstoneMode ordinal
    // 3 = speedMultiplier * 100 (int)
    public static final int DATA_STATUS   = 0;
    public static final int DATA_PROGRESS = 1;
    public static final int DATA_RSMODE   = 2;
    public static final int DATA_SPEED    = 3;
    public static final int DATA_COUNT    = 4;

    private final ContainerData data;

    /**
     * The world position of the Miner block — stored so the redstone-mode
     * packet can include it without needing a separate lookup.
     * Defaults to ZERO on the client side until the server sets it.
     */
    private BlockPos blockPos = BlockPos.ZERO;

    // -------------------------------------------------------------------------
    // Client-side constructor (called by MenuType factory)
    // -------------------------------------------------------------------------
    public MinerMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, new ItemStackHandler(2), new SimpleContainerData(DATA_COUNT));
    }

    // -------------------------------------------------------------------------
    // Server-side constructor (called by MinerBlockEntity.createMenu)
    // -------------------------------------------------------------------------
    public MinerMenu(int containerId, Inventory playerInv, IItemHandler itemHandler, ContainerData data) {
        super(Oretory.MINER_MENU.get(), containerId);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;

        addDataSlots(data);

        // Slot 0: Fuel input — only valid fuels allowed
        addSlot(new SlotItemHandler(itemHandler, 0, 44, 35) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return FuelTier.isValidFuel(stack);
            }
        });

        // Slot 1: Output — players cannot insert (pipes/hoppers extract from bottom face)
        addSlot(new SlotItemHandler(itemHandler, 1, 116, 35) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }
        });

        // Player inventory (3 rows)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar
        for (int k = 0; k < 9; ++k) {
            addSlot(new Slot(playerInv, k, 8 + k * 18, 142));
        }
    }

    // -------------------------------------------------------------------------
    // Block pos (set by server-side BE, read by packet helper on client)
    // -------------------------------------------------------------------------
    public void setBlockPos(BlockPos pos) { this.blockPos = pos; }
    public BlockPos getBlockPos()         { return blockPos; }

    // -------------------------------------------------------------------------
    // Data accessors
    // -------------------------------------------------------------------------
    public int getStatus()         { return data.get(DATA_STATUS);   }
    public int getMiningProgress() { return data.get(DATA_PROGRESS); }
    public int getRsMode()         { return data.get(DATA_RSMODE);   }
    /** speedMultiplier * 100, or 0 if no fuel. */
    public int getSpeedEncoded()   { return data.get(DATA_SPEED);    }

    // -------------------------------------------------------------------------
    // Shift-click
    // -------------------------------------------------------------------------
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < 2) {
            // Machine slots → player inventory
            if (!moveItemStackTo(stack, 2, 38, true)) return ItemStack.EMPTY;
        } else {
            // Player inventory → fuel slot (only valid fuels)
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
    public boolean stillValid(@NotNull Player player) {
        return true;
    }
}