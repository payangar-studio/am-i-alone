package com.payangar.amialone.gametest;

import com.mojang.authlib.GameProfile;
import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionIdentity;
import com.payangar.amialone.companion.CompanionNames;
import com.payangar.amialone.companion.CompanionSkin;
import com.payangar.amialone.companion.Companions;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.scores.PlayerTeam;

/** The companion on the server: its identity, its saving, its name tag, its death. */
public class CompanionServerGameTest {
    @GameTest
    public void keepsItsIdentityThroughSaving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        CompanionIdentity identity = companion.getAttached(Companions.IDENTITY);
        helper.assertTrue(identity != null, "a summoned companion has no identity");

        CompanionSkin skin = level.registryAccess()
                .lookupOrThrow(CompanionSkin.REGISTRY)
                .getValue(identity.skin().orElseThrow());
        helper.assertTrue(skin != null, "the companion's skin is not in the registry: " + identity.skin());
        List<String> groupFirstNames = level.registryAccess().lookupOrThrow(CompanionNames.REGISTRY).stream()
                .filter(names -> names.isFor(skin.group()))
                .flatMap(names -> names.firstNames().stream())
                .toList();
        helper.assertTrue(
                groupFirstNames.contains(identity.firstName()),
                identity.firstName() + " is not a first name of the group " + skin.group().getSerializedName());
        helper.assertTrue(!identity.lastName().isEmpty(), "the companion has no last name");
        helper.assertValueEqual(companion.getName().getString(), identity.fullName(), "name");

        // The save a chunk writes for its entities, then the load that brings them back.
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        helper.assertTrue(companion.save(output), "the companion is not saved with its chunk");
        UUID uuid = companion.getUUID();
        companion.discard();
        Entity loaded = EntityType.loadEntityRecursive(
                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()),
                level,
                EntitySpawnReason.LOAD,
                entity -> entity);
        helper.assertTrue(loaded instanceof Companion, "the save did not load back as a companion: " + loaded);
        helper.assertValueEqual(loaded.getUUID(), uuid, "UUID");
        helper.assertValueEqual(((Companion) loaded).identity(), identity, "identity");
        helper.succeed();
    }

    /** The identity must be there when the spawn packet goes out, or clients first draw a default skin. */
    @GameTest
    public void hasItsIdentityBeforeEnteringTheWorld(GameTestHelper helper) {
        Entity created = Companions.TYPE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        // Read first: printing the entity asks for its name, which would draw the identity.
        boolean hasIdentity = created != null && created.hasAttached(Companions.IDENTITY);
        helper.assertTrue(created instanceof Companion, "the server did not create a companion");
        helper.assertTrue(hasIdentity, "a companion has no identity until it enters a world");
        helper.succeed();
    }

    @GameTest
    public void isRenamedByANameTag(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack nameTag = new ItemStack(Items.NAME_TAG);
        nameTag.set(DataComponents.CUSTOM_NAME, Component.literal("Bob"));
        player.setItemInHand(InteractionHand.MAIN_HAND, nameTag);

        player.interactOn(companion, InteractionHand.MAIN_HAND, companion.position());

        helper.assertValueEqual(companion.getName().getString(), "Bob", "name");
        helper.assertTrue(nameTag.isEmpty(), "the name tag was not used");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void diesWithAPlayerDeathMessage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        List<Component> received = new ArrayList<>();
        ServerPlayer witness = makeWitness(helper, received);
        PlayerTeam team = level.getScoreboard().addPlayerTeam("companion_death_test");

        Companion announced = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        level.getScoreboard().addPlayerToTeam(announced.getScoreboardName(), team);
        String name = announced.getName().getString();
        announced.kill(level);
        List<Component> announcedMessages = List.copyOf(received);

        received.clear();
        Companion silent;
        level.getGameRules().set(GameRules.SHOW_DEATH_MESSAGES, false, server);
        try {
            silent = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
            silent.kill(level);
        } finally {
            level.getGameRules().set(GameRules.SHOW_DEATH_MESSAGES, true, server);
        }
        List<Component> silentMessages = List.copyOf(received);

        // LivingEntity.tickDeath removes a dead entity after 20 ticks. The witness and the team
        // are cleaned up before any assertion, so a failure leaves nothing behind.
        helper.runAfterDelay(30, () -> {
            boolean removed = announced.isRemoved() && silent.isRemoved();
            boolean leftTeam = level.getScoreboard().getPlayersTeam(announced.getScoreboardName()) == null;
            server.getPlayerList().remove(witness);
            level.getScoreboard().removePlayerTeam(team);
            helper.assertTrue(
                    announcedMessages.stream().anyMatch(message -> message.getString().contains(name)),
                    "no death message naming " + name + " reached the player: " + announcedMessages);
            helper.assertTrue(
                    silentMessages.isEmpty(),
                    "a death message was sent with show_death_messages off: " + silentMessages);
            helper.assertTrue(removed, "a dead companion was not removed");
            helper.assertTrue(leftTeam, "a dead companion stayed in its team");
            helper.succeed();
        });
    }

    /** A connected player that records the system messages it receives. */
    private static ServerPlayer makeWitness(GameTestHelper helper, List<Component> received) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "witness"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), ClientInformation.createDefault()) {
            @Override
            public void sendSystemMessage(Component message, boolean overlay) {
                received.add(message);
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
