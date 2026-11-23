package com.payangar.amialone.fabric;

import com.payangar.amialone.CommonEntrypoint;
import com.payangar.amialone.companion.CompanionCommands;
import com.payangar.amialone.companion.CompanionPlayer;
import com.payangar.amialone.companion.CompanionRoster;
import com.payangar.amialone.companion.Companions;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** The companions on Fabric: their roster saved with the server, and the events they hook into. */
public final class FabricCompanions {
    /** The roster, a server-wide attachment that Fabric saves with the world. */
    private static final AttachmentType<CompanionRoster> ROSTER = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(CommonEntrypoint.MOD_ID, "companions"),
            builder -> builder.persistent(CompanionRoster.CODEC).initializer(() -> CompanionRoster.EMPTY));

    public static final Companions COMPANIONS = new Companions(new Companions.Store() {
        @Override
        public CompanionRoster get(MinecraftServer server) {
            return server.globalAttachments().getAttachedOrCreate(ROSTER);
        }

        @Override
        public void set(MinecraftServer server, CompanionRoster roster) {
            server.globalAttachments().setAttached(ROSTER, roster);
        }
    });

    /** The players who joined this tick, to whom the companions are hidden once they are listed. */
    private static final List<ServerPlayer> JOINED = new ArrayList<>();

    private FabricCompanions() {}

    static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registries, selection) -> CompanionCommands.register(dispatcher, COMPANIONS));
        ServerLifecycleEvents.SERVER_STARTED.register(COMPANIONS::restoreAll);
        // The join message of a companion: it joins as a player, but nobody connected.
        ServerMessageEvents.ALLOW_GAME_MESSAGE.register((server, message, overlay) -> !COMPANIONS.joining());
        // Fabric fires JOIN while vanilla places the player, before it sends the joining player the
        // list of players: hiding the companions has to wait for the end of the tick.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!(handler.player instanceof CompanionPlayer)) {
                JOINED.add(handler.player);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            JOINED.forEach(player -> {
                if (!player.hasDisconnected()) {
                    COMPANIONS.hideFrom(player);
                }
            });
            JOINED.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> JOINED.clear());
    }
}
