# Whole-Plugin Bug Sweep Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix every confirmed major, moderate, and minor defect from the 2026-08-25 whole-plugin audit while preserving the existing in-memory single-round architecture.

**Architecture:** Keep `GameManager` as the round mutation boundary and strengthen its existing collaborators rather than introducing a second session model. Make participant scope explicit at listeners, treat saved spawn points as exact player-feet destinations, validate stored data before it enters lifecycle state, and retain snapshots until restoration reports success.

**Tech Stack:** Java 21, Paper API 1.21.4, JUnit 5, Mockito, Maven Surefire.

## Global Constraints

- Work only in the existing `codex/phase-1-lifecycle` worktree.
- Keep all work uncommitted unless the user explicitly requests a commit.
- An active survivor death converts the survivor to infected with the full configured infected lives.
- Pre-active infected deaths do not consume lives and return to the holding spawn under containment.
- Infection by attacks remains direct-player melee only; death conversion is a separate explicit rule.
- No database, restart recovery, multi-arena support, new weapons, or world-spawn fallback.
- Spawn management remains config-only and never changes arena blocks.

---

### Task 1: Participant-scoped combat and complete death routing

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/ParticipantDamageListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedDeathListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedRespawnListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Test: `src/test/java/me/DaWHeL/infected/Handlers/ParticipantDamageListenerTest.java`
- Test: `src/test/java/me/DaWHeL/infected/Handlers/InfectedDeathListenerTest.java`
- Test: `src/test/java/me/DaWHeL/infected/Handlers/InfectedRespawnListenerTest.java`
- Test: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`

**Interfaces:**
- `ParticipantDamageListener` ignores damage unless both players satisfy `GameManager.isRoundParticipant(Player)`.
- `GameManager.handlePlayerDeath(Player)` returns whether the death belongs to the round and records active-survivor conversion, active infected life consumption, or pre-active infected holding respawn.
- `GameManager.claimHoldingRespawn(Player)` claims a pending pre-active infected respawn.

- [ ] Write regressions for lobby PvP, active survivor conversion, last-survivor conclusion, and pre-active infected holding respawn.
- [ ] Run the focused tests and confirm failures are caused by the missing participant/death behavior.
- [ ] Implement participant guards, death state transitions, pending holding respawns, and respawn routing.
- [ ] Run the focused tests until green.

### Task 2: Exact and validated spawn behavior

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/TeleportManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/SpawnRepository.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminSetupService.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminGuiManager.java`
- Test: `src/test/java/me/DaWHeL/infected/TeleportManagerTest.java`
- Test: `src/test/java/me/DaWHeL/infected/SpawnRepositoryTest.java`
- Test: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Test: `src/test/java/me/DaWHeL/infected/gui/AdminSetupServiceTest.java`
- Test: `src/test/java/me/DaWHeL/infected/gui/AdminGuiManagerTest.java`

**Interfaces:**
- Batch destinations are exact clones of `loadedLocations(role).get(index % size)`.
- `SpawnRepository` excludes sections without finite numeric `x`, `y`, and `z`.
- Start/readiness treat `INFECTED_RESPAWN` as available only when `InfectedRespawnSelector.isSafe` accepts at least one loaded location.

- [ ] Write regressions for exact coordinates, malformed numbers, unsafe start rejection, loaded-world readiness, and exact GUI preview.
- [ ] Run the focused tests and confirm the expected failures.
- [ ] Implement strict deserialization, exact routing, common safe-respawn readiness, and exact previews.
- [ ] Run the focused tests until green.

### Task 3: Restoration, roster, and admin safety

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/PlayerStateSnapshot.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/InfectedPlugin.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminGuiManager.java`
- Test: `src/test/java/me/DaWHeL/infected/PlayerStateSnapshotTest.java`
- Test: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Test: `src/test/java/me/DaWHeL/infected/gui/AdminGuiManagerTest.java`

**Interfaces:**
- `PlayerStateSnapshot.restore(Player)` returns `true` only when every property and the location restore successfully.
- `GameManager` removes a snapshot only after `restore` succeeds.
- Shutdown restoration resolves every online UUID with a snapshot, including queued spectators.

- [ ] Write regressions for queued shutdown restoration, failed-teleport snapshot retention, eliminated-player removal, dead toggle rejection, survivor presentation, lobby-only setup mutations, scoped winners, and online-player enable registration.
- [ ] Run focused tests and confirm failures.
- [ ] Implement the restoration result, unified restoration roster, roster/admin guards, presentation scoping, setup phase gates, and enable reconstruction.
- [ ] Run focused tests until green.

### Task 4: Legacy command parity and small lifecycle/UX fixes

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/commands/ListTeleportPoints.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/TeleportToTeleportPoint.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/TpInfectedSpawn.java`
- Modify: `src/main/java/me/DaWHeL/infected/ScoreboardManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/MatchPresentationService.java`
- Modify: `src/main/resources/config.yml`
- Test: add or extend the corresponding command, scoreboard, lifecycle, and presentation tests.

**Interfaces:**
- Legacy survivor-point commands read through `SpawnRepository` and preserve exact coordinates.
- Scoreboard entry identity uses invisible suffixes while visible configured text remains unchanged.
- Round teleport completion wrappers forget their registered task handles.

- [ ] Write regressions for legacy list/teleport parity, cancelled-teleport feedback, duplicate scoreboard lines, and completed-task forgetting.
- [ ] Run focused tests and confirm failures.
- [ ] Implement repository-backed commands, accurate feedback, unique scoreboard entries, task forgetting, and time-limit-neutral default copy.
- [ ] Run focused tests until green.

### Task 5: Whole-plugin verification and review

**Files:**
- Review all files changed by Tasks 1-4.

- [ ] Run `mvn.cmd -o clean package` and require zero failures/errors.
- [ ] Run `git diff --check -- . ':(exclude)target/**'`.
- [ ] Inspect the complete source diff for unrelated changes, missing registration, stale legacy paths, and lifecycle collections not cleared at cleanup/shutdown.
- [ ] Re-run the original audit scenarios against the final code and document any live-Paper-only gaps.
