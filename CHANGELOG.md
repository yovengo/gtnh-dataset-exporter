# Changelog

## 0.2.0 — First functional release

### Added

- `/gtnhexport` and a compact **EX** button in NEI open the same export settings screen.
- Exact observed item variants with typed NBT, source provenance, display names and optional GregTech material data.
- Thaumcraft aspect profiles; crafting, furnace, GregTech and Thaumcraft recipe observations; ore dictionary and fluid data.
- Transparent 32/64/128 px item icons with a verified reuse cache.
- Disk-backed candidate catalog, bounded output writer, sharded JSONL, checksums, coverage report and dataset validator.
- `/gtnhexport inspect` for the held item, plus cancellation and output-folder access.

### Windows validation

- Tested in stable GTNH 2.9.0 with 232,540 item variants in the recipe/ore dictionary and icon runs.
- The complete recipe/ore dictionary dataset passed `tools/validate_dataset.py`: 260,252 recipe rows and 156,071 ore dictionary rows, with no unresolved item references.
- The complete icon dataset passed the validator, including hashes and dimensions of 232,519 PNG files. Of 232,540 icon records, 218,245 rendered, 14,274 were transparent and 21 reported mod renderer exceptions.
- A separate aspect run wrote one profile for each of 230,579 item variants, with no aspect resolution errors. Its observed variant count differed from later runs; exports are snapshots of the installed game's observed states.

### Known limitations

- `completed` means the run finished; category `partial` statuses and source limitations remain authoritative in `manifest.json` and `coverage.json`.
- Recipe semantics are partial for custom recipe classes, dynamic outputs, container returns and machine-specific conditions. The tested pack reported 48,529 recipe registrations with incomplete interpretation.
- Some advertised item states are not registered or are not renderable by their owning mods. Those cases remain visible as warnings, fallback names, transparent icons or icon errors rather than being silently removed.
- The exporter enumerates observed variants, not every possible NBT, damage, charge or world-dependent state.
- The current release targets stable GTNH 2.9.0 in a singleplayer world. Compatibility with other pack versions is not established.

Install the normal `gtnhdatasetexporter-0.2.0.jar` in the instance's `mods/` directory. Do not install the `-dev` or `-sources` JARs.
