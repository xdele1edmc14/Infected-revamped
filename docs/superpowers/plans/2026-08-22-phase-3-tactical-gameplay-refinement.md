# Phase 3 Tactical Gameplay Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete finite infected respawns, enforce one reversible infected buff task, remove Jump Feather, and protect the full gameplay lifecycle with automated tests.

**Architecture:** Preserve `GameManager` as the round mutation boundary, `InfectedLifeTracker` as the life counter, `InfectedRespawnSelector` as the safe-location policy, and `RoundTaskRegistry` as the scheduled-work owner. Add a small plugin-item ownership seam for tagged tracking compasses, while listeners and commands remain thin adapters.

**Tech Stack:** Java 21, Paper 1.21.4 API, Maven, JUnit 5, Mockito.

## Global Constraints

- Continue in the existing `codex/phase-1-lifecycle` linked worktree.
- Preserve all uncommitted Phase 1 and Phase 2 changes.
- Use failing tests before every production behavior change.
- Zombies remain melee-only; no database or restart recovery is added.
- Dedicated unsafe infected respawns cancel the round; there is no world-spawn fallback.
- Do not commit unless the user explicitly requests it.

---

### Task 1: Finite-life death and safe-respawn race coverage

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedDeathListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedRespawnListener.java`
- Modify: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/Handlers/InfectedDeathListenerTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/Handlers/InfectedRespawnListenerTest.java`

**Interfaces:**
- Preserves: `boolean GameManager.handleInfectedDeath(Player)` as the atomic life-consumption boundary.
- Consumes: `InfectedRespawnSelector.select(List<Location>, Random)` and `RoundTaskRegistry` phase-guarded scheduling.
- Produces: one observable pending-elimination state consumed by the next respawn event and cleared by cleanup.

- [ ] Add a failing death-listener test proving non-`ACTIVE` infected deaths do not clear drops, consume lives, or schedule elimination.
- [ ] Run `mvn.cmd "-Dtest=InfectedDeathListenerTest" test` and confirm the phase guard is missing.
- [ ] Add the minimal `ACTIVE`/role guard before death ownership and life consumption.
- [ ] Re-run the death-listener test and confirm it passes.
- [ ] Add failing lifecycle/respawn tests for one decrement per active death, remaining-life safe respawn, final-life spectator transition, stale delayed loadout suppression, and no-safe-point cancellation.
- [ ] Run `mvn.cmd "-Dtest=GameManagerLifecycleTest,InfectedRespawnListenerTest" test` and confirm only uncovered race/cleanup assertions fail.
- [ ] Make the minimal manager/listener changes needed to consume the pending elimination once, retain current buff state on valid respawn, and clear pending markers during cleanup.
- [ ] Re-run the focused tests and confirm they pass.

### Task 2: Single round-owned buff task and tagged compass

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/InfectedBuffController.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/BuffInfectedCommand.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedRespawnListener.java`
- Create: `src/test/java/me/DaWHeL/infected/InfectedBuffControllerTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/Handlers/InfectedRespawnListenerTest.java`

**Interfaces:**
- Produces: `InfectedBuffController.apply(Player, boolean)`, `removeOwnedCompasses(Player)`, and `isOwnedCompass(ItemStack)` using a `NamespacedKey` persistent-data byte tag.
- Produces: `RoundActionResult GameManager.toggleInfectedBuff()` as the only command mutation boundary.
- Preserves: `boolean GameManager.isBuffEnabled()` for respawn and role-shift adapters.

- [ ] Add failing controller tests proving enabled state applies Speed II and Resistance II, adds exactly one tagged compass, repeated apply does not duplicate it, disabled state removes only tagged compasses, and disabled state restores Speed I without resistance.
- [ ] Run `mvn.cmd "-Dtest=InfectedBuffControllerTest" test` and confirm the controller is absent.
- [ ] Implement the controller with Paper persistent-data metadata and inventory-slot iteration that preserves ordinary compasses.
- [ ] Re-run the controller tests and confirm they pass.
- [ ] Add failing lifecycle tests proving repeated enable is idempotent, disable cancels the one tracking task, re-enable creates only one replacement, cleanup cancels it, tracking excludes queued spectators, and newly infected players receive current state.
- [ ] Run `mvn.cmd "-Dtest=GameManagerLifecycleTest" test` and confirm the existing command/task implementation violates the single-owner assertions.
- [ ] Move buff mutation into `GameManager`, store one round task handle, register/cancel it through `RoundTaskRegistry`, and delegate player item/effect work to `InfectedBuffController`.
- [ ] Make `BuffInfectedCommand` a thin phase-safe toggle adapter and make respawn/role-shift paths call the controller once.
- [ ] Re-run the lifecycle, controller, command, and respawn tests and confirm they pass.

### Task 3: Remove Jump Feather completely

**Files:**
- Delete: `src/main/java/me/DaWHeL/infected/Handlers/JumpFeatherListener.java`
- Delete: `src/main/java/me/DaWHeL/infected/commands/GiveFeather.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/InfectedPlugin.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/HelpInfectedCommand.java`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/gui/PluginMetadataTest.java`

**Interfaces:**
- Removes: feather scheduling, cooldown, no-fall, listener, command, help, and metadata surfaces.
- Preserves: all non-feather combat, respawn, and general cleanup behavior.

- [ ] Add/update a failing plugin-metadata behavior test that loads `plugin.yml` and asserts `givefeather` is not registered while required gameplay commands remain registered.
- [ ] Run `mvn.cmd "-Dtest=PluginMetadataTest" test` and confirm `givefeather` is still exposed.
- [ ] Remove command/listener registration, metadata, help text, manager state/methods, and feather-only lifecycle tests; delete both feature classes.
- [ ] Run `rg -n -i "jump feather|givefeather|Material\\.FEATHER|featherCooldown|NoFallProtection|startFeatherTask" src README.md` and confirm no feature references remain.
- [ ] Re-run `PluginMetadataTest` and all lifecycle tests and confirm they pass.

### Task 4: Gameplay regression matrix and final verification

**Files:**
- Modify: `src/test/java/me/DaWHeL/infected/CombatPolicyTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/RoundOutcomePolicyTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/RoundStartValidatorTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/Handlers/PlayerRoundListenerTest.java`
- Verify: all modified production and test files.

**Interfaces:**
- Exercises: selection, head-start protection, finite lives, outcomes, join/quit routing, cleanup, friendly fire, and setup validation through their public domain boundaries.

- [ ] Add focused missing regression cases with literal expectations for deterministic starting-infected count, all non-active infection rejection, both-team friendly fire, every terminal outcome, queued join/quit exclusion, cleanup task cancellation/restoration, and each invalid setup category.
- [ ] Run `mvn.cmd "-Dtest=CombatPolicyTest,GameManagerLifecycleTest,RoundOutcomePolicyTest,RoundStartValidatorTest,PlayerRoundListenerTest" test` and confirm any newly exposed behavior gap fails for the intended reason.
- [ ] Apply only the minimal production corrections required by failing behavior tests, re-running each focused class to green before proceeding.
- [ ] Run `mvn.cmd -o clean package` and confirm all tests pass with zero failures/errors and `target/Infected-2.0.0.jar` is produced.
- [ ] Restore tracked generated `target/` files with `git restore --worktree --source=HEAD -- target`.
- [ ] Run `git diff --check`, inspect `git status --short`, and confirm unrelated user work is preserved.
- [ ] Leave all Phase 3 changes uncommitted and report the exact worktree and verification totals.
