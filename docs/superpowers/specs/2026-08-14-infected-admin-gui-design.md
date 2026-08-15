# Infected Admin GUI Design

## Goal

Add a polished, inventory-based control desk for administrators without changing the event's gameplay, round, infection, life, respawn, inventory, or balancing behavior.

## Command Surface

- `/infected` opens the main menu for in-game senders with `infected.admin`.
- Console use prints the current running state, survivor and infected totals, setup summary, and concise command help.
- `/infected gui addteleport <name>` saves the executing player's current location as an existing shared teleport point without modifying blocks.
- Existing commands remain registered and retain their current behavior.
- Every root-command and GUI action checks `infected.admin` at execution time.

## Architecture

`AdminGuiManager` owns menu creation, navigation, and action dispatch. Every inventory uses a typed `InventoryHolder`; titles are presentation only and are never used to identify menus. Small holder types retain only the context needed by their screen, such as a teleport page, selected point name, selected player UUID, or confirmation action.

`AdminGuiListener` is the inventory safety boundary. It cancels clicks, drags, number-key swaps, double-click collection, offhand swaps, and other item-transfer attempts whenever the top inventory has an Infected GUI holder. It rejects stale or unauthorized actions before delegating a valid click to the manager.

`AdminSetupService` provides config-only reads and writes for the infected spawn and named teleport points. GUI add, clear, and delete operations save configuration but never place, replace, or remove world blocks. Legacy teleport commands continue using their existing manager behavior.

Shared item and layout helpers provide consistent names, concise lore, black/gray borders, click instructions, footer controls, and player heads without duplicating rendering logic.

## Menus

### Main Control Desk

The compact 36-slot `Infected Event Control` menu uses zero-based slot indexes. Its primary controls are centered across slots 11-15: status, infected spawn, teleport points, setup status, and players. Event actions use slots 20, 22, and 24 for start, stop, and reload. The footer places the disabled root Back item at 27, Quick Help at 34, and Close at 35. Quick Help is the main menu's only help item; the former duplicate Help/legend footer item is not shown. Empty functional space remains readable instead of being filled with unrelated decorations.

Status colors communicate current state: green for ready/start, yellow for incomplete or caution, red for missing/blocked/destructive actions, and aqua for navigation or information.

### Infected Spawn

The main-menu spawn item shows configuration state, world, and coordinates. Left-click saves the administrator's exact current location. Right-click teleports to a loaded configured world. Shift-right-click opens a 27-slot confirmation menu; confirming removes only the `infected-spawn` config section.

### Teleport Points

The 54-slot point menu lists named entries in stable case-insensitive order and paginates when necessary. Each item shows its name, world, X, Y, and Z. Left-click teleports the administrator to a centered location one block above the stored platform coordinate when the world is loaded. Shift-right-click opens deletion confirmation. Confirmed deletion removes only the config entry and explicitly states that world blocks remain unchanged.

Slot 49 explains `/infected gui addteleport <name>` and closes the menu after printing the command prompt in chat. Slots 45 and 53 provide Back and Close. Previous and next controls use slots 46 and 52 only when relevant.

### Setup Status

The checklist reports infected-spawn presence, teleport-point count, participant totals, starting infected count, infected teleport delay, and teleport batch size. Readiness is a UI aid based only on a configured infected spawn and at least one teleport point. It does not replace or alter game-start validation.

### Player Status

Survivors use green player heads and infected use red player heads. The summary at slot 4 shows both totals. Player entries are sorted by name and may paginate. Clicking a current participant opens an action menu that can invoke the existing team-toggle behavior. Remove Player is displayed as unavailable with a warning because the current removal implementation resets and re-adds survivors; fixing that belongs to gameplay work outside this task.

### Status, Help, and Confirmations

Live Status is read-only and shows the running state and both team totals. Help presents the four-step setup flow and makes clear that the GUI is not a gameplay editor.

Start, stop, spawn clearing, and teleport deletion use 27-slot confirmation menus with Confirm at 11, an exact action description at 13, and Cancel at 15. Confirmation revalidates permission, current event state, and selected config/player data so stale menus fail safely.

## Action Behavior

Start uses the existing start flow only when the UI readiness check passes and the game is not already running. Stop uses the existing stop flow only while a game is running. Reload calls the existing configuration reload operation, reports success in chat, and redraws the current menu.

Missing or unloaded worlds, removed config entries, offline players, changed team membership, and changed event state produce clear red or yellow messages and return the administrator to the safest relevant menu. No GUI failure mutates gameplay state speculatively.

## Testing

Automated tests will cover config-only setup mutations, stable point discovery, readiness reporting, root-command permission/console behavior, holder-based menu identification, protected inventory interactions, slot routing, confirmation requirements, pagination, and stale-state rejection. MockBukkit or narrowly scoped test doubles may be added as test dependencies if compatible with Paper 1.21.4 and Java 21.

The final verification is `mvn.cmd clean package`, supplemented by targeted tests and `git diff --check`. Runtime-only Bukkit behavior that cannot be fully simulated will be called out rather than claimed as live-server verified.

## Scope Guardrails

This work adds no database, persistence, restart recovery, multi-arena model, gameplay redesign, block mutation, or unrelated refactor. It does not change infection rules, guns, finite lives, respawns, inventory handling, or balancing.
