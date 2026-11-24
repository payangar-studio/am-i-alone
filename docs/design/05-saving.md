# Saving

## Where each form is saved

Decision: each form is saved where vanilla keeps that kind of thing, never in
the players' files. In both cases, what is saved is the form's entity save,
which holds the core with the rest (inventory, equipment, position...).

| | Anchored form | Anchor form |
|---|---|---|
| Where | With its chunk, in the world's entity files | In the mod's data, saved with the world (a Fabric global attachment): one entry per companion, keyed by its UUID, holding its entity save, which includes its dimension and position |
| When | When vanilla saves or unloads the chunk | When its owner disconnects, before each world save, and when the server stops |
| Back in the world | With its chunk | When its owner connects, where it was |
| Entry removed | Never (it has none) | When the companion switches back to the anchored form, or is removed for good |

Why the mod saves the anchor form: a player is never stored in a chunk, and
vanilla only writes the files of connected players, which the anchor form is
not.

Why the entry goes when the companion switches back: the anchored form is then
saved with its chunk; an entry left behind would bring a second companion back
when its owner connects.

## What happens, step by step

Owner disconnects (Fabric's `ServerPlayConnectionEvents.DISCONNECT`):

1. The anchor form's entity save is written to its entry.
2. The anchor form leaves the world. The companion is away with its owner.

World save (autosave, `/save-all`, server stop):

1. Before vanilla writes anything (Fabric's `ServerLifecycleEvents.BEFORE_SAVE`),
   the mod updates the entries of the anchor forms in the world, and keeps the
   entries of the companions that are away.
2. Vanilla writes the chunks (with the anchored forms) and the mod's data, in
   the same save.

Owner connects (Fabric's `ServerPlayConnectionEvents.JOIN`):

1. The mod finds the entries of the companions this player owns.
2. For each, it reads the dimension and the position from the entry, loads
   that chunk with a ticket, as vanilla does for a joining player
   (`TicketType.PLAYER_SPAWN`), creates the anchor form from the entry and adds
   it to the world.

## Edge cases

Fact, met in the prototype: at startup, vanilla saves the world before any
player has joined. A save must therefore only update the companions present in
the world and keep the others; replacing the whole list would erase every
companion whose owner is away.

A crash between two saves brings the chunks and the mod's data back to the same
moment, since they are written in the same save: no companion lost or
duplicated. A crash while the save is being written is not covered; vanilla has
the same window between the players' files and the chunks.

## Open question: an owner who never comes back

An anchor form whose owner never connects again stays in the mod's data, out of
the world, for good. Whether it should come back in the world after a while (as
an anchored form, without owner) is not decided.
