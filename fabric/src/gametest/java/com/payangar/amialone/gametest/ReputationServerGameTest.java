package com.payangar.amialone.gametest;

import static com.payangar.amialone.gametest.CompanionInventoryServerGameTest.connectPlayer;

import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.Companions;
import com.payangar.amialone.companion.RecruitMenu;
import com.payangar.amialone.companion.Recruitment;
import com.payangar.amialone.companion.Recruitment.Payment;
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
            RecruitMenu.open(liked, companion, Payment.BUTTON);
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
}
