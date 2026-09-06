# Random Weapon Chest Loot Design

## Goal

Add a GUI-driven random weapon loot system for the standalone Infected event. An administrator selects a cuboid with a plugin selection wand, registers exact gun, ammo, and grenade items, and manually fills or clears every normal or trapped chest in that region.

The feature is deliberately manual in this release. It does not run automatically when a round starts. `Fill Chests Now` always creates a fresh loadout by emptying every discovered chest before inserting generated loot. `Clear Chests` completely empties those chests, including items not placed by this plugin.

## Confirmed Loot Rules

- Every filled chest receives exactly one gun and that gun's corresponding ammo.
- A configurable global chance may add one second, different gun. A chest never contains more than two guns.
- If the second-gun roll succeeds but fewer than two distinct eligible guns exist, the chest remains a one-gun chest.
- Every selected gun always receives its linked ammo. Guns without configured ammo make the setup invalid and cannot be used by `Fill Chests Now`.
- Grenades are optional bonus loot. A configurable global grenade chance selects at most one registered grenade type for a chest.
- Gun and grenade selection uses rarity weights. More-rare entries have lower weights and are selected less often.
- The initial rarity presets and weights are `COMMON: 100`, `UNCOMMON: 50`, `RARE: 20`, `EPIC: 8`, and `LEGENDARY: 2`. The GUI displays the preset rather than exposing raw probability math.
- Second-gun chance defaults to 10%. Grenade chance defaults to 25%. Both are editable from 0% through 100% in the wizard.
- Each gun editor configures an ammo bundle range. One bundle contributes the captured ammo stack amount, and multiple bundles are consolidated into the fewest legal stacks before placement. Stacks split only at the item's real maximum stack size. The default is one bundle; minimum and maximum are constrained to valid positive values.
- Each grenade editor configures its rarity and a quantity range. The selected exact grenade item is cloned to produce that amount, split according to its maximum stack size when necessary.
- Gun selection for the second slot is weighted without replacement, so the same gun cannot be selected twice for one chest.

## Main GUI and Wizard

The existing 36-slot `Infected Event Control` remains the root menu. A chest icon named `Random Weapon Chests` is added at the centered footer slot 31. Existing main-menu controls retain their current slots.

Clicking the chest opens a separate 54-slot `Weapon Chest Wizard`; guns and grenades do not appear directly in the main menu.

The wizard contains:

- A region summary showing point 1, point 2, world, dimensions, intersecting chunk count, and configured scan limit.
- A `Selection Wand` control that gives or refreshes the administrator's wand and explains its controls.
- Separate `Guns & Ammo` and `Grenades` category buttons.
- Global `Second Gun Chance` and `Grenade Chance` controls. Left-click increases and right-click decreases by 5 percentage points; shift-click changes by 25 percentage points.
- `Fill Chests Now` and `Clear Chests` actions, each protected by a confirmation screen that shows the exact region and explains that chests are discovered during execution.
- Standard Back, Close, and Help controls consistent with the existing typed admin menus.

### Selection Wand

The wizard gives the administrator a uniquely named blaze rod tagged with an Infected persistent-data key. Only this tagged item acts as the selector.

- Left-clicking a block sets point 1 to that block's coordinates.
- Right-clicking a block sets point 2.
- Selection events are cancelled so the wand does not break, place, open, or activate the clicked block.
- Both points must be in the same world. Selecting a point in another world replaces the incompatible selection and explains what happened.
- The selection is saved as event setup configuration and survives ordinary plugin reloads. It is not an active-round or chest-content persistence system.

### Gun and Ammo Menus

The paginated `Guns & Ammo` menu lists registered guns using exact clones of their captured item as icons. The administrator picks a gun up from their inventory and drops it onto the marked gun input. The drop is intercepted and cancelled, so an exact copy is registered without consuming the original. Air and the selection wand are rejected.

Clicking a registered gun opens its editor. The editor provides:

- Exact gun preview.
- Rarity preset control.
- Linked-ammo status and a marked ammo input. Dropping the exact ammo stack onto it links a non-consumed copy to that gun.
- Minimum and maximum ammo-bundle controls.
- A marked replacement-gun drop input.
- A confirmation-protected delete action. Deleting a gun also removes its ammo association because ammo belongs to that gun entry.

The same ammo item may be linked independently to multiple guns. The system does not attempt to infer ammo by material, name, lore, or weapon-plugin API.

### Grenade Menus

The paginated `Grenades` menu lists exact registered grenade snapshots. Dropping an inventory item onto the marked grenade input registers a non-consumed copy. Clicking an entry opens an editor for its rarity, minimum quantity, maximum quantity, drop-based replacement, and confirmation-protected deletion.

