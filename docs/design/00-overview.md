# Am I Alone? · design

What the mod does and why, decided before the code. The code follows these
documents; this branch does not follow the code.

These Markdown files are the design for agents. `index.html` is the same
design as a page for people, built from them by the `mod-design` skill: edit
the documents, never the page by hand.

## Overview

The mod adds companions: characters that look and animate like players, but
that nobody controls. Some live unowned in village taverns or structures; a
player can make one their own, the way one tames a wolf, and give it orders
(follow, stay, wander, stay around its home).

The companion's design is in `01-companion.md`.

## Glossary

| Word | Meaning |
|---|---|
| Companion | A character of the mod, owned or not. What defines it (identity, owner, mode, home, memory) outlives its changes of form. |
| Owner | The player a companion belongs to. A companion may have none. |
| Mode | The order an owner gives their companion (follow, stay, wander, stay around the home...). |
| Home | The place a companion stays around when its mode says so. |
| Form | How a companion exists in the world on the server. It always has exactly one. |
| ??? (stored form) | The form of a companion that lives as an ordinary entity, stored and saved with its chunk. Name to choose. |
| ??? (world-player form) | The form of a companion that is a player of the world and loads the world around it like a player. Name to choose. The stored form also extends `Player`; what differs is being a player *of the world*. |
| Connected player | A player of the server's list (`PlayerList`): counted in the player count, subject to the connection limit, shown in Tab. A companion never is one. |
| Player of the world | A player of a world's list (`ServerLevel.players`): loads chunks around it, makes monsters spawn, counts for sleeping. |

## Minecraft and Fabric terms used here

| Term | What it is |
|---|---|
| `Player`, `ServerPlayer` | Vanilla's class of every player, and its server version for a connected player. |
| `RemotePlayer` | The class a client uses for the other players it sees. |
| Mannequin, `Avatar` | Vanilla's player-shaped decoration entity, and the class it shares with players. |
| `makeMockPlayer` | Vanilla's helper that creates a player without a connection, for its own tests. |
| Brain | Vanilla's AI system (villagers, piglins, warden): memories, sensors, activities. |
| Global attachment | Data that Fabric API keeps for the whole server and saves with the world. |

## Mechanics

- The two forms of a companion and the switch between them: see
  `01-companion.md`.

## Blocks and items

None yet.

## Config

None yet.

## Sides

`mod.toml` sets `both-required`: the mod runs on the server and the client.

- **Server**: the two forms of the companion, its brain (vanilla's Brain), the
  switch between forms, saving.
- **Client**: one representation whatever the form: a remote player
  (`RemotePlayer`), drawn by the player renderer. This is what makes the
  player animation mods animate it.
- **Server to client**: the entity's spawn packet carries the type
  `am_i_alone:companion` for both forms; then the synced data of a player
  (health, pose, equipment...). No profile is sent, which keeps the companion
  out of Tab.

## Open questions

They are asked in `01-companion.md`, where each problem is described.
