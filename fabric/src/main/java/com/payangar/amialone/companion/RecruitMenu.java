package com.payangar.amialone.companion;

import com.payangar.amialone.companion.Recruitment.Payment;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A companion's recruitment screen, on both sides: what a player sees of a companion that has no
 * owner before paying for it. Its equipment is shown, not reachable: the menu has no slot over the
 * companion, only the player's inventory and, when the server picked that way, a payment slot.
 *
 * <p>Recruit is the menu button {@link #RECRUIT_BUTTON}: the server checks the payment again,
 * takes it, makes the player the owner and opens the companion's screen in its place.
 */
public class RecruitMenu extends CompanionViewMenu {
    public static final int RECRUIT_BUTTON = 0;
    public static final int PAYMENT_SLOT = 0;
    public static final int PLAYER_START = 1;
    public static final int PLAYER_END = PLAYER_START + 36;

    /**
     * The payment slot, at the bottom of the column right of the weapons, level with the boots,
     * its box a padding away from the weapons' boxes.
     */
    public static final int INFO_X = CompanionMenu.WEAPONS_X - 1 + 18 + CompanionMenu.PADDING;
    public static final int PAYMENT_X = INFO_X + 1;
    public static final int PAYMENT_Y = CompanionMenu.COMPANION_Y + 3 * 18;

    /** The armor shown, top to bottom, then the primary and secondary designated weapons. */
    public static final int EQUIPMENT_SIZE = CompanionMenu.ARMOR.size() + 2;

    /**
     * What the server sends the client when it opens the screen: the companion's entity id, its
     * price, the way to pay, and a copy of its armor and designated weapons, which a client does
     * not otherwise have.
     */
    public record Opening(int companionId, int price, Payment payment, List<ItemStack> equipment) {
        private static final StreamCodec<ByteBuf, Payment> PAYMENT_CODEC =
                ByteBufCodecs.BOOL.map(slot -> slot ? Payment.SLOT : Payment.BUTTON, payment -> payment == Payment.SLOT);

        public static final StreamCodec<RegistryFriendlyByteBuf, Opening> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                Opening::companionId,
                ByteBufCodecs.VAR_INT,
                Opening::price,
                PAYMENT_CODEC,
                Opening::payment,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(EQUIPMENT_SIZE)),
                Opening::equipment,
                Opening::new);
    }

    private final int price;
    private final Payment payment;
    private final List<ItemStack> equipment;
    private final Container paymentSlot = new SimpleContainer(1);

    /** The server's menu, over the companion itself. */
    public RecruitMenu(int containerId, Inventory playerInventory, Companion companion, Payment payment) {
        this(containerId, playerInventory, companion, companion.price(), payment, equipmentOf(companion));
    }

    /** The client's menu, built from what the server sent. */
    public RecruitMenu(int containerId, Inventory playerInventory, Opening opening) {
        this(
                containerId,
                playerInventory,
                find(playerInventory.player, opening.companionId()),
                opening.price(),
                opening.payment(),
                opening.equipment());
    }

    private RecruitMenu(
            int containerId,
            Inventory playerInventory,
            LivingEntity companion,
            int price,
            Payment payment,
            List<ItemStack> equipment) {
        super(Companions.RECRUIT_MENU, containerId, playerInventory, companion);
        this.price = price;
        this.payment = payment;
        this.equipment = List.copyOf(equipment);
        addSlot(new PaymentSlot(paymentSlot, PAYMENT_X, PAYMENT_Y));
        addPlayerInventory(playerInventory);
    }

    /**
     * Opens the recruitment screen of {@code companion}, which has no owner, for {@code player},
     * with the way to pay of the development switch.
     */
    public static void open(ServerPlayer player, Companion companion) {
        open(player, companion, Payment.current());
    }

    /** Opens the recruitment screen with the way to pay {@code payment}: tests try both. */
    public static void open(ServerPlayer player, Companion companion, Payment payment) {
        player.openMenu(new ExtendedMenuProvider<Opening>() {
            @Override
            public Opening getScreenOpeningData(ServerPlayer target) {
                return new Opening(companion.getId(), companion.price(), payment, equipmentOf(companion));
            }

            @Override
            public Component getDisplayName() {
                return companion.getName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return new RecruitMenu(containerId, inventory, companion, payment);
            }
        });
    }

    /** A copy of the companion's armor, top to bottom, then of its designated weapons. */
    private static List<ItemStack> equipmentOf(Companion companion) {
        List<ItemStack> equipment = new ArrayList<>();
        for (EquipmentSlot slot : CompanionMenu.ARMOR) {
            equipment.add(companion.getItemBySlot(slot).copy());
        }
        equipment.add(companion.inventory().getItem(CompanionInventory.PRIMARY_WEAPON).copy());
        equipment.add(companion.inventory().getItem(CompanionInventory.SECONDARY_WEAPON).copy());
        return equipment;
    }

    /** Its price in emeralds. */
    public int price() {
        return price;
    }

    public Payment payment() {
        return payment;
    }

    /** Its armor, top to bottom, then its designated weapons, as they were when the screen opened. */
    public List<ItemStack> equipment() {
        return equipment;
    }

    /**
     * Whether {@code player} can pay the price: with the button, the emeralds of their inventory;
     * with the payment slot, the emeralds in it. The client asks it to grey Recruit.
     */
    public boolean canPay(Player player) {
        return switch (payment) {
            case BUTTON -> Recruitment.emeralds(player.getInventory()) >= price;
            case SLOT -> paymentSlot.getItem(0).is(Items.EMERALD) && paymentSlot.getItem(0).getCount() >= price;
        };
    }

    /**
     * Recruit, on the server: takes exactly the price, makes {@code player} the companion's owner,
     * then opens the companion's screen, which closes this one. Vanilla calls it for the open menu
     * only, once {@link #stillValid} said the companion has no owner yet.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != RECRUIT_BUTTON
                || !(player instanceof ServerPlayer serverPlayer)
                || !(companion() instanceof Companion companion)
                || !canPay(player)) {
            return false;
        }
        switch (payment) {
            case BUTTON -> Recruitment.takeEmeralds(player.getInventory(), price);
            case SLOT -> paymentSlot.removeItem(0, price);
        }
        companion.setAttached(Companions.OWNER, CompanionOwner.of(player));
        CompanionMenu.open(serverPlayer, companion, false);
        return true;
    }

    /** Valid while the companion is alive, within reach, and still has no owner. */
    @Override
    public boolean stillValid(Player player) {
        return companionInReach(player) && !Companion.hasOwner(companion());
    }

    /**
     * Shift-click: emeralds from the player to the payment slot, when that is the way to pay; from
     * the payment slot, back to the player.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        boolean done = index == PAYMENT_SLOT
                ? moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)
                : payment == Payment.SLOT && stack.is(Items.EMERALD) && moveItemStackTo(stack, PAYMENT_SLOT, PLAYER_START, false);
        if (!done) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, moved);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == moved.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return moved;
    }

    /**
     * Gives back what is left in the payment slot, as a villager's screen does: to the inventory,
     * dropped when it does not fit or the player is dead or gone.
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer) {
            clearContainer(player, paymentSlot);
        }
    }

    /** The payment slot: emeralds only, and only when that is the way to pay. */
    private class PaymentSlot extends PlainSlot {
        PaymentSlot(Container container, int x, int y) {
            super(container, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return payment == Payment.SLOT && stack.is(Items.EMERALD);
        }

        @Override
        public boolean isActive() {
            return payment == Payment.SLOT;
        }
    }
}
