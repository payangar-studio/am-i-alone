package com.payangar.amialone.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.payangar.amialone.CommonEntrypoint;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * An entry of the {@code am_i_alone:companion_names} registry: a list of names a companion's name
 * is drawn from. A datapack replaces an entry with a file at the same path, or adds entries.
 *
 * @param group the group its first names belong to; without one, they belong to every group
 * @param firstNames first names, drawn for a companion whose skin is of the group
 * @param lastNames last names, shared by every group
 */
public record CompanionNames(Optional<CompanionGroup> group, List<String> firstNames, List<String> lastNames) {
    public static final ResourceKey<Registry<CompanionNames>> REGISTRY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(CommonEntrypoint.MOD_ID, "companion_names"));

    public static final Codec<CompanionNames> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    CompanionGroup.CODEC.optionalFieldOf("group").forGetter(CompanionNames::group),
                    Codec.STRING.listOf().optionalFieldOf("first_names", List.of()).forGetter(CompanionNames::firstNames),
                    Codec.STRING.listOf().optionalFieldOf("last_names", List.of()).forGetter(CompanionNames::lastNames))
            .apply(instance, CompanionNames::new));

    /** Whether its first names may be drawn for a companion of the group. */
    public boolean isFor(CompanionGroup companionGroup) {
        return group.map(companionGroup::equals).orElse(true);
    }
}
