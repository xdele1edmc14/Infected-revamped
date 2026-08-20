# Phase 1 Round Lifecycle, Cleanup, and State Restoration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Build a leak-free Infected round lifecycle with config-only teleport-point management, participant-only hunger, exact player restoration, centralized task cancellation, configurable starts and time limits, and deliberate quit-driven outcomes.

**Architecture:** Keep GameManager as the command/listener facade while adding an immutable PlayerStateSnapshot and scoped RoundTaskRegistry. Extend the phase machine with DEPLOYING; lock and snapshot participants during a real countdown, route every gameplay callback through the registry with round/phase guards, and send all conclusions through one idempotent cleanup path.

**Tech Stack:** Java 21, Paper API 1.21.4, Maven, JUnit Jupiter 5, Mockito 5.

## Global Constraints

- Preserve role-specific spawn storage, combat policy, admin GUI, infected lives, and scoreboard/command consumers.
- settings.minimum-players defaults to 2 and must be at least 2.
- settings.start-countdown-seconds defaults to 10 and must be zero or greater.
- settings.round-time-limit-seconds defaults to 0, must be zero or greater, and 0 disables expiry.
- Time expiry awards survivors when at least one survivor remains.
- Active empty-survivor and empty-infected rosters award zombies and survivors respectively; pre-active team loss is abandonment without a winner.
- Never commit target output or the untracked Bugs To fix.md file.
- Observe a failing test before every production behavior change.

---

## File Structure

- Create src/main/java/me/DaWHeL/infected/PlayerStateSnapshot.java for defensive state capture/restoration.
- Create src/main/java/me/DaWHeL/infected/RoundTaskRegistry.java for gameplay and cleanup task scopes.
- Create corresponding focused unit tests and a HungerListener test.
- Modify phase, validation, conclusion, and outcome-policy types for complete lifecycle rules.
- Modify GameManager for countdown, snapshots, task ownership, expiry, restoration, and outcomes.
- Modify role/listener/command scheduling callers to use manager-owned lifecycle operations.
- Modify config and setup snapshots for the three lifecycle settings.

### Task 1: Config-Only Teleport Points and Participant Hunger

**Files:**
- Modify: src/main/java/me/DaWHeL/infected/TeleportManager.java
- Modify: src/main/java/me/DaWHeL/infected/SpawnRepository.java
- Modify: src/main/java/me/DaWHeL/infected/commands/RemoveTeleportCommand.java
- Modify: src/main/java/me/DaWHeL/infected/Handlers/HungerListener.java
- Modify: src/main/java/me/DaWHeL/infected/InfectedPlugin.java
- Modify: src/test/java/me/DaWHeL/infected/TeleportManagerTest.java
- Modify: src/test/java/me/DaWHeL/infected/SpawnRepositoryTest.java
- Create: src/test/java/me/DaWHeL/infected/Handlers/HungerListenerTest.java

**Interfaces:**
- Consumes: SpawnRepository save/delete and GameManager.isRoundParticipant(Player).
- Produces: boolean TeleportManager.removeTeleportPoint(String) and HungerListener(GameManager).

- [ ] **Step 1: Write failing teleport tests**

Add savesPointWithoutChangingTerrain and removesOnlyStoredConfiguration. The first captures the Location passed to savePoint and verifies World.getBlockAt is never called. The second stubs deletePoint to true, asserts true, and verifies plugin.getServer is never called.

- [ ] **Step 2: Run and verify RED**

Run: mvn.cmd -Dtest=TeleportManagerTest,SpawnRepositoryTest test

Expected: FAIL because add/remove still edit blocks and repository deletion does not report presence.

- [ ] **Step 3: Implement minimal config-only behavior**

Remove Material, World, platform loops, and world lookups. Make repository deletion return whether the named path existed:

    public boolean removeTeleportPoint(String name) {
        return spawnRepository.deletePoint(SpawnRole.SURVIVOR, name);
    }

Update RemoveTeleportCommand feedback for removed versus missing.

- [ ] **Step 4: Write failing hunger tests**

Construct FoodLevelChangeEvent for a Player. Stub isRoundParticipant true and assert cancellation; stub false and assert the event remains uncancelled.

- [ ] **Step 5: Run and verify RED**

Run: mvn.cmd -Dtest=HungerListenerTest test

Expected: FAIL because HungerListener has no manager and cancels globally.

- [ ] **Step 6: Implement participant-only hunger**

    public HungerListener(GameManager gameManager) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
    }

    public void onHungerChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && gameManager.isRoundParticipant(player)) {
            event.setCancelled(true);
        }
    }

Register new HungerListener(gameManager).

- [ ] **Step 7: Verify and commit**

