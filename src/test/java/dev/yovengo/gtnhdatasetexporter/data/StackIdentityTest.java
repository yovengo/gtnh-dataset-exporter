package dev.yovengo.gtnhdatasetexporter.data;

import static org.junit.Assert.*;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

public class StackIdentityTest {

    private String canonical(NBTTagCompound tag) {
        return Json.GSON.toJson(StackIdentity.canonicalNbt(tag));
    }

    @Test
    public void compoundOrderDoesNotChangeIdentity() {
        NBTTagCompound a = new NBTTagCompound(), b = new NBTTagCompound();
        a.setString("name", "Copper");
        a.setInteger("charge", 12);
        b.setInteger("charge", 12);
        b.setString("name", "Copper");
        assertEquals(canonical(a), canonical(b));
    }

    @Test
    public void numericTypesAndLongPrecisionArePreserved() {
        NBTTagCompound a = new NBTTagCompound(), b = new NBTTagCompound();
        a.setInteger("value", 1);
        b.setLong("value", 1);
        assertNotEquals(canonical(a), canonical(b));
        b.setLong("value", 9007199254740993L);
        assertTrue(canonical(b).contains("9007199254740993"));
    }

    @Test
    public void listOrderAndOriginalStackDataArePreserved() {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagInt(1));
        list.appendTag(new NBTTagInt(2));
        NBTTagCompound a = new NBTTagCompound();
        a.setTag("values", list);
        String before = a.toString();
        String first = canonical(a);
        assertEquals(before, a.toString());
        assertEquals(2, list.tagCount());
        NBTTagList reversed = new NBTTagList();
        reversed.appendTag(new NBTTagInt(2));
        reversed.appendTag(new NBTTagInt(1));
        NBTTagCompound b = new NBTTagCompound();
        b.setTag("values", reversed);
        assertNotEquals(first, canonical(b));
    }

    @Test
    public void arraysContainValuesRatherThanOnlyLengths() {
        NBTTagCompound a = new NBTTagCompound(), b = new NBTTagCompound();
        a.setByteArray("bytes", new byte[] { 1, 2 });
        b.setByteArray("bytes", new byte[] { 2, 1 });
        assertNotEquals(canonical(a), canonical(b));
        a.setIntArray("ints", new int[] { Integer.MIN_VALUE, Integer.MAX_VALUE });
        assertTrue(canonical(a).contains("2147483647"));
    }

    @Test
    public void snapshotKeepsExactTypedNbtJson() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("large", 9007199254740993L);
        tag.setIntArray("ints", new int[] { Integer.MIN_VALUE, Integer.MAX_VALUE });
        tag.setString("escaped", "<>\nПривет");
        String encoded = canonical(tag);
        String id = "test:item@40000#" + Json.hash(encoded);
        StackIdentity restored = StackIdentity.fromSnapshot(id, "test:item", 40000, encoded);
        assertEquals(id, restored.id);
        assertEquals(encoded, Json.GSON.toJson(restored.nbt));
    }

    @Test(expected = IllegalArgumentException.class)
    public void deeplyNestedDataHasAnExplicitLimit() {
        NBTTagCompound root = new NBTTagCompound(), tail = root;
        for (int i = 0; i < 70; i++) {
            NBTTagCompound child = new NBTTagCompound();
            tail.setTag("child", child);
            tail = child;
        }
        canonical(root);
    }
}
