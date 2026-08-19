# Round Participation, Admin Safety, and Respawn Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make late joins, quit outcomes, administrative roster changes, and infected respawns deterministic and safe within the existing round lifecycle.

**Architecture:** Keep `GameManager` as the round owner. Add small pure outcome and spawn-safety collaborators, structured admin results, UUID-based queued membership, and an eligibility check at the teleport boundary.

**Tech Stack:** Java 21, Paper API 1.21.4, Maven, JUnit 5, Mockito

## Global Constraints

- Preserve all existing lifecycle, GUI, combat, and spawn-separation work and release the completed change as 1.0.3.
- Keep the event memory-only with no database, restart recovery, or pre-match snapshots.
- Cleanup wipes/resets players because this is an isolated single-round event.
- Use failing tests before every production behavior change.
- Do not commit or create a branch unless the user explicitly requests it.

---

### Task 1: Cause-Aware Round Outcomes

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/RosterChange.java`
- Create: `src/main/java/me/DaWHeL/infected/RoundConclusion.java`
- Create: `src/main/java/me/DaWHeL/infected/RoundOutcomePolicy.java`
- Test: `src/test/java/me/DaWHeL/infected/RoundOutcomePolicyTest.java`

**Interfaces:**
- Produces: `RoundOutcomePolicy.evaluate(RoundPhase, int, int, RosterChange): RoundConclusion`
- Produces: roster-change values `INFECTION`, `INFECTED_ELIMINATION`, `SURVIVOR_DEPARTURE`, and `INFECTED_DEPARTURE`

- [ ] **Step 1: Write parameterized failing policy tests**

```java
assertEquals(INFECTED_WIN, evaluate(ACTIVE, 0, 2, INFECTION));
assertEquals(CANCELLED, evaluate(ACTIVE, 0, 2, SURVIVOR_DEPARTURE));
assertEquals(SURVIVORS_WIN, evaluate(ACTIVE, 2, 0, INFECTED_DEPARTURE));
assertEquals(CANCELLED, evaluate(HEADSTART, 2, 0, INFECTED_DEPARTURE));
assertEquals(CANCELLED, evaluate(ACTIVE, 0, 0, INFECTED_DEPARTURE));
```

- [ ] **Step 2: Verify RED**

Run: `mvn.cmd test '-Dtest=RoundOutcomePolicyTest'`

Expected: test compilation fails because the new policy types do not exist.

- [ ] **Step 3: Implement the minimal pure policy**

```java
public static RoundConclusion evaluate(
        RoundPhase phase, int survivors, int infected, RosterChange change) {
    if (survivors == 0 && infected == 0) return CANCELLED;
    if (phase == COUNTDOWN || phase == HEADSTART) {
        return survivors == 0 || infected == 0 ? CANCELLED : NONE;
    }
    if (phase != ACTIVE) return NONE;
    if (change == INFECTION && survivors == 0) return INFECTED_WIN;
    if ((change == INFECTED_ELIMINATION || change == INFECTED_DEPARTURE)
            && infected == 0 && survivors > 0) return SURVIVORS_WIN;
    if (change == SURVIVOR_DEPARTURE && survivors == 0) return CANCELLED;
    return NONE;
}
```

- [ ] **Step 4: Verify GREEN**

Run: `mvn.cmd test '-Dtest=RoundOutcomePolicyTest'`

- [ ] **Step 5: Inspect the task diff and leave it uncommitted**

Run: `git diff --check -- src/main/java/me/DaWHeL/infected src/test/java/me/DaWHeL/infected`

### Task 2: Queued Joins, Departures, and Safe Removal

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/RoundActionResult.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/InfectedLifeTracker.java`
- Modify: `src/main/java/me/DaWHeL/infected/TeleportManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/PlayerJoinListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/PlayerQuitListener.java`
- Test: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Test: `src/test/java/me/DaWHeL/infected/Handlers/PlayerRoundListenerTest.java`
- Test: `src/test/java/me/DaWHeL/infected/TeleportManagerTest.java`

