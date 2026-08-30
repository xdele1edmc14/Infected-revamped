# Whole-Plugin Bug Sweep Fixes Design

## Goal

Correct the confirmed major, moderate, and minor defects from the 2026-08-25 whole-plugin audit without adding persistence, multi-arena support, new weapons, or unrelated gameplay features.

## Participant and death lifecycle

`GameManager` remains the round mutation boundary, but membership-sensitive listeners must distinguish lobby role records from current round participation. Combat policy applies only when both attacker and victim are current round participants, so the plugin never changes ordinary lobby or unrelated-server PvP.

An active survivor death converts that player to infected. The conversion removes the survivor role, grants the full configured infected-life count, keeps the original pre-round snapshot, and routes the pending respawn to a validated dedicated infected-respawn point. If that was the last survivor, the infected win is concluded normally; cleanup restoration takes precedence over the pending infected respawn. A survivor death before `ACTIVE` does not manufacture an infection or winner.

An infected death during `DEPLOYING` or `HEADSTART` does not consume a life. The player remains infected and respawns at the loaded holding spawn, after which containment remains active until release. Active infected deaths retain the existing finite-life behavior.

Dead players cannot be admitted or switched through `/togglezombie`, and dead lobby players cannot be counted toward start validation. Eliminated infected remain round-owned and can be removed by `/removeplayer`.

## Spawn storage, validation, and routing

Every saved survivor and infected-release point represents the exact player-feet location. Batch routing assigns configured points round-robin without hidden X, Y, or Z offsets. No unchecked synthetic grid is generated around a point.

Spawn deserialization requires a nonblank world plus numeric finite `x`, `y`, and `z` values. Optional yaw and pitch default to zero only when absent. Malformed entries are excluded instead of becoming `0,0,0`.

Start validation requires at least one currently safe dedicated infected-respawn point using the same selector used at respawn time. Runtime selection still revalidates because terrain, borders, and world state can change during a round.

Legacy `/addteleport`, `/removeteleport`, `/listteleportpoints`, and `/tttp` operate on `spawns.survivor` through `SpawnRepository`. Teleport commands preserve exact coordinates and report cancellation instead of false success.

GUI previews use the exact stored location. Role spawn creation, deletion, and holding-spawn mutation are lobby-only, preventing live-round setup invalidation. GUI readiness uses loaded locations and the same safe-respawn definition as actual start validation.

## Restoration, presentation, and task ownership

Plugin shutdown restores every online player that still owns a snapshot, including queued spectators. Snapshot records are removed only after restoration completes without property exceptions and the restoration teleport succeeds. Failed restoration stays available for a later safe retry and is logged.

Switching infected back to survivor applies the survivor role presentation, including the green tab name. Winner titles and sounds are sent only to round participants and queued spectators; configurable chat announcements retain their existing server-broadcast behavior.

Completed teleport tasks notify `GameManager` so their registry handles are forgotten immediately. Scoreboard lines receive invisible unique suffixes so repeated visible text does not collapse. Default active survivor presentation must not promise time expiry when the time limit is disabled.

On plugin enable, already-online players are registered once as lobby survivors. This supports hot re-enable without changing normal startup behavior.

## Testing

Regression tests must be observed failing before each production change. Coverage includes lobby PvP isolation, queued-spectator shutdown restoration, survivor-death conversion, pre-active infected death routing, exact batch destinations, malformed coordinate rejection, safe-respawn start validation, legacy command parity, eliminated-player removal, dead-player toggle rejection, survivor role presentation, restoration retry retention, lobby-only setup mutation, scoped victory presentation, online-player enable reconstruction, unique scoreboard lines, and completed-task forgetting.

The final gate is `mvn.cmd -o clean package`, followed by `git diff --check -- . ':(exclude)target/**'`. Work remains uncommitted unless the user explicitly requests a commit.
