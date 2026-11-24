# The two forms

## What each form is

Decision: on the server, a companion always has exactly one of two forms.

| | Anchored form | Anchor form |
|---|---|---|
| What it is | An entity extending `Player`, without a connection, like the mock player of vanilla's gametests (`makeMockPlayer`) | A `ServerPlayer` added to the world (`level.addNewPlayer`) without going through the list of connected players, with a fake connection that drops what is sent to it |
| Loads the world | No: frozen when its chunk unloads | Yes, exactly like a player |
| Connected player | No | No |
| Player of the world | No | Yes |
| Saved | With its chunk | By the mod (see `05-saving.md`) |
| Cost | An entity's | A player's |

Why two: a companion must be as cheap as a villager when nobody needs it, and
load the world exactly like a player when its owner does. One vanilla class
cannot be both: any `ServerPlayer` in a world becomes a player of that world
(the world and the chunk map check `instanceof ServerPlayer`), and a player is
never stored with a chunk.

## When a companion takes each form

Decision: a companion takes the anchor form when its owner puts it in follow
mode, and the anchored form otherwise. The other modes are not designed yet. A
player has at most one companion in follow mode at a time.

The lifecycle, as a state diagram:

1. Spawned (in a tavern, a structure, or by a command): anchored.
2. Put in follow mode by its owner: anchor.
3. Another mode, or no owner any more: anchored again, in the chunk where it
   stands.
4. Its owner disconnects: the companion leaves the world with them (away),
   saved by the mod.
5. Its owner connects again: back in the world, anchor, where it was.

## What the anchor form keeps of a player

Decision: every effect of a player, on purpose. It counts for sleeping, so an
owner who wants to skip the night places a bed for their companion too; it
makes monsters spawn around it.

## The names

Decision: **anchored** and **anchor**. The anchored form is held by its chunk;
the anchor form holds the world loaded around it. The names say what the form
is to the world, not what it does for its owner: another mode than follow may
take the anchor form later.

## Ruled out

| Option | Why it is ruled out |
|---|---|
| One fake connected player for every companion (like Carpet's bots) | Counted among the players, limited by `max-players`, shown in Tab unless worked around; a player's cost for each companion, even unowned; never stored with its chunk |
| One entity, loading chunks with a ticket (like ender pearls) | A ticket keeps chunks active, but what vanilla does around a player (monster spawning, among others) goes through the world's list of players |
| A mixin to stop a player from loading chunks | No longer needed: the form settles chunk loading |
