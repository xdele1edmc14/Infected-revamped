# GamingBarns Guns Damage Attribution Checklist

Automated tests cover the player-attribution paths exposed through the Paper and Bukkit APIs: direct players, projectile shooters, Paper causing/direct entities, TNT sources, area-effect-cloud sources, evoker-fang owners, nested sources, cycles, and unattributable damage. The installed GamingBarns Guns build still requires this live-server check because its emitted damage event and source chain are not available in the unit-test environment.

## Test setup

- Use the exact GamingBarns Guns build installed on the event server.
- Configure a valid infected holding spawn plus at least one survivor, infected-release, and infected-respawn spawn.
- Join with at least three players so two can remain survivors while one is infected.
- Keep the server console visible and record the gun item, weapon action, Bukkit damager type, Paper causing entity, Paper direct entity, projectile shooter or owned-entity source, whether the event was cancelled, and the health before/after each shot.
- Test once during `HEADSTART` and again during `ACTIVE`.

If the current server build does not expose those source values in existing diagnostics, add temporary event-monitor instrumentation on a test server only. Do not infer the shooter from proximity, aim direction, or the nearest player.

## Required checks

| Scenario | Phase | Expected result |
| --- | --- | --- |
| Survivor shoots another survivor with a rifle/projectile weapon | `ACTIVE` | Damage event is cancelled and victim health does not decrease. The responsible player should resolve through the event damager or damage-source chain. |
| Survivor damages an infected with the same gun | `ACTIVE` | Damage is allowed. The infected remains infected and normal infected-life/death handling applies. |
| Infected attempts to shoot a survivor | `HEADSTART` | Damage is cancelled. The survivor is not infected and loses no health. |
| Infected attempts to shoot a survivor | `ACTIVE` | Damage is cancelled because infected combat is melee-only. The survivor is not infected and loses no health. |
| Infected directly punches a survivor | `HEADSTART` | Damage is cancelled and infection does not occur. |
| Infected directly punches a survivor | `ACTIVE` | The survivor is infected through the direct-melee path. |
| Infected gun effect damages multiple survivors or leaves a delayed damage source | `ACTIVE` | Every attributable indirect hit is cancelled and none of the victims are infected. |

## Attribution record

For every gun scenario, capture:

```text
GamingBarns version:
Weapon and action:
Round phase:
Event class:
Direct Bukkit damager type:
Projectile shooter/source:
Paper causing entity:
Paper direct entity:
Resolved attacking player:
Event cancelled:
Victim health before/after:
Victim role before/after:
Result: PASS / FAIL
```

## Failure rule

A live check fails if survivor friendly fire changes health, an infected gun damages or infects a survivor, or a survivor gun cannot damage an infected. If GamingBarns supplies no player-owned damager, projectile shooter, causing entity, direct entity, or supported owner/source chain, retain the captured diagnostics and add an adapter for that confirmed event shape; do not add guess-based attribution.