**Interfaces:**
- Consumes: `RoundOutcomePolicy.evaluate(...)`
- Produces: `GameManager.queueLateJoin(Player): boolean`
- Produces: `GameManager.handleQuit(Player): void`
- Produces: `GameManager.removePlayer(Player): RoundActionResult`
- Produces: `GameManager.isQueued(Player): boolean`
- Produces: `TeleportManager.teleportPlayersBatch(..., Predicate<Player>, Consumer<TeleportBatchResult>)`

- [ ] **Step 1: Add failing late-join and removal tests**

```java
listener.onPlayerJoin(event);
verify(gameManager).queueLateJoin(player);
verify(gameManager, never()).addLateJoinInfected(player);

RoundActionResult result = manager.removePlayer(activeSurvivor);
assertTrue(result.success());
assertEquals(ParticipantRole.NONE, manager.roleOf(activeSurvivor));
```

Also assert queued joins enter spectator mode, remain outside both rosters, final-survivor departure cancels, final-infected departure awards survivors, and removal never invokes lobby registration.

- [ ] **Step 2: Verify RED**

Run: `mvn.cmd test '-Dtest=GameManagerLifecycleTest,PlayerRoundListenerTest'`

Expected: compilation failures for the new queue/removal APIs and behavior failures against the infected late-join path.

- [ ] **Step 3: Add a failing teleport eligibility test**

```java
manager.teleportPlayersBatch(role, List.of(removed), 1, 0,
        player -> false, completion::set);
operation.run();
verify(removed, never()).teleport(any(Location.class));
assertTrue(completion.get().success());
```

- [ ] **Step 4: Verify the teleport test fails**

Run: `mvn.cmd test '-Dtest=TeleportManagerTest'`

Expected: compilation failure because the eligibility overload does not exist.

- [ ] **Step 5: Implement queue ownership, centralized removal, and eligibility checks**

Add UUID queue membership, remove conflicting states before every role insertion, remove life state through `InfectedLifeTracker.remove(UUID)`, apply `RoundOutcomePolicy` once per roster change, and clear the queue during final cleanup/shutdown. Pass round-id and current-role predicates into survivor/release batches.

- [ ] **Step 6: Verify GREEN**

Run: `mvn.cmd test '-Dtest=GameManagerLifecycleTest,PlayerRoundListenerTest,TeleportManagerTest'`

- [ ] **Step 7: Inspect the task diff and leave it uncommitted**

Run: `git diff --check -- src/main/java/me/DaWHeL/infected src/test/java/me/DaWHeL/infected`

### Task 3: Phase-Safe Administrative Commands

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/ToggleZombie.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/RemovePlayer.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/Reload.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/StopGame.java`
- Modify: `src/main/java/me/DaWHeL/infected/InfectedPlugin.java`
- Test: `src/test/java/me/DaWHeL/infected/commands/ToggleZombieTest.java`
- Test: `src/test/java/me/DaWHeL/infected/commands/RemovePlayerTest.java`
- Test: `src/test/java/me/DaWHeL/infected/commands/ReloadTest.java`
- Test: `src/test/java/me/DaWHeL/infected/commands/StopGameTest.java`

**Interfaces:**
- Consumes: `RoundActionResult`
- Produces: `GameManager.toggleZombieSafely(Player): RoundActionResult`

- [ ] **Step 1: Write failing command-policy tests**

Assert `/togglezombie` rejects non-`ACTIVE` phases, `/removeplayer` rejects `LOBBY` and `ENDING`, reload invokes `reloadConfig()` only in `LOBBY`, and stop distinguishes `LOBBY`, `ENDING`, and stoppable phases. Assert every rejection gives the sender an actionable message.

- [ ] **Step 2: Verify RED**

Run: `mvn.cmd test '-Dtest=ToggleZombieTest,RemovePlayerTest,ReloadTest,StopGameTest'`

Expected: new tests fail because commands currently mutate without the agreed phase gates.

- [ ] **Step 3: Implement structured command delegation**

Commands resolve targets and send the exact `RoundActionResult.message()`. `Reload` receives `GameManager` in its constructor. `GameManager.toggleZombieSafely` accepts queued spectators and active participants, rejects a switch that would empty either team, removes conflicting states, and never leaves a player in both rosters.

- [ ] **Step 4: Verify GREEN**

Run: `mvn.cmd test '-Dtest=ToggleZombieTest,RemovePlayerTest,ReloadTest,StopGameTest'`

- [ ] **Step 5: Run command and GUI compatibility tests**

Run: `mvn.cmd test '-Dtest=StartGameTest,StopGameTest,AdminEventActionsTest,InfectedAdminCommandTest,PluginMetadataTest'`

### Task 4: Safe Dedicated Infected Respawns and Finite Lives

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/InfectedRespawnSelector.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedDeathListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedRespawnListener.java`
- Test: `src/test/java/me/DaWHeL/infected/InfectedRespawnSelectorTest.java`
- Test: `src/test/java/me/DaWHeL/infected/Handlers/InfectedDeathListenerTest.java`
- Test: `src/test/java/me/DaWHeL/infected/Handlers/InfectedRespawnListenerTest.java`

