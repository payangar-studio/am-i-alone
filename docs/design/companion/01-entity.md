# The entity

Technical page: what a companion is on the server.

## In game

A companion is a character of the world like a villager: it is saved where it
stands, freezes when its area unloads, and dies like a creature.

## How it is built

Decision: on the server, a companion is one entity, `Companion`, extending
`Avatar`. `Avatar` is vanilla's player-shaped entity that is not a player: the
class the Mannequin and every player share. A companion is never a `Player` on
the server, and never changes class.

Why `Avatar`: what makes the animation is on the client (`02-facade.md`); the
server only needs an entity whose data a client player can read. Everything a
`Player` would bring on the server (a profile, a player's scoreboard name, a
player's death, a connection) is what the companion must not have.

Decision: the companion has its own entity type, `am_i_alone:companion`,
declared as `EntityType<Avatar>`: its factory returns a `Companion` on the
server and a `ClientCompanion` on the client, two different subclasses of
`Avatar`. Fact: `Player` extends `Avatar` (`Player.java:126`), so both fit.

Decision: the companion declares no synched data of its own. Its identity and
skin travel as a Fabric synced attachment (`04-identity.md`).

Decision: the companion sets the model customisation byte of `Avatar` (the
skin layers shown: hat, jacket, sleeves, trousers), as the Mannequin does
(`Mannequin.java:61`); its default, 0, would hide them
(`AvatarRenderer.java:164-170`).

Facts, read in the 26.3 code, that follow from not being a `Player`:

- Its UUID is its entity UUID, saved and loaded by vanilla like any entity.
- Its scoreboard name is its UUID (`Entity.getScoreboardName`,
  `Entity.java:3370`): it never collides with a player's name. `Player` would
  have used its profile name (`Player.java:1672`).
- It is saved with its chunk and frozen when the chunk unloads, like any
  entity.
- It is not a `Mob`: it has no goals and no pathfinding of its own. Its
  behaviour runs on vanilla's Brain, with a motor still to design
  (`../behaviour/`).

Decision: its attributes are a living entity's, as the Mannequin's
(`DefaultAttributes.java:140`), not a player's: a player's waypoint transmit
range would put every companion on every player's locator bar. A player's
ability to be found that way, if ever wanted, is chosen on purpose.
