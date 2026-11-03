package com.arcaneenchanting.inventory;

import java.util.Arrays;
import java.util.List;

import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.arcaneenchanting.CommonProxy;
import com.arcaneenchanting.Config;
import com.arcaneenchanting.tile.TileArcaneTable;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Server-authoritative container of the Arcane Enchanting Table.
 *
 * <p>
 * The option data is mirrored to the client through the vanilla window-property channel
 * ({@link ICrafting#sendProgressBarUpdate}), so no custom network packets are needed. Button clicks are sent with
 * the vanilla enchant packet: ids 0..2 select an option, {@link #ACTION_RESCRY} rerolls the options.
 */
public class ContainerArcaneTable extends Container {

    public static final int ACTION_RESCRY = 3;

    // Synchronised data layout.
    public static final int D_LEVEL = 0; // 3 values
    public static final int D_CLUE_ID = 3; // 3 values, -1 if none
    public static final int D_CLUE_LEVEL = 6; // 3 values
    public static final int D_HIDDEN = 9; // 3 values: number of further (hidden) enchantments
    public static final int D_POWER = 12;
    public static final int D_NAME_SEED = 13;
    public static final int D_COUNT = 14;

    // GUI layout (shared with GuiArcaneTable).
    public static final int SLOT_X = 35;
    public static final int SLOT_Y = 50;
    public static final int INV_Y = 140;

    public final World world;
    public final int posX, posY, posZ;
    public final IInventory tableInventory;
    public final int[] data = new int[D_COUNT];

    private final EntityPlayer player;
    private final int[] lastSent = new int[D_COUNT];
    private EnchantOptions options;

    public ContainerArcaneTable(InventoryPlayer playerInv, World world, int x, int y, int z) {
        this.world = world;
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.player = playerInv.player;
        this.tableInventory = new InventoryBasic("ArcaneTable", true, 1) {

            @Override
            public int getInventoryStackLimit() {
                return 1;
            }

            @Override
            public void markDirty() {
                super.markDirty();
                ContainerArcaneTable.this.onCraftMatrixChanged(this);
            }
        };

        Arrays.fill(lastSent, Integer.MIN_VALUE);
        clearOptionData();

        addSlotToContainer(new Slot(tableInventory, 0, SLOT_X, SLOT_Y) {

            @Override
            public boolean isItemValid(ItemStack stack) {
                return true;
            }

            @Override
            public int getSlotStackLimit() {
                return 1;
            }
        });

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, INV_Y + 58));
        }

        if (!world.isRemote) {
            data[D_POWER] = Math.min(EnchantOptions.computePower(world, x, y, z), 99);
        }
    }

    public TileArcaneTable getTile() {
        TileEntity te = world.getTileEntity(posX, posY, posZ);
        return te instanceof TileArcaneTable ? (TileArcaneTable) te : null;
    }

    // ------------------------------------------------------------------ option computation (server)

    private void clearOptionData() {
        for (int i = 0; i < EnchantOptions.COUNT; ++i) {
            data[D_LEVEL + i] = 0;
            data[D_CLUE_ID + i] = -1;
            data[D_CLUE_LEVEL + i] = 0;
            data[D_HIDDEN + i] = 0;
        }
    }

    private void recompute() {
        ItemStack stack = tableInventory.getStackInSlot(0);
        int power = EnchantOptions.computePower(world, posX, posY, posZ);
        long seed = EnchantOptions.getSeed(player);
        options = EnchantOptions.compute(power, stack, seed);

        clearOptionData();
        data[D_POWER] = Math.min(power, 99);
        data[D_NAME_SEED] = (int) (seed & 0x7FFF);
        if (options == null) {
            return;
        }
        for (int i = 0; i < EnchantOptions.COUNT; ++i) {
            if (!options.isAvailable(i)) {
                continue;
            }
            List<EnchantmentData> list = options.results[i];
            EnchantmentData clue = list.get(0);
            data[D_LEVEL + i] = options.levels[i];
            data[D_CLUE_ID + i] = clue.enchantmentobj.effectId;
            data[D_CLUE_LEVEL + i] = clue.enchantmentLevel;
            data[D_HIDDEN + i] = list.size() - 1;
        }
    }

    @Override
    public void onCraftMatrixChanged(IInventory inv) {
        if (inv == tableInventory && !world.isRemote) {
            recompute();
            detectAndSendChanges();
        }
    }

    // ------------------------------------------------------------------ synchronisation

    @Override
    public void addCraftingToCrafters(ICrafting crafter) {
        super.addCraftingToCrafters(crafter);
        for (int i = 0; i < D_COUNT; ++i) {
            crafter.sendProgressBarUpdate(this, i, data[i]);
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        for (int i = 0; i < D_COUNT; ++i) {
            if (lastSent[i] != data[i]) {
                for (Object o : crafters) {
                    ((ICrafting) o).sendProgressBarUpdate(this, i, data[i]);
                }
                lastSent[i] = data[i];
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int id, int value) {
        if (id >= 0 && id < D_COUNT) {
            data[id] = value;
        }
    }

    // ------------------------------------------------------------------ actions

    @Override
    public boolean enchantItem(EntityPlayer player, int action) {
        if (world.isRemote) {
            return false;
        }
        ItemStack stack = tableInventory.getStackInSlot(0);
        TileArcaneTable tile = getTile();
        if (stack == null || options == null || tile == null || tile.isRitualActive()) {
            return false;
        }
        boolean creative = player.capabilities.isCreativeMode;

        if (action == ACTION_RESCRY) {
            int cost = Config.rescryCost;
            if (!creative && player.experienceLevel < cost) {
                return false;
            }
            if (!creative && cost > 0) {
                player.addExperienceLevel(-cost);
            }
            EnchantOptions.advanceSeed(player);
            recompute();
            detectAndSendChanges();
            tile.onRescry();
            return true;
        }

        if (!options.isAvailable(action)) {
            return false;
        }
        int level = options.levels[action];
        if (!creative && player.experienceLevel < level) {
            return false;
        }
        if (!creative) {
            player.addExperienceLevel(-level);
        }
        List<EnchantmentData> result = options.results[action];
        ItemStack taken = stack.copy();
        tableInventory.setInventorySlotContents(0, null);
        EnchantOptions.advanceSeed(player);
        tile.beginRitual(taken, result, action, player);
        return true;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return world.getBlock(posX, posY, posZ) == CommonProxy.arcaneTable
            && player.getDistanceSq(posX + 0.5D, posY + 0.5D, posZ + 0.5D) <= 64.0D;
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        if (!world.isRemote) {
            ItemStack stack = tableInventory.getStackInSlotOnClosing(0);
            if (stack != null && !player.inventory.addItemStackToInventory(stack)) {
                player.dropPlayerItemWithRandomChoice(stack, false);
            }
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();

        if (index == 0) {
            if (!mergeItemStack(stack, 1, 37, true)) {
                return null;
            }
        } else {
            Slot tableSlot = (Slot) inventorySlots.get(0);
            if (tableSlot.getHasStack() || !tableSlot.isItemValid(stack)) {
                return null;
            }
            ItemStack one = stack.copy();
            one.stackSize = 1;
            stack.stackSize--;
            tableSlot.putStack(one);
        }

        if (stack.stackSize <= 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        if (stack.stackSize == original.stackSize) {
            return null;
        }
        slot.onPickupFromSlot(player, stack);
        return original;
    }
}
