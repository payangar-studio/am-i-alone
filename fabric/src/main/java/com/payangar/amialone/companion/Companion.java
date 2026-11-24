package com.payangar.amialone.companion;

import java.util.Arrays;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.scores.Team;

/**
 * A companion on the server: a player-shaped entity that is not a player, saved with its chunk.
 * Clients build it as a player ({@code ClientCompanion}), so the player animation mods animate it.
 *
 * <p>It declares no synched data: any would take an id the client player uses for its own data.
 * Its identity travels as the {@link Companions#IDENTITY} attachment instead.
 */
public class Companion extends Avatar {
    private static final byte ALL_LAYERS = (byte) Arrays.stream(PlayerModelPart.values())
            .mapToInt(PlayerModelPart::getMask)
            .reduce(0, (a, b) -> a | b);

    public Companion(EntityType<? extends Avatar> type, Level level) {
        super(type, level);
        // The skin layers (hat, jacket, sleeves, trousers), hidden by the default 0.
        entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, ALL_LAYERS);
        // Drawn before the entity enters a world, so its spawn reaches clients with it. A
        // companion loaded from its chunk then replaces it with its saved one.
        if (!level.isClientSide()) {
            identity();
        }
    }

    /** Its identity, drawn the first time it is asked for, then kept with the entity. */
    public CompanionIdentity identity() {
        return getAttachedOrCreate(Companions.IDENTITY, () -> CompanionIdentity.draw(registryAccess(), random));
    }

    @Override
    public ResolvableProfile getProfile() {
        // No player profile: the skin comes from the identity.
        return ResolvableProfile.Static.EMPTY;
    }

    /** A name tag's name, otherwise the drawn name, as for a player. */
    @Override
    public Component getName() {
        if (hasCustomName()) {
            return super.getName();
        }
        String name = identity().fullName();
        return name.isEmpty() ? super.getName() : Component.literal(name);
    }

    @Override
    public void die(DamageSource source) {
        if (!isRemoved() && !dead && level() instanceof ServerLevel level) {
            announceDeath(level);
        }
        super.die(source);
    }

    /**
     * Broadcasts the death message as {@code ServerPlayer.die} does for a player: the same game
     * rule, the same message from the combat tracker, the same visibility of its team. The player
     * list's team broadcasts take a player, so the visibility is applied here.
     */
    private void announceDeath(ServerLevel level) {
        if (!level.getGameRules().get(GameRules.SHOW_DEATH_MESSAGES)) {
            return;
        }
        Component message = getCombatTracker().getDeathMessage();
        PlayerList players = level.getServer().getPlayerList();
        Team team = getTeam();
        Team.Visibility visibility = team == null ? Team.Visibility.ALWAYS : team.getDeathMessageVisibility();
        switch (visibility) {
            case ALWAYS -> players.broadcastSystemMessage(message, false);
            case HIDE_FOR_OTHER_TEAMS -> {
                for (ServerPlayer player : players.getPlayers()) {
                    if (player.getTeam() == team) {
                        player.sendSystemMessage(message);
                    }
                }
            }
            case HIDE_FOR_OWN_TEAM -> {
                for (ServerPlayer player : players.getPlayers()) {
                    if (player.getTeam() != team) {
                        player.sendSystemMessage(message);
                    }
                }
            }
            case NEVER -> {}
        }
    }
}
