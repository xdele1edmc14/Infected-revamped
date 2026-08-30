# Match Experience and Admin Workflow Design

## Scope

This phase upgrades the player-facing round presentation and makes administration usable through either the GUI or commands. It also hardens ownership and cleanup of the infected zombie head. It does not change infection combat, finite-life counts, team selection, or arena terrain.

## Match presentation

`MatchPresentationService` owns titles and sounds but not lifecycle state. `GameManager` calls it only at established phase boundaries.

- During the final three countdown seconds, locked participants hear a configurable rising countdown sound and see the remaining number as a title.
- When countdown reaches zero and deployment begins, participants see a configurable `GET READY` title and hear a deployment sound.
- When infected release succeeds and the phase becomes `ACTIVE`, survivors see `SURVIVE!`, infected see `HUNT!`, and queued spectators see `ROUND STARTED`. Every group receives a configurable subtitle and the active-round sound.
- Presentation is scoped to round participants and queued spectators. It is never broadcast to unrelated online players.
- Missing or invalid sound names are logged and skipped without cancelling the round.
- Titles, subtitles, sounds, volume, pitch, fade-in, stay, and fade-out are configurable with safe defaults.

## Command workflow

`AdminActionService` is the shared action boundary for start, stop, reload, status, and help. Standalone commands and `/infected` subcommands delegate to it, producing the same phase validation and feedback.

Supported forms:

- `/startinfected` and `/infected start`
- `/stopinfected` and `/infected stop`
- `/reloadinfected` and `/infected reload`
- `/infected status`
- `/helpinfected` and `/infected help`
- `/infected` with no arguments continues to open the GUI for players; console receives status and help.

All actions require `infected.admin`. Start, stop, reload, status, and help support console where the underlying action permits it. Tab completion exposes only valid root subcommands.

## Spawn creation workflow

The GUI remains the entry point for normal spawn creation. Each survivor, infected-release, and infected-respawn list has an `Add Current Location` control.

1. Clicking Add closes the inventory and creates a 60-second pending session containing the admin UUID, spawn role, and source page.
2. The admin walks to the desired location and types the spawn name in chat.
3. The chat message is cancelled so it is never broadcast.
4. On the main server thread, the plugin validates `[A-Za-z0-9_-]{1,32}`, rejects the reserved word `close`, rejects duplicates within that role, and saves the admin's current location without modifying blocks.
5. A successful save reopens the same role's spawn list and displays the new point.
6. Invalid or duplicate names keep the session open and explain the correction.
7. Typing `close` cancels the session and reopens the same GUI page.
8. Timeout, quit, plugin disable, configuration reload, or round start cancels the session safely. Timeout tells the admin how to restart creation.

The legacy point commands remain available for backwards compatibility but the GUI no longer tells administrators to run them.

## Infected head ownership and cleanup

`InfectedRoleEquipment` creates zombie heads tagged with a plugin `PersistentDataContainer` key. Initial infection and respawn use this factory instead of creating raw `ZOMBIE_HEAD` items.

- Round cleanup removes only tagged role heads before restoring the pre-match snapshot.
- Living, dead, respawning, quitting, removed, stopped, cancelled, winning, and plugin-disable paths retain snapshot restoration.
- A legitimate zombie head worn before the match is restored because untagged items are never treated as plugin-owned.
- A defensive post-respawn cleanup runs before the saved snapshot is applied.

## Testing

Automated tests cover countdown scoping and pitch, deployment and active role titles, invalid sound handling, command parity and phase restrictions, spawn prompt capture/close/timeout/duplicate behavior, async-to-sync handoff, non-destructive saving, and tagged-head cleanup for living and dead participants. The full Maven package build must remain green and produce `target/Infected-2.0.0.jar`.
