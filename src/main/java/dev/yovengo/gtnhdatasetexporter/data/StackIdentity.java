package dev.yovengo.gtnhdatasetexporter.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.oredict.OreDictionary;

import com.google.gson.JsonElement;

/** Exact observed stack identity; never performs ore unification or drops NBT fields. */
public final class StackIdentity {

    public final String id;
    public final String registryName;
    public final int metadata;
    public final Object nbt;
    public final int nbtCharacters;
    public final String encodedNbt;

    public StackIdentity(ItemStack stack) {
        if (stack == null || stack.getItem() == null) throw new IllegalArgumentException("Missing item");
        registryName = (String) Item.itemRegistry.getNameForObject(stack.getItem());
        if (registryName == null) throw new IllegalArgumentException("Unregistered item");
        metadata = stack.getItemDamage();
        nbt = canonicalNbt(stack.getTagCompound());
        String encoded = Json.GSON.toJson(nbt);
        encodedNbt = encoded;
        nbtCharacters = encoded.length();
        if (nbtCharacters > 65536) throw new IllegalArgumentException("NBT exceeds 64 Ki characters");
        id = registryName + "@" + metadata + (nbt == null ? "" : "#" + Json.hash(encoded));
    }

    /** Restores the identity recorded at discovery, independent of ItemStack NBT read hooks. */
    public static StackIdentity fromSnapshot(String id, String registryName, int metadata, String encodedNbt) {
        return new StackIdentity(id, registryName, metadata, encodedNbt);
    }

    private StackIdentity(String id, String registryName, int metadata, String encodedNbt) {
        this.id = id;
        this.registryName = registryName;
        this.metadata = metadata;
        this.encodedNbt = encodedNbt;
        this.nbtCharacters = encodedNbt.length();
        if (nbtCharacters > 65536) throw new IllegalArgumentException("NBT exceeds 64 Ki characters");
        this.nbt = "null".equals(encodedNbt) ? null : Json.GSON.fromJson(encodedNbt, JsonElement.class);
        String expected = registryName + "@" + metadata + (nbt == null ? "" : "#" + Json.hash(encodedNbt));
        if (!expected.equals(id) || !Json.GSON.toJson(nbt)
            .equals(encodedNbt)) throw new IllegalArgumentException("Corrupt candidate identity snapshot: " + id);
    }

    public boolean wildcard() {
        return metadata == OreDictionary.WILDCARD_VALUE;
    }

    public Map<String, Object> record() {
        return Json.object("id", id, "registryName", registryName, "metadata", metadata, "nbt", nbt);
    }

    public static Object canonicalNbt(NBTBase tag) {
        return canonicalNbt(tag, 0, new int[] { 0 });
    }

    private static Object canonicalNbt(NBTBase tag, int depth, int[] nodes) {
        if (tag == null) return null;
        if (depth > 64 || ++nodes[0] > 16384) throw new IllegalArgumentException("NBT complexity limit exceeded");
        Object value;
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            Map<String, Object> fields = new TreeMap<>();
            for (String key : compound.func_150296_c()) {
                fields.put(key, canonicalNbt(compound.getTag(key), depth + 1, nodes));
            }
            value = fields;
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag.copy();
            List<Object> values = new ArrayList<>();
            for (int i = list.tagCount() - 1; i >= 0; i--) {
                values.add(canonicalNbt(list.removeTag(i), depth + 1, nodes));
            }
            Collections.reverse(values);
            return Json.object("type", 9, "elementType", ((NBTTagList) tag).func_150303_d(), "value", values);
        } else if (tag instanceof NBTTagByteArray) {
            byte[] bytes = ((NBTTagByteArray) tag).func_150292_c();
            if (bytes.length > 32768) throw new IllegalArgumentException("NBT byte array too large");
            value = Json.hex(bytes);
        } else if (tag instanceof NBTTagIntArray) {
            int[] ints = ((NBTTagIntArray) tag).func_150302_c();
            if (ints.length > 8192) throw new IllegalArgumentException("NBT int array too large");
            value = ints.clone();
        } else if (tag instanceof NBTTagString) {
            value = ((NBTTagString) tag).func_150285_a_();
            if (((String) value).length() > 65536) throw new IllegalArgumentException("NBT string too large");
        } else {
            // Typed numeric literals retain long precision, signed zero and non-finite float values.
            value = tag.toString();
        }
        return Json.object("type", tag.getId(), "value", value);
    }
}
