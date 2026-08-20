# Phase 1 Round Lifecycle, Cleanup, and State Restoration Design

**Date:** 2026-08-20

**Status:** Approved for implementation planning

## Goal

Establish a leak-free round lifecycle that never changes arena terrain while managing teleport points, affects hunger only for current participants, restores every plugin-owned player mutation, cancels all scheduled round work, and reaches an explicit result for starts, stops, departures, failures, wins, and time expiry.

## Scope

This phase covers:

- config-only `/addteleport` and `/removeteleport` behavior;
- participant-scoped hunger management;
- exact pre-match state capture and restoration;
- centralized ownership and cancellation of round tasks;
- configurable minimum players and start countdown;
- an optional match time limit;
- deliberate abandonment, cancellation, survivor-win, and zombie-win outcomes; and
- quit handling that restores the departing participant before evaluating the resulting roster.

This phase does not replace the existing role-specific spawn system, combat rules, admin GUI, infected-life model, or scoreboard presentation. It changes those areas only where they must integrate with lifecycle ownership and restoration.

The untracked `Bugs To fix.md` file and generated `target/` contents are outside the change and must remain uncommitted.

## Recommended Architecture

Keep `GameManager` as the facade used by commands and listeners, while extracting two focused lifecycle collaborators:

- `PlayerStateSnapshot` is an immutable, defensive copy of every player value the plugin changes. It owns capture and restoration behavior so role classes do not need to know how cleanup works.
- `RoundTaskRegistry` owns every scheduled task associated with a round or its cleanup. It distinguishes gameplay work from cleanup work, providing a gameplay-cancellation boundary when ending begins and an all-work cancellation boundary for plugin disable or defensive cleanup before a new round.

This focused retrofit preserves existing command and listener interfaces while moving lifecycle-sensitive work behind testable boundaries.

The alternatives were rejected for these reasons:

- Adding more fields and cancellation calls directly to the existing `GameManager` would make an already large class harder to reason about and would leave commands and listeners able to schedule unowned work.
- Replacing the current manager with a new `RoundSession` architecture would produce a cleaner long-term model but would require a broad rewrite of commands, the GUI, scoreboard consumers, and existing tests beyond Phase 1.

## Round Phases

The phase flow is:

```text
LOBBY -> COUNTDOWN -> DEPLOYING -> HEADSTART -> ACTIVE -> ENDING -> LOBBY
```

- `LOBBY`: online players may be registered without mutating their state. No match hunger or gameplay task applies.
- `COUNTDOWN`: the eligible roster is locked, pre-match snapshots exist, and the configurable start clock is running. Late joiners wait for the next round.
- `DEPLOYING`: the final roster has passed validation, teams are selected, initial infected are contained, and survivor teleport batches are running.
- `HEADSTART`: survivors finished teleporting and the infected-release delay is running.
- `ACTIVE`: infected release completed, combat outcomes and the optional match time limit apply.
- `ENDING`: gameplay is blocked, gameplay tasks are cancelled, and participants are being restored before lobby reconstruction.

Legal automatic transitions are:

```text
LOBBY -> COUNTDOWN -> DEPLOYING -> HEADSTART -> ACTIVE -> ENDING -> LOBBY
             |            |            |          |
             +------------+------------+----------+---> ENDING on stop, abandonment, or failure
```

Repeated ending requests are idempotent. `isGameRunning()` remains a compatibility predicate for every phase except `LOBBY`; lifecycle-sensitive code uses exact phases.

## Configurable Round Controls

Add these defaults:

```yaml
settings:
  minimum-players: 2
  start-countdown-seconds: 10
  round-time-limit-seconds: 0
```

- `minimum-players` must be at least `2`.
- `start-countdown-seconds` must be zero or greater. Zero starts deployment immediately after the same validation and snapshot steps.
- `round-time-limit-seconds` must be zero or greater. Zero disables the active-round time limit.
- `starting-zombies` must be at least `1` and strictly less than both `minimum-players` and the actual participant count.

The initial start request validates all static configuration and current player-count requirements before entering `COUNTDOWN`. The countdown locks the roster immediately; late joins are not admitted. A departure is allowed while the locked online roster still meets `minimum-players`. Falling below the minimum abandons the round.

At countdown expiry, validation runs again against the remaining locked, online roster and currently loaded spawn configuration. No team selection, teleport, inventory mutation, or role mutation happens unless this final validation succeeds. Countdown messages are sent at start, at 10 seconds when applicable, and for seconds 5 through 1. Cancellation has a distinct message.

When `ACTIVE` begins and the time limit is positive, one tracked expiry task is scheduled. If it fires for the same round while survivors remain, survivors win. Normal active roster checks will already have produced a zombie win if no survivors remain.

## Explicit Round Outcomes

