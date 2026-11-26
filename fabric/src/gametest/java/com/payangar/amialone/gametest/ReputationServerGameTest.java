package com.payangar.amialone.gametest;

import static com.payangar.amialone.gametest.CompanionInventoryServerGameTest.connectPlayer;

import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.Companions;
import com.payangar.amialone.companion.RecruitMenu;
import com.payangar.amialone.companion.Recruitment;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A companion's opinion of players, made of vanilla's rumours: what it hears from villagers, what a
 * hit or a killing in front of it adds, and the recruitment price it moves.
 */
public class ReputationServerGameTest {
    @GameTest
    public void aHitMakesItThinkLessOfThePlayer(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        ServerPlayer player = connectPlayer(helper, "brute");
        try {
            companion.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1.0F);
            // A villager's minor negative rumour: +25, weight -1.
            helper.assertValueEqual(companion.opinionOf(player), -25, "opinion after a hit");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest
    public void killingACompanionInFrontOfAVillagerCountsAsKillingAVillager(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        Villager villager = (Villager) helper.spawn(EntityTypes.VILLAGER, 1, 2, 4);
        ServerPlayer player = connectPlayer(helper, "murderer");
        try {
            companion.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);
            helper.assertTrue(companion.isDeadOrDying(), "the companion survived");
            // A villager's major negative rumour: +25, weight -5.
            helper.assertValueEqual(villager.getPlayerReputation(player), -125, "the witness's reputation of the killer");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest
    public void killingACompanionInFrontOfAnotherCountsAsKillingAVillager(GameTestHelper helper) {
        Companion victim = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        Companion witness = (Companion) helper.spawn(Companions.TYPE, 1, 2, 4);
        ServerPlayer player = connectPlayer(helper, "murderer");
        try {
            victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);
            helper.assertValueEqual(witness.opinionOf(player), -125, "the witness's opinion of the killer");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** A dying villager tells the companion through vanilla's own path, once its senses saw it. */
    @GameTest(maxTicks = 100)
    public void killingAVillagerInFrontOfItCountsAsAVillagerKilled(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        Villager villager = (Villager) helper.spawn(EntityTypes.VILLAGER, 1, 2, 4);
        ServerPlayer player = connectPlayer(helper, "murderer");
        helper.runAfterDelay(40, () -> {
            try {
                villager.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);
                helper.assertValueEqual(companion.opinionOf(player), -125, "the companion's opinion of the killer");
            } finally {
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
            helper.succeed();
        });
    }

    /** It hears a talking villager next to it as a villager would: the rumour, less its loss. */
    @GameTest(maxTicks = 200)
    public void itHearsTheRumoursOfAVillagerNextToIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // The morning: villagers are idle, so they talk.
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set 1000");
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        Villager villager = (Villager) helper.spawn(EntityTypes.VILLAGER, 2, 2, 1);
        UUID player = UUID.randomUUID();
        villager.getGossips().add(player, GossipType.MINOR_NEGATIVE, 100);
        // Lost when passed on: the minor negative rumour's 20.
        helper.succeedWhen(() -> helper.assertValueEqual(
                companion.gossips().getReputation(player, type -> true), -80, "the companion's opinion heard"));
    }

    @GameTest
    public void itKeepsItsRumoursThroughSaving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        UUID player = UUID.randomUUID();
        companion.gossips().add(player, GossipType.MAJOR_POSITIVE, 20);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        helper.assertTrue(companion.save(output), "the companion is not saved with its chunk");
        companion.discard();
        Entity loaded = EntityType.loadEntityRecursive(
                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()),
                level,
                EntitySpawnReason.LOAD,
                entity -> entity);
        helper.assertValueEqual(
                ((Companion) loaded).gossips().getReputation(player, type -> true), 100, "opinion after loading");
        helper.succeed();
    }

    @GameTest
    public void aGoodOpinionLowersThePriceAndABadOneRaisesIt(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        int base = companion.price();
        ServerPlayer liked = connectPlayer(helper, "liked");
        ServerPlayer disliked = connectPlayer(helper, "disliked");
        try {
            // Cured by the player: +100, so 10 off at 0.1.
            companion.gossips().add(liked.getUUID(), GossipType.MAJOR_POSITIVE, 20);
            helper.assertValueEqual(Recruitment.priceFor(companion, liked), Math.max(base - 10, 1), "price when liked");
            // Hit once: -25, so floor(-2.5) = -3, 3 more.
            companion.gossips().add(disliked.getUUID(), GossipType.MINOR_NEGATIVE, 25);
            helper.assertValueEqual(Recruitment.priceFor(companion, disliked), base + 3, "price when disliked");

            liked.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            RecruitMenu.open(liked, companion);
            RecruitMenu menu = (RecruitMenu) liked.containerMenu;
            helper.assertValueEqual(menu.price(), Math.max(base - 10, 1), "the screen's price to pay");
            helper.assertValueEqual(menu.basePrice(), base, "the screen's drawn price");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(liked);
            helper.getLevel().getServer().getPlayerList().remove(disliked);
        }
        helper.succeed();
    }

