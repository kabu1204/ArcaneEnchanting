package com.arcaneenchanting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = ArcaneEnchanting.MODID,
    name = "Arcane Enchanting",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]")
public class ArcaneEnchanting {

    public static final String MODID = "arcaneenchanting";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static ArcaneEnchanting instance;

    @SidedProxy(
        clientSide = "com.arcaneenchanting.client.ClientProxy",
        serverSide = "com.arcaneenchanting.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.load(event.getSuggestedConfigurationFile());
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
