# Client facade

Technical page: how a client shows a companion.

## In game

A companion looks and animates like any other player, with the player
animation mods; it does not appear in the Tab list.

## How it is built

Fact, read in the 26.3 code of these mods: they only act on an entity that is a
`Player`, often an `AbstractClientPlayer`, on the client. Not Enough
Animations, ETF, EMF Compat and Touch'n Motion check it explicitly; the Player
Animation Library attaches to every `Avatar`. The server does not need a
connected player: the client building a player is enough.

Decision: whatever the form behind it, the client builds a companion as a
remote player, `ClientCompanion`, a subclass of `RemotePlayer`. When a
companion comes into view:

1. The server sends the spawn packet with the type `am_i_alone:companion`, for
   both forms: the anchor form overrides `getAddEntityPacket`, which would
   otherwise announce `minecraft:player`.
2. On the client, the factory of that type returns a `ClientCompanion`, as
   vanilla does for the Mannequin, whose client factory returns a
   `ClientMannequin`.
3. The server sends the player's synced data (health, pose, equipment) and the
   identity, as a synced attachment (`02-identity.md`).
4. Vanilla draws any `AbstractClientPlayer` with the player renderer
   (`EntityRenderDispatcher`): the companion gets the same renderer as real
   players, with the animation mods' hooks on it.

Decision: no profile is ever sent for a companion. A client lists a player in
Tab only from its profile (`ClientboundPlayerInfoUpdatePacket`), which vanilla
sends for connected players; the anchor form is not one, and the spawn packet
of the companion type needs no profile.

Fact (prototype on branch `spike/companion-forms`): Fresh Animations Player
Extension (an EMF pack), Not Enough Animations, Better Combat and Touch'n
Motion animate the companion in both forms. ETF and EMF Compat were loaded but
not checked one by one. Better Combat animates an attack, but only starts one
from a real client's key press: making a companion attack will take code of
ours.

Ruled out:

| Option | Why |
|---|---|
| A subclass of `Avatar` (like the Mannequin), or a mob drawn with the player model | Not Enough Animations, ETF, EMF Compat and Touch'n Motion check for `Player` or `AbstractClientPlayer`: they would not animate it |
| A mixin to hide a player from Tab | No longer needed: no profile is sent |
