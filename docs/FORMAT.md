# Dataset format 1.0.0

UTF-8 throughout. The schema version is independent of the exporter/modpack versions.
Consumers must reject unknown major versions, tolerate additional fields, and inspect status fields.

## Manifest and integrity

`manifest.json` is created with `state: running` before discovery and replaced atomically after finalization.
Final states are `completed`, `cancelled`, `failed`. Abrupt process termination may leave `running`.
Only files listed in the manifest are finalized data files. Ignore `.part` files.

The manifest contains the run ID, UTC times, exporter version (including the Git revision/dirty suffix),
identity/schema versions, options, environment, input hashes, category statuses and file index.
`environment.targetGtnhVersion` is the supported target, not a claim of independent version detection.
`packVerification: dependency_versions_only` documents the actual verification level.
`inputHashStatus` can be partial when cancelled during fingerprinting.

Category status is `complete`, `partial` or `not_requested`. Complete is relative to the declared
sources/observed states; it never asserts enumeration of arbitrary NBT states.
Recipes remain partial until all required semantics and source coverage are validated.

The `files` index contains paths, SHA-256 hashes and byte sizes. JSONL shards also contain record counts
and uncompressed byte sizes. Shards roll at approximately 32 MiB of uncompressed UTF-8, between records.
PNG hashes and sizes are in the checksummed `icons` index. `coverage.json` is also checksummed.

## Identity

An item ID is `registryName@metadata` for a stack without NBT, or
`registryName@metadata#<full SHA-256 of canonical NBT JSON>` for a stack with NBT.
An empty compound is distinct from absent NBT. Registry names and metadata are also separate fields.
Count is excluded, and exported catalog stacks represent one item.

NBT is a typed tree: `{type, value}`; lists additionally store `elementType`.
Compounds have lexicographically sorted keys using Java UTF-16 string ordering.
Lists retain order. Numeric values use the vanilla NBT numeric literal string, retaining type,
long precision and floating-point special values. Byte arrays use lowercase hex; int arrays use JSON integers.
Strings retain their contents. Canonical JSON is compact Gson JSON with HTML escaping disabled and nulls retained;
object field order is `type`, `elementType` (lists only), `value`. Gson escapes U+2028 and U+2029.
There is no NBT blacklist, ore unification or localization in identity generation.

Wildcard metadata (`32767`) is encoded as `metadata: "*"` with `itemId: null` in a predicate reference.
It has no catalog item or icon. NBT matching is source-defined and independent of exact catalog identity.

## Files

| Prefix | Records |
| --- | --- |
| `item-types` | Registered Item classes, registry names and subtype flags |
| `items` | Exact variant IDs, typed NBT, names/status, discovery sources, optional GT material/prefix |
| `aspects` | One result per written item when selected: status, aspect-tag quantities, resolver, smeltability |
| `aspect-definitions` | Aspect tags, localized names, colors and component tags |
| `recipes` | Source-dependent recipe observations, content hashes and semantic limitations |
| `oredict` | One group/member pair per row; empty groups have a null member |
| `fluids` | Fluid registry names and localized names |
| `fluid-containers` | Registered empty/filled containers and fluid amounts |
| `icons` | Item ID, status, relative PNG path, size, dimensions, hash and visible bounds |
| `errors` | Source, operation, optional item ID, exception, message and bounded stack trace |

All prefixes above use numbered `.jsonl.gz` shards. A category with zero records need not have a file.
`coverage.json` is a standalone JSON document with source statuses, counters, limits and known omissions.
Items whose mod throws while obtaining a localized name retain their exact ID and NBT. They receive a technical
`displayName` of `registryName (metadata)`, `nameStatus: fallback`, and an `errors` row with the original exception.
The temporary `candidate-spool` directory is an implementation detail and is not included in the manifest index.

Aspect `present` means at least one positive quantity; `empty` means an API result without positive quantities;
`absent` means null; `error` means resolution failed. Zero/negative values are retained for diagnosis.
If Aspects is disabled, the manifest says `not_requested` and no per-item aspect rows are emitted.
`smeltability: unknown` must not be converted into a guaranteed furnace recipe.

## Recipe observations

Kinds currently include `crafting`, `furnace`, `gregtech_machine`, `gregtech_assembly_line`,
`thaumcraft_arcane`, `thaumcraft_crucible`, `thaumcraft_infusion`, `thaumcraft_unknown`.

Each recipe has a content hash `id`. Identical observed registrations may share that ID; rows preserve multiplicity.
There is no globally stable registration index. File/row order is not identity.

For crafting, `inputs` is the ordered ingredient list (row-major for known shaped layouts).
A nested list in an ingredient slot denotes alternatives, not simultaneous inputs. Null slots remain null.
Shaped Forge ore recipes currently expose their inputs but not their dimensions/mirror flag; these are partial.
Output quantities are retained. Container returns and dynamic outputs are not simulated.

GT machine `inputs` is the combined input view supplied by GT, including amount, expected consumed amount,
ore group and NBT sensitivity. `rawInputs` retains the registered slots. `inputAlternativesBySlot` and
`oreDictionaryGroupsBySlot` preserve the explicit GT alternative-registration view where available.
Zero-sized input stacks are non-consumed catalysts; do not silently discard them.
`inputChances`, `outputChances` and fluid chance arrays are parallel to the **raw** slots;
the denominator is 10,000, with absent arrays meaning certainty. Input chances concern consumption,
not whether the ingredient is needed. Fluid quantities use mB. Fake/disabled/hidden flags are retained.
Special metadata and machine-specific matching remain partial, even when all arrays are available.

TC recipes retain available research/aspect requirements. Nested/dynamic output forms may include unsupported
values; `semantics: partial` is intentional. Unknown ingredient objects retain their class name and increment
diagnostic counters. Do not treat an unsupported object or null dynamic output as an empty requirement/result.

## Icons

Transparent PNGs at the selected dimensions, without count or durability overlays.
File names are SHA-256 of the item ID, safe on Windows. The index maps IDs to files explicitly.
`visibleBounds` is `[left, top, width, height]` at alpha >= 16; empty renders use four zeros.
Statuses are `rendered`, `cached`, `empty`, `error`. Error rows have no image path.
Empty renders remain available for inspection but make the icon category partial.
Animated/time-dependent renderers are captured at the current game render state; there is no all-frame promise.

## Reproducibility and comparison

Compare item IDs, aspect profiles, recipe content and source counts under matching environments.
Ignore run IDs, timestamps, file ordering and cache-vs-render status when comparing semantic results.
Recipe source iteration order and animated pixels may vary. A matching environment fingerprint is a cache key,
not a proof that all game logic is deterministic.
