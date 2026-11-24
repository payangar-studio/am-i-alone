# Companion

The character itself, the first feature to build. Owner and modes come later
(`../ownership/`).

## In game

A companion looks, moves and animates like a player: the player animation mods
(Fresh Animations, Not Enough Animations, Touch'n Motion, Better Combat...)
animate it as they animate players. It is not a player: it takes no slot on the
server, and it is not in the Tab list.

When nobody needs it, it lives like a villager: it stays where it is, and
freezes when its area unloads. When it follows its owner, it keeps the world
alive around it like a player. Whatever happens, it keeps everything it has.

Decision: a companion is not a player. It gains a player's abilities one at a
time, each chosen on purpose (keeping the world alive around it, a death
message like a player's...), rather than behaving as a player in everything.

Decision: its skin is one of the skins the mod ships, like an entity's variant:
chosen when the companion is created, it never changes after. Players' own
skins are not used.

Decision: a companion that dies is dead for good, like a creature
(`06-death.md`).

Requirements:

1. **Animated like a player, owned or not**, by the player animation mods.
2. **Cheap when nobody needs it.** A tavern may hold several; they cost like
   villagers and freeze with their area.
3. **Keeps the world alive when it follows its owner**: the world around it
   lives as around a player, chunks loaded, entities, machines, crops growing.
4. **Not a player of the server.** Not in the Tab list, not counted among the
   players, never in the way of a player who wants to join.
5. **Nothing lost, nothing duplicated**, whatever happens to it: its owner
   leaving, the server stopping.

## How it is built

Decision: a companion is not a player. It gets a player's abilities one by
one, each through a vanilla mechanism the mod drives itself (its own chunk
ticket, the death message vanilla builds, broadcast with vanilla's
functions...), and never by taking a player's behaviour as a whole.

A companion is one ordinary entity on the server, which clients build as a
player. When it follows its owner, it places a chunk ticket of the mod's own
type where it stands, and the world around it lives as around a player.

| Class | What it is |
|---|---|
| `Companion` | The companion on the server: an entity extending `Avatar`, vanilla's player-shaped entity that is not a player (the Mannequin's class), saved with its chunk |
| `ClientCompanion` | The facade on the client, extending `RemotePlayer`: what the client builds for a companion, so the animation mods see a player |
| Companion ticket | Not a class: a `TicketType` of the mod, registered like vanilla's, which a following companion places and moves with it |

Decision: no mixin. Every piece goes through a vanilla or Fabric API. One may
come later for despawning around a companion far from any player
(`03-loading.md`).

Until the other features exist, test commands stand in for them: one creates a
companion (where the places will), one places or removes its ticket (where
follow mode will). Their syntax is decided with the issue of the first brick.

Sub-pages:

| Page | What it answers |
|---|---|
| `01-entity.md` The entity | What the companion is on the server, and why `Avatar` |
| `02-facade.md` Client facade | What a client builds for a companion, why the mods animate it, why it is not in Tab |
| `03-loading.md` World loading | How a following companion keeps the world alive like a player, and what a player brings that it does not |
| `04-identity.md` Identity, names and skins | Its name, its UUID, its skin variant, and how the client learns them |
| `05-saving.md` Saving | Where a companion is saved, when its owner leaves, at restart and crash |
| `06-death.md` Death | What happens when a companion dies, its death message, the death mods |

Ruled out:

| Option | Why |
|---|---|
| A server entity extending `Player`, in two forms (an entity at rest, a `ServerPlayer` when following) switched behind one client entity | Being a `Player` on the server brought one problem after another: its UUID forced back from its profile on every load, a scoreboard name taken from its name and colliding with a real player of the same name, a player's death path, an overridden entity type with a hidden coupling, a form switch that needed a client mixin and a handover of everything, two places to save it |
| A hidden fake `ServerPlayer`, an anchor, standing next to the companion to load the world | Two entities for one: it had to be hidden from clients, from mobs, from collisions, from the locator bar, from experience orbs and from `/kill`, and it still did not bring natural spawning or despawning around the companion |
| A token `ServerPlayer`, never in the world, registered in the distance manager (`DistanceManager.addPlayer`) | The same chunk result as a ticket, with more risk: a player object that only exists as a key, statistics and advancements registered for it, a raised global mob cap |
| One fake connected player for every companion (like Carpet's bots) | Counted among the players, limited by `max-players`, shown in Tab unless worked around; a player's cost for each companion, even unowned; never stored with its chunk |
