# Configurable Scoreboard Design

## Goal

Replace the minimal team-count sidebar with a configurable, role-aware scoreboard that shows personal round performance and the current survivor-to-infected balance.

## Display

The sidebar title and every displayed line come from `config.yml`. Survivor, infected, and spectator layouts are configured separately so irrelevant statistics are omitted rather than rendered as empty labels.

The default survivor layout shows role, kills, both team counts, and a twelve-segment `Outbreak` bar. The infected layout replaces kills with infections and remaining lives. The spectator layout shows role, team counts, and the same bar.

The bar represents the current active team split, not a predicted win probability. Blue segments represent survivors and red segments represent infected. When both teams are active, each receives at least one visible segment.

## Formatting and placeholders

Scoreboard text supports MiniMessage. Legacy ampersand color codes remain supported on a per-string basis so existing configurations continue to render. A single configured string should use either MiniMessage or legacy formatting, not mix both syntaxes.

Supported placeholders are `{role}`, `{kills}`, `{infections}`, `{lives}`, `{max_lives}`, `{lives_hearts}`, `{survivors}`, `{infected}`, and `{outbreak_bar}`. Bar length, segment appearance, heart appearance, role names, layouts, title, and refresh interval are all configurable.

## State and attribution

Kills and infections are round-local and reset at round start and cleanup. An infection is credited to the infected attacker whose valid active melee hit converts a survivor. A kill is credited to an active survivor when the final damage source of an infected death resolves to that survivor, including supported projectile and owned-entity sources.

The existing infected-life tracker remains the source of truth for remaining and maximum lives.

## Safety and tests

Scoreboard lines use teams and component prefixes so duplicate and blank configured lines remain distinct. At most Minecraft's fifteen sidebar rows are rendered. Tests cover round-stat counters, bar apportionment and edge cases, text rendering, infection attribution, and kill attribution.
