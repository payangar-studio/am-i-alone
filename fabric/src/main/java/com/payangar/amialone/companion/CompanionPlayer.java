package com.payangar.amialone.companion;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;

/**
 * The body of a companion: a player with nobody behind it. The server and the clients treat it as
 * any other player; what drives it is its {@link Brain}, the AI framework of vanilla's villagers
 * and piglins, which accepts any living entity.
 */
public final class CompanionPlayer extends ServerPlayer {
    /** Every skin layer shown (hat, jacket, sleeves, trousers, cape): a client sends the same. */
    private static final int ALL_MODEL_PARTS = 0x7F;

    /**
     * What the companion's brain knows and does: nothing yet. Behaviours, memories and sensors are
     * added here.
     */
    private static final Brain.Provider<CompanionPlayer> BRAIN = Brain.provider(List.of());

    private final UUID owner;

    CompanionPlayer(MinecraftServer server, ServerLevel level, CompanionRecord record) {
        super(server, level, record.profile(), companionInformation());
        this.owner = record.owner();
    }

    /**
     * What a client would send about itself. {@code allowsListing} false keeps the companion out of
     * the server list's sample of players.
     */
    static ClientInformation companionInformation() {
        return new ClientInformation(
                "en_us",
                2,
                ChatVisiblity.HIDDEN,
                false,
                ALL_MODEL_PARTS,
                Player.DEFAULT_MAIN_HAND,
                false,
                false,
                ParticleStatus.MINIMAL);
    }

    public UUID owner() {
        return owner;
    }

    @Override
    protected Brain<CompanionPlayer> makeBrain(Brain.Packed packed) {
        return BRAIN.makeBrain(this, packed);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Brain<CompanionPlayer> getBrain() {
        return (Brain<CompanionPlayer>) super.getBrain();
    }

    /**
     * The level ticks every player, but a player's body (physics, food, health) ticks from its
     * connection, which the server never ticks for a companion: the companion runs both, as the
     * connection would, then its brain.
     */
    @Override
    public void tick() {
        super.tick();
        xo = getX();
        yo = getY();
        zo = getZ();
        doTick();
        if (isAlive()) {
            getBrain().tick(level(), this);
        }
    }
}
