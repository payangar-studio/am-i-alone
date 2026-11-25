# Saving

## In game

A companion survives everything a world goes through: a restart, a crash, its
owner leaving. A following companion leaves with its owner when they
disconnect, and comes back with them, where it was.

## How it is built

Decision: a companion in the world is an ordinary entity, saved with its chunk
by vanilla. Its ticket is never saved: it is placed again when needed
(`03-loading.md`).

Decision: the mod's data (a Fabric global attachment, saved with the world)
keeps, for each owner, their following companion: its UUID, dimension and
position while it is in the world, and its whole entity save while it is away
with its owner. Never in the players' files.

Decision: its inventory is part of its entity save (`07-inventory.md`), so it
goes wherever the companion goes, with nothing more to write.

Step by step:

1. Owner disconnects (`ServerPlayConnectionEvents.DISCONNECT`): the companion's
   entity save is written to its owner's entry, its ticket is removed, and the
   companion is removed from the world (discarded, so its chunk forgets it).
   It is away with its owner.
2. World save: vanilla writes the chunks (with the companions in the world) and
   the mod's data, in the same save.
3. Owner connects (`ServerPlayConnectionEvents.JOIN`): the mod reads their
   entry, loads that chunk with a ticket as vanilla does for a joining player
   (`TicketType.PLAYER_SPAWN`), recreates the companion from its save if it was
   away (or finds it in its chunk), and places its ticket again.

Decision: an entry's saved entity is removed when the companion comes back; the
whole entry when it stops following, dies (`06-death.md`) or is removed for
good. An entry left behind would bring a second companion back.

A crash between two saves brings the chunks and the mod's data back to the same
moment, since they are written in the same save. A crash while the save is
being written is not covered; vanilla has the same window between the players'
files and the chunks.

Open question: whether vanilla runs the disconnection of every player when the
server stops, to check. If it does not, the companion stays saved with its
chunk where it was, and the owner's next connection finds it there through
the position kept in the entry.

Open question: an owner who never comes back. Their companion stays in the
mod's data, out of the world, for good; whether it should come back after a
while, without owner, is not decided.
