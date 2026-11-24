# Am I Alone? · design

What the mod does and why, decided before the code. The code follows these
documents; this branch does not follow the code.

These Markdown files are the design for agents. The HTML pages next to them are
the same design as a wiki for developers, built by the `mod-design` skill: this
document becomes its home page (`index.html`), and each other document
(`NN-<subject>.md`) a page of its own. Edit the documents, never the pages by
hand.

## Overview

The mod adds companions: characters that look and animate like players, but
that nobody controls. Some live unowned in village taverns or structures; a
player can make one their own, the way one tames a wolf, and give it orders:
follow, decided so far, and later others such as stay or wander.

The whole, at the macro level: a companion is one identity (its core) that
lives on the server in one of two forms, the **anchored** form (an entity
stored with its chunk) or the **anchor** form (a player of the world that keeps
the world loaded around it). Clients never see the forms: they see one facade,
a remote player that the player animation mods animate, and the form changes
behind it.

## Glossary

| Word | Meaning |
|---|---|
| Companion | A character of the mod, owned or not. Its identity (name, skin, UUID), owner, mode, home and memory outlive its changes of form. |
| Core | What the companion is, carried by its current form: identity, owner, mode, home, and the rule that picks the form. |
| Form | How a companion exists in the world on the server. It always has exactly one. |
| Anchored form | The form of a companion that lives as an ordinary entity, held by its chunk: saved with it, frozen when it unloads. |
| Anchor form | The form of a companion that is a player of the world: it holds the world loaded around it, like a player ("anchor" is the usual name of a chunk loader in mods). |
| Facade | What a client sees of a companion: one remote player, the same object whatever the form behind it. |
| Owner | The player a companion belongs to. A companion may have none. |
| Mode | The order an owner gives their companion. Only follow is decided so far. |
| Home | The place a companion stays around when its mode says so. |
| Connected player | A player of the server's list (`PlayerList`): counted, subject to the connection limit, shown in Tab. A companion never is one. |
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
| Synced attachment | Data that Fabric API attaches to an entity and sends to the clients that see it. |

## Requirements

1. **Animated like a player, owned or not.** The mods that animate players
   (EMF and ETF, Fresh Animations Player Extension, Not Enough Animations,
   Touch'n Motion, Better Combat, EMF Compat) animate it with no compatibility
   code on our side.
2. **Cheap when unowned.** A village tavern may hold several; they cost like
   villagers and freeze with their chunk.
3. **Like a player when it follows its owner**, with every effect of a player:
   chunks, monster spawning, sleeping.
4. **Never a connected player.** Not counted, takes no slot of the connection
   limit, not shown in Tab.
5. **Nothing lost, nothing duplicated.** Whatever happens (switching form, its
   owner leaving, the server stopping), it keeps exactly its inventory, its
   equipment and everything else it had.
6. **An invisible switch.** Changing form does not show in game.
7. **No mixin** while a vanilla or Fabric API does the job. The invisible
   switch needs one, on the client.

## Pages

| Page | What it answers |
|---|---|
| `01-forms.md` | What the two forms are, when a companion takes each, and what happens when its owner leaves. |
| `02-identity.md` | What the core holds, how both forms share one identity, and where the Brain and the motor sit. |
| `03-switch.md` | How a companion changes form without losing or duplicating anything, and without the client seeing it. |
| `04-sides.md` | What a client receives and builds for a companion, and why the animation mods animate it. |
| `05-saving.md` | Where and when each form is saved, and what happens at disconnection, restart and crash. |

## Blocks and items

None yet.

## Config

None yet.

## Open questions

- The motor (how the Brain's intentions become player inputs): see
  `02-identity.md`.
- The invisible switch: checking it in game, and a switch while riding or
  dying: see `03-switch.md`.
- An owner who never comes back: see `05-saving.md`.
