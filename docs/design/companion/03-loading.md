# World loading

## In game

When a companion follows its owner, the world around it lives exactly as around
a player: chunks stay loaded, entities move, machines run, crops grow. When
nobody needs it, it freezes with its area like any creature.

## How it is built

Fact, read in the 26.3 code: a player keeps the world alive only through
tickets. `DistanceManager.addPlayer` places a `PLAYER_SIMULATION` ticket
(simulate and keep-dimension-active flags) at level 31 minus the simulation
distance, and `PLAYER_LOADING` tickets (loading flag) on the chunks within view
distance (`DistanceManager.java:109-133`, `267-283`).

Fact: everything that makes a chunk live depends on tickets only, never on a
player being there:

| What lives in a chunk | Decided by | Reference (26.3) |
|---|---|---|
| Entities move and act | The chunk's ticket level: entity ticking | `ServerLevel.java:438` |
| Scheduled block and fluid ticks (redstone, water...) | The chunk's ticket level: block ticking | `ServerLevel.java:478` |
| Random ticks (crops growing, leaves, ice...) | The chunks of the simulation tracker, fed by tickets; no player check | `ServerChunkCache.java:395`, `ChunkMap.java:940-950`, `ServerLevel.tickChunk` (`:503`) |
| The dimension stays awake | Any ticket with the keep-dimension-active flag | `ServerLevel.java:416-426` |

Decision: the mod registers its own `TicketType` (its constructor
`new TicketType(0L, flags)` is public; registered in
`BuiltInRegistries.TICKET_TYPE` like vanilla's) with the loading, simulation
and keep-dimension-active flags. A following companion places it at its chunk,
with a player's radius (the simulation distance plus 2; the view distance for
loading if it is a separate ticket), and moves it each time it changes chunk.

1. The ticket is placed when follow mode starts, or when the owner of a
   following companion connects.
2. It moves each time the companion changes chunk.
3. It is removed when follow mode ends, when the owner disconnects (the
   companion leaves with them), or when the companion dies.

Inferred, not tested: a ticket asks for its whole area at once, where a
player's loading tickets are spread over time chunk by chunk.

### What a ticket does not bring

Fact, read in the 26.3 code: some things only happen near a real player,
whatever the tickets.

| What only a real player brings | Why | Reference (26.3) |
|---|---|---|
| Natural spawning of monsters and animals | Spawn chunks are counted from the players, and the spawner needs a nearest player in the world's list | `ChunkMap.java:926-937`, `:969-975`, `NaturalSpawner.java:176-179` |
| Despawning of mobs | Measured from the nearest real player | `Mob.java:686-702` |
| Thunder strikes, inhabited time | Only in spawn chunks | `ServerChunkCache.java:403-410` |
| Fire spread | Needs a player within 128 blocks by default | `ServerLevel.java:1927-1929` |

Decision: in follow mode they come from the owner, who is near the companion by
definition; when the owner disconnects, the companion leaves with them, ticket
included. Nothing is added for them now.

Open question: a future mode that keeps the world alive far from any player
would need despawning (and maybe spawning) around the companion. Vanilla has no
API for it. Lead, not checked in detail: one targeted mixin in
`Mob.checkDespawn`, where it looks up the nearest player; spawning would need
several injection points.
