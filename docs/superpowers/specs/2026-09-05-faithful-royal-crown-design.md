# Faithful Royal Crown Blockbench Design

## Goal

Create a normally sized Minecraft-style royal crown in a new Blockbench Java Block/Item project. The model should closely follow the supplied reference without becoming oversized or unnecessarily complex.

## Form and Scale

- Use cube-only geometry suitable for the Java Block/Item format.
- Use a 16-by-16-unit band footprint and a 12-unit total height including the cross; raised jewels may project no more than 1 unit beyond the band.
- Form a hollow-looking gold band sized like wearable headgear rather than a large display prop.
- Add a compact dark-red inner cap that remains visibly recessed inside the gold rim.
- Build four gold arches that rise from the band and meet beneath a centered gold cross.

## Decoration

- Use warm gold with lighter highlights and darker gold accents for readable voxel depth.
- Place raised diamond-shaped jewels around the band in red, blue, and green, matching the reference's alternating colors.
- Keep the jewels and trim thick enough to read clearly from normal item-view distance.
- Avoid tiny geometry that could be represented by color variation instead.

## Project Structure

- Create a new project named `faithful_royal_crown`; do not modify the active `stone_pedestal - Converted` project.
- Organize the model under a single `Crown` root with child groups for `Band`, `Inner_Cap`, `Arches`, `Cross`, and `Jewels`.
- Use a small purpose-built palette or texture set for gold, gold highlight/shadow, dark red fabric, and red/blue/green gems.

## Verification

- Reconfirm the active project name, UUID, and `java_block` format before placing geometry.
- Verify the final model contains cubes only, has the intended group hierarchy, and remains within the target scale.
- Save a named checkpoint, export the editable project as `faithful_royal_crown.bbmodel`, and inspect a Blockbench screenshot from a useful three-quarter angle.
- Confirm Blockbench reports no validation errors before completion.
