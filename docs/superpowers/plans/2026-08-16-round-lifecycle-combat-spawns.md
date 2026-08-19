# Round Lifecycle, Combat, and Spawn Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make round startup, head start, active combat, spawn routing, and multi-batch cleanup deterministic while preventing all attributable survivor friendly fire and keeping infection direct-melee-only.

**Architecture:** `GameManager` remains the public gameplay facade and becomes the only lifecycle owner. Focused pure-Java types define phases, start validation, combat decisions, spawn roles, and unique batching; Bukkit-facing repositories and listeners translate configuration and events into those tested decisions.

**Tech Stack:** Java 21, Paper API 1.21.4, Maven, JUnit Jupiter 5.11.4, Mockito 5.14.2.

## Global Constraints

- Keep the explicit phase names `LOBBY`, `COUNTDOWN`, `HEADSTART`, `ACTIVE`, and `ENDING`.
- Infection is allowed only during `ACTIVE` and only from a direct infected-player melee hit.
- Survivor-to-survivor damage is cancelled for every attributable direct, projectile, or indirect attacker.
- Infected indirect damage is cancelled because zombies are melee-only.
- Preserve the standalone, in-memory event model; do not add persistence or restart recovery.
- Keep `infected-spawn` as the infected holding spawn.
- Migrate legacy `teleports` non-destructively into independent role groups and never delete the legacy section.
- GUI-created spawn points remain config-only and never alter arena blocks.
- Preserve the existing 36-slot main admin GUI layout.
- Do not commit, push, create a branch, or stage files.
- Preserve pre-existing `target/` changes and the untracked `Bugs To fix.md` file.

---

### Task 1: Round and batching primitives

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/RoundPhase.java`
- Create: `src/main/java/me/DaWHeL/infected/UniqueBatchQueue.java`
- Test: `src/test/java/me/DaWHeL/infected/RoundPhaseTest.java`
- Test: `src/test/java/me/DaWHeL/infected/UniqueBatchQueueTest.java`

**Interfaces:**
- Produces: `RoundPhase.isRunning()`, `RoundPhase.canTransitionTo(RoundPhase)`.
- Produces: `UniqueBatchQueue<T>.nextBatch(int)`, `isComplete()`, and `size()`; constructor removes duplicate keys while retaining insertion order.

- [ ] **Step 1: Write failing phase and unique-batch tests**

```java
assertAll(
    () -> assertTrue(RoundPhase.LOBBY.canTransitionTo(RoundPhase.COUNTDOWN)),
    () -> assertTrue(RoundPhase.HEADSTART.canTransitionTo(RoundPhase.ENDING)),
    () -> assertFalse(RoundPhase.ACTIVE.canTransitionTo(RoundPhase.HEADSTART)),
    () -> assertFalse(RoundPhase.LOBBY.isRunning()),
    () -> assertTrue(RoundPhase.ENDING.isRunning())
);

UniqueBatchQueue<String> queue = new UniqueBatchQueue<>(
    List.of("alpha", "bravo", "alpha", "charlie"), value -> value);
assertEquals(List.of("alpha", "bravo"), queue.nextBatch(2));
assertEquals(List.of("charlie"), queue.nextBatch(2));
assertTrue(queue.isComplete());
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `mvn.cmd test '-Dtest=RoundPhaseTest,UniqueBatchQueueTest'`

Expected: test compilation fails because `RoundPhase` and `UniqueBatchQueue` do not exist.

- [ ] **Step 3: Implement the minimal primitives**

```java
public enum RoundPhase {
    LOBBY, COUNTDOWN, HEADSTART, ACTIVE, ENDING;

    public boolean isRunning() {
        return this != LOBBY;
    }

    public boolean canTransitionTo(RoundPhase next) {
        return switch (this) {
            case LOBBY -> next == COUNTDOWN;
            case COUNTDOWN -> next == HEADSTART || next == ENDING;
            case HEADSTART -> next == ACTIVE || next == ENDING;
            case ACTIVE -> next == ENDING;
            case ENDING -> next == LOBBY;
        };
    }
}
```

`UniqueBatchQueue` copies values into a `LinkedHashMap<K,T>`, rejects a batch size below one, advances one cursor, and returns immutable batch copies.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `mvn.cmd test '-Dtest=RoundPhaseTest,UniqueBatchQueueTest'`

Expected: both test classes pass.

- [ ] **Step 5: Inspect the uncommitted task diff**

