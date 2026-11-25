package com.payangar.amialone.gametest;

import com.payangar.amialone.client.companion.ClientCompanion;
import com.payangar.amialone.client.companion.CompanionScreen;
import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionIdentity;
import com.payangar.amialone.companion.CompanionInventory;
import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.CompanionMenu.Tab;
import com.payangar.amialone.companion.CompanionSkin;
import com.payangar.amialone.companion.Companions;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The companion's armor and screen seen from a client: its armor on it in the world, then its
 * screen with the storage closed, with the storage open, and on the Debug tab, which closes the storage, each
 * compared with a reference render of src/gametest/resources/templates/ (companion_armor.png,
 * companion_screen.png, companion_screen_storage.png, companion_screen_debug.png); then its hearts
 * going down when it is hurt, screen open.
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
            // Let its position and head settle before the render.
            context.waitTicks(20);
            // Under a test profile (mod.testProfile), other mods change the render: the
            // references hold the mod alone.
            boolean compare = System.getProperty("mod.testProfile") == null;
            if (compare) {
                context.assertScreenshotEquals(
                        TestScreenshotComparisonOptions.of("companion_armor").save().disableCounterPrefix());
            }

            // Only the debug stick opens the screen until companions have owners.
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

            // The handle opens the storage: on the server at once, on the client once the drawer is out.
            context.runOnClient(client -> screen(client).toggleStorage());
            context.waitFor(client -> screen(client).getMenu().storageOpen());
            check(storageOpenOnServer(world), "the server's storage is closed after the handle's click");
            context.waitTicks(2);
            screenshot(context, compare, "companion_screen_storage");

            // The Debug tab closes the storage, on both sides.
            context.runOnClient(client -> screen(client).selectTab(Tab.DEBUG));
            context.waitTicks(2);
            check(!context.computeOnClient(client -> screen(client).getMenu().storageOpen()), "the storage is open on the Debug tab");
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
        }
    }

    private static void screenshot(ClientGameTestContext context, boolean compare, String name) {
        if (compare) {
            context.assertScreenshotEquals(TestScreenshotComparisonOptions.of(name).save().disableCounterPrefix());
        }
    }

    private static boolean storageOpenOnServer(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server ->
                ((CompanionMenu) server.getPlayerList().getPlayers().getFirst().containerMenu).storageOpen());
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
    private static UUID summonEquipped(ServerPlayer player) {
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
