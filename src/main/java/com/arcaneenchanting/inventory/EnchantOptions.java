package com.arcaneenchanting.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.arcaneenchanting.util.ArcaneGeometry;

/**
 * The three enchanting options offered for one item.
 *
 * <p>
 * Unlike the vanilla 1.7.10 table, the options are derived from a per-player seed. The same item therefore always
 * shows the same options until the player enchants something or pays to re-scry. This makes the revealed
 * enchantment (the "clue" shown in the tooltip) trustworthy and removes the free reroll obtained by taking the item
 * out and putting it back.
 */
public final class EnchantOptions {

    public static final int COUNT = 3;
    private static final String SEED_KEY = "ArcaneEnchantingSeed";

    public final int power;
    public final int[] levels = new int[COUNT];
    /** Final enchantments for each option (already reduced for books), or null if the option is empty. */
    public final List<EnchantmentData>[] results;

    @SuppressWarnings("unchecked")
    private EnchantOptions(int power) {
        this.power = power;
        this.results = new List[COUNT];
    }

    public boolean isAvailable(int option) {
        return option >= 0 && option < COUNT && levels[option] > 0 && results[option] != null;
    }

    public static int computePower(World world, int x, int y, int z) {
        return (int) ArcaneGeometry.scan(world, x, y, z, null);
    }

    /** Computes the options for an item. Returns null when the item cannot be enchanted. */
    @SuppressWarnings("unchecked")
    public static EnchantOptions compute(int power, ItemStack stack, long seed) {
        if (stack == null || !stack.isItemEnchantable()) {
            return null;
        }
        EnchantOptions opts = new EnchantOptions(power);
        Random levelRand = new Random(seed);
        for (int i = 0; i < COUNT; ++i) {
            opts.levels[i] = EnchantmentHelper.calcItemStackEnchantability(levelRand, i, power, stack);
        }

        boolean isBook = stack.getItem() == Items.book;
        for (int i = 0; i < COUNT; ++i) {
            if (opts.levels[i] <= 0) {
                opts.levels[i] = 0;
                continue;
            }
            Random listRand = new Random(seed * 31L + 0x5DEECE66DL * (i + 1));
            List<EnchantmentData> list = EnchantmentHelper.buildEnchantmentList(listRand, stack, opts.levels[i]);
            if (list == null || list.isEmpty()) {
                opts.levels[i] = 0;
                continue;
            }
            List<EnchantmentData> copy = new ArrayList<EnchantmentData>(list);
            // Vanilla behaviour for books: one of the rolled enchantments is discarded.
            if (isBook && copy.size() > 1) {
                copy.remove(listRand.nextInt(copy.size()));
            }
            opts.results[i] = copy;
        }
        return opts;
    }

    // ------------------------------------------------------------------ per-player seed

    public static long getSeed(EntityPlayer player) {
        NBTTagCompound tag = persistedTag(player);
        if (!tag.hasKey(SEED_KEY)) {
            tag.setLong(SEED_KEY, player.worldObj.rand.nextLong());
        }
        return tag.getLong(SEED_KEY);
    }

    public static void advanceSeed(EntityPlayer player) {
        persistedTag(player).setLong(SEED_KEY, player.worldObj.rand.nextLong());
    }

    /** The Forge sub-tag that survives death and dimension changes. */
    private static NBTTagCompound persistedTag(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }
}
