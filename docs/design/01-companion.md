# The companion

## Requirements

1. **Animated like a player, owned or not.** The mods that animate players
   (EMF and ETF, Fresh Animations Player Extension, Not Enough Animations,
   Touch'n Motion, Better Combat, EMF Compat) animate it with no compatibility
   code on our side.
2. **Cheap when unowned.** A village tavern may hold several; they cost like
   villagers and freeze with their chunk.
3. **Like a player when its owner wants it.** Owned and in a mode that asks for
   it, it loads the world exactly as a player does: chunks, monster spawning,
   everything the server does around a player.
4. **Never a connected player.** Not counted among the players, takes no slot
   of the connection limit, not shown in Tab.
5. **No mixin**: no change to Minecraft's own classes while a vanilla or Fabric API
   does the job.

## What makes the animation: the client

Fact, read in the 26.3 code of these mods: they only act on an entity that is a
`Player`, often an `AbstractClientPlayer`, on the client. The server does not
need a connected player: the client building a player is enough.

Decision: whatever its form on the server, the client builds a companion as a
remote player (`ClientCompanion`, a subclass of `RemotePlayer`), drawn by the
player renderer. The spawn packet carries the type `am_i_alone:companion`; the
factory of that type returns the client class on the client, as vanilla does
for the Mannequin.

Fact, checked in game (prototype on branch `spike/companion-forms`): Fresh
Animations Player Extension (an EMF pack), Not Enough Animations, Better Combat
and Touch'n Motion animate the companion in both forms. ETF and EMF Compat were
loaded but not checked one by one. Better Combat animates an attack, but only
starts one from a real client's key press: making a companion attack will take
code of ours.

## The two forms

Decision: on the server, a companion always has exactly one of two forms.

| | ??? (stored form) | ??? (world-player form) |
|---|---|---|
| What it is | An entity extending `Player`, without a connection, like the mock player of vanilla's gametests (`makeMockPlayer`) | A `ServerPlayer` added to the world (`level.addNewPlayer`) without going through the list of connected players, with a fake connection that drops what is sent to it |
| Loads the world | No: freezes when its chunk unloads | Yes, exactly like a player |
| Connected player | No | No |
| Player of the world | No | Yes: counts for sleeping and monster spawning |
| Cost | An entity's | A player's |

Decision: a companion takes the world-player form only when it has an owner **and**
its mode asks it to load the world. In every other case, it takes the stored
form.

### Switching forms

Both forms are `Player`s and write the same data (inventory, health, food,
effects, position, Brain memories, and the mod's data). Switching is saving the
current form, removing it from the world, and creating the other from that
data, at the same place and with the same UUID.

On the client, the entity disappears and reappears at the same place. Question:
does it show in game? If it does, the switch needs hiding.

### Open question: the names of the two forms

"Passive" and "active" are ruled out: an owned companion in a "stay home" mode
would be in the "passive" form, which reads like a mode. Options:

| Option | Stored form | World-player form | What the name says |
|---|---|---|---|
| Vanilla class | `CompanionEntity` | `CompanionPlayer` | What it extends; "entity" is ambiguous, both are entities |
| Link to the world | `ChunkBody` | `PlayerBody` | Where it lives: stored with its chunk, or a player of the world |

### Open question: which modes take the world-player form

Following its owner, surely: without loading the world, the companion would
freeze as soon as its owner walks away. The other modes (stay, wander, stay
around the home) depend on what a companion left far from its owner should do:
live on (world-player form, a player's cost) or freeze (stored form).

### Open question: when the owner disconnects

Does their companion keep the world-player form, and so its chunks loaded, while
its owner is away?

### Open question: what the world-player form keeps of a player

As a player of the world, it counts for sleeping ("1/2 players sleeping") and
makes monsters spawn around it. Each effect can be wanted, neutralised, or set
per mode; sleeping may be neutralised without a mixin if the companion goes to
bed with its owner.

## The core and the forms

Decision: the two forms extend different vanilla classes (`Player` and
`ServerPlayer`), so the logic cannot live in a shared parent class. It lives in
an object, the companion's core, that the form carries:

- **the core**: identity (name, skin, UUID), owner, mode, home, and the rule
  that decides which form to take. It is saved in the form's data and moves
  from one form to the other with it;
- **the forms**: only what vanilla requires (type, saving, spawn packet,
  tick). They hand the rest to the core, through an interface both forms
  implement;
- **the Brain**: vanilla's AI system of villagers and piglins. It lives inside
  the form, as in any vanilla creature, and its memories are saved with it. It
  accepts any living entity. Its behaviours are written once, for "a living entity that is
  a companion form", and serve both forms;
- **the motor**: vanilla's pathfinding requires a `Mob`, and neither form is
  one. The motor turns the Brain's intentions (go there, look at that) into
  player inputs (move, jump, turn). Its design is still to come.

Fact: the world-player form announces the companion type by overriding
`getAddEntityPacket`, and both forms override `getType()` and `typeHolder()` to
carry the type `am_i_alone:companion` instead of `minecraft:player`. A private
field of `Entity` still holds `minecraft:player`, and a few methods read it
directly (default name, loot table, size, network update interval). For a
companion these values are a player's today, so they match; a test has to cover
this coupling.

Fact: `Player` takes its UUID back from its profile on every load. A stored form
reloaded with its chunk would change identity; the companion's profile (UUID,
name, skin) has to be saved and restored by the core.

### Open question: the skin and the name on the client

No profile is sent to the client (that is what keeps the companion out of Tab),
so the client knows neither its skin nor its name. Two options, to be measured:

| Option | What it takes |
|---|---|
| A synced entity data | The same declaration in both forms and in the client class, at the same rank: any offset breaks the sync |
| A Fabric attachment synced with the entity | A dedicated Fabric API; to be copied when the form switches |

## Saving

Decision: each form is saved where vanilla keeps that kind of thing.

| | Stored form | World-player form |
|---|---|---|
| Where | With its chunk, in the world's entity files | In the mod's data, saved with the world (a Fabric global attachment); never in the players' files |
| When | When vanilla saves or unloads the chunk | Before each world save (`BEFORE_SAVE`), and when the server stops |
| On restart | Comes back with its chunk | Recreated by the mod at startup, at its position, and loads its chunks again |

Chunks and the mod's data are written during the same save: a crash between two
saves brings both back to the same moment, with no companion lost or duplicated.
A crash while the save is being written is not covered; vanilla has the same
window between the players' files and the chunks.

Fact, met in the prototype: at startup, vanilla saves the world before the
server is declared started. Saving the world-player forms at that point would
overwrite the data with an empty list: it only happens once the world-player forms
are restored.

## Ruled out

| Option | Why it is ruled out |
|---|---|
| A fake connected player (like Carpet's bots) | Counted among the players, limited by `max-players`, shown in Tab unless worked around; a player's cost for each companion, even unowned; never stored with its chunk |
| A subclass of `Avatar` (like the Mannequin), or a mob drawn with the player model | Not Enough Animations, ETF, EMF Compat and Touch'n Motion check for `Player` or `AbstractClientPlayer`: they would not animate it |
| Loading chunks with a ticket (like ender pearls) | A ticket keeps chunks active, but what vanilla does around a player (monster spawning, among others) goes through the world's list of players |
| A mixin to hide a player from Tab, or to stop it loading chunks | No longer needed: the form settles chunk loading, and the absence of a profile settles Tab |
