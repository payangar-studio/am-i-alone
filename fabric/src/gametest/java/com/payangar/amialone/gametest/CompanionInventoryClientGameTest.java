package com.payangar.amialone.gametest;

import com.payangar.amialone.client.companion.ClientCompanion;
import com.payangar.amialone.client.companion.CompanionScreen;
import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionIdentity;
import com.payangar.amialone.companion.CompanionInventory;
import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.CompanionSkin;
import com.payangar.amialone.companion.Companions;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The companion's armor and inventory seen from a client: its armor on it in the world, then its
 * inventory screen, each compared with a reference render of src/gametest/resources/templates/
 * (companion_armor.png, companion_inventory.png).
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
                    && companion.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS));
            // Let its position and head settle before the render.
            context.waitTicks(20);
            // Under a test profile (mod.testProfile), other mods change the render: the
            // references hold the mod alone.
            boolean compare = System.getProperty("mod.testProfile") == null;
            if (compare) {
                context.assertScreenshotEquals(
                        TestScreenshotComparisonOptions.of("companion_armor").save().disableCounterPrefix());
            }

            world.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                CompanionMenu.open(player, (Companion) player.level().getEntity(id));
            });
            context.waitForScreen(CompanionScreen.class);
            // The 3D view follows the mouse: a fixed cursor, over no slot, for the same render.
            context.getInput().setCursorPos(554, 244);
            context.waitFor(client -> ((CompanionScreen) client.gui.screen()).getMenu()
                    .getSlot(CompanionMenu.WEAPONS_START).getItem().is(Items.IRON_SWORD));
            context.waitTicks(5);
            if (compare) {
                context.assertScreenshotEquals(
                        TestScreenshotComparisonOptions.of("companion_inventory").save().disableCounterPrefix());
            }
        }
    }

    /**
     * A companion three blocks in front of the player, facing them, with a fixed identity, iron
     * armor, a sword and a shield as designated weapons, and some storage.
     */
    private static UUID summonEquipped(ServerPlayer player) {
        ServerLevel level = player.level();
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
        Companion companion = (Companion) Companions.TYPE.create(level, EntitySpawnReason.COMMAND);
        companion.setAttached(Companions.IDENTITY, IDENTITY);
        companion.snapTo(player.getX(), player.getY(), player.getZ() + 3.0, 180.0F, 0.0F);
        companion.setYHeadRot(180.0F);
        companion.setYBodyRot(180.0F);
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
