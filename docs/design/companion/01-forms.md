# The two forms

## In game

A companion is either at rest or keeping the world alive:

- **at rest** (anchored), it lives like a villager: it stays in its area, is
  saved with it, and freezes when the area unloads;
- **keeping the world alive** (anchor), it loads the world around it exactly
  like a player, with every effect of a player: it makes monsters spawn around
  it, and it counts for sleeping, so its owner places a bed for it too to skip
  the night.

Decision: a companion starts anchored, wherever it comes from. It becomes an
anchor when its owner puts it in follow mode, and anchored again in another
mode or without owner. What the owner and the modes decide, including what
happens when the owner disconnects, is in `../ownership/`.

The lifecycle, as a state diagram: spawned → anchored; follow mode → anchor;
another mode or no owner → anchored; owner disconnects → away; owner connects
→ anchor.

## How it is built

Decision: the names. **Anchored**: held by its chunk. **Anchor**: holds the
world loaded around it. They say what the form is to the world, not what it
does for its owner: another mode than follow may take the anchor form later.

| | Anchored form, `AnchoredCompanion` | Anchor form, `AnchorCompanion` |
|---|---|---|
| Extends | `Player`, without a connection, like vanilla's gametest mock player (`makeMockPlayer`) | `ServerPlayer`, added to the world (`level.addNewPlayer`) outside the list of connected players, with a fake connection that drops what is sent to it |
| Loads the world | No: frozen when its chunk unloads | Yes, exactly like a player |
| Connected player | No | No |
| Player of the world | No | Yes |
| Saved | With its chunk | By the mod (`05-saving.md`) |
| Cost | An entity's | A player's |

Why two: one vanilla class cannot be both. Fact, read in the 26.3 code: a world
registers as one of its players any `ServerPlayer` it starts tracking
(`ServerLevel`), and the chunk map loads chunks around it
(`ChunkMap.addEntity`); a player is never stored with a chunk.

Fact, read in the 26.3 code: a `ServerPlayer` gets its statistics and
advancements from the server's player list when it is built
(`getPlayerStats`, `getPlayerAdvancements`), which works outside the list; in
the prototype, the anchor form ticked, loaded chunks and saved without being in
it. Code that goes through the list of connected players (`@a`, a command
naming a player) does not see it.

Open question: changing dimension (a portal) in the anchor form, where vanilla
moves a `ServerPlayer` through its connection: to check.

Ruled out:

| Option | Why |
|---|---|
| One fake connected player for every companion (like Carpet's bots) | Counted among the players, limited by `max-players`, shown in Tab unless worked around; a player's cost for each companion, even unowned; never stored with its chunk |
| One entity loading chunks with a ticket (like ender pearls) | A ticket keeps chunks active, but what vanilla does around a player (monster spawning, among others) goes through the world's list of players |
| A mixin to stop a player from loading chunks | No longer needed: the form settles chunk loading |
