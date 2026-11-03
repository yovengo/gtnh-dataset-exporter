package dev.yovengo.gtnhdatasetexporter;

import net.minecraftforge.client.ClientCommandHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import dev.yovengo.gtnhdatasetexporter.client.ExportCommand;
import dev.yovengo.gtnhdatasetexporter.client.ExportController;
import dev.yovengo.gtnhdatasetexporter.client.NEIExporterButton;

public final class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        Config.load(event.getSuggestedConfigurationFile());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        ExportController controller = new ExportController();
        FMLCommonHandler.instance()
            .bus()
            .register(controller);
        ClientCommandHandler.instance.registerCommand(new ExportCommand(controller));
        NEIExporterButton.install(controller);
    }
}
