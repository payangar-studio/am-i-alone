package com.payangar.amialone.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/**
 * The player a companion belongs to: their UUID, and their name when they recruited it, which a
 * client shows without looking the player up.
 */
public record CompanionOwner(UUID id, String name) {
    public static final Codec<CompanionOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("id").forGetter(CompanionOwner::id),
                    Codec.STRING.fieldOf("name").forGetter(CompanionOwner::name))
            .apply(instance, CompanionOwner::new));

    public static final StreamCodec<ByteBuf, CompanionOwner> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CompanionOwner::id, ByteBufCodecs.STRING_UTF8, CompanionOwner::name, CompanionOwner::new);

    public static CompanionOwner of(Player player) {
        return new CompanionOwner(player.getUUID(), player.getGameProfile().name());
    }

    public boolean is(Player player) {
        return id.equals(player.getUUID());
    }
}
