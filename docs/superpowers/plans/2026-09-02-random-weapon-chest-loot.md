# Random Weapon Chest Loot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a main-GUI wizard that captures exact custom-NBT guns, linked ammo, and grenades, selects a chest region with a wand, and manually fills or completely clears all normal/trapped chest inventories in that region.

**Architecture:** A focused `loot` package owns binary item snapshots, YAML storage, cuboid/chest discovery, weighted planning, and validated mutation. A focused `gui.weapon` controller renders the wizard and editors through typed holders, while the existing main GUI only adds the chest entry point. All chest mutation occurs after a complete validation and capacity-planning pass.

**Tech Stack:** Java 21, Paper API 1.21.4, Bukkit YAML, JUnit 5.11.4, Mockito 5.14.2, Maven.

## Global Constraints

- Preserve exact server-understood item NBT/data components with `ItemStack.serializeAsBytes()` and `ItemStack.deserializeBytes()`; do not reconstruct items from material/name/lore.
- Every filled chest has one gun plus linked ammo; an optional second gun must be distinct; no chest exceeds two guns.
- Fill is a fresh loadout and Clear removes every item, including manually placed contents.
- Only normal and trapped chests are eligible; double chests count once; barrels and other containers are excluded.
- Actions are manual, require `infected.admin`, require `LOBBY`, temporarily load pregenerated chunks in bounded batches, and never generate chunks.
- Leave all source, tests, specs, and plans uncommitted unless the user separately requests a commit.
- Preserve the unrelated untracked `Bugs To fix.md` and the checkout's existing two documentation commits.

## File Structure

- `src/main/java/me/DaWHeL/infected/loot/LootRarity.java`: named rarity weights and cycling.
- `src/main/java/me/DaWHeL/infected/loot/BlockPoint.java`: stored integer world/block coordinate.
- `src/main/java/me/DaWHeL/infected/loot/ChestRegion.java`: normalized inclusive cuboid and chunk enumeration.
- `src/main/java/me/DaWHeL/infected/loot/ItemSnapshotCodec.java`: exact Base64 binary item codec.
- `src/main/java/me/DaWHeL/infected/loot/WeaponLootCatalog.java`: immutable settings, gun, ammo, and grenade records.
- `src/main/java/me/DaWHeL/infected/loot/WeaponLootRepository.java`: `weapon-loot.yml` load/validate/atomic save and catalog edits.
- `src/main/java/me/DaWHeL/infected/loot/ChestLootGenerator.java`: deterministic weighted selection and all-or-nothing slot planning.
- `src/main/java/me/DaWHeL/infected/loot/DiscoveredChest.java`: canonical identity plus Bukkit inventory.
- `src/main/java/me/DaWHeL/infected/loot/ChestDiscoveryService.java`: tile-entity discovery and double-chest deduplication.
- `src/main/java/me/DaWHeL/infected/loot/WeaponChestService.java`: action validation, discovery, planning, fill, and clear orchestration.
- `src/main/java/me/DaWHeL/infected/loot/WeaponSelectionListener.java`: tagged wand and point selection.
- `src/main/java/me/DaWHeL/infected/gui/weapon/WeaponMenuHolder.java`: typed weapon-menu context and stable entry IDs.
- `src/main/java/me/DaWHeL/infected/gui/weapon/WeaponLootGuiManager.java`: wizard/catalog/editor rendering and click routing.
- Existing GUI/plugin files: add the main chest icon, protected-menu delegation, wiring, reload, and listener registration.
- Matching tests under `src/test/java/me/DaWHeL/infected/loot` and `src/test/java/me/DaWHeL/infected/gui/weapon`.

---

