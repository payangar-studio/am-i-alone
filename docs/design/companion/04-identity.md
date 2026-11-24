# Identity, names and skins

## In game

A companion has a name and a skin. Its skin is one of the skins the mod ships,
chosen when it is created, and it never changes after, like a wolf's variant.

Decision: its name is a first name and a last name ("Aldric Fenwood"), drawn
at random when it is created and kept forever, like its skin. The first name
matches the skin: each skin belongs to a group (woman or man), first names are
listed per group, last names are shared by all. A name tag renames a
companion, as for any vanilla mob.

## How it is built

Decision: the identity is the companion's UUID (its entity UUID), its name and
its skin variant, saved with the entity. The name and the skin variant reach the
clients as a Fabric synced attachment of the entity, which Fabric sends to every
client that sees it.

Decision: the identity is drawn when the server creates the entity, before it
enters a world, so the spawn reaches the clients with it; drawn later, a client
first shows the default skin and the type's name. A companion loaded from its
chunk replaces the drawn one with its saved identity.

Fact: they cannot be synched data of the companion. Any data the companion
declared would take id 17 or more (`ClassTreeIdRegistry.java:32`), which the
client's `ClientCompanion` uses for `Player`'s own data
(`Player.java:135-140`); a serializer that does not match makes the client fail
(`SynchedEntityData.java:119`).

Decision: the skin is a variant, chosen once when the companion is created and
never changed: the companion holds a reference to one entry of the mod's skins,
each entry a texture and a model, wide or slim. The client builds its skin from
it (`ClientCompanion.getSkin`, `02-facade.md`).

Decision: an entry names its texture as an asset id (`texture`), not derived
from its own id. The mod's skins live under
`assets/am_i_alone/textures/entity/companion/`; until they exist, the
placeholder entries point at vanilla's default player textures without copying
them.

```json
{ "texture": "am_i_alone:entity/companion/<name>", "model": "wide", "group": "man" }
```

Fact, read in the 26.3 code: vanilla's entity variants (`pig_variant`,
`wolf_variant`...) are registries of entries, chosen at spawn
(`VariantUtils.selectVariantToSpawn`) and saved with the entity.

Decision: the mod's skins behave as vanilla's variants: a registry filled by
data (`am_i_alone:companion_skin`), so a datapack or another mod can add skins,
and the server sends the list to the clients.

Ruled out: a player's skin, which comes from Mojang's servers with a signed
profile, needs the network and ties a companion to an account.

Decision: each entry of the `am_i_alone:companion_skin` registry also carries
its group (woman or man), which the first name follows.

### Names

Decision: names are data, like the skins: a second registry filled by data,
`am_i_alone:companion_names`, which stays on the server. Its entries live at
`data/<namespace>/am_i_alone/companion_names/<entry>.json` (Fabric puts a
modded registry's entries under the registry's namespace). A datapack replaces
an entry by putting a file at the same path (to change the language or the mood
of a server), or adds entries. The mod ships one default set: first names of
women, first names of men, last names.

Each entry holds an optional group, first names and last names, each list
optional. First names without a group belong to every group; last names are
shared by all. A companion's first name is drawn among the first names of its
skin's group, its last name among all the last names.

```json
{ "group": "woman", "first_names": ["Adela", "Brenna"] }
{ "last_names": ["Ashdown", "Fenwood"] }
```

Decision: the drawn name is part of the identity, defined once and carried by
the synced attachment. A name tag sets vanilla's custom name, which then wins
wherever the name is shown, the death message included (through
`getDisplayName`).
