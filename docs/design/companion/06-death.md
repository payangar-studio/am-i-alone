# Death

## In game

A companion that dies is dead for good, like a creature: it does not respawn.
The mods that change how death looks (Ragdoll Deaths, Simple Blood) apply to it
as they apply to a player.

## How it is built

Decision: death stays vanilla's in both forms, so that every mod that changes
death applies to it; the switch waits for it (`03-switch.md`).

- **Anchored form**: vanilla ticks it like any entity. `LivingEntity.tickDeath`
  sends the death event 60, then removes it (`RemovalReason.KILLED`) after 20
  ticks. `Player` and `ServerPlayer` do not override it.
- **Anchor form**: `ServerPlayer.tick()` does not call the body tick; vanilla
  calls `doTick()` from the connection's tick, which the fake connection never
  gets. The anchor form calls `doTick()` itself every tick, as the prototype
  did; otherwise it never dies.

Decision: when a companion in the anchor form dies, its entry in the mod's data
is removed, so that its owner's next connection does not bring it back
(`05-saving.md`).

Fact, read in the 26.3 code: `ServerPlayer.die` broadcasts a death message to
every player through the player list, and does not go through
`LivingEntity.die`; the anchored form sends no message.

Open question: the death message, the same in both forms: none (a creature), to
the owner only (as vanilla does for a tamed animal, not checked in 26.3), or to
the whole server (a player).

### Death mods

Fact, read in Ragdoll Deaths 1.2.1 sources and Simple Blood 1.3.1 bytecode:

- **Simple Blood works as is.** It reads hits and death from the synced health.
  For a `Player`, its creative and spectator check reads the profile, absent for
  a companion, so the check passes. It takes the blood colour from the skin only
  for downloaded skins, so a companion bleeds the default red.
- **Ragdoll Deaths creates the body** (captured when `deathTime > 0`, kept as a
  ghost on unload). But the companion is a `Player` on the client, so its
  `playerBodyUntilRespawn` setting (on by default) keeps the body until a living
  player with the same UUID appears, which never happens: the body stays until
  a level change (or until its chunk unloads, for a physics body), and holds a
  slot of `maxActiveRagdolls`.
- Neither mod injects into `handleRemoveEntities` or `handleAddEntity`. Ragdoll
  Deaths needs the real removal at death, which the switch never cuts into.

Decision: an issue will be opened on Ragdoll Deaths' repository (MIT,
github.com/Frostfleee/Ragdoll-Deaths), proposing to keep a body until respawn
only for a real connected player; not opened yet. Until then, a modpack can set
`playerBodyUntilRespawn=false`, which also affects real players.
