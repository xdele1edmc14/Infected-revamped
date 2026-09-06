# Stone Pedestal Blockbench Design

## Objective

Create only the pedestal from the supplied dark-fantasy reference as a compact Minecraft display prop in the dedicated Blockbench project `stone_pedestal`.

## Scope

- Use the existing Generic Model project.
- Build with cubes only.
- Center the model on X/Z `0`.
- Use a maximum footprint of `32 x 32` Blockbench units.
- Use a total height of `20` units.
- Keep the design symmetrical, readable, and easy to edit.
- Do not add textures, materials, painting, animation, particles, or lighting.
- Do not create the crown, floating object, vines, moss, foliage, or loose rubble.

## Vertical Structure

| Section | X/Z size | Y range |
| --- | --- | --- |
| Bottom foundation slab | 32 x 32 | 0-2 |
| Recessed lower step | 28 x 28 | 2-4 |
| Raised plinth band | 24 x 24 | 4-6 |
| Main shrine body | 22 x 22 | 6-15 |
| Lower cornice | 24 x 24 | 15-16 |
| Upper cornice | 27 x 27 | 16-18 |
| Heavy capstone | 30 x 30 | 18-20 |

## Architectural Details

- Frame the main body with four thick corner pillars.
- Place one deeply recessed central panel on each side.
- Build a square labyrinth relief inside each panel using narrow cubes.
- Add layered lintels above all four panels.
- Run a band of small block dentils beneath the upper cornice.
- Use stepped offsets to break up the foundation and upper bands.
- Keep all structural geometry symmetrical. Weathering and asymmetry are reserved for a later pass.

## Outliner

```text
Pedestal
|-- Foundation
|-- Lower_Platform
|-- Main_Body
|   |-- Corner_Pillars
|   |-- Wall_Panels
|   `-- Relief_Carvings
|-- Cornice
|   |-- Lintels
|   `-- Dentils
`-- Capstone
```

## Self-Prompt

Using the live Blockbench MCP project named `stone_pedestal`, construct only a freestanding dark-fantasy stone pedestal inspired by the supplied reference. Build it as a compact Minecraft display prop centered at X/Z 0, with a 32 x 32-unit maximum footprint and a total height of 20 units. Use cubes only, with no meshes. Create a layered foundation, stepped lower platform, substantial square shrine body, four corner pillars, recessed panels on all four sides, block-built square labyrinth reliefs, layered lintels, a dentil band, projecting cornices, and a heavy top capstone. Keep the geometry readable, symmetrical, and editable. Do not create the crown or any floating object. Do not add vines, moss, foliage, rubble, textures, materials, painting, lighting, particles, or animation. Organize all elements under a `Pedestal` root group with clearly named structural subgroups. Save a checkpoint before construction, then validate the project hierarchy, cube count, dimensions, and final silhouette using Blockbench MCP screenshots.

## Acceptance Criteria

- The project contains exactly one intended root group named `Pedestal`.
- All visible model elements are cubes.
- The model fits within X/Z `-16..16` and Y `0..20`.
- The foundation, shrine body, corner pillars, four recessed panels, four relief motifs, cornice layers, dentils, and capstone are visibly distinct.
- No textures or excluded decorative elements are present.
- A final Blockbench screenshot shows a coherent pedestal silhouette from a three-quarter view.
