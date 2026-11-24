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

Decision: death stays vanilla's. The companion, an ordinary living entity, dies
like one: `LivingEntity.die`, then `LivingEntity.tickDeath` sends the death
event 60 and removes it (`RemovalReason.KILLED`) after 20 ticks. Its anchor is
removed with it, and its entry in the mod's data too (`05-saving.md`).

Fact, read in the 26.3 code: a player's death message comes from the combat
tracker every living entity has (`CombatTracker.getDeathMessage`, line 88),
which turns the killing damage type into a translated message
(`death.attack.<msgId>`, fall variants, and so on); damage types are data, so a
mod that adds damage types or death messages changes what it returns.
`ServerPlayer.die` (line 880) then checks the `show_death_messages` game rule
and broadcasts the message according to the death message visibility of the
dying player's team: everyone, the team only, or everyone but the team.

Decision: the companion, not a `ServerPlayer`, does the same steps when it
dies: the same game rule, the same message from its combat tracker, the same
visibility rule of its team (`Entity.getTeam`), sent to the connected players.
The player list's team broadcasts take a `Player`, so the companion applies the
visibility rule over the connected players itself, with vanilla's team
settings.

Fact: a mod that changes the message itself, through damage types, translations
or the combat tracker, reaches companions. A mod that hooks `ServerPlayer.die`
directly does not.

### Death mods

Fact, read in Ragdoll Deaths 1.2.1 sources and Simple Blood 1.3.1 bytecode:

- **Simple Blood works as is.** It reads hits and death from the synced health.
  For a `Player` on the client, its creative and spectator check reads the
  profile, absent for a companion, so the check passes. It takes the blood
  colour from the skin only for downloaded skins, so a companion bleeds the
  default red.
- **Ragdoll Deaths creates the body** (captured when `deathTime > 0`, kept as a
  ghost on unload). But the companion is a `Player` on the client, so its
  `playerBodyUntilRespawn` setting (on by default) keeps the body until a living
  player with the same UUID appears, which never happens: the body stays until
  a level change (or until its chunk unloads, for a physics body), and holds a
  slot of `maxActiveRagdolls`.

Decision: an issue will be opened on Ragdoll Deaths' repository (MIT,
github.com/Frostfleee/Ragdoll-Deaths), proposing to keep a body until respawn
only for a real connected player; not opened yet. Until then, a modpack can set
`playerBodyUntilRespawn=false`, which also affects real players.
