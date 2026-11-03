package dev.yovengo.gtnhdatasetexporter.export;

import java.util.Map;

import dev.yovengo.gtnhdatasetexporter.data.Json;

public final class ExportOptions {

    public final boolean aspects;
    public final boolean recipes;
    public final boolean oreDictionary;
    public final boolean icons;
    public final boolean reuseIcons;
    public final int iconSize;
    public final int budgetMillis;

    public ExportOptions(boolean aspects, boolean recipes, boolean oreDictionary, boolean icons, boolean reuseIcons,
        int iconSize, int budgetMillis) {
        if (iconSize != 32 && iconSize != 64 && iconSize != 128)
            throw new IllegalArgumentException("Invalid icon size");
        if (budgetMillis < 1 || budgetMillis > 10) throw new IllegalArgumentException("Invalid frame budget");
        this.aspects = aspects;
        this.recipes = recipes;
        this.oreDictionary = oreDictionary;
        this.icons = icons;
        this.reuseIcons = reuseIcons;
        this.iconSize = iconSize;
        this.budgetMillis = budgetMillis;
    }

    public Map<String, Object> record() {
        return Json.object(
            "items",
            true,
            "aspects",
            aspects,
            "recipes",
            recipes,
            "oreDictionary",
            oreDictionary,
            "icons",
            icons,
            "reuseIcons",
            reuseIcons,
            "iconSize",
            iconSize,
            "frameBudgetMillis",
            budgetMillis);
    }
}
