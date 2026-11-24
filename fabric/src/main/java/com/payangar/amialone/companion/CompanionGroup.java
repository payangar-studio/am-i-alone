package com.payangar.amialone.companion;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The group of a skin, which the companion's first name follows. */
public enum CompanionGroup implements StringRepresentable {
    WOMAN("woman"),
    MAN("man");

    public static final Codec<CompanionGroup> CODEC = StringRepresentable.fromEnum(CompanionGroup::values);

    private final String name;

    CompanionGroup(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
