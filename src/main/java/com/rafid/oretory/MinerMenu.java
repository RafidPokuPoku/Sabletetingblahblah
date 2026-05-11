package com.rafid.oretory;

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
    private final ContainerData data;

    // Client-side constructor
    public MinerMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, new ItemStackHandler(2), new SimpleContainerData(1));
    }

    // Server-side constructor (The one called by the Block Entity)
    public MinerMenu(int containerId, Inventory playerInv, IItemHandler itemHandler, ContainerData data) {
        super(Oretory.MINER_MENU.get(), containerId);
        checkContainerDataCount(data, 1);
        this.data = data;

        // Sync the data slot so the client knows the status
        this.addDataSlots(data);

        // Slot 0: Input/Fuel
        this.addSlot(new SlotItemHandler(itemHandler, 0, 44, 35));

        // Slot 1: Output
        this.addSlot(new SlotItemHandler(itemHandler, 1, 116, 35) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }
        });

        // Player Inventory
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInv, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        // Player Hotbar
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInv, k, 8 + k * 18, 142));
        }
    }

    // This method allows the Screen to read the status (0, 1, or 2)
    public int getStatus() {
        return this.data.get(0);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemStack = stackInSlot.copy();

            if (index < 2) {
                if (!this.moveItemStackTo(stackInSlot, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(stackInSlot, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemStack;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }
}