# Outdoor Generated Weapon Chests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate, identify, fill, empty, and safely remove outdoor weapon-chest platforms from the existing administration wizard.

**Architecture:** Keep loot selection behind the existing loot module while adding a separate generated-layout module. A shared operation gate serializes all world mutations; pure Poisson selection and narrowly injected world/repository seams keep the risky rules testable.

**Tech Stack:** Java 21, Paper API 1.21.4, Bukkit YAML/PDC, JUnit 5, Mockito, Maven.

## Global Constraints

- Work in the existing dirty checkout without discarding unrelated user changes.
- One chest sits above the center of nine level gold blocks.
- Only outdoor, flat, solid, unobstructed sites qualify.
- Loot targets tagged or exact-pattern chests; physical removal targets registry-owned placements only.
- Generate, remove, fill, and empty remain separate GUI actions and run only in the lobby.

---

### Task 1: Configuration and Ownership Records

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/loot/WeaponLootCatalog.java`
- Modify: `src/main/java/me/DaWHeL/infected/loot/WeaponLootRepository.java`
- Create: `src/main/java/me/DaWHeL/infected/loot/GeneratedChestPlacement.java`
- Create: `src/main/java/me/DaWHeL/infected/loot/GeneratedChestRepository.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/WeaponLootRepositoryTest.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/GeneratedChestRepositoryTest.java`

**Interfaces:**
- Produces: `Settings.generatedChestCount()`, `WeaponLootRepository.setGeneratedChestCount(int)`, and atomic placement registry CRUD.

- [ ] Write failing persistence and validation tests for count and placement snapshots.
- [ ] Run the focused tests and confirm failures are due to missing interfaces.
- [ ] Add the count field and atomic generated-placement YAML repository.
- [ ] Run the focused tests to green.

### Task 2: Eligibility and Poisson Planning

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/loot/ChestEligibility.java`
- Create: `src/main/java/me/DaWHeL/infected/loot/PoissonDiscPlacement.java`
- Modify: `src/main/java/me/DaWHeL/infected/loot/ChestDiscoveryService.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/ChestEligibilityTest.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/PoissonDiscPlacementTest.java`

**Interfaces:**
- Consumes: a namespaced ownership key and valid X/Z candidates.
- Produces: one centralized eligibility predicate and deterministic adaptive Poisson acceptance.

- [ ] Write failing tests proving exact-pattern and marker eligibility while rejecting ordinary chests.
- [ ] Write failing tests proving deterministic non-overlapping Poisson selections and bounded failure.
- [ ] Run focused tests to verify red.
- [ ] Implement the pure rules and connect discovery to eligibility.
- [ ] Run focused tests to green.

### Task 3: Outdoor Validation and Shared Operation Gate

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/loot/OutdoorChestSiteValidator.java`
- Create: `src/main/java/me/DaWHeL/infected/loot/ChestOperationGate.java`
- Modify: `src/main/java/me/DaWHeL/infected/loot/WeaponChestService.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/OutdoorChestSiteValidatorTest.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/ChestOperationGateTest.java`

**Interfaces:**
- Produces: `Optional<Site> validate(World, ChestRegion, int, int)` and `Lease tryAcquire()`.

- [ ] Write failing tests for level outdoor support, roof-depth rejection, clearance, and mutual exclusion.
- [ ] Run focused tests to verify red.
- [ ] Implement validator and gate; inject the shared gate into loot actions.
- [ ] Run focused tests to green.

### Task 4: Tick-Batched Generate and Remove Operations

**Files:**
- Create: `src/main/java/me/DaWHeL/infected/loot/GeneratedChestService.java`
- Create: `src/main/java/me/DaWHeL/infected/loot/StreamedGeneratedChestOperation.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/GeneratedChestServiceTest.java`
- Test: `src/test/java/me/DaWHeL/infected/loot/StreamedGeneratedChestOperationTest.java`

**Interfaces:**
- Consumes: selected region, configured count, validator, placement registry, ownership key, and shared gate.
- Produces: preview plus asynchronous `GENERATE` and `REMOVE` actions with structured results.

- [ ] Write failing tests for plan-before-mutation, exact structure geometry, ownership tagging, restoration, conflict preservation, and failure cleanup.
- [ ] Run focused tests to verify red.
- [ ] Implement bounded planning and mutation stages.
- [ ] Run focused tests to green.

### Task 5: Wizard Integration and Wiring

**Files:**
- Modify: `src/main/java/me/DaWHeL/infected/gui/weapon/WeaponMenuHolder.java`
- Modify: `src/main/java/me/DaWHeL/infected/gui/weapon/WeaponLootGuiManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/InfectedPlugin.java`
- Test: `src/test/java/me/DaWHeL/infected/gui/weapon/WeaponLootGuiManagerTest.java`

**Interfaces:**
- Consumes: both chest mutation modules and count repository setting.
- Produces: count control and separate confirmed generate, fill, empty, and remove actions.

- [ ] Write failing GUI routing, count-step, stale-confirmation, and completion-report tests.
- [ ] Run the focused tests to verify red.
- [ ] Add wizard controls, confirmations, action routing, and plugin wiring.
- [ ] Run focused tests to green.

### Task 6: Full Verification

**Files:**
- Modify as required by integration failures only.

- [ ] Run `mvn.cmd clean package` and fix only demonstrated regressions.
- [ ] Run `git diff --check`.
- [ ] Review the final diff for unrelated changes and document the required live Paper checks.
