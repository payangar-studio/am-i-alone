package com.payangar.amialone.gametest;

import com.payangar.amialone.companion.CompanionException;
import com.payangar.amialone.companion.CompanionPlayer;
import com.payangar.amialone.fabric.FabricCompanions;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * What a client sees of a companion: a player it renders, absent from its Tab list. The world is
 * then saved and reopened, as a server restart would: the companion comes back, still hidden from
 * the player who joins.
 */
public class CompanionClientGameTest implements FabricClientGameTest {
    private static final String NAME = "Foxtrot";

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        UUID id;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            id = world.getServer().computeOnServer(server -> {
                try {
                    return FabricCompanions.COMPANIONS
                            .spawn(server.getPlayerList().getPlayers().getFirst(), NAME)
                            .getUUID();
                } catch (CompanionException e) {
                    throw new AssertionError("could not spawn " + NAME, e);
                }
            });
            assertSeenAsAnUnlistedPlayer(context, id);
            save = world.getWorldSave();
        }

        try (TestSingleplayerContext world = save.open()) {
            world.getConnection().waitForChunksRender();
            boolean restored = world.getServer()
                    .computeOnServer(server -> server.getPlayerList().getPlayer(id) instanceof CompanionPlayer);
            if (!restored) {
                throw new AssertionError(NAME + " was not restored when the world reopened");
            }
            assertSeenAsAnUnlistedPlayer(context, id);
        }
    }

    private static void assertSeenAsAnUnlistedPlayer(ClientGameTestContext context, UUID id) {
        context.waitFor(client -> client.level != null && client.level.getPlayerByUUID(id) instanceof AbstractClientPlayer);
        // Its unlisting reaches the client at the end of the server tick that added it.
        context.waitTicks(5);
        context.runOnClient(client -> {
            if (info(client, id) == null) {
                throw new AssertionError("the client does not know " + NAME + " as a player");
            }
            if (client.getConnection().getListedOnlinePlayers().contains(info(client, id))) {
                throw new AssertionError(NAME + " is in the Tab list");
            }
        });
    }

    private static PlayerInfo info(Minecraft client, UUID id) {
        return client.getConnection().getPlayerInfo(id);
    }
}