## Exact Custom-NBT Preservation

Custom NBT support is a hard requirement. Registered items are not reconstructed from material, display name, lore, or a lossy hand-authored YAML map.

Each dropped `ItemStack` is cloned and serialized with the server API's binary item serialization, then Base64-encoded in `weapon-loot.yml`. Deserialization produces a fresh item snapshot and loot generation clones that snapshot again before use. This preserves all data understood by the running Paper version, including persistent-data-container tags, custom model data/data components, enchantments, attributes, names, lore, plugin-specific metadata, and captured stack amount.

Every load validates that each stored payload can be decoded and deserialized. Invalid or version-incompatible entries are reported by stable entry ID and excluded from eligibility; any invalid gun or missing linked ammo blocks filling. The source configuration remains untouched for manual repair. Because undecodable bytes cannot produce a safe GUI item preview, corrupt entries must be repaired or removed directly in `weapon-loot.yml`, followed by Reload Config; unrelated GUI edits preserve those raw entries.

Binary item serialization is server-version-aware. This feature guarantees exact round-tripping on the supported Paper 1.21.4 runtime; a future Minecraft data migration must be verified before claiming compatibility with a different server version.

## Data Model and Storage

`weapon-loot.yml` is separate from the existing gameplay `config.yml`. This avoids bloating normal event settings with long Base64 item payloads and permits atomic, focused saves.

The model contains:

- Region: world UUID/name plus integer point 1 and point 2 coordinates.
- Global settings: second-gun chance, grenade chance, and safety limits.
- Guns keyed by generated stable UUID: serialized gun, rarity, serialized linked ammo, minimum ammo bundles, and maximum ammo bundles.
- Grenades keyed by generated stable UUID: serialized item, rarity, minimum quantity, and maximum quantity.

Display names are presentation only and may be duplicated. Stable UUIDs identify entries in holder slot mappings, editors, confirmations, and storage, preventing renamed or visually identical items from targeting the wrong configuration.

Writes use a temporary sibling file followed by replacement of `weapon-loot.yml`, so a failed save does not knowingly leave a half-written catalog. The service keeps immutable validated snapshots for generation and replaces the in-memory snapshot only after a successful save/reload.

## Chest Discovery

Chest discovery does not iterate every block in a potentially large cuboid. It enumerates the chunks intersecting the selected region and asks each chunk only for live normal/trapped-chest tile states inside the inclusive cuboid. Using live states ensures generated contents mutate the placed inventories rather than detached snapshots.

The operation accepts already-loaded chunks and pregenerated chunks that are currently unloaded. It keeps at most eight Paper asynchronous chunk-load requests in flight, polls completed requests from a bounded server-tick task, temporarily loads pregenerated chunks with terrain generation disabled, and immediately unloads temporary chunks that contain no selected chest. Chunks containing selected chests receive operation-owned plugin tickets only until planning and mutation finish, after which those tickets are removed and chunks loaded by the operation are unloaded. A selected chunk that is not pregenerated aborts the action before mutation; the plugin never generates missing terrain. Raw block volume is not limited because discovery never scans ordinary blocks. A wizard/YAML setting limits intersecting chunks from 1 through an absolute maximum of 5,000; the default is 5,000.

Double chests whose two halves are inside the cuboid are deduplicated by their canonical pair of block locations and treated as one 54-slot inventory. Retained chest chunks are rescanned after asynchronous loading stabilizes, preventing a cross-chunk double chest from first appearing as a single chest and later being counted twice. If only one half is inside the selection, only that 27-slot block inventory is eligible. Single normal and trapped chests are treated as 27-slot inventories. Barrels, shulker boxes, minecart chests, and all other containers are excluded.

Discovery results are used only for the current confirmed action. Fill and clear confirmations start a fresh bounded scan rather than trusting stale locations from the menu.

## Validation and Manual Actions

The loot system follows the existing start-flow pattern: validate first, return a structured result, show all actionable failures, and mutate only after the requirements pass.

Shared action conditions are:

- The actor still has `infected.admin`.
- The event is in `LOBBY`; no round is starting or running.
- No fill or clear operation is already active.
- Both selection points exist in the same available world.
- The intersecting chunk count is within the configured limit, which cannot exceed 5,000.
- Every intersecting chunk is either already loaded or pregenerated and safe to load without terrain generation.
- At least one unique chest inventory is discovered.

`Fill Chests Now` additionally requires at least one valid gun and valid linked ammo for every registered gun. All rarity and quantity settings must be valid.

