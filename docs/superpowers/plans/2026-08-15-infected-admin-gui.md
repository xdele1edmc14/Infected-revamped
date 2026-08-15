# Infected Admin GUI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a permission-safe, inventory-based Infected event control desk while leaving all existing gameplay and legacy command behavior unchanged.

**Architecture:** A config-only setup service exposes immutable setup data and narrowly scoped mutations. A typed holder identifies every plugin menu, while an admin GUI manager renders screens and routes validated actions; a small listener owns inventory safety and a root command owns `/infected` plus its config-only add-point helper.

**Tech Stack:** Java 21, Paper API 1.21.4, Bukkit inventories, Maven, JUnit Jupiter 5, Mockito 5.

## Global Constraints

- Do not change gameplay, rounds, infection, guns, finite lives, respawns, inventory handling, or balancing.
- Do not modify arena blocks from any GUI action or `/infected gui addteleport`.
- Preserve all existing commands and their behavior.
- Require `infected.admin` when opening or using the GUI.
- Identify menus by custom `InventoryHolder`, never by title alone.
- Require confirmation for start, stop, infected-spawn clearing, and teleport deletion.
- Build with `mvn.cmd clean package`.

---

### Task 1: Test Foundation and Config-Only Setup Service

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/me/DaWHeL/infected/gui/AdminSetupService.java`
- Test: `src/test/java/me/DaWHeL/infected/gui/AdminSetupServiceTest.java`

**Interfaces:**
- Produces: `AdminSetupService(InfectedPlugin plugin)`
- Produces: `Optional<StoredLocation> infectedSpawn()`
- Produces: `List<TeleportPoint> teleportPoints()` sorted by name, case-insensitively
- Produces: `SetupSnapshot snapshot(int survivors, int infected)`
- Produces: `void setInfectedSpawn(Location location)`, `boolean clearInfectedSpawn()`, `void saveTeleportPoint(String name, Location location)`, and `boolean deleteTeleportPoint(String name)`
- Produces immutable nested records `StoredLocation`, `TeleportPoint`, and `SetupSnapshot`; `SetupSnapshot.ready()` is true only when the spawn exists and at least one point exists.

- [ ] **Step 1: Add JUnit Jupiter, Mockito, and Surefire test dependencies**

Add JUnit `5.11.4`, Mockito `5.14.2`, and Maven Surefire `3.5.2` without changing the Paper dependency or Java version.

- [ ] **Step 2: Write failing config-service tests**

Use Mockito for the plugin and `YamlConfiguration` as the real configuration object. Cover exact-location spawn storage, config-only teleport storage and deletion, stable point ordering, readiness, missing configuration sections, and `saveConfig()` calls.

```java
@Test
void savesTeleportPointWithoutCallingLegacyTeleportManager() {
    Location location = mockLocation("arena", 12.75, 64.0, -4.25, 90f, 5f);

    service.saveTeleportPoint("Alpha", location);

    assertEquals("arena", config.getString("teleports.Alpha.world"));
    assertEquals(12.75, config.getDouble("teleports.Alpha.x"));
    verify(plugin).saveConfig();
    verifyNoMoreInteractions(plugin);
}
```

- [ ] **Step 3: Run the focused test and confirm RED**

Run: `mvn.cmd -Dtest=AdminSetupServiceTest test`

Expected: compilation failure because `AdminSetupService` does not exist.

- [ ] **Step 4: Implement the minimal setup service**

Read and write only `infected-spawn` and `teleports.<name>` keys. Reject blank point names and names containing `.` so one GUI command cannot create nested or ambiguous config paths. Store full doubles plus yaw and pitch for new GUI-created points; continue reading old integer-only points correctly through Bukkit's numeric getters.

- [ ] **Step 5: Run the focused test and confirm GREEN**

Run: `mvn.cmd -Dtest=AdminSetupServiceTest test`

Expected: all `AdminSetupServiceTest` tests pass.

---

### Task 2: Typed Menu Context and Pure GUI Policy

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/gui/AdminMenuHolder.java`
- Create: `src/main/java/me/DaWHeL/infected/gui/AdminGuiPolicy.java`
- Test: `src/test/java/me/DaWHeL/infected/gui/AdminGuiPolicyTest.java`

