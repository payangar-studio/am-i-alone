package com.payangar.amialone.companion;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Creates, restores and removes the companions of a server. A companion joins the server as a
 * player, the way a client's player joins it, and leaves it the same way; its body is saved by
 * vanilla as player data. All of it runs on the server thread.
 */
public final class Companions {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The names a Minecraft account can have: the profile of a companion follows the same rule. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    /** Where the roster is kept: the loader decides how it is saved with the world. */
    public interface Store {
        CompanionRoster get(MinecraftServer server);

        void set(MinecraftServer server, CompanionRoster roster);
    }

    private final Store store;
    private boolean joining;

    public Companions(Store store) {
        this.store = store;
    }

    public CompanionRoster roster(MinecraftServer server) {
        return store.get(server);
    }

    /** The body of a companion, when it is in the world. */
    public static Optional<CompanionPlayer> body(MinecraftServer server, UUID id) {
        return server.getPlayerList().getPlayer(id) instanceof CompanionPlayer body
                ? Optional.of(body)
                : Optional.empty();
    }

    /** Creates a companion owned by {@code owner}, with its skin, next to it. */
    public CompanionPlayer spawn(ServerPlayer owner, String name) throws CompanionException {
        MinecraftServer server = owner.level().getServer();
        if (!NAME.matcher(name).matches()) {
            throw new CompanionException(Component.translatable("am_i_alone.companion.invalid_name", name));
        }
        if (store.get(server).byName(name).isPresent()
                || server.getPlayerList().getPlayerByName(name) != null) {
            throw new CompanionException(Component.translatable("am_i_alone.companion.name_taken", name));
        }
        CompanionRecord record = new CompanionRecord(
                UUID.randomUUID(), name, owner.getUUID(), owner.getGameProfile().properties());
        store.set(server, store.get(server).with(record));
        CompanionPlayer body = new CompanionPlayer(server, owner.level(), record);
        body.snapTo(owner.position(), owner.getYRot(), 0);
        join(server, body);
        return body;
    }

    /** Removes a companion for good: its body leaves the world, the roster forgets it. */
    public void dismiss(MinecraftServer server, CompanionRecord record) {
        body(server, record.id()).ifPresent(body -> leave(server, body));
        store.set(server, store.get(server).without(record.id()));
    }

    /**
     * Brings back into the world the companions whose body is not there, as vanilla brings back a
     * player who logs in: where its player data says, or at the world spawn without any.
     */
    public void restoreAll(MinecraftServer server) {
        for (CompanionRecord record : store.get(server).companions()) {
            if (server.getPlayerList().getPlayer(record.id()) == null) {
                restore(server, record);
            }
        }
    }

    private void restore(MinecraftServer server, CompanionRecord record) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(LOGGER)) {
            Optional<ValueInput> data = server.getPlayerList()
                    .loadPlayerData(record.nameAndId())
                    .map(tag -> TagValueInput.create(reporter, server.registryAccess(), tag));
            ServerPlayer.SavedPosition saved = data.flatMap(d -> d.read(ServerPlayer.SavedPosition.MAP_CODEC))
                    .orElse(ServerPlayer.SavedPosition.EMPTY);
            ServerLevel level = saved.dimension().map(server::getLevel).orElseGet(server::overworld);
            Vec3 position = saved.position()
                    .orElseGet(() -> Vec3.atBottomCenterOf(
                            server.getWorldData().overworldData().getRespawnData().pos()));
            Vec2 rotation = saved.rotation().orElse(Vec2.ZERO);

            ChunkPos chunk = ChunkPos.containing(BlockPos.containing(position));
            level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_SPAWN, chunk, 3);
            level.getChunk(chunk.x(), chunk.z());

            CompanionPlayer body = new CompanionPlayer(server, level, record);
            data.ifPresent(body::load);
            body.snapTo(position, rotation.x, rotation.y);
            join(server, body);
            data.ifPresent(d -> {
                body.loadAndSpawnEnderPearls(d);
                body.loadAndSpawnParentVehicle(d);
            });
        }
    }

    /** True while a companion joins: the join message vanilla broadcasts then is its own. */
    public boolean joining() {
        return joining;
    }

    /** Sends {@code viewer} the companions to hide from its Tab list: call it once it joined. */
    public void hideFrom(ServerPlayer viewer) {
        List<CompanionPlayer> bodies = viewer.level().getServer().getPlayerList().getPlayers().stream()
                .filter(CompanionPlayer.class::isInstance)
                .map(CompanionPlayer.class::cast)
                .toList();
        if (!bodies.isEmpty()) {
            viewer.connection.send(unlisted(viewer.registryAccess(), bodies));
        }
    }

    private void join(MinecraftServer server, CompanionPlayer body) {
        CommonListenerCookie cookie =
                new CommonListenerCookie(body.getGameProfile(), 0, CompanionPlayer.companionInformation(), false);
        joining = true;
        try {
            server.getPlayerList().placeNewPlayer(CompanionConnection.create(), body, cookie);
        } finally {
            joining = false;
        }
        server.getPlayerList().broadcastAll(unlisted(server.registryAccess(), List.of(body)));
    }

    /** What a disconnecting player goes through, without the message saying it left. */
    private static void leave(MinecraftServer server, CompanionPlayer body) {
        body.disconnect();
        server.getPlayerList().remove(body);
    }

    /**
     * The update that takes players out of the Tab list. Vanilla builds this packet only with
     * {@code listed} true, so it is decoded from the bytes vanilla's own codec reads: the actions,
     * then for each player its id and its listed flag.
     */
    static ClientboundPlayerInfoUpdatePacket unlisted(
            RegistryAccess registries, Collection<? extends ServerPlayer> players) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            buffer.writeEnumSet(
                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                    ClientboundPlayerInfoUpdatePacket.Action.class);
            buffer.writeVarInt(players.size());
            for (ServerPlayer player : players) {
                buffer.writeUUID(player.getUUID());
                buffer.writeBoolean(false);
            }
            return ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
