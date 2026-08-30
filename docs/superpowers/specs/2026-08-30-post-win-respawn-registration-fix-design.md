# Post-Win Respawn Registration Fix Design

## Problem

In a two-player round, a death can end the match while the losing player is still in Bukkit's dead state. Cleanup restores the living player to the lobby roster. When the dead player reaches `PlayerRespawnEvent`, snapshot restoration succeeds, but `GameManager.restoreAfterRoundRespawn` refuses to restore their lobby role while `Player.isDead()` remains true. No later event retries registration, so the next start sees only one participant and fails the minimum-player check.

## Chosen Approach

Treat successful snapshot restoration from `PlayerRespawnEvent` as the authoritative transition back into the lobby. When the round is already in `LOBBY`, the player is online, and respawn restoration succeeds, `GameManager` will upsert the survivor without checking `Player.isDead()`.

The ordinary lobby join path and the final participant collection used by `startGame` keep their existing online/alive checks. This limits the exception to the respawn lifecycle point and prevents a genuinely dead or disconnected player from starting a round.

No delayed task is introduced. This avoids a one-tick race with an immediate start, adds no lifecycle-owned work to cancel, and keeps snapshot removal and role restoration in one atomic path.

## Testing

Add a regression test that:

1. Starts an active two-player round.
2. Marks the last survivor dead and processes the death that awards the infected win.
3. Runs cleanup while both players remain online.
4. Restores the dead player through the respawn path while Bukkit still reports them dead.
5. Marks the respawn complete and verifies the immediate next round accepts both players.

The test must fail against `2.0.0`, pass after the fix, and the complete Maven package suite must remain green. Release metadata will advance to `2.0.1`.
