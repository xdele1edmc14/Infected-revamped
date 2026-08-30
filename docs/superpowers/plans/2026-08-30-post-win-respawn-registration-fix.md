# Post-Win Respawn Registration Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure a player whose death ends a two-player round is registered for the immediate next round after respawning.

**Architecture:** Keep strict alive checks on normal lobby joins and final start validation. Make successful snapshot restoration during `PlayerRespawnEvent` authoritative for restoring the player's lobby survivor role, even while Bukkit temporarily reports the player as dead.

**Tech Stack:** Java 21, Paper API 1.21, JUnit 5, Mockito, Maven

## Global Constraints

- Do not add a delayed or repeating task.
- Preserve snapshot restoration behavior and normal join/start alive checks.
- Release the verified fix as version `2.0.1`.
- Keep generated `target/` files out of commits.

---

### Task 1: Lock Down and Fix Post-Win Respawn Registration

**Files:**
- Modify: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java:837-863`

**Interfaces:**
- Consumes: `GameManager.handlePlayerDeath(Player)`, `GameManager.restoreAfterRoundRespawn(Player)`, and `GameManager.startGame()`.
- Produces: successful lobby survivor upsert after respawn restoration in `LOBBY`.

- [x] **Step 1: Add the failing two-player regression test**

Add `playerWhoseDeathEndsRoundCanJoinTheImmediateNextRoundAfterRespawn`. Start an active two-player round, keep both players online, mark the last survivor dead, process the winning death and cleanup, restore through the respawn path while `isDead()` is still true, then mark the player alive and assert that the next `startGame()` succeeds.

- [x] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn.cmd -o "-Dtest=GameManagerLifecycleTest#playerWhoseDeathEndsRoundCanJoinTheImmediateNextRoundAfterRespawn" test
```

Expected: FAIL with the minimum-player validation errors because only one player is in the lobby roster.

- [x] **Step 3: Implement the minimal event-aware fix**

In `restoreAfterRoundRespawn`, change the successful lobby restoration condition from:

```java
if (phase == RoundPhase.LOBBY && player.isOnline() && !player.isDead()) {
```

to:

```java
if (phase == RoundPhase.LOBBY && player.isOnline()) {
```

Do not change `registerLobbySurvivor`, `registerOnlineLobbySurvivors`, or `uniqueOnlineLobbyPlayers`.

- [x] **Step 4: Run the focused test and verify GREEN**

Run the focused Maven command from Step 2. Expected: one test passes with zero failures/errors.

- [x] **Step 5: Run the lifecycle test class**

Run:

```powershell
mvn.cmd -o "-Dtest=GameManagerLifecycleTest" test
```

Expected: all lifecycle tests pass.

- [x] **Step 6: Commit the regression fix**

Stage `GameManager.java`, `GameManagerLifecycleTest.java`, and this implementation plan. Commit with `fix: restore respawned players to the next round`.

### Task 2: Release and Verify Version 2.0.1

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/test/java/me/DaWHeL/infected/gui/PluginMetadataTest.java`

**Interfaces:**
- Consumes: the fixed and tested round lifecycle.
- Produces: `target/Infected-2.0.1.jar` with packaged metadata version `2.0.1`.

- [x] **Step 1: Update active release metadata**

Change the Maven project version, source `plugin.yml` version, and metadata test expectation from `2.0.0` to `2.0.1`. Preserve historical design/plan references to earlier artifacts.

- [x] **Step 2: Run the complete clean package build**

Run:

```powershell
mvn.cmd -o clean package
```

Expected: 266 tests pass with zero failures/errors and `target/Infected-2.0.1.jar` is produced.

- [x] **Step 3: Verify release artifact and source hygiene**

Inspect the packaged `plugin.yml`, calculate SHA-256, run `git diff --check -- . ':(exclude)target/**'`, and confirm only intended non-`target/` files changed.

- [x] **Step 4: Commit and push**

Stage only source, test, metadata, spec, and plan files. Commit the patch release and push `codex/phase-1-lifecycle` to `origin`, then verify the remote head equals local `HEAD`.
