package dev.yovengo.gtnhdatasetexporter.export;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import dev.yovengo.gtnhdatasetexporter.data.Json;

public final class EnvironmentSnapshot {

    private EnvironmentSnapshot() {}

    public static Map<String, Object> capture(Minecraft mc) {
        Map<String, String> mods = new TreeMap<>();
        for (ModContainer mod : Loader.instance()
            .getModList()) mods.put(mod.getModId(), mod.getVersion());
        List<String> packs = new ArrayList<>();
        for (ResourcePackRepository.Entry entry : mc.getResourcePackRepository()
            .getRepositoryEntries()) {
            packs.add(entry.getResourcePackName());
        }
        Map<String, Object> result = Json.object(
            "minecraftVersion",
            "1.7.10",
            "targetGtnhVersion",
            "2.9.0",
            "packVerification",
            "dependency_versions_only",
            "mods",
            mods,
            "language",
            mc.gameSettings.language,
            "resourcePacks",
            packs,
            "javaVersion",
            System.getProperty("java.version"),
            "os",
            System.getProperty("os.name"));
        result.put(
            "graphics",
            Json.object(
                "vendor",
                org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR),
                "renderer",
                org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER),
                "version",
                org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION)));
        return result;
    }

    /** Checks the integration API versions, not the entire pack. Never labels a modified pack as verified. */
    public static String compatibilityProblem() {
        Map<String, String> expected = new TreeMap<>();
        expected.put("gregtech", "5.09.54.265");
        expected.put("NotEnoughItems", "2.8.171-GTNH");
        expected.put("Thaumcraft", "4.2.3.5");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            ModContainer mod = Loader.instance()
                .getIndexedModList()
                .get(entry.getKey());
            if (mod == null) return "Missing " + entry.getKey() + ". Use stable GTNH 2.9.0.";
            if (!entry.getValue()
                .equals(mod.getVersion())) {
                return entry.getKey() + " " + entry.getValue() + " required; found " + mod.getVersion();
            }
        }
        return null;
    }

    static Map<String, String> hashInputs(Path game, java.util.function.BooleanSupplier cancelled) throws IOException {
        Map<String, String> hashes = new TreeMap<>();
        for (String name : new String[] { "mods", "config", "scripts", "resourcepacks" }) {
            Path root = game.resolve(name);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> paths = Files.walk(root)) {
                java.util.Iterator<Path> iterator = paths.filter(Files::isRegularFile)
                    .sorted()
                    .iterator();
                while (iterator.hasNext()) {
                    if (cancelled.getAsBoolean()) return hashes;
                    Path file = iterator.next();
                    hashes.put(
                        game.relativize(file)
                            .toString()
                            .replace('\\', '/'),
                        hashFile(file));
                }
            }
        }
        return hashes;
    }

    public static String hashFile(Path file) throws IOException {
        MessageDigest digest = Json.digest();
        byte[] buffer = new byte[65536];
        try (InputStream input = Files.newInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return Json.hex(digest.digest());
    }
}
