package com.arcaneenchanting;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.arcaneenchanting.block.BlockArcaneTable;
import com.arcaneenchanting.inventory.ContainerArcaneTable;
import com.arcaneenchanting.inventory.GuiHandler;
import com.arcaneenchanting.tile.TileArcaneTable;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

/** Server-safe setup. The client proxy extends this class and adds rendering. */
public class CommonProxy {

    public static BlockArcaneTable arcaneTable;

    public void preInit(FMLPreInitializationEvent event) {
        arcaneTable = new BlockArcaneTable();
        GameRegistry.registerBlock(arcaneTable, "arcane_table");
        GameRegistry.registerTileEntity(TileArcaneTable.class, ArcaneEnchanting.MODID + ":arcane_table");
    }

    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(ArcaneEnchanting.instance, new GuiHandler());

        // Upgrade recipe: a vanilla enchanting table surrounded by obsidian, lapis and a diamond.
        GameRegistry.addRecipe(
            new ItemStack(arcaneTable),
            "LDL",
            "OTO",
            "OOO",
            'L',
            Blocks.lapis_block,
            'D',
            Items.diamond,
            'O',
            Blocks.obsidian,
            'T',
            Blocks.enchanting_table);
    }

    /** Returns the client GUI for the table container (null on a dedicated server). */
    public Object createTableGui(ContainerArcaneTable container) {
        return null;
    }

    /** Called every client tick for each loaded table. No-op on a dedicated server. */
    public void tickTableClient(TileArcaneTable tile) {}

    /** Called on the client when the server fires a block event for a table. */
    public void onTableEvent(TileArcaneTable tile, int eventId, int param) {}
}