Run: `git diff --check -- src/main/java/me/DaWHeL/infected/RoundPhase.java src/main/java/me/DaWHeL/infected/UniqueBatchQueue.java src/test/java/me/DaWHeL/infected/RoundPhaseTest.java src/test/java/me/DaWHeL/infected/UniqueBatchQueueTest.java`

Expected: no output; leave files uncommitted.

---

### Task 2: Role-specific spawn repository and legacy migration

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/SpawnRole.java`
- Create: `src/main/java/me/DaWHeL/infected/SpawnRepository.java`
- Test: `src/test/java/me/DaWHeL/infected/SpawnRepositoryTest.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminSetupService.java`
- Modify: `src/test/java/me/DaWHeL/infected/gui/AdminSetupServiceTest.java`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Produces: `SpawnRole.SURVIVOR`, `INFECTED_RELEASE`, `INFECTED_RESPAWN`, each with `configKey()`, `commandKey()`, and `displayName()`.
- Produces: `SpawnRepository.holdingSpawn()`, `points(SpawnRole)`, `loadedLocations(SpawnRole)`, `randomLocation(SpawnRole,Random)`, `savePoint(SpawnRole,String,Location)`, `deletePoint(SpawnRole,String)`, `migrateLegacyTeleports()`.
- Produces: `StoredSpawn` and `NamedSpawn` records using world, x, y, z, yaw, and pitch.
- `AdminSetupService` delegates point storage to `SpawnRepository` and defaults existing no-role methods to `SpawnRole.SURVIVOR`.

- [ ] **Step 1: Write failing migration and role-isolation tests**

```java
config.set("teleports.North.world", "arena");
config.set("teleports.North.x", 4.5);
config.set("spawns.infected-release.North.world", "release-world");

SpawnRepository.MigrationResult result = repository.migrateLegacyTeleports();

assertAll(
    () -> assertTrue(result.migrated()),
    () -> assertEquals("arena", repository.points(SpawnRole.SURVIVOR).getFirst().location().world()),
    () -> assertEquals("release-world", repository.points(SpawnRole.INFECTED_RELEASE).getFirst().location().world()),
    () -> assertEquals("arena", repository.points(SpawnRole.INFECTED_RESPAWN).getFirst().location().world()),
    () -> assertTrue(config.contains("teleports.North")),
    () -> assertTrue(config.getBoolean("migrations.role-spawns"))
);
```

Also assert a second migration is a no-op, malformed legacy entries are reported and skipped, role writes are full-precision and config-only, and stable case-insensitive ordering remains.

- [ ] **Step 2: Run focused repository tests and verify RED**

Run: `mvn.cmd test '-Dtest=SpawnRepositoryTest,AdminSetupServiceTest'`

Expected: compilation fails because the role-aware repository API does not exist.

- [ ] **Step 3: Implement spawn roles, repository, and service delegation**

```java
public enum SpawnRole {
    SURVIVOR("survivor", "survivor", "Survivor"),
    INFECTED_RELEASE("infected-release", "release", "Infected Release"),
    INFECTED_RESPAWN("infected-respawn", "respawn", "Infected Respawn");
}

public List<NamedSpawn> points(SpawnRole role) {
    return readNamedLocations("spawns." + role.configKey());
}
```

Migration copies each valid `teleports.<name>` entry only when the corresponding destination name is absent, retains legacy data, sets `migrations.role-spawns: true`, and calls `plugin.saveConfig()` once after a completed first migration.

`loadedLocations(SpawnRole)` resolves stored world names through `plugin.getServer().getWorld(...)` and drops unavailable worlds. `randomLocation(SpawnRole,Random)` chooses from that resolved list without reading a different role.

Update `config.yml` with empty role sections and a concise migration comment. Do not pre-set the migration marker in the default config, because existing server configurations must execute the one-time copy.

- [ ] **Step 4: Run focused repository/service tests and verify GREEN**

Run: `mvn.cmd test '-Dtest=SpawnRepositoryTest,AdminSetupServiceTest'`

Expected: all repository and setup-service tests pass.

- [ ] **Step 5: Inspect the uncommitted task diff**

Run: `git diff --check -- src/main/java/me/DaWHeL/infected/SpawnRole.java src/main/java/me/DaWHeL/infected/SpawnRepository.java src/main/java/me/DaWHeL/infected/gui/AdminSetupService.java src/test/java/me/DaWHeL/infected/SpawnRepositoryTest.java src/test/java/me/DaWHeL/infected/gui/AdminSetupServiceTest.java src/main/resources/config.yml`

Expected: no output; leave files uncommitted.

---

### Task 3: Start validation

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/RoundStartValidator.java`
- Test: `src/test/java/me/DaWHeL/infected/RoundStartValidatorTest.java`

