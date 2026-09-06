# Weathered Pedestal Texture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the pedestal's cyan/green placeholder atlas with a readable weathered ancient-stone texture.

**Architecture:** Preserve the existing UV layout and repaint only pixels already used by the pedestal. Derive tonal treatment from cube names and face orientation, then verify the result in Blockbench's textured viewport and exported project.

**Tech Stack:** Blockbench Java Block/Item, Blockbench MCP texture and project tools

## Global Constraints

- Work only in `stone_pedestal - Converted`, UUID `cf0a1007-b41d-88ea-aee8-46faf042a0f8`.
- Preserve the active converted project's 77 cubes, 0 meshes, and 11 groups; do not restore the two right-relief cubes missing from the original source during this texture-only pass.
- Reuse the existing embedded 256 x 256 texture.
- Do not add crown geometry or crown colors.
- Leave repository changes uncommitted.

---

### Task 1: Protect and inspect the source atlas

**Files:**
- Modify: live Blockbench project `stone_pedestal - Converted`

**Interfaces:**
- Consumes: existing texture `texture`
- Produces: checkpoint `before_weathered_stone_paint` and confirmed UV atlas

- [ ] Verify project identity, element counts, texture dimensions, and active texture UUID.
- [ ] Save checkpoint `before_weathered_stone_paint`.
- [ ] Capture the existing texture and model viewport as before-state evidence.

### Task 2: Paint the weathered stone atlas

**Files:**
- Modify: embedded Blockbench texture `texture`

**Interfaces:**
- Consumes: existing face UV rectangles and cube names
- Produces: opaque atlas pixels using the approved eight-color palette

- [ ] Repaint only pixels covered by existing cube-face UV rectangles; preserve unused transparent pixels.
- [ ] Use `#5A4637` and `#73563A` as the broad-face bases with deterministic low-amplitude variation.
- [ ] Use `#241A16` and `#3A2B24` for recessed panels, relief gaps, and undersides.
- [ ] Use broken `#987348` and `#B08A58` edge wear on steps, pillars, lintels, cornices, and capstone faces.
- [ ] Add sparse `#334226` and `#596B35` moss clusters to lower-platform and foundation side-face UV regions.
- [ ] Refresh Blockbench's textured material and viewport without changing project geometry.

### Task 3: Verify and save

**Files:**
- Create: `stone_pedestal_colored.bbmodel`

**Interfaces:**
- Consumes: painted live project
- Produces: editable colored Blockbench project and verification evidence

- [ ] Capture the painted texture and a three-quarter textured viewport screenshot.
- [ ] Confirm the cyan/green placeholder colors are absent from the visible pedestal.
- [ ] Confirm 77 cubes, 0 meshes, 11 groups, and one intended texture.
- [ ] Read Blockbench validator status and report all remaining warnings or errors honestly.
- [ ] Export the editable project through codec `project` to `stone_pedestal_colored.bbmodel`.
