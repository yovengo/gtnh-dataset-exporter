package dev.yovengo.gtnhdatasetexporter.export;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.zip.GZIPOutputStream;

import javax.imageio.ImageIO;

import dev.yovengo.gtnhdatasetexporter.DatasetExporter;
import dev.yovengo.gtnhdatasetexporter.data.Json;

public final class DatasetWriter {

    private static final long SHARD_BYTES = 32L * 1024 * 1024;
    private final long shardBytes;
    private final BlockingQueue<Batch> queue = new ArrayBlockingQueue<>(16);
    private final Map<String, Shard> shards = new LinkedHashMap<>();
    private final List<Object> files = new ArrayList<>();
    private final Map<String, Object> manifest;
    private final Path gameDirectory;
    public final Path directory;
    public volatile boolean ready;
    public volatile boolean done;
    public volatile String failure;
    public volatile String fingerprint;
    private volatile boolean cancelPreparation;
    private final java.util.Set<String> cachedIconKeys = new java.util.HashSet<>();
    private final Thread worker;

    public DatasetWriter(Path directory, Path gameDirectory, Map<String, Object> manifest) {
        this(directory, gameDirectory, manifest, SHARD_BYTES);
    }

    DatasetWriter(Path directory, Path gameDirectory, Map<String, Object> manifest, long shardBytes) {
        this.directory = directory;
        this.gameDirectory = gameDirectory;
        this.manifest = manifest;
        this.shardBytes = shardBytes;
        worker = new Thread(this::run, "GTNH dataset writer");
        worker.setDaemon(true);
        worker.start();
    }

    public boolean hasCapacity() {
        return queue.remainingCapacity() > 0 && failure == null;
    }

    public void cancelPreparation() {
        cancelPreparation = true;
    }

    public void fail(Throwable cause) {
        failure = "Candidate catalog failed: " + cause;
        worker.interrupt();
    }

    public boolean mayHaveCachedIcon(String key) {
        return ready && cachedIconKeys.contains(key);
    }

    public void submit(Batch batch) {
        if (!queue.offer(batch)) throw new IllegalStateException("Writer queue was not checked before producing data");
    }

