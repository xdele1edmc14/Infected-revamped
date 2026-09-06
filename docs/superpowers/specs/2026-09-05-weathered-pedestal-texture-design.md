# Weathered Pedestal Texture Design

## Objective

Repaint the existing 256 x 256 pedestal texture in the active Blockbench Java Block/Item project to match the supplied ancient-stone reference.

## Constraints

- Preserve the active converted project's 77 pedestal cubes and existing group hierarchy. The converted project is missing `right_relief_inner_top` and `right_relief_inner_bottom` compared with the original 79-cube source; this texture-only pass does not restore them.
- Reuse the existing embedded texture instead of creating geometry for surface detail.
- Do not add crown geometry or crown colors during this pass.
- Keep the texture readable at Minecraft display scale.

## Palette

- Deep crevice brown: `#241A16`
- Dark stone brown: `#3A2B24`
- Mid weathered stone: `#5A4637`
- Warm stone: `#73563A`
- Worn ochre edge: `#987348`
- Dust highlight: `#B08A58`
- Dark moss: `#334226`
- Moss highlight: `#596B35`

## Surface Treatment

- Give the broad stone faces irregular mid-brown and warm-brown block variation.
- Darken recessed panels, relief gaps, and undersides to improve architectural depth.
- Add warm ochre highlights along step rims, pillar edges, lintels, cornices, and the capstone perimeter.
- Keep highlights broken and uneven rather than tracing every edge continuously.
- Add sparse moss only around lower corners, the base, and a few protected recesses.
- Avoid dense noise, bright saturation, modern metallic colors, and crown-like gold.

## Acceptance Criteria

- The cyan/green placeholder appearance is gone.
- The pedestal reads as dark, weathered ancient stone from a three-quarter view.
- Recesses are visibly darker than projecting bands.
- Warm edge wear and moss remain accents rather than dominant colors.
- Geometry remains 77 cubes, 0 meshes, and 11 groups.
- Blockbench validation reports no errors.
