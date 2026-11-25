# Companion debug stick

Scaffolding: an item to test what has no interaction in the game yet. Each of
its actions is removed once the real interaction exists.

## In game

An item, the companion debug stick (`am_i_alone:debug_stick`; "Companion
Debug Stick", "Bâton de débogage des compagnons" in French), that only
operators can use: in anyone else's hand it does nothing. It is obtained by
`/give` only, in no creative tab. It looks like vanilla's debug stick, glint
included; its name tells them apart.

| Action | What it does | Removed when |
|---|---|---|
| Right-click on a companion | Opens the companion's inventory screen (`../companion/07-inventory.md`) | The owner opens it by an interaction of the ownership feature (`../ownership/`) |

Decision: more actions will come on the same item, one per thing to test.

Question: whether the stick ships in published versions of the mod (one build,
harmless since only operators use it and only `/give` gives it) or only in
development (the debug code kept apart from the rest).

## How it is built

Decision: "operator" is the gamemaster permission, the one vanilla's debug stick
checks (`Permissions.COMMANDS_GAMEMASTER`, `Player.java:1823-1825`), without
vanilla's creative mode requirement, so it can be tested in survival.
It is checked on the server.

Decision: the item handles the click itself (`Item.interactLivingEntity`); the
companion knows nothing of it. Fact: a companion does not consume a
right-click, so the item gets it: `Player.interactOn` asks the entity first,
then the item (`Player.java:806-842`), and neither `LivingEntity`, `Avatar`
nor `Companion` answer it (`Entity.java:2317-2389`).

Decision: the item has no texture of the mod: its client item definition
(`assets/am_i_alone/items/debug_stick.json`) points to vanilla's debug stick
model (`minecraft:item/debug_stick`), and the item carries
`enchantment_glint_override`, as vanilla's does (`Items.java:2416-2423`).