**Interfaces:**
- Produces: `RoundStartValidator.Input` record with loaded holding/spawn flags, participant and starting-infected counts, batch size, teleport delay ticks, and head-start seconds.
- Produces: `RoundStartValidator.Result` record with immutable `errors()`, `valid()`, and `message()`.

- [ ] **Step 1: Write failing table-driven validation tests**

```java
RoundStartValidator.Result valid = validator.validate(new Input(
    true, EnumSet.allOf(SpawnRole.class), 6, 2, 5, 40, 10));
assertTrue(valid.valid());

RoundStartValidator.Result invalid = validator.validate(new Input(
    false, EnumSet.of(SpawnRole.SURVIVOR), 2, 2, 0, -1, -1));
assertEquals(List.of(
    "Infected holding spawn is missing or its world is not loaded.",
    "Infected release spawns are missing or unavailable.",
    "Infected respawn spawns are missing or unavailable.",
    "Starting infected must be lower than the participant count.",
    "Teleport batch size must be at least 1.",
    "Teleport delay cannot be negative.",
    "Infected release delay cannot be negative."
), invalid.errors());
```

Add separate cases for fewer than two participants and `starting-zombies < 1` so every rejection branch is mutation-sensitive.

- [ ] **Step 2: Run the validator test and verify RED**

Run: `mvn.cmd test -Dtest=RoundStartValidatorTest`

Expected: compilation fails because `RoundStartValidator` does not exist.

- [ ] **Step 3: Implement stable-order validation**

Implement `validate(Input)` as independent checks in the exact order asserted by the test. The `Result` constructor copies the error list; `valid()` returns `errors.isEmpty()` and `message()` joins errors with a single space.

- [ ] **Step 4: Run the validator test and verify GREEN**

Run: `mvn.cmd test -Dtest=RoundStartValidatorTest`

Expected: all validation cases pass.

- [ ] **Step 5: Inspect the uncommitted task diff**

Run: `git diff --check -- src/main/java/me/DaWHeL/infected/RoundStartValidator.java src/test/java/me/DaWHeL/infected/RoundStartValidatorTest.java`

Expected: no output; leave files uncommitted.

---

### Task 4: Unified combat attribution and policy

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/ParticipantRole.java`
- Create: `src/main/java/me/DaWHeL/infected/CombatPolicy.java`
- Create: `src/main/java/me/DaWHeL/infected/DamageAttackerResolver.java`
- Create: `src/main/java/me/DaWHeL/infected/Handlers/ParticipantDamageListener.java`
- Test: `src/test/java/me/DaWHeL/infected/CombatPolicyTest.java`
- Test: `src/test/java/me/DaWHeL/infected/DamageAttackerResolverTest.java`
- Test: `src/test/java/me/DaWHeL/infected/Handlers/ParticipantDamageListenerTest.java`
- Delete: `src/main/java/me/DaWHeL/infected/Handlers/FriendlyFireListener.java`
- Delete: `src/main/java/me/DaWHeL/infected/Handlers/HitHandler.java`

**Interfaces:**
- Produces: `ParticipantRole.NONE`, `SURVIVOR`, `INFECTED`.
- Produces: `CombatPolicy.decide(RoundPhase,ParticipantRole,ParticipantRole,boolean,boolean)` returning `Decision(cancelDamage, infectVictim)`.
- Produces: `DamageAttackerResolver.resolve(EntityDamageEvent)` returning `Optional<Player>`.
- Consumes from `GameManager`: `getPhase()`, `roleOf(Player)`, and `infectPlayer(Player,boolean)`.

- [ ] **Step 1: Write failing combat-policy tests**

```java
assertEquals(new Decision(true, false), policy.decide(
    RoundPhase.ACTIVE, ParticipantRole.SURVIVOR, ParticipantRole.SURVIVOR, false, false));
assertEquals(new Decision(true, false), policy.decide(
    RoundPhase.HEADSTART, ParticipantRole.INFECTED, ParticipantRole.SURVIVOR, true, false));
