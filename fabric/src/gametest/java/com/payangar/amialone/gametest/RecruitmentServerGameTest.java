package com.payangar.amialone.gametest;

import static com.payangar.amialone.gametest.CompanionInventoryServerGameTest.click;
import static com.payangar.amialone.gametest.CompanionInventoryServerGameTest.connectPlayer;
import static com.payangar.amialone.gametest.CompanionInventoryServerGameTest.pressButton;

import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionInventory;
import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.CompanionOwner;
import com.payangar.amialone.companion.Companions;
import com.payangar.amialone.companion.RecruitMenu;
import com.payangar.amialone.companion.Recruitment;
import com.payangar.amialone.companion.Recruitment.Payment;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Recruiting a companion: its price, the right-click that opens its screen, paying both ways. */
public class RecruitmentServerGameTest {
    @GameTest
    public void aNewCompanionKeepsItsPriceThroughSaving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        Integer price = companion.getAttached(Companions.PRICE);
        helper.assertTrue(price != null, "a new companion has no price");
        helper.assertTrue(
                price >= Recruitment.MIN_PRICE && price <= Recruitment.MAX_PRICE,
                "the price " + price + " is out of " + Recruitment.MIN_PRICE + " to " + Recruitment.MAX_PRICE);

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        helper.assertTrue(companion.save(output), "the companion is not saved with its chunk");
        companion.discard();
        Entity loaded = EntityType.loadEntityRecursive(
                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()),
                level,
                EntitySpawnReason.LOAD,
                entity -> entity);
        helper.assertTrue(loaded instanceof Companion, "the save did not load back as a companion: " + loaded);
        helper.assertValueEqual(((Companion) loaded).price(), price, "price after loading");
        helper.succeed();
    }

    @GameTest
    public void aRightClickOpensTheRecruitmentScreenWhileItHasNoOwner(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        ServerPlayer player = connectPlayer(helper, "recruiter");
        InteractionResult result;
        try {
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            result = player.interactOn(companion, InteractionHand.MAIN_HAND, companion.position());
            helper.assertTrue(
                    player.containerMenu instanceof RecruitMenu,
                    "a right-click opened no recruitment screen: " + player.containerMenu);
            RecruitMenu menu = (RecruitMenu) player.containerMenu;
            helper.assertValueEqual(menu.price(), companion.price(), "the screen's price");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.assertTrue(result instanceof InteractionResult.Success, "the companion did not take the click: " + result);
        helper.succeed();
    }

    @GameTest
    public void aRightClickByItsOwnerOpensItsScreen(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        ServerPlayer player = connectPlayer(helper, "owner");
        try {
            companion.setAttached(Companions.OWNER, CompanionOwner.of(player));
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            player.interactOn(companion, InteractionHand.MAIN_HAND, companion.position());
            helper.assertTrue(
                    player.containerMenu instanceof CompanionMenu menu && !menu.debug(),
                    "a right-click by the owner did not open the companion's screen: " + player.containerMenu);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** The screen carries a copy of the armor and designated weapons, which a client lacks. */
    @GameTest
    public void theScreenShowsItsEquipment(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        companion.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        companion.inventory().setItem(CompanionInventory.PRIMARY_WEAPON, new ItemStack(Items.IRON_SWORD));
        ServerPlayer player = connectPlayer(helper, "viewer");
        try {
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            RecruitMenu.open(player, companion, Payment.BUTTON);
            List<ItemStack> equipment = ((RecruitMenu) player.containerMenu).equipment();
            helper.assertValueEqual(equipment.size(), RecruitMenu.EQUIPMENT_SIZE, "pieces of equipment shown");
            helper.assertTrue(equipment.get(1).is(Items.IRON_CHESTPLATE), "the chestplate is not shown: " + equipment);
            helper.assertTrue(equipment.get(4).is(Items.IRON_SWORD), "the primary weapon is not shown: " + equipment);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest
    public void recruitingWithTheButtonTakesThePrice(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        int price = companion.price();
        ServerPlayer player = connectPlayer(helper, "buyer");
        try {
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            // Split over two stacks, so the taking crosses stacks.
            player.getInventory().setItem(3, new ItemStack(Items.EMERALD, 10));
            player.getInventory().setItem(20, new ItemStack(Items.EMERALD, price));
            RecruitMenu.open(player, companion, Payment.BUTTON);
            pressButton(player, player.containerMenu, RecruitMenu.RECRUIT_BUTTON);

            helper.assertTrue(Companion.isOwner(companion, player), "the buyer does not own the companion");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 10, "emeralds left after paying");
            helper.assertTrue(
                    player.containerMenu instanceof CompanionMenu menu && !menu.debug(),
                    "the companion's screen did not open after recruiting: " + player.containerMenu);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest
    public void recruitingWithTooFewEmeraldsTakesNothing(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        int price = companion.price();
        ServerPlayer player = connectPlayer(helper, "poor_buyer");
        try {
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            player.getInventory().setItem(3, new ItemStack(Items.EMERALD, price - 1));
            RecruitMenu.open(player, companion, Payment.BUTTON);
            helper.assertTrue(!((RecruitMenu) player.containerMenu).canPay(player), "Recruit is active with too few emeralds");
            pressButton(player, player.containerMenu, RecruitMenu.RECRUIT_BUTTON);

            helper.assertTrue(!Companion.hasOwner(companion), "the companion has an owner without being paid");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), price - 1, "emeralds after a refused Recruit");
            helper.assertTrue(player.containerMenu instanceof RecruitMenu, "the recruitment screen closed");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest
    public void recruitingWithThePaymentSlotTakesThePriceFromIt(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        int price = companion.price();
        ServerPlayer player = connectPlayer(helper, "slot_buyer");
        try {
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            // Inventory slot 9 is the menu's first player slot.
            player.getInventory().setItem(9, new ItemStack(Items.EMERALD, price + 5));
            player.getInventory().setItem(10, new ItemStack(Items.DIAMOND));
            RecruitMenu.open(player, companion, Payment.SLOT);
            RecruitMenu menu = (RecruitMenu) player.containerMenu;
            helper.assertTrue(!menu.canPay(player), "Recruit is active with an empty payment slot");
            click(player, menu, RecruitMenu.PLAYER_START + 1, ContainerInput.QUICK_MOVE);
            helper.assertTrue(!menu.getSlot(RecruitMenu.PAYMENT_SLOT).hasItem(), "a diamond went to the payment slot");
            click(player, menu, RecruitMenu.PLAYER_START, ContainerInput.QUICK_MOVE);
            helper.assertValueEqual(
                    menu.getSlot(RecruitMenu.PAYMENT_SLOT).getItem().getCount(), price + 5, "emeralds in the payment slot");
            pressButton(player, menu, RecruitMenu.RECRUIT_BUTTON);

            helper.assertTrue(Companion.isOwner(companion, player), "the buyer does not own the companion");
            // The rest went back to the inventory when the companion's screen replaced this one.
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 5, "emeralds left after paying");
            helper.assertTrue(player.containerMenu instanceof CompanionMenu, "the companion's screen did not open");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest
    public void emeraldsLeftInThePaymentSlotGoBackOnClose(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        int price = companion.price();
        ServerPlayer player = connectPlayer(helper, "hesitant");
        try {
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            player.getInventory().setItem(9, new ItemStack(Items.EMERALD, price - 1));
            RecruitMenu.open(player, companion, Payment.SLOT);
            RecruitMenu menu = (RecruitMenu) player.containerMenu;
            click(player, menu, RecruitMenu.PLAYER_START, ContainerInput.QUICK_MOVE);
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 0, "emeralds in the inventory once in the slot");
            pressButton(player, menu, RecruitMenu.RECRUIT_BUTTON);
            helper.assertTrue(!Companion.hasOwner(companion), "the companion was recruited below its price");
            player.closeContainer();
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), price - 1, "emeralds back after closing");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** The button way has no payment slot: nothing goes there, whatever a client sends. */
    @GameTest
    public void theButtonWayHasNoPaymentSlot(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        ServerPlayer player = connectPlayer(helper, "button_user");
        try {
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 10));
            RecruitMenu.open(player, companion, Payment.BUTTON);
            RecruitMenu menu = (RecruitMenu) player.containerMenu;
            click(player, menu, RecruitMenu.PLAYER_START, ContainerInput.QUICK_MOVE);
            click(player, menu, RecruitMenu.PLAYER_START, ContainerInput.PICKUP);
            click(player, menu, RecruitMenu.PAYMENT_SLOT, ContainerInput.PICKUP);
            helper.assertTrue(!menu.getSlot(RecruitMenu.PAYMENT_SLOT).hasItem(), "emeralds went to a hidden payment slot");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest
    public void theFirstToPayRecruitsItAndTheOtherScreenCloses(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        ServerPlayer first = connectPlayer(helper, "first");
        ServerPlayer second = connectPlayer(helper, "second");
        try {
            for (ServerPlayer player : List.of(first, second)) {
                player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
                player.getInventory().setItem(3, new ItemStack(Items.EMERALD, 64));
                RecruitMenu.open(player, companion, Payment.BUTTON);
            }
            RecruitMenu secondMenu = (RecruitMenu) second.containerMenu;
            pressButton(first, first.containerMenu, RecruitMenu.RECRUIT_BUTTON);
            helper.assertTrue(Companion.isOwner(companion, first), "the first to pay does not own the companion");
            helper.assertTrue(!secondMenu.stillValid(second), "the other recruitment screen is still valid");
            pressButton(second, secondMenu, RecruitMenu.RECRUIT_BUTTON);
            helper.assertTrue(Companion.isOwner(companion, first), "the second player took the companion over");
            helper.assertValueEqual(second.getInventory().countItem(Items.EMERALD), 64, "the second player's emeralds");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(first);
            helper.getLevel().getServer().getPlayerList().remove(second);
        }
        helper.succeed();
    }

    @GameTest
    public void anotherPlayersCompanionCannotBeRecruited(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        CompanionOwner owner = new CompanionOwner(UUID.randomUUID(), "someone_else");
        companion.setAttached(Companions.OWNER, owner);
        ServerPlayer player = connectPlayer(helper, "thief");
        try {
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            player.getInventory().setItem(3, new ItemStack(Items.EMERALD, 64));
            // A screen opened by other means: still not valid, so its button is refused.
            RecruitMenu.open(player, companion, Payment.BUTTON);
            pressButton(player, player.containerMenu, RecruitMenu.RECRUIT_BUTTON);
            helper.assertValueEqual(companion.getAttached(Companions.OWNER), owner, "owner");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 64, "emeralds");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }
}
