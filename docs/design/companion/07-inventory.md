# Inventory

## In game

A companion carries as much as a player: 36 slots of storage, four armor
slots, and two designated weapon slots, primary and secondary. It picks
nothing up yet.

Decision: no hotbar and no selected slot. A player chooses what they hold; what
a companion holds is its own choice, made by its brain (not designed yet,
`../behaviour/`): it will take in hand what it uses (its designated weapon to
fight, food to eat) and put it back after. Until then its hands stay empty.

Decision: the designated weapon slots are not its hands. Each takes one weapon
or one shield, and either slot takes either: a shield can be the primary. A
weapon is something that deals damage: swords, axes, spears, the mace, the
trident, bows and crossbows. Other mods' weapons and shields count when they
are tagged as such, as almost all are (Farmer's Delight's skillet included);
a modpack adds the others with a datapack (below). Tools (pickaxes, shovels,
hoes) are not weapons.

Decision: the armor it wears shows on it in the world, as on a player. Nothing
else of its inventory shows.

Decision: when it dies, it drops everything on the ground, as a player does:
storage, armor, designated weapons, and its hands once it holds something. `keepInventory` does not apply to
companions: a player keeps their items because they respawn, and a companion
does not (`06-death.md`). Items with the curse of vanishing are destroyed, as
for a player.

Decision: no block takes items from a companion: a hopper under it leaves it
alone.

Decision: the inventory screen shows the companion in 3D following the mouse,
its armor slots, its two designated weapon slots and its 36 slots, above the
inventory of the player who opened it; items move both ways. For now only an
operator opens it, with the companion debug stick (`../debug/00-overview.md`). It is drawn
with the kit of the mod's config screens (part of the socle, the base code
`mod update` maintains), with no texture of the mod: a scaffolding
look, which may change while testing.

Question: Tools on my Back (in grimoire-et-sortileges, the modpack the mod is
tested with) shows the tools a
player carries on their back. Whether it shows a companion's, and from which
slots, is not checked; it matters once a companion's weapons show.

### Grave mods

Decision: no compatibility code with grave mods. A companion drops its items
through vanilla's death path, so a grave mod that one day accepts entities
other than players will catch them with nothing to change here.

Fact, read on 2026-11-25 in their sources: every Fabric grave mod checked hooks
the death path a companion goes through (`LivingEntity.dropAllDeathLoot`) or a
player-only one, then keeps only players, and none has a setting or an API for
another entity.

| Mod | 26.x Fabric build | Where it keeps only players |
|---|---|---|
| Universal Graves 3.13.0 | Yes | `instanceof ServerPlayer` (`LivingEntityMixin.java:22`) |
| Gravestones (Pneumono) 1.4.3 | Yes | `instanceof Player` (`LivingEntityMixin.java:23`) |
| Your Items Are Safe | Yes | `instanceof ServerPlayer` (`ModFabric.java:40`) |
| You're in Grave Danger | No (1.21.1) | `instanceof ServerPlayer` (`LivingEntityMixin.java:20`) |
| Forgotten Graves | No (1.20.4) | Its mixin targets `Player` |
| Corpse, GraveStone Mod (henkelmax) | No Fabric | Player death event |

## How it is built

Decision: the armor is vanilla's equipment (`EntityEquipment`, the slots
`HEAD`, `CHEST`, `LEGS`, `FEET`), which every living entity has. Fact:
vanilla saves it under `equipment` (`LivingEntity.java:768-770`), sends it to
the clients that see the companion when it changes and when they start seeing
it (`LivingEntity.java:2911-2993`, `ServerEntity.java:314-327`), and `/item`
reaches it as `armor.*` (`LivingEntity.java:3925-3950`). The client facade
applies it with no code of ours (`ClientPacketListener.java:1475-1480`, then
`PlayerEquipment`).

Decision: the 36 storage slots and the two designated weapon slots are the
companion's own container, a field of the entity, as a villager holds its own
(`AbstractVillager.java:60`). Fact: vanilla's player `Inventory` cannot be
reused: its constructor takes a `Player` (`Inventory.java:59-66`).

Decision: the container is saved in the companion's entity save. It goes
wherever the entity save goes: in its chunk, and in its owner's entry while it
is away with them (`05-saving.md`), with nothing more to write.

Decision: the container is sent to a client only through the inventory
screen's menu, by vanilla's menu sync. Fact: vanilla never sends a player's
storage to other clients either; they get only the equipment
(`ServerPlayer.java:298-312`).

Decision: the companion is not a vanilla `Container`. Fact: hoppers, and the
hopper minecart, take only from entities that are one
(`HopperBlockEntity.java:391-393`, `EntitySelector.java:13`).

Decision: `/item` reaches the storage as `container.0` to `container.35`, as on
a player (`Player.java:1687-1726`), and the designated weapons as
`container.36` (primary) and `container.37` (secondary), through the entity's
`getSlot`. Fact: an entity answers no slot unless it says so
(`Entity.java:3577`).

