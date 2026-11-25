package com.payangar.amialone.gametest;

import com.mojang.authlib.GameProfile;
import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionInventory;
import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.CompanionMenu.Tab;
import com.payangar.amialone.companion.Companions;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SlotRanges;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** The companion's inventory on the server: its slots, its saving, its death, hoppers, its menu. */
public class CompanionInventoryServerGameTest {
    @GameTest
    public void keepsItsInventoryThroughSaving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        Map<String, Item> items = Map.of(
                "container.0", Items.DIAMOND,
                "container.35", Items.EMERALD,
                "container.36", Items.IRON_SWORD,
                "container.37", Items.SHIELD,
                "armor.head", Items.IRON_HELMET,
                "armor.chest", Items.IRON_CHESTPLATE,
                "armor.legs", Items.IRON_LEGGINGS,
                "armor.feet", Items.IRON_BOOTS);
        items.forEach((slot, item) -> replaceItem(helper, companion, slot, item));
        items.forEach((slot, item) -> helper.assertValueEqual(itemIn(companion, slot), item, "/item put in " + slot));

        // The save a chunk writes for its entities, then the load that brings them back.
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        helper.assertTrue(companion.save(output), "the companion is not saved with its chunk");
        companion.discard();
        Entity loaded = EntityType.loadEntityRecursive(
                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()),
                level,
                EntitySpawnReason.LOAD,
                entity -> entity);
        helper.assertTrue(loaded instanceof Companion, "the save did not load back as a companion: " + loaded);
        items.forEach((slot, item) -> helper.assertValueEqual(itemIn(loaded, slot), item, slot + " after loading"));
        helper.succeed();
    }

    @GameTest
    public void designatedWeaponSlotsTakeWeaponsOnly(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        for (int slot : List.of(CompanionInventory.PRIMARY_WEAPON, CompanionInventory.SECONDARY_WEAPON)) {
            String name = "container." + slot;
            for (Item item : List.of(Items.IRON_SWORD, Items.BOW, Items.SHIELD, Items.IRON_PICKAXE, Items.STONE)) {
                companion.inventory().setItem(slot, ItemStack.EMPTY);
                replaceItem(helper, companion, name, item);
                boolean weapon = item != Items.IRON_PICKAXE && item != Items.STONE;
                helper.assertValueEqual(itemIn(companion, name), weapon ? item : Items.AIR, name + " given " + item);
            }
        }
        helper.succeed();
    }

    @GameTest
    public void dropsEverythingWhenItDies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        CompanionInventory inventory = companion.inventory();
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 5));
        inventory.setItem(1, vanishing(helper, Items.GOLDEN_APPLE));
        inventory.setItem(35, new ItemStack(Items.EMERALD));
        inventory.setItem(CompanionInventory.PRIMARY_WEAPON, new ItemStack(Items.IRON_SWORD));
        inventory.setItem(CompanionInventory.SECONDARY_WEAPON, new ItemStack(Items.SHIELD));
        companion.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        companion.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        companion.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        companion.setItemSlot(EquipmentSlot.FEET, vanishing(helper, Items.IRON_BOOTS));

        level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
        try {
            companion.kill(level);
        } finally {
            level.getGameRules().set(GameRules.KEEP_INVENTORY, false, server);
        }

        Map<Item, Integer> dropped = level.getEntitiesOfClass(ItemEntity.class, companion.getBoundingBox().inflate(4)).stream()
                .collect(Collectors.toMap(drop -> drop.getItem().getItem(), drop -> drop.getItem().getCount(), Integer::sum));
        helper.assertValueEqual(
                dropped,
                Map.of(
                        Items.DIAMOND, 5,
                        Items.EMERALD, 1,
                        Items.IRON_SWORD, 1,
                        Items.SHIELD, 1,
                        Items.IRON_HELMET, 1,
                        Items.IRON_CHESTPLATE, 1,
                        Items.IRON_LEGGINGS, 1),
                "items dropped with keepInventory on, the cursed golden apple and boots destroyed");
        helper.assertTrue(inventory.isEmpty(), "the dead companion still holds items");
        helper.succeed();
    }

    @GameTest(maxTicks = 60)
    public void hoppersTakeNothingFromIt(GameTestHelper helper) {
        BlockPos hopper = new BlockPos(1, 1, 1);
        helper.setBlock(hopper, Blocks.HOPPER);
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        companion.inventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
        companion.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));

        // A hopper pulls every 8 ticks.
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(
                    helper.getBlockEntity(hopper, HopperBlockEntity.class).isEmpty(),
                    "the hopper took items from the companion");
            helper.assertValueEqual(companion.inventory().getItem(0).getCount(), 5, "diamonds left in the companion");
            helper.assertValueEqual(companion.getItemBySlot(EquipmentSlot.HEAD).getItem(), Items.IRON_HELMET, "helmet");
            helper.succeed();
        });
    }

    /**
     * From the player, armor to its slot and weapons to a free designated weapon slot, storage open or
     * not; anything else to the storage while the storage is open only. From the companion, back.
     */
    @GameTest
    public void shiftClickSortsAsAPlayerInventory(GameTestHelper helper) {
        for (boolean storage : List.of(false, true)) {
            Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            CompanionMenu menu = new CompanionMenu(1, player.getInventory(), companion, false);
            if (storage) {
                helper.assertTrue(menu.clickMenuButton(player, CompanionMenu.OPEN_STORAGE_BUTTON), "the storage did not open");
            }
            List<Item> carried = List.of(Items.IRON_HELMET, Items.IRON_SWORD, Items.SHIELD, Items.DIRT, Items.BOW);
            for (int i = 0; i < carried.size(); i++) {
                player.getInventory().setItem(9 + i, new ItemStack(carried.get(i)));
                // The player's slot 9 is the first of the menu's player slots.
                menu.clicked(CompanionMenu.PLAYER_START + i, 0, ContainerInput.QUICK_MOVE, player);
            }
            CompanionInventory inventory = companion.inventory();
            String on = storage ? " with the storage open" : " with the storage closed";
            helper.assertValueEqual(companion.getItemBySlot(EquipmentSlot.HEAD).getItem(), Items.IRON_HELMET, "armor" + on);
            helper.assertValueEqual(
                    inventory.getItem(CompanionInventory.PRIMARY_WEAPON).getItem(), Items.IRON_SWORD, "primary" + on);
            helper.assertValueEqual(
                    inventory.getItem(CompanionInventory.SECONDARY_WEAPON).getItem(), Items.SHIELD, "secondary" + on);
            helper.assertValueEqual(inventory.getItem(0).getItem(), storage ? Items.DIRT : Items.AIR, "first storage slot" + on);
            helper.assertValueEqual(
                    inventory.getItem(1).getItem(), storage ? Items.BOW : Items.AIR, "a weapon with no free weapon slot" + on);
            helper.assertValueEqual(
                    player.getInventory().countItem(Items.DIRT) + player.getInventory().countItem(Items.BOW),
                    storage ? 0 : 2,
                    "dirt and bow left to the player" + on);

            menu.clicked(CompanionMenu.ARMOR_START, 0, ContainerInput.QUICK_MOVE, player);
            helper.assertTrue(companion.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "the helmet stayed on the companion" + on);
            helper.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_HELMET)), "the helmet is not back" + on);
            companion.discard();
        }
        helper.succeed();
    }

    /**
     * While the storage is closed, the storage takes and gives nothing whatever the client sends:
     * clicks, shift-clicks and double-click gathering, through the server's packet handler. After
     * the storage's open button it does, until the close button, or until the Debug tab, which closes
     * the storage and on which the storage does not open.
     */
    @GameTest
    public void storageTakesNothingWhileClosed(GameTestHelper helper) {
        ServerPlayer player = connectPlayer(helper, "storage_user");
        try {
            Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            CompanionMenu.open(player, companion, true);
            CompanionMenu menu = (CompanionMenu) player.containerMenu;
            CompanionInventory storage = companion.inventory();
            checkStorage(helper, player, menu, storage, false, " once the screen opened");
            pressButton(player, menu, CompanionMenu.OPEN_STORAGE_BUTTON);
            checkStorage(helper, player, menu, storage, true, " after the open button");
            pressButton(player, menu, CompanionMenu.CLOSE_STORAGE_BUTTON);
            checkStorage(helper, player, menu, storage, false, " after the close button");
            pressButton(player, menu, CompanionMenu.OPEN_STORAGE_BUTTON);
            pressButton(player, menu, Tab.DEBUG.ordinal());
            helper.assertValueEqual(menu.tab(), Tab.DEBUG, "the tab after the Debug tab's button");
            checkStorage(helper, player, menu, storage, false, " after the Debug tab's button");
            pressButton(player, menu, CompanionMenu.OPEN_STORAGE_BUTTON);
            checkStorage(helper, player, menu, storage, false, " after the open button on the Debug tab");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** The Debug tab exists in a screen opened with the debug stick only. */
    @GameTest
    public void debugTabOnlyWithTheStick(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CompanionMenu plain = new CompanionMenu(1, player.getInventory(), companion, false);
        helper.assertValueEqual(plain.tabs(), List.of(Tab.COMPANION), "the tabs without the stick");
        helper.assertTrue(!plain.clickMenuButton(player, Tab.DEBUG.ordinal()), "the Debug tab opened without the stick");
        helper.assertValueEqual(plain.tab(), Tab.COMPANION, "the tab after the Debug tab's button without the stick");
        CompanionMenu debug = new CompanionMenu(2, player.getInventory(), companion, true);
        helper.assertValueEqual(debug.tabs(), List.of(Tab.COMPANION, Tab.DEBUG), "the tabs with the stick");
        helper.assertTrue(debug.clickMenuButton(player, Tab.DEBUG.ordinal()), "the Debug tab did not open with the stick");
        helper.assertValueEqual(debug.tab(), Tab.DEBUG, "the tab after the Debug tab's button with the stick");
        helper.succeed();
    }

    /** Checks each way of reaching the storage, from the same items each time. */
    private static void checkStorage(
            GameTestHelper helper,
            ServerPlayer player,
            CompanionMenu menu,
            CompanionInventory storage,
            boolean open,
            String on) {
        helper.assertValueEqual(menu.storageOpen(), open, "the storage open" + on);
        // Taking with a click.
        fillForStorageCheck(player, menu, storage);
        click(player, menu, CompanionMenu.STORAGE_START, ContainerInput.PICKUP);
        helper.assertValueEqual(storage.countItem(Items.DIAMOND), open ? 0 : 5, "stored diamonds after a click on them" + on);

        // Putting with a click: the dirt on the cursor, then on an empty storage slot.
        fillForStorageCheck(player, menu, storage);
        click(player, menu, CompanionMenu.PLAYER_START, ContainerInput.PICKUP);
        click(player, menu, CompanionMenu.STORAGE_START + 1, ContainerInput.PICKUP);
        helper.assertValueEqual(storage.countItem(Items.DIRT), open ? 3 : 0, "stored dirt after a click with it" + on);

        // Shift-click into the storage: to an empty slot, and onto a matching stack.
        fillForStorageCheck(player, menu, storage);
        click(player, menu, CompanionMenu.PLAYER_START, ContainerInput.QUICK_MOVE);
        click(player, menu, CompanionMenu.PLAYER_START + 1, ContainerInput.QUICK_MOVE);
        helper.assertValueEqual(storage.countItem(Items.DIRT), open ? 3 : 0, "stored dirt after a shift-click" + on);
        helper.assertValueEqual(storage.countItem(Items.DIAMOND), open ? 6 : 5, "stored diamonds after a shift-click" + on);

        // Shift-click out of the storage.
        fillForStorageCheck(player, menu, storage);
        click(player, menu, CompanionMenu.STORAGE_START, ContainerInput.QUICK_MOVE);
        helper.assertValueEqual(storage.countItem(Items.DIAMOND), open ? 0 : 5, "stored diamonds after a shift-click on them" + on);

        // Double-click gathering: a diamond on the cursor gathers the others.
        fillForStorageCheck(player, menu, storage);
        click(player, menu, CompanionMenu.PLAYER_START + 1, ContainerInput.PICKUP);
        click(player, menu, CompanionMenu.PLAYER_START + 1, ContainerInput.PICKUP_ALL);
        helper.assertValueEqual(storage.countItem(Items.DIAMOND), open ? 0 : 5, "stored diamonds after gathering" + on);
        helper.assertValueEqual(menu.getCarried().getCount(), open ? 6 : 1, "diamonds gathered on the cursor" + on);
    }

    /** Five diamonds in the storage; three dirt then one diamond in the player's first slots. */
    private static void fillForStorageCheck(ServerPlayer player, CompanionMenu menu, CompanionInventory storage) {
        menu.setCarried(ItemStack.EMPTY);
        storage.clearContent();
        storage.setItem(0, new ItemStack(Items.DIAMOND, 5));
        player.getInventory().clearContent();
        player.getInventory().setItem(9, new ItemStack(Items.DIRT, 3));
        player.getInventory().setItem(10, new ItemStack(Items.DIAMOND));
    }

    /** A menu button as a client sends it (a tab, the storage), through the server's packet handler. */
    private static void pressButton(ServerPlayer player, CompanionMenu menu, int button) {
        player.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(menu.containerId, button));
    }

    @GameTest
    public void menuOpenedWithoutTheStickIsValidForTheOwnerOnly(GameTestHelper helper) {
        ServerPlayer player = connectPlayer(helper, "not_owner");
        boolean valid;
        try {
            Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            CompanionMenu.open(player, companion, false);
            valid = player.containerMenu.stillValid(player);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        // No companion has an owner until ownership exists.
        helper.assertTrue(!valid, "a menu opened without the stick is valid for a player who does not own the companion");
        helper.succeed();
    }

    @GameTest
    public void menuIsInvalidOnceTheCompanionIsGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = connectPlayer(helper, "menu_user");
        try {
            checkMenuInvalidAfter(helper, player, companion -> companion.kill(level), "the companion died");
            checkMenuInvalidAfter(helper, player, Entity::discard, "the companion was discarded");
            checkMenuInvalidAfter(
                    helper,
                    player,
                    companion -> player.snapTo(companion.position().add(0.0, 0.0, 10.0)),
                    "the player went out of reach");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * Opens a companion's menu, checks that a click works, then that the menu turns invalid after
     * {@code change} and that a click no longer moves anything.
     */
    private static void checkMenuInvalidAfter(
            GameTestHelper helper, ServerPlayer player, Consumer<Companion> change, String reason) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
        player.getInventory().clearContent();
        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND));
        CompanionMenu.open(player, companion, true);
        helper.assertTrue(player.containerMenu instanceof CompanionMenu, "the menu did not open");
        CompanionMenu menu = (CompanionMenu) player.containerMenu;
        helper.assertTrue(menu.stillValid(player), "the menu is not valid next to a living companion");
        pressButton(player, menu, CompanionMenu.OPEN_STORAGE_BUTTON);
        click(player, menu, CompanionMenu.PLAYER_START, ContainerInput.QUICK_MOVE);
        helper.assertValueEqual(companion.inventory().countItem(Items.DIAMOND), 1, "diamonds moved by a valid click");

        player.getInventory().setItem(9, new ItemStack(Items.EMERALD));
        change.accept(companion);
        helper.assertTrue(!menu.stillValid(player), "the menu is still valid once " + reason);
        click(player, menu, CompanionMenu.PLAYER_START, ContainerInput.QUICK_MOVE);
        click(player, menu, CompanionMenu.STORAGE_START, ContainerInput.QUICK_MOVE);
        helper.assertValueEqual(player.getInventory().getItem(9).getItem(), Items.EMERALD, "the player's emerald once " + reason);
        helper.assertValueEqual(player.getInventory().countItem(Items.DIAMOND), 0, "diamonds taken back once " + reason);
        player.closeContainer();
        companion.discard();
    }

    /** A left click as a client sends it, through the server's packet handler. */
    private static void click(ServerPlayer player, CompanionMenu menu, int slot, ContainerInput input) {
        player.connection.handleContainerClick(new ServerboundContainerClickPacket(
                menu.containerId,
                menu.getStateId(),
                (short) slot,
                (byte) 0,
                input,
                Int2ObjectMaps.emptyMap(),
                HashedStack.EMPTY));
    }

    /** A player connected to the test's server, without a client. */
    static ServerPlayer connectPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static void replaceItem(GameTestHelper helper, Entity entity, String slot, Item item) {
        MinecraftServer server = helper.getLevel().getServer();
        String command = "item replace entity " + entity.getStringUUID() + " " + slot + " with " + item;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static Item itemIn(Entity entity, String slot) {
        return entity.getSlot(SlotRanges.nameToIds(slot).slots().getInt(0)).get().getItem();
    }

    private static ItemStack vanishing(GameTestHelper helper, Item item) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(
                helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.VANISHING_CURSE),
                1);
        return stack;
    }
}
