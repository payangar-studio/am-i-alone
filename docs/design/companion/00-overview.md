# Companion

The character itself, the first feature to build: owner and modes come later
(`../ownership/`).

## In game

A companion looks, moves and animates like a player: the player animation mods
(Fresh Animations, Not Enough Animations, Touch'n Motion, Better Combat...)
animate it as they animate players. It is not a player: it takes no slot on the
server, and it is not in the Tab list.

When nobody needs it, it lives like a villager: it stays where it is, and
freezes when its area unloads. When it must keep the world alive around it (an
owner taking it along, later), it does so exactly like a player. Whatever
happens, it keeps everything it has, and it never changes visibly.

Decision: a companion that dies is dead for good, like a creature, not like a
player: it does not respawn. Kept simple for now.

Decision: its skin is one of the skins the mod ships, like an entity's
variant: chosen when the companion is created, it never changes after.
Players' own skins are not used.

Requirements:

1. **Animated like a player, owned or not**, by the player animation mods.
2. **Cheap when nobody needs it.** A tavern may hold several; they cost like
   villagers and freeze with their area.
3. **Like a player when it must keep the world alive**, with every effect of a
   player: the world around it stays loaded, monsters spawn, it counts for
   sleeping.
4. **Not a player of the server.** Not in the Tab list, not counted among the
   players, never in the way of a player who wants to join.
5. **Nothing lost, nothing duplicated**, whatever happens to it.
6. **No visible change** when it changes form.

## How it is built

A companion is one identity, its core, that lives on the server in one of two
forms, the anchored form or the anchor form. Clients see one facade, a remote
player, and the form changes behind it.

| Class | What it is |
|---|---|
| `Companion` | The core: identity, owner, mode, home, the rule that picks the form |
| `CompanionForm` | The interface both forms implement, through which they hand the core what is not vanilla plumbing |
| `AnchoredCompanion` | The anchored form, an entity extending `Player` |
| `AnchorCompanion` | The anchor form, extending `ServerPlayer` |
| `ClientCompanion` | The facade on the client, extending `RemotePlayer` |

Constraints of the build: the player animation mods need no compatibility
code of ours; **no mixin** while a vanilla or Fabric API does the job (one is
needed, on the client, for the invisible switch).

How the parts talk: each form carries its core and gives it through
`CompanionForm` (the core, and the form as a vanilla entity); the core knows
its current form only through the form that carries it. The switch is run on
the server thread by the core, which reads its rule and asks for the other
form (`03-switch.md`).

Until the other features exist, test commands stand in for them: one creates
a companion (where the places will), one switches its form (where the modes
will). Their syntax is decided with the issue of the first brick.

Sub-pages:

| Page | What it answers |
|---|---|
| `01-forms.md` The two forms | What each form is, when a companion takes each, what the anchor form keeps of a player |
| `02-identity.md` Shared identity | What the core holds, how both forms share one identity, the companion type |
| `03-switch.md` Switching forms | How a companion changes form, without loss, duplication or visible change |
| `04-facade.md` Client facade | What a client builds for a companion, why the mods animate it, why it is not in Tab |
| `05-saving.md` Saving | Where and when each form is saved, at disconnection, restart and crash |
| `06-death.md` Death | How a companion dies for good, and how the death mods apply to it |
