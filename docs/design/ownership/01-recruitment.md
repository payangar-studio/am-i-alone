# Recruitment

How a player makes a companion their own: they pay its price in emeralds.

## In game

Decision: a right-click on a companion that has no owner opens its
recruitment screen, whatever the player holds, without the companion debug
stick. Sneaking lets the held item act instead, as on the companion's own
screen (`../companion/08-screen.md`).

Decision: a companion that has an owner cannot be recruited. For anyone but
its owner a right-click does nothing, as before.

Decision: the recruitment screen is its own screen, not a tab of the
companion's screen: it is what a stranger sees before the companion is
theirs. It shows, read-only:

- its armor and its two designated weapons, as icons: nothing can be taken
  or put there; hovering one shows the item's tooltip;
- its view in 3D, its name with the icon of its group, its health and armor
  bars, as on the companion's screen;
- its attributes, in the drawer, whose only panel is the attributes one; its
  storage is not shown;
- its price and the Recruit button, under the hearts, where the companion's
  screen shows the content of its tab;
- the player's inventory, below.

Decision: the price is a number of emeralds, drawn once when the companion
is created, uniformly between 12 and 36, saved with it and never drawn again.
It has no cap, unlike a villager's offers (64 at most): once the equipment
counts, a price may pass a stack. It does not depend on the equipment yet: a scale of what each
piece of equipment adds comes with the equipment companions spawn with.

Decision: two ways to pay are built, to be tried in game; one is kept, the
other removed:

| Way | What the player does |
|---|---|
| Button | Clicks Recruit; the emeralds are taken from their inventory. The button is greyed while they have fewer than the price. |
| Payment slot | Puts emeralds in a slot of the screen, as in a beacon; Recruit becomes active once the slot holds the price, and takes exactly the price from it. What is left in the slot goes back to the inventory when the screen closes, or drops if it does not fit or the player is dead or gone, as in a villager's screen. |

Question: which way to pay is kept. A price above 64 would need more than
one payment slot.

Decision: a player in creative mode pays too, as with villagers.

Decision: once the player has paid, the companion is theirs, and that is
all: it does not follow them, it has no mode yet (`00-overview.md`).

Decision: right after the payment, the recruitment screen closes and the
companion's screen opens, now that the player is its owner.

Decision: when two players have the screen of the same companion open, the
first to pay recruits it; the other's screen closes.

## How it is built

### The owner

Decision: the owner is a persistent attachment of the companion, synced to
every client that sees it, as its identity (`../companion/04-identity.md`):
the owner's UUID and their name at the time of recruitment. The client needs
it to predict a right-click (recruitment screen, companion's screen or
nothing) and to show the owner on the companion's screen.
`Companion.isOwner` reads it.

Fact: today no owner is stored: `Companion.isOwner` always answers no
(`Companion.java:142-148`), and the screen's owner line always says none
(`CompanionScreen.java:672-675`).

Question: the owner's name when they change it on their account.

### The price

Decision: the price is a persistent attachment, drawn in the constructor on
the server through `getAttachedOrCreate`, as the identity
(`Companion.java:72-80`). Fact: Fabric keeps the constructor's attachments
when the saved entity has none, and replaces them by the saved ones
otherwise (`AttachmentTargetsMixin`, read in the bytecode of
fabric-data-attachment-api 2.2.35), so a companion keeps its price through
`/summon` and through saving.

Ruled out: drawing it at the first tick, which was needed only for a price
that depends on the equipment: the constructor runs before `/summon` or the
code give the equipment (`EntityType.java:312-317`).

### The screen

Decision: its own container menu (`RecruitMenu`) and screen, opened with
Fabric's `ExtendedMenuType`. The opening data carry the companion's entity
id, its price, the way to pay, and a copy of the armor and designated
weapons: vanilla sends a client the armor of an entity it sees, but not the
designated weapons, which are inventory slots (`../companion/07-inventory.md`).
The copy is taken when the screen opens.

Ruled out: a recruitment mode of the companion's menu. It would make the
companion's slots read-only, which means blocking every way vanilla moves an
item (pick up, place, number-key swap, throw, shift-click, double-click
gathering, `AbstractContainerMenu.java:422-547`), and it would make the
recruitment part of the companion's management screen, which it is not.

Decision: the pieces both screens show are shared rather than copied: the
3D view, the name heading, the health and armor bars, the drawer and its
attributes panel, and the server's attributes message, which today runs from
`CompanionMenu.broadcastChanges` (`CompanionMenu.java:317-347`).

Decision: the menu's slots are the player's inventory, plus the payment slot
when that way is tried, which accepts emeralds only. Its validity: the
companion alive, within 4 blocks as the companion's screen, and still without
an owner.

Decision: Recruit is a menu button (`clickMenuButton`), which vanilla calls
only for the open menu, still valid (`ServerGamePacketListenerImpl.java:2046-2058`).
The server checks the emeralds again, takes them, sets the owner, then
opens the companion's screen for the player, which closes the recruitment
one. The
client greys the button with the same test, as the enchanting table does
(`EnchantmentMenu.java:135-148`, `EnchantmentScreen.java:112`). With the
button, emeralds are counted and taken from the player's 36 inventory slots,
the ones the menu shows, so that client and server count the same.

Decision: while both ways are tried, the server picks the way from a
development switch and sends it in the opening data; the client only follows.

### Right-click

Decision: `Companion.interact` opens the recruitment screen for anyone when
the companion has no owner, the companion's screen for its owner, and does
nothing for anyone else; sneaking, and the debug stick in an operator's hand,
let the click pass as today. `ClientCompanion.interact` predicts the same
from the synced owner.
