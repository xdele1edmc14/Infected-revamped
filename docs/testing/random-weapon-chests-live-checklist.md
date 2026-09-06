# Random Weapon Chests Live Checklist

Run this checklist on the supported Paper 1.21.4 server with the actual gun plugin and representative custom items installed. Automated tests do not replace this runtime check for server-owned item serialization, chest holders, or visual inventory behavior.

## Setup and Selection

- Run `/infected` as an operator and confirm the centered `Random Weapon Chests` chest opens the separate wizard.
- Receive the selection wand. Confirm left-click sets point 1 and right-click sets point 2 without breaking, placing, opening, or activating the clicked blocks.
- Try selecting the second point in another world and confirm the old incompatible point is cleared with a warning.
- Confirm a non-admin cannot use the wand or any already-open weapon menu.
- Select a region spanning loaded chunks and containing single normal chests, single trapped chests, and both halves of a double chest.
- Put a double chest across a chunk boundary and confirm it is filled/cleared exactly once; select only one half and confirm the outside half is untouched.
- Unload pregenerated intersecting chunks and confirm Fill and Clear discover their chests through bounded asynchronous loading, do not hitch the main tick, do not generate terrain, and release chunks loaded by the operation afterward.
- Include one intentionally non-pregenerated intersecting chunk and confirm the action aborts before changing any chest.

## Exact Item Capture

- Hold a real custom-NBT gun and add it. Reopen/reload the plugin and confirm its name, lore, model/data components, enchantments, attributes, PDC/plugin tags, and other behavior remain intact.
- Pick up that gun from the player inventory and drop it onto the gun input. Click the registered gun, then pick up its exact corresponding ammo and drop it onto the ammo input. Configure an ammo-bundle range greater than one.
- Add at least two distinct guns at different rarity presets and link ammo to every gun.
- Add custom-NBT grenades at different rarities and quantity ranges.
- Confirm adding or replacing any entry copies the dropped cursor item without consuming or modifying the original.
- Confirm multiple one-item ammo bundles are consolidated into one stack, up to the ammo item's real maximum stack size.
- Select a large, tall region and confirm raw block volume is accepted. Adjust the chunk scan limit in the wizard and confirm it cannot exceed 5,000.

## Generation Rules

- Set second-gun chance to 0% and grenade chance to 0%; fill and confirm every unique chest inventory has exactly one gun and its linked ammo.
- Set second-gun chance to 100%; fill and confirm every chest has two different guns when two eligible guns exist, never more than two, and both guns have their own ammo.
- Leave only one configured gun with second-gun chance at 100%; confirm chests still contain only that one gun plus ammo.
- Set grenade chance to 100%; confirm every chest receives one selected grenade type within its configured quantity range.
- Run enough fills with mixed rarity presets to confirm rarer items visibly occur less often; do not judge statistical balance from only a few chests.
- Inspect generated gun, ammo, and grenade items with the gun plugin and an NBT/PDC inspection tool to confirm their custom data and actual behavior survive generation.
- Confirm a double chest is filled once as one 54-slot inventory and that barrels, shulkers, hopper minecarts, and other containers are untouched.

## Destructive Actions and Guards

- Put unrelated manual items into the selected chests, run Fill, and confirm every chest receives a fresh loadout with no old contents retained.
- Put unrelated manual items into the selected chests, run Clear, and confirm every slot is empty.
- Start an Infected round and confirm both Fill and Clear are blocked.
- While a large scan is active, confirm every round-start entry point is rejected until the operation reports completion.
- Remove a gun's linked ammo and confirm Fill is blocked with a clear error while Clear remains available.
- Open a confirmation, then change the region or catalog from another admin; confirm execution rescans and revalidates current state.
- Have two admins confirm actions together and confirm only one operation can mutate chests at a time.

Record the Paper build, gun-plugin build, tested item IDs, and any failed observation before declaring live compatibility.
