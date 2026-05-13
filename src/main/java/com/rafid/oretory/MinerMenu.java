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

public class MinerMenu extends AbstractContainerMenu {

    public static final int DATA_STATUS    = 0;
    public static final int DATA_PROGRESS  = 1;
    public static final int DATA_RSMODE    = 2;
    public static final int DATA_SPEED     = 3;
    public static final int DATA_THRESHOLD = 4;
    public static final int DATA_COUNT     = 5;

    private final ContainerData data;
    private BlockPos blockPos = BlockPos.ZERO;

    // Client-side constructor — reads BlockPos from the extra buf sent by the server.
    public MinerMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, new ItemStackHandler(2), new SimpleContainerData(DATA_COUNT));
        this.blockPos = buf.readBlockPos();
    }

    // Server-side constructor.
    public MinerMenu(int containerId, Inventory playerInv, IItemHandler itemHandler, ContainerData data) {
        super(Oretory.MINER_MENU.get(), containerId);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;

        addDataSlots(data);

        addSlot(new SlotItemHandler(itemHandler, 0, 44, 35) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return FuelTier.isValidFuel(stack);
            }
        });

        addSlot(new SlotItemHandler(itemHandler, 1, 116, 35) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }
        });

        for (int row = 0; row < 3; ++row)
            for (int col = 0; col < 9; ++col)
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));

        for (int k = 0; k < 9; ++k)
            addSlot(new Slot(playerInv, k, 8 + k * 18, 142));
    }

    public void setBlockPos(BlockPos pos) { this.blockPos = pos; }
    public BlockPos getBlockPos()         { return blockPos; }

    public int getStatus()          { return data.get(DATA_STATUS);    }
    public int getMiningProgress()  { return data.get(DATA_PROGRESS);  }
    public int getRsMode()          { return data.get(DATA_RSMODE);    }
    public int getSpeedEncoded()    { return data.get(DATA_SPEED);     }
    public int getOutputThreshold() { return Math.max(1, data.get(DATA_THRESHOLD)); }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < 2) {
            if (!moveItemStackTo(stack, 2, 38, true)) return ItemStack.EMPTY;
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
    public boolean stillValid(@NotNull Player player) {
        return true;
    }
}