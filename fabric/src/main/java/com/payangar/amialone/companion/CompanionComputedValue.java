package com.payangar.amialone.companion;

import com.payangar.amialone.CommonEntrypoint;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.AttackRange;

/**
 * A value of the companion that no attribute holds, computed by the mod on the server, which a
 * {@link CompanionAttribute} entry names by its id: a fixed set, which data cannot add to.
 */
public enum CompanionComputedValue {
    /**
     * The distances its attack reaches, from its weapon ({@link Companion#attackWeapon}): the
     * weapon's {@code attack_range} component, survival reach, or else up to its entity interaction
     * range, as vanilla's {@code LivingEntity.getAttackRangeWith}. Its base is the interaction
     * range. The component's mob factor is left out: it applies to mobs, and a companion is not one.
     */
    ATTACK_RANGE("attack_range");

    public static final StreamCodec<ByteBuf, CompanionComputedValue> STREAM_CODEC =
            ByteBufCodecs.idMapper(id -> values()[id], CompanionComputedValue::ordinal);

    private final Identifier id;

    CompanionComputedValue(String path) {
        this.id = Identifier.fromNamespaceAndPath(CommonEntrypoint.MOD_ID, path);
    }

    public Identifier id() {
        return id;
    }

    public static Optional<CompanionComputedValue> byId(Identifier id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst();
    }

    /** Its value for {@code companion}, read on the server. */
    public CompanionAttributeValues.Computed compute(Companion companion) {
        return switch (this) {
            case ATTACK_RANGE -> {
                AttackRange range = companion.getAttackRangeWith(companion.attackWeapon());
                yield new CompanionAttributeValues.Computed(
                        this,
                        range.minReach(),
                        range.maxReach(),
                        companion.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
            }
        };
    }
}
