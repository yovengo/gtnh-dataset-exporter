package dev.yovengo.gtnhdatasetexporter.integration;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import dev.yovengo.gtnhdatasetexporter.data.Json;
import dev.yovengo.gtnhdatasetexporter.data.StackIdentity;
import dev.yovengo.gtnhdatasetexporter.export.ExportContext;
import gregtech.api.util.GTRecipe;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;

public final class RecipeCollector {

    private RecipeCollector() {}

    public static void crafting(Object recipe, ExportContext ctx) {
        Map<String, Object> row = Json.object(
            "kind",
            "crafting",
            "className",
            recipe.getClass()
                .getName());
        Object inputs = null;
        ItemStack output = null;
        boolean exactClass = false;
        if (recipe instanceof ShapedRecipes) {
            ShapedRecipes shaped = (ShapedRecipes) recipe;
            inputs = shaped.recipeItems;
            row.put("width", shaped.recipeWidth);
            row.put("height", shaped.recipeHeight);
            row.put("shaped", true);
            row.put("mirrored", true);
            exactClass = recipe.getClass() == ShapedRecipes.class;
        } else if (recipe instanceof ShapelessRecipes) {
            inputs = ((ShapelessRecipes) recipe).recipeItems;
            row.put("shaped", false);
            exactClass = recipe.getClass() == ShapelessRecipes.class;
        } else if (recipe instanceof ShapedOreRecipe) {
            inputs = ((ShapedOreRecipe) recipe).getInput();
            row.put("shaped", true);
            row.put("layoutStatus", "unavailable");
        } else if (recipe instanceof ShapelessOreRecipe) {
            inputs = ((ShapelessOreRecipe) recipe).getInput();
            row.put("shaped", false);
            exactClass = recipe.getClass() == ShapelessOreRecipe.class;
        }
        if (recipe instanceof IRecipe) output = ((IRecipe) recipe).getRecipeOutput();
        row.put("inputs", ingredient(inputs, ctx));
        row.put("output", ctx.stack(output, "exact"));
        row.put("semantics", "partial");
        row.put("knownIngredientLayout", exactClass);
        row.put("limitations", "Container returns and dynamic crafting results are not evaluated");
        if (inputs == null || !exactClass) ctx.unsupported(recipe);
        emit(row, ctx);
    }

    public static void furnace(ItemStack input, ItemStack output, float experience, ExportContext ctx) {
        emit(
            Json.object(
                "kind",
                "furnace",
                "input",
                ctx.stack(input, "item_and_metadata"),
                "output",
                ctx.stack(output, "exact"),
                "experience",
                experience,
                "semantics",
                "complete"),
            ctx);
    }

    public static void gregtech(String map, GTRecipe recipe, ExportContext ctx) {
        List<Object> itemInputs = new ArrayList<>();
        for (GTRecipe.RecipeItemInput input : recipe.getCachedCombinedItemInputs()) {
            Object representative = ctx
                .stack(input.unifiedStack, input.usesNbtMatching ? "nbt_sensitive" : "gregtech_unified");
            itemInputs.add(
                Json.object(
                    "representative",
                    representative,
                    "amount",
                    input.inputAmount,
                    "expectedConsumedAmount",
                    input.chanceWeightedAmount,
                    "consumed",
                    input.inputAmount != 0,
                    "oreDictionary",
                    input.oreDictId < 0 ? null : OreDictionary.getOreName(input.oreDictId),
                    "nbtSensitive",
                    input.usesNbtMatching));
        }
        Map<String, Object> row = Json.object(
            "kind",
            "gregtech_machine",
            "recipeMap",
            map,
            "className",
            recipe.getClass()
                .getName(),
            "inputs",
            itemInputs,
            "rawInputs",
            ingredient(recipe.mInputs, ctx),
            "outputs",
            ingredient(recipe.mOutputs, ctx),
            "fluidInputs",
            fluids(recipe.mFluidInputs),
            "fluidOutputs",
            fluids(recipe.mFluidOutputs),
            "fluidInputAlternatives",
            fluidAlternatives(recipe.mAltFluidInputs),
            "inputChances",
            copy(recipe.mInputChances),
            "outputChances",
            copy(recipe.mOutputChances),
            "fluidInputChances",
            copy(recipe.mFluidInputChances),
            "fluidOutputChances",
            copy(recipe.mFluidOutputChances),
            "chanceDenominator",
            10000,
            "missingChance",
            "certain",
            "durationTicks",
            recipe.mDuration,
            "euPerTick",
            recipe.mEUt,
            "specialValue",
            recipe.mSpecialValue,
            "specialItems",
            ingredient(recipe.mSpecialItems, ctx),
            "enabled",
            recipe.mEnabled,
            "hidden",
            recipe.mHidden,
            "fake",
            recipe.mFakeRecipe,
            "nbtSensitive",
            recipe.isNBTSensitive,
            "needsEmptyOutput",
            recipe.mNeedsEmptyOutput,
            "semantics",
            "partial",
            "limitations",
            "Special metadata and machine-specific checks are not fully decoded");
        if (recipe instanceof GTRecipe.GTRecipe_WithAlt) {
            GTRecipe.GTRecipe_WithAlt alternative = (GTRecipe.GTRecipe_WithAlt) recipe;
            row.put("inputAlternativesBySlot", ingredient(alternative.mOreDictAlt, ctx));
            List<String> oreNames = new ArrayList<>();
            if (alternative.mOreDictIds != null) for (int id : alternative.mOreDictIds) {
                oreNames.add(id < 0 ? null : OreDictionary.getOreName(id));
            }
            row.put("oreDictionaryGroupsBySlot", oreNames);
        }
        emit(row, ctx);
    }

