# Switching forms

## In game

Nothing shows. A companion changing form keeps walking, attacking or standing
as it was, with everything it has: no flicker, no item lost, no item doubled.

## How it is built

### What triggers a switch

Decision: the core decides. Its rule (anchor form in follow mode with an owner,
anchored form otherwise) is evaluated again whenever what it reads changes: the
mode, the owner. Until ownership is built, a test command switches a
companion, and gives the anchor form the player who runs it as its owner, so
that saving works as designed (`05-saving.md`).

### One living form, handed over whole

Decision: one living form at any time, handed over whole in one server tick, in
the world and at the place where the companion is:

| Step | Anchored to anchor | Anchor to anchored |
|---|---|---|
| 1. Save | Vanilla's entity save (`saveWithoutId`): the same contents for both forms, since both are `Player`s: inventory, equipment, ender chest, experience, abilities, health, food, effects, position, rotation, Brain memories, the core | The same |
| 2. Remove | Discarded (`RemovalReason.DISCARDED`, which vanilla does not save): its chunk forgets it | Out of the world's list of players (`removePlayerImmediately`) |
| 3. Create | The core restores the profile; the anchor form loads the save and takes the old entity id (`setId`) | The same, for the anchored form |
| 4. Add | A fake connection, then `level.addNewPlayer` | `addFreshEntity` |

The entity id is reused as vanilla does when a player respawns
(`PlayerList.respawn`). The anchored form is discarded rather than unloaded so
that its chunk cannot save it and bring it back next to the new form.

Decision: a test checks that a round trip between the forms keeps the inventory
and the equipment exactly, with nothing lost or duplicated.

### The client keeps its entity

Decision: the client entity is the companion's facade; the forms change behind
it, on the server only. The sequence:

1. The server sends a mod packet to the clients that see the companion: "the
   companion of entity id N changes form". A client that has an entity N marks
   it; one that has none ignores the packet.
2. The server saves the old form and removes it: vanilla sends the removal of
   N, which a client that marked N ignores.
3. The server creates and adds the new form with entity id N: vanilla sends its
   arrival, which the client ignores too. The `ClientCompanion` stays the same
   object, and the mark is cleared.
4. The server sends the new form's data, as for any entity: the client applies
   it to the same object.

Decision: the ignoring is done by a client mixin on `handleRemoveEntities` and
`handleAddEntity` of `ClientPacketListener`, for marked entities only. No
vanilla or Fabric API keeps an entity on the client across a removal.

Fact, read in the 26.3 code: without it, the client removes its entity and
builds a new one (`ClientLevel.addEntity` discards an entity with the same id
first), and what the client and the animation mods keep on the entity starts
over: a Better Combat attack would be cut.

### Death and riding stay vanilla

Decision: the switch never cuts into a vanilla mechanic. A dying or dead
companion goes through vanilla's death in its current form, so that every mod
that changes death applies to it; a riding or ridden companion stays under
vanilla's riding. The switch waits until vanilla is done, and never stands in
for it.

### Open questions

- **What a companion's death is.** Vanilla's player death ends on a respawn
  that a client asks for; a companion has no client. What follows its death
  (it stays dead, it comes back, its items) is to decide, with vanilla's death
  left untouched.

- **Checking it in game**: a switch in the middle of a Better Combat attack,
  and while walking, must not show.
- **A client that starts seeing the companion during the switch**: whether it
  can receive the arrival without the notice, to check against how vanilla
  tracks entities.

### Ruled out

| Option | Why |
|---|---|
| A separate central inventory | One living form handed over whole already rules out loss and duplication; a second copy would add the desync it was meant to prevent |
| Handing the client state over to a new client entity | What the animation mods add to the entity is lost, unless every field is copied by reflection |
| Reviving the removed client entity | Vanilla never does it, and mods that clean up an entity's data on removal will have dropped it already |