assertEquals(new Decision(true, false), policy.decide(
    RoundPhase.ACTIVE, ParticipantRole.INFECTED, ParticipantRole.SURVIVOR, false, false));
assertEquals(new Decision(false, true), policy.decide(
    RoundPhase.ACTIVE, ParticipantRole.INFECTED, ParticipantRole.SURVIVOR, true, false));
assertEquals(new Decision(false, false), policy.decide(
    RoundPhase.ACTIVE, ParticipantRole.INFECTED, ParticipantRole.SURVIVOR, true, true));
```

Add cases for infected friendly fire, non-participants, lobby survivors, and every non-active infection phase.

- [ ] **Step 2: Write failing attacker-resolution tests**

Use Mockito to cover a direct player, `Projectile.getShooter()`, `DamageSource.getCausingEntity()`, `DamageSource.getDirectEntity()`, nested projectiles, cyclic entity references, and an unattributable event. Expectations are literal player identities, not mock-call counts.

- [ ] **Step 3: Run policy/resolver tests and verify RED**

Run: `mvn.cmd test '-Dtest=CombatPolicyTest,DamageAttackerResolverTest'`

Expected: compilation fails because policy and resolver types do not exist.

- [ ] **Step 4: Implement policy and resolver**

```java
boolean sameSurvivorTeam = attacker == SURVIVOR && victim == SURVIVOR;
boolean sameInfectedTeam = attacker == INFECTED && victim == INFECTED;
if (sameSurvivorTeam || sameInfectedTeam) return Decision.cancel();
if (attacker == INFECTED && phase != ACTIVE) return Decision.cancel();
if (attacker == INFECTED && !directPlayerDamager) return Decision.cancel();
boolean infect = !alreadyCancelled && phase == ACTIVE
        && attacker == INFECTED && victim == SURVIVOR && directPlayerDamager;
return new Decision(false, infect);
```

The resolver checks the event's by-entity damager first, then the Paper damage source's causing and direct entities. It recognizes `Player` and `Projectile` shooter chains and uses an identity-based visited set to prevent recursion loops.

- [ ] **Step 5: Write the failing unified-listener tests**

Assert observable event cancellation and victim role conversion for: indirect survivor friendly fire, head-start infected melee, active infected projectile damage, active direct melee, and an already-cancelled direct hit.

- [ ] **Step 6: Replace the two old listeners with the unified listener**

Use one `@EventHandler(priority = EventPriority.HIGHEST)` method on `EntityDamageEvent`. A hit is direct melee only when the event is `EntityDamageByEntityEvent`, its direct damager is the same resolved `Player`, and its cause is `ENTITY_ATTACK` or `ENTITY_SWEEP_ATTACK`. Apply cancellation before infection; never infect an already-cancelled event.

- [ ] **Step 7: Run all combat tests and verify GREEN**

Run: `mvn.cmd test '-Dtest=CombatPolicyTest,DamageAttackerResolverTest,ParticipantDamageListenerTest'`

Expected: all combat tests pass.

- [ ] **Step 8: Inspect the uncommitted task diff**

Run: `git diff --check -- src/main/java/me/DaWHeL/infected src/test/java/me/DaWHeL/infected`

Expected: no output; leave files uncommitted.

---

### Task 5: Result-bearing role-specific teleport batches

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/TeleportBatchResult.java`
- Modify: `src/main/java/me/DaWHeL/infected/TeleportManager.java`
- Test: `src/test/java/me/DaWHeL/infected/TeleportBatchResultTest.java`
- Test: `src/test/java/me/DaWHeL/infected/TeleportManagerTest.java`

**Interfaces:**
- Produces: `TeleportBatchResult(attempted,succeeded,failedPlayerIds,error)` with `success()`.
- Produces: `TeleportManager.teleportPlayersBatch(SpawnRole,List<Player>,int,long,Consumer<TeleportBatchResult>)` returning `BukkitTask` or `null` when completion is synchronous.
- Consumes: `SpawnRepository.points(SpawnRole)`.

- [ ] **Step 1: Write failing result and teleport-routing tests**

Assert literal results for all-success, one cancelled teleport, and missing spawn points. Capture teleported locations to prove each role reads its own group. Assert the completion callback runs once for a missing group instead of silently disappearing.

- [ ] **Step 2: Run teleport tests and verify RED**

Run: `mvn.cmd test '-Dtest=TeleportBatchResultTest,TeleportManagerTest'`

Expected: compilation fails because the result-bearing role API does not exist.

