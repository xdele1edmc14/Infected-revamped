# Outdoor Generated Weapon Chests Design

## Goal

Add a manual administration workflow that places a configurable number of event chests across the selected arena, fills or empties only eligible event chests, and removes only structures owned by the plugin. The selected arena is approximately 1,000 by 1,000 blocks and already generated. Placement must look organic and must be restricted to safe outdoor ground.

## Confirmed Structure and Actions

Each generated site is a level 3x3 layer of nine gold blocks at ground height. A single chest sits one block above the center gold block, so there is gold directly under the chest.

The weapon chest wizard exposes four independent, confirmation-protected actions:

- `Generate Layout`: remove the prior plugin-owned layout, then place a fresh layout using the configured count.
- `Fill Loot`: freshly fill eligible event chests using the existing weapon loot catalog.
- `Empty Loot`: empty eligible event chest inventories without removing blocks.
- `Remove Generated`: remove only plugin-owned structures and restore their recorded original terrain.

The generated count is stored in `weapon-loot.yml`, defaults to 100, is constrained to 1 through 1,000, and is adjustable in the GUI by 10 or 50 depending on whether Shift is held.

## Eligibility and Ownership

Loot operations accept a chest when either condition is true:

1. Its tile-state persistent data contains this plugin's generated-chest identifier.
2. It sits exactly above the center of a complete 3x3 gold-block layer.

Physical removal is deliberately stricter. Only placements in the generated-placement registry are considered, and a chest block is removed only when its persistent identifier matches the registered placement. A manually built patterned chest can receive loot but is never physically removed by the plugin.

The plugin stores generated placements separately from the loot catalog in `generated-weapon-chests.yml`. Each record contains a stable UUID, world name, chest coordinates, the exact block-data strings of the nine blocks replaced by gold, and a `PENDING` or `ACTIVE` transaction state. Writes use a temporary file and atomic replacement when supported.

## Outdoor Placement

Placement uses seeded, adaptive Poisson-disc sampling over X/Z coordinates. A regeneration creates one fresh seed and uses it for the lifetime of that in-memory planning operation. Planning changes no blocks, so an interruption can safely restart with a new seed. Candidate checks are performed in bounded batches on the server thread because Bukkit world access is not thread safe.

Chunks do not need to be loaded before the action starts. Like loot discovery, generation asynchronously loads already-generated chunks without generating new terrain. It temporarily tickets only candidate footprints that remain relevant, retains accepted-site and registered-old-layout chunks through mutation, then releases the tickets and saves/unloads chunks that the operation loaded. This avoids holding the entire 1,000x1,000 arena in memory at once. A candidate touching any ungenerated chunk fails safely instead of expanding the world.

For a candidate to qualify:

- Its entire 3x3 footprint is inside the selected cuboid with a one-block horizontal inset.
- All nine columns have the same highest motion-blocking Y coordinate. Leaves count as obstructions, so sites are never selected under tree canopies.
- The platform Y and chest Y are inside the selected vertical bounds.
- All nine surface blocks and the next three blocks below them are solid and non-liquid; this rejects ordinary roofs and thin overhangs.
- The two blocks above every footprint column are air, leaving the chest and its surroundings unobstructed.
- No replaced block is a tile state.

Random candidates are accepted only when they remain outside the current Poisson radius of accepted sites. The initial radius is derived from selected horizontal area and requested count. If valid terrain cannot satisfy that radius after a bounded number of attempts, the radius gradually relaxes without dropping below the 3x3 non-overlap distance. The operation fails without changing blocks if it cannot produce the configured count within its total attempt budget.

## Mutation and Recovery Safety

The operation completes discovery and layout planning before changing the world. New sites cannot overlap the previous registered layout because those occupied sites fail candidate clearance.

Regeneration then processes small batches per tick with a write-ahead registry:

1. Remove each old registered site and restore its original block data.
2. Revalidate each planned new site and capture the nine original block-data values.
3. Atomically persist the entire new layout as `PENDING` before changing any new site.
4. Immediately before each mutation, revalidate the site and compare all nine current block-data values with the captured originals.
5. Replace those blocks with gold, place the center chest, and attach the stable ownership UUID.
6. Atomically mark the entire layout `ACTIVE` after all structures exist.

The old registry remains intact until all old sites have been restored, so an interruption can safely retry restoration. A pending registry similarly lets `Remove Generated` recover an interrupted placement, including a partial gold platform or the tiny interval where a complete generated chest exists before its marker update. Registry files are rewritten only at transaction boundaries, not once per chest.

Ownership is checked again in the same tick immediately before every restoration. If an active registered coordinate contains an untagged chest, that site is treated as a conflict and left untouched. Registered gold coordinates are restored only while they still contain gold; player-changed non-gold blocks are preserved. Manual chests and gold elsewhere are never examined by physical removal.

For double chests, loot discovery returns the combined inventory only when both halves independently satisfy the region and eligibility rules. An ordinary chest attached to an eligible chest therefore cannot be emptied through the eligible half.

All generate, remove, fill, and empty actions share one operation gate. They can run only in the lobby, only one can run at once, and a round cannot start while an operation holds the gate.

## Module Seams

- `GeneratedChestRepository`: durable ownership and original-terrain records.
- `OutdoorChestSiteValidator`: converts an X/Z candidate into a valid level outdoor site or rejects it.
- `PoissonDiscPlacement`: owns random spacing, adaptive radius, and bounded attempts without Bukkit dependencies.
- `GeneratedChestService`: coordinates validation, tick-batched planning, regeneration, removal, tagging, and restoration.
- `ChestEligibility`: centralizes the plugin-tag or exact-pattern rule used by discovery.
- `ChestOperationGate`: provides the shared single-operation lease used by layout and loot mutations.

The existing `ChestLootGenerator` remains unchanged. The GUI remains presentation and routing only.

## Verification

Automated tests cover count configuration persistence and limits, GUI routing and stale-count confirmation, pattern and tag eligibility, mixed-eligibility double chests, deterministic Poisson spacing and bounded failure, outdoor validation, registry round-tripping, write-ahead ordering, mutation-time revalidation, interrupted-placement recovery, ownership-safe removal, and operation gating. Final verification is `mvn.cmd clean package` and `git diff --check`. A live Paper 1.21.4 check remains necessary for heightmap behavior, tile-state persistence, chest opening, and visual placement on the actual map.
