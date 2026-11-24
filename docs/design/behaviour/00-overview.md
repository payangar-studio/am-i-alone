# Behaviour

Not designed yet.

## In game

What a companion does on its own, according to its mode: follow its owner,
stay, wander, stay around its home, and behaviours that depend on what it is
doing.

## How it is built

What is known, at the macro level:

- **The Brain**: vanilla's AI system of villagers and piglins (memories,
  sensors, activities). It accepts any living entity, a companion included. Vanilla already has a memory for
  a home (`MemoryModuleType.HOME`) and activities such as idle, work, rest and
  fight.
- **The motor**: turns the Brain's intentions (go there, look at that) into
  movement (move, jump, turn). Vanilla's pathfinding requires a `Mob`, and a
  companion is not one (`../companion/01-entity.md`); some movement behaviours
  of vanilla are `Mob`-only too.

Open questions, designed with the first behaviour that moves (follow):

- the motor;
- moving a following companion to another dimension: an ordinary entity,
  with its ticket moved to the new dimension;
- whether hostile mobs attack a companion: vanilla's hostile mobs aim at
  players, which a companion is not on the server (inferred, not checked).