- [ ] **Step 3: Implement the role-aware batch contract**

Snapshot players and locations before scheduling. Track attempted online players, successful `Player.teleport` calls, and failed UUIDs. If locations are empty, call completion synchronously with `error = "No <role> spawns are available."`. If the player queue is empty, complete successfully. Validate batch size at the boundary.

Keep the existing 5x5 distribution calculation but read `double` coordinates and retain yaw/pitch from the selected spawn.

- [ ] **Step 4: Run teleport tests and verify GREEN**

Run: `mvn.cmd test '-Dtest=TeleportBatchResultTest,TeleportManagerTest'`

Expected: all teleport tests pass.

- [ ] **Step 5: Inspect the uncommitted task diff**

Run: `git diff --check -- src/main/java/me/DaWHeL/infected/TeleportBatchResult.java src/main/java/me/DaWHeL/infected/TeleportManager.java src/test/java/me/DaWHeL/infected/TeleportBatchResultTest.java src/test/java/me/DaWHeL/infected/TeleportManagerTest.java`

Expected: no output; leave files uncommitted.

---

### Task 6: Central round orchestration and controlled cleanup

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/StartResult.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Test: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Test: `src/test/java/me/DaWHeL/infected/GameManagerCleanupTest.java`

**Interfaces:**
- Produces: `GameManager.getPhase()`, `roleOf(Player)`, `validateStart()`, `startGame()` returning `StartResult`, and `stopGame()` returning `boolean`.
- Produces: `resetPlayerState(Player)` and unique `registerLobbySurvivor(Player)`.
- Consumes: `RoundStartValidator`, `SpawnRepository`, `TeleportManager`, `RoundPhase`, and `UniqueBatchQueue<Player>`.

- [ ] **Step 1: Write failing validation/no-mutation lifecycle tests**

Create a `GameManager` with mocked plugin, server, repository, and teleport manager. Assert invalid start returns the exact validator errors while phase remains `LOBBY`, team lists are unchanged, and no teleport is requested.

- [ ] **Step 2: Write failing phase-order and stale-callback tests**

Capture teleport completion consumers and scheduled release runnables. Assert the observable sequence:

```text
start accepted -> COUNTDOWN
survivor result success -> HEADSTART
release delay runs -> release batch requested
release result success -> ACTIVE
```

After stopping, invoking a captured callback from the invalidated round must not change `ENDING` or the rebuilt later lobby.

- [ ] **Step 3: Run lifecycle tests and verify RED**

Run: `mvn.cmd test -Dtest=GameManagerLifecycleTest`

Expected: compilation or assertions fail because lifecycle ownership remains split.

- [ ] **Step 4: Implement start orchestration and round task ownership**

Inject or construct the repository, teleport manager, validator, and random selector through production and package-private test constructors. Keep one monotonic `roundId` and a collection of `BukkitTask` handles. Each callback begins with:

```java
if (capturedRoundId != roundId || phase != expectedPhase) {
    return;
}
```

`transitionTo` rejects illegal phase changes. Start snapshots unique online lobby participants, selects at least one survivor and infected, registers infected lives, moves infected to holding, begins the survivor batch, and broadcasts one start message.

- [ ] **Step 5: Run lifecycle tests and verify GREEN**

Run: `mvn.cmd test -Dtest=GameManagerLifecycleTest`

Expected: lifecycle tests pass.

- [ ] **Step 6: Write failing multi-batch cleanup tests**

Build a roster containing duplicate UUIDs across survivor, infected, and participant sources. Use a batch size of two and capture reset calls. Assert every UUID appears exactly once, rosters remain unchanged until the last batch, a second stop during `ENDING` returns `false`, and final lobby survivors contain each online UUID once.

- [ ] **Step 7: Implement idempotent ENDING cleanup**

Set `ENDING` before cancelling round tasks. Build a `UniqueBatchQueue<Player>` keyed by UUID. Each cleanup tick calls `resetPlayerState` only for online players. The final tick clears role lists once, rebuilds unique lobby survivors from `Bukkit.getOnlinePlayers()`, clears round-only state, and transitions to `LOBBY`.

`resetPlayerState` contains the existing glow, game-mode, spawn teleport, inventory, armor, and potion cleanup but never adds a survivor. `registerLobbySurvivor` removes an existing UUID before constructing one `Survivor`.

- [ ] **Step 8: Run cleanup and lifecycle tests and verify GREEN**