    private void run() {
        try {
            Files.createDirectories(directory);
            manifest.put("state", "running");
            atomicJson(directory.resolve("manifest.json"), manifest);
            Map<String, String> hashes = EnvironmentSnapshot.hashInputs(gameDirectory, () -> cancelPreparation);
            manifest.put("inputHashes", hashes);
            manifest.put("inputHashStatus", cancelPreparation ? "partial" : "complete");
            Map<String, String> renderInputs = new java.util.TreeMap<>(hashes);
            // Export settings do not affect rendered pixels.
            renderInputs.remove("config/gtnhdatasetexporter.cfg");
            fingerprint = Json.hash(Json.GSON.toJson(manifest.get("environment")) + Json.GSON.toJson(renderInputs));
            manifest.put("environmentFingerprint", fingerprint);
            Path cacheDirectory = gameDirectory.resolve("gtnh-dataset-exporter/icon-cache");
            if (Files.isDirectory(cacheDirectory)) {
                try (java.util.stream.Stream<Path> paths = Files.list(cacheDirectory)) {
                    paths.map(
                        path -> path.getFileName()
                            .toString())
                        .filter(name -> name.endsWith(".png"))
                        .forEach(name -> cachedIconKeys.add(name.substring(0, name.length() - 4)));
                }
            }
            atomicJson(directory.resolve("manifest.json"), manifest);
            ready = true;
            while (true) {
                Batch batch = queue.take();
                for (Row row : batch.rows) writeRow(row.category, row.value);
                if (batch.icon != null) writeIcon(batch.icon);
                if (batch.summary != null) {
                    for (Shard shard : shards.values()) shard.close();
                    shards.clear();
                    if (batch.coverage instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> coverage = (Map<String, Object>) batch.coverage;
                        coverage.put("emptyIcons", emptyIcons);
                    }
                    atomicJson(directory.resolve("coverage.json"), batch.coverage);
                    files.add(
                        Json.object(
                            "path",
                            "coverage.json",
                            "bytes",
                            Files.size(directory.resolve("coverage.json")),
                            "sha256",
                            EnvironmentSnapshot.hashFile(directory.resolve("coverage.json"))));
                    manifest.putAll(batch.summary);
                    manifest.put("emptyIcons", emptyIcons);
                    if (emptyIcons > 0) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> categories = (Map<String, Object>) manifest.get("categories");
                        if (categories != null) categories.put("icons", "partial");
                    }
                    manifest.put("files", files);
                    atomicJson(directory.resolve("manifest.json"), manifest);
                    break;
                }
            }
        } catch (Exception e) {
            if (failure == null) failure = e.toString();
            DatasetExporter.LOG.error("Dataset writer failed", e);
            for (Shard shard : shards.values()) {
                try {
                    shard.abort();
                } catch (IOException ignored) {}
            }
            try {
                manifest.put("state", "failed");
                manifest.put("failure", failure);
                manifest.put("files", files);
                atomicJson(directory.resolve("manifest.json"), manifest);
            } catch (IOException secondary) {
                DatasetExporter.LOG.error("Could not record failed export", secondary);
            }
        } catch (Error fatal) {
            failure = "Writer terminated: " + fatal.getClass()
                .getName();
            throw fatal;
        } finally {
            queue.clear();
            done = true;
        }
    }

    private void writeRow(String category, Object value) throws IOException {
        byte[] encoded = (Json.GSON.toJson(value) + "\n").getBytes(StandardCharsets.UTF_8);
        Shard shard = shards.get(category);
        if (shard == null || shard.bytes + encoded.length > shardBytes) {
            int part = shard == null ? 1 : shard.part + 1;
            if (shard != null) shard.close();
            shard = new Shard(category, part);
            shards.put(category, shard);
        }
        shard.output.write(encoded);
        shard.bytes += encoded.length;
        shard.count++;
    }

    private void writeIcon(Icon icon) throws IOException {
        Path target = directory.resolve("icons")
            .resolve(icon.fileName);
        Path cache = gameDirectory.resolve("gtnh-dataset-exporter/icon-cache")
            .resolve(icon.cacheKey + ".png");
        Files.createDirectories(target.getParent());
        Files.createDirectories(cache.getParent());
        String status = "rendered";
        BufferedImage image = null;
        if (icon.reuse && Files.isRegularFile(cache)) {
            Path checksum = cache.resolveSibling(cache.getFileName() + ".sha256");
            if (Files.isRegularFile(checksum) && Files.size(cache) < 4 * 1024 * 1024
                && EnvironmentSnapshot.hashFile(cache)
                    .equals(new String(Files.readAllBytes(checksum), StandardCharsets.UTF_8))) {
                image = ImageIO.read(cache.toFile());
                if (image != null && image.getWidth() == icon.size && image.getHeight() == icon.size) status = "cached";
                else image = null;
            }
        }
        if (image == null && icon.pixels == null) {
            // A null-pixel request probes the cache before rendering.
            icon.cacheHit = false;
            icon.checked = true;
            return;
        }
        if (image == null) {
            image = new BufferedImage(icon.size, icon.size, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, icon.size, icon.size, icon.pixels, 0, icon.size);
        }
        int[] bounds = visibleBounds(image);
        if (bounds[2] == 0) status = "empty";
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        if (!ImageIO.write(image, "PNG", temporary.toFile())) throw new IOException("PNG writer unavailable");
        move(temporary, target);
        String sha256 = EnvironmentSnapshot.hashFile(target);
        if (!status.equals("empty")) {
            Files
                .copy(target, cache.resolveSibling(cache.getFileName() + ".part"), StandardCopyOption.REPLACE_EXISTING);
            move(cache.resolveSibling(cache.getFileName() + ".part"), cache);
            Path hashTemp = cache.resolveSibling(cache.getFileName() + ".sha256.part");
            Files.write(hashTemp, sha256.getBytes(StandardCharsets.UTF_8));
            move(hashTemp, cache.resolveSibling(cache.getFileName() + ".sha256"));
        }
        writeRow(
            "icons",
            Json.object(
                "itemId",
                icon.itemId,
                "status",
                status,
                "path",
                "icons/" + icon.fileName,
                "sha256",
                sha256,
                "bytes",
                Files.size(target),
                "width",
                icon.size,
                "height",
                icon.size,
                "visibleBounds",
                bounds));
        if (status.equals("empty")) emptyIcons++;
        icon.cacheHit = true;
        icon.checked = true;
    }

    public volatile int emptyIcons;

    static int[] visibleBounds(BufferedImage image) {
        int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, y) >>> 24) >= 16) {
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        return right < 0 ? new int[] { 0, 0, 0, 0 } : new int[] { left, top, right - left + 1, bottom - top + 1 };
    }

    public static void atomicJson(Path target, Object value) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        Files.write(temporary, (Json.GSON.toJson(value) + "\n").getBytes(StandardCharsets.UTF_8));
        move(temporary, target);
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private final class Shard {

        final String name;
        final int part;
        final Path temporary;
        final MessageDigest digest = Json.digest();
        final OutputStream output;
        long count;
        long bytes;

        Shard(String category, int part) throws IOException {
            this.part = part;
            name = String.format(java.util.Locale.ROOT, "%s-%05d.jsonl.gz", category, part);
            temporary = directory.resolve(name + ".part");
            output = new GZIPOutputStream(
                new BufferedOutputStream(new DigestOutputStream(Files.newOutputStream(temporary), digest)));
        }

        void close() throws IOException {
            output.close();
            Path target = directory.resolve(name);
            move(temporary, target);
            files.add(
                Json.object(
                    "path",
                    name,
                    "records",
                    count,
                    "bytes",
                    Files.size(target),
                    "uncompressedBytes",
                    bytes,
                    "sha256",
                    Json.hex(digest.digest())));
        }

        void abort() throws IOException {
            output.close();
        }
    }

    public static final class Batch {

        final List<Row> rows = new ArrayList<>();
        public Icon icon;
        public Map<String, Object> summary;
        public Object coverage;

        public void add(String category, Object value) {
            rows.add(new Row(category, value));
        }

        public boolean isEmpty() {
            return rows.isEmpty() && icon == null && summary == null;
        }
    }

    private static final class Row {

        final String category;
        final Object value;

        Row(String category, Object value) {
            this.category = category;
            this.value = value;
        }
    }

    public static final class Icon {

        public final String itemId;
        public final String fileName;
        public final String cacheKey;
        public final int size;
        public final boolean reuse;
        public final int[] pixels;
        public volatile boolean checked;
        public volatile boolean cacheHit;

        public Icon(String itemId, String fingerprint, int size, boolean reuse, int[] pixels) {
            this.itemId = itemId;
            fileName = Json.hash(itemId) + ".png";
            cacheKey = Json.hash("renderer-v1|" + fingerprint + "|" + size + "|" + itemId);
            this.size = size;
            this.reuse = reuse;
            this.pixels = pixels;
        }
    }
}
