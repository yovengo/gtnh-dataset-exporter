# Windows acceptance run

Use a separate stable 2.9.0 instance and a small creative world.

1. Install the normal JAR, launch, open the inventory and wait for NEI's item loading.
2. Open an inventory and use the **EX** button above NEI's Options button; also try `/gtnhexport`.
   Check that both open the same screen, the cross closes it without cancelling a running export,
   and the screen remains readable at GUI Scale Auto/Normal/Large. Check checkbox alignment at a smaller window size.
3. First export: Items + Aspects; disable Recipes, Ore Dictionary and Icons output.
   Candidate discovery should still visit the same sources as a full export.
4. Check progress responsiveness, world pause while the modal is open, closing/reopening the screen,
   cancellation during discovery, and starting another run after cancellation.
5. Validate a completed dataset with `tools/validate_dataset.py`. Review per-source errors, unknown recipe classes,
   item counts by mod, aspect status counts and GT prefix/material fields.
   Specifically check that `furnace`, `gregtech_recipes`, `gregtech_assembly_line`, `thaumcraft_recipes`,
   `aspect_definitions`, and `fluid_definitions` are no longer `not_started`. The previous run stopped discovery
   at the cumulative 32 MiB NBT cutoff.
   Check `writtenItems == discoveredVariants`, and that the export directory no longer contains `candidate-spool`
   after completion. The 40 observed BuildCraft facade name exceptions should now produce `nameStatus: fallback`
   with a readable technical name; the underlying exceptions should still appear in the errors file.
6. Use `/gtnhexport inspect` on several materials/forms, metadata variants, an enchanted item and a fluid container.
   Compare exported aspect quantities against in-game behavior. A few known examples are regression controls,
   not the definition of coverage.
7. Enable Recipes + Ore Dictionary. Check that the item ID set matches the prior run and that references resolve.
   Review GT alternatives, zero-sized catalysts, probabilistic outputs, Assembly Line and TC recipes.
8. Enable Icons at 64 px. Inspect flat icons, 3D blocks, GT generated items, enchantment effects and custom renderers.
   Look for empty, clipped, upside-down or opaque-background images and ensure the game UI renders normally afterward.
9. Repeat with the same options: the icon index should contain cache hits. Changing icon size/resource packs should
   invalidate affected cache keys. Changing only export category selection should not invalidate cached pixels.
10. Cancel during rendering, unload the world during a run, and retry. Verify cancelled/partial status and inspectable files.

Return the export's `manifest.json`, `coverage.json`, any `errors-*.jsonl.gz`, relevant game log excerpts,
and screenshots of the modal/icons if something is wrong. For coverage analysis, retain the full dataset.

Acceptance is not just "the JAR loaded": the modal, runtime API behavior, candidate coverage, aspect results,
OpenGL state restoration, memory use and repeatability all need the full-pack test.
