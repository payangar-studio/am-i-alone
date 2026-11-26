# Sides

Technical page, for the whole mod.

## How it is built

`mod.toml` (this project's description of the mod, from which the build writes
`fabric.mod.json`) sets `both-required`: the mod runs on the server and on the
client.

| Side | What runs there |
|---|---|
| Server | The companions (`Companion`), their Brain, the tickets of following companions, saving |
| Client | The facade of each companion (`ClientCompanion`), drawn by the player renderer |

What crosses from the server to the client:

| What | When | What for | Detail |
|---|---|---|---|
| Spawn packet, type `am_i_alone:companion` | A companion comes into view | The client builds a `ClientCompanion` | `companion/02-facade.md` |
| Synched data, ids 0 to 16 (`Entity`, `LivingEntity`, `Avatar`) | Then, and on every change | Health, pose, equipment, main hand, skin layers | `companion/02-facade.md` |
| Identity (synced attachment) | Then, and on every change | Name and skin variant on the facade | `companion/04-identity.md` |
| Skin variants (registry) | When a client joins | The textures a skin variant names | `companion/04-identity.md` |
| Equipment (`ClientboundSetEquipmentPacket`) | When it comes into view, and on every change | Its armor on the facade | `companion/07-inventory.md` |
| Effects (`ClientboundUpdateMobEffectPacket`, `ClientboundRemoveMobEffectPacket`) | When it comes into view, and when one is added, updated or removed | The heart types of its health bar | `companion/08-screen.md` |
| Absorption (synced attachment) | When it comes into view, and on every change | The absorption hearts | `companion/08-screen.md` |
| Its screen's menu (opening with the entity id and the debug flag, then vanilla's slot sync and menu data) | When a player opens its screen, then on every change | The screen, its slots, its knockback resistance | `companion/08-screen.md` |

What goes from the client to the server:

| What | When | What for | Detail |
|---|---|---|---|
| Vanilla's container clicks | A player moves items in the companion's screen | Its armor, designated weapons, storage | `companion/07-inventory.md` |
| Vanilla's menu button packet | A player switches tabs or opens, switches or closes the drawer | The server knows the open tab and drawer panel | `companion/08-screen.md` |
