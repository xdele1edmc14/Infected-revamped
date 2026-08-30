# Phase 2 Player Routing, Team Outcomes, and Admin Safety Design

## Goal

Make every join, departure, removal, and administrative action resolve through the round lifecycle without adding duplicate roles, leaving plugin-owned state behind, or producing an accidental winner.

## Existing foundation

Phase 1 already owns player snapshots, round tasks, cleanup, and the `LOBBY -> COUNTDOWN -> DEPLOYING -> HEADSTART -> ACTIVE -> ENDING -> LOBBY` state machine. Phase 2 will keep `GameManager` as the single mutation boundary and will harden the existing implementation instead of introducing a parallel roster or command system.

## Player routing

- `LOBBY`: an online joiner is registered once as a lobby survivor without applying match state.
- `COUNTDOWN`, `DEPLOYING`, `HEADSTART`, or `ACTIVE`: a joiner is snapshotted before mutation, removed from every roster, placed in spectator mode, and queued for the next lobby.
- `ENDING`: a joiner waits unchanged while cleanup reconstructs the lobby.
- A queued spectator is excluded from participants, team counts, hunger management, combat, feathers, and win checks.
- During `ACTIVE`, `/togglezombie <player>` may explicitly admit a queued spectator as infected only after a safe infected-respawn teleport succeeds. A failed admission leaves the player queued and cancels the unsafe round.

## Departure and outcome rules

Roster removal happens before one outcome evaluation. Snapshot restoration happens before winner or abandonment presentation.

- `ACTIVE`, survivors `0`, infected greater than `0`: infected win, including when the final survivor disconnects or is administratively removed.
- `ACTIVE`, infected `0`, survivors greater than `0`: survivors win, including when the final infected disconnects, is removed, or is eliminated.
- Any live phase with both teams empty: abandonment.
- `COUNTDOWN`, `DEPLOYING`, or `HEADSTART` with either required team empty: abandonment.
- Explicit administrator stops and safety failures are cancellations/stops, not team wins.
- A queued spectator leaving changes no team count and cannot conclude the round.

## `/removeplayer`

Removal is allowed in `COUNTDOWN`, `DEPLOYING`, `HEADSTART`, and `ACTIVE`. It is rejected in `LOBBY` and `ENDING`.

The operation removes the target from survivor and infected lists, infected lives, the late-join queue, locked/round participant maps, containment and teleport bypasses, feather cooldowns, and no-fall protection. It then restores the exact captured pre-match state and evaluates a departure outcome once. It must not call lobby registration or create a replacement survivor.

## Administrative phase policy

The valid phases will be explicit on `RoundPhase` so commands and `GameManager` share one policy:

- `/togglezombie`: `ACTIVE` only.
- `/removeplayer`: `COUNTDOWN`, `DEPLOYING`, `HEADSTART`, and `ACTIVE`.
- `/reloadinfected`: `LOBBY` only.
- `/stopinfected`: `COUNTDOWN`, `DEPLOYING`, `HEADSTART`, and `ACTIVE`.

Rejections identify the allowed phase or explain that cleanup is already running. Team toggles cannot remove the final member of either active team. All mutations remain idempotent with respect to UUID-based membership.

## Testing

Tests will cover the complete phase matrix, all running-phase late joins, queued-spectator exclusion, active and pre-active departure outcomes, queued and infected `/removeplayer` behavior, exact snapshot restoration, no immediate survivor re-addition, safe queued admission, failed admission cancellation, and stop/reload phase gates. Production changes will follow failing regression tests.

## Scope

No database, restart recovery, configurable routing modes, or new team-selection command is added. Changes remain uncommitted until explicitly requested.
