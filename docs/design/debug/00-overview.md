# Companion debug stick

The operators' debug mode: with it, an operator reaches any companion and
what a player could not do, such as changing its owner.

## In game

An item, the companion debug stick (`am_i_alone:debug_stick`; "Companion
Debug Stick", "Bâton de débogage des compagnons" in French), that only
operators can use: in anyone else's hand it does nothing. It is obtained by
`/give` only, in no creative tab. It looks like vanilla's debug stick, glint
included; its name tells them apart.

| Action | What it does | Removed when |
|---|---|---|
| Right-click on a companion | Opens the companion's screen with its Debug tab (`../companion/08-screen.md`), whoever owns it | Stays |

Decision: the Debug tab of the companion's screen carries the debug actions
(its owner, later changing it), rather than more gestures on the item.

Question: whether the stick ships in published versions of the mod (one build,
harmless since only operators use it and only `/give` gives it) or only in
development (the debug code kept apart from the rest).

## How it is built

Decision: "operator" is the gamemaster permission, the one vanilla's debug stick
checks (`Permissions.COMMANDS_GAMEMASTER`, `Player.java:1823-1825`), without
vanilla's creative mode requirement, so it can be tested in survival.
It is checked on the server.

Decision: the item handles the click itself (`Item.interactLivingEntity`).
Fact: `Player.interactOn` asks the entity first, then the item
(`Player.java:806-842`). The companion answers a right-click itself, but lets
it pass to the stick in an operator's hand (`../companion/08-screen.md`).

Decision: the item has no texture of the mod: its client item definition
(`assets/am_i_alone/items/debug_stick.json`) points to vanilla's debug stick
model (`minecraft:item/debug_stick`), and the item carries
`enchantment_glint_override`, as vanilla's does (`Items.java:2416-2423`).
