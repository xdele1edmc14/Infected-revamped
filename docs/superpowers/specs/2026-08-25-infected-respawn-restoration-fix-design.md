# Infected Respawn and Restoration Fix Design

## Problem

Configured infected respawn points with non-centered decimal coordinates can be rejected even on a safe, flat floor because the validator requires one block to support the player's full width at the raw coordinate. When an eliminated infected respawns, the listener does not assign a destination, so Minecraft can fall back to the world's default spawn at `0,0,0`. Cleanup can also consume a pre-round snapshot while a participant is still dead, allowing respawn processing to retain plugin-owned zombie equipment.

## Design

- Normalize each infected respawn candidate to the center of its current block while preserving world, Y, yaw, and pitch; validate and return that normalized destination.
- During active play, assign a safe dedicated destination to eliminated infected before switching them to spectator mode.
- If a death ends or cancels the round, restore the dead participant through `PlayerRespawnEvent` and use the captured pre-round location as the event destination instead of allowing a vanilla world-spawn fallback.
- Do not consume a participant snapshot during cleanup while that player is dead. Restore it on their next respawn, including original armor and helmet, then admit them to the lobby roster.
- Retain existing behavior when no safe dedicated point exists: cancel the round, but restore the currently respawning player rather than leaving their destination unset.

## Verification

- A configured point near a block edge selects the centered version of that point.
- An eliminated infected in an active round receives a dedicated respawn location and becomes a spectator.
- A dead participant whose round ends retains a pending snapshot until respawn; respawn restores the original armor and location.
- Existing selector, finite-life, cleanup, and lifecycle tests remain green.
