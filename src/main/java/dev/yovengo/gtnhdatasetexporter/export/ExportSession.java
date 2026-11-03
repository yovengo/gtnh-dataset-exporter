package dev.yovengo.gtnhdatasetexporter.export;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import dev.yovengo.gtnhdatasetexporter.Tags;
import dev.yovengo.gtnhdatasetexporter.client.IconRenderer;
import dev.yovengo.gtnhdatasetexporter.data.Json;
import dev.yovengo.gtnhdatasetexporter.export.CandidateCatalog.Candidate;
import dev.yovengo.gtnhdatasetexporter.integration.AspectResolver;
import dev.yovengo.gtnhdatasetexporter.integration.CandidateSources;

public final class ExportSession {

    private final ExportContext context;
    private final DatasetWriter writer;
    private final List<CandidateSources.Task> tasks;
    private final IconRenderer renderer = new IconRenderer();
    private Object world;
    private final long started = System.currentTimeMillis();
    private int taskIndex;
    private Iterator<Candidate> variants;
    private Iterator<Candidate> icons;
    private Candidate pendingIcon;
    private DatasetWriter.Icon probe;
    private boolean cancelled;
    private boolean finishing;
    private boolean completedSources;
    private String phase = "Preparing files";
    private int processed;
    private int rendered;
    private int total;
    private int sourceStartErrors;
    private int sourceStartUnsupported;
    private long finished;
    private boolean released;
    private final Map<String, java.util.Set<String>> lastSources = new java.util.HashMap<>();

