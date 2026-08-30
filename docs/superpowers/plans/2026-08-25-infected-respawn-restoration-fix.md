# Infected Respawn and Restoration Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent infected deaths from falling back to `0,0,0` and ensure plugin-owned zombie heads are removed after round cleanup.

**Architecture:** Normalize dedicated respawn candidates at the selector boundary, always assign an explicit destination during active infected respawns, and keep dead-player snapshots pending until `PlayerRespawnEvent` can restore them safely. The existing `GameManager` continues to own round state and snapshots; the respawn listener only coordinates the Bukkit event.

**Tech Stack:** Java 21, Paper 1.21 API, JUnit 5, Mockito, Maven

## Global Constraints

- Preserve existing uncommitted Phase 1-3 work.
- Keep teleport-point storage non-destructive.
- Do not weaken hazard, headroom, build-height, or world-border validation.
- Do not create a commit unless the user explicitly asks.

---

### Task 1: Normalize Dedicated Infected Respawns

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/InfectedRespawnSelector.java`
- Test: `src/test/java/me/DaWHeL/infected/InfectedRespawnSelectorTest.java`

**Interfaces:**
- Consumes: configured `List<Location>` candidates
- Produces: `Optional<Location>` containing a safe block-centered destination

- [x] Add a regression test using a safe location near a block edge and assert selection returns the block center while preserving Y, yaw, and pitch.
- [x] Run `mvn.cmd -o -Dtest=InfectedRespawnSelectorTest test` and confirm the new test fails because the current selector rejects the raw coordinate.
- [x] Center X and Z before safety validation and selection.
- [x] Re-run the targeted test and confirm it passes.

### Task 2: Assign Explicit Destinations for Eliminated Infected

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedRespawnListener.java`
- Test: `src/test/java/me/DaWHeL/infected/Handlers/InfectedRespawnListenerTest.java`

**Interfaces:**
- Consumes: `SpawnRole.INFECTED_RESPAWN` locations and infected finite-life state
- Produces: an explicit `PlayerRespawnEvent#setRespawnLocation` call before spectator transition

- [x] Add a regression test showing an eliminated infected in `ACTIVE` receives a safe dedicated destination.
- [x] Run `mvn.cmd -o -Dtest=InfectedRespawnListenerTest test` and confirm it fails because the eliminated branch leaves the destination unset.
- [x] Select and assign the destination before scheduling spectator mode.
- [x] Re-run the targeted listener test and confirm it passes.

### Task 3: Restore Dead Participants on Respawn

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/PlayerStateSnapshot.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedRespawnListener.java`
- Test: `src/test/java/me/DaWHeL/infected/GameManagerCleanupTest.java`
- Test: `src/test/java/me/DaWHeL/infected/Handlers/InfectedRespawnListenerTest.java`

**Interfaces:**
- Produces: `GameManager.restoreAfterRoundRespawn(Player): Optional<Location>`
- Produces: snapshot restoration that can return its captured destination without teleporting during `PlayerRespawnEvent`

- [x] Add regression tests proving cleanup does not consume a dead player's snapshot and respawn restores the captured armor/location.
- [x] Run the targeted cleanup and listener tests and confirm they fail for the current eager restoration behavior.
- [x] Defer snapshot consumption for dead players, add respawn-safe restoration, and make the listener assign the captured destination.
- [x] Re-run targeted tests and confirm they pass.

### Task 4: Full Verification

**Files:**
- Verify all modified source and test files

- [x] Run `mvn.cmd -o clean package`.
- [x] Confirm zero failures/errors and `target/Infected-2.0.0.jar` exists.
- [x] Inspect packaged `plugin.yml` and confirm version `2.0.0`.
- [x] Run `git diff --check -- . ':(exclude)target/**'`.
