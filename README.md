# GTNH Dataset Exporter

A client-side Forge tool for developers working with **stable GTNH 2.9.0 / Minecraft 1.7.10**.
Export observed item variants, Thaumcraft aspects, registered recipes, ore dictionary entries and rendered item icons.
The dataset is independent of any website or planner.

**Status: first functional release.** The exporter has been exercised in stable GTNH 2.9.0 on Windows:
item/aspect, recipe/ore dictionary and icon exports completed, and the full datasets passed the bundled validator.
Recipe semantics and some mod-specific item rendering remain partial; see [the release notes](CHANGELOG.md).

## Install and export

1. Use a separate copy of **stable GTNH 2.9.0** on Windows.
2. Put the normal exporter JAR in `mods/`. Do not install the `-dev.jar` or `-sources.jar`.
3. Load a singleplayer world. A small creative test world is convenient.
4. Open the inventory once so NEI can finish loading, then run `/gtnhexport`.
5. Choose categories in the modal and press **Start Export**.
6. Use **Open Output Folder** when the run finishes. Always inspect `manifest.json` and `coverage.json`.

The output is inside the game instance:

    gtnh-dataset-exporter/exports/<UTC timestamp>-<run ID>/

The command opens a centered modal with category checkboxes, 32/64/128 px icons,
icon reuse, three work budgets, progress, cancellation and output-folder access.
Selections are saved in `config/gtnhdatasetexporter.cfg`.
Items are always included because other categories reference their IDs.
The small **EX** button above NEI's Options button opens the same screen. Hover it for a tooltip.
The `/gtnhexport` command remains available.

Commands:

| Command | Action |
| --- | --- |
| `/gtnhexport` | Open settings or return to the current run |
| `/gtnhexport cancel` | Request cancellation between work units |
| `/gtnhexport inspect` | Inspect the held stack's exact ID and aspect result; print NBT in the game log |

Inspect also reports whether the item was written in the last export in this game session and its candidate sources.
It is unavailable during an active export because aspect lookups may populate Thaumcraft caches.
Closing the screen does **not** cancel a run. Unloading the world requests cancellation.
The screen requests the normal singleplayer pause; exporting proceeds through client render events.
Closing it resumes the world. Avoid changing scripts, resource packs or registrations during export.

## What is collected

Candidate discovery runs regardless of which optional output categories are selected:

- Item registry and all advertised creative tabs.
- NEI's loaded item catalog, independent of the current search query.
- GregTech's registered meta-items and X32 material generation rules, including variants hidden in creative.
- Thaumcraft object tags and grouped metadata tags.
- Ore dictionary members and registered fluid containers.
- Inputs and outputs of vanilla/Forge crafting, furnace, GT recipe maps, GT Assembly Line and Thaumcraft crafting.

Every item retains its discovery sources. No item or aspect profiles are hardcoded.
Metadata and **all observed NBT** participate in identity; stack count does not.
Wildcard metadata remains a predicate and is never rendered as an item.

Aspect results distinguish `present`, `empty`, `absent` and `error`.
The resolver calls `ThaumcraftCraftingManager.getObjectTags` followed by `getBonusTags`,
as in the supplied Thaumcraft 4.2.3.5 furnace bytecode. Installed runtime patches apply to those calls.
Furnace capacity and patched acceptance are not simulated: `smeltability` is explicitly `unknown`.

Recipe output includes fluids, chances, alternative inputs, zero-size catalysts, duration,
EU/t, available research fields and source classes. **The recipe category is currently `partial`.**
Unknown classes, dynamic results, container returns, special metadata and machine-specific conditions
must not be interpreted as fully supported. Raw and normalized ingredients are observations,
not an executable recipe specification or a computed availability score.

## Coverage and limits

`completed` means the run finished. It does **not** mean that every possible GTNH item state or recipe system exists in the export.
Check each category's status, each source's status, counters and the limitations in the coverage report.

- Arbitrary NBT, every damage/charge value, world drops and unlisted recipe systems are not exhaustively enumerated.
- Candidates are spooled to disk in the export directory and deduplicated in 128 partitions before item writing.
  There is no cumulative NBT or variant-count cutoff. Individual NBT still has depth/node/array limits and a
  64 Ki-character encoded limit. Rejections are reported.
- Work is split across frames; a slow call inside another mod cannot be preempted safely.
- The writer has a bounded queue. JSON serialization, gzip, PNG encoding and hashing run off the game thread.
  Candidate spooling and partition consolidation perform buffered disk I/O on the game thread, subject to the
  frame budget. The temporary `candidate-spool` directory is removed after the run finishes.
- GUI names use the current language, recorded in the manifest. English can be selected in Minecraft for English names.
- Icons are snapshots, not animations. World/time-sensitive renderers may differ between runs.
- Icon reuse validates a PNG/checksum pair and keys it by stack identity, render version, size and environment.
  A cache hit is copied into the export, so the dataset does not depend on the external cache directory.
- Cancellation retains completed files and writes a cancelled manifest. Process termination may leave a `running`
  manifest and `.part` files. A new run rescans data and can reuse valid icons; arbitrary job resume is not implemented.
- Exact NEI, GT and Thaumcraft dependency versions are checked against the stable pack. This is **not** full
  pack certification. All loaded mod versions and hashes of instance mods/config/scripts/resource packs are recorded;
  the target pack version is not presented as an independently detected pack version.

## Validate a dataset

Python 3.9+ and the standard library are sufficient; users of the mod do not need Python to export.

    python3 tools/validate_dataset.py /path/to/export

The validator checks checksums, byte sizes, JSONL record counts, duplicate item IDs, item references,
aspect/icon coverage and PNG signatures/dimensions. It prints status distributions and counts by mod.
Use `--allow-partial` to inspect a cancelled/failed run; missing references remain diagnostics.
See [the format contract](docs/FORMAT.md) and [Windows acceptance checklist](docs/WINDOWS-TEST.md).

## Development

Build on macOS Apple Silicon with **JDK 25** using the provided Gradle wrapper:

    ./gradlew spotlessApply
    ./gradlew build

The normal distributable is `build/libs/gtnhdatasetexporter-<version>.jar` (reobfuscated by the build).
The build uses Java 8 bytecode; JDK 25 is the build JVM, not a new runtime requirement for the pack.
Game dependencies are pinned compile-only dependencies and are not bundled into the exporter JAR.
This workflow does not launch Minecraft on macOS.

Automated tests cover typed canonical NBT, stack identity/provenance, wildcard handling,
sharded gzip output, finalization, cancellation, disk failures, checksums and icon cache validation.
They do not replace the Windows game test.

Code is organized into `client` (screen/command/rendering), `integration` (specific game APIs),
`export` (session/catalog/writer) and `data` (identity/serialization).
The GT meta-item integration uses a small verified reflection boundary to avoid loading unrelated
optional item-interface dependencies at compile time. It is fixed to the pinned GT version.

## License

MIT; see [LICENSE](LICENSE). Game/mod dependencies and exported art retain their own licenses.
No Oracle exporter source was copied into this project.