The manager represents conclusions explicitly rather than treating every empty roster or stop as the same event.

| Condition | Phase | Result |
| --- | --- | --- |
| Locked roster falls below `minimum-players` | `COUNTDOWN` | Abandoned; no winner |
| Either selected team becomes empty | `DEPLOYING` or `HEADSTART` | Abandoned; no winner |
| Survivor roster becomes empty, including through quits | `ACTIVE` | Zombies win |
| Infected roster becomes empty, including through quits | `ACTIVE` | Survivors win |
| Both active rosters become empty in one manager operation | `ACTIVE` | Abandoned; no winner |
| Positive match time limit expires with survivors remaining | `ACTIVE` | Survivors win |
| Administrator stops the round | Any non-lobby, non-ending phase | Cancelled; no winner |
| Setup validation, teleport, or safe-respawn requirement fails | Applicable pre-active or active phase | Cancelled; no winner |

Quit and administrative removal use the same roster-conclusion policy after performing player restoration and membership removal. Sequential server events cannot announce a second outcome because the first conclusion enters `ENDING` immediately.

Messages for abandonment, cancellation, survivor wins, and zombie wins are separate configurable entries. Winner titles remain winner-only; abandoned and cancelled rounds never reuse winner presentation.

## Exact Player-State Capture and Restoration

Lobby registration must be state-neutral. Creating a lobby `Survivor` entry must no longer clear a helmet, change glow, recolor a tab name, remove effects, clear inventory contents, or otherwise prepare a player for gameplay.

`PlayerStateSnapshot` is captured exactly once for every player in the locked countdown roster, before the plugin applies a role or other match-owned mutation. Mutable Bukkit values are cloned or copied when captured and again when restored.

The snapshot includes every value this plugin changes during a round:

- location and orientation;
- game mode;
- glowing state;
- complete storage contents, armor contents, off-hand item, and held slot;
- active potion effects, including type, duration, amplifier, ambient, particle, and icon properties;
- player-list name and player-list header/footer formatting exposed by the target Paper API;
- assigned scoreboard;
- compass target; and
- food level, saturation, and exhaustion so the participant-scoped hunger policy cannot leak a changed survival state.

Restoration first removes the plugin's current role/effect/loadout state, then applies the captured values. It returns the player to the captured location if its world is still available. If that world cannot be used, it falls back to the player's current world's spawn rather than failing the rest of restoration.

An online participant who quits is restored synchronously while the quit event still exposes a live player, then removed from snapshots and all round collections. Normal cleanup restores every remaining online participant exactly once by UUID. A successfully restored snapshot is removed immediately, making repeated cleanup safe.

Plugin disable cannot rely on future scheduled work. It cancels tasks first, synchronously restores every online participant with a snapshot, clears in-memory state, and does not schedule teleports or delayed cleanup during shutdown.

## Participant-Scoped Hunger

`HungerListener` receives `GameManager` and cancels a `FoodLevelChangeEvent` only when all of these conditions hold:

- the event entity is a `Player`;
- the manager is outside `LOBBY`; and
- the player's UUID belongs to the locked current-round participant set.

Late joiners, queued spectators, administrators, removed players, restored quitters, and unrelated server players keep normal hunger behavior. Hunger protection ends as soon as restoration removes the participant from the round.

## Non-Destructive Teleport-Point Commands

`/addteleport <name>` records the player's precise world, coordinates, yaw, and pitch through `SpawnRepository` and sends the existing success/failure feedback. It performs no block reads or writes.

`/removeteleport <name>` deletes only the matching survivor-spawn configuration entry and reports whether an entry existed. It does not load a world, calculate a platform area, or replace blocks with air.

These legacy commands remain survivor-spawn shorthands. The existing role-specific admin GUI remains config-only and unchanged in behavior.

## Centralized Scheduled-Work Ownership

All round and cleanup scheduling goes through `GameManager` and is registered in `RoundTaskRegistry`. Commands and listeners do not create independent `BukkitRunnable` instances or call the Bukkit scheduler directly for gameplay work.

Tracked work includes:

- countdown ticks;
- survivor and infected teleport batches;
- infected head-start release;
- active match time expiry;
- infected compass tracking;
- feather spawning;
- feather cooldown expiry and no-fall expiry;
- infected respawn follow-up loadout work;
- delayed listener actions that mutate a participant; and
- batched cleanup.

Every callback captures the current round ID and validates its expected phase and participant membership before mutation. The registry rejects new gameplay work after gameplay cancellation and rejects all new work after all-work cancellation. Self-cancelling completed tasks are removed from the registry so it does not retain dead handles for the life of the plugin.

Entering `ENDING` cancels the registry's gameplay scope before scheduling the cleanup batch in its cleanup scope. A repeated ending request cannot schedule another cleanup. Defensive all-work cancellation occurs before accepting a new start, although legal phase transitions already prevent a new round during cleanup. Plugin disable cancels both scopes and permanently closes the registry.

