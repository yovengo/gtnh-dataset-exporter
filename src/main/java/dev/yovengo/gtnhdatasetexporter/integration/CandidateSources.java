package dev.yovengo.gtnhdatasetexporter.integration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.oredict.OreDictionary;

import codechicken.nei.ItemList;
import dev.yovengo.gtnhdatasetexporter.data.Json;
import dev.yovengo.gtnhdatasetexporter.export.ExportContext;
import gregtech.api.recipe.RecipeMap;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;

public final class CandidateSources {

    private CandidateSources() {}

    public interface Task {

        String name();

        /** One bounded unit; false means the source has been exhausted. */
        boolean step(ExportContext context) throws Exception;
    }

    @FunctionalInterface
    private interface Reader {

        void read(Object value, ExportContext ctx) throws Exception;
    }

    public static List<Task> create() {
        List<Task> tasks = new ArrayList<>();
        tasks.add(scan("registry", () -> Item.itemRegistry.iterator(), (value, ctx) -> {
            Item item = (Item) value;
            ctx.batch.add(
                "item-types",
                Json.object(
                    "registryName",
                    Item.itemRegistry.getNameForObject(item),
                    "className",
                    item.getClass()
                        .getName(),
                    "hasSubtypes",
                    item.getHasSubtypes()));
            if (!item.getHasSubtypes()) ctx.discover(new ItemStack(item, 1, 0));
        }));
        tasks.add(new CreativeVariants());
        tasks.add(scan("nei", () -> {
            if (!ItemList.loadFinished) throw new IllegalStateException("NEI item list is still loading");
            return ItemList.items.iterator();
        }, (value, ctx) -> ctx.discover((ItemStack) value)));
        tasks.add(new GregTechVariants());
        tasks.add(
            scan(
                "thaumcraft_tags",
                () -> new ArrayList<>(ThaumcraftApi.objectTags.keySet()).iterator(),
                CandidateSources::tagKey));
        tasks.add(
            scan(
                "thaumcraft_grouped_tags",
                () -> new ArrayList<>(ThaumcraftApi.groupedObjectTags.entrySet()).iterator(),
                (value, ctx) -> {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
                    List<?> key = (List<?>) entry.getKey();
                    Item item = (Item) key.get(0);
                    for (int metadata : (int[]) entry.getValue()) ctx.discover(new ItemStack(item, 1, metadata));
                }));
        tasks.add(new OreDictionaryMembers());
        tasks.add(
            scan(
                "fluid_containers",
                () -> Arrays.asList(FluidContainerRegistry.getRegisteredFluidContainerData())
                    .iterator(),
                (value, ctx) -> {
                    FluidContainerRegistry.FluidContainerData data = (FluidContainerRegistry.FluidContainerData) value;
                    Object filled = ctx.stack(data.filledContainer, "exact");
                    Object empty = ctx.stack(data.emptyContainer, "exact");
                    if (ctx.options.recipes) ctx.batch.add(
                        "fluid-containers",
                        Json.object(
                            "filled",
                            filled,
                            "empty",
                            empty,
                            "fluid",
                            data.fluid.getFluid()
                                .getName(),
                            "amountMb",
                            data.fluid.amount));
                }));
        tasks.add(
            scan(
                "crafting",
                () -> CraftingManager.getInstance()
                    .getRecipeList()
                    .iterator(),
                RecipeCollector::crafting));
        tasks.add(
            scan(
                "furnace",
                () -> FurnaceRecipes.smelting()
                    .getSmeltingList()
                    .entrySet()
                    .iterator(),
                (value, ctx) -> {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
                    ItemStack output = (ItemStack) entry.getValue();
                    RecipeCollector.furnace(
                        (ItemStack) entry.getKey(),
                        output,
                        FurnaceRecipes.smelting()
                            .func_151398_b(output),
                        ctx);
                }));
        // Lazy flattening: do not materialize the complete recipe graph in memory.
        tasks.add(
            scan(
                "gregtech_recipes",
                () -> RecipeMap.ALL_RECIPE_MAPS.entrySet()
                    .stream()
                    .flatMap(
                        entry -> entry.getValue()
                            .getAllRecipes()
                            .stream()
                            .map(recipe -> new java.util.AbstractMap.SimpleImmutableEntry<>(entry.getKey(), recipe)))
                    .iterator(),
                (value, ctx) -> {
                    @SuppressWarnings("unchecked")
                    Map.Entry<String, gregtech.api.util.GTRecipe> entry = (Map.Entry<String, gregtech.api.util.GTRecipe>) value;
                    RecipeCollector.gregtech(entry.getKey(), entry.getValue(), ctx);
                }));
        tasks.add(
            scan(
                "thaumcraft_recipes",
                () -> ThaumcraftApi.getCraftingRecipes()
                    .iterator(),
                RecipeCollector::thaumcraft));
        tasks.add(
            scan(
                "gregtech_assembly_line",
                () -> gregtech.api.util.GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes.iterator(),
                (value, ctx) -> RecipeCollector
                    .assemblyLine((gregtech.api.util.GTRecipe.RecipeAssemblyLine) value, ctx)));
        tasks.add(
            scan(
                "aspect_definitions",
                () -> Aspect.aspects.values()
                    .iterator(),
                (value, ctx) -> {
                    if (!ctx.options.aspects) return;
                    Aspect aspect = (Aspect) value;
                    List<String> components = new ArrayList<>();
                    if (aspect.getComponents() != null)
                        for (Aspect component : aspect.getComponents()) components.add(component.getTag());
                    ctx.batch.add(
                        "aspect-definitions",
                        Json.object(
                            "tag",
                            aspect.getTag(),
                            "name",
                            aspect.getName(),
                            "color",
                            aspect.getColor(),
                            "components",
                            components));
                }));
        tasks.add(
            scan(
                "fluid_definitions",
                () -> FluidRegistry.getRegisteredFluids()
                    .entrySet()
                    .iterator(),
                (value, ctx) -> {
                    if (!ctx.options.recipes) return;
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) value;
                    net.minecraftforge.fluids.Fluid fluid = (net.minecraftforge.fluids.Fluid) entry.getValue();
                    ctx.batch.add(
                        "fluids",
                        Json.object(
                            "name",
                            entry.getKey(),
                            "displayName",
                            fluid.getLocalizedName(new net.minecraftforge.fluids.FluidStack(fluid, 1000))));
                }));
        return tasks;
    }

    private static Task scan(String name, Supplier<Iterator<?>> iteratorFactory, Reader reader) {
        return new Task() {

            private Iterator<?> iterator;
            private boolean failedInitialization;

            public String name() {
                return name;
            }

            public boolean step(ExportContext ctx) throws Exception {
                if (failedInitialization) return false;
                if (iterator == null) {
                    failedInitialization = true;
                    iterator = iteratorFactory.get();
                    failedInitialization = false;
                }
                Object value;
                try {
                    if (!iterator.hasNext()) return false;
                    value = iterator.next();
                } catch (java.util.ConcurrentModificationException e) {
                    failedInitialization = true;
                    throw e;
                }
                ctx.subject = value == null ? "null"
                    : value.getClass()
                        .getName();
                reader.read(value, ctx);
                return true;
            }
        };
    }

    private static final class CreativeVariants implements Task {

        private final Iterator<?> items = Item.itemRegistry.iterator();
        private Iterator<CreativeTabs> tabs = java.util.Collections.<CreativeTabs>emptyList()
            .iterator();
        private Iterator<ItemStack> stacks = java.util.Collections.<ItemStack>emptyList()
            .iterator();
        private Item item;

        public String name() {
            return "creative";
        }

        public boolean step(ExportContext ctx) {
            if (stacks.hasNext()) {
                ctx.discover(stacks.next());
                return true;
            }
            if (tabs.hasNext()) {
                CreativeTabs tab = tabs.next();
                ctx.subject = Item.itemRegistry.getNameForObject(item) + " / "
                    + (tab == null ? "no creative tab" : tab.getTabLabel());
                List<ItemStack> values = new ArrayList<>();
                try {
                    item.getSubItems(item, tab, values);
                } finally {
                    stacks = values.iterator();
                }
                return true;
            }
            if (!items.hasNext()) return false;
            item = (Item) items.next();
            List<CreativeTabs> attempts = new ArrayList<>();
            if (item.getCreativeTabs() != null) attempts.addAll(Arrays.asList(item.getCreativeTabs()));
            // Some mod items reject null; only use it when no real creative tab is advertised.
            if (attempts.isEmpty()) attempts.add(null);
            tabs = attempts.iterator();
            return true;
        }
    }

    private static final class OreDictionaryMembers implements Task {

        private final Iterator<String> names = Arrays.asList(OreDictionary.getOreNames())
            .iterator();
        private Iterator<ItemStack> members = java.util.Collections.<ItemStack>emptyList()
            .iterator();
        private String name;

        public String name() {
            return "oredict";
        }

        public boolean step(ExportContext ctx) {
            if (members.hasNext()) {
                Object reference = ctx.stack(members.next(), "forge_oredict");
                if (ctx.options.oreDictionary) ctx.batch.add("oredict", Json.object("name", name, "member", reference));
                return true;
            }
            if (!names.hasNext()) return false;
            name = names.next();
            members = OreDictionary.getOres(name, false)
                .iterator();
            if (!members.hasNext() && ctx.options.oreDictionary)
                ctx.batch.add("oredict", Json.object("name", name, "member", null));
            return true;
        }
    }

    private static void tagKey(Object value, ExportContext ctx) {
        if (!(value instanceof List<?>)) throw new IllegalArgumentException("Unknown Thaumcraft tag key");
        List<?> key = (List<?>) value;
        if (key.size() < 2 || !(key.get(0) instanceof Item))
            throw new IllegalArgumentException("Unknown Thaumcraft tag key");
        Item item = (Item) key.get(0);
        if (key.get(1) instanceof Number) ctx.discover(new ItemStack(item, 1, ((Number) key.get(1)).intValue()));
        else if (key.get(1) instanceof int[]) {
            for (int meta : (int[]) key.get(1)) ctx.discover(new ItemStack(item, 1, meta));
        } else throw new IllegalArgumentException("Unknown Thaumcraft metadata rule");
    }

    /** Public GT API accessed reflectively to avoid loading its unrelated optional item-interface dependencies. */
    private static final class GregTechVariants implements Task {

        private Iterator<?> items;
        private Item current;
        private int metadata;
        private BitSet enabled;
        private int offset;
        private Class<?> generatedClass;
        private Method prefix;
        private Method material;
        private Method allows;
        private Field enabledField;
        private Field offsetField;
        private boolean initializationFailed;

        public String name() {
            return "gregtech_variants";
        }

        public boolean step(ExportContext ctx) throws Exception {
            if (initializationFailed) return false;
            if (items == null) {
                initializationFailed = true;
                Class<?> base = Class.forName("gregtech.api.items.MetaGeneratedItem");
                generatedClass = Class.forName("gregtech.api.items.MetaGeneratedItemX32");
                prefix = generatedClass.getMethod("getOrePrefix", int.class);
                material = generatedClass.getMethod("getMaterial", int.class);
                allows = generatedClass.getMethod(
                    "doesMaterialAllowGeneration",
                    Class.forName("gregtech.api.enums.OrePrefixes"),
                    Class.forName("gregtech.api.enums.Materials"));
                enabledField = base.getField("mEnabledItems");
                offsetField = base.getField("mOffset");
                items = ((Map<?, ?>) base.getField("sInstances")
                    .get(null)).values()
                        .iterator();
                initializationFailed = false;
            }
            if (current == null) {
                if (!items.hasNext()) return false;
                current = (Item) items.next();
                enabled = (BitSet) enabledField.get(current);
                offset = offsetField.getShort(current);
                metadata = generatedClass.isInstance(current) ? 0 : offset;
            }
            int meta = metadata++;
            if (meta < 32000 && generatedClass.isInstance(current)) {
                Object p = prefix.invoke(current, meta), m = material.invoke(current, meta);
                if (p != null && m != null && Boolean.TRUE.equals(allows.invoke(current, p, m))) {
                    ctx.discover(
                        new ItemStack(current, 1, meta),
                        Json.object("prefix", p.toString(), "material", m.toString()));
                }
            }
            if (meta >= offset && enabled.get(meta - offset)) ctx.discover(new ItemStack(current, 1, meta));
            if (metadata >= Math.max(32000, offset + enabled.length())) current = null;
            return true;
        }
    }
}
