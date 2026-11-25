package com.payangar.amialone.companion;

import com.payangar.amialone.CommonEntrypoint;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

/**
 * What the companion registers: its entity type, its identity attachment, its inventory menu, its
 * data registries.
 */
public final class Companions {
    /**
     * What a client builds for a companion, set by the client entrypoint, as vanilla does for the
     * Mannequin: a client player, so the player renderer and the animation mods take it.
     */
    public static EntityType.EntityFactory<Avatar> clientFactory;

    public static final ResourceKey<EntityType<?>> TYPE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("companion"));

    /**
     * Declared as {@code EntityType<Avatar>}: its factory returns a {@link Companion} on the server
     * and a client player on the client, two subclasses of {@code Avatar}.
     */
    public static final EntityType<Avatar> TYPE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            TYPE_KEY,
            EntityType.Builder.<Avatar>of(Companions::create, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(Avatar.DEFAULT_EYE_HEIGHT)
                    .vehicleAttachment(Avatar.DEFAULT_VEHICLE_ATTACHMENT)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .noLootTable()
                    .build(TYPE_KEY));

    public static final AttachmentType<CompanionIdentity> IDENTITY = AttachmentRegistry.create(
            id("identity"),
            builder -> builder.persistent(CompanionIdentity.CODEC)
                    .syncWith(CompanionIdentity.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** The inventory screen's menu, which sends the companion's entity id to the client. */
    public static final ExtendedMenuType<CompanionMenu, Integer> MENU = Registry.register(
            BuiltInRegistries.MENU, id("companion"), new ExtendedMenuType<>(CompanionMenu::new, ByteBufCodecs.VAR_INT));

    private Companions() {}

    /** Called once when the mod loads. */
    public static void register() {
        FabricDefaultAttributeRegistry.register(TYPE, LivingEntity.createLivingAttributes());
        DynamicRegistries.registerSynced(CompanionSkin.REGISTRY, CompanionSkin.CODEC);
        DynamicRegistries.register(CompanionNames.REGISTRY, CompanionNames.CODEC);
    }

    private static Avatar create(EntityType<Avatar> type, Level level) {
        return level.isClientSide() && clientFactory != null
                ? clientFactory.create(type, level)
                : new Companion(type, level);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(CommonEntrypoint.MOD_ID, path);
    }
}