The plugin-wide scoreboard updater is either moved into the registry for the active lifecycle or replaced with an enable-owned task whose handle is explicitly cancelled on disable and which performs no participant mutation outside a round. No untracked repeating task may remain.

## Cleanup Sequence

All conclusion paths use one cleanup sequence:

1. Transition to `ENDING` and invalidate the active round ID.
2. Cancel all gameplay work in the registry.
3. Snapshot the UUID-unique union of locked participants and current role rosters.
4. Restore online participants in configured bounded batches, with the cleanup task registered and cancellable.
5. Remove each successfully restored snapshot and participant from temporary match collections.
6. Clear role rosters, infected lives, cooldowns, containment, teleport bypass, buff state, and match-owned scoreboard state.
7. Rebuild a state-neutral lobby roster from current online players without applying role cosmetics or inventory mutations.
8. Clear completed cleanup-task handles and transition to `LOBBY`.

If a player disconnects before their cleanup batch, quit handling performs immediate restoration and removes the UUID from the queue's effective eligibility. If a player joins during `ENDING`, they are not added to a role until final lobby reconstruction.

## Compatibility and Integration Changes

- `HungerListener` is constructed with `GameManager`.
- `PlayerJoinListener` registers lobby membership without invoking state-changing role setup.
- `PlayerQuitListener` delegates restoration, roster removal, and outcome evaluation to one manager method.
- `InfectedRespawnListener`, `JumpFeatherListener`, `BuffInfectedCommand`, teleport batches, timers, and cooldowns request tracked scheduling from the manager.
- Role setup applies match-owned cosmetics and loadouts only after the pre-match snapshot exists.
- Existing public survivor/infected views remain available to scoreboards and commands.
- Existing role-specific spawn configuration and legacy migration remain intact.

## Error Handling

Start rejection reports all configuration and setup failures in stable order and leaves the phase, roster, snapshots, inventory, and tasks unchanged.

If snapshot capture fails for any locked participant, the countdown is cancelled before deployment and every already-captured participant is restored. If one property cannot be restored, restoration continues with all remaining properties and logs the player UUID and failed property; one failure cannot leave the rest of a player's state dirty.

Teleport failure, unsafe infected respawn, and stale callback handling retain their existing safe-cancellation behavior. Stale callbacks perform no mutation and cannot conclude or activate a later round.

## Test Strategy

All production changes follow red-green-refactor cycles. Tests use pure collaborators where possible and Mockito only for Paper objects that cannot be constructed directly.

Coverage includes:

- adding and removing teleport points without any world or block access;
- missing and existing teleport-point removal feedback;
- participant, nonparticipant, queued, removed, and lobby hunger events;
- defensive snapshot copies and exact restoration of inventory, armor, effects, glow, tab formatting, scoreboard, location, game mode, compass, and hunger values;
- state-neutral lobby registration;
- immediate quit restoration before team-outcome evaluation;
- countdown start, milestones, zero duration, roster lock, allowed departure, below-minimum abandonment, final revalidation, and stale ticks;
- invalid minimum, countdown, time-limit, and starting-zombie configuration;
- survivor quit producing a zombie win when the active survivor roster reaches zero;
- infected quit producing a survivor win when the active infected roster reaches zero;
- pre-active team loss producing abandonment without winner messaging;
- administrative and failure cancellation without winner messaging;
- disabled time limits, tracked positive limits, stale expiry, and survivor wins on expiry;
- registration and cancellation of teleport, tracking, timer, cooldown, respawn, feather, no-fall, and cleanup tasks;
- stop, abandonment, failure, winner, plugin-disable, and defensive new-start cancellation;
- repeated conclusion and cleanup calls remaining idempotent; and
- full compatibility with the existing automated suite.

Verification runs focused tests for each red-green cycle, the full `mvn.cmd test` suite, `mvn.cmd clean package`, `git diff --check`, and an intended-file review.

## Acceptance Criteria

The phase is accepted when:

- `/addteleport` and `/removeteleport` never read or change arena blocks;
- hunger changes are cancelled only for current locked round participants;
- every player value changed by the plugin is restored exactly after quit, stop, cancellation, abandonment, failure, winner cleanup, and plugin disable;
- no scheduled round callback can mutate state after its round ends or a new round begins;
- minimum players and countdown behavior are configurable and revalidated before deployment;
- a disabled time limit schedules no expiry, while an enabled expiry awards survivors when survivors remain;
- active empty-team outcomes work for quits as well as infections or deaths;
- pre-active abandonment and administrative/failure cancellation never announce a winner;
- cleanup and repeated stop requests are idempotent; and
- all automated verification passes without committing unrelated or generated files.