Run: `mvn.cmd test '-Dtest=GameManagerLifecycleTest,GameManagerCleanupTest'`

Expected: all orchestration and cleanup tests pass.

- [ ] **Step 9: Inspect the uncommitted task diff**

Run: `git diff --check -- src/main/java/me/DaWHeL/infected/GameManager.java src/main/java/me/DaWHeL/infected/StartResult.java src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java src/test/java/me/DaWHeL/infected/GameManagerCleanupTest.java`

Expected: no output; leave files uncommitted.

---

### Task 7: Phase-aware listeners, commands, and plugin wiring

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/InfectedPlugin.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/StartGame.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/StopGame.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/PlayerJoinListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/PlayerQuitListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedDeathListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedRespawnListener.java`
- Create: `src/main/java/me/DaWHeL/infected/Handlers/InfectedContainmentListener.java`
- Test: `src/test/java/me/DaWHeL/infected/Handlers/InfectedContainmentListenerTest.java`
- Modify: `src/main/resources/plugin.yml`

**Interfaces:**
- Consumes the Task 6 `GameManager` lifecycle API.
- `InfectedRespawnListener` consumes `SpawnRepository.randomLocation(SpawnRole.INFECTED_RESPAWN, Random)`.
- `InfectedContainmentListener` consumes `GameManager.isContainedInfected(Player)` and `isRoundTeleportBypass(Player)`.

- [ ] **Step 1: Write failing containment tests**

Assert a contained infected block-coordinate move is cancelled, a yaw/pitch-only move is allowed, a survivor move is allowed, and a round-owned teleport bypass is allowed.

- [ ] **Step 2: Run containment tests and verify RED**

Run: `mvn.cmd test -Dtest=InfectedContainmentListenerTest`

Expected: compilation fails because the listener does not exist.

- [ ] **Step 3: Implement containment and phase-aware listeners**

Register the unified damage and containment listeners. Remove registration of the deleted hit/friendly-fire listeners. Join handling uses unique lobby registration in `LOBBY`, performs no roster mutation in `ENDING`, and preserves infected late joining in the other phases while recording the participant. Quit handling delegates to `GameManager.removeParticipant(Player)` and only triggers win evaluation in `ACTIVE`.

Respawns require `ACTIVE`, retain spectator elimination, and choose only a valid infected-respawn location. The delayed buff callback captures the round identity or verifies the player remains active infected before applying effects.

- [ ] **Step 4: Centralize start/stop command results**

`StartGame` calls `gameManager.startGame()` once and sends rejection reasons to the command sender. It contains no team selection, teleport batching, delay scheduling, or second start broadcast. `StopGame` reports success only when `gameManager.stopGame()` returns true.

- [ ] **Step 5: Wire repository migration before gameplay objects**

In `onEnable`, construct `SpawnRepository`, run `migrateLegacyTeleports()`, then construct `TeleportManager` and `GameManager` with the same repository. Log skipped malformed migration paths. In `onDisable`, call synchronous `gameManager.shutdown()` before server task cancellation.

- [ ] **Step 6: Run listener and command tests**

Run: `mvn.cmd test '-Dtest=*ListenerTest,*CommandTest,GameManagerLifecycleTest,GameManagerCleanupTest'`

Expected: all focused integration-facing unit tests pass.

- [ ] **Step 7: Inspect the uncommitted task diff**

Run: `git diff --check -- src/main/java src/test/java src/main/resources/plugin.yml`

Expected: no output; leave files uncommitted.

---

### Task 8: Role-aware admin GUI and setup status

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminSetupService.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminMenuHolder.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminGuiNavigator.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminGuiManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminEventActions.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/InfectedAdminCommand.java`
- Modify: `src/test/java/me/DaWHeL/infected/gui/AdminSetupServiceTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/gui/AdminGuiManagerTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/gui/AdminEventActionsTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/gui/InfectedAdminCommandTest.java`
- Modify: `src/main/resources/plugin.yml`

**Interfaces:**
- `AdminMenuHolder` carries a `SpawnRole` for teleport menus and deletion confirmations.
- `AdminGuiNavigator.openTeleportPoints(Player,SpawnRole,int)` replaces the shared-point overload; a compatibility default opens survivor points.
- `AdminSetupService.SetupSnapshot` reports holding-spawn state and three role counts.
- `AdminEventActions.start(Player)` consumes `GameManager.startGame()` directly.

