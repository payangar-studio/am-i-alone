package com.payangar.amialone.gametest;

import com.payangar.amialone.client.companion.ClientCompanion;
import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionIdentity;
import com.payangar.amialone.companion.CompanionSkin;
import com.payangar.amialone.companion.Companions;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * The companion seen from a client: built as a client player with its skin and name, out of the
 * Tab list, and drawn as a player (compared with the reference render
 * src/gametest/resources/templates/companion.png); its effects and absorption, which a player's
 * client gets only for itself, reach the client for its health bar.
 */
public class CompanionClientGameTest implements FabricClientGameTest {
    private static final CompanionIdentity IDENTITY = new CompanionIdentity(
            "Aldric",
            "Fenwood",
            Optional.of(ResourceKey.create(
                    CompanionSkin.REGISTRY, Identifier.fromNamespaceAndPath("am_i_alone", "placeholder_steve"))));

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            UUID id = world.getServer().computeOnServer(server -> summonInFrontOfThePlayer(
                    server.getPlayerList().getPlayers().getFirst()));

            context.waitFor(client -> client.level != null
                    && client.level.getEntity(id) instanceof ClientCompanion companion
                    && companion.getAttached(Companions.IDENTITY) != null);
            Entity entity = context.computeOnClient(client -> client.level.getEntity(id));
            check(entity instanceof AbstractClientPlayer, "the client did not build a client player");
            PlayerSkin skin = context.computeOnClient(client -> ((ClientCompanion) entity).getSkin());
            check(
                    skin.body().texturePath().equals(Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png")),
                    "the companion does not wear its skin: " + skin.body().texturePath());
            String name = context.computeOnClient(client -> entity.getName().getString());
            check(name.equals("Aldric Fenwood"), "the companion is not named after its identity: " + name);
            boolean listed = context.computeOnClient(client -> client.getConnection().getListedOnlinePlayers().stream()
                    .anyMatch(info -> info.getProfile().id().equals(id)));
            check(!listed, "the companion is in the Tab list");

            // Let its position and head settle before the render.
            context.waitTicks(20);
            // Under a test profile (mod.testProfile), other mods change the render: the
            // references hold the mod alone.
            if (System.getProperty("mod.testProfile") == null) {
                context.assertScreenshotEquals(
                        TestScreenshotComparisonOptions.of("companion").save().disableCounterPrefix());
            }

            // Its effects reach the client when given and when cleared, and so does its absorption.
            world.getServer().runCommand("effect give " + id + " minecraft:poison 60");
            context.waitFor(client -> companion(client, id).hasEffect(MobEffects.POISON));
            world.getServer().runCommand("effect clear " + id + " minecraft:poison");
            context.waitFor(client -> !companion(client, id).hasEffect(MobEffects.POISON));
            world.getServer().runCommand("effect give " + id + " minecraft:absorption 60 1");
            context.waitFor(client -> companion(client, id).getAbsorptionAmount() == 8.0F);

            // A client that starts seeing a companion gets the effects it already has.
            UUID late = world.getServer().computeOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                Companion companion = (Companion) Companions.TYPE.create(player.level(), EntitySpawnReason.COMMAND);
                companion.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1200));
                companion.snapTo(player.getX() + 2.0, player.getY(), player.getZ() + 3.0, 180.0F, 0.0F);
                player.level().addFreshEntity(companion);
                return companion.getUUID();
            });
            context.waitFor(client -> client.level.getEntity(late) instanceof ClientCompanion companion
                    && companion.hasEffect(MobEffects.REGENERATION));
        }
    }

    private static LivingEntity companion(Minecraft client, UUID id) {
        return (LivingEntity) client.level.getEntity(id);
    }

    /** A companion three blocks in front of the player, facing them, with a fixed identity. */
    private static UUID summonInFrontOfThePlayer(ServerPlayer player) {
        ServerLevel level = player.level();
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
        Companion companion = (Companion) Companions.TYPE.create(level, EntitySpawnReason.COMMAND);
        companion.setAttached(Companions.IDENTITY, IDENTITY);
        companion.snapTo(player.getX(), player.getY(), player.getZ() + 3.0, 180.0F, 0.0F);
        companion.setYHeadRot(180.0F);
        companion.setYBodyRot(180.0F);
        level.addFreshEntity(companion);
        return companion.getUUID();
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }
}
