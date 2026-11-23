package com.payangar.amialone.companion;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** Every companion of a world, whether its body is in the world or not. Immutable. */
public record CompanionRoster(List<CompanionRecord> companions) {
    public static final CompanionRoster EMPTY = new CompanionRoster(List.of());
    public static final Codec<CompanionRoster> CODEC =
            CompanionRecord.CODEC.listOf().xmap(CompanionRoster::new, CompanionRoster::companions);

    public CompanionRoster {
        companions = List.copyOf(companions);
    }

    public Optional<CompanionRecord> byId(UUID id) {
        return companions.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public Optional<CompanionRecord> byName(String name) {
        return companions.stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst();
    }

    public CompanionRoster with(CompanionRecord companion) {
        return new CompanionRoster(Stream.concat(
                        companions.stream().filter(c -> !c.id().equals(companion.id())), Stream.of(companion))
                .toList());
    }

    public CompanionRoster without(UUID id) {
        return new CompanionRoster(
                companions.stream().filter(c -> !c.id().equals(id)).toList());
    }
}
