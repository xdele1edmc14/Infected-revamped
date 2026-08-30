# Phase 2 Player Routing, Team Outcomes, and Admin Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make late joins, departures, removals, and administrative commands obey one explicit lifecycle policy and always restore player state safely.

**Architecture:** Keep `GameManager` as the sole roster mutation boundary, `RoundOutcomePolicy` as the pure team-count decision boundary, and `RoundPhase` as the shared phase-capability boundary. Bukkit listeners and commands remain thin adapters that delegate to those components.

**Tech Stack:** Java 21, Paper 1.21.4 API, Maven, JUnit 5, Mockito.

## Global Constraints

- Continue in the existing `codex/phase-1-lifecycle` linked worktree.
- Preserve Phase 1 snapshot restoration and task ownership.
- Zombies remain melee-only; no database or restart recovery is added.
- Do not commit unless the user explicitly requests it.

---

### Task 1: Explicit phase capabilities

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/RoundPhase.java`
- Modify: `src/test/java/me/DaWHeL/infected/RoundPhaseTest.java`

**Interfaces:**
- Produces: `queuesLateJoins()`, `allowsParticipantRemoval()`, `allowsZombieToggle()`, `allowsConfigReload()`, and `allowsAdminStop()`.

- [ ] Add a failing phase-matrix test asserting the exact allowed phases for every capability.
- [ ] Run `mvn.cmd -Dtest=RoundPhaseTest test` and confirm the methods are missing.
- [ ] Implement the five switch-based capability methods with no mutable state.
- [ ] Re-run `RoundPhaseTest` and confirm it passes.

### Task 2: Routing and admin gates use the shared policy

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/Reload.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/StopGame.java`
- Modify: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/commands/StopGameTest.java`

**Interfaces:**
- Consumes: the `RoundPhase` capability methods from Task 1.
- Preserves: `RoundActionResult toggleZombieSafely(Player)`, `boolean queueLateJoin(Player)`, and `RoundActionResult removePlayer(Player)`.

- [ ] Add failing tests for removal during `DEPLOYING`, the deployment wording in a rejected removal, and `/stopinfected` during all four live phases.
- [ ] Run the focused lifecycle and command tests and confirm the missing deployment coverage/message fails.
- [ ] Replace duplicated phase comparisons with the shared capabilities and update the removal rejection to name countdown, deployment, head start, and active play.
- [ ] Re-run the focused tests and confirm they pass.

### Task 3: Complete team-count evaluation

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/RoundOutcomePolicy.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/test/java/me/DaWHeL/infected/RoundOutcomePolicyTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`

**Interfaces:**
- Produces: `RoundOutcomePolicy.evaluate(RoundPhase, int, int)` for count-only checks.
- Preserves: the cause-aware four-argument overload used after roster changes.

- [ ] Add failing tests proving a direct active roster check awards infected and survivor wins rather than checking only the both-empty case.
- [ ] Run the focused outcome/lifecycle tests and confirm `checkWin()` fails those cases.
- [ ] Add the count-only overload and make `GameManager.checkWin()` delegate to it.
- [ ] Re-run the focused tests and confirm all outcome paths pass.

### Task 4: Atomic removal and late-join regression coverage

**Files:**
- Modify: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/Handlers/PlayerRoundListenerTest.java`

**Interfaces:**
- Exercises: `queueLateJoin`, `handleQuit`, `removePlayer`, `isQueued`, `roleOf`, and snapshot restoration.

- [ ] Add tests for joins in every running phase, queued-spectator removal, final infected removal, and absence of survivor recreation after removal.
- [ ] Run the focused tests; if an assertion fails, make only the minimal `GameManager` or listener correction required by the approved design.
- [ ] Re-run the focused tests and confirm they pass.

### Task 5: Full verification

**Files:**
- Verify: all modified sources and tests.

- [ ] Run `mvn.cmd clean package` in the sandbox-native verification mirror.
- [ ] Confirm 0 test failures/errors and a shaded `Infected-2.0.0.jar`.
- [ ] Run `git diff --check` and inspect `git status --short` to ensure generated artifacts and unrelated files are untouched.
- [ ] Leave all Phase 2 changes uncommitted and report the exact worktree path.
