# Round Lifecycle, Combat, and Spawn Separation Design

**Date:** 2026-08-16

**Status:** Approved for implementation planning

## Goal

Make round starts, head starts, active combat, and cleanup deterministic while completely preventing attributable survivor friendly fire, keeping infection melee-only, validating setup before state changes, and separating survivor, infected-release, and infected-respawn arena spawns.

## Scope

This change covers:

- explicit round phases;
- start validation;
- survivor friendly-fire prevention for direct and indirect attackers;
- melee-only infection during active play;
- reliable survivor head start and initial-infected containment;
- role-specific arena spawn groups with backward-compatible migration;
- round-owned teleport and delay tasks;
- idempotent participant cleanup and duplicate-free lobby reconstruction; and
- the existing admin GUI and commands needed to configure and report the new spawn groups.

This remains a small, memory-only event plugin. It will not add a database, restart recovery, inventory snapshots, unrelated-server isolation, or a new gun system. Existing generated `target/` changes and the untracked `Bugs To fix.md` file are outside this change and must be preserved.

## Recommended Architecture

Use a focused round-controller retrofit. `GameManager` remains the public gameplay facade, but it becomes the sole owner of phase transitions, participant membership, round task cancellation, start orchestration, and cleanup orchestration. Small pure-Java collaborators handle validation, combat decisions, spawn roles, batch progression, and legal phase changes so behavior can be tested without a running Paper server.

The alternatives were rejected for these reasons:

- Patching the current callbacks in place would retain split ownership between `StartGame`, `GameManager`, `TeleportManager`, and listeners, leaving stale callbacks and roster races possible.
- Replacing every role object and public list with a new immutable session model would be cleaner long-term, but it would expand this fix into a broad rewrite of commands, the scoreboard, and the admin GUI.

## Round Phases

Add `RoundPhase` with exactly these values:

- `LOBBY`: players may be registered as lobby survivors; no round task is active.
- `COUNTDOWN`: the validated participant snapshot and initial teams are fixed, infected are moved to the holding spawn, and survivors are teleported to survivor arena spawns.
- `HEADSTART`: every survivor arena teleport succeeded, initial infected remain contained, infection is disabled, and the configured release delay is running.
- `ACTIVE`: every infected release teleport succeeded; direct infected melee hits may infect survivors and win checks are enabled.
- `ENDING`: start, teleport, release, and combat actions are blocked while each participant is reset once and lobby membership is rebuilt.

`GameManager.isGameRunning()` remains for compatibility and returns `true` for `COUNTDOWN`, `HEADSTART`, `ACTIVE`, and `ENDING`. New code uses `getPhase()` or an exact phase predicate instead of treating all non-lobby phases as equivalent.

Legal automatic transitions are:

```text
LOBBY -> COUNTDOWN -> HEADSTART -> ACTIVE -> ENDING -> LOBBY
                     \                         ^
                      +---- failure/stop ------+

COUNTDOWN -------------------------------------> ENDING
```

An administrator may stop from `COUNTDOWN`, `HEADSTART`, or `ACTIVE`. A start or teleport failure also enters `ENDING`. Repeated stop requests during `ENDING` do not start another cleanup.

## Start Validation and Start Flow

Both `/startinfected` and the admin GUI call one `GameManager` start entry point and receive a structured result. Commands do not set phases, select teams, schedule release, or broadcast an independent second start message.

Validation runs while the phase is still `LOBBY` and must pass before any roster, inventory, position, or phase mutation. It rejects the start with actionable reasons unless all conditions hold:

- the infected holding spawn exists and its world is loaded;
- at least one valid, loaded survivor arena spawn exists;
- at least one valid, loaded infected-release arena spawn exists;
- at least one valid, loaded infected-respawn arena spawn exists;
- at least two online lobby participants exist;
- `settings.starting-zombies` is at least `1`;
- `settings.starting-zombies` is strictly less than the participant count;
- `settings.teleport-batch-size` is at least `1`;
- `settings.teleport-delay` is not negative; and
- `settings.infected-teleport-delay` is not negative.

After validation:

1. `GameManager` snapshots participants by UUID, transitions to `COUNTDOWN`, clears prior round-life state, and selects the configured number of initial infected.
2. Every initial infected is moved to the holding spawn and marked contained.
3. Survivors are teleported in batches using only survivor spawns.
4. A batch result succeeds only if every still-online queued survivor returns `true` from `Player.teleport`. Empty or unavailable spawn lists are failures, never silent early returns.
5. If survivor teleporting succeeds, the phase becomes `HEADSTART` and the configured delay begins. If it fails, the round enters cleanup.
6. When the delay ends and the same round is still in `HEADSTART`, infected are teleported in batches using only infected-release spawns.
7. The phase becomes `ACTIVE` only after every still-online initial infected release teleport succeeds. Failure enters cleanup.

