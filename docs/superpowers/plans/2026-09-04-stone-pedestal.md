# Stone Pedestal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved untextured, cube-only dark-fantasy pedestal in the dedicated Blockbench project `stone_pedestal`.

**Architecture:** Construct one `Pedestal` root with named structural subgroups. Build the large silhouette first, add four-sided architectural relief second, then perform structural and screenshot validation.

**Tech Stack:** Blockbench Generic Model, Blockbench MCP tools

## Global Constraints

- Work only in the live project named `stone_pedestal` with UUID `e9ba5103-d2c4-7452-11da-f8157173e573`.
- Use cubes only; create no meshes.
- Keep all geometry within X/Z `-16..16` and Y `0..20`.
- Create no textures, materials, painting, crown, floating object, vines, moss, foliage, rubble, lighting, particles, or animation.
- Leave repository changes uncommitted.

---

### Task 1: Validate and prepare the live project

**Files:**
- Reference: `docs/superpowers/specs/2026-09-04-stone-pedestal-design.md`

**Interfaces:**
- Consumes: active Blockbench project state
- Produces: verified empty target and undo checkpoint `before_pedestal_geometry`

- [ ] **Step 1:** Call `get_project_info` and confirm the project name, UUID, format, and zero element counts.
- [ ] **Step 2:** Call `list_outline` and confirm there are no existing roots.
- [ ] **Step 3:** Call `save_checkpoint` with name `before_pedestal_geometry`.

### Task 2: Build the hierarchy and primary silhouette

**Files:**
- Modify: live Blockbench project `crown`

**Interfaces:**
- Consumes: empty validated project
- Produces: `Pedestal` hierarchy and primary 20-unit-tall silhouette

- [ ] **Step 1:** Create `Pedestal` at `[0,0,0]`, then create `Foundation`, `Lower_Platform`, `Main_Body`, `Cornice`, and `Capstone` beneath it.
- [ ] **Step 2:** Create `Corner_Pillars`, `Wall_Panels`, and `Relief_Carvings` beneath `Main_Body`; create `Lintels` and `Dentils` beneath `Cornice`.
- [ ] **Step 3:** Add the primary cubes:
  - `foundation_slab`: `[-16,0,-16]` to `[16,2,16]`
  - `foundation_step`: `[-14,2,-14]` to `[14,4,14]`
  - `lower_plinth`: `[-12,4,-12]` to `[12,6,12]`
  - `shrine_core`: `[-9,6,-9]` to `[9,15,9]`
  - `lower_cornice`: `[-12,15,-12]` to `[12,16,12]`
  - `upper_cornice`: `[-13.5,16,-13.5]` to `[13.5,18,13.5]`
  - `capstone`: `[-15,18,-15]` to `[15,20,15]`
- [ ] **Step 4:** Add four corner pillars from Y `6` to `15`, using X intervals `[-11,-8]` and `[8,11]` crossed with Z intervals `[-11,-8]` and `[8,11]`.
- [ ] **Step 5:** Call `get_project_info`; expect 11 cubes, 11 groups, 0 meshes, and 0 textures.

### Task 3: Add recessed panels and lintels

**Files:**
- Modify: live Blockbench project `crown`

**Interfaces:**
- Consumes: primary pedestal silhouette
- Produces: four framed architectural faces

- [ ] **Step 1:** Add four panel backings:
  - front: `[-8,7,-10]` to `[8,14,-9]`
  - back: `[-8,7,9]` to `[8,14,10]`
  - left: `[-10,7,-8]` to `[-9,14,8]`
  - right: `[9,7,-8]` to `[10,14,8]`
- [ ] **Step 2:** Add four lintels:
  - front: `[-9,14,-11]` to `[9,15,-9]`
  - back: `[-9,14,9]` to `[9,15,11]`
  - left: `[-11,14,-9]` to `[-9,15,9]`
  - right: `[9,14,-9]` to `[11,15,9]`
