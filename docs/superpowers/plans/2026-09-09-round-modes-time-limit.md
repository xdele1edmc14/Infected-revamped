# Round Modes and Time Limit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add lobby-selectable Time Limit and Deathmatch rules without cluttering the admin GUI.

**Architecture:** Add pure `RoundMode` and immutable `RoundRules` domain types, then let `GameManager` snapshot the selected rules at round start. Reuse the manager's guarded round-task lifecycle for a one-second timer and expose mode/time values to the existing GUI and configurable scoreboard.

**Tech Stack:** Java 21, Paper API, Bukkit scheduler abstraction, JUnit 5, Mockito, Maven.

## Global Constraints

- Time Limit forces exactly one infected life and awards survivors when active time expires.
- Deathmatch has no timer and uses its own configured infected-life count.
- Both modes have independent configured starting-zombie counts.
- The GUI adds only one mode control and permits changes only in the lobby.
- Preserve all unrelated dirty work and do not commit.

---

### Task 1: Immutable mode rules

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/RoundMode.java`
- Create: `src/main/java/me/DaWHeL/infected/RoundRules.java`
- Create: `src/test/java/me/DaWHeL/infected/RoundRulesTest.java`

**Interfaces:**
- Produces: `RoundMode.next()`, `RoundMode.parse(String)`, and `RoundRules.from(ConfigurationSection, RoundMode)`.

- [ ] Write tests proving Time Limit forces one life, Deathmatch reads its lives, each mode reads its own starting count, legacy paths are fallbacks, and bad numeric values are clamped safely.
- [ ] Run `mvn.cmd test '-Dtest=RoundRulesTest'` and verify the missing types fail compilation.
- [ ] Implement the two domain types with no Bukkit runtime side effects.
- [ ] Re-run the focused test and verify it passes.

### Task 2: Lifecycle and timer

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Consumes: `RoundRules.from(...)`.
- Produces: `selectedRoundMode()`, `cycleRoundMode()`, `roundModeDisplayName()`, `roundTimeRemainingSeconds()`, and mode-aware `configuredInfectedLives()` / starting rules.

- [ ] Add lifecycle tests proving lobby-only cycling, per-mode starting infected/lives, active-only timer countdown, timeout survivor victory, and stale timer rejection.
- [ ] Run the focused lifecycle tests and verify the new assertions fail for missing behavior.
- [ ] Snapshot rules before start validation, use the snapshot for team assignment/lives, schedule a guarded one-second countdown on `ACTIVE`, and announce a dedicated timeout survivor victory.
- [ ] Add nested mode configuration plus timeout message defaults while keeping legacy fallbacks in code.
- [ ] Re-run lifecycle tests and verify they pass.

### Task 3: Compact GUI and scoreboard visibility

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminGuiLayout.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/AdminGuiManager.java`
- Modify: `src/test/java/me/DaWHeL/infected/gui/AdminGuiManagerTest.java`
- Modify: `src/main/java/me/DaWHeL/infected/ScoreboardTemplate.java`
- Modify: `src/main/java/me/DaWHeL/infected/ScoreboardManager.java`
- Modify: `src/test/java/me/DaWHeL/infected/ScoreboardTemplateTest.java`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Consumes: the read-only and cycle methods exposed by `GameManager`.
- Produces: one lobby mode button plus `{round_mode}` and `{time_remaining}` scoreboard placeholders.

- [ ] Add GUI tests proving the mode slot cycles in lobby and reports rejection during a round; add placeholder tests with literal expected values.
- [ ] Run focused GUI/template tests and verify expected failures.
- [ ] Add the single mode item and handler, include the selected mode in start confirmation state, and pass mode/time through scoreboard state.
- [ ] Re-run focused tests and verify they pass.

### Task 4: Full verification

**Files:**
- Verify all intended files above; modify only to fix failures caused by this feature.

- [ ] Run `mvn.cmd clean package` and require zero failures/errors.
- [ ] Run `git diff --check` and require no whitespace errors.
- [ ] Review `git status --short` and the scoped diff to confirm unrelated dirty work remains intact and no commit was created.

### Task 5: Time boss bar and zombie tracking compass

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/RoundTimeBossBar.java`
- Create: `src/main/java/me/DaWHeL/infected/TrackingCompass.java`
- Create: `src/test/java/me/DaWHeL/infected/RoundTimeBossBarTest.java`
- Create: `src/test/java/me/DaWHeL/infected/TrackingCompassTest.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/RoundRules.java`
- Modify: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/RoundRulesTest.java`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- `RoundTimeBossBar.update(int remainingSeconds, int totalSeconds, Iterable<Player> viewers)` updates title/progress and reconciles viewers; `close()` removes it.
- `TrackingCompass.ensurePresent(Player infected)` gives each zombie one compass; the active-round tracker points it at the nearest online survivor in the same world once per second.

- [ ] Add failing tests for boss-bar fraction/title/viewer cleanup, compass provisioning, nearest-survivor targeting, and tracker cleanup.
- [ ] Run focused tests and require failures caused by missing behavior.
- [ ] Start one compass tracker during active play and cancel it before round cleanup and shutdown.
- [ ] Keep the infected inventory lock and death-drop clearing authoritative so the compass cannot be removed.
- [ ] Run focused tests, then `mvn.cmd clean package` and `git diff --check`.