Every scheduled callback captures the current round identity. A callback whose round is no longer current performs no mutation. All round-owned tasks are cancelled when `ENDING` begins.

## Head-Start Containment

Initial infected remain at the configured holding position through `COUNTDOWN` and `HEADSTART`. A movement listener blocks block-to-block position changes for contained infected while still allowing them to rotate their view. Teleports owned by the round controller temporarily bypass containment.

During `COUNTDOWN` and `HEADSTART`:

- infection is impossible;
- damage caused by an infected participant is cancelled, including direct melee, projectiles, and other attributable indirect sources; and
- survivor friendly fire remains cancelled.

Containment is removed only as each infected player is released by the round-owned release batch. If release fails, cleanup removes containment without activating combat.

## Combat Attribution and Decisions

Replace the split `FriendlyFireListener` and `HitHandler` decision paths with one participant-damage listener backed by pure resolver and policy classes.

For a damaged player, the responsible attacking player is resolved in this order:

1. a direct `Player` damager;
2. a projectile whose `ProjectileSource` is a `Player`;
3. Paper's `DamageSource.getCausingEntity()`;
4. Paper's `DamageSource.getDirectEntity()`; and
5. supported Bukkit-owned indirect entities whose source or owner can be resolved to a `Player`.

Resolution is recursive with an identity-based visited set so nested or cyclic indirect sources cannot loop. An event with no attributable player is not guessed to belong to a participant.

Combat policy is:

- survivor-to-survivor damage is always cancelled when both players are current participants, regardless of phase or directness;
- infected-to-infected damage remains cancelled;
- damage caused by infected is cancelled outside `ACTIVE`;
- indirect damage caused by infected is cancelled during `ACTIVE`, because zombies are melee-only;
- a survivor may damage an infected during `ACTIVE`, including with GamingBarns Guns when Paper exposes the shooter through the standard event or damage-source chain; and
- infection occurs only for a non-cancelled `EntityDamageByEntityEvent` whose direct damager is the infected `Player`, whose victim is a survivor, and whose phase is `ACTIVE`.

Resolving a gun shooter for friendly-fire cancellation never makes that gun eligible to infect a player. If GamingBarns emits damage with neither a player-caused entity nor a player-owned Bukkit/Paper damage source, the event does not contain enough attribution to identify a shooter; the implementation will cover every attribution path exposed by the server API and include a focused runtime verification checklist for the installed gun build.

## Role-Specific Spawn Storage and Migration

Keep the existing single `infected-spawn` section as the pre-release holding spawn. Add independent named arena point groups:

```yaml
spawns:
  survivor:
    point-name: { world, x, y, z, yaw, pitch }
  infected-release:
    point-name: { world, x, y, z, yaw, pitch }
  infected-respawn:
    point-name: { world, x, y, z, yaw, pitch }
```

On enable, a one-time migration checks `migrations.role-spawns`. When the marker is absent, each valid legacy `teleports.<name>` point is copied independently into all three role groups without deleting or changing `teleports`. The marker is saved only after the copy completes. Existing role-group entries win on name collisions so migration cannot overwrite newer explicit configuration.

After migration, gameplay reads only role-specific groups:

- survivors use `spawns.survivor` during `COUNTDOWN`;
- initial infected use `spawns.infected-release` after `HEADSTART`; and
- infected deaths with remaining lives use `spawns.infected-respawn`.

The existing 36-slot main admin layout remains unchanged. Its Teleport Points submenu first selects Survivor, Infected Release, or Infected Respawn, then lists and manages points for that role. GUI-created points remain config-only and never alter arena blocks. `/infected gui addteleport <name>` remains a survivor-spawn shorthand; `/infected gui addteleport <survivor|release|respawn> <name>` selects a role explicitly. Legacy teleport commands default to survivor spawns so existing command usage remains valid.

The setup snapshot and GUI readiness display separate counts for all three arena roles. Readiness uses the same rules as actual start validation rather than being a weaker UI-only approximation.

## Controlled Cleanup and Lobby Reconstruction

Entering `ENDING` is idempotent:

