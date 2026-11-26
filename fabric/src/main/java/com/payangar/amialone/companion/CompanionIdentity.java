package com.payangar.amialone.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;

/**
 * What makes a companion who it is, beside its entity UUID: its drawn name and its skin, chosen
 * once when it is created and kept forever. It is saved with the entity and synced to the clients
 * that see it.
 *
 * @param skin the skin entry; empty only when the registry had none when the companion was created
 */
public record CompanionIdentity(String firstName, String lastName, Optional<ResourceKey<CompanionSkin>> skin) {
    public static final Codec<CompanionIdentity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("first_name").forGetter(CompanionIdentity::firstName),
                    Codec.STRING.fieldOf("last_name").forGetter(CompanionIdentity::lastName),
                    ResourceKey.codec(CompanionSkin.REGISTRY).optionalFieldOf("skin").forGetter(CompanionIdentity::skin))
            .apply(instance, CompanionIdentity::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionIdentity> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            CompanionIdentity::firstName,
            ByteBufCodecs.STRING_UTF8,
            CompanionIdentity::lastName,
            ResourceKey.streamCodec(CompanionSkin.REGISTRY).apply(ByteBufCodecs::optional),
            CompanionIdentity::skin,
            CompanionIdentity::new);

    /** The drawn name, "First Last", or an empty string when the name lists were empty. */
    public String fullName() {
        return Stream.of(firstName, lastName).filter(part -> !part.isEmpty()).reduce((a, b) -> a + " " + b).orElse("");
    }

    /** The group of its skin, which its first name follows; empty without a skin of the registry. */
    public Optional<CompanionGroup> group(RegistryAccess registries) {
        return skin.flatMap(key -> registries.lookupOrThrow(CompanionSkin.REGISTRY).getOptional(key))
                .map(CompanionSkin::group);
    }

    /** Draws a new identity: a skin, then a first name of its group and a last name. */
    public static CompanionIdentity draw(RegistryAccess registries, RandomSource random) {
        Optional<Holder.Reference<CompanionSkin>> skin =
                registries.lookupOrThrow(CompanionSkin.REGISTRY).getRandom(random);
        CompanionGroup group =
                skin.map(entry -> entry.value().group()).orElseGet(() -> Util.getRandom(CompanionGroup.values(), random));
        Registry<CompanionNames> names = registries.lookupOrThrow(CompanionNames.REGISTRY);
        List<String> firstNames = names.stream()
                .filter(list -> list.isFor(group))
                .flatMap(list -> list.firstNames().stream())
                .toList();
        List<String> lastNames =
                names.stream().flatMap(list -> list.lastNames().stream()).toList();
        return new CompanionIdentity(
                pick(firstNames, random), pick(lastNames, random), skin.map(Holder.Reference::key));
    }

    private static String pick(List<String> names, RandomSource random) {
        return names.isEmpty() ? "" : Util.getRandom(names, random);
    }
}
