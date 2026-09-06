# Remove Jump Feather Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove every live Jump Feather integration so the plugin neither creates nor gives special behavior to feathers.

**Architecture:** Remove the feature at its composition root, command metadata, help surface, game-state owner, and event implementation. Preserve historical documents and unrelated worktree changes; ordinary Minecraft feathers remain ordinary items.

**Tech Stack:** Java 21, Paper API 1.21.4, Maven, JUnit 5, Mockito

## Global Constraints

- No participant, including infected players, receives or uses a Jump Feather after this change.
- Introduce no replacement mechanic or configuration toggle.
- Preserve historical design and implementation documents.
- Preserve all unrelated uncommitted work already present in the repository.

---

### Task 1: Remove the Jump Feather runtime and administrator surface

**Files:**
- Modify: `src/test/java/me/DaWHeL/infected/gui/PluginMetadataTest.java`
- Create: `src/test/java/me/DaWHeL/infected/commands/HelpInfectedCommandTest.java`
- Modify: `src/test/java/me/DaWHeL/infected/GameManagerLifecycleTest.java`
- Modify: `src/main/java/me/DaWHeL/infected/GameManager.java`
- Modify: `src/main/java/me/DaWHeL/infected/InfectedPlugin.java`
- Modify: `src/main/java/me/DaWHeL/infected/commands/HelpInfectedCommand.java`
- Modify: `src/main/resources/plugin.yml`
- Delete: `src/main/java/me/DaWHeL/infected/Handlers/JumpFeatherListener.java`
- Delete: `src/main/java/me/DaWHeL/infected/commands/GiveFeather.java`

**Interfaces:**
- Consumes: Existing Bukkit plugin startup, `GameManager` round lifecycle, and command metadata.
- Produces: A plugin with no `givefeather` command, Jump Feather listener, distribution scheduler, cooldown API, or help entry.

- [ ] **Step 1: Add failing administrator-surface regression tests**

Add this assertion to `PluginMetadataTest.declaresAdminGuiCommandWithoutRemovingLegacyCommands()`:

```java
() -> assertFalse(metadata.isConfigurationSection("commands.givefeather"))
```

Create `HelpInfectedCommandTest` using a mocked `CommandSender`, capture all `sendMessage(String)` arguments, and assert that none contains `givefeather` or `Jump Feather`:

```java
@Test
void helpDoesNotAdvertiseJumpFeather() {
    CommandSender sender = mock(CommandSender.class);

    new HelpInfectedCommand().onCommand(
            sender, mock(Command.class), "helpinfected", new String[0]);

    ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
    verify(sender, atLeastOnce()).sendMessage(messages.capture());
    assertTrue(messages.getAllValues().stream()
            .noneMatch(message -> message.toLowerCase().contains("givefeather")
                    || message.toLowerCase().contains("jump feather")));
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```powershell
mvn.cmd test '-Dtest=PluginMetadataTest,HelpInfectedCommandTest'
```

Expected: both new assertions fail because `plugin.yml` and the help output still advertise `/givefeather`.

- [ ] **Step 3: Remove the minimal administrator-facing references**

Delete the `givefeather` command block from `src/main/resources/plugin.yml`, delete the `/givefeather` message from `HelpInfectedCommand`, and remove `getCommand("givefeather").setExecutor(new GiveFeather());` from `InfectedPlugin`.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run:

```powershell
mvn.cmd test '-Dtest=PluginMetadataTest,HelpInfectedCommandTest'
```

Expected: PASS with zero failures.

- [ ] **Step 5: Remove the runtime implementation and state**

Delete `JumpFeatherListener.java` and `GiveFeather.java`. In `InfectedPlugin`, delete Jump Feather listener registration and `gameManager.startFeatherTask()`. In `GameManager`, delete `featherCooldown`, every clear/remove operation on it, `startFeatherTask()`, `setFeatherCooldown(...)`, and `isOnFeatherCooldown(...)`; remove imports that become unused. Delete `GameManagerLifecycleTest.featherTaskInspectsOnlyActiveParticipantsAndStopsDuringEnding()` because the scheduled behavior no longer exists.

- [ ] **Step 6: Verify no live references remain**

Run:

```powershell
rg -n -i "JumpFeather|Jump Feather|givefeather|featherCooldown|startFeatherTask|setFeatherCooldown|isOnFeatherCooldown" src/main src/test
```

Expected: no matches. Historical references under `docs/` are intentionally retained.

- [ ] **Step 7: Run focused lifecycle and metadata tests**

Run:

```powershell
mvn.cmd test '-Dtest=GameManagerLifecycleTest,PluginMetadataTest,HelpInfectedCommandTest'
```

Expected: PASS with zero failures.

- [ ] **Step 8: Run the complete verification build**

Run:

```powershell
mvn.cmd clean package
```

Expected: BUILD SUCCESS with all tests passing and shaded plugin artifacts created under `target/`.

- [ ] **Step 9: Review the feature-only diff and commit**

Review only the listed source/test/resource files with `git diff --check` and `git diff`. Stage only those paths, leaving unrelated changes unstaged, then commit:

```powershell
git commit -m "refactor: remove Jump Feather feature"
```