### Task 1: Cuboid and Rarity Domain

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/loot/LootRarity.java`
- Create: `src/main/java/me/DaWHeL/infected/loot/BlockPoint.java`
- Create: `src/main/java/me/DaWHeL/infected/loot/ChestRegion.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/ChestRegionTest.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/LootRarityTest.java`

**Interfaces:**
- Produces: `ChestRegion.between(BlockPoint, BlockPoint)`, `contains(int,int,int)`, `volume()`, `chunkKeys()`, and `LootRarity.weight()/next()/previous()`.

- [ ] Write failing tests proving inclusive normalization, cross-world rejection, negative-coordinate chunk math, overflow-safe volume, and exact rarity weights `100/50/20/8/2`.
- [ ] Run `mvn.cmd "-Dtest=ChestRegionTest,LootRarityTest" test`; expect missing-type compilation failures.
- [ ] Implement immutable records and an enum. The region factory must enforce matching nonblank world names and compute bounds with `Math.min/Math.max`; `volume()` must use `Math.multiplyExact` on `long` values.

```java
public record BlockPoint(String world, int x, int y, int z) {}

public record ChunkKey(int x, int z) {}

public enum LootRarity {
    COMMON(100), UNCOMMON(50), RARE(20), EPIC(8), LEGENDARY(2);
}
```

- [ ] Rerun the targeted tests and inspect `git diff --check`.

### Task 2: Exact Item Snapshots and Persistent Catalog

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/loot/ItemSnapshotCodec.java`
- Create: `src/main/java/me/DaWHeL/infected/loot/WeaponLootCatalog.java`
- Create: `src/main/java/me/DaWHeL/infected/loot/WeaponLootRepository.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/ItemSnapshotCodecTest.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/WeaponLootRepositoryTest.java`

**Interfaces:**
- Consumes: `BlockPoint`, `ChestRegion`, `LootRarity`.
- Produces: immutable `Settings`, `GunEntry`, `GrenadeEntry`, `WeaponLootCatalog`; repository getters and edit methods `setPoint`, `addGun`, `replaceGun`, `setGunAmmo`, `updateGun`, `removeGun`, equivalent grenade operations, and `reload()`.

- [ ] Write failing codec tests using a real `ItemStack` with amount, name/lore, custom model data, enchantment, and `PersistentDataContainer`; assert `isSimilar`, amount equality, and PDC value after Base64 round-trip.
- [ ] Write failing repository tests using `@TempDir` for defaults, stable UUID keys, region/settings persistence, malformed Base64 reporting, missing-ammo invalidation, immutable snapshots, and save/reload round-trip.
- [ ] Run `mvn.cmd "-Dtest=ItemSnapshotCodecTest,WeaponLootRepositoryTest" test`; expect missing types.
- [ ] Implement the codec exactly as below, rejecting null/air items and cloning decoded results.

```java
String encode(ItemStack item) {
    return Base64.getEncoder().encodeToString(item.clone().serializeAsBytes());
}

ItemStack decode(String payload) {
    return ItemStack.deserializeBytes(Base64.getDecoder().decode(payload)).clone();
}
```

- [ ] Implement `WeaponLootCatalog` with defensive `ItemStack` clones on construction and access; clamp chances only at the GUI boundary and reject invalid persisted values during validation.
- [ ] Implement `WeaponLootRepository` over a dedicated `YamlConfiguration`. Save to `weapon-loot.yml.tmp`, then replace the destination with `Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)` and a non-atomic replacement fallback only when atomic moves are unsupported.
- [ ] Rerun targeted tests and `git diff --check`.

