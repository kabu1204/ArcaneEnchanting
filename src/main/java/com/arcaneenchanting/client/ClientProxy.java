package com.arcaneenchanting.client;

import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

import com.arcaneenchanting.CommonProxy;
import com.arcaneenchanting.inventory.ContainerArcaneTable;
import com.arcaneenchanting.tile.TileArcaneTable;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        RenderArcaneTable renderer = new RenderArcaneTable();
        ClientRegistry.bindTileEntitySpecialRenderer(TileArcaneTable.class, renderer);
        MinecraftForgeClient
            .registerItemRenderer(Item.getItemFromBlock(arcaneTable), new ItemRendererArcaneTable(renderer));
    }

    @Override
    public Object createTableGui(ContainerArcaneTable container) {
        return new GuiArcaneTable(container);
    }

    @Override
    public void tickTableClient(TileArcaneTable tile) {
        TableClientTicker.tick(tile);
    }

    @Override
    public void onTableEvent(TileArcaneTable tile, int eventId, int param) {
        TableClientTicker.onEvent(tile, eventId, param);
    }
}