Run: mvn.cmd -Dtest=TeleportManagerTest,SpawnRepositoryTest,HungerListenerTest test
Expected: PASS.

Commit: git commit -m "fix: scope hunger and teleport point changes"

### Task 2: Exact Player-State Snapshot

**Files:**
- Create: src/main/java/me/DaWHeL/infected/PlayerStateSnapshot.java
- Create: src/test/java/me/DaWHeL/infected/PlayerStateSnapshotTest.java

**Interfaces:**
- Produces: static PlayerStateSnapshot capture(Player) and void restore(Player).

- [ ] **Step 1: Write a failing defensive-copy test**

Configure literal storage/armor/offhand items, held slot, mode, glow, effects, Adventure tab name/header/footer, scoreboard, location, compass, food, saturation, and exhaustion. Capture, mutate original arrays and locations, restore, and assert the original literal values reach Bukkit setters.

The production mutation this catches is retaining mutable Bukkit arrays/items/locations.

- [ ] **Step 2: Run and verify RED**

Run: mvn.cmd -Dtest=PlayerStateSnapshotTest test
Expected: test compilation FAIL because PlayerStateSnapshot is missing.

- [ ] **Step 3: Implement defensive capture**

Use final fields. Clone locations and every non-null ItemStack in arrays:

    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            result[index] = source[index] == null ? null : source[index].clone();
        }
        return result;
    }

Copy effects with List.copyOf. Retain immutable Adventure components and the scoreboard reference.

- [ ] **Step 4: Write a failing restoration/fallback test**

Give the player current plugin effects/items, mark the captured world unavailable through Player.getServer().getWorld(UUID), and assert current effects are removed, originals are restored, all other properties restore, and teleport uses current-world spawn.

- [ ] **Step 5: Run and verify RED**

Run: mvn.cmd -Dtest=PlayerStateSnapshotTest test
Expected: FAIL on missing removal or world fallback.

- [ ] **Step 6: Implement complete restoration**

Remove every current effect before adding captured effects. Restore storage, armor, offhand, held slot, formatting, scoreboard, compass, mode, glow, hunger, and location/fallback. Clone mutable values again during restore.

- [ ] **Step 7: Verify and commit**

Run: mvn.cmd -Dtest=PlayerStateSnapshotTest test
Expected: PASS.

Commit: git commit -m "feat: capture and restore participant state"

### Task 3: Scoped Round Task Registry

**Files:**
- Create: src/main/java/me/DaWHeL/infected/RoundTaskRegistry.java
- Create: src/test/java/me/DaWHeL/infected/RoundTaskRegistryTest.java

**Interfaces:**
- Produces: trackGameplay, trackCleanup, forget, cancelGameplay, cancelAll, resetForNewRound, and package-private counts.

- [ ] **Step 1: Write failing scope tests**

Track gameplay, cancel gameplay, then track cleanup. Verify gameplay cancels and cleanup remains. Track both scopes, call cancelAll, then track a late handle; verify all three cancel. Add forget and repeated-cancel idempotence cases.

- [ ] **Step 2: Run and verify RED**

Run: mvn.cmd -Dtest=RoundTaskRegistryTest test
Expected: test compilation FAIL because the registry is missing.

- [ ] **Step 3: Implement scoped identity sets**

Use LinkedHashSet handles, ignore null, immediately cancel handles tracked into closed scopes, and ensure resetForNewRound cancels both scopes before reopening empty sets.

- [ ] **Step 4: Verify and commit**

Run: mvn.cmd -Dtest=RoundTaskRegistryTest test
Expected: PASS.

Commit: git commit -m "feat: centralize round task ownership"

### Task 4: Phase, Validation, and Outcome Policy

**Files:**
- Modify: RoundPhase.java, RoundStartValidator.java, RoundConclusion.java, RoundOutcomePolicy.java
- Modify: their three existing test files.

**Interfaces:**
- Produces: RoundPhase.DEPLOYING, Input minimum/countdown/time-limit fields, and RoundConclusion.ABANDONED.

- [ ] **Step 1: Write failing phase tests**

Assert COUNTDOWN -> DEPLOYING -> HEADSTART is legal, COUNTDOWN -> HEADSTART is illegal, and every non-ending match phase may enter ENDING.

- [ ] **Step 2: Run RED, implement, run GREEN**

Run before: mvn.cmd -Dtest=RoundPhaseTest test
Expected: compilation FAIL for DEPLOYING.
Add enum value and exhaustive switch branches.
Run after: same command; expected PASS.

- [ ] **Step 3: Write failing validator boundaries**

Reject minimum 1, countdown -1, time limit -1, starting zombies equal to minimum, and participants below minimum. Accept minimum 2, countdown 0, time limit 0 with valid spawns and three players.

- [ ] **Step 4: Run RED and extend validator**