**Interfaces:**
- Produces enum `AdminMenuHolder.MenuType` with `MAIN`, `LIVE_STATUS`, `TELEPORT_POINTS`, `SETUP_STATUS`, `PLAYERS`, `PLAYER_ACTIONS`, `HELP`, and `CONFIRMATION`.
- Produces enum `AdminMenuHolder.ConfirmationAction` with `START`, `STOP`, `CLEAR_INFECTED_SPAWN`, and `DELETE_TELEPORT_POINT`.
- Produces factories for root menus, paged menus, selected-player menus, and confirmation menus.
- Produces `AdminGuiPolicy.canStart(SetupSnapshot snapshot, boolean running)`, `pageCount(int itemCount, int pageSize)`, `clampPage(...)`, and `isTopInventoryClick(int rawSlot, int topSize)`.

- [ ] **Step 1: Write failing policy tests**

Cover blocked start while running, blocked start while incomplete, page clamping for empty and multi-page lists, and top-inventory slot boundaries. Assert holder factories retain type, page, target, and confirmation action without referring to inventory titles.

- [ ] **Step 2: Run the focused tests and confirm RED**

Run: `mvn.cmd -Dtest=AdminGuiPolicyTest test`

Expected: compilation failure because holder and policy types do not exist.

- [ ] **Step 3: Implement the immutable holder and policy**

The holder keeps its created `Inventory` reference through a package-private setter used once by the renderer, and `getInventory()` returns that inventory. Context fields are immutable; only the inventory binding is deferred because Bukkit requires a holder before inventory creation.

- [ ] **Step 4: Run the focused tests and confirm GREEN**

Run: `mvn.cmd -Dtest=AdminGuiPolicyTest test`

Expected: all policy tests pass.

---

### Task 3: Root Command and Completion

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/gui/InfectedAdminCommand.java`
- Test: `src/test/java/me/DaWHeL/infected/gui/InfectedAdminCommandTest.java`

**Interfaces:**
- Consumes: `AdminGuiManager.openMain(Player)` and `AdminSetupService.saveTeleportPoint(String, Location)`.
- Produces: one `CommandExecutor` and `TabCompleter` for `/infected`.
- Root player behavior: permission check then open menu.
- Console behavior: status, survivor/infected counts, readiness, and `/infected` help.
- Nested behavior: `/infected gui addteleport <name>` for players only, with config-only storage and a refreshed teleport menu.

- [ ] **Step 1: Write failing command tests**

Use Mockito command senders and players. Verify unauthorized players never open a menu, console output contains status and counts, valid root use opens the main menu, invalid point names are rejected, config-only save is called for valid names, and completion suggests `gui`, `addteleport`, and online-safe point-name input positions only.

- [ ] **Step 2: Run focused command tests and confirm RED**

Run: `mvn.cmd -Dtest=InfectedAdminCommandTest test`

Expected: compilation failure because `InfectedAdminCommand` does not exist.

- [ ] **Step 3: Implement command routing and completion**

Keep output concise and consistently use “Infected.” Return `true` for every handled path and provide exact usage only for malformed GUI subcommands.

- [ ] **Step 4: Run focused command tests and confirm GREEN**

Run: `mvn.cmd -Dtest=InfectedAdminCommandTest test`

Expected: all command tests pass.

---

### Task 4: Menu Rendering, Navigation, Confirmations, and Inventory Safety

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/gui/AdminGuiItems.java`
- Create: `src/main/java/me/DaWHeL/infected/gui/AdminGuiManager.java`
- Create: `src/main/java/me/DaWHeL/infected/gui/AdminGuiListener.java`
- Test: `src/test/java/me/DaWHeL/infected/gui/AdminGuiListenerTest.java`

