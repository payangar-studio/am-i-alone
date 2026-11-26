# Am I Alone? · design

What the mod does and why, decided before the code. The code follows these
documents; this branch does not follow the code.

These Markdown files are the design for agents. The HTML pages next to them are
the same design as a wiki for developers, built by the `mod-design` skill: this
document becomes its home page (`index.html`), each feature a folder whose
`00-overview.md` becomes its page, and each other document a sub-page. Edit the
documents, never the pages by hand.

## In game

The mod adds companions: characters that look and move like players, but that
nobody controls. Some live in village taverns or structures; a player can recruit
one for emeralds, make it their own, and give it orders.

## Features

| Feature | What it does in game | State |
|---|---|---|
| `companion/` Companion | The character itself: looks and animates like a player, costs little when nobody needs it, keeps everything it has | Designed; first to build |
| `ownership/` Ownership and modes | A player recruits a companion for emeralds, owns it and gives it orders, such as follow | Recruitment designed; next to build |
| `behaviour/` Behaviour | What a companion does on its own: follow, stay, wander, stay around its home | Not designed |
| `places/` Where companions live | Where companions appear: taverns, structures | Not designed |
| `debug/` Companion debug stick | The operators' debug mode: any companion's screen, with debug options | Designed |

How they fit: the places spawn companions; ownership gives a companion an owner
and a mode; behaviour makes it act on that mode; the companion feature is the
character all of them act on.

Pages for the whole mod: `sides.md` (what runs on the client and on the
server, and what crosses between them).

## Glossary

| Word | Meaning |
|---|---|
| Companion | A character of the mod, owned or not: one ordinary entity on the server, with a name, a skin, an owner, a mode, a home and a memory. |
| Companion ticket | A chunk ticket of the mod's own type, which a companion places where it stands while it follows its owner: the world around it lives as around a player. |
| Facade | What a client sees of a companion: a remote player, which the player animation mods animate. |
| Owner | The player a companion belongs to. A companion may have none. |
| Recruitment | How a player makes a companion without an owner their own: they pay its price in emeralds on its recruitment screen. |
| Opinion | What a companion thinks of a player: the villagers' rumours, heard and kept as a villager does. It lowers or raises the price of its recruitment. |
| Mode | The order an owner gives their companion. Only follow is decided so far. |
| Home | The place a companion stays around when its mode says so. |
| Designated weapon | One of a companion's two weapon slots, primary and secondary: what it reaches for to fight, not what it holds. |
| Companion debug stick | The operators' item for the debug mode: any companion's screen, with debug options. Vanilla's own is always called vanilla's debug stick. |
| Connected player | A player of the server's list (`PlayerList`): counted, subject to the connection limit, shown in Tab. A companion never is one. |
| Player of the world | A player of a world's list (`ServerLevel.players`): loads chunks, makes monsters spawn, counts for sleeping. |

## Minecraft and Fabric terms

| Term | What it is |
|---|---|
| `Player`, `ServerPlayer` | Vanilla's class of every player, and its server version for a connected player. |
| `RemotePlayer` | The class a client uses for the other players it sees. |
| Mannequin, `Avatar` | Vanilla's player-shaped decoration entity, and `Avatar`, the class it shares with players: a player-shaped entity that is not a player. |
| Brain | Vanilla's AI system (villagers, piglins, warden): memories, sensors, activities. |
| Global attachment | Data that Fabric API keeps for the whole server and saves with the world. |
| Synced attachment | Data that Fabric API attaches to an entity and sends to the clients that see it. |
| `mod.toml` | This project's description of the mod, from which the build writes `fabric.mod.json`. |

## Mods to keep working

The mods the companion must stay compatible with, with no compatibility code
where possible. Taken from the modpack grimoire-et-sortileges.

| Mod | What it does to a companion | Where |
|---|---|---|
| Player Animation Library, Not Enough Animations, ETF, EMF, EMF Compat (core, Better Combat, Not Enough Animations), Fresh Animations and its Player Extension (resource packs), Touch'n Motion | Animate it as a player, on the client facade | `companion/02-facade.md` |
| Better Combat | Animates it as a player, but a held weapon's pose and the attacks need an adapter; optional | `companion/02-facade.md` |
| Ragdoll Deaths, Simple Blood | Change how its death looks | `companion/06-death.md` |
| Tools on my Back | Shows a player's tools on their back; not checked on a companion | `companion/07-inventory.md` |
| Death Locator | Marks where a player died; not checked on a companion | `companion/06-death.md` |
| Overflowing Bars, Colorful Hearts (not in the pack) | Change how the HUD draws hearts and armor; not on the companion's bars. An adapter for Overflowing Bars comes later | `companion/08-screen.md` |
| Grave mods (none in the pack) | Keep only players: a companion's items stay on the ground | `companion/07-inventory.md` |

## Open questions

- Despawning around a companion far from any player, for a future mode
  (`companion/03-loading.md`).
- An issue to open on Ragdoll Deaths (`companion/06-death.md`).
- Disconnection at server stop, and an owner who never comes back
  (`companion/05-saving.md`).
- Recruitment: the owner's name when they change it, which way to pay is
  kept, its own sound (`ownership/01-recruitment.md`).
- Other conditions to recruit, a price that grows with the equipment, the
  other modes (`ownership/00-overview.md`).
- The motor (`behaviour/00-overview.md`).
- Where companions live (`places/00-overview.md`).

## Blocks, items, config

One item, the companion debug stick, for operators only (`debug/00-overview.md`). No
block, no config yet.