Run: mvn.cmd -Dtest=RoundStartValidatorTest test
Expected: FAIL because new fields/rules are absent.
Append fields and add stable errors in documented order.

- [ ] **Step 5: Write failing explicit-outcome tests**

Assert ACTIVE 0 survivors/2 infected -> INFECTED_WIN; ACTIVE 3/0 -> SURVIVORS_WIN; HEADSTART 3/0 -> ABANDONED; ACTIVE 0/0 -> ABANDONED.

- [ ] **Step 6: Run RED, implement policy, verify**

Run before: mvn.cmd -Dtest=RoundOutcomePolicyTest test
Expected: FAIL for ABANDONED.
Order policy as both-empty abandonment, active one-empty winner, pre-active one-empty abandonment, otherwise NONE.

Run: mvn.cmd -Dtest=RoundPhaseTest,RoundStartValidatorTest,RoundOutcomePolicyTest test
Expected: PASS.

- [ ] **Step 7: Commit**

Commit: git commit -m "feat: define complete round controls and outcomes"

### Task 5: Locked Countdown and Match Expiry

**Files:**
- Modify: src/main/java/me/DaWHeL/infected/GameManager.java
- Modify: src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java
- Modify: src/main/resources/config.yml

**Interfaces:**
- Consumes: PlayerStateSnapshot, RoundTaskRegistry, extended validator.
- Produces: isRoundParticipant(Player), actual COUNTDOWN, DEPLOYING orchestration, tracked expiry.

- [ ] **Step 1: Write failing roster-lock test**

Start with three lobby players, capture countdown runnable, register a fourth, complete countdown, and assert only the original three enter selection/teleport. Verify role creation and teleport do not occur before completion.

- [ ] **Step 2: Run and verify RED**

Run: mvn.cmd -Dtest=GameManagerLifecycleTest#locksRosterUntilCountdownCompletes test
Expected: FAIL because start deploys immediately.

- [ ] **Step 3: Implement countdown state**

Add UUID maps lockedParticipants/playerSnapshots. Initial start validates, resets task registry, locks online lobby players, captures snapshots, enters COUNTDOWN, and schedules a tracked tick. Move current selection/teleport code to beginDeployment(roundId).

- [ ] **Step 4: Write failing edge tests**

Cover departure above minimum continuing, departure below minimum abandoning, zero duration deploying without repeating tick, final revalidation failure, and stale tick doing nothing.

- [ ] **Step 5: Run RED and implement ticks**

Run named lifecycle tests; expect failures.
Implement start/10/5..1 milestones, online locked-count checks, final validation, and countdown-handle forgetting.

- [ ] **Step 6: Write failing expiry tests**

Prove 0 schedules none, positive schedules seconds*20 once, active firing concludes survivors win, and stale firing after stop does nothing.

- [ ] **Step 7: Run RED and implement expiry**

Run: mvn.cmd -Dtest=GameManagerLifecycleTest test
Expected: FAIL before implementation.
Schedule guarded expiry after ACTIVE transition only for positive config.

- [ ] **Step 8: Add config defaults/messages and verify**

Add minimum-players, start-countdown-seconds, round-time-limit-seconds, milestone, abandonment, cancellation, and expiry messages.

Run: mvn.cmd -Dtest=GameManagerLifecycleTest,RoundStartValidatorTest test
Expected: PASS.

- [ ] **Step 9: Commit**

Commit: git commit -m "feat: add locked countdown and match expiry"

### Task 6: Exact Cleanup, Quit Restoration, and Team Wins

**Files:**
- Modify: GameManager.java, Roles/Survivor.java, role factories, PlayerJoinListener.java, PlayerQuitListener.java
- Modify: GameManagerCleanupTest.java, GameManagerLifecycleTest.java, Handlers/PlayerRoundListenerTest.java

**Interfaces:**
- Produces: state-neutral lobby registration, handleQuit(Player), exact idempotent cleanup.

- [ ] **Step 1: Write failing state-neutral lobby test**

Register in LOBBY and assert no glow, helmet, tab, inventory, effect, teleport, mode, or scoreboard setter. Assert membership exists once.

- [ ] **Step 2: Run and verify RED**

Run: mvn.cmd -Dtest=PlayerRoundListenerTest,GameManagerLifecycleTest test
Expected: FAIL because Survivor setup mutates the player.

- [ ] **Step 3: Separate membership from match setup**

Make lobby Survivor construction neutral. Apply survivor match cosmetics only after snapshots exist during deployment.

- [ ] **Step 4: Write failing quit outcome tests**

Last active survivor quit: restore first, then zombies win. Last active infected quit: restore first, then survivors win. Team loss in COUNTDOWN/DEPLOYING/HEADSTART: abandonment without winner presentation.

