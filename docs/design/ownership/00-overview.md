# Ownership and modes

Partly decided, not built yet. Its first brick is recruitment
(`01-recruitment.md`): a player pays a companion without an owner in emeralds,
and owns it.

## In game

A player can make a companion their own by recruiting it, and give it
an order, its mode.

Decisions so far:

- **Recruitment**: a player makes a companion that has no owner their own by
  paying its price in emeralds, on its recruitment screen
  (`01-recruitment.md`). A companion that has an owner cannot be recruited.
  Its price follows its opinion of the player, made of the villagers' rumours
  (`02-reputation.md`).
- **Follow**: the companion goes with its owner, into another dimension too.
  It is the only mode decided. Whether a companion follows its owner through a
  portal depends on its mode.
- **One companion in follow mode per player** at a time.
- **It leaves with its owner**: when the owner disconnects, a following
  companion leaves the world with them, and comes back where it was when they
  connect again.
- **Not counted for sleeping**: a following companion keeps the world alive
  around it, but it is not a player, so it does not count among the sleepers
  needed to skip the night. (An earlier decision, that it counted and needed
  its own bed, is withdrawn.)

Open questions: other conditions to recruit a companion, a price that grows
with its equipment, an owner giving their companion to another player; the
other modes
(stay, wander, stay around its home), which will come later.

## How it is built

The owner and the mode are saved with the companion. Follow mode is what
makes the companion place its ticket (`../companion/03-loading.md`); leaving and
coming back with the owner is how a following companion is saved
(`../companion/05-saving.md`).
