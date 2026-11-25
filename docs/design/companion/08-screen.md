# The companion's screen

## In game

A right-click on a companion opens its screen: everything a player does with
it goes through there. One window with tabs: the companion's view, armor and
designated weapons, and the player's inventory below, stay in place on every
tab; only the area on the right changes.

| Tab | The area on the right shows | Who sees it |
|---|---|---|
| Companion | Its name, its health and armor bars, its owner, and its mode (a list to pick from) once the modes exist | Whoever opens the screen |
| Inventory | Its 36 storage slots (`07-inventory.md`) | Whoever opens the screen |
| Debug | Debug options: its owner, later changing it | An operator holding the companion debug stick (`../debug/00-overview.md`) |

Decision: the armor and the designated weapons are managed from every tab,
the Companion tab included; the Inventory tab only adds the storage.

Decision: the screen shows only what works. Until the modes exist, the
Companion tab has no mode list; until the owner can be changed, the Debug tab
only shows the current owner.

Decision: the mode is a list: a click opens the possible modes, one is picked
(`../ownership/`). The modes themselves are not designed yet.

Decision: the gestures, whatever the player holds:

| Gesture | What happens |
|---|---|
| Right-click | Opens the screen, for its owner only; for anyone else, nothing |
| Sneak and right-click | The held item acts as on any entity (a name tag renames it, a splash potion is thrown) |
| Right-click holding the companion debug stick, as an operator | Opens the screen with the Debug tab, whoever owns the companion; for anyone else the stick is any item |

Fact: a companion cannot be leashed, as a villager: only entities that are
`Leashable` can (`Mob.java:100`), and a companion is not a `Mob`.

Until ownership exists (`../ownership/`), no companion has an owner, so only
the debug stick opens the screen.

Decision: the health and armor bars look and behave exactly as the player's
own in the HUD: the same hearts (half hearts, rows, hardcore, poisoned,
withered, frozen, absorption, the blink when hit and the bounce of
regeneration) and the same armor icons. They follow the companion live: a hit
while the screen is open takes the hearts away as on the player's bar. A
resource pack that retextures them applies.

Decision: mods that change how the HUD draws hearts or armor (Overflowing Bars,
Colorful Hearts) do not apply to the companion's bars. A compatibility with
Overflowing Bars comes later, as an optional adapter.

## How it is built

### One menu, tabs on the client

Decision: the screen is one container menu, the one of the inventory screen
grown (`07-inventory.md`): armor, designated weapons, storage and the player's
inventory are its slots from the first tab. Switching tabs closes nothing: the
item held on the cursor stays, and the view does not move.

Decision: the server knows the open tab. The client tells it with vanilla's
menu button packet (`ServerboundContainerButtonClickPacket`, which the server
checks against the open menu and its validity before calling
`clickMenuButton`, `ServerGamePacketListenerImpl.java:2046-2059`). Outside the
Inventory tab, the storage slots take and give nothing on the server.

Fact: hiding a slot (`Slot.isActive`) only acts on the client's drawing and
hover (`Slot.java:92-94`, `AbstractContainerScreen.java:133,223,268`); the
server's clicks, shift-click and double-click gathering ignore it. Without the
check above, a modified client would reach the storage from any tab.

Decision: the Debug tab exists only when the server opened the screen with the
debug stick: the server decides it when it opens the menu and sends it with
the companion's entity id (Fabric's `ExtendedMenuType`). The menu's validity
adds: whoever opened it without the stick must still be the owner.

### Opening

Decision: the companion answers the right-click itself (`interact`), before
any item: it opens the screen for its owner and lets the click pass when the
player sneaks, or holds the debug stick with the gamemaster permission. The debug stick opens the screen with
the Debug tab from its own `interactLivingEntity`, which vanilla calls when the
entity let the click pass (`Player.java:806-842`).

### Health and armor bars

Fact, read in the 26.3 code: vanilla draws the player's hearts and armor in
private methods of `Hud` (`extractHearts`, `extractArmor`, `Hud.java:817-896`),
tied to the camera player and to the HUD's position. Fabric API wraps their
call sites in the HUD (`HudMixin`); Overflowing Bars and Colorful Hearts
replace those HUD elements and draw for the camera player only.

Decision: the screen draws the bars itself, with vanilla's sprites
(`minecraft:hud/heart/*`, `minecraft:hud/armor_*`) and vanilla's rules (rows,
row height, absorption, heart types, hardcore, low-health wobble, regeneration
bounce, blink), its own blink state kept by the screen.

Fact: what a client knows of a companion for the bars:

| Data | Reaches the client | By |
|---|---|---|
| Health, max health | Yes | Synched health; `MAX_HEALTH` is a synced attribute (`Attributes.java:65-70`) |
| Armor value | Yes | `ARMOR` is a synced attribute, with the equipment's modifiers applied on the server (`AttributeMap.java:26-31`) |
| Frozen | Yes | Synched data (`Entity.java:280`) |
| Hit (blink) | Yes | `ClientboundDamageEventPacket` to the clients that see it (`ServerLevel.java:1203-1205`) |
| Effects (poison, wither, regeneration) | No | Vanilla sends them to the player concerned and to riders only (`LivingEntity.java:1074-1079`) |
| Absorption | No | Only `Player` syncs it, as synched data a companion cannot declare (`01-entity.md`) |

Decision: the companion sends its effects to the clients that see it, with
vanilla's own packets (`ClientboundUpdateMobEffectPacket`,
`ClientboundRemoveMobEffectPacket`), when an effect is added, updated or
removed, and all of them when a client starts seeing it. Fact: the client
applies them to any living entity (`ClientPacketListener.java:1746-1758`,
`1902-1906`).

Decision: its absorption travels as a Fabric synced attachment, as its
identity does (`04-identity.md`).

Ruled out:

| Option | Why |
|---|---|
| A hub screen whose Inventory button opens another screen | Each switch goes through the server and recentres the cursor; the item held on the cursor goes back to the inventory |
| The inventory first, with the orders as buttons beside the slots (most companion mods) | The screen must welcome modes and other features; the inventory is one part of it |
| Drawing the HUD's own health and armor elements for the companion | Only possible as a workaround: swapping the camera entity at each frame, Fabric's internal classes, vanilla's private methods by reflection. Overflowing Bars and Colorful Hearts keep their blink and displayed health in one shared object, so the companion's bar and the player's own would disturb each other |
| Mode buttons (Follow, Stay) | Replaced by a list: the modes will grow |
