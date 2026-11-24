# The companion

## Requirements

1. **Animated like a player, owned or not.** The mods that animate players
   (EMF and ETF, Fresh Animations Player Extension, Not Enough Animations,
   Touch'n Motion, Better Combat, EMF Compat) animate it with no compatibility
   code on our side.
2. **Cheap when unowned.** A village tavern may hold several; they cost like
   villagers and freeze with their chunk.
3. **Like a player when it follows its owner.** In follow mode, it loads the
   world exactly as a player does, with every effect of a player: chunks,
   monster spawning, sleeping.
4. **Never a connected player.** Not counted among the players, takes no slot
   of the connection limit, not shown in Tab.
5. **Nothing lost, nothing duplicated.** Whatever happens to it (switching
   form, its owner leaving, the server stopping), a companion keeps exactly its
   inventory, its equipment and everything else it had.
6. **An invisible switch.** Changing form does not show in game.
7. **No mixin**: no change to Minecraft's own classes while a vanilla or Fabric
   API does the job.

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

| | Anchored form | Anchor form |
|---|---|---|
| What it is | An entity extending `Player`, without a connection, like the mock player of vanilla's gametests (`makeMockPlayer`) | A `ServerPlayer` added to the world (`level.addNewPlayer`) without going through the list of connected players, with a fake connection that drops what is sent to it |
| Loads the world | No: freezes when its chunk unloads | Yes, exactly like a player |
| Connected player | No | No |
| Player of the world | No | Yes |
| Cost | An entity's | A player's |

Decision: a companion takes the anchor form when its owner puts it in
follow mode, and the anchored form otherwise. The other modes are not designed
yet. A player has at most one companion in follow mode at a time.

Decision: the anchor form keeps every effect of a player, on purpose. It
counts for sleeping, so an owner who wants to skip the night places a bed for
their companion too; it makes monsters spawn around it.

Decision: when its owner disconnects, the companion leaves the world with them,
saved by the mod, and comes back where it was when they reconnect.

### Switching forms

Decision: a companion has one living form at any time, and the switch hands it
over whole. In one server tick, the current form is saved (inventory,
equipment, health, food, effects, position, Brain memories, the mod's data),
removed from the world, and the other form is created from that save, at the
same place and with the same UUID. No copy stays elsewhere: there is no separate
inventory to keep in sync. A test checks that a round trip between the forms
keeps the inventory and the equipment exactly, with nothing lost or duplicated.

### Open question: how to make the switch invisible

On the client, the switch is today the removal of an entity followed by the
arrival of another with the same UUID: the client builds a new
`ClientCompanion`, and what the client and the animation mods keep per entity
(interpolation, animation state) starts over. Making it invisible is a
requirement; how is still to be found and measured in game.

### The names of the two forms

Decision: **anchored** and **anchor**. The anchored form is held by its chunk;
the anchor form holds the world loaded around it ("anchor" is the usual name of
a chunk loader in mods). The names say what the form is to the world, not what
it does for its owner: another mode than follow may take the anchor form later.

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
  accepts any living entity, so its behaviours are written once, for "a living
  entity that is a companion form", and serve both forms;
- **the motor**: vanilla's pathfinding requires a `Mob`, and neither form is
  one. The motor turns the Brain's intentions (go there, look at that) into
  player inputs (move, jump, turn). Its design is still to come.

Fact: the anchor form announces the companion type by overriding
`getAddEntityPacket`, and both forms override `getType()` and `typeHolder()` to
carry the type `am_i_alone:companion` instead of `minecraft:player`. A private
field of `Entity` still holds `minecraft:player`, and a few methods read it
directly (default name, loot table, size, network update interval). For a
companion these values are a player's today, so they match; a test has to cover
this coupling.

Fact: `Player` takes its UUID back from its profile on every load. A anchored form
reloaded with its chunk would change identity; the companion's profile (UUID,
name, skin) has to be saved and restored by the core.

Decision: the skin and the name are one piece of data, defined once and carried
by whichever form the companion has, and synced to the client with the entity (a
Fabric attachment synced with the entity). Not a synced entity data declared in
both forms and in the client class, where any offset between the three breaks
the sync.

## Saving

Decision: each form is saved where vanilla keeps that kind of thing, never in
the players' files.

| | Anchored form | Anchor form |
|---|---|---|
| Where | With its chunk, in the world's entity files | In the mod's data, saved with the world (a Fabric global attachment) |
| When | When vanilla saves or unloads the chunk | When its owner disconnects, before each world save (`BEFORE_SAVE`), and when the server stops |
| Back in the world | With its chunk | When its owner connects, where it was |

Chunks and the mod's data are written during the same save: a crash between two
saves brings both back to the same moment, with no companion lost or duplicated.
A crash while the save is being written is not covered; vanilla has the same
window between the players' files and the chunks.

Fact, met in the prototype: at startup, vanilla saves the world before any
player has joined. A save of the mod's data must therefore only update the
companions that are in the world, and keep those whose owner is away; replacing
the whole list would erase them.

## Ruled out

| Option | Why it is ruled out |
|---|---|
| A fake connected player (like Carpet's bots) | Counted among the players, limited by `max-players`, shown in Tab unless worked around; a player's cost for each companion, even unowned; never stored with its chunk |
| A subclass of `Avatar` (like the Mannequin), or a mob drawn with the player model | Not Enough Animations, ETF, EMF Compat and Touch'n Motion check for `Player` or `AbstractClientPlayer`: they would not animate it |
| Loading chunks with a ticket (like ender pearls) | A ticket keeps chunks active, but what vanilla does around a player (monster spawning, among others) goes through the world's list of players |
| A mixin to hide a player from Tab, or to stop it loading chunks | No longer needed: the form settles chunk loading, and the absence of a profile settles Tab |
| A separate central inventory | One living form at a time, handed over whole, already rules out loss and duplication; a second copy would add the desync it was meant to prevent |
