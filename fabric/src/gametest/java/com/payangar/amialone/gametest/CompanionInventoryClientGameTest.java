package com.payangar.amialone.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.payangar.amialone.client.companion.ClientCompanion;
import com.payangar.amialone.client.companion.CompanionScreen;
import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionAttributeValues;
import com.payangar.amialone.companion.CompanionIdentity;
import com.payangar.amialone.companion.CompanionInventory;
import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.CompanionMenu.Panel;
import com.payangar.amialone.companion.CompanionMenu.Tab;
import com.payangar.amialone.companion.CompanionSkin;
import com.payangar.amialone.companion.Companions;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The companion's armor and screen seen from a client: its armor on it in the world, then its
 * screen with the drawer closed, open on its storage, open on its attributes, and on the Debug tab,
 * which closes the drawer, each compared with a reference render of src/gametest/resources/templates/
 * (companion_armor.png, companion_screen.png, companion_screen_storage.png,
 * companion_screen_attributes.png, companion_screen_debug.png); then its hearts going down when it
 * is hurt, screen open; then the drawer's panel, which a reopened screen remembers; then Escape on
 * a narrow screen, which puts the drawer away first.
 */
public class CompanionInventoryClientGameTest implements FabricClientGameTest {
    private static final CompanionIdentity IDENTITY = new CompanionIdentity(
            "Aldric",
            "Fenwood",
            Optional.of(ResourceKey.create(
                    CompanionSkin.REGISTRY, Identifier.fromNamespaceAndPath("am_i_alone", "placeholder_steve"))));

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            UUID id = world.getServer().computeOnServer(server -> summonEquipped(
                    server.getPlayerList().getPlayers().getFirst()));

