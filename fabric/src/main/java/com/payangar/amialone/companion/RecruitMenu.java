package com.payangar.amialone.companion;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * A companion's recruitment screen, on both sides: what a player sees of a companion that has no
 * owner before paying for it. Its equipment is shown, not reachable: the menu has no slot over the
 * companion, only the player's inventory.
 *
 * <p>Recruit is the menu button {@link #RECRUIT_BUTTON}: the server checks the player's emeralds
 * again, takes the price from their inventory, makes them the owner and opens the companion's
 * screen in its place.
 */
public class RecruitMenu extends CompanionViewMenu {
    public static final int RECRUIT_BUTTON = 0;

    /** The armor shown, top to bottom, then the primary and secondary designated weapons. */
    public static final int EQUIPMENT_SIZE = CompanionMenu.ARMOR.size() + 2;

    /**
     * What the server sends the client when it opens the screen: the companion's entity id, the
     * price to pay and its drawn price, and a copy of its armor and designated weapons, which a
     * client does not otherwise have.
     */
    public record Opening(int companionId, int price, int basePrice, List<ItemStack> equipment) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Opening> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                Opening::companionId,
                ByteBufCodecs.VAR_INT,
                Opening::price,
                ByteBufCodecs.VAR_INT,
                Opening::basePrice,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(EQUIPMENT_SIZE)),
                Opening::equipment,
                Opening::new);
    }

    private final int price;
    private final int basePrice;
    private final List<ItemStack> equipment;

    /** The server's menu, over the companion itself, at {@code price}. */
    public RecruitMenu(int containerId, Inventory playerInventory, Companion companion, int price) {
        this(containerId, playerInventory, companion, price, companion.price(), equipmentOf(companion));
    }

    /** The client's menu, built from what the server sent. */
    public RecruitMenu(int containerId, Inventory playerInventory, Opening opening) {
        this(
                containerId,
                playerInventory,
                find(playerInventory.player, opening.companionId()),
                opening.price(),
                opening.basePrice(),
                opening.equipment());
    }

    private RecruitMenu(
            int containerId,
            Inventory playerInventory,
            LivingEntity companion,
            int price,
            int basePrice,
            List<ItemStack> equipment) {
        super(Companions.RECRUIT_MENU, containerId, playerInventory, companion);
        this.price = price;
        this.basePrice = basePrice;
        this.equipment = List.copyOf(equipment);
        addPlayerInventory(playerInventory);
    }

    /**
     * Opens the recruitment screen of {@code companion}, which has no owner, for {@code player}. The
     * price is the player's, from the companion's opinion of them, when the screen opens.
     */
    public static void open(ServerPlayer player, Companion companion) {
        int price = Recruitment.priceFor(companion, player);
        player.openMenu(new ExtendedMenuProvider<Opening>() {
            @Override
            public Opening getScreenOpeningData(ServerPlayer target) {
                return new Opening(companion.getId(), price, companion.price(), equipmentOf(companion));
            }

            @Override
            public Component getDisplayName() {
                return companion.getName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return new RecruitMenu(containerId, inventory, companion, price);
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

    /** What the player pays, in emeralds. */
    public int price() {
        return price;
    }

    /** The companion's drawn price, before its opinion of the player and Hero of the Village. */
    public int basePrice() {
        return basePrice;
    }

    /** Its armor, top to bottom, then its designated weapons, as they were when the screen opened. */
    public List<ItemStack> equipment() {
        return equipment;
    }

    /** Whether {@code player} has the price in their inventory. The client asks it to grey Recruit. */
    public boolean canPay(Player player) {
        return Recruitment.emeralds(player.getInventory()) >= price;
    }

    /**
     * Recruit, on the server: takes exactly the price from the player's inventory, makes {@code
     * player} the companion's owner, then opens the companion's screen, which closes this one.
     * Vanilla calls it for the open menu only, once {@link #stillValid} said the companion has no
     * owner yet.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != RECRUIT_BUTTON
                || !(player instanceof ServerPlayer serverPlayer)
                || !(companion() instanceof Companion companion)
                || !canPay(player)) {
            return false;
        }
        Recruitment.takeEmeralds(player.getInventory(), price);
        companion.setAttached(Companions.OWNER, CompanionOwner.of(player));
        CompanionMenu.open(serverPlayer, companion, false);
        return true;
    }

    /** Valid while the companion is alive, within reach, and still has no owner. */
    @Override
    public boolean stillValid(Player player) {
        return companionInReach(player) && !Companion.hasOwner(companion());
    }

    /** Shift-click moves nothing: the screen has nowhere to put an item. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
