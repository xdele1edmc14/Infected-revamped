# Match Experience and Admin Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add polished round titles and sounds, command/GUI parity, fully GUI-initiated chat-named spawn creation, and reliable plugin-owned zombie-head cleanup.

**Architecture:** `GameManager` remains lifecycle owner and delegates presentation to `MatchPresentationService`. `AdminActionService` centralizes command behavior, `SpawnCreationSessionManager` owns pending chat prompts, and `InfectedRoleEquipment` owns tagged zombie heads.

**Tech Stack:** Java 21, Paper 1.21.4 API, Adventure components, Bukkit configuration, JUnit 5, Mockito, Maven

## Global Constraints

- Preserve all existing uncommitted Phase 1-3 and respawn-fix work.
- Do not modify arena blocks when saving or removing spawn points.
- Release as version `2.0.0` following the user-requested completed-revamp version change.
- Do not commit, merge, push, or remove the worktree.

---

### Task 1: Match Presentation Service

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/MatchPresentationService.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/resources/config.yml`
- Test: `src/test/java/me/DaWHeL/infected/MatchPresentationServiceTest.java`
- Test: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`

**Interfaces:**
- Produces: `countdown(Collection<Player>, int)`, `deployment(Collection<Player>)`, and `active(Collection<Player>, Collection<Player>, Collection<Player>)`

- [ ] Write failing tests for final-three countdown cues, deployment presentation, role-specific active titles, participant scoping, and invalid sounds.
- [ ] Run `mvn.cmd -o "-Dtest=MatchPresentationServiceTest,GameManagerLifecycleTest" test` and confirm failures identify missing presentation behavior.
- [ ] Implement the service, configuration defaults, and phase-boundary calls.
- [ ] Re-run the targeted tests and confirm they pass.

### Task 2: Shared Admin Actions and Root Subcommands

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/admin/AdminActionService.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/InfectedAdminCommand.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/StartGame.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/StopGame.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/Reload.java`
- Modify: `src/main/java/me/DaWHeL/infected/InfectedPlugin.java`
- Test: `src/test/java/me/DaWHeL/infected/admin/AdminActionServiceTest.java`
- Test: `src/test/java/me/DaWHeL/infected/gui/InfectedAdminCommandTest.java`

**Interfaces:**
- Produces: `start(CommandSender)`, `stop(CommandSender)`, `reload(CommandSender)`, `status(CommandSender)`, and `help(CommandSender)`

- [ ] Write failing tests showing standalone and root command forms share results, permissions, phase checks, console support, and tab completion.
- [ ] Run the targeted admin tests and confirm the new root subcommands fail.
- [ ] Implement shared actions and delegate both command styles.
- [ ] Re-run the targeted tests and confirm they pass.

### Task 3: Chat-Named GUI Spawn Creation

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/gui/SpawnCreationSessionManager.java`
- Create: `src/main/java/me/DaWHeL/infected/gui/SpawnCreationChatListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminGuiManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminSetupService.java`
- Modify: `src/main/java/me/DaWHeL/infected/InfectedPlugin.java`
- Test: `src/test/java/me/DaWHeL/infected/gui/SpawnCreationSessionManagerTest.java`
- Test: `src/test/java/me/DaWHeL/infected/gui/AdminGuiManagerTest.java`

**Interfaces:**
- Produces: `begin(Player, SpawnRole, int)`, `capture(Player, String)`, `cancel(Player, boolean)`, and `cancelAll()`

- [ ] Write failing tests for GUI start, hidden chat capture, successful save/reopen, `close`, invalid names, duplicates, timeout, quit, reload, round start, and disable.
- [ ] Run the targeted GUI/session tests and confirm the missing session behavior fails.
- [ ] Implement the synchronous session manager, async chat bridge, GUI integration, and lifecycle cancellation hooks.
- [ ] Re-run targeted tests and confirm they pass.

### Task 4: Tagged Infected Role Equipment

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/InfectedRoleEquipment.java`
- Modify: `src/main/java/me/DaWHeL/infected/Roles/Infected.java`
- Modify: `src/main/java/me/DaWHeL/infected/BukkitParticipantRoleFactory.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedRespawnListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Test: `src/test/java/me/DaWHeL/infected/InfectedRoleEquipmentTest.java`
- Test: `src/test/java/me/DaWHeL/infected/GameManagerCleanupTest.java`
- Test: `src/test/java/me/DaWHeL/infected/Handlers/InfectedRespawnListenerTest.java`

**Interfaces:**
- Produces: `createHead()`, `isOwnedHead(ItemStack)`, and `removeOwnedHead(Player)`

- [ ] Write failing tests for tagged creation, removal of owned heads, preservation of legitimate heads, and living/dead cleanup.
- [ ] Run targeted equipment and lifecycle tests and confirm the ownership cases fail.
- [ ] Route all infected head creation and cleanup through `InfectedRoleEquipment`.
- [ ] Re-run targeted tests and confirm they pass.

### Task 5: Full Verification

**Files:**
- Verify all changed sources, resources, tests, design, and plan files

- [ ] Run `mvn.cmd -o clean package` and confirm zero failures or errors.
- [ ] Inspect `target/Infected-2.0.0.jar!/plugin.yml` and confirm version and command metadata.
- [ ] Run `git diff --check -- . ':(exclude)target/**'`.
- [ ] Report the JAR path and SHA-256 while leaving changes uncommitted.