    public ExportSession(Minecraft mc, ExportOptions options) {
        world = mc.theWorld;
        String runId = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now()) + "-"
            + UUID.randomUUID()
                .toString()
                .substring(0, 8);
        Path game = mc.mcDataDir.toPath();
        Path directory = game.resolve("gtnh-dataset-exporter/exports")
            .resolve(runId);
        context = new ExportContext(options, directory.resolve("candidate-spool"));
        tasks = CandidateSources.create();
        for (CandidateSources.Task task : tasks) context.sources.put(task.name(), Json.object("status", "not_started"));
        Map<String, Object> manifest = Json.object(
            "schemaVersion",
            "1.0.0",
            "identityVersion",
            1,
            "exporterVersion",
            Tags.VERSION,
            "runId",
            runId,
            "startedAt",
            Instant.now()
                .toString(),
            "options",
            options.record(),
            "environment",
            EnvironmentSnapshot.capture(mc));
        writer = new DatasetWriter(directory, game, manifest);
    }

    public Path directory() {
        return writer.directory;
    }

    public boolean running() {
        return !writer.done;
    }

    public void cancel() {
        cancelled = true;
        writer.cancelPreparation();
    }

    public int warnings() {
        return context.errors;
    }

    public int candidateCount() {
        return context.catalog.prepared() ? total : context.catalog.observed();
    }

    public boolean candidateCountFinal() {
        return context.catalog.prepared();
    }

    public long elapsedSeconds() {
        return ((finished == 0 ? System.currentTimeMillis() : finished) - started) / 1000;
    }

    public java.util.Set<String> sourcesFor(String id) {
        return lastSources.get(id);
    }

    public String phase() {
        if (writer.failure != null) return "Export failed";
        if (writer.done) return cancelled ? "Export cancelled" : "Export complete";
        if (cancelled) return "Cancelling export";
        return phase;
    }

    public String detail() {
        if (writer.failure != null) return writer.failure;
        if (writer.done) {
            String detail = candidateCount() + " variants saved";
            if (context.errors > 0) detail += " | " + context.errors + " warnings logged";
            if (writer.emptyIcons > 0) detail += " | " + writer.emptyIcons + " blank icons";
            return detail;
        }
        if (!writer.ready) return "Fingerprinting mods, configuration and resource packs...";
        if (finishing) return "Closing files and writing checksums...";
        if (!completedSources) return "Source " + (taskIndex + 1)
            + " / "
            + tasks.size()
            + " | "
            + context.catalog.observed()
            + " observations";
        if (!context.catalog.prepared()) return "Consolidating " + context.catalog.partitionsProcessed()
            + " / "
            + CandidateCatalog.partitionCount()
            + " partitions";
        return icons == null ? processed + " / " + total + " variants" : rendered + " / " + total + " icons";
    }

    public double progress() {
        if (writer.done) return 1;
        if (!writer.ready) return 0;
        if (!completedSources) return 0.05 + 0.25 * taskIndex / tasks.size();
        if (!context.catalog.prepared())
            return 0.3 + 0.1 * context.catalog.partitionsProcessed() / CandidateCatalog.partitionCount();
        if (icons == null) return 0.4 + (context.options.icons ? 0.25 : 0.5) * processed / Math.max(1, total);
        return 0.65 + 0.3 * rendered / Math.max(1, total);
    }

    /** Render ticks continue while the modal pauses integrated-server ticks. */
    public void tick(Minecraft mc) {
        if (writer.done) {
            if (!released) {
                finished = System.currentTimeMillis();
                if (context.catalog.prepared()) total = context.catalog.size();
                context.catalog.clear();
                variants = null;
                icons = null;
                pendingIcon = null;
                probe = null;
                tasks.clear();
                world = null;
                renderer.close();
                released = true;
            }
            return;
        }
        if (mc.theWorld != world) cancel();
        if (!writer.ready || finishing || !writer.hasCapacity()) return;
        long deadline = System.nanoTime() + context.options.budgetMillis * 1000000L;
        int units = 0;
        while (System.nanoTime() < deadline && writer.hasCapacity() && !finishing && units++ < 128) {
            context.batch = new DatasetWriter.Batch();
            context.subject = null;
            if (cancelled) {
                finish();
                return;
            }
            try {
                step();
            } catch (Exception | LinkageError | StackOverflowError e) {
                context.error(phase, pendingIcon == null ? null : pendingIcon.identity.id, e);
                if (e instanceof java.io.UncheckedIOException || completedSources && !context.catalog.prepared()) {
                    writer.fail(e);
                    finishing = true;
                }
                if (pendingIcon != null) {
                    context.batch.add("icons", Json.object("itemId", pendingIcon.identity.id, "status", "error"));
                    pendingIcon = null;
                    probe = null;
                    rendered++;
                }
            }
            if (!finishing && !context.batch.isEmpty()) writer.submit(context.batch);
            if (probe != null && !probe.checked) break;
        }
    }

    private void step() throws Exception {
        if (!completedSources) {
            CandidateSources.Task task = tasks.get(taskIndex);
            context.source = task.name();
            phase = "Discovering: " + task.name()
                .replace('_', ' ');
            context.sources.put(task.name(), Json.object("status", "running"));
            long begin = System.nanoTime();
            boolean more = task.step(context);
            if (System.nanoTime() - begin > 100000000L) context.count(task.name() + ".slowCallsOver100ms");
            if (!more) {
                context.sources.put(
                    task.name(),
                    Json.object(
                        "status",
                        context.errors > sourceStartErrors || context.unsupportedRecipes > sourceStartUnsupported
                            ? "partial"
                            : "complete"));
                sourceStartErrors = context.errors;
                sourceStartUnsupported = context.unsupportedRecipes;
                if (++taskIndex == tasks.size()) {
                    completedSources = true;
                }
            }
            return;
        }
        if (!context.catalog.prepared()) {
            phase = "Consolidating candidate catalog";
            if (context.catalog.prepareStep()) {
                total = context.catalog.size();
                variants = context.catalog.iterator();
            }
            return;
        }
        if (variants.hasNext()) {
            context.source = "items";
            phase = "Writing items and aspects";
            Candidate candidate = variants.next();
            processed++;
            context.subject = candidate.identity.id;
            Map<String, Object> row = candidate.identity.record();
            row.put("sources", candidate.sources);
            row.put("gregtechMaterial", candidate.gregtechMaterial);
            lastSources.put(candidate.identity.id, new java.util.TreeSet<>(candidate.sources));
            String modId = candidate.identity.registryName.split(":", 2)[0];
            context.count("mods." + modId + ".items");
            row.put(
                "className",
                candidate.stack.getItem()
                    .getClass()
                    .getName());
            try {
                row.put("displayName", candidate.stack.getDisplayName());
                row.put("nameStatus", "present");
            } catch (RuntimeException | LinkageError e) {
                row.put("displayName", candidate.identity.registryName + " (" + candidate.identity.metadata + ")");
                row.put("nameStatus", "fallback");
                context.error("display_name", candidate.identity.id, e);
            }
            context.batch.add("items", row);
            if (context.options.aspects) {
                Map<String, Object> profile;
                try {
                    profile = AspectResolver.resolve(candidate.identity.id, candidate.stack);
                } catch (RuntimeException | LinkageError | StackOverflowError e) {
                    context.error("resolve_aspects", candidate.identity.id, e);
                    profile = Json.object("itemId", candidate.identity.id, "status", "error", "aspects", null);
                }
                context.count("aspects." + profile.get("status"));
                context.count("mods." + modId + ".aspects." + profile.get("status"));
                context.batch.add("aspects", profile);
            }
            return;
        }
        if (context.options.icons) {
            context.source = "icons";
            phase = "Rendering item icons";
            if (icons == null) icons = context.catalog.iterator();
            if (pendingIcon == null && icons.hasNext()) pendingIcon = icons.next();
            if (pendingIcon != null) {
                if (context.options.reuseIcons && probe == null) {
                    probe = new DatasetWriter.Icon(
                        pendingIcon.identity.id,
                        writer.fingerprint,
                        context.options.iconSize,
                        true,
                        null);
                    if (writer.mayHaveCachedIcon(probe.cacheKey)) {
                        context.batch.icon = probe;
                        return;
                    }
                    probe.checked = true;
                }
                if (probe != null && !probe.checked) return;
                if (probe == null || !probe.cacheHit) {
                    ItemStack stack = pendingIcon.stack.copy();
                    context.batch.icon = new DatasetWriter.Icon(
                        pendingIcon.identity.id,
                        writer.fingerprint,
                        context.options.iconSize,
                        false,
                        renderer.render(stack, context.options.iconSize));
                }
                rendered++;
                pendingIcon = null;
                probe = null;
                return;
            }
        }
        finish();
    }

    private void finish() {
        if (context.catalog.prepared()) total = context.catalog.size();
        finishing = true;
        phase = "Finalizing export";
        try {
            renderer.close();
        } catch (RuntimeException e) {
            context.error("close_renderer", null, e);
        }
        Map<String, Object> categories = Json.object(
            "items",
            processed == total && completedSources && context.errors == 0 && context.unsupportedRecipes == 0
                ? "complete"
                : "partial",
            "aspects",
            !context.options.aspects ? "not_requested"
                : processed == total && completedSources && context.errors == 0 && context.unsupportedRecipes == 0
                    ? "complete"
                    : "partial",
            "recipes",
            context.options.recipes ? "partial" : "not_requested",
            "oreDictionary",
            !context.options.oreDictionary ? "not_requested"
                : completedSources && context.errors == 0 && context.unsupportedRecipes == 0 ? "complete" : "partial",
            "icons",
            !context.options.icons ? "not_requested"
                : rendered == total && completedSources && context.errors == 0 && context.unsupportedRecipes == 0
                    ? "complete"
                    : "partial");
        context.batch.coverage = Json.object(
            "sources",
            context.sources,
            "counts",
            context.counts,
            "discoveredVariants",
            candidateCount(),
            "writtenItems",
            processed,
            "processedIcons",
            rendered,
            "errors",
            context.errors,
            "unsupportedRecipes",
            context.unsupportedRecipes,
            "limits",
            Json.object("maxNbtCharactersPerVariant", 65536, "catalogPartitions", CandidateCatalog.partitionCount()),
            "limitations",
            Arrays.asList(
                "Finite observed NBT states only; no exhaustive dynamic state enumeration",
                "Wildcard rules remain predicates, not concrete items",
                "Unknown crafting classes and recipe systems outside the listed sources are not fully decoded",
                "World resource sources and unlisted mod-specific recipe systems are not exported",
                "Recipe semantics and furnace acceptance require further validation",
                "Only integration dependency versions are verified; pack version is not inferred from them"));
        context.batch.summary = Json.object(
            "state",
            cancelled ? "cancelled" : "completed",
            "finishedAt",
            Instant.now()
                .toString(),
            "categories",
            categories,
            "coverageScope",
            "listed_sources_and_observed_states",
            "candidateCount",
            candidateCount(),
            "errors",
            context.errors,
            "unsupportedRecipes",
            context.unsupportedRecipes);
        writer.submit(context.batch);
    }
}
