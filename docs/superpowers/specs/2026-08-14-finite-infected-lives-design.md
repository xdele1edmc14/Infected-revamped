# Finite Infected Lives Design

## Goal

Let survivors win a running Infected round by exhausting every active infected player's configured lives.

## Rules

- Add `settings.infected-lives` to `config.yml`. Its value is the total number of lives each player receives when they become infected, including the life they are currently playing.
- On an infected player's death, consume one life.
- If at least one life remains, the player uses the existing infected respawn flow and is returned to an arena teleport point with the infected kit and effects.
- If no lives remain, remove the player from the active infected roster and set them to spectator mode after respawn. Eliminated players do not receive the infected kit, effects, or an arena respawn location.
- A survivor converted to infected receives the same configured number of lives as an initial infected player.
- A round ends with an infected victory when no active survivors remain, or with a survivor victory when no active infected remain. Win checks only act while a round is running.
- Every per-player life record is cleared when a round stops, so it never affects a later round.

## Design

`GameManager` owns the active infected roster and a per-round map from player UUID to remaining lives. It exposes focused operations for registering an infected player, consuming a death, and determining whether that player remains active. This makes the death and respawn listeners agree on one source of truth without putting mutable round state in a role object.

The death listener continues to suppress infected drops and XP. It will also consume the dead infected player's life. When the death eliminates the player, it removes them from the active roster, marks them for spectator mode at respawn, and immediately reevaluates the win condition.

The respawn listener first checks whether the player is still an active infected player. Only active infected use the existing arena-respawn and zombie-kit path. A player marked eliminated is switched to spectator mode after their respawn; they are not returned to play.

`checkWin()` has two mutually exclusive running-round branches. It preserves the existing infected-win messages for an empty survivor roster and adds configurable survivor-win messages for an empty infected roster. Both branches announce the result, show titles, then stop and clean up the round.

## Verification

Automated tests will cover life registration, a non-final infected death, final-death elimination, survivor conversion receiving a fresh life allowance, and both win-result selections. Maven packaging will verify the Paper plugin compiles.
