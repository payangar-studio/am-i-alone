# The anchor

## In game

When a companion follows its owner, the world stays alive around it exactly as
around a player: the area around it stays loaded, monsters spawn there and
mobs do not despawn, and it counts for sleeping, so its owner places a bed for
it too to skip the night. Nothing else shows: nobody sees, touches, pushes or
attacks what does it.

## How it is built

Decision: a following companion has an anchor, `CompanionAnchor`, a hidden
`ServerPlayer` that stands at the companion's position, moved there every tick,
and does only what a player does to the world around it. It has no identity,
no inventory and no rendering of its own.

Decision: the anchor is added to the world (`level.addNewPlayer`) but never to
the server's list of connected players: it is a player of the world, not a
connected player. Its fake connection drops everything sent to it: a
disconnected connection would queue packets without limit
(`Connection.java:283`).

Lifecycle:

1. Created when the companion starts following (follow mode), or when its
   owner connects while it follows.
2. Moved to the companion's position every tick.
3. Removed when the companion stops following, when its owner disconnects, or
   when the companion dies.
4. Recreated at once if something kills it (`/kill @e` reaches it).

Until ownership is built, a test command attaches or detaches the anchor, and
records the player who runs it as the companion's owner, so that leaving and
coming back with the owner work as designed (`05-saving.md`).

Decision: a stable UUID per anchor, different from its companion's. Fact: two
entities sharing a UUID would crash `addNewPlayer`, which casts the existing
entity to a `ServerPlayer` (`ServerLevel.java:987-991`).

### What it does and does not do

| Effect | How | Source |
|---|---|---|
| Loads the world | Its player ticket, like any player of the world | `ChunkMap.java:1033`, `DistanceManager.java:109-115` |
| Natural monster spawning | Counts as a player nearby | `ChunkMap.java:997` |
| Mob despawning | Counts as a player nearby | `Mob.java:686` |
| Sleeping | Counts as an active player (see below) | `SleepStatus.java:39-45` |
| Seen by clients | Never: `broadcastToPlayer` answers false | `ChunkMap.java:1379` |
| Chunks sent to it | Never: chunks are sent only to connected players, so no entity is paired with it and it gets no block updates | `MinecraftServer.java:1129`, `ChunkMap.java:227` |
| Attacked by mobs | Never: survival with `abilities.invulnerable`, so `canBeSeenAsEnemy` is false, and `canBeSeenByAnyone` false | `Player.java:716`, `LivingEntity.java:940`, `TargetingConditions.java:64` |
| Collisions, projectiles | None: `isPickable`, `isPushable`, `canBeHitByProjectile` false | |
| Locator bar | Absent: `isTransmittingWaypoint` and `isReceivingWaypoints` false | `Player.java:219`, `ServerLevel.java:2022-2030` |
| Phantoms | Never: the time since rest only grows in the body tick, which the anchor never runs | `ServerPlayer.java:672`, `PhantomSpawner.java:40-42` |

Decision: survival with invulnerable abilities, not another game mode. Fact:
spectator would be left out of natural spawning (`ChunkMap.java:997`) and of
sleeping (`SleepStatus.java:40`); creative still collides.

### Sleeping

Fact: a non-spectator anchor counts as an active player for sleeping
(`SleepStatus.java:39-45`): with one real player and a 100% threshold, two
sleepers are needed. Vanilla's sleeping needs a real bed
(`LivingEntity.java:3773`) and the body tick the anchor never runs
(`ServerPlayer.java:581-620`).

Decision: when its companion sleeps in a bed, the anchor is marked asleep
(`setSleepingPos`, `isSleepingLongEnough` answering true) and the world's
sleeping count is updated (`updateSleepingPlayerList`). How a companion goes
to bed is designed with its behaviour (`../behaviour/`).

### Known side effects

- **Stats and advancements**: a `ServerPlayer` registers both in the player
  list's maps when it is built (`ServerPlayer.java:378-379`); they are not
  saved for a player outside the list (`PlayerList.java:622`), but they stay in
  the maps, hence a stable UUID per anchor. Advancement announcements go
  through the player list (`PlayerAdvancements.java:179`).
- **Accepted as player-like**: the wandering trader and patrols may pick the
  anchor as the player they spawn near (`WanderingTraderSpawner.java:76`,
  `PatrolSpawner.java:31`); monster spawners count it
  (`EntityGetter.java:100`).

Open question: experience orbs fly toward the anchor and are never collected
(`ExperienceOrb.java:157`). To solve without a mixin if possible.

Ruled out:

| Option | Why |
|---|---|
| Loading chunks with a ticket alone (like ender pearls) | A ticket keeps chunks active, but what vanilla does around a player (monster spawning, despawning, sleeping) goes through the world's list of players |
| A spectator anchor | Left out of natural spawning and of sleeping |