    @GameTest
    public void heroOfTheVillageLowersThePrice(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        companion.setAttached(Companions.PRICE, 20);
        ServerPlayer hero = connectPlayer(helper, "hero");
        try {
            // Level I: 30 % of 20.
            hero.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1000, 0));
            helper.assertValueEqual(Recruitment.priceFor(companion, hero), 14, "price for a hero of level I");
            // Level V: 55 % of 20, rounded down.
            hero.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1000, 4));
            helper.assertValueEqual(Recruitment.priceFor(companion, hero), 9, "price for a hero of level V");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(hero);
        }
        helper.succeed();
    }

    @GameTest
    public void thePriceNeverDropsBelowOne(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        companion.setAttached(Companions.PRICE, Recruitment.MIN_PRICE);
        ServerPlayer player = connectPlayer(helper, "beloved");
        try {
            companion.gossips().add(player.getUUID(), GossipType.MAJOR_POSITIVE, 20);
            companion.gossips().add(player.getUUID(), GossipType.MINOR_POSITIVE, 25);
            player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1000, 4));
            helper.assertValueEqual(Recruitment.priceFor(companion, player), 1, "the lowest price");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** A splash potion of healing a player throws: vanilla heals it at once, the mixin names the thrower. */
    @GameTest(maxTicks = 100)
    public void aThrownPotionOfHealingMakesItThinkBetterOfTheThrower(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        companion.setHealth(10.0F);
        ServerPlayer player = connectPlayer(helper, "healer");
        ThrownSplashPotion potion = new ThrownSplashPotion(
                helper.getLevel(), player, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING));
        potion.setPos(companion.getX(), companion.getY() + 2.5, companion.getZ());
        potion.setDeltaMovement(0.0, -0.5, 0.0);
        helper.getLevel().addFreshEntity(potion);
        // Instant health I restores 4 at the centre of the splash: 2 hearts.
        helper.succeedWhen(() -> {
            helper.assertValueEqual(companion.opinionOf(player), 2, "opinion of the thrower");
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    @GameTest
    public void healingItAtFullHealthBringsNothing(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        ServerPlayer player = connectPlayer(helper, "healer");
        try {
            MobEffects.INSTANT_HEALTH.value().applyInstantaneousEffect(helper.getLevel(), null, player, companion, 1, 1.0);
            helper.assertValueEqual(companion.opinionOf(player), 0, "opinion after healing a companion at full health");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest
    public void aHealByACreatureCreditsNoOne(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        companion.setHealth(10.0F);
        Entity witch = helper.spawn(EntityTypes.WITCH, 3, 2, 3);
        MobEffects.INSTANT_HEALTH.value().applyInstantaneousEffect(helper.getLevel(), null, witch, companion, 0, 1.0);
        helper.assertTrue(companion.getHealth() > 10.0F, "the witch's potion did not heal the companion");
        helper.assertTrue(companion.gossips().getGossipEntries().isEmpty(), "a witch's heal left a rumour");
        helper.succeed();
    }

    /** Regeneration heals over time; each heart goes to the player who applied it. */
    @GameTest(maxTicks = 200)
    public void regenerationMakesItThinkBetterOfWhoAppliedIt(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        companion.setHealth(10.0F);
        ServerPlayer player = connectPlayer(helper, "healer");
        // Regeneration II heals 1 every 25 ticks.
        companion.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1), player);
        helper.succeedWhen(() -> {
            helper.assertTrue(companion.opinionOf(player) >= 2, "opinion of the healer: " + companion.opinionOf(player) + ", health " + companion.getHealth() + ", regeneration " + companion.hasEffect(MobEffects.REGENERATION));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /** The Debug tab shows the companion's opinion of the player who opened it, kept up to date. */
    @GameTest
    public void theDebugTabShowsItsOpinionOfTheViewer(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        ServerPlayer player = connectPlayer(helper, "operator");
        try {
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            companion.gossips().add(player.getUUID(), GossipType.MINOR_NEGATIVE, 25);
            CompanionMenu.open(player, companion, true);
            CompanionMenu menu = (CompanionMenu) player.containerMenu;
            menu.broadcastChanges();
            helper.assertValueEqual(menu.opinion(), -25, "the Debug tab's opinion");
            companion.gossips().add(player.getUUID(), GossipType.MINOR_POSITIVE, 10);
            menu.broadcastChanges();
            helper.assertValueEqual(menu.opinion(), -15, "the Debug tab's opinion after a change");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }
}