**Interfaces:**
- Produces: `InfectedRespawnSelector.select(List<Location>, Random): Optional<Location>`
- Produces: `InfectedRespawnSelector.isSafe(Location): boolean`
- Produces: `GameManager.cancelForUnsafeInfectedRespawn(): boolean`

- [ ] **Step 1: Write failing selector tests**

Use mocked Paper worlds and blocks to assert rejection for outside-border points, ground collision below or protruding above the saved feet height, insufficient full-width support, blocked feet, blocked headroom, lava/fire/magma/cactus/powder-snow hazards, and build-height violations. Assert selection skips an unsafe first candidate and returns a safe later candidate.

- [ ] **Step 2: Verify RED**

Run: `mvn.cmd test '-Dtest=InfectedRespawnSelectorTest'`

Expected: compilation failure because the selector does not exist.

- [ ] **Step 3: Implement the selector**

Evaluate cloned loaded locations in randomized order. Require `WorldBorder.isInside`, valid min/max height, non-hazardous ground whose collision bounds provide full-width support exactly at the saved feet height, and passable non-hazardous feet/head blocks. Return `Optional.empty()` when none pass.

- [ ] **Step 4: Verify selector GREEN**

Run: `mvn.cmd test '-Dtest=InfectedRespawnSelectorTest'`

- [ ] **Step 5: Write failing death/respawn integration tests**

Assert a death calls `handleInfectedDeath` once, remaining-life respawn uses only a selector-approved `INFECTED_RESPAWN` point, eliminated infected become spectators without a location assignment, and no safe point calls `cancelForUnsafeInfectedRespawn` with no world-spawn fallback.

- [ ] **Step 6: Verify RED**

Run: `mvn.cmd test '-Dtest=InfectedDeathListenerTest,InfectedRespawnListenerTest'`

- [ ] **Step 7: Implement death/respawn integration**

Connect the death result to the existing life tracker, use `spawnRepository.loadedLocations(INFECTED_RESPAWN)` through the selector, delete the world-spawn fallback, and route an empty safe selection to idempotent round cancellation.

- [ ] **Step 8: Verify GREEN**

Run: `mvn.cmd test '-Dtest=InfectedLifeTrackerTest,InfectedDeathListenerTest,InfectedRespawnListenerTest,InfectedRespawnSelectorTest'`

### Task 5: Full Verification and Review

**Files:**
- Modify only if tests expose a requirement gap; every fix requires a new failing test first.

- [ ] **Step 1: Run the full test suite**

Run: `mvn.cmd test`

Expected: all tests pass with zero failures and errors.

- [ ] **Step 2: Build the plugin**

Run: `mvn.cmd clean package`

Expected: `BUILD SUCCESS` and a version-1.0.3 JAR containing the new classes.

- [ ] **Step 3: Check whitespace and the protected dirty checkout**

Run: `git diff --check`

Run: `git status --short`

Confirm no unrelated user file was overwritten, no branch or commit was created, and all new source/test/docs files are intentional.

- [ ] **Step 4: Review behavioral coverage**

Confirm tests distinguish earned zombie wins from survivor abandonment, prove final infected departure wins for survivors, prove `/removeplayer` never re-adds its target, prove command phase gates, and prove there is no unsafe/world-spawn infected respawn fallback.