### Task 3: Weighted Loot and Capacity Planning

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/loot/ChestLootGenerator.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/ChestLootGeneratorTest.java`

**Interfaces:**
- Consumes: `WeaponLootCatalog` and injected `RandomGenerator`.
- Produces: `PlanResult plan(WeaponLootCatalog catalog, int inventorySize)` containing either exact slot-to-item contents or validation errors.

- [ ] Write failing deterministic tests for one guaranteed gun, guaranteed linked ammo, zero/100% second-gun chance, distinct second selection, two-gun cap, weighted boundary selection, zero/100% grenade chance, quantity ranges, stack splitting, randomized slots, and failure when planned stacks exceed 27 slots.
- [ ] Run `mvn.cmd "-Dtest=ChestLootGeneratorTest" test`; expect missing type failures.
- [ ] Implement cumulative-weight selection with `nextLong(totalWeight)`, remove the first gun from second-gun candidates, and roll inclusive integer ranges.
- [ ] Consolidate total ammo bundle quantities and grenade quantities into the fewest legal stacks without changing captured metadata. Shuffle available slot indexes with the injected randomness and return a complete plan without mutating an inventory.
- [ ] Rerun targeted tests and `git diff --check`.

### Task 4: Efficient Chest Discovery

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/loot/DiscoveredChest.java`
- Create: `src/main/java/me/DaWHeL/infected/loot/ChestDiscoveryService.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/ChestDiscoveryServiceTest.java`

**Interfaces:**
- Consumes: `ChestRegion`.
- Produces: whole-region discovery for already-loaded chunks plus per-chunk discovery used by the bounded pregenerated-chunk operation.

- [ ] Write failing Mockito tests for missing world, unloaded intersecting chunks, out-of-region tile entities, normal chest inclusion, trapped chest inclusion, barrel exclusion, and two chest halves mapping to one canonical double-chest identity.
- [ ] Run `mvn.cmd "-Dtest=ChestDiscoveryServiceTest" test`; expect missing types.
- [ ] Implement chunk enumeration through `World.isChunkLoaded(x,z)` and `World.getChunkAt(x,z).getTileEntities()`. Filter `Chest` block states inside the inclusive bounds.
- [ ] Canonicalize double chests from both holder block locations sorted lexicographically; canonicalize singles from their one block location. Deduplicate before returning inventories.
- [ ] Rerun targeted tests and `git diff --check`.

### Task 5: Validated Fill and Complete Clear

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/loot/WeaponChestService.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/WeaponChestServiceTest.java`

**Interfaces:**
- Consumes: `GameManager`, repository snapshot, discovery service, and generator.
- Produces: static `ActionPreview` validation, synchronous compatibility actions, and scheduled bounded execution with structured completion results.

- [ ] Write failing tests for `LOBBY` gating, missing points, cross-world/over-5,000-chunk regions, large block volumes, invalid catalog, missing ammo, no chests, loaded-chunk failures, concurrent-operation rejection, preplanning every inventory before mutation, fresh clearing, complete clear, and affected counts.
- [ ] Run `mvn.cmd "-Dtest=WeaponChestServiceTest" test`; expect missing type failures.
- [ ] Implement a single validation pipeline. `clear()` skips gun-catalog validation; `fill()` requires at least one gun and valid ammo on every gun.
- [ ] Plan every discovered inventory first. Only when every plan succeeds, call `Inventory.clear()` and apply cloned planned stacks. Guard the action with `AtomicBoolean.compareAndSet(false, true)` and release it in `finally`.
- [ ] Rerun targeted tests and `git diff --check`.

### Task 6: Tagged Selection Wand

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/loot/WeaponSelectionListener.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/WeaponSelectionListenerTest.java`

**Interfaces:**
- Consumes: plugin namespaced key and repository `setPoint(int, BlockPoint)`.
- Produces: `ItemStack createWand()` and Bukkit `PlayerInteractEvent` behavior.

- [ ] Write failing tests for exact PDC tagging, permission rejection, left-click point 1, right-click point 2, event cancellation, non-wand pass-through, air-click pass-through, and cross-world selection reset/message behavior.
- [ ] Run `mvn.cmd "-Dtest=WeaponSelectionListenerTest" test`; expect missing type failures.
- [ ] Implement a blaze-rod wand tagged with `PersistentDataType.BYTE`; inspect the tag, not item name/lore. Copy it into the first available player slot without consuming or replacing unrelated items.
- [ ] Map left block clicks to point 1 and right block clicks to point 2. Cancel only recognized wand block interactions and save immediately through the repository.
- [ ] Rerun targeted tests and `git diff --check`.

