# Infected Revamped

**Infected Revamped** is an active rebuild of DaWHeL's original Infected Minecraft minigame plugin. The original project provided the core idea: survivors are released into an arena, a small group begins infected, and every survivor they hit joins the infected team.

This fork exists to turn that prototype into a safer, clearer, and more reliable minigame for real servers. It is being rebuilt with improved round flow, better setup and administrator controls, safer player handling, and fewer global side effects.

> [!WARNING]
> This project is actively being rebuilt and is **not production-ready**. Do not use it for live events or on a server with valuable player inventories until its gameplay, restoration, and reset systems have been completed and tested.

## Original Plugin Credit

The original Infected plugin was created by **DaWHeL**. This repository is a fork and revamp effort, not a claim of authorship over the original work. Its goal is to preserve the Infected game concept while improving the underlying implementation.

## Game Concept

- Players gather in a lobby before the round begins.
- A configurable number of players are chosen as the starting infected.
- Survivors are sent into the arena first and receive a short head start.
- Infected are released after the grace period and infect survivors through PvP.
- The infected team grows until the round reaches its conclusion.

## Current Technical Base

The project currently targets Paper 1.21.x and Java 21. It includes the original plugin's commands, configuration, teleport-point system, infected spawn, player roles, scoreboard, and gameplay listeners as a starting point for the revamp.

## Development Status

The current focus is on making the plugin dependable before adding new gameplay features. That includes safe player inventory handling, predictable round start and stop behavior, correct win conditions, contained arena rules, and administrator-friendly setup.

For the existing command reference, use `/helpinfected` in-game. Expect commands, configuration, and gameplay behavior to change as the revamp progresses.
