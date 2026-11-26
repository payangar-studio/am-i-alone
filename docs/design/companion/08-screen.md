# The companion's screen

## In game

A right-click on a companion opens its screen: everything a player does with
it goes through there.

Decision: the layout, on every tab:

- At the top left, its armor slots, its view in 3D and its two designated
  weapon slots, the same sizes as the first inventory screen.
- At their right, its name as the main heading, with an icon of its group:
  pink for a woman, blue for a man (a name too long for the column ends with
  "…"). Right under the name, its armor and health bars; under the last row
  of hearts, the content of the tab.
- Below, the player's inventory, centred.
- The same padding on all four sides of the window, as vanilla's containers
  (7 pixels), and between the left part and the name column.
- A drawer slides out from under the window, on the right, as tall as the
  window, with two panels: its storage and its attributes. Each has a handle
  on the drawer's edge, the two stacked and sharing their border: a
  monochrome icon (a chest, bars), an arrow that points where the drawer will
  go, and a tooltip. The window stays whole, drawer out or not.

The drawer's handles:

| Click | What happens |
|---|---|
| A handle, drawer in | The drawer slides out on that panel |
| The open panel's handle | The drawer slides back in |
| The other handle, drawer out | The drawer shows that panel at once, without going in |

Decision: the open panel's handle takes the drawer's colour and stands 2
pixels further out, merged into the drawer; the tooltip names the panel, or
the action ("Put its inventory away") on the open one. Drawer in, both
handles show an arrow pointing out; drawer out, only the open panel's handle
does, pointing back. The handles are
buttons: they take the keyboard focus and are read by the narrator. The
drawer remembers its panel: the screen opens again with the drawer out on
it. When the drawer covers the window (a narrow screen), Escape puts it away
first.

Decision: the storage panel is the player's inventory turned on its side: 4
columns of 9 rows.

Decision: the attributes panel lists its attributes in three groups (Defence,
Movement, Hazards), the headings in white, each row a short label and its
value aligned right; hovering a row shows the attribute's full name, its
current value and its base. Not "statistics": that is vanilla's screen of the
player's counters.

| Attributes | Shown |
|---|---|
| Max health, armor, armor toughness, knockback resistance, movement speed | Always |
| Max absorption, water and ground movement efficiency, jump, step height, oxygen, burning time, explosion knockback resistance, safe fall distance, fall damage multiplier | Only when gear or an effect changes them from their default |
| Camera distance, name tag distances, waypoint range, gravity, scale, air drag, friction, bounciness, interaction range, attack knockback | Never: they mean nothing to a player looking at a companion |

Decision: values as vanilla writes them (`#.##`); knockback resistance ×10 as
item tooltips show it, movement speed as a percentage of normal, burning time
and fall damage as percentages. A value that differs from its default takes
the colour of its effect: green for a gain, red for a loss. Rows that overflow
the panel scroll with the wheel.

Tabs above the window, for real changes of page only:

| Tab | The content on the right shows | Who sees it |
|---|---|---|
| Companion | Its owner, and its mode (a list to pick from) once the modes exist | Whoever opens the screen |
| Debug | Debug options: its owner, later changing it | An operator holding the companion debug stick (`../debug/00-overview.md`) |

Decision: the drawer belongs to the Companion tab; switching to Debug puts it
away. The tabs share their border with each other, as the handles do.

Decision: when the drawer out does not fit on the screen, the whole screen
moves left to make room, as vanilla's recipe book moves the inventory; if
even that is not enough, the drawer covers part of the window.

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
| Right-click with the companion debug stick in the clicking hand, as an operator | Opens the screen with the Debug tab, whoever owns the companion; for anyone else, or in the other hand, the stick is any item |

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
inventory are its slots. Switching tabs or moving the drawer closes nothing:
the item held on the cursor stays, and the view does not move.

Decision: the server knows the open tab and the drawer's open panel. The
client tells it with vanilla's menu button packet
(`ServerboundContainerButtonClickPacket`, which the server checks against the
open menu and its validity before calling `clickMenuButton`,
`ServerGamePacketListenerImpl.java:2046-2059`). Unless the storage panel is
the open one, the storage slots take and give nothing on the server.

Fact: hiding a slot (`Slot.isActive`) only acts on the client's drawing and
hover (`Slot.java:92-94`, `AbstractContainerScreen.java:133,223,268`); the
server's clicks, shift-click and double-click gathering ignore it. Without the
check above, a modified client would reach the storage with the drawer in.

Decision: the Debug tab exists only when the server opened the screen with the
debug stick: the server decides it when it opens the menu and sends it with
the companion's entity id (Fabric's `ExtendedMenuType`). The menu's validity
adds: whoever opened it without the stick must still be the owner.

Fact: slot positions are fixed once the menu is built, so the storage slots
sit at the drawer's open position and show only once it is fully out; the
player's inventory cannot move either. The name column has room for the
hearts up to 200 health; from 221 the owner line reaches the player's
inventory label, from about 380 the hearts cover its slots. Companions do not
get that much health for now.

### Attributes on the client

Fact: on the client a companion is a player (`02-facade.md`), so its attribute
map is a player's (`Player.createAttributes`), with attributes the companion
does not have and other defaults: the panel lists a fixed set of the
companion's attributes (`LivingEntity.createLivingAttributes`), never the
client's map. The values of the syncable ones arrive by vanilla
(`ClientboundUpdateAttributesPacket`); knockback resistance is not syncable
(`Attributes.java:61`), and the equipment's modifiers are applied on the
server only.

Decision: knockback resistance travels through the menu's data
(`ContainerData`, ×1000, sent each time it changes), like a furnace's
progress.

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
| The storage as an Inventory tab, the right-hand area switching between its info and its 36 slots | Tabs are kept for real changes of page; the storage is a part of the same page, which the drawer adds without hiding anything |
| The storage as a second window beside the first, or as a panel replacing the info | The window must stay whole; the storage comes out of it, not next to it |
| A tall 3D view over the whole height, or the bars in the right column | Tried on mockups: the view out of proportion with the rest, the bars misaligned |
