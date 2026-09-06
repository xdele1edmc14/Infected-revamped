# Configurable Scoreboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved role-aware, configurable Outbreak scoreboard with MiniMessage support and round-local player statistics.

**Architecture:** `RoundStatsTracker` owns Bukkit-independent counters, while `GameManager` validates and exposes gameplay updates. `ScoreboardBar` and `ScoreboardTextRenderer` handle pure presentation logic; `ScoreboardManager` selects configured role layouts, supplies placeholders, and renders Adventure components through scoreboard teams.

**Tech Stack:** Java 21, Paper 1.21.4, Adventure MiniMessage 4.20, JUnit 5, Mockito

## Global Constraints

- Do not hardcode scoreboard copy or layout in Java; use `config.yml`.
- Support MiniMessage and existing ampersand-formatted strings.
- Preserve unrelated dirty-worktree changes.

---

### Task 1: Round statistics and combat attribution

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/RoundStatsTracker.java`
- Create: `src/test/java/me/DaWHeL/infected/RoundStatsTrackerTest.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/ParticipantDamageListener.java`
- Modify: `src/main/java/me/DaWHeL/infected/Handlers/InfectedDeathListener.java`
- Modify: matching listener and lifecycle tests

**Interfaces:**
- Produces: `recordSurvivorKill(Player)`, `recordInfection(Player)`, `kills(Player)`, `infections(Player)`, `remainingInfectedLives(Player)`, and `configuredInfectedLives()` on `GameManager`.

- [ ] Write tests proving counters increment independently and clear between rounds.
- [ ] Run the focused tests and confirm they fail because the behavior is absent.
- [ ] Implement the tracker and hook infection/death attribution into existing listeners.
- [ ] Run focused tests and the lifecycle suite.

### Task 2: Presentation primitives

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/ScoreboardBar.java`
- Create: `src/main/java/me/DaWHeL/infected/ScoreboardTextRenderer.java`
- Create: corresponding unit tests

**Interfaces:**
- Produces: `ScoreboardBar.build(int, int, int, String, String, String)` returning configured markup, and `ScoreboardTextRenderer.render(String, Map<String,String>)` returning an Adventure `Component`.

- [ ] Write tests for proportional, minimum-visible, and empty bars.
- [ ] Write tests for MiniMessage, legacy ampersand text, and placeholder substitution.
- [ ] Run focused tests and confirm the missing behavior fails.
- [ ] Implement the minimum pure presentation classes and rerun the tests.

### Task 3: Configurable sidebar integration

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/ScoreboardManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/InfectedPlugin.java`
- Modify: `src/main/resources/config.yml`
- Test: `src/test/java/me/DaWHeL/infected/ScoreboardManagerTest.java`

**Interfaces:**
- Consumes: the GameManager statistic accessors and presentation primitives from Tasks 1 and 2.
- Produces: role-specific configured sidebar layouts with all approved placeholders.

- [ ] Write a test proving role layouts and placeholders are selected without embedded display copy.
- [ ] Run it and confirm the old scoreboard cannot satisfy it.
- [ ] Replace entry-text rows with unique team rows and Adventure component prefixes.
- [ ] Add default MiniMessage layouts, configurable bar/hearts/role names, and refresh interval.
- [ ] Run all tests and `mvn clean package`; inspect the final diff for unrelated changes.
