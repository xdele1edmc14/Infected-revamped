# Finite Infected Lives Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add configurable infected lives, spectator elimination, and a survivor victory when no infected players remain.

**Architecture:** `GameManager` owns a round-local, Bukkit-independent tracker for remaining lives and eliminated players. The death and respawn listeners ask it for the game decision, keeping role membership, life count, and the win condition consistent.

**Tech Stack:** Java 21, Paper 1.21 API, Maven, JUnit 5.

## Global Constraints

- `settings.infected-lives` is the total number of deaths an infected player can take before elimination; its minimum is `1`.
- A final death removes the player from the active infected roster and sets spectator mode after their respawn; no cage teleport is used.
- No active survivors is an infected win; no active infected is a survivor win.
- Round-local life and elimination state is cleared on every `stopGame()`.
- Preserve the user-owned `Bugs To fix.md`.

---

### Task 1: Add isolated life and outcome rules with automated tests

**Files:**

- Create: `src/main/java/me/DaWHeL/infected/InfectedLifeTracker.java`
- Create: `src/main/java/me/DaWHeL/infected/RoundWinner.java`
- Create: `src/main/java/me/DaWHeL/infected/RoundWinCondition.java`
- Create: `src/test/java/me/DaWHeL/infected/InfectedLifeTrackerTest.java`
- Create: `src/test/java/me/DaWHeL/infected/RoundWinConditionTest.java`
- Modify: `pom.xml`

**Interfaces:**

- Produces `register(UUID, int)`, `consumeLife(UUID)`, `isEliminated(UUID)`, and `clear()` on `InfectedLifeTracker`.
- Produces `Optional<RoundWinner> RoundWinCondition.determine(int survivors, int infected)`.

- [ ] **Step 1: Write failing tests and add JUnit 5 dependencies**

Add test-scoped `org.junit.jupiter:junit-jupiter:5.11.4` and Surefire `3.5.2` to `pom.xml`. Write a test that registers three lives and requires the first two deaths to return `true`, then the third to return `false` and mark the player eliminated. Write a test that one life eliminates on first death. Write resolver tests for survivor win, infected win, and no winner while both teams remain.

```java
assertTrue(tracker.consumeLife(playerId));
assertTrue(tracker.consumeLife(playerId));
assertFalse(tracker.consumeLife(playerId));
assertTrue(tracker.isEliminated(playerId));
assertEquals(Optional.of(RoundWinner.SURVIVORS), RoundWinCondition.determine(2, 0));
```

- [ ] **Step 2: Run the tracker test in red state**

Run: `mvn test -Dtest=InfectedLifeTrackerTest`

Expected: compilation failure because `InfectedLifeTracker` has not been implemented.

- [ ] **Step 3: Implement minimal pure Java rules**

Use `Map<UUID, Integer>` for remaining lives and `Set<UUID>` for eliminated players. `register` rejects life counts below one and replaces existing player state. `consumeLife` decrements and returns `true` while more than one remains; otherwise it removes remaining state, marks elimination, and returns `false`. `RoundWinCondition` returns infected for zero survivors, survivors for zero infected, otherwise empty.

```java
if (remaining > 1) {
    remainingLives.put(playerId, remaining - 1);
    return true;
}
remainingLives.remove(playerId);
eliminatedPlayers.add(playerId);
return false;
```

- [ ] **Step 4: Verify focused tests and commit**

Run: `mvn test -Dtest=InfectedLifeTrackerTest,RoundWinConditionTest`

Expected: PASS.

Run: `git add pom.xml src/main/java/me/DaWHeL/infected/InfectedLifeTracker.java src/main/java/me/DaWHeL/infected/RoundWinner.java src/main/java/me/DaWHeL/infected/RoundWinCondition.java src/test/java/me/DaWHeL/infected/InfectedLifeTrackerTest.java src/test/java/me/DaWHeL/infected/RoundWinConditionTest.java && git commit -m "feat: add finite infected life tracking"`

### Task 2: Connect the rules to active roles and round outcome

**Files:**

- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/resources/config.yml`

**Interfaces:**

- Consumes the tracker and outcome classes from Task 1.
- Produces `boolean handleInfectedDeath(Player)` and `boolean isEliminatedInfected(Player)` for listener use.

- [ ] **Step 1: Register configured lives when a player becomes infected**

Add `settings.infected-lives: 3` to `config.yml`. In `GameManager.infectPlayer`, remove a prior role for that player, add the new infected role, and register `Math.max(1, plugin.getConfig().getInt("settings.infected-lives", 3))`. Clear tracker state at the beginning and end of a round.

- [ ] **Step 2: Consume final lives and select either winner**

`handleInfectedDeath` consumes one life. On final death it removes the player only from the active infected roster, retains the eliminated marker, invokes `checkWin()`, and returns `false`; otherwise return `true`. Replace the one-sided win check with `RoundWinCondition` and add `messages.all-survivors.chat`, `.title`, and `.subtitle` to the config. Keep existing all-infected fallbacks and sound.

```java
RoundWinCondition.determine(survivors.size(), infected.size()).ifPresent(winner -> {
    announceWinner(winner);
    stopGame();
});
```

- [ ] **Step 3: Verify all unit tests and commit**

Run: `mvn test`

Expected: PASS.

Run: `git add src/main/java/me/DaWHeL/infected/GameManager.java src/main/resources/config.yml && git commit -m "feat: add survivor win condition"`

### Task 3: Apply death and spectator behavior

**Files:**

- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedDeathListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedRespawnListener.java`

**Interfaces:**

- Consumes `GameManager.handleInfectedDeath(Player)` and `GameManager.isEliminatedInfected(Player)`.

- [ ] **Step 1: Delegate infected death to `GameManager`**

Retain the current drop and XP suppression, then call `handleInfectedDeath(player)` after confirming the dead player is active infected.

- [ ] **Step 2: Make an eliminated player a spectator**

At the start of `onPlayerRespawn`, check `isEliminatedInfected(player)`. If true, schedule `player.setGameMode(GameMode.SPECTATOR)` on the next server task only when the round is still running, then return. Only active infected continue through the existing arena teleport, zombie equipment, effects, and message flow.

- [ ] **Step 3: Package and commit**

Run: `mvn clean package`

Expected: `BUILD SUCCESS`.

Run: `git add src/main/java/me/DaWHeL/infected/Handlers/InfectedDeathListener.java src/main/java/me/DaWHeL/infected/Handlers/InfectedRespawnListener.java && git commit -m "feat: spectate eliminated infected"`
