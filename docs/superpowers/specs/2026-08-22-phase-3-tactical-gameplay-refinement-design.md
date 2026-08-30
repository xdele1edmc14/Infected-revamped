# Phase 3 Tactical Gameplay Refinement Design

## Goal

Complete the finite-life infected loop, make `/buffinfected` round-safe and reversible, remove Jump Feather entirely, and add regression coverage for the gameplay and lifecycle guarantees established in Phases 1 and 2.

## Existing foundation

`GameManager` remains the single round mutation boundary. `InfectedLifeTracker` owns round-local remaining lives, `InfectedRespawnSelector` validates dedicated `INFECTED_RESPAWN` locations, `RoundTaskRegistry` owns scheduled work, `PlayerStateSnapshot` restores pre-round state, and `RoundOutcomePolicy` decides team outcomes. Phase 3 extends these existing seams rather than creating a second role, task, or respawn system.

The round remains an in-memory, single-event flow with no database or restart recovery. Zombies remain melee-only. Queued spectators do not belong to either team and do not affect win checks.

## Finite infected deaths and respawns

An infected death consumes one configured life only while the round is `ACTIVE`. Duplicate death notifications, deaths during `HEADSTART` or `ENDING`, and deaths from players who are not active infected do not consume lives.

When lives remain, `InfectedRespawnListener` selects only from dedicated `INFECTED_RESPAWN` locations. A candidate is valid only when its world is loaded, it is inside the world border with player width accounted for, it is inside build height, it has solid full-width support, clear feet and head space, no collision, and no hazardous support or occupancy. The listener sets the event respawn location and restores the infected loadout one tick later only if the same round is still `ACTIVE` and the player is still infected.

When the final life is consumed, the player is removed from the active infected roster, marked as eliminated for the pending respawn event, and placed in spectator mode. Team outcome evaluation then awards survivors if they are the last active team. If no safe dedicated respawn exists for a remaining-life death, the round is cancelled as a safety failure; world spawn is never used as a fallback.

## Infected buff ownership

`/buffinfected` remains an administrator on/off toggle available only during `ACTIVE`. Enabling it applies the buff state to every active infected player, creates at most one tracking task for the round, and gives each infected at most one plugin-owned tracking compass. Repeating enable calls while already enabled do not create another task, duplicate potion effects, or add another owned compass.

The tracking compass is identified with a plugin namespaced persistent-data tag. Tracking updates inspect only active infected players and target only active survivors. Ordinary player-owned compasses are never removed by the plugin.

Disabling the buff cancels and forgets the single tracking task, removes the plugin-owned compass from active infected inventories, removes buff resistance, and restores the normal infected Speed I effect. Newly infected players and infected players respawning with lives remaining receive whichever buff state is current. Round cleanup cancels the task, clears the buff flag and handle, and relies on the existing player snapshots to restore original effects, inventory, armor, and compass target.

## Complete Jump Feather removal

Jump Feather is removed as a feature rather than disabled. The plugin no longer registers `JumpFeatherListener` or `/givefeather`; `GiveFeather`, the listener, the periodic feather distributor, feather cooldown state, feather-only no-fall protection, help text, command metadata, and feather-specific tests are deleted. No production or test source retains a `Material.FEATHER` gameplay check.

Removing this feature does not alter the general combat or fall-damage rules. It only removes behavior that existed to support Jump Feather.

## Automated gameplay coverage

The regression suite will protect behavior at the existing domain boundaries:

- deterministic starting-infected selection and exact configured count;
- head-start protection and melee-only infection rules;
- finite-life consumption, remaining-life respawn, final-life elimination, and safe-spawn rejection;
- infected, survivor, abandonment, cancellation, and time-expiry outcomes;
- join queuing, quit outcomes, and queued-spectator exclusion;
- cleanup cancellation and exact restoration of plugin-owned state;
- survivor and infected friendly-fire cancellation;
- invalid minimum-player, team-count, spawn, and timing setup rejection;
- one buff tracking task, one tagged compass per infected, role-shift updates, disable cleanup, and round cleanup.

Tests assert observable state and outcomes rather than source text or mock existence. Production changes follow red-green-refactor cycles, with focused Maven test runs for each behavior before the full package build.

## Error handling and lifecycle safety

All delayed callbacks retain the existing round/phase guards. A stale respawn, loadout, tracking, or cleanup callback becomes a no-op after phase change or task cancellation. Explicit admin stops and unsafe respawns remain cancellations, never accidental team wins. Cleanup is idempotent and leaves the manager ready for a new round without a buff task, tagged compass, eliminated-player marker, role duplication, feather state, or stale scheduled work.

## Scope

No new weapons, classes, cages, persistence, restart recovery, configurable buff tiers, or world-spawn fallback are added. Version metadata is unchanged. All work stays in the existing `codex/phase-1-lifecycle` worktree and remains uncommitted unless the user explicitly requests a commit.
