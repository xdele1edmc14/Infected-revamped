# Faithful Royal Crown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and export a normally sized, cube-only royal crown in a separate live Blockbench project.

**Architecture:** Create a fresh Java Block/Item project and organize all geometry beneath one `Crown` root with five functional child groups. Use solid-color textures and economical silhouette geometry, then verify project identity, hierarchy, element types, dimensions, visual appearance, and native project export.

**Tech Stack:** Blockbench MCP, Java Block/Item model format, editable `.bbmodel` export.

## Global Constraints

- Do not modify the active `stone_pedestal - Converted` project.
- The band footprint is 16 by 16 units; total height is 12 units; raised gems may project at most 1 unit beyond the band.
- Use cubes only—no meshes, cylinders, or spheres.
- Match the reference with gold trim, a recessed dark-red cap, four rising arch supports, a centered cross, and alternating red, blue, and green diamond jewels.
- Keep geometry compact; surface color variation does not justify extra cubes.
- Leave repository changes uncommitted.

---

### Task 1: Create and Confirm the Isolated Project

**Files:**
- Create: live Blockbench project `faithful_royal_crown`

**Interfaces:**
- Consumes: approved crown design and Blockbench MCP project APIs.
- Produces: confirmed `java_block` project UUID used by every later task.

- [ ] Create `faithful_royal_crown` with format `java_block`.
- [ ] Immediately call `get_project_info` and require the exact project name, new UUID, `java_block` format, and zero geometry before continuing.
- [ ] Save checkpoint `faithful_royal_crown_empty`.

### Task 2: Create Palette and Hierarchy

**Files:**
- Modify: live Blockbench project `faithful_royal_crown`

**Interfaces:**
- Consumes: confirmed project UUID from Task 1.
- Produces: solid textures `gold`, `gold_light`, `gold_dark`, `velvet_red`, `ruby`, `sapphire`, and `emerald`; groups `Crown/Band`, `Crown/Inner_Cap`, `Crown/Arches`, `Crown/Cross`, and `Crown/Jewels`.

- [ ] Reconfirm the active project UUID before mutation.
- [ ] Create seven 16-by-16 solid-color textures using `#D39B16`, `#F1C84B`, `#8A5A0A`, `#671326`, `#B51E2E`, `#1769B0`, and `#239B36`.
- [ ] Create the `Crown` root at `[8, 0, 8]` and its five named child groups at `[8, 0, 8]` with zero rotation.
- [ ] Inspect the group-only outline and require exactly one root with those five children.

### Task 3: Build the Crown Body

**Files:**
- Modify: live Blockbench project `faithful_royal_crown`

**Interfaces:**
- Consumes: `Band` and `Inner_Cap` groups plus gold and velvet textures.
- Produces: a hollow-looking 16-by-16 crown band and recessed red cap.

- [ ] Place four lower trim rails spanning the front, back, left, and right perimeter from y=0 to y=1.
- [ ] Place four central gold band walls from y=1 to y=3 using 1-unit perimeter thickness.
- [ ] Place four upper trim rails from y=3 to y=4, with `gold_light` on upper trim and `gold_dark` on small corner accents.
- [ ] Place a recessed cap from x=2 to 14, z=2 to 14, and y=3.15 to 4.65 using `velvet_red`, leaving the gold rim readable.
- [ ] Recheck project identity and require zero meshes.

### Task 4: Add Arches, Cross, and Jewels

**Files:**
- Modify: live Blockbench project `faithful_royal_crown`

**Interfaces:**
- Consumes: body from Task 3 and `Arches`, `Cross`, and `Jewels` groups.
- Produces: the reference-defining royal silhouette and colored decoration.

- [ ] Build front, back, left, and right rising supports from two thin gold cubes each; use only single-axis 22.5-degree rotations and meet beneath `[8, 8, 8]`.
- [ ] Build the centered cross with a vertical gold post from y=7.5 to 12 and a horizontal arm from x=6 to 10 at y=10 to 11.25.
- [ ] Place five front and five back 1.75-unit diamond jewels, rotated 45 degrees around Z, in the sequence ruby, sapphire, emerald, sapphire, ruby.
- [ ] Place three jewels on each side, rotated 45 degrees around X, in the sequence ruby, sapphire, emerald.
- [ ] Ensure gems project no more than 1 unit beyond the band and no geometry exceeds y=12.

### Task 5: Verify and Export

**Files:**
- Create: `outputs/faithful_royal_crown.bbmodel`

**Interfaces:**
- Consumes: completed live Blockbench model.
- Produces: verified editable model export and visual evidence.

- [ ] Confirm `get_project_info` reports `faithful_royal_crown`, format `java_block`, zero meshes, seven textures, and one `Crown` root.
- [ ] Inspect the full outline for the five required child groups and named body, arch, cross, and gem cubes.
- [ ] Query available export formats and export with codec `project` to the absolute `outputs/faithful_royal_crown.bbmodel` path.
- [ ] Parse the returned project JSON and verify cube-only elements, finite positive dimensions, unique UUID ownership, the required hierarchy, and bounds no larger than x=-1..17, y=0..12, z=-1..17.
- [ ] Save checkpoint `faithful_royal_crown_final_verified`.
- [ ] Capture a three-quarter Blockbench screenshot and confirm the gold band, red cap, four supports, cross, and three gem colors are all visibly distinct.
