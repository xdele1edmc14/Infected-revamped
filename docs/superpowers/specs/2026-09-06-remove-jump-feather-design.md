# Remove Jump Feather Design

## Goal

Remove the Jump Feather feature from the plugin's runtime and administrator-facing interface. No participant, including infected players, should receive or use a Jump Feather after this change.

## Scope

- Delete the Jump Feather interaction listener and the `/givefeather` command implementation.
- Stop registering the listener, command, and repeating distribution task during plugin startup.
- Remove feather distribution, cooldown tracking, and cooldown cleanup from `GameManager`.
- Remove `/givefeather` from plugin metadata and the administrator help output.
- Remove or replace tests that require the deleted feature, and add regression coverage for its absence where practical.
- Preserve historical design and implementation documents; they describe earlier releases and are not runtime configuration.

## Architecture and Data Flow

The removal cuts the feature at every live integration point. `InfectedPlugin` will no longer start or register any Jump Feather behavior. `GameManager` will no longer own feather-related state or scheduling. The command registry and help output will no longer advertise a way to create the item. With the listener deleted, ordinary feathers will have no plugin-specific launch or fall-damage behavior.

No replacement mechanic or configuration toggle will be introduced.

## Compatibility and Cleanup

Existing unrelated inventory cleanup continues to operate normally. A Jump Feather left in a player's inventory from an older server session becomes an ordinary feather because the custom listener is removed. Historical documentation remains unchanged to avoid rewriting the record of prior implementation decisions.

The repository currently contains unrelated uncommitted work. Implementation will edit only Jump Feather integration points and directly affected tests or metadata, preserving all other changes.

## Verification

- Run focused tests covering plugin metadata, lifecycle scheduling, and help output if covered by the current suite.
- Search production resources and Java sources for live `JumpFeather`, `givefeather`, `featherCooldown`, and feather-task references.
- Run the complete Maven test suite and package build.