Decision: the drop at death overrides `dropEquipment`, empty for a living
entity (`LivingEntity.java:1515-1516`): it destroys the items with the curse of
vanishing, then drops the container and the equipment, as `Player.dropEquipment`
does without its `keepInventory` check (`Player.java:553-568`). It runs inside
vanilla's `dropAllDeathLoot`.

### The inventory screen

Decision: a menu type of the mod, with Fabric's `ExtendedMenuType`, which sends
the companion's entity id to the client so the screen can draw it. Its slots:

| Slots | Built on | Accepts |
|---|---|---|
| Armor | Vanilla's `ArmorSlot`, which takes any living entity (`ArmorSlot.java:18-60`) | What fits that armor slot, as for a player |
| Designated weapons | A slot of the mod | One weapon or one shield |
| Storage | Plain slots | Anything |
| The player's inventory | Vanilla's standard inventory slots | Anything |

Decision: shift-click moves an item as in a player's inventory: from the
player, armor to its slot, a weapon or a shield to a free designated weapon
slot, anything else to the storage; from the companion, back to the player.

Decision: the client screen extends `AbstractContainerScreen` and draws itself
with the kit's `Draw` and `Theme`. Fact: they depend only on vanilla's drawing
and font classes, not on `ConfigScreen` (`vendor/gui/Draw.java`,
`vendor/gui/Theme.java`). The 3D view is vanilla's
`InventoryScreen.extractEntityInInventoryFollowsMouse`, public and open to any
living entity (`InventoryScreen.java:100`).

Decision: the menu stays valid while the companion is alive and the player
within 4 blocks of reach, as the horse's (`AbstractMountInventoryMenu.java:27-32`).
Fact: the server drops a click on an invalid menu
(`ServerGamePacketListenerImpl.java:1979-2012`) and closes it at the player's
next tick (`ServerPlayer.java:589-593`). Each case it covers:

| While the screen is open | Without the check |
|---|---|
| The companion dies | It drops everything at once, but stays 20 ticks before it is removed (`LivingEntity.java:1468`, `576-580`): items put in then are lost |
| Its chunk unloads, or it leaves with its owner | It is saved, then removed: an item taken after would be duplicated |
| It changes dimension | A copy is made in the new dimension and the old one removed (`Entity.java:3187-3194`): same duplication |

### What counts as a weapon

Decision: an item tag of the mod, `am_i_alone:companion_weapons`, decides what
the designated weapon slots accept. It is built from vanilla's tags
(`#minecraft:swords`, `#minecraft:axes`, `#minecraft:spears`,
`#minecraft:enchantable/weapon`) and Fabric's conventional ones
(`#c:tools/melee_weapon`, `ranged_weapon`, `bow`, `crossbow`, `trident`,
`mace`, `shield`, each with `required: false`). A modpack adds an item with a
datapack; removing one means replacing the whole tag.

Fact, read in the 26.3 data and the Fabric conventional tags 4.10.5: no tag or
component says "weapon or shield" alone. The `weapon` component is also on
pickaxes, shovels and hoes (`ToolMaterial.java:57`); bows and crossbows carry
no weapon component, only their class `ProjectileWeaponItem`; the shield is
marked by `blocks_attacks`. Vanilla has no shield or ranged weapon tag; the
`c:` tags list vanilla items by id, so `c:tools/melee_weapon` misses a modded
sword that is only in `#minecraft:swords`, which is why both families are in
the tag.

Fact, read in the jars of grimoire-et-sortileges: the tag accepts Farmer's
Delight's knives and skillet (in `#minecraft:enchantable/weapon`) and ReArm's
netherite bow, crossbow and shield (in `c:tools/bow`, `crossbow`, `shield`). No
other mod of the pack adds a weapon.

Ruled out:

| Option | Why |
|---|---|
| A hotbar and a selected slot whose item is in hand, as a player has | A player picks what they hold by hand; a companion's brain will pick it, so a hotbar would serve nobody |
| The designated weapon slots as its two hands | It would hold its weapon all the time; holding is the brain's choice, the designated weapon is what it reaches for |
| Vanilla's chest screen over the 36 slots (`ChestMenu`, four rows) | No client code, but no armor, no designated weapons, no view of the companion |
| A read-only screen fed by a snapshot | Items must be put in and taken out from the screen |
| Weapons recognised by their components (`weapon`, `blocks_attacks`, `ProjectileWeaponItem`) | Accepts pickaxes, shovels and hoes, which carry `weapon` too; nothing a datapack can adjust |
| The tag, plus components as a fallback, minus a tag of exclusions | It gives the same result as the tag alone on vanilla and on the pack, and needs a second tag. It only adds modded weapons that no tag lists, and it lets through a modded pickaxe that no tag lists |