    public static void assemblyLine(GTRecipe.RecipeAssemblyLine recipe, ExportContext ctx) {
        emit(
            Json.object(
                "kind",
                "gregtech_assembly_line",
                "inputs",
                ingredient(recipe.mInputs, ctx),
                "inputAlternativesBySlot",
                ingredient(recipe.mOreDictAlt, ctx),
                "fluidInputs",
                fluids(recipe.mFluidInputs),
                "output",
                ctx.stack(recipe.mOutput, "exact"),
                "researchItem",
                ctx.stack(recipe.mResearchItem, "source_defined"),
                "researchTime",
                recipe.mResearchTime,
                "researchVoltage",
                recipe.mResearchVoltage,
                "durationTicks",
                recipe.mDuration,
                "euPerTick",
                recipe.mEUt,
                "semantics",
                "partial",
                "limitations",
                "Machine-specific matching and research requirements require runtime validation"),
            ctx);
    }

    public static void thaumcraft(Object recipe, ExportContext ctx) {
        Map<String, Object> row = Json.object(
            "className",
            recipe.getClass()
                .getName(),
            "semantics",
            "partial");
        if (recipe instanceof ShapedArcaneRecipe) {
            ShapedArcaneRecipe r = (ShapedArcaneRecipe) recipe;
            row.put("kind", "thaumcraft_arcane");
            row.put("inputs", ingredient(r.getInput(), ctx));
            row.put("width", r.width);
            row.put("height", r.height);
            row.put("output", ctx.stack(r.getRecipeOutput(), "exact"));
            row.put("aspects", AspectResolver.values(r.getAspects()));
            row.put("research", r.getResearch());
        } else if (recipe instanceof ShapelessArcaneRecipe) {
            ShapelessArcaneRecipe r = (ShapelessArcaneRecipe) recipe;
            row.put("kind", "thaumcraft_arcane");
            row.put("shaped", false);
            row.put("inputs", ingredient(r.getInput(), ctx));
            row.put("output", ctx.stack(r.getRecipeOutput(), "exact"));
            row.put("aspects", AspectResolver.values(r.getAspects()));
            row.put("research", r.getResearch());
        } else if (recipe instanceof CrucibleRecipe) {
            CrucibleRecipe r = (CrucibleRecipe) recipe;
            row.put("kind", "thaumcraft_crucible");
            row.put("input", ingredient(r.catalyst, ctx));
            row.put("output", ctx.stack(r.getRecipeOutput(), "exact"));
            row.put("aspects", AspectResolver.values(r.aspects));
            row.put("research", r.key);
        } else if (recipe instanceof InfusionRecipe) {
            InfusionRecipe r = (InfusionRecipe) recipe;
            row.put("kind", "thaumcraft_infusion");
            row.put("centralInput", ctx.stack(r.getRecipeInput(), "thaumcraft"));
            row.put("inputs", ingredient(r.getComponents(), ctx));
            row.put("output", ingredient(r.getRecipeOutput(), ctx));
            row.put("aspects", AspectResolver.values(r.getAspects()));
            row.put("research", r.getResearch());
            row.put("instability", r.getInstability());
        } else {
            row.put("kind", "thaumcraft_unknown");
            ctx.unsupported(recipe);
        }
        row.put("limitations", "Research-dependent matching, subclasses and dynamic NBT outputs are not evaluated");
        emit(row, ctx);
    }

    private static Object ingredient(Object value, ExportContext ctx) {
        if (value == null) return null;
        if (value instanceof ItemStack) return ctx.stack((ItemStack) value, "source_defined");
        if (value instanceof String) return Json.object("kind", "oredict", "name", value);
        if (value instanceof Iterable<?>) {
            List<Object> entries = new ArrayList<>();
            for (Object entry : (Iterable<?>) value) entries.add(ingredient(entry, ctx));
            return entries;
        }
        if (value.getClass()
            .isArray()) {
            List<Object> entries = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) entries.add(ingredient(Array.get(value, i), ctx));
            return entries;
        }
        ctx.count(
            "unsupportedIngredient." + value.getClass()
                .getName());
        return Json.object(
            "kind",
            "unsupported",
            "className",
            value.getClass()
                .getName());
    }

    private static int[] copy(int[] values) {
        return values == null ? null : values.clone();
    }

    private static List<Object> fluids(FluidStack[] stacks) {
        List<Object> values = new ArrayList<>();
        if (stacks != null) for (FluidStack stack : stacks) {
            values.add(
                stack == null ? null
                    : Json.object(
                        "kind",
                        "fluid",
                        "name",
                        stack.getFluid()
                            .getName(),
                        "amountMb",
                        stack.amount,
                        "nbt",
                        StackIdentity.canonicalNbt(stack.tag)));
        }
        return values;
    }

    private static List<Object> fluidAlternatives(FluidStack[][] stacks) {
        List<Object> values = new ArrayList<>();
        if (stacks != null) for (FluidStack[] stack : stacks) values.add(fluids(stack));
        return values;
    }

    private static void emit(Map<String, Object> row, ExportContext ctx) {
        ctx.count("recipes.observed");
        row.put("id", Json.hash(Json.GSON.toJson(row)));
        if (ctx.options.recipes) ctx.batch.add("recipes", row);
    }
}
