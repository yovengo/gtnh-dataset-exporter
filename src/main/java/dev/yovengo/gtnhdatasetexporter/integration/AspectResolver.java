package dev.yovengo.gtnhdatasetexporter.integration;

import java.util.Map;
import java.util.TreeMap;

import net.minecraft.item.ItemStack;

import dev.yovengo.gtnhdatasetexporter.data.Json;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

public final class AspectResolver {

    private AspectResolver() {}

    /** Same call sequence as TC 4.2.3.5 TileAlchemyFurnace; runtime patches apply to these calls. */
    public static Map<String, Object> resolve(String id, ItemStack original) {
        ItemStack stack = original.copy();
        stack.stackSize = 1;
        AspectList base = ThaumcraftCraftingManager.getObjectTags(stack);
        AspectList resolved = ThaumcraftCraftingManager.getBonusTags(stack, base == null ? null : base.copy());
        Map<String, Integer> values = values(resolved);
        boolean positive = values.values()
            .stream()
            .anyMatch(amount -> amount > 0);
        return Json.object(
            "itemId",
            id,
            "status",
            resolved == null ? "absent" : positive ? "present" : "empty",
            "aspects",
            values,
            "stackSize",
            1,
            "resolver",
            "thaumcraft_object_and_bonus_tags",
            "smeltability",
            "unknown",
            "smeltabilityNote",
            "Furnace capacity and patched acceptance not evaluated");
    }

    public static Map<String, Integer> values(AspectList list) {
        Map<String, Integer> result = new TreeMap<>();
        if (list != null) for (Aspect aspect : list.getAspects()) {
            if (aspect != null) result.put(aspect.getTag(), list.getAmount(aspect));
        }
        return result;
    }
}
