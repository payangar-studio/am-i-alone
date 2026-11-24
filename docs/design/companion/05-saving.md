# Saving

## In game

A companion survives everything a player's world goes through: a restart, a
crash, its owner leaving. At rest, it is saved with its area; keeping the world
alive, it leaves with its owner when they disconnect and comes back with them,
where it was.

## How it is built

Decision: each form is saved where vanilla keeps that kind of thing, never in
the players' files. In both cases, what is saved is the form's entity save,
which holds the core with the rest.

| | Anchored form | Anchor form |
|---|---|---|
| Where | With its chunk, in the world's entity files | In the mod's data, saved with the world (a Fabric global attachment): one entry per companion, keyed by its UUID, holding its entity save, which includes its dimension and position |
| When | When vanilla saves or unloads the chunk | When its owner disconnects, before each world save, and when the server stops |
| Back in the world | With its chunk | When its owner connects, where it was |
| Entry removed | Never: it has none | When the companion switches back to the anchored form, or is removed for good |

Why the mod saves the anchor form: a player is never stored in a chunk, and
vanilla only writes the files of connected players. Why its entry goes when the
companion switches back: an entry left behind would bring a second companion
back when its owner connects.

Step by step:

1. Owner disconnects (`ServerPlayConnectionEvents.DISCONNECT`): the anchor
   form's entity save is written to its entry, then the form leaves the world.
2. World save (autosave, `/save-all`, server stop): before vanilla writes
   anything (`ServerLifecycleEvents.BEFORE_SAVE`), the mod updates the entries
   of the anchor forms in the world and keeps those of the companions that are
   away. Vanilla then writes the chunks and the mod's data, in the same save.
3. Owner connects (`ServerPlayConnectionEvents.JOIN`): for each entry of a
   companion they own, the mod reads the dimension and position, loads that
   chunk with a ticket as vanilla does for a joining player
   (`TicketType.PLAYER_SPAWN`), creates the anchor form and adds it.

Fact, met in the prototype: at startup, vanilla saves the world before any
player has joined. A save must only update the companions present in the world
and keep the others; replacing the whole list would erase every companion whose
owner is away.

A crash between two saves brings the chunks and the mod's data back to the same
moment, since they are written in the same save. A crash while the save is
being written is not covered; vanilla has the same window between the players'
files and the chunks.

Open question: an owner who never comes back. Their anchor form stays in the
mod's data, out of the world, for good; whether it should come back after a
while, as an anchored form without owner, is not decided.
