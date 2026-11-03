package dev.yovengo.gtnhdatasetexporter;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import dev.yovengo.gtnhdatasetexporter.export.ExportOptions;

public final class Config {

    private static Configuration configuration;
    private static ExportOptions current = new ExportOptions(true, true, true, true, true, 64, 4);

    private Config() {}

    public static void load(File file) {
        configuration = new Configuration(file);
        int size = configuration.getInt("iconSize", "export", 64, 32, 128, "PNG dimensions: 32, 64 or 128.");
        if (size != 32 && size != 64 && size != 128) size = 64;
        int budget = configuration.getInt("frameBudgetMillis", "export", 4, 1, 10, "Client work budget per frame.");
        current = new ExportOptions(
            flag("aspects"),
            flag("recipes"),
            flag("oreDictionary"),
            flag("icons"),
            flag("reuseIcons"),
            size,
            budget);
        if (configuration.hasChanged()) configuration.save();
    }

    private static boolean flag(String name) {
        return configuration.getBoolean(name, "export", true, "Default export selection.");
    }

    public static ExportOptions options() {
        return current;
    }

    public static void save(ExportOptions options) {
        current = options;
        configuration.get("export", "aspects", true)
            .set(options.aspects);
        configuration.get("export", "recipes", true)
            .set(options.recipes);
        configuration.get("export", "oreDictionary", true)
            .set(options.oreDictionary);
        configuration.get("export", "icons", true)
            .set(options.icons);
        configuration.get("export", "reuseIcons", true)
            .set(options.reuseIcons);
        configuration.get("export", "iconSize", 64)
            .set(options.iconSize);
        configuration.get("export", "frameBudgetMillis", 4)
            .set(options.budgetMillis);
        configuration.save();
    }
}