- [ ] **Step 3:** Add three front border strips at Z `-10.5..-9.5`: vertical strips X `-8..-7` and `7..8` at Y `7..14`, plus a bottom strip X `-8..8` at Y `7..8`.
- [ ] **Step 4:** Add three back border strips at Z `9.5..10.5`: vertical strips X `-8..-7` and `7..8` at Y `7..14`, plus a bottom strip X `-8..8` at Y `7..8`.
- [ ] **Step 5:** Add three left border strips at X `-10.5..-9.5`: vertical strips Z `-8..-7` and `7..8` at Y `7..14`, plus a bottom strip Z `-8..8` at Y `7..8`.
- [ ] **Step 6:** Add three right border strips at X `9.5..10.5`: vertical strips Z `-8..-7` and `7..8` at Y `7..14`, plus a bottom strip Z `-8..8` at Y `7..8`.

### Task 4: Add four square labyrinth reliefs

**Files:**
- Modify: live Blockbench project `crown`

**Interfaces:**
- Consumes: four panel backings
- Produces: one readable seven-bar relief motif per side

- [ ] **Step 1:** On the front face at Z `-10.75..-10`, add seven bars in the X/Y plane: outer top `[-6,12.5]..[6,13.25]`, outer left `[-6,8]..[-5.25,13.25]`, outer bottom `[-6,8]..[3,8.75]`, inner top `[-3.5,11]..[3.5,11.75]`, inner right `[2.75,9.5]..[3.5,11.75]`, inner bottom `[-1.5,9.5]..[3.5,10.25]`, and center stem `[-1.5,9.5]..[-0.75,11]`.
- [ ] **Step 2:** Mirror the same seven X/Y bars onto the back face at Z `10..10.75`.
- [ ] **Step 3:** Rotate the motif into the Z/Y plane on the left face at X `-10.75..-10`.
- [ ] **Step 4:** Mirror the Z/Y motif onto the right face at X `10..10.75`.

### Task 5: Add the dentil band

**Files:**
- Modify: live Blockbench project `crown`

**Interfaces:**
- Consumes: cornice and lintels
- Produces: twenty evenly spaced projecting dentils

- [ ] **Step 1:** Add five front dentils at Z `-12.5..-11.5` and five back dentils at Z `11.5..12.5`. Use Y `14.5..15.5` and X intervals `-8.75..-7.25`, `-4.75..-3.25`, `-0.75..0.75`, `3.25..4.75`, and `7.25..8.75`.
- [ ] **Step 2:** Add five left dentils at X `-12.5..-11.5` and five right dentils at X `11.5..12.5`. Use Y `14.5..15.5` and Z intervals `-8.75..-7.25`, `-4.75..-3.25`, `-0.75..0.75`, `3.25..4.75`, and `7.25..8.75`.

### Task 6: Validate and visually inspect

**Files:**
- Modify: live Blockbench project `crown`

**Interfaces:**
- Consumes: completed geometry
- Produces: structural evidence and three-quarter screenshot

- [ ] **Step 1:** Call `get_project_info`; expect 79 cubes, 11 groups, 0 meshes, and 0 textures.
- [ ] **Step 2:** Call `list_outline` and confirm the single intended root and subgroup hierarchy.
- [ ] **Step 3:** Query all cubes with `find_elements_by_criteria`; confirm no cube exceeds the design bounds.
- [ ] **Step 4:** Set a three-quarter perspective camera aimed at the pedestal center.
- [ ] **Step 5:** Capture a viewport screenshot and inspect the foundation, panels, corner pillars, reliefs, dentils, cornices, and capstone.
- [ ] **Step 6:** If the silhouette is unclear, make only small geometry corrections and repeat structural and screenshot validation.

## Execution Result

- Built and exported `stone_pedestal.bbmodel` with 79 cubes and 11 groups.
- Verified bounds X/Z `-16..16` and Y `0..20`.
- Verified 0 meshes, 0 textures, and 0 textured faces.
- Blockbench validator reported 0 errors and 0 warnings.
- Both MCP screenshot routes returned blank WebGL canvas captures, including solid, colored-solid, and wireframe preview modes. Structural validation succeeded, but automated visual inspection could not be completed through the capture endpoint.
