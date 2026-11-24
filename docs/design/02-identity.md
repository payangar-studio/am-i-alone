# Shared identity: the core and the forms

## Why a core

Decision: the two forms extend different vanilla classes (`Player` for the
anchored form, `ServerPlayer` for the anchor form), so the logic cannot live in
a shared parent class: Java has one parent per class. It lives in an object,
the companion's core, that the current form carries.

| Part | What it holds or does |
|---|---|
| Core | Identity (UUID, name, skin), owner, mode, home, and the rule that picks the form. Saved in the form's data; it moves from one form to the other with it |
| Forms | Only what vanilla requires: their type, saving, the spawn packet, the tick. They hand the rest to the core, through an interface both forms implement |
| Brain | Vanilla's AI system of villagers and piglins, inside the form as in any vanilla creature; its memories are saved with the form. It accepts any living entity, so its behaviours are written once, for "a living entity that is a companion form", and serve both forms |
| Motor | Turns the Brain's intentions (go there, look at that) into player inputs (move, jump, turn): vanilla's pathfinding requires a `Mob`, and neither form is one. Open question, not designed yet |

## One identity, defined once

Decision: the identity (UUID, name, skin) is one piece of data, defined once in
the core, carried by whichever form the companion has, and synced to the
clients with the entity, as a Fabric synced attachment. Not a synced entity
data declared in both forms and in the client class, where any offset between
the three breaks the sync.

Fact, read in the 26.3 code: `Player` takes its UUID back from its profile on
every load. A form rebuilt from saved data would change identity; the core
restores the profile (UUID, name, skin) before the form uses it.

## The companion type

Fact: `Player` sets its type to `minecraft:player` in its constructor. Both
forms override `getType()` and `typeHolder()` to carry `am_i_alone:companion`,
which is what saving and the network read. A private field of `Entity` still
holds `minecraft:player`, and a few methods read it directly (default name,
loot table, size, network update interval). For a companion these values are a
player's today, so they match; a test covers this coupling.

## Open question: the motor

How the Brain's intentions become player inputs (a path, then moving, jumping
and turning along it) is not designed yet. It is the next brick after the forms.
