# Shared identity

## In game

A companion is the same character whatever its form: same name, same skin,
same inventory, same memory.

## How it is built

Decision: the two forms extend different vanilla classes (`Player` and
`ServerPlayer`), so the logic cannot live in a shared parent class: Java has
one parent per class. It lives in `Companion`, the core, that the current form
carries.

| Part | What it holds or does |
|---|---|
| Core (`Companion`) | Identity (UUID, name, skin), owner, mode, home, and the rule that picks the form. Saved in the form's data; it moves from one form to the other with it |
| Forms (`CompanionForm`) | Only what vanilla requires: their type, saving, the spawn packet, the tick. They hand the rest to the core |
| Brain | Vanilla's AI, inside the form as in any vanilla creature; its memories are saved with the form; its behaviours are written once for both forms (see `../behaviour/`) |

Decision: the identity (UUID, name, skin) is one piece of data, defined once in
the core, carried by the current form and synced to the clients as a Fabric
synced attachment. The skin is a variant, chosen once when the companion is created and never
changed: the core holds a reference to one entry of the mod's skins, each entry
a texture (`assets/am_i_alone/textures/entity/companion/<id>.png`) and a
model, wide or slim. A profile carries no skin for a companion.

Fact, read in the 26.3 code: vanilla's entity variants (`pig_variant`,
`wolf_variant`...) are registries of entries, chosen at spawn
(`VariantUtils.selectVariantToSpawn`) and saved with the entity.

Open question: the mod's skins as a registry filled by data, like vanilla's
variants (a datapack or another mod can add skins, and the server sends the
list to the clients), or as a list fixed in the mod's code. Not a synced entity data declared in both forms and in the
client class, where any offset between the three breaks the sync.

Fact, read in the 26.3 code: `Player` takes its UUID back from its profile on
every load (`readAdditionalSaveData`), and its profile is a final field set by
its constructor. A form rebuilt from saved data would change identity. So each
form reads its core from the save right after vanilla's own reading, puts the
saved UUID back, and answers `getGameProfile()` (public, overridable) with the
core's profile rather than the one its constructor received. It holds both for
an anchored form a chunk loads, which vanilla builds from the entity type's
factory before reading its save.

Ruled out for the skin: a player's skin, which comes from Mojang's servers
with a signed profile, needs the network and ties a companion to an account.

Fact: `Player` sets its type to `minecraft:player` in its constructor. Both
forms override `getType()` and `typeHolder()` to carry `am_i_alone:companion`,
which is what saving and the network read. A private field of `Entity` still
holds `minecraft:player`, and a few methods read it directly (default name,
loot table, size, network update interval). These values are a player's today,
so they match; a test covers this coupling.
