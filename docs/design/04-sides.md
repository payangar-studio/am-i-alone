# Sides: what the client sees

`mod.toml` (this project's description of the mod, from which the build writes
`fabric.mod.json`) sets `both-required`: the mod runs on the server and the
client.

## What makes the animation

Fact, read in the 26.3 code of these mods: they only act on an entity that is a
`Player`, often an `AbstractClientPlayer`, on the client. Not Enough
Animations, ETF, EMF Compat and Touch'n Motion check it explicitly; the Player
Animation Library attaches to every `Avatar`. The server does not need a
connected player: the client building a player is enough.

## How a client builds the facade

Decision: whatever the form behind it, the client builds a companion as a
remote player (`ClientCompanion`, a subclass of `RemotePlayer`). The steps when
a companion comes into view:

1. The server sends the spawn packet with the type `am_i_alone:companion`, for
   both forms (the anchor form overrides `getAddEntityPacket`, which would
   otherwise announce `minecraft:player`).
2. The client asks the factory of that type for an entity. On the client, the
   factory returns a `ClientCompanion`, as vanilla does for the Mannequin, whose
   client factory returns a `ClientMannequin`.
3. The server sends the player's synced data (health, pose, equipment...) and
   the identity, as a synced attachment (see `02-identity.md`).
4. The client draws it with the player renderer: vanilla picks it for any
   `AbstractClientPlayer` (`EntityRenderDispatcher`), so the companion gets the
   same renderer as real players, and the animation mods' hooks on it.

## Out of Tab

Decision: no profile is ever sent for a companion. A client lists a player in
Tab only from its profile (`ClientboundPlayerInfoUpdatePacket`), which vanilla
sends for connected players; the anchor form is not one, and the spawn packet
of the companion type needs no profile. So no companion appears in Tab, with no
code for it.

## What crosses between the sides

| From server to client | When | What for |
|---|---|---|
| Spawn packet, type `am_i_alone:companion` | The companion comes into view | The client builds a `ClientCompanion` |
| A player's synced data | Then, and on every change | Health, pose, equipment, as for any player |
| Identity (synced attachment) | Then, and on every change | Name and skin on the facade |
| Form switch notice (mod packet) | Just before a switch | The client keeps its entity (see `03-switch.md`) |

## Checked in game

Fact (prototype on branch `spike/companion-forms`): Fresh Animations Player
Extension (an EMF pack), Not Enough Animations, Better Combat and Touch'n Motion
animate the companion in both forms. ETF and EMF Compat were loaded but not
checked one by one. Better Combat animates an attack, but only starts one from a
real client's key press: making a companion attack will take code of ours.

## Ruled out

| Option | Why it is ruled out |
|---|---|
| A subclass of `Avatar` (like the Mannequin), or a mob drawn with the player model | Not Enough Animations, ETF, EMF Compat and Touch'n Motion check for `Player` or `AbstractClientPlayer`: they would not animate it |
| A mixin to hide a player from Tab | No longer needed: no profile is sent, so Tab never knows the companion |