**Interfaces:**
- Produces: `openMain`, `openLiveStatus`, `openTeleportPoints`, `openSetupStatus`, `openPlayers`, `openPlayerActions`, `openHelp`, and confirmation-opening methods.
- Produces: `handleClick(Player, AdminMenuHolder, int rawSlot, ClickType click)`.
- Listener contract: when the top inventory holder is `AdminMenuHolder`, always cancel click and drag events; delegate only valid top-inventory clicks by still-authorized players.

- [ ] **Step 1: Write failing listener safety tests**

Mock the inventory view, top inventory, holder, click event, and drag event. Verify plugin-menu interactions are always cancelled, bottom-inventory shift-clicks are cancelled without action dispatch, top clicks delegate only for authorized players, and unrelated inventories remain untouched.

- [ ] **Step 2: Run listener tests and confirm RED**

Run: `mvn.cmd -Dtest=AdminGuiListenerTest test`

Expected: compilation failure because the listener does not exist.

- [ ] **Step 3: Implement listener safety boundary**

Cancel before reading the clicked item. Ignore `InventoryAction.NOTHING`; never move GUI items. Recheck `infected.admin`; close the view and send a red denial if permission was lost.

- [ ] **Step 4: Implement shared rendering helpers**

Use `ItemStack`/`ItemMeta` helpers for display names, lore, colors, player heads, border panes, and footer buttons. Hide irrelevant item flags and use UTF-8-safe Java source text.

- [ ] **Step 5: Implement menu screens and click routing**

Use the exact requested main and confirmation slots. Use content slots 9-44 excluding footer/navigation positions for paged point and player entries. Re-read manager/config state every time a screen opens. Store point names or player UUIDs in holder context rather than parsing display names.

Start confirmation calls the existing `StartGame` executor so survivor and infected teleport behavior remains centralized. Stop confirmation calls `gameManager.stopGame()`. Reload calls `plugin.reloadConfig()`. Toggle calls `gameManager.toggleZombie(target)`. Remove Player remains a disabled warning item.

- [ ] **Step 6: Run all tests and confirm GREEN**

Run: `mvn.cmd test`

Expected: all setup, policy, command, and listener tests pass.

---

### Task 5: Plugin Registration and End-to-End Build

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/InfectedPlugin.java`
- Modify: `src/main/resources/plugin.yml`
- Test: all tests under `src/test/java`

**Interfaces:**
- Registers one shared `AdminSetupService`, `AdminGuiManager`, `AdminGuiListener`, and `InfectedAdminCommand` after the existing managers are initialized.
- Declares `/infected` with usage `/infected [gui addteleport <name>]` and `infected.admin` while leaving every legacy command declaration intact.

- [ ] **Step 1: Write a failing metadata assertion**

Add a test that loads `plugin.yml` as a resource string and asserts the `infected:` command, permission, and usage exist while representative legacy commands remain present.

- [ ] **Step 2: Run the metadata test and confirm RED**

Run: `mvn.cmd -Dtest=PluginMetadataTest test`

Expected: failure because `infected:` is absent.

- [ ] **Step 3: Register the command, completer, and listener**

Fail fast with `Objects.requireNonNull(getCommand("infected"))`. Reuse the same command instance as executor and tab completer. Do not reorder or remove legacy registrations.

- [ ] **Step 4: Run the full clean build**

Run: `mvn.cmd clean package`

Expected: `BUILD SUCCESS`, all tests pass, and the shaded plugin JAR is created under `target/`.

- [ ] **Step 5: Run source and diff checks**

Run: `rg -n "Â|â€|Ã" src pom.xml`

Expected: no broken-encoding sequences in the new GUI source or metadata. Existing unrelated mojibake is reported separately if found.

Run: `git diff --check`

Expected: no whitespace errors.

- [ ] **Step 6: Review scope and changed files**

Confirm the diff contains only GUI infrastructure, the root command, test/build configuration, plugin registration, metadata, and design/plan documentation. Preserve the pre-existing modified Maven status file and untracked `Bugs To fix.md`.