- [ ] **Step 1: Write failing admin-service and command tests**

Assert the setup snapshot is ready only when all three role groups, holding spawn, player/count settings, and delay settings pass. Assert both command forms:

```text
/infected gui addteleport Alpha
/infected gui addteleport release Alpha
```

save to survivor and infected-release respectively, while invalid roles return the exact usage string and do not save.

- [ ] **Step 2: Run focused admin tests and verify RED**

Run: `mvn.cmd test '-Dtest=AdminSetupServiceTest,InfectedAdminCommandTest,AdminEventActionsTest'`

Expected: assertions fail because the admin layer still assumes one shared teleport group.

- [ ] **Step 3: Implement role-aware service, command, and action behavior**

Keep no-role `saveTeleportPoint`, `deleteTeleportPoint`, and `teleportPoints` methods as survivor compatibility delegates. Add role overloads. Parse `survivor`, `release`, and `respawn` through `SpawnRole.fromCommandKey(String)`. After a successful role-aware add, reopen the same role page.

`AdminEventActions.start` calls `GameManager.startGame()` and maps its structured result to `ActionResult`, eliminating server command dispatch and duplicated readiness logic.

- [ ] **Step 4: Write failing role-navigation and stale-confirmation tests**

Assert the Teleport Points entry opens a role selector, each selector opens only its group's points, stable slot targets retain both role and name, deletion confirmation cannot delete a same-name point from another role, and the main inventory remains exactly 36 slots with unchanged main control positions.

- [ ] **Step 5: Implement role selector and role-bound point menus**

Add a `TELEPORT_ROLES` menu type. Use three centered controls for Survivor, Infected Release, and Infected Respawn. The 54-slot point menu title and lore identify the selected role. Add/delete/teleport actions carry that role through the holder and confirmation state. Preserve the existing config-only behavior and all stale-action revalidation.

Update setup status to list the three counts independently and show exact validation failures. Status surfaces `gameManager.getPhase().name()` instead of only Running/Stopped.

- [ ] **Step 6: Run all admin GUI tests and verify GREEN**

Run: `mvn.cmd test '-Dtest=AdminSetupServiceTest,InfectedAdminCommandTest,AdminEventActionsTest,AdminGuiManagerTest,AdminGuiLayoutTest,AdminGuiListenerTest,AdminGuiPolicyTest'`

Expected: all admin tests pass and the main-menu slot contract remains unchanged.

- [ ] **Step 7: Inspect the uncommitted task diff**

Run: `git diff --check -- src/main/java/me/DaWHeL/infected/gui src/test/java/me/DaWHeL/infected/gui src/main/resources/plugin.yml`

Expected: no output; leave files uncommitted.

---

### Task 9: Full regression, package, and runtime handoff

**Files:**
- Create: `docs/testing/gamingbarns-damage-attribution-checklist.md`
- Modify only if required by verified failures: files changed in Tasks 1-8.

**Interfaces:**
- No new production API. This task verifies the complete spec and records the live-only GamingBarns check.

- [ ] **Step 1: Write the live GamingBarns checklist**

Record exact server checks for survivor rifle/projectile friendly fire, survivor gun damage to infected, infected gun damage cancellation, infected gun non-infection, direct infected melee infection only in `ACTIVE`, and console diagnostics identifying the Bukkit event damager plus Paper causing/direct entities. State that automated tests cover API-visible attribution while the installed gun build requires this live confirmation.

- [ ] **Step 2: Run the complete automated test suite**

Run: `mvn.cmd test`

Expected: zero failures and zero errors across existing and new tests.

- [ ] **Step 3: Build the distributable plugin**

Run: `mvn.cmd clean package`

Expected: exit code `0` and a shaded plugin JAR under `target/` using the existing project version.

- [ ] **Step 4: Check source quality and exact working-tree scope**

Run: `git diff --check`

Run: `git status --short`

Expected: no whitespace errors; intended source, test, resource, spec, plan, and checklist files are visible, while the pre-existing generated `target/` changes and `Bugs To fix.md` remain preserved and unstaged.

- [ ] **Step 5: Review every acceptance criterion against evidence**

Map each design acceptance criterion to a named automated test or the GamingBarns live checklist. Report any live-only item explicitly instead of claiming it was server-verified locally.

- [ ] **Step 6: Leave the completed work uncommitted**

Do not stage, commit, push, or create a branch. Report the final test count, package result, intended changed files, and the path of the produced JAR.
