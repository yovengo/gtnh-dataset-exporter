package dev.yovengo.gtnhdatasetexporter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = DatasetExporter.MOD_ID,
    name = "GTNH Dataset Exporter",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    dependencies = "required-after:NotEnoughItems;required-after:gregtech;required-after:Thaumcraft")
public final class DatasetExporter {

    public static final String MOD_ID = "gtnhdatasetexporter";
    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    @SidedProxy(
        clientSide = "dev.yovengo.gtnhdatasetexporter.ClientProxy",
        serverSide = "dev.yovengo.gtnhdatasetexporter.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
