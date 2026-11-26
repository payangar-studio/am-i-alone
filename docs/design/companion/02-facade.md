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
player: the client building one is enough.

Decision: the client builds a companion as `ClientCompanion`, a subclass of
`RemotePlayer`, the class a client uses for the other players it sees. When a
companion comes into view:

1. The server sends vanilla's spawn packet with the type
   `am_i_alone:companion` (`Entity.getAddEntityPacket`, `Entity.java:3803`).
2. The client handles only the player type specially and builds every other
   type with its factory (`ClientPacketListener.java:605-614`): the factory of
   the companion type returns a `ClientCompanion`, as vanilla's Mannequin
   factory returns a `ClientMannequin` on the client.
3. The server sends the companion's synched data and its identity (a synced
   attachment, `04-identity.md`).
4. Vanilla draws any `AbstractClientPlayer` with the player renderer
   (`EntityRenderDispatcher.java:92`): the companion gets the same renderer as
   real players, with the animation mods' hooks on it. It joins the client's
   list of players (`ClientLevel.java:1286`), which those mods read.

Fact: the synched data of a server `Companion` fits a client `ClientCompanion`.
Each class numbers its data after its parent's (`ClassTreeIdRegistry.java:32`):
`Entity` 0 to 7, `LivingEntity` 8 to 14, `Avatar` 15 and 16, then `Player` 17
to 20 (absorption, score, shoulder parrots), which the client alone has. The
server sends only 0 to 16, with the same serializers on both sides; the client
fails only on a serializer that does not match
(`SynchedEntityData.java:119`), and keeps its defaults for 17 to 20 (no
absorption, no parrots), which is right for a companion.

Decision: `ClientCompanion` overrides what a client player reads from a profile
it does not have:

| Method | Vanilla answer without a profile | `ClientCompanion` answer |
|---|---|---|
| `getSkin()` | The default skin for the UUID (`AbstractClientPlayer.java:59`) | The skin variant's texture and model (`04-identity.md`) |
| `getName()` | The name given at construction (`Player.java:1610`) | The companion's name |
| `getScoreboardName()` | The profile name | Its UUID, as on the server, so the client finds its team (name colour) |
| `shouldShowName()` | Always true (`Player.java:1597`): a player's name shows at all times | Its custom name's visibility, or whether the local crosshair is on it (`04-identity.md`) |

Fact, read in the 26.3 code: the player renderer applies the mob rule
(`AvatarRenderer.java:186-188`, as `MobRenderer.java:14-16`): the name shows
when `shouldShowName()` is true, or when the entity has a custom name and is
under the crosshair. Only `Player`'s answer makes a player's name permanent;
the Mannequin, drawn by the same renderer, keeps `LivingEntity`'s answer and
behaves as a mob. Only the renderers call `shouldShowName()`. The crosshair
target is computed every frame (`Minecraft.java:3088-3097`) within the entity
interaction range (`LocalPlayer.java:1222-1261`).

Question, to check in game: the crosshair can target a companion.
`Player.isPickable` refuses a spectator (`Player.java:1640`); a client player's
game mode comes from its profile, which a companion lacks, so it should not
count as one (inferred).

Fact: on the client, `getType()` answers `minecraft:player`, which `Player`'s
constructor sets (`Player.java:177`). Rendering does not read it; only code on
the client that reads an entity's type would see a player there.

Decision: no profile is ever sent for a companion. A client lists a player in
Tab only from its profile (`ClientboundPlayerInfoUpdatePacket`); a companion
never has one, so it is never in Tab, with no code for it.

Fact (prototype on branch `spike/companion-forms`): Fresh Animations Player
Extension (an EMF pack), Not Enough Animations, Better Combat and Touch'n Motion
animate a client companion built as a `RemotePlayer`. ETF and EMF Compat were
loaded but not checked one by one. Better Combat animates an attack, but only
starts one from a real client's key press: making a companion attack will take
code of ours.

Fact, read in Better Combat 3.2.2: the pose of a held weapon at rest, for a
player seen by another client, comes from two attachments Better Combat sets on
the server, only for a `Player` (`PlayerEntityMixin.java:44-55`). A companion
holding a weapon shows it without that pose.

Decision: what Better Combat needs from a companion (the weapon pose, the
attacks) goes in an adapter, kept apart in the mod so it can move to a mod of
its own later. Better Combat stays optional: the mod works without it. Not
built yet.

Ruled out:

| Option | Why |
|---|---|
| A client entity extending `Avatar` (like the Mannequin), or a mob drawn with the player model | Not Enough Animations, ETF, EMF Compat and Touch'n Motion check for `Player` or `AbstractClientPlayer`: they would not animate it |
| A renderer of our own for the companion type, to show its name as a mob's | The dispatcher sends every `AbstractClientPlayer` to the player renderers before looking up the type (`EntityRenderDispatcher.java:90-95`); it would also lose the animation mods' hooks |
| A mixin on the player renderer's name rule | Not needed: overriding the entity's `shouldShowName()` is enough |
