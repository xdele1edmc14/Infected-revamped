# Round Modes and Time Limit Design

## Goal

Let an administrator choose one uncluttered round mode from the existing Infected GUI before a round starts.

## Rules

- `TIME_LIMIT` uses its own configured starting-zombie count and duration. Infected players have exactly one life. Survivors win by eliminating every infected player or by having at least one survivor alive when the active-play timer reaches zero. Infected win by infecting every survivor before then.
- `DEATHMATCH` has no timer. It uses its own configured starting-zombie count and infected-life count. Survivors win by exhausting every infected player's lives; infected win by infecting every survivor.
- Shared transport and lifecycle settings remain shared. The mode-specific configuration is deliberately limited to gameplay rules that differ between modes.
- The GUI contains one `Round Mode` control. It cycles modes only in `LOBBY`; active-round rules are immutable.
- Mode selection is in memory. `settings.default-round-mode` supplies the selection after plugin startup.

## Architecture

`RoundMode` names the two modes. `RoundRules` is an immutable, validated snapshot loaded from the selected mode's configuration. `GameManager` owns the selected lobby mode, captures `RoundRules` at start, schedules/cancels the active timer with the existing round-task lifecycle, and exposes read-only mode/time data to the GUI and scoreboard.

The scoreboard adds `{round_mode}` and `{time_remaining}` placeholders. Deathmatch renders `No Limit`; Time Limit renders `m:ss`. A separate configurable timeout victory message avoids falsely claiming that every infected player was eliminated.

During active Time Limit play, every participant sees a configurable countdown boss bar. In both modes, every zombie receives a locked tracking compass that updates once per second to point at the nearest online survivor in the same world.

## Safety and compatibility

- Existing `settings.starting-zombies` and `settings.infected-lives` remain fallback paths for installations that have not migrated their config.
- Timer callbacks are guarded by round id and active phase, so callbacks from an ended round cannot finish a newer round.
- No database, restart recovery, health modification, branch, commit, or unrelated cleanup is added.

## Verification

Tests cover config snapshots, mode cycling/gating, per-mode starting zombies and lives, timer victory, stale/cancelled timer behavior, boss-bar progress/cleanup, compass targeting, GUI routing, and scoreboard placeholders. The full Maven package and diff whitespace checks must pass.
