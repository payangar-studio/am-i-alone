# Death

## In game

A companion that dies is dead for good, like a creature: it does not respawn.
The mods that change how death looks (Ragdoll Deaths, Simple Blood) apply to it
as they apply to a player.

Decision: every player sees the death message of a companion, exactly as for a
player ("Bob was slain by Zombie", with every variant vanilla has), following
the same rules (the death messages game rule, team visibility). Nothing custom:
a mod that adds death messages adds them for companions too.

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

Fact, read in the 26.3 code: a player's death message comes from the combat
tracker every living entity has (`CombatTracker.getDeathMessage`, line 88),
which turns the killing damage type into a translated message
(`death.attack.<msgId>`, fall variants, and so on); damage types are data, so
a mod that adds damage types or death messages changes what it returns.
`ServerPlayer.die` (line 880) then checks the `show_death_messages` game rule,
sends the message to the dying player (`ClientboundPlayerCombatKillPacket`),
and broadcasts it through the player list according to the team's death
message visibility: everyone, the team only, or everyone but the team
(`broadcastSystemMessage`, `broadcastSystemToTeam`,
`broadcastSystemToAllExceptTeam`, which take any `Player`).

Decision: the anchor form, a `ServerPlayer`, goes through `ServerPlayer.die`
as is: the packet to itself goes to its fake connection and is dropped, the
broadcast reaches every connected player. The anchored form, a `Player` but not
a `ServerPlayer`, overrides `die` to run vanilla's death (`super.die`), then
the same steps: the same game rule, the same message from its combat tracker,
the same broadcast by team visibility. Only the packet to the dying player is
left out: nobody plays it.

Fact: a mod that changes the message itself, through damage types, translations
or the combat tracker, reaches both forms. A mod that hooks `ServerPlayer.die`
directly reaches only the anchor form.

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
