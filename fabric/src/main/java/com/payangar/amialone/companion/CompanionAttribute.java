package com.payangar.amialone.companion;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.payangar.amialone.CommonEntrypoint;
import java.util.Optional;
import net.minecraft.ChatFormatting;
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
 * clients: a row of the companion screen's Attributes panel. It names either an attribute, with
 * {@code "attribute"}, or a value the mod computes, with {@code "value"} (one of {@link
 * CompanionComputedValue}). What has no entry is not shown.
 *
 * <p>Both are named by their id and resolved when used: an entry whose attribute does not exist,
 * its mod absent, or whose value this version of the mod does not compute, loads and is ignored,
 * where a missing id would fail the whole load of the registries.
 *
 * @param source the attribute's id, or the computed value's
 * @param group the group it is shown in
 * @param order its place in the group, the lowest first
 * @param label its short label; the attribute's or the value's own name when absent
 * @param format how its value reads
 * @param shown when it is shown
 * @param colourThreshold what an attribute's value is coloured against, instead of the attribute's
 *     default
 */
public record CompanionAttribute(
        Either<ResourceKey<Attribute>, Identifier> source,
        Holder<CompanionAttributeGroup> group,
        int order,
        Optional<Component> label,
        Format format,
        Shown shown,
        Optional<Double> colourThreshold) {
    public static final ResourceKey<Registry<CompanionAttribute>> REGISTRY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(CommonEntrypoint.MOD_ID, "companion_attribute"));

    public static final Codec<CompanionAttribute> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.mapEither(
                                    ResourceKey.codec(Registries.ATTRIBUTE).fieldOf("attribute"),
                                    Identifier.CODEC.fieldOf("value"))
                            .forGetter(CompanionAttribute::source),
                    RegistryCodecs.holder(CompanionAttributeGroup.REGISTRY).fieldOf("group").forGetter(CompanionAttribute::group),
                    Codec.INT.fieldOf("order").forGetter(CompanionAttribute::order),
                    ComponentSerialization.CODEC.optionalFieldOf("label").forGetter(CompanionAttribute::label),
                    Format.CODEC.optionalFieldOf("format", Format.PLAIN).forGetter(CompanionAttribute::format),
                    Shown.CODEC.optionalFieldOf("shown", Shown.ALWAYS).forGetter(CompanionAttribute::shown),
                    Codec.DOUBLE.optionalFieldOf("colour_threshold").forGetter(CompanionAttribute::colourThreshold))
            .apply(instance, CompanionAttribute::new));

    /** Below this, two values are the same. */
    private static final double EPSILON = 1.0E-6;

    /** The attribute, if the entry names one and it exists. */
    public Optional<Holder.Reference<Attribute>> resolve() {
        return source.left().flatMap(BuiltInRegistries.ATTRIBUTE::get);
    }

    /** The computed value, if the entry names one this version of the mod computes. */
    public Optional<CompanionComputedValue> computed() {
        return source.right().flatMap(CompanionComputedValue::byId);
    }

    /**
     * Whether the panel shows the entry: always, while its value differs from its default ({@code
     * changed}), or while the companion is armed ({@code armed}).
     */
    public boolean shows(boolean changed, boolean armed) {
        return switch (shown) {
            case ALWAYS -> true;
            case WHEN_CHANGED -> changed;
            case WHEN_ARMED -> armed;
        };
    }

    /** Whether {@code value} of {@code attribute} differs from the attribute's default. */
    public static boolean differsFromDefault(Attribute attribute, double value) {
        return Math.abs(value - attribute.getDefaultValue()) > EPSILON;
    }

    /**
     * How {@code value} of {@code attribute} reads: compared with the entry's colour threshold, or
     * else with the attribute's default (not the companion's base, which a command can move), a
     * gain or a loss as the attribute's sentiment says, and neither when equal.
     */
    public Tone tone(Attribute attribute, double value) {
        double reference = colourThreshold.orElse(attribute.getDefaultValue());
        if (Math.abs(value - reference) <= EPSILON) {
            return Tone.NEUTRAL;
        }
        ChatFormatting style = attribute.getStyle(value > reference);
        return style == ChatFormatting.BLUE ? Tone.GOOD : style == ChatFormatting.RED ? Tone.BAD : Tone.NEUTRAL;
    }

    /** How a value reads, which its colour shows. */
    public enum Tone {
        NEUTRAL,
        GOOD,
        BAD
    }

    /** How a value reads. */
    public enum Format implements StringRepresentable {
        /** The value. */
        PLAIN("plain"),
        /** Tenfold, as vanilla's item tooltips show knockback resistance. */
        TIMES_TEN("times_ten"),
        /** A percentage of the attribute's default: 100% is normal. */
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
        /** Only while its value differs from the attribute's default: gear or an effect changes it. */
        WHEN_CHANGED("when_changed"),
        /** Only while the companion is armed ({@link Companion#armed}). */
        WHEN_ARMED("when_armed");

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
