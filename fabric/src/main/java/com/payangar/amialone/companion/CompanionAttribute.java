package com.payangar.amialone.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.payangar.amialone.CommonEntrypoint;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * An entry of the {@code am_i_alone:companion_attribute} registry, filled by data and synced to the
 * clients: an attribute the companion screen's Attributes panel shows. An attribute without an
 * entry is not shown.
 *
 * <p>The attribute is named by its id and resolved when used: an entry whose attribute does not
 * exist, its mod absent, loads and is ignored, where a missing attribute would fail the whole
 * load of the registries.
 *
 * @param attribute the attribute's id
 * @param group the group it is shown in
 * @param order its place in the group, the lowest first
 * @param label its short label; the attribute's own name when absent
 * @param format how its value reads
 * @param shown when it is shown
 */
public record CompanionAttribute(
        ResourceKey<Attribute> attribute,
        Holder<CompanionAttributeGroup> group,
        int order,
        Optional<Component> label,
        Format format,
        Shown shown) {
    public static final ResourceKey<Registry<CompanionAttribute>> REGISTRY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(CommonEntrypoint.MOD_ID, "companion_attribute"));

    public static final Codec<CompanionAttribute> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ResourceKey.codec(Registries.ATTRIBUTE).fieldOf("attribute").forGetter(CompanionAttribute::attribute),
                    RegistryCodecs.holder(CompanionAttributeGroup.REGISTRY).fieldOf("group").forGetter(CompanionAttribute::group),
                    Codec.INT.fieldOf("order").forGetter(CompanionAttribute::order),
                    ComponentSerialization.CODEC.optionalFieldOf("label").forGetter(CompanionAttribute::label),
                    Format.CODEC.optionalFieldOf("format", Format.PLAIN).forGetter(CompanionAttribute::format),
                    Shown.CODEC.optionalFieldOf("shown", Shown.ALWAYS).forGetter(CompanionAttribute::shown))
            .apply(instance, CompanionAttribute::new));

    /** The attribute, if it exists. */
    public Optional<Holder.Reference<Attribute>> resolve() {
        return BuiltInRegistries.ATTRIBUTE.get(attribute);
    }

    /** How a value reads. */
    public enum Format implements StringRepresentable {
        /** The value. */
        PLAIN("plain"),
        /** Tenfold, as vanilla's item tooltips show knockback resistance. */
        TIMES_TEN("times_ten"),
        /** A percentage of its base: 100% is normal. */
        PERCENT_OF_DEFAULT("percent_of_default"),
        /** A multiplier, as a percentage. */
        PERCENT("percent");

        public static final Codec<Format> CODEC = StringRepresentable.fromEnum(Format::values);

        private final String name;

        Format(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** When an attribute is shown. */
    public enum Shown implements StringRepresentable {
        ALWAYS("always"),
        /** Only while its value differs from its base: gear or an effect changes it. */
        WHEN_CHANGED("when_changed");

        public static final Codec<Shown> CODEC = StringRepresentable.fromEnum(Shown::values);

        private final String name;

        Shown(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