            // Vanilla's equipment sync puts the armor on the client companion.
            context.waitFor(client -> client.level != null
                    && client.level.getEntity(id) instanceof ClientCompanion companion
                    && companion.getAttached(Companions.IDENTITY) != null
                    && companion.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS)
                    && companion.getArmorValue() == 15);
            // A client builds a companion as a player, whose default speed is not the companion's:
            // the spawn must bring the companion's.
            double speed = context.computeOnClient(client -> attribute(client, id, Attributes.MOVEMENT_SPEED));
            check(speed == Attributes.MOVEMENT_SPEED.value().getDefaultValue(), "the client companion's speed is " + speed);
            // Let its position and head settle before the render.
            context.waitTicks(20);
            // Under a test profile (mod.testProfile), other mods change the render: the
            // references hold the mod alone.
            boolean compare = System.getProperty("mod.testProfile") == null;
            if (compare) {
                Screenshots.compare(context, "companion_armor");
            }

            // This companion has no owner: the debug stick opens its screen.
            world.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                CompanionMenu.open(player, (Companion) player.level().getEntity(id), true);
            });
            context.waitForScreen(CompanionScreen.class);
            // The 3D view follows the mouse: a fixed cursor, in the tabs' row, over nothing.
            context.getInput().setCursorPos(634, 46);
            context.waitFor(client -> screen(client).getMenu()
                    .getSlot(CompanionMenu.WEAPONS_START).getItem().is(Items.IRON_SWORD));
            context.waitTicks(5);
            screenshot(context, compare, "companion_screen");

            // The Inventory handle opens the storage: on the server at once, on the client once the
            // drawer is out.
            context.runOnClient(client -> screen(client).pressHandle(Panel.INVENTORY));
            context.waitFor(client -> screen(client).getMenu().storageOpen());
            check(storageOpenOnServer(world), "the server's storage is closed after the Inventory handle's click");
            context.waitTicks(2);
            screenshot(context, compare, "companion_screen_storage");

            // Attributes changed on the server reach the screen through the menu's message, its
            // knockback resistance included, which vanilla does not sync.
            world.getServer().runOnServer(server -> {
                LivingEntity companion = (LivingEntity) server.getPlayerList().getPlayers().getFirst().level().getEntity(id);
                modify(companion, Attributes.KNOCKBACK_RESISTANCE, 0.4, AttributeModifier.Operation.ADD_VALUE);
                modify(companion, Attributes.MOVEMENT_SPEED, 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                modify(companion, Attributes.SAFE_FALL_DISTANCE, -1.0, AttributeModifier.Operation.ADD_VALUE);
                modify(companion, Attributes.OXYGEN_BONUS, 1.0, AttributeModifier.Operation.ADD_VALUE);
            });
            context.waitFor(client -> sentValue(client, Attributes.KNOCKBACK_RESISTANCE) == 0.4
                    && Math.abs(sentValue(client, Attributes.MOVEMENT_SPEED) - 0.98) < 1.0E-9
                    && sentValue(client, Attributes.SAFE_FALL_DISTANCE) == 2.0
                    && sentValue(client, Attributes.OXYGEN_BONUS) == 1.0);

            // The Attributes handle swaps the panel at once, and closes the storage on both sides.
            context.runOnClient(client -> screen(client).pressHandle(Panel.ATTRIBUTES));
            check(
                    !context.computeOnClient(client -> screen(client).getMenu().storageOpen()),
                    "the storage is open on the Attributes panel");
            context.waitTicks(2);
            check(!storageOpenOnServer(world), "the server's storage is open on the Attributes panel");
            screenshot(context, compare, "companion_screen_attributes");

            // The Debug tab closes the drawer, on both sides.
            context.runOnClient(client -> screen(client).selectTab(Tab.DEBUG));
            context.waitTicks(2);
            check(
                    context.computeOnClient(client -> screen(client).getMenu().panel()) == null,
                    "the drawer is open on the Debug tab");
            check(!storageOpenOnServer(world), "the server's storage is open on the Debug tab");
            screenshot(context, compare, "companion_screen_debug");

            // A hit while the screen is open takes hearts away.
            context.runOnClient(client -> screen(client).selectTab(Tab.COMPANION));
            context.waitFor(client -> screen(client).bars().lastHealth() == 15);
            world.getServer().runOnServer(server -> {
                ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
                level.getEntity(id).hurtServer(level, level.damageSources().generic(), 6.0F);
            });
            context.waitFor(client -> screen(client).bars().lastHealth() < 15);

            // The drawer's open panel is remembered for the session: a screen closed with the drawer
            // out on the Attributes reopens with it out there, on both sides.
            context.runOnClient(client -> screen(client).pressHandle(Panel.ATTRIBUTES));
            context.runOnClient(client -> screen(client).onClose());
            context.waitFor(client -> !(client.gui.screen() instanceof CompanionScreen));
            // The server closes the menu once the client's packet arrives; a menu opened before
            // would close with it.
            for (int tick = 0; world.getServer().computeOnServer(server ->
                    server.getPlayerList().getPlayers().getFirst().containerMenu instanceof CompanionMenu); tick++) {
                check(tick < 100, "the server did not close the menu");
                context.waitTick();
            }
            world.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                CompanionMenu.open(player, (Companion) player.level().getEntity(id), true);
            });
            context.waitForScreen(CompanionScreen.class);
            context.waitTicks(2);
            check(
                    context.computeOnClient(client -> screen(client).getMenu().panel()) == Panel.ATTRIBUTES,
                    "the reopened screen's drawer is not on the Attributes");
            check(
                    world.getServer().computeOnServer(server -> ((CompanionMenu)
                                    server.getPlayerList().getPlayers().getFirst().containerMenu)
                            .panel()) == Panel.ATTRIBUTES,
                    "the server's drawer is not on the Attributes after reopening");

            // On a screen too narrow for the panel and the drawer side by side, Escape puts the
            // drawer away first, then closes the screen.
            context.getInput().resizeWindow(640, 480);
            context.waitTicks(2);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(2);
            check(context.computeOnClient(client -> client.gui.screen() instanceof CompanionScreen screen
                    && screen.getMenu().panel() == null), "Escape did not put the drawer away first");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitFor(client -> !(client.gui.screen() instanceof CompanionScreen));
        }
    }

    private static void screenshot(ClientGameTestContext context, boolean compare, String name) {
        if (compare) {
            Screenshots.compare(context, name);
        }
    }

    private static boolean storageOpenOnServer(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server ->
                ((CompanionMenu) server.getPlayerList().getPlayers().getFirst().containerMenu).storageOpen());
    }

    private static double attribute(Minecraft client, UUID id, Holder<Attribute> attribute) {
        return ((LivingEntity) client.level.getEntity(id)).getAttributeValue(attribute);
    }

    /** The value of {@code attribute} the server last sent the open screen; NaN before any. */
    private static double sentValue(Minecraft client, Holder<Attribute> attribute) {
        CompanionAttributeValues.Value value = screen(client).getMenu().attributeValues().get(attribute);
        return value == null ? Double.NaN : value.value();
    }

    private static void modify(LivingEntity entity, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        entity.getAttribute(attribute).addPermanentModifier(new AttributeModifier(
                Identifier.fromNamespaceAndPath("am_i_alone_gametest", "test"), amount, operation));
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }

    private static CompanionScreen screen(Minecraft client) {
        return (CompanionScreen) client.gui.screen();
    }

    /**
     * A companion three blocks in front of the player, facing them, with a fixed identity, 15
     * health, iron armor, a sword and a shield as designated weapons, and some storage.
     */
    static UUID summonEquipped(ServerPlayer player) {
        ServerLevel level = player.level();
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
        Companion companion = (Companion) Companions.TYPE.create(level, EntitySpawnReason.COMMAND);
        companion.setAttached(Companions.IDENTITY, IDENTITY);
        companion.snapTo(player.getX(), player.getY(), player.getZ() + 3.0, 180.0F, 0.0F);
        companion.setYHeadRot(180.0F);
        companion.setYBodyRot(180.0F);
        // Seven and a half hearts out of ten: a half heart in the bar.
        companion.setHealth(15.0F);
        companion.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        companion.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        companion.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        companion.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        CompanionInventory inventory = companion.inventory();
        inventory.setItem(CompanionInventory.PRIMARY_WEAPON, new ItemStack(Items.IRON_SWORD));
        inventory.setItem(CompanionInventory.SECONDARY_WEAPON, new ItemStack(Items.SHIELD));
        inventory.setItem(0, new ItemStack(Items.BREAD, 12));
        inventory.setItem(10, new ItemStack(Items.TORCH, 32));
        inventory.setItem(35, new ItemStack(Items.ARROW, 64));
        level.addFreshEntity(companion);
        player.getInventory().setItem(9, new ItemStack(Items.APPLE, 5));
        return companion.getUUID();
    }
}
