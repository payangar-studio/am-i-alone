# Behaviour

Not designed yet.

## In game

What a companion does on its own, according to its mode: follow its owner,
stay, wander, stay around its home, and behaviours that depend on what it is
doing.

## How it is built

What is known, at the macro level:

- **The Brain**: vanilla's AI system of villagers and piglins (memories,
  sensors, activities). It accepts any living entity, so its behaviours are
  written once for both forms of a companion. Vanilla already has a memory for
  a home (`MemoryModuleType.HOME`) and activities such as idle, work, rest and
  fight.
- **The motor**: turns the Brain's intentions (go there, look at that) into
  player inputs (move, jump, turn). Vanilla's pathfinding requires a `Mob`, and
  neither form is one; some movement behaviours of vanilla are `Mob`-only too.

Open questions, designed with the first behaviour that moves (follow):

- the motor;
- moving a companion to another dimension, in either form: vanilla moves a
  `ServerPlayer` through its connection, which the anchor form only fakes.
