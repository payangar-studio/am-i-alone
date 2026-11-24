# Ownership and modes

Partly decided, not built yet: the first brick is the companion alone
(`../companion/`).

## In game

A player can make a companion their own, the way one tames a wolf, and give it
an order, its mode.

Decisions so far:

- **Follow**: the companion goes with its owner, into another dimension too.
  It is the only mode decided. Whether a companion follows its owner through a
  portal depends on its mode, not on its form.
- **One companion in follow mode per player** at a time.
- **It leaves with its owner**: when the owner disconnects, a following
  companion leaves the world with them, and comes back where it was when they
  connect again.
- **Every effect of a player**: a following companion counts for sleeping, so
  the owner places a bed for it too to skip the night.

Open questions: how a player makes a companion their own; the other modes
(stay, wander, stay around its home), which will come later.

## How it is built

The mode is part of the core. Follow mode is what makes a companion take the
anchor form (`../companion/01-forms.md`); leaving and coming back with the owner
is how the anchor form is saved (`../companion/05-saving.md`).
