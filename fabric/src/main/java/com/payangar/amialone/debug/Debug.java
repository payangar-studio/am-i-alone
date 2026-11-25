package com.payangar.amialone.debug;

import com.payangar.amialone.CommonEntrypoint;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** The debug tools: the companion debug stick, given by {@code /give} only, in no creative tab. */
public final class Debug {
    private static final ResourceKey<Item> DEBUG_STICK_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(CommonEntrypoint.MOD_ID, "debug_stick"));

    /** Looks like vanilla's debug stick, glint included: its client item definition uses that model. */
    public static final Item DEBUG_STICK = Registry.register(
            BuiltInRegistries.ITEM,
            DEBUG_STICK_KEY,
            new DebugStickItem(new Item.Properties()
                    .setId(DEBUG_STICK_KEY)
                    .stacksTo(1)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    private Debug() {}

    /** Called once when the mod loads. */
    public static void register() {}
}