1. Set the phase to `ENDING` before cancelling or scheduling anything.
2. Cancel every task owned by the current round and invalidate its identity.
3. Snapshot the union of recorded round participants and current survivor/infected rosters into UUID-keyed insertion order.
4. Clear temporary infected life, cooldown, and containment state that must not leak into another round.
5. Reset each online snapshot participant in configured batches. A UUID-level processed set guarantees one reset attempt per cleanup even if the same player appeared in more than one source roster.
6. Resetting player state does not add a `Survivor` or otherwise mutate either roster.
7. After the final reset batch, clear both role rosters once, then rebuild lobby survivors from current online players through an upsert that permits one entry per UUID.
8. Transition to `LOBBY` only after reconstruction completes.

Players who disconnect before their cleanup turn are not re-added from a stale `Player` reference. When they later join during `LOBBY`, the normal join path applies the lobby reset and unique survivor registration once. Players joining during `ENDING` are held out of the role rosters and included in the final online-player reconstruction.

Win checks run only in `ACTIVE`. Announcing a winner starts the same idempotent cleanup path as an administrator stop; it cannot schedule a second reset.

## Compatibility Changes

- `PlayerJoinListener` becomes phase-aware: lobby joins become unique survivors, ending joins wait for lobby reconstruction, and other phase joins retain the plugin's existing infected late-join behavior while being recorded as round participants.
- `PlayerQuitListener` removes membership through `GameManager`, which updates participant state and runs win checks only during `ACTIVE`.
- `InfectedRespawnListener` requires `ACTIVE` and uses only infected-respawn spawns. Eliminated infected still become spectators.
- Existing scoreboard, list, toggle, and GUI consumers may continue reading survivor and infected views, but direct mutation is replaced where it affects lifecycle correctness.
- `GameManager.resetPlayer` is split into state reset and lobby registration so cleanup cannot accidentally rebuild a partial roster from its final batch.
- `onDisable` cancels round-owned tasks and performs immediate in-memory teardown without attempting asynchronous server-shutdown teleports.

## Error Handling and Messages

Start rejection returns all failing setup reasons in stable order so commands and the GUI can show the same result. No start broadcast occurs on rejection.

A teleport batch failure identifies the affected role and begins cleanup. It never advances to the next phase. Stop and teleport failure messages are broadcast once per round. Stale callbacks, repeated stop commands, or repeated winner checks do not duplicate messages or cleanup.

Configuration migration skips malformed legacy points and logs their exact paths. Start validation then reports any role group left without a valid loaded point.

## Test Strategy

All production behavior is developed test-first. Existing tests form a 43-test baseline.

Add focused tests for:

- every legal and illegal phase transition;
- compatibility behavior of `isGameRunning()`;
- start validation for missing holding spawn, each missing role group, unloaded worlds, invalid starting-infected counts, insufficient players, and invalid batch/delay values;
- no phase or roster mutation when validation fails;
- direct-player, projectile-shooter, causing-entity, direct-entity, nested-source, cyclic-source, and unattributable damage resolution;
- survivor friendly-fire cancellation for melee, projectile, and indirect damage;
- infected friendly fire and all infected-caused head-start damage cancellation;
- active direct infected melee infection;
- no infection during other phases, from cancelled hits, or from indirect/gun damage;
- survivor teleport completion before `HEADSTART`;
- release delay and successful release before `ACTIVE`;
- failed survivor/release teleports entering `ENDING`;
- containment blocking position changes but allowing view rotation;
- one-time legacy spawn migration, collision preservation, and old-section retention;
- role-specific spawn selection for survivors, release, and respawn;
- GUI role navigation and both add-teleport command forms;
- duplicate participant entries and more than one cleanup batch restoring each UUID once;
- roster clearing only after the final cleanup batch;
- repeated stop/winner calls producing one cleanup; and
- stale round callbacks producing no state changes.

Verification consists of the focused red-green test cycles, the full Maven test suite, `mvn.cmd clean package`, `git diff --check`, inspection that only intended source/docs files changed, and a live-server checklist for the installed GamingBarns Guns build's damage attribution.

## Acceptance Criteria

The change is accepted when:

- attributable survivor-to-survivor damage cannot reduce health through direct melee, projectiles, or indirect Bukkit/Paper damage sources;
- only a direct infected melee hit during `ACTIVE` can infect a survivor;
- infected cannot perform damaging combat during `HEADSTART` and cannot leave holding containment before release;
- the round cannot leave `LOBBY` with invalid spawns, invalid counts, or insufficient players;
- survivors finish teleporting before the head-start clock begins;
- infected finish their release teleports before the phase becomes `ACTIVE`;
- survivor, release, and respawn paths use independent spawn groups;
- stopping a multi-batch round resets each participant once and produces a unique complete lobby roster;
- stale callbacks cannot release infected or mutate a later round;
- existing GUI layout and config-only GUI point creation remain intact; and
- all automated verification passes with no unintended source or generated-file changes included.
