package dev.yovengo.gtnhdatasetexporter.export;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import dev.yovengo.gtnhdatasetexporter.data.StackIdentity;

/** Disk-backed catalog. A hash partition bounds the memory used for deduplication. */
public final class CandidateCatalog {

    private static final int PARTITIONS = 128;
    private final Path directory;
    private final DataOutputStream[] partitions = new DataOutputStream[PARTITIONS];
    private DataOutputStream uniqueOutput;
    private int nextPartition;
    private int observed;
    private int unique;
    private boolean prepared;

    public CandidateCatalog(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public StackIdentity add(ItemStack stack, String source) {
        return add(stack, source, null);
    }

    public StackIdentity add(ItemStack stack, String source, Map<String, Object> gregtechMaterial) {
        if (prepared || uniqueOutput != null) throw new IllegalStateException("Catalog discovery has ended");
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        StackIdentity identity = new StackIdentity(copy);
        if (identity.wildcard()) return identity;
        int partition = identity.id.hashCode() & (PARTITIONS - 1);
        try {
            DataOutputStream out = partitions[partition];
            if (out == null) {
                out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(partitionPath(partition)), 8192));
                partitions[partition] = out;
            }
            Candidate candidate = new Candidate(identity, copy);
            candidate.sources.add(source);
            candidate.gregtechMaterial = gregtechMaterial;
            writeRecord(out, candidate);
            observed++;
            return identity;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int size() {
        return prepared ? unique : observed;
    }

    public int observed() {
        return observed;
    }

    public boolean prepared() {
        return prepared;
    }

    public int partitionsProcessed() {
        return nextPartition;
    }

    public static int partitionCount() {
        return PARTITIONS;
    }

    /** Consolidates one partition per call, so the game-loop budget remains effective. */
    public boolean prepareStep() {
        if (prepared) return true;
        try {
            if (uniqueOutput == null) {
                for (DataOutputStream out : partitions) if (out != null) out.close();
                uniqueOutput = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(uniquePath()), 65536));
            }
            if (nextPartition < PARTITIONS) {
                Path path = partitionPath(nextPartition++);
                if (Files.exists(path)) {
                    Map<String, Candidate> merged = new TreeMap<>();
                    try (DataInputStream in = new DataInputStream(
                        new BufferedInputStream(Files.newInputStream(path), 65536))) {
                        Candidate record;
                        while ((record = readRecord(in)) != null) {
                            Candidate existing = merged.get(record.identity.id);
                            if (existing == null) merged.put(record.identity.id, record);
                            else {
                                existing.sources.addAll(record.sources);
                                if (existing.gregtechMaterial == null)
                                    existing.gregtechMaterial = record.gregtechMaterial;
                            }
                        }
                    }
                    for (Candidate candidate : merged.values()) {
                        writeRecord(uniqueOutput, candidate);
                        unique++;
                    }
                    Files.delete(path);
                }
                return false;
            }
            uniqueOutput.close();
            uniqueOutput = null;
            prepared = true;
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Iterator<Candidate> iterator() {
        if (!prepared) throw new IllegalStateException("Catalog is not consolidated");
        try {
            final DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(uniquePath()), 65536));
            return new Iterator<Candidate>() {

                private Candidate next = readNext();

                private Candidate readNext() {
                    try {
                        Candidate value = readRecord(in);
                        if (value == null) in.close();
                        return value;
                    } catch (IOException e) {
                        try {
                            in.close();
                        } catch (IOException ignored) {}
                        throw new UncheckedIOException(e);
                    }
                }

                @Override
                public boolean hasNext() {
                    return next != null;
                }

                @Override
                public Candidate next() {
                    if (next == null) throw new NoSuchElementException();
                    Candidate value = next;
                    next = readNext();
                    return value;
                }
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void clear() {
        try {
            if (uniqueOutput != null) uniqueOutput.close();
            for (DataOutputStream out : partitions) if (out != null) out.close();
            for (int i = 0; i < PARTITIONS; i++) Files.deleteIfExists(partitionPath(i));
            Files.deleteIfExists(uniquePath());
            Files.deleteIfExists(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path partitionPath(int partition) {
        return directory.resolve("partition-" + partition + ".nbt");
    }

    private Path uniquePath() {
        return directory.resolve("unique.nbt");
    }

    private static void writeRecord(DataOutputStream out, Candidate candidate) throws IOException {
        out.writeUTF(candidate.identity.id);
        out.writeUTF(candidate.identity.registryName);
        out.writeInt(candidate.identity.metadata);
        byte[] encoded = candidate.identity.encodedNbt.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length);
        out.write(encoded);
        NBTTagCompound tag = candidate.stack.getTagCompound();
        out.writeBoolean(tag != null);
        if (tag != null) CompressedStreamTools.write((NBTTagCompound) tag.copy(), out);
        out.writeInt(candidate.sources.size());
        for (String source : candidate.sources) out.writeUTF(source);
        out.writeBoolean(candidate.gregtechMaterial != null);
        if (candidate.gregtechMaterial != null) {
            out.writeUTF((String) candidate.gregtechMaterial.get("prefix"));
            out.writeUTF((String) candidate.gregtechMaterial.get("material"));
        }
    }

    private static Candidate readRecord(DataInputStream in) throws IOException {
        String id;
        try {
            id = in.readUTF();
        } catch (EOFException end) {
            return null;
        }
        String registryName = in.readUTF();
        int metadata = in.readInt();
        int nbtBytes = in.readInt();
        if (nbtBytes < 0 || nbtBytes > 262144) throw new IOException("Invalid candidate NBT length: " + nbtBytes);
        byte[] encoded = new byte[nbtBytes];
        in.readFully(encoded);
        StackIdentity identity;
        try {
            identity = StackIdentity
                .fromSnapshot(id, registryName, metadata, new String(encoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid candidate identity snapshot: " + id, e);
        }
        Item item = (Item) Item.itemRegistry.getObject(registryName);
        if (item == null || !registryName.equals(Item.itemRegistry.getNameForObject(item)))
            throw new IOException("Catalog item is no longer registered: " + registryName);
        ItemStack stack = new ItemStack(item, 1, metadata);
        if (in.readBoolean()) stack.setTagCompound(CompressedStreamTools.read(in));
        Candidate candidate = new Candidate(identity, stack);
        int count = in.readInt();
        if (count < 0 || count > 256) throw new IOException("Invalid source count: " + count);
        for (int i = 0; i < count; i++) candidate.sources.add(in.readUTF());
        if (in.readBoolean()) candidate.gregtechMaterial = dev.yovengo.gtnhdatasetexporter.data.Json
            .object("prefix", in.readUTF(), "material", in.readUTF());
        return candidate;
    }

    public static final class Candidate {

        public final StackIdentity identity;
        public final ItemStack stack;
        public final Set<String> sources = new TreeSet<>();
        public Map<String, Object> gregtechMaterial;

        private Candidate(StackIdentity identity, ItemStack stack) {
            this.identity = identity;
            this.stack = stack;
        }
    }
}
