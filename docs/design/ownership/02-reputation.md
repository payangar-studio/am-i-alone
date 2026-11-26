# Reputation

What a companion thinks of a player, and how it lowers or raises the price of
its recruitment (`01-recruitment.md`).

## In game

Decision: a companion has its own opinion of each player, made of the same
rumours as a villager's, with the same numbers, the same forgetting and the
same word of mouth. The rumour system is taken as it is, unchanged.

Fact, how a villager's opinion works (vanilla 26.3):

| Rumour | What adds it | Weight | Cap | Forgotten per day | Lost when passed on |
|---|---|---|---|---|---|
| Major positive | Curing this zombie villager (+20) | ×5 | 20 | never | 20, so it never spreads |
| Minor positive | The same cure (+25) | ×1 | 25 | 1 | 5 |
| Trading | Each trade with it (+2) | ×1 | 25 | 2 | 20, so at most 5 spreads |
| Minor negative | Hitting it (+25) | ×−1 | 200 | 20 | 20 |
| Major negative | Killing a villager in front of it (+25 for each witness) | ×−5 | 100 | 10 | 10 |

The opinion is the sum of each rumour's value times its weight, from −700 to
+150. Two villagers who meet pass rumours on, one way, at most once a minute
each, in the morning, the late afternoon and around the bell; the good spreads
badly and the bad spreads well.

Decision: what a companion's opinion hears:

| Event | Rumour, as for a villager |
|---|---|
| A player hits it | Minor negative +25 |
| A player kills a villager or a companion in front of it | Major negative +25 |
| A villager passes rumours on to it | As between two villagers: up to 10, with their losses |

It cannot trade, but trading rumours reach it by word of mouth, as any other.

Decision: one event is the companion's own, which a villager does not have:
**a player who heals it** gets a minor positive rumour, +1 for each heart (2
health) actually restored. Healing a companion at full health brings nothing,
so potions thrown for nothing buy no opinion. Only a player's heal counts: a
witch's potion, or one a dispenser throws, credits no one.

Fact: in vanilla, a player heals another creature only with potion effects:
healing and regeneration, drunk by no one but thrown, lingering, or on a
tipped arrow (beacons and conduits reach players only; no item heals any
creature). A tipped arrow also deals damage, so its hit counts as any hit.

Question: heals from magic mods, handled later as a compatibility of their
own.

Decision: killing a companion in front of villagers counts for them as killing
a villager: their own prices go up for the killer.

Decision: the price of a recruitment follows the opinion as a villager's
offer does, with a coefficient of 0.1, between the villagers' 0.05 (most
offers) and 0.2 (armour, tools, books): the opinion × 0.1, rounded down,
comes off the price. A bad opinion raises it, by at least 1, as a villager's.

Fact: a companion's good opinion stays small. The major positive rumour of a
cure never spreads to it, and hearing several villagers keeps the strongest
version of each rumour rather than adding them (`GossipContainer.java:161-163`).
Its minor positive, heard or earned by healing it, is capped at 25, and the
trading it hears at 5: +30 at best, so 3 emeralds off. A
bad opinion goes down to −700, 70 more. Decision: kept at 0.1: the rumour
system makes the good spread badly, and that is accepted; Hero of the Village
gives the larger discounts.

Decision: Hero of the Village lowers it too, as at a villager: by 30 % (level
I) to 55 % (level V) of the drawn price, at least 1, on top of the opinion.

Decision: the price never drops below 1, and has no cap
(`01-recruitment.md`). It is computed when the recruitment screen opens.

Decision: the screen shows a lowered or raised price as a villager's offer
does: the drawn price struck through in red, the price to pay beside it
(`01-recruitment.md`).

## How it is built

Decision: the companion uses vanilla's rumour pieces themselves, so that a mod
changing them changes the companions too: a `GossipContainer` of its own,
saved with the entity as a villager saves its own (`GossipContainer.CODEC`),
the `GossipType` values, `transferFrom` to hear a villager, `decay` once a day
of game time, and `ReputationEventHandler`, the interface through which a
dying villager tells its witnesses (`Villager.java:597-606`).

Fact: two of a villager's rules are private code, and the companion copies
them, so a mod changing them does not reach the companions: the rumour an
event adds (`Villager.onReputationEventFrom`, `Villager.java:858-868`) and the
price of an offer (`Villager.updateSpecialPrices`, `Villager.java:409-424`).
Decision: this is accepted.

Decision: the companion is a `ReputationEventHandler`. A villager dying in
front of it tells it as it tells a villager, with nothing more in the mod.
A hit on it (`setLastHurtByMob`) tells itself only, as a villager's; its
death tells its witnesses (villagers and companions within 16 blocks that see
it), through the same interface, with vanilla's event types.

Decision: it hears a villager standing within √5 blocks of it, as two villagers
do (`TradeWithVillager.java:48-50`), while that villager is idle or meeting
(its brain's activity, so a mod changing the villagers' schedule changes when
companions hear), at most once a minute (1200 ticks, `Villager.java:813-815`).
Fact: a companion does not move yet, so it hears only the villagers who come
close.

Decision: a heal is credited in the companion's `heal`: the health restored
(after minus before), added up for each player, +1 minor positive for every
2. Who healed comes from the effect behind the heal:

- regeneration, and instant health on a tipped arrow: vanilla gives the
  companion whoever applied the effect (`onEffectAdded`, `onEffectUpdated`,
  `LivingEntity.java:1066,1082`); the companion remembers it for that effect,
  resolving a lingering cloud or an ownerless projectile to its owner, and
  never lets the periodic update without a source (`LivingEntity.java:846`)
  forget it;
- instant health from a thrown or lingering potion: vanilla heals directly,
  without telling anyone who threw it (`HealOrHarmMobEffect.java:28-38`).

Decision: one mixin, the mod's first, on `HealOrHarmMobEffect.applyInstantaneousEffect`:
it tells a companion target the thrower before vanilla heals it. Its reason:
it is the only exact way, since neither the companion nor Fabric API's effect
events receive the thrower there. Ruled out: crediting the nearest player
after a heal (a witch's potion would credit a player who did nothing),
looking for the potion or cloud close by (two potions at once, another mod's
heal credited wrongly), and not counting it (the most common heal of all).

Fact: the client never receives rumours; the server computes the price and
sends the drawn price and the price to pay with the screen's opening data.
