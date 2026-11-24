# Switching forms

## What triggers a switch

Decision: the core decides. Its rule (anchor form in follow mode with an owner,
anchored form otherwise) is evaluated again whenever what it reads changes: the
mode, the owner. When the result differs from the current form, the core
switches.

## One living form, handed over whole

Decision: a companion has one living form at any time, and the switch hands it
over whole, in one server tick, in the world and at the place where the
companion is:

1. **Save** the current form with vanilla's entity save
   (`saveWithoutId`). Both forms are `Player`s, so the save holds the same
   things for both: inventory, equipment, ender chest, experience, abilities,
   health, food, effects, position, rotation, Brain memories, and the core.
2. **Remove** it from the world. The anchored form is removed as discarded
   (`RemovalReason.DISCARDED`, which vanilla does not save): its chunk forgets
   it, so it cannot come back next to the new form. The anchor form leaves the
   world's list of players (`removePlayerImmediately`).
3. **Create** the other form and load the save into it. The core restores the
   profile first (see `02-identity.md`), then the form takes the old form's
   entity id (`setId`), as vanilla does when a player respawns
   (`PlayerList.respawn`).
4. **Add** it to the world: `addFreshEntity` for the anchored form;
   for the anchor form, a fake connection first, then `level.addNewPlayer`.

No copy stays elsewhere, so there is nothing to keep in sync: no separate
inventory, no second copy of the equipment. A test checks that a round trip
between the forms keeps the inventory and the equipment exactly, with nothing
lost or duplicated.

## The client keeps its entity

Decision: the client entity is the companion's facade; the forms change behind
it, on the server only. The sequence:

1. The server sends a mod packet to the clients that see the companion: "the
   companion of entity id N changes form". A client that has an entity N marks
   it; one that has none ignores the packet.
2. The server saves the old form and removes it: vanilla sends the removal of
   N. A client that marked N ignores it.
3. The server creates and adds the new form with entity id N: vanilla sends
   its arrival. A client that marked N ignores it, keeps its `ClientCompanion`,
   and clears the mark.
4. The server sends the new form's data (health, equipment, position...), as
   for any entity: the client applies it to the same object.

The ignoring is done by a client mixin on the two handlers of
`ClientPacketListener` that receive these packets, `handleRemoveEntities` and
`handleAddEntity`, for marked entities only. No vanilla or Fabric API keeps an
entity on the client across a removal.

Fact, read in the 26.3 code: without it, the client removes its entity and
builds a new one (`ClientLevel.addEntity` discards an entity with the same id
first), and what the client and the animation mods keep on the entity starts
over (interpolation, walk animation, the state Not Enough Animations and the
Player Animation Library add to it): a Better Combat attack would be cut.

## Open questions

- **Checking it in game.** Once built: a switch in the middle of a Better
  Combat attack, and while walking, must not show.
- **A companion riding, dead or dying.** A switch while it rides a vehicle or
  carries a passenger, or while it is dying: dismount first, wait, or refuse.
- **A client that starts seeing the companion during the switch.** Whether it
  can receive the arrival without the notice, and what it then shows, is to
  check against how vanilla tracks entities.

## Ruled out

| Option | Why it is ruled out |
|---|---|
| A separate central inventory | One living form handed over whole already rules out loss and duplication; a second copy would add the desync it was meant to prevent |
| Handing the client state over to a new client entity | What the animation mods add to the entity is lost, unless every field is copied by reflection |
| Reviving the removed client entity | Vanilla never does it, and mods that clean up an entity's data on removal will have dropped it already |
