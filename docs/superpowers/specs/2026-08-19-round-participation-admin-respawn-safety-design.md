# Round Participation, Admin Safety, and Respawn Safety Design

**Date:** 2026-08-19

**Status:** Approved for implementation

## Goal

Make late joins, disconnects, administrative roster changes, and infected respawns obey the existing round phases without creating false wins, duplicate roles, or stranded players.

## Constraints

- Keep the existing `LOBBY -> COUNTDOWN -> HEADSTART -> ACTIVE -> ENDING` lifecycle and release this work as 1.0.3.
- Keep the event memory-only. Do not add databases, restart recovery, or pre-match player snapshots.
- Cleanup continues to wipe/reset players for this isolated single-round event.
- Leave all changes uncommitted unless the user explicitly asks for a commit.

## Participant States and Late Joins

`GameManager` remains the sole owner of round membership. A player is in exactly one effective state: lobby survivor, active survivor, active infected, queued spectator, or non-participant.

Players joining during `COUNTDOWN`, `HEADSTART`, or `ACTIVE` become queued spectators. They are excluded from both team rosters and every win calculation. At final cleanup, online queued spectators are reset and registered once for the next lobby.

During `ACTIVE`, an administrator may use `/togglezombie <player>` to explicitly add a queued spectator as infected. The operation removes queue membership, records the round participant, grants the configured infected lives, applies the infected role, and teleports the player to a safe dedicated infected-respawn point. The operation must find the safe point before mutating membership. If none exists, the round is cancelled as a setup failure.

## Explicit Round Outcomes

Round results depend on both roster counts and the event that changed them:

- infection of the last survivor during `ACTIVE` produces an infected victory;
- final-life elimination of the last infected produces a survivor victory;
- departure or administrative removal of the last infected while survivors remain produces a survivor victory;
- departure or administrative removal of the last survivor produces abandonment cancellation, never an infected victory;
- all participants leaving produces abandonment cancellation;
- either team becoming empty during `COUNTDOWN` or `HEADSTART` cancels the start without a winner; and
- administrator stop, teleport failure, or absence of a safe infected respawn cancels without a winner.

A small pure policy maps phase, resulting roster counts, and roster-change cause to `NONE`, `SURVIVORS_WIN`, `INFECTED_WIN`, or `CANCELLED`. `GameManager` alone applies the conclusion and starts idempotent cleanup.

## Quit and Remove-Player Flow

Quit and administrative removal share one roster-removal path but supply distinct user-facing messages. The manager first captures the player's role, then removes the UUID from survivor, infected, queued, round-participant, containment, teleport-bypass, cooldown, and infected-life state.

`/removeplayer` is valid only during `COUNTDOWN`, `HEADSTART`, and `ACTIVE`. It accepts active participants and queued spectators. An online target is immediately wiped/reset to the normal event exit state, without lobby registration. The resulting roster change is evaluated exactly once.

Pending survivor and infected-release batches recheck a player-supplied eligibility predicate immediately before each teleport. Disconnected or removed players are skipped successfully, preventing an old batch from returning them to the arena.

## Command Phase Policy

- `/togglezombie` is valid only in `ACTIVE`. It may add a queued spectator or switch a current participant. A switch that would empty either active team is rejected with a clear message rather than manufacturing a win.
- `/removeplayer` is valid in `COUNTDOWN`, `HEADSTART`, and `ACTIVE`.
- `/reloadinfected` is valid only in `LOBBY`.
- `/stopinfected` is valid in `COUNTDOWN`, `HEADSTART`, and `ACTIVE`; it reports separate `LOBBY` and `ENDING` rejections.

All command-facing mutations return structured success/failure results and messages. Role assignment removes every conflicting state before adding the new one, preventing duplicate survivor/infected entries.

## Finite Lives and Safe Infected Respawns

Each infected death during `ACTIVE` consumes exactly one configured life. A final death removes the infected from the active roster and produces spectator mode on respawn while the round is still `ACTIVE` or `ENDING`; completed cleanup restores the normal `LOBBY` state. A non-final death remains eligible for infected respawn.

The respawn selector examines all loaded `INFECTED_RESPAWN` points in randomized order and accepts only a point that:

- has a loaded world and lies within its world border;
- lies within the world's build-height range;
- has a non-hazardous collision surface immediately below whose top exactly matches the saved feet height and supports the player's full width;
- has passable, non-hazardous blocks at feet and head height; and
- is not in lava, fire, soul fire, magma, campfire, cactus, berry bush, powder snow, or another explicitly blocked hazard material.

Only a validated dedicated point may be assigned to a living infected. There is no world-spawn fallback. If no configured point is safe at respawn time, the manager broadcasts a setup error and cancels the round.

## Testing

Tests are written and observed failing before production changes. Coverage includes queued late joins, explicit admin admission, every departure outcome, pre-active cancellation, removal without re-registration, stale teleport-batch skipping, command phase gates, duplicate-role prevention, life consumption, final-death spectator handling, safe-ground/headroom/hazard/border checks, choosing a later safe spawn after an unsafe candidate, and cancellation when none are safe.

Final verification runs focused Maven tests, the full suite, `mvn.cmd clean package`, `git diff --check`, and a changed-file review. No live GamingBarns Guns compatibility claim is part of this work.
