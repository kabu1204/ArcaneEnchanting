package com.arcaneenchanting.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import com.arcaneenchanting.ArcaneEnchanting;
import com.arcaneenchanting.tile.TileArcaneTable;

import cpw.mods.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {

    public static final int GUI_ARCANE_TABLE = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_ARCANE_TABLE && world.getTileEntity(x, y, z) instanceof TileArcaneTable) {
            return new ContainerArcaneTable(player.inventory, world, x, y, z);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_ARCANE_TABLE && world.getTileEntity(x, y, z) instanceof TileArcaneTable) {
            return ArcaneEnchanting.proxy.createTableGui(new ContainerArcaneTable(player.inventory, world, x, y, z));
        }
        return null;
    }
}
