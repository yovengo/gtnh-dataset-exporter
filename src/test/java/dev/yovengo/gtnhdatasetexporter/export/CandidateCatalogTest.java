package dev.yovengo.gtnhdatasetexporter.export;

import static org.junit.Assert.*;

import java.util.Iterator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CandidateCatalogTest {

    private static Item item;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @BeforeClass
    public static void registerTestItem() throws Exception {
        item = new Item();
        // Forge registration needs LaunchWrapper; register directly in this JVM.
        java.lang.reflect.Method raw = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        raw.setAccessible(true);
        raw.invoke(Item.itemRegistry, 31000, "exportertest:variant", item);
    }

    private CandidateCatalog catalog() throws Exception {
        return new CandidateCatalog(
            temp.newFolder("spool")
                .toPath());
    }

    private static void prepare(CandidateCatalog catalog) {
        while (!catalog.prepareStep()) { /* one disk partition per call */ }
    }

    @Test
    public void countIsExcludedAndProvenanceIsMergedAcrossDiskRecords() throws Exception {
        CandidateCatalog catalog = catalog();
        ItemStack stack = new ItemStack(item, 64, 7);
        String id = catalog.add(stack, "nei").id;
        assertEquals(id, catalog.add(new ItemStack(item, 1, 7), "recipe").id);
        assertEquals(64, stack.stackSize);
        prepare(catalog);
        assertEquals(1, catalog.size());
        CandidateCatalog.Candidate candidate = catalog.iterator()
            .next();
        assertEquals(1, candidate.stack.stackSize);
        assertEquals(2, candidate.sources.size());
        assertTrue(candidate.sources.contains("nei"));
        assertTrue(candidate.sources.contains("recipe"));
        catalog.clear();
    }

    @Test
    public void metadataNbtAndMaterialSurviveSpooling() throws Exception {
        CandidateCatalog catalog = catalog();
        ItemStack plain = new ItemStack(item, 1, 7);
        String a = catalog.add(plain, "nei").id;
        ItemStack tagged = plain.copy();
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("charge", 10);
        tagged.setTagCompound(nbt);
        String b = catalog.add(tagged, "nei").id;
        assertNotEquals(a, b);
        nbt.setInteger("charge", 11);
        String c = catalog.add(tagged, "nei").id;
        assertNotEquals(b, c);
        catalog.add(new ItemStack(item, 1, 8), "nei");
        catalog.add(
            tagged,
            "gregtech",
            dev.yovengo.gtnhdatasetexporter.data.Json.object("prefix", "ingot", "material", "Iron"));
        prepare(catalog);
        assertEquals(4, catalog.size());
        int seen = 0;
        for (Iterator<CandidateCatalog.Candidate> it = catalog.iterator(); it.hasNext();) {
            CandidateCatalog.Candidate candidate = it.next();
            if (candidate.identity.id.equals(c)) {
                assertEquals("Iron", candidate.gregtechMaterial.get("material"));
                assertEquals(2, candidate.sources.size());
                seen++;
            }
        }
        assertEquals(1, seen);
        catalog.clear();
    }

    @Test
    public void wildcardRemainsARuleRatherThanARenderableItem() throws Exception {
        CandidateCatalog catalog = catalog();
        assertTrue(
            catalog.add(new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE), "oredict")
                .wildcard());
        prepare(catalog);
        assertEquals(0, catalog.size());
        assertFalse(
            catalog.iterator()
                .hasNext());
        catalog.clear();
    }

    @Test
    public void cumulativeNbtBeyondFormerLimitIsExported() throws Exception {
        CandidateCatalog catalog = catalog();
        char[] chars = new char[62000];
        java.util.Arrays.fill(chars, 'x');
        String payload = new String(chars);
        for (int i = 0; i < 550; i++) {
            ItemStack stack = new ItemStack(item, 1, i);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("payload", payload);
            stack.setTagCompound(tag);
            catalog.add(stack, "creative");
        }
        prepare(catalog);
        assertEquals(550, catalog.size());
        Iterator<CandidateCatalog.Candidate> items = catalog.iterator();
        int count = 0;
        while (items.hasNext()) {
            assertNotNull(items.next().identity.nbt);
            count++;
        }
        assertEquals(550, count);
        catalog.clear();
    }

    @Test
    public void metadataOutsideVanillaShortRangeKeepsItsIdentity() throws Exception {
        CandidateCatalog catalog = catalog();
        ItemStack stack = new ItemStack(item, 1, 40000);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("seed", 42);
        stack.setTagCompound(tag);
        String id = catalog.add(stack, "creative").id;
        prepare(catalog);
        CandidateCatalog.Candidate restored = catalog.iterator()
            .next();
        assertEquals(id, restored.identity.id);
        assertEquals(id, new dev.yovengo.gtnhdatasetexporter.data.StackIdentity(restored.stack).id);
        assertEquals(40000, restored.identity.metadata);
        assertEquals(40000, restored.stack.getItemDamage());
        assertEquals(
            42,
            restored.stack.getTagCompound()
                .getInteger("seed"));
        catalog.clear();
    }
}
