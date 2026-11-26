package com.payangar.amialone.companion;

import com.payangar.amialone.CommonEntrypoint;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * What the server sends the client while a companion's screen is open, at its opening and again
 * when one changes: the current and base values of every attribute that has a {@link
 * CompanionAttribute} entry and that the companion has, and the {@link CompanionComputedValue}s
 * entries name. A client builds a companion as a player, whose attribute map is not the
 * companion's, and vanilla syncs only some attributes: the panel shows these values only.
 *
 * @param containerId the open menu's id, so that a late message does not reach another screen
 */
public record CompanionAttributeValues(int containerId, List<Value> values, List<Computed> computed)
        implements CustomPacketPayload {
    public static final Type<CompanionAttributeValues> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(CommonEntrypoint.MOD_ID, "companion_attribute_values"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionAttributeValues> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            CompanionAttributeValues::containerId,
            Value.STREAM_CODEC.apply(ByteBufCodecs.list()),
            CompanionAttributeValues::values,
            Computed.STREAM_CODEC.apply(ByteBufCodecs.list()),
            CompanionAttributeValues::computed,
            CompanionAttributeValues::new);

    /** An attribute's current value, modifiers applied, and its base. */
    public record Value(Holder<Attribute> attribute, double value, double base) {
        static final StreamCodec<RegistryFriendlyByteBuf, Value> STREAM_CODEC = StreamCodec.composite(
                Attribute.STREAM_CODEC,
                Value::attribute,
                ByteBufCodecs.DOUBLE,
                Value::value,
                ByteBufCodecs.DOUBLE,
                Value::base,
                Value::new);
    }

    /**
     * A computed value: from {@code min} to {@code max}, the same for a single number, and the base
     * it starts from.
     */
    public record Computed(CompanionComputedValue value, double min, double max, double base) {
        static final StreamCodec<RegistryFriendlyByteBuf, Computed> STREAM_CODEC = StreamCodec.composite(
                CompanionComputedValue.STREAM_CODEC,
                Computed::value,
                ByteBufCodecs.DOUBLE,
                Computed::min,
                ByteBufCodecs.DOUBLE,
                Computed::max,
                ByteBufCodecs.DOUBLE,
                Computed::base,
                Computed::new);
    }

    /** The values of {@code companion}'s attributes that have an entry, read on the server. */
    public static List<Value> of(LivingEntity companion) {
        Set<Holder<Attribute>> attributes = new LinkedHashSet<>();
        companion.registryAccess()
                .lookupOrThrow(CompanionAttribute.REGISTRY)
                .forEach(entry -> entry.resolve().ifPresent(attributes::add));
        List<Value> values = new ArrayList<>();
        for (Holder<Attribute> attribute : attributes) {
            AttributeInstance instance = companion.getAttribute(attribute);
            if (instance != null) {
                values.add(new Value(attribute, instance.getValue(), instance.getBaseValue()));
            }
        }
        return values;
    }

    /** {@code companion}'s computed values that an entry names, read on the server. */
    public static List<Computed> computedOf(Companion companion) {
        Set<CompanionComputedValue> computed = new LinkedHashSet<>();
        companion.registryAccess()
                .lookupOrThrow(CompanionAttribute.REGISTRY)
                .forEach(entry -> entry.computed().ifPresent(computed::add));
        return computed.stream().map(value -> value.compute(companion)).toList();
    }

    @Override
    public Type<CompanionAttributeValues> type() {
        return TYPE;
    }
}
