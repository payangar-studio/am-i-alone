package com.payangar.amialone.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.payangar.amialone.CommonEntrypoint;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * An entry of the {@code am_i_alone:companion_attribute_group} registry, filled by data and synced
 * to the clients: a group of the companion screen's Attributes panel, which {@link
 * CompanionAttribute} entries name. A group without a shown attribute is not shown.
 *
 * @param heading the heading drawn above its attributes
 * @param order its place among the groups, the lowest first
 */
public record CompanionAttributeGroup(Component heading, int order) {
    public static final ResourceKey<Registry<CompanionAttributeGroup>> REGISTRY = ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(CommonEntrypoint.MOD_ID, "companion_attribute_group"));

    public static final Codec<CompanionAttributeGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ComponentSerialization.CODEC.fieldOf("heading").forGetter(CompanionAttributeGroup::heading),
                    Codec.INT.fieldOf("order").forGetter(CompanionAttributeGroup::order))
            .apply(instance, CompanionAttributeGroup::new));
}