### Task 7: Weapon Wizard, Catalogs, and Editors

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/gui/weapon/WeaponMenuHolder.java`
- Create: `src/main/java/me/DaWHeL/infected/gui/weapon/WeaponLootGuiManager.java`
- Create: `src/main/java/me/DaWHeL/infected/gui/weapon/WeaponLootGuiListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminGuiManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminGuiLayout.java`
- Test: `src/test/java/me/DaWHeL/infected/gui/weapon/WeaponLootGuiManagerTest.java`
- Test: `src/test/java/me/DaWHeL/infected/gui/weapon/WeaponLootGuiListenerTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/gui/AdminGuiLayoutTest.java`

**Interfaces:**
- Consumes: repository editing API, `WeaponChestService`, selection listener, and `AdminGuiNavigator.openMain`.
- Produces: wizard/catalog/editor/confirmation navigation and safe typed-menu click handling.

- [ ] Write failing layout and routing tests for main slot 31, wizard entry, region/wand/category/settings/action controls, paginated stable UUID targets, cursor-drop copy without consumption, invalid drop rejection, rarity/range edits, linked-ammo drops, grenade edits, delete confirmations, and stale target rejection.
- [ ] Write failing listener tests matching existing admin protections: cancel top and bottom transfer, drag, hotbar swap, collect, offhand, unsupported clicks, and unauthorized use.
- [ ] Run `mvn.cmd "-Dtest=AdminGuiLayoutTest,WeaponLootGuiManagerTest,WeaponLootGuiListenerTest" test`; expect failures.
- [ ] Add `AdminGuiLayout.RANDOM_WEAPON_CHESTS = 31`, render a `CHEST` icon, and delegate that click to `WeaponLootGuiManager.openWizard(player)`.
- [ ] Implement the 54-slot typed wizard, gun/grenade catalogs, entry editors, chance controls, confirmations, Back/Close/Help, and stable UUID holder mappings. Use exact cloned catalog items as icons and append instructions to cloned lore only; never modify stored templates.
- [ ] Implement the dedicated listener so weapon menus receive the same safety boundary as existing admin menus.
- [ ] Rerun targeted tests and `git diff --check`.

### Task 8: Plugin Wiring, Reload, and End-to-End Regression

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/InfectedPlugin.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminGuiManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminEventActions.java`
- Modify: `src/main/resources/config.yml`
- Test: `src/test/java/me/DaWHeL/infected/gui/PluginMetadataTest.java`
- Create: `docs/testing/random-weapon-chests-live-checklist.md`

**Interfaces:**
- Consumes: all earlier tasks.
- Produces: enabled plugin feature, existing Reload integration, safety defaults, and runtime QA checklist.

- [ ] Write failing integration-focused tests verifying plugin wiring contracts, repository reload from the existing Reload action, safety defaults, and no new external gun dependency.
- [ ] Run the targeted tests and confirm the intended failures.
- [ ] Construct the codec, repository, discovery service, generator, action service, selection listener, and weapon GUI once in `onEnable`; register both new listeners and inject the weapon GUI entry point into the admin GUI.
- [ ] Add documented safety defaults under `weapon-loot` only if they belong in normal config; keep binary entries and region data in generated `weapon-loot.yml`.
- [ ] Make existing Reload Config reload both standard config and the weapon repository, report loot-file validation errors, and redraw safely.
- [ ] Write a live checklist covering wand selection, normal/trapped/double chests, custom gun NBT/PDC survival, guaranteed linked ammo, second-gun frequency/cap, grenades, fresh fill, destructive clear, unloaded chunks, running-round rejection, and two-admin contention.
- [ ] Run `mvn.cmd clean package`; require zero failures/errors and a new plugin JAR.
- [ ] Run `git diff --check`, inspect `git status --short`, and confirm `Bugs To fix.md` remains untouched and no generated `target/` files are included in the intended source diff.
