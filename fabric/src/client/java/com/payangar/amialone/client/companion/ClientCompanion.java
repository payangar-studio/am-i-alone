package com.payangar.amialone.client.companion;

import com.mojang.authlib.GameProfile;
import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionIdentity;
import com.payangar.amialone.companion.CompanionSkin;
import com.payangar.amialone.companion.Companions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a client builds for a companion: a remote player, drawn by the player renderer, so the
 * player animation mods animate it. It has no profile of its own: no profile is ever sent for a
 * companion, so it is not in the Tab list, and what a client player reads from its profile comes
 * from the companion's identity instead.
 */
public class ClientCompanion extends RemotePlayer {
    // The spawn packet sets the entity's UUID; the profile only fills the constructor.
    private static final GameProfile NO_PROFILE = new GameProfile(Util.NIL_UUID, "Companion");

    private @Nullable CompanionIdentity skinIdentity;
    private @Nullable PlayerSkin skin;

    public ClientCompanion(ClientLevel level) {
        super(level, NO_PROFILE);
    }

    /** Makes every client level build companions as client companions. */
    public static void register() {
        Companions.clientFactory = (type, level) ->
                level instanceof ClientLevel clientLevel ? new ClientCompanion(clientLevel) : new Companion(type, level);
    }

    private @Nullable CompanionIdentity identity() {
        return getAttached(Companions.IDENTITY);
    }

    @Override
    public PlayerSkin getSkin() {
        CompanionIdentity identity = identity();
        if (identity == null) {
            return super.getSkin();
        }
        if (identity != skinIdentity) {
            skinIdentity = identity;
            skin = identity.skin()
                    .flatMap(key -> registryAccess().lookupOrThrow(CompanionSkin.REGISTRY).getOptional(key))
                    .map(entry -> PlayerSkin.insecure(entry.texture(), null, null, entry.model()))
                    .orElse(null);
        }
        return skin != null ? skin : super.getSkin();
    }

    /** A name tag's name, otherwise the drawn name, as on the server. */
    @Override
    public Component getName() {
        Component customName = getCustomName();
        if (customName != null) {
            return customName;
        }
        CompanionIdentity identity = identity();
        return identity == null || identity.fullName().isEmpty()
                ? super.getName()
                : Component.literal(identity.fullName());
    }

    /**
     * Its name only while the crosshair is on it, as a mob's, even without a name tag; a player's
     * always shows.
     */
    @Override
    public boolean shouldShowName() {
        return isCustomNameVisible() || this == Minecraft.getInstance().crosshairPickEntity;
    }

    /**
     * The server's answer to a right-click ({@code Companion.interact}), predicted: no swing and no
     * item use where the server lets nothing happen.
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (Companion.letsClickPass(player, hand)) {
            return super.interact(player, hand, location);
        }
        return Companion.isOwner(this, player)
                ? InteractionResult.SUCCESS.withoutItem()
                : InteractionResult.CONSUME.withoutItem();
    }

    /** Its absorption as the server sent it: a client player reads it from data a companion lacks. */
    @Override
    public float getAbsorptionAmount() {
        return getAttachedOrElse(Companions.ABSORPTION, 0.0F);
    }

    /** Its name as plain text, rather than the placeholder profile's name. */
    @Override
    public String getPlainTextName() {
        return getName().getString();
    }

    /** Its UUID, as on the server, so the client finds its team. */
    @Override
    public String getScoreboardName() {
        return getStringUUID();
    }
}