- [ ] **Step 5: Run and verify RED**

Run focused lifecycle/listener tests.
Expected: current wipe/missing infected-empty win/generic cancellation behavior fails assertions.

- [ ] **Step 6: Implement manager-owned quit flow**

Restore/remove snapshot first, remove UUID from every roster and temporary collection, then evaluate one explicit conclusion. Delegate listener logic to this method.

- [ ] **Step 7: Replace wipe cleanup**

Restore snapshots once by UUID in cleanup batches. Remove inventory/effect wipe and forced spawn reset. Rebuild lobby membership without cosmetics. Clear buff, cooldown, life, containment, bypass, and match scoreboard state.

- [ ] **Step 8: Add idempotence/disable tests**

Duplicate roster references restore once; repeated stop restores once; quit-restored player is skipped later; disable restores online snapshots synchronously and schedules nothing.

Run: mvn.cmd -Dtest=GameManagerCleanupTest,GameManagerLifecycleTest,PlayerRoundListenerTest test
Expected: PASS.

- [ ] **Step 9: Commit**

Commit: git commit -m "fix: restore quitters and conclude empty teams"

### Task 7: Route All Scheduled Work Through the Registry

**Files:**
- Modify: GameManager.java, TeleportManager.java, InfectedRespawnListener.java, JumpFeatherListener.java, BuffInfectedCommand.java, InfectedPlugin.java
- Modify focused tests for those callers.

**Interfaces:**
- Produces guarded manager scheduling for round-later, round-repeating, cleanup-repeating, cooldown, no-fall, tracking, and respawn work.

- [ ] **Step 1: Write failing ownership/stale-callback tests**

Cover teleport, head-start, expiry, compass, feather spawn, cooldown, no-fall, respawn follow-up, cleanup, and scoreboard handles. After cancellation manually invoke captured callbacks and assert no phase, inventory, compass, velocity, or role mutation.

- [ ] **Step 2: Run and verify RED**

Run: mvn.cmd -Dtest=GameManagerLifecycleTest,GameManagerCleanupTest,InfectedRespawnListenerTest,TeleportManagerTest test
Expected: FAIL because listeners/commands schedule directly.

- [ ] **Step 3: Add guarded manager scheduling**

Wrap callbacks with round ID, allowed phase, and membership checks. Track returned handle immediately and forget completed handles.

- [ ] **Step 4: Replace direct callers**

Respawn uses one guarded tick; jump no-fall state lives in manager; buffs use one manager tracker; feather spawning becomes round-owned; teleport completion forgets its task. Store/cancel the plugin-wide scoreboard handle explicitly or make it lifecycle-owned.

- [ ] **Step 5: Verify suite and scheduling search**

Run: mvn.cmd test
Expected: PASS.

Run: rg -n "BukkitRunnable|runTask|runTaskLater|runTaskTimer|scheduleSync" src/main/java

Expected: gameplay calls exist only in BukkitPluginTaskScheduler; any enable-owned scoreboard call has an explicit handle cancelled on disable.

- [ ] **Step 6: Commit**

Commit: git commit -m "fix: cancel all round owned scheduled work"

### Task 8: Setup Integration and Final Verification

**Files:**
- Modify: gui/AdminSetupService.java and its test.
- Modify: gui/AdminGuiManager.java and its test only if needed for compact readiness display.
- Modify: README.md only if its restoration warning becomes inaccurate.

**Interfaces:**
- Produces setup snapshot minimum/countdown/time-limit values and readiness matching RoundStartValidator.

- [ ] **Step 1: Write failing setup tests**

Use literal config values and assert snapshot exposure. Invalid values must make readiness false through the shared validator path.

- [ ] **Step 2: Run and verify RED**

Run: mvn.cmd -Dtest=AdminSetupServiceTest,AdminGuiManagerTest test
Expected: FAIL because snapshot fields are absent.

- [ ] **Step 3: Implement setup integration**

Extend snapshot records and reuse validator output. Add compact lore only where existing layout has room; keep the 36-slot main layout.

- [ ] **Step 4: Run full verification**

Run:
    mvn.cmd -Dtest=AdminSetupServiceTest,AdminGuiManagerTest test
    mvn.cmd test
    mvn.cmd clean package
    git diff --check
    git status --short

Expected: focused and full suites have zero failures/errors; package exits 0; diff check prints nothing; status contains intended files plus preserved Bugs To fix.md only.

- [ ] **Step 5: Acceptance and generated-file review**

Map each design acceptance criterion to a named test. Inspect scheduling search and changed diff. Restore generated target changes only, leaving source and Bugs To fix.md untouched.

- [ ] **Step 6: Commit**

Commit: git commit -m "feat: expose safe lifecycle controls"