Before changing any chest, the loot engine builds the complete loadout plan for every discovered inventory. It consolidates ammo bundle quantities into the fewest legal stacks, splits only at real maximum stack sizes, randomizes eligible slots, and verifies that every required gun, ammo stack, and grenade fits. If any chest cannot hold its planned loadout, the entire action fails before any chest is emptied.

The service owns a round-start lease for the entire operation, so normal start commands and GUI actions are rejected until chest work finishes. The round must remain in `LOBBY` throughout scanning and planning; defensive phase checks abort before mutation and release retained chunks if that condition changes externally. Once a bounded mutation pass has begun, it finishes the remaining batches so an interruption cannot leave only part of the selected chest set changed.

After successful planning, the plugin clears each discovered inventory and applies its planned fresh contents on the main thread. `Clear Chests` rescans and then clears every slot in every discovered inventory. Both actions report the number of unique inventories affected. Emptying is intentionally destructive and includes manually placed items.

## Architecture

The GUI remains a presentation and navigation layer. Loot behavior is separated into focused units:

- `WeaponLootRepository`: loads, validates, and atomically saves the region, settings, and exact item snapshots.
- `ItemSnapshotCodec`: converts exact `ItemStack` values to and from version-aware Base64 binary payloads.
- `WeaponLootCatalog`: immutable validated gun, ammo, grenade, rarity, and global-setting data.
- `ChestRegion`: normalizes the two points, exposes inclusive bounds, dimensions, volume, and intersecting chunk coordinates.
- `ChestDiscoveryService`: finds and deduplicates eligible inventories without scanning every block.
- `ChestLootGenerator`: pure random selection and capacity planning; it has no GUI, config, or world mutation responsibility.
- `WeaponChestService`: coordinates validation, discovery, plan creation, fill, and clear operations and returns structured action results.
- `WeaponSelectionListener`: owns tagged-wand interaction and point updates.
- Focused weapon-menu holders and GUI methods: own stable entry IDs and click routing without putting loot rules into `AdminGuiManager`.

The plugin wires these services once during enable and reloads the loot repository when the existing Reload Config action runs. No gun-plugin dependency is introduced.

## Failure Handling and Safety

- Confirmation actions recheck permission, phase, region, chunks, catalog, and chest presence at execution time.
- Stale menu entries are rejected and return to the relevant catalog page.
- Empty-hand capture, invalid binary items, missing ammo, invalid ranges, unloaded worlds, non-pregenerated chunks, oversize regions, zero chests, and insufficient inventory capacity produce clear messages without modifying chests.
- A second administrator cannot start another fill/clear while one is active.
- Inventory menus continue cancelling transfer, hotbar, collect, drag, and unsupported click behavior through typed holders.
- The selection wand has no effect for unauthorized players and no effect outside its explicit block-click gestures.
- Fill and clear do not modify chest blocks, signs, hoppers, redstone, or any other arena blocks.
- The first release does not remember generated loot ownership, automatically refill, regenerate on timers, or restore cleared chest contents.

## Testing and Verification

Automated tests cover:

- Exact binary item round-tripping, including PDC/custom metadata and stack amount.
- YAML repository reload, malformed payload handling, stable IDs, and save failure behavior.
- Cuboid normalization, inclusive bounds, dimensions, safety limits, and chunk enumeration.
- Normal/trapped chest filtering and double-chest deduplication.
- Weighted rarity selection with injectable deterministic randomness.
- One guaranteed gun, linked ammo guarantee, optional distinct second gun, two-gun maximum, optional grenade, and quantity boundaries.
- Capacity planning and all-or-nothing rejection before mutation.
- Fresh-fill clearing and complete clear behavior.
- Phase, permission, region, pregenerated-chunk, catalog, chest-presence, and concurrent-operation validation.
- Selection-wand tagging, left/right point behavior, cancellation, cross-world handling, and permission checks.
- Main-menu chest slot, holder types, navigation, editor click routing, pagination, confirmations, stale targets, and protected inventory behavior.
- Existing GUI and gameplay tests remain green.

Final automated verification is `mvn.cmd clean package` followed by `git diff --check`. Because exact double-chest holder behavior, third-party gun metadata, and visible menu interaction depend on a real Paper server, the handoff will include a short live-server checklist and will not claim those behaviors as live-tested unless that test is actually run.

## Scope Guardrails

This release adds one configured region, one manual loot catalog, and manual fill/clear actions. It adds no automatic round-start fill, timed regeneration, per-arena profiles, database, restart recovery, chest-content restoration, barrels, gun-plugin API dependency, economy, or unrelated gameplay changes. Those can be added later without changing the exact-item catalog or loot-generator boundaries.
