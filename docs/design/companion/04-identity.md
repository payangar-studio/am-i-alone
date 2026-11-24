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

Fact: they cannot be synched data of the companion. Any data the companion
declared would take id 17 or more (`ClassTreeIdRegistry.java:32`), which the
client's `ClientCompanion` uses for `Player`'s own data
(`Player.java:135-140`); a serializer that does not match makes the client fail
(`SynchedEntityData.java:119`).

Decision: the skin is a variant, chosen once when the companion is created and
never changed: the companion holds a reference to one entry of the mod's skins,
each entry a texture (`assets/am_i_alone/textures/entity/companion/<id>.png`)
and a model, wide or slim. The client builds its skin from it
(`ClientCompanion.getSkin`, `02-facade.md`).

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

Decision: names are data, like the skins: JSON lists shipped by the mod (for
example under `data/am_i_alone/companion_names/`: first names per group, last
names), read like vanilla's data. A datapack replaces a list by putting a file
at the same path (to change the language or the mood of a server), or adds
lists. The mod ships one default list.

Decision: the drawn name is part of the identity, defined once and carried by
the synced attachment. A name tag sets vanilla's custom name, which then wins
wherever the name is shown, the death message included (through
`getDisplayName`).

Not verified: how vanilla overrides or merges a plain JSON data file from a
datapack (as opposed to registries and tags). The exact format of the lists is
to be fixed when building.
