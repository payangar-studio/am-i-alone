# Sides

Technical page, for the whole mod.

## How it is built

`mod.toml` (this project's description of the mod, from which the build writes
`fabric.mod.json`) sets `both-required`: the mod runs on the server and on the
client.

| Side | What runs there |
|---|---|
| Server | The companions' forms, their core and Brain, the switch between forms, saving |
| Client | The facade of each companion (`ClientCompanion`), drawn by the player renderer; the mixin that keeps it across a switch |

What crosses from the server to the client:

| What | When | What for | Detail |
|---|---|---|---|
| Spawn packet, type `am_i_alone:companion` | A companion comes into view | The client builds a `ClientCompanion` | `companion/04-facade.md` |
| A player's synced data | Then, and on every change | Health, pose, equipment | `companion/04-facade.md` |
| Identity (synced attachment) | Then, and on every change | Name and skin on the facade | `companion/02-identity.md` |
| Switch begins (mod packet) | Just before a switch | The client marks the entity and ignores its removal and arrival | `companion/03-switch.md` |
| Switch ends (mod packet) | Right after a switch | The client applies a removal still pending, then clears the mark | `companion/03-switch.md` |

Nothing goes from the client to the server yet.
