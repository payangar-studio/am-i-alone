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
alive around it exactly like a player. Whatever happens, it keeps everything it
has.

Decision: its skin is one of the skins the mod ships, like an entity's variant:
chosen when the companion is created, it never changes after. Players' own
skins are not used.

Decision: a companion that dies is dead for good, like a creature
(`06-death.md`).

Requirements:

1. **Animated like a player, owned or not**, by the player animation mods.
2. **Cheap when nobody needs it.** A tavern may hold several; they cost like
   villagers and freeze with their area.
3. **Like a player when it follows its owner**, with every effect of a player:
   the world around it stays loaded, monsters spawn, it counts for sleeping.
4. **Not a player of the server.** Not in the Tab list, not counted among the
   players, never in the way of a player who wants to join.
5. **Nothing lost, nothing duplicated**, whatever happens to it: its owner
   leaving, the server stopping.
6. **Nothing else shows**: what keeps the world alive around a following
   companion is never seen, touched, collided with or attacked.

## How it is built

A companion is one ordinary entity on the server, which clients build as a
player. When it follows its owner, a hidden player, its anchor, stands where it
stands and keeps the world alive around it.

| Class | What it is |
|---|---|
| `Companion` | The companion on the server: an entity extending `Avatar`, vanilla's player-shaped entity that is not a player (the Mannequin's class), saved with its chunk |
| `ClientCompanion` | The facade on the client, extending `RemotePlayer`: what the client builds for a companion, so the animation mods see a player |
| `CompanionAnchor` | The anchor, extending `ServerPlayer`: hidden, attached to a companion in follow mode, loads the world around it like a player |

Decision: no mixin. Every piece goes through a vanilla or Fabric API.

Until the other features exist, test commands stand in for them: one creates a
companion (where the places will), one attaches or detaches its anchor (where
follow mode will). Their syntax is decided with the issue of the first brick.

Sub-pages:

| Page | What it answers |
|---|---|
| `01-entity.md` The entity | What the companion is on the server, and why `Avatar` |
| `02-facade.md` Client facade | What a client builds for a companion, why the mods animate it, why it is not in Tab |
| `03-anchor.md` The anchor | How a following companion keeps the world alive without anything else showing |
| `04-identity.md` Identity and skins | Its name, its UUID, its skin variant, and how the client learns them |
| `05-saving.md` Saving | Where a companion is saved, when its owner leaves, at restart and crash |
| `06-death.md` Death | What happens when a companion dies, its death message, the death mods |

Ruled out:

| Option | Why |
|---|---|
| A server entity extending `Player`, in two forms (an entity at rest, a `ServerPlayer` when following) switched behind one client entity | Being a `Player` on the server brought one problem after another: its UUID forced back from its profile on every load, a scoreboard name taken from its name and colliding with a real player of the same name, a player's death path, an overridden entity type with a hidden coupling, a form switch that needed a client mixin and a handover of everything, two places to save it |
| One fake connected player for every companion (like Carpet's bots) | Counted among the players, limited by `max-players`, shown in Tab unless worked around; a player's cost for each companion, even unowned; never stored with its chunk |
