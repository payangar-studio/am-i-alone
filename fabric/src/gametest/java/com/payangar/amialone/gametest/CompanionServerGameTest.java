package com.payangar.amialone.gametest;

import com.payangar.amialone.companion.CompanionPlayer;
import com.payangar.amialone.companion.CompanionRecord;
import com.payangar.amialone.fabric.FabricCompanions;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The body of a companion on a headless server: it joins as a player, its body ticks, it comes back
 * from its player data, and it leaves when dismissed. Each test dismisses what it spawned, since
 * the tests share one server.
 */
public class CompanionServerGameTest {
    @GameTest
    public void spawnCommandAddsAnOwnedPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = owner(helper, new Vec3(2, 1, 2));
        server.getCommands()
                .performPrefixedCommand(server.createCommandSourceStack().withEntity(owner), "companion spawn Alpha");

        CompanionRecord record = FabricCompanions.COMPANIONS.roster(server).byName("Alpha")
                .orElseThrow(() -> helper.assertionException(Component.literal("Alpha is not in the roster")));
        helper.assertValueEqual(record.owner(), owner.getUUID(), Component.literal("owner"));
        helper.assertTrue(
                server.getPlayerList().getPlayer(record.id()) instanceof CompanionPlayer,
                Component.literal("Alpha is not a player of the server"));
        helper.assertValueEqual(
                record.properties(),
                owner.getGameProfile().properties(),
                Component.literal("skin properties"));

        cleanUp(server, record.id(), owner);
        helper.succeed();
    }

    @GameTest
    public void bodyFallsLikeAPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = owner(helper, new Vec3(2, 6, 2));
        CompanionPlayer body = spawn(helper, owner, "Bravo");
        double startY = body.getY();

        helper.succeedWhen(() -> {
            helper.assertTrue(body.getY() < startY - 1, Component.literal("the body did not fall"));
            cleanUp(server, body.getUUID(), owner);
        });
    }

    @GameTest
    public void restoresFromItsPlayerData(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = owner(helper, new Vec3(2, 1, 2));
        CompanionPlayer body = spawn(helper, owner, "Charlie");
        UUID id = body.getUUID();
        Vec3 position = helper.absoluteVec(new Vec3(4.5, 1, 4.5));
        body.snapTo(position, 90, 0);

        // What a server stop does to every player: save it, then take it out of the world.
        server.getPlayerList().remove(body);
        helper.assertTrue(server.getPlayerList().getPlayer(id) == null, Component.literal("Charlie did not leave"));

        FabricCompanions.COMPANIONS.restoreAll(server);
        ServerPlayer restored = server.getPlayerList().getPlayer(id);
        helper.assertTrue(restored instanceof CompanionPlayer, Component.literal("Charlie was not restored"));
        helper.assertTrue(restored != body, Component.literal("the old body came back"));
        helper.assertTrue(
                restored.position().distanceTo(position) < 0.01,
                Component.literal("Charlie came back at " + restored.position() + ", not " + position));

        cleanUp(server, id, owner);
        helper.succeed();
    }

    @GameTest
    public void dismissCommandRemovesTheCompanion(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = owner(helper, new Vec3(2, 1, 2));
        CompanionPlayer body = spawn(helper, owner, "Delta");

        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "companion dismiss Delta");

        helper.assertTrue(
                server.getPlayerList().getPlayer(body.getUUID()) == null,
                Component.literal("Delta is still a player of the server"));
        helper.assertTrue(
                FabricCompanions.COMPANIONS.roster(server).byName("Delta").isEmpty(),
                Component.literal("Delta is still in the roster"));
        server.getPlayerList().remove(owner);
        helper.succeed();
    }

    @GameTest
    public void refusesATakenName(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = owner(helper, new Vec3(2, 1, 2));
        CompanionPlayer body = spawn(helper, owner, "Echo");

        server.getCommands()
                .performPrefixedCommand(server.createCommandSourceStack().withEntity(owner), "companion spawn Echo");

        helper.assertValueEqual(
                server.getPlayerList().getPlayers().stream()
                        .filter(p -> p.getGameProfile().name().equals("Echo"))
                        .count(),
                1L,
                Component.literal("players named Echo"));
        cleanUp(server, body.getUUID(), owner);
        helper.succeed();
    }

    private static ServerPlayer owner(GameTestHelper helper, Vec3 relative) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        owner.snapTo(helper.absoluteVec(relative), 0, 0);
        return owner;
    }

    private static CompanionPlayer spawn(GameTestHelper helper, ServerPlayer owner, String name) {
        try {
            return FabricCompanions.COMPANIONS.spawn(owner, name);
        } catch (Exception e) {
            throw helper.assertionException(Component.literal("could not spawn " + name + ": " + e.getMessage()));
        }
    }

    private static void cleanUp(MinecraftServer server, UUID companion, ServerPlayer owner) {
        FabricCompanions.COMPANIONS.roster(server).byId(companion)
                .ifPresent(record -> FabricCompanions.COMPANIONS.dismiss(server, record));
        server.getPlayerList().remove(owner);
    }
}
