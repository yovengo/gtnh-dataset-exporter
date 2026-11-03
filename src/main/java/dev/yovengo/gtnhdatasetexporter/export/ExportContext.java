package dev.yovengo.gtnhdatasetexporter.export;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.item.ItemStack;

import dev.yovengo.gtnhdatasetexporter.data.Json;
import dev.yovengo.gtnhdatasetexporter.data.StackIdentity;

public final class ExportContext {

    public final CandidateCatalog catalog;
    public final ExportOptions options;
    public final Map<String, Long> counts = new TreeMap<>();
    public final Map<String, Object> sources = new TreeMap<>();
    public DatasetWriter.Batch batch;
    public int errors;
    public int unsupportedRecipes;
    public String source = "initialization";
    public String subject;

    public ExportContext(ExportOptions options, Path spoolDirectory) {
        this.options = options;
        this.catalog = new CandidateCatalog(spoolDirectory);
    }

    public void count(String key) {
        counts.put(key, counts.getOrDefault(key, 0L) + 1);
    }

    public StackIdentity discover(ItemStack stack) {
        return discover(stack, null);
    }

    public StackIdentity discover(ItemStack stack, Map<String, Object> gregtechMaterial) {
        if (stack == null) return null;
        Object registered = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
        subject = (registered == null ? stack.getItem()
            .getClass()
            .getName() + " (unregistered)" : registered) + "@" + stack.getItemDamage();
        count(source + ".observed");
        StackIdentity identity = catalog.add(stack, source, gregtechMaterial);
        if (identity.wildcard()) count(source + ".wildcards");
        return identity;
    }

    public Object stack(ItemStack stack, String matching) {
        if (stack == null) return null;
        StackIdentity identity = discover(stack);
        return Json.object(
            "kind",
            "item",
            "itemId",
            identity.wildcard() ? null : identity.id,
            "registryName",
            identity.registryName,
            "metadata",
            identity.wildcard() ? "*" : identity.metadata,
            "nbt",
            identity.nbt,
            "amount",
            stack.stackSize,
            "consumed",
            stack.stackSize != 0,
            "matching",
            matching);
    }

    public void error(String operation, String itemId, Throwable error) {
        errors++;
        count(source + ".errors");
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        String text = trace.toString();
        batch.add(
            "errors",
            Json.object(
                "source",
                source,
                "operation",
                operation,
                "subject",
                subject,
                "itemId",
                itemId,
                "exception",
                error.getClass()
                    .getName(),
                "message",
                error.getMessage(),
                "stackTrace",
                text.substring(0, Math.min(text.length(), 8192))));
    }

    public void unsupported(Object recipe) {
        unsupportedRecipes++;
        count(
            "unsupportedRecipes." + recipe.getClass()
                .getName());
    }
}
