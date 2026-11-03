package dev.yovengo.gtnhdatasetexporter.export;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.yovengo.gtnhdatasetexporter.data.Json;

public class DatasetWriterTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void shardRotationRetainsEveryRecordAndChecksum() throws Exception {
        Path game = temporary.newFolder()
            .toPath();
        DatasetWriter writer = new DatasetWriter(
            game.resolve("export"),
            game,
            Json.object("environment", Json.object()),
            40);
        awaitReady(writer);
        DatasetWriter.Batch data = new DatasetWriter.Batch();
        for (int i = 0; i < 10; i++) data.add("items", Json.object("id", "test:item@" + i));
        writer.submit(data);
        finish(writer, "completed");
        int count = 0, shards = 0;
        for (com.google.gson.JsonElement element : read(writer.directory.resolve("manifest.json"))
            .getAsJsonArray("files")) {
            JsonObject file = element.getAsJsonObject();
            Path path = writer.directory.resolve(
                file.get("path")
                    .getAsString());
            assertEquals(
                EnvironmentSnapshot.hashFile(path),
                file.get("sha256")
                    .getAsString());
            if (file.has("records")) {
                count += file.get("records")
                    .getAsInt();
                shards++;
            }
        }
        assertEquals(10, count);
        assertTrue(shards > 1);
    }

    @Test
    public void ioFailureTerminatesWorkerAndNeverReportsSuccess() throws Exception {
        Path game = temporary.newFolder()
            .toPath();
        Path target = game.resolve("not-a-directory");
        Files.write(target, new byte[] { 1 });
        DatasetWriter writer = new DatasetWriter(target, game, Json.object("environment", Json.object()));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!writer.done && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(writer.done);
        assertNotNull(writer.failure);
        assertFalse(writer.ready);
    }

    @Test
    public void manifestIsFinalizedAfterChecksummedJsonlFiles() throws Exception {
        Path game = temporary.newFolder()
            .toPath();
        DatasetWriter writer = new DatasetWriter(
            game.resolve("export"),
            game,
            Json.object("environment", Json.object()));
        awaitReady(writer);
        JsonObject initial = read(writer.directory.resolve("manifest.json"));
        assertEquals(
            "running",
            initial.get("state")
                .getAsString());
        DatasetWriter.Batch data = new DatasetWriter.Batch();
        data.add("items", Json.object("id", "example:item@7", "displayName", "Русское имя"));
        writer.submit(data);
        finish(writer, "completed");
        JsonObject manifest = read(writer.directory.resolve("manifest.json"));
        assertEquals(
            "completed",
            manifest.get("state")
                .getAsString());
        JsonObject file = manifest.getAsJsonArray("files")
            .get(0)
            .getAsJsonObject();
        Path shard = writer.directory.resolve(
            file.get("path")
                .getAsString());
        assertEquals(
            EnvironmentSnapshot.hashFile(shard),
            file.get("sha256")
                .getAsString());
        assertEquals(
            1,
            file.get("records")
                .getAsInt());
        try (InputStreamReader reader = new InputStreamReader(
            new GZIPInputStream(Files.newInputStream(shard)),
            StandardCharsets.UTF_8)) {
            assertEquals(
                "Русское имя",
                new JsonParser().parse(reader)
                    .getAsJsonObject()
                    .get("displayName")
                    .getAsString());
        }
        assertFalse(Files.exists(shard.resolveSibling(shard.getFileName() + ".part")));
    }

    @Test
    public void cancellationNeverClaimsCompletion() throws Exception {
        Path game = temporary.newFolder()
            .toPath();
        DatasetWriter writer = new DatasetWriter(
            game.resolve("export"),
            game,
            Json.object("environment", Json.object()));
        awaitReady(writer);
        finish(writer, "cancelled");
        assertEquals(
            "cancelled",
            read(writer.directory.resolve("manifest.json")).get("state")
                .getAsString());
    }

    @Test
    public void cachedIconsAreVerifiedAndCopiedIntoEachDataset() throws Exception {
        Path game = temporary.newFolder()
            .toPath();
        DatasetWriter first = new DatasetWriter(game.resolve("first"), game, Json.object("environment", Json.object()));
        awaitReady(first);
        int[] pixels = new int[32 * 32];
        pixels[33] = 0xFFFFFFFF;
        DatasetWriter.Batch image = new DatasetWriter.Batch();
        image.icon = new DatasetWriter.Icon("test:item@0", first.fingerprint, 32, false, pixels);
        first.submit(image);
        finish(first, "completed");
        DatasetWriter second = new DatasetWriter(
            game.resolve("second"),
            game,
            Json.object("environment", Json.object()));
        awaitReady(second);
        DatasetWriter.Batch probe = new DatasetWriter.Batch();
        probe.icon = new DatasetWriter.Icon("test:item@0", second.fingerprint, 32, true, null);
        second.submit(probe);
        finish(second, "completed");
        assertTrue(probe.icon.cacheHit);
        assertTrue(
            Files.isRegularFile(
                second.directory.resolve("icons")
                    .resolve(probe.icon.fileName)));
        Files.write(
            game.resolve("gtnh-dataset-exporter/icon-cache")
                .resolve(probe.icon.cacheKey + ".png"),
            new byte[] { 1, 2, 3 });
        DatasetWriter third = new DatasetWriter(game.resolve("third"), game, Json.object("environment", Json.object()));
        awaitReady(third);
        DatasetWriter.Batch corrupt = new DatasetWriter.Batch();
        corrupt.icon = new DatasetWriter.Icon("test:item@0", third.fingerprint, 32, true, null);
        third.submit(corrupt);
        finish(third, "completed");
        assertFalse(corrupt.icon.cacheHit);
    }

    @Test
    public void transparentBoundsDetectAnEmptyRender() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        assertArrayEquals(new int[] { 0, 0, 0, 0 }, DatasetWriter.visibleBounds(image));
        image.setRGB(3, 5, 0xFFFFFFFF);
        image.setRGB(7, 9, 0xFFFFFFFF);
        assertArrayEquals(new int[] { 3, 5, 5, 5 }, DatasetWriter.visibleBounds(image));
    }

    private void finish(DatasetWriter writer, String state) throws Exception {
        DatasetWriter.Batch end = new DatasetWriter.Batch();
        end.summary = Json.object("state", state);
        end.coverage = Json.object("sources", Json.object());
        writer.submit(end);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!writer.done && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue("Writer did not finish", writer.done);
        assertNull(writer.failure);
    }

    private void awaitReady(DatasetWriter writer) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!writer.ready && !writer.done && System.nanoTime() < deadline) Thread.sleep(5);
        assertNull(writer.failure);
        assertTrue("Writer did not initialize", writer.ready);
    }

    private JsonObject read(Path file) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
            .getAsJsonObject();
    }
}
