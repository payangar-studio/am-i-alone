package com.payangar.amialone.companion;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ExtraCodecs;

/**
 * What identifies a companion outside its body: the profile it plays under and the player who
 * owns it. Its body (position, inventory, health, brain) is saved by vanilla as the player data of
 * {@link #id()}.
 *
 * @param properties the profile properties, which carry the skin
 */
public record CompanionRecord(UUID id, String name, UUID owner, PropertyMap properties) {
    public static final Codec<CompanionRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                    UUIDUtil.CODEC.fieldOf("id").forGetter(CompanionRecord::id),
                    Codec.STRING.fieldOf("name").forGetter(CompanionRecord::name),
                    UUIDUtil.CODEC.fieldOf("owner").forGetter(CompanionRecord::owner),
                    ExtraCodecs.PROPERTY_MAP
                            .optionalFieldOf("properties", PropertyMap.EMPTY)
                            .forGetter(CompanionRecord::properties))
            .apply(i, CompanionRecord::new));

    public GameProfile profile() {
        return new GameProfile(id, name, properties);
    }

    public NameAndId nameAndId() {
        return new NameAndId(id, name);
    }
}
