package com.payangar.amialone.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.payangar.amialone.CommonEntrypoint;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * An entry of the {@code am_i_alone:companion_skin} registry, filled by data and synced to the
 * clients like vanilla's entity variants.
 *
 * @param texture the skin texture, as an asset id ({@code am_i_alone:entity/companion/<id>} for
 *     {@code assets/am_i_alone/textures/entity/companion/<id>.png})
 * @param model the player model the skin is drawn on
 * @param group the group the companion's first name is drawn from
 */
public record CompanionSkin(ClientAsset.ResourceTexture texture, PlayerModelType model, CompanionGroup group) {
    public static final ResourceKey<Registry<CompanionSkin>> REGISTRY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(CommonEntrypoint.MOD_ID, "companion_skin"));

    public static final Codec<CompanionSkin> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ClientAsset.ResourceTexture.CODEC.fieldOf("texture").forGetter(CompanionSkin::texture),
                    PlayerModelType.CODEC.fieldOf("model").forGetter(CompanionSkin::model),
                    CompanionGroup.CODEC.fieldOf("group").forGetter(CompanionSkin::group))
            .apply(instance, CompanionSkin::new));
}
