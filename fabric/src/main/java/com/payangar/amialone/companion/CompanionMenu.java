package com.payangar.amialone.companion;

import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.ticks.ContainerSingleItem;
import org.jspecify.annotations.Nullable;

/**
 * The companion's inventory screen, on both sides: its armor, its designated weapons, its storage,
 * and the inventory of the player who opened it. The client builds it from the companion's entity
 * id and an empty container, which the server's menu sync fills.
 *
 * <p>Its slots are not highlightable: the screen draws its own highlight, not vanilla's sprite.
 */
public class CompanionMenu extends AbstractContainerMenu {
    public static final int ARMOR_START = 0;
    public static final int WEAPONS_START = 4;
    public static final int STORAGE_START = 6;
    public static final int PLAYER_START = STORAGE_START + CompanionInventory.STORAGE_SIZE;
    public static final int PLAYER_END = PLAYER_START + 36;

    /** Where the slots are drawn, in pixels from the screen's top left corner. */
    public static final int ARMOR_X = 8;
    public static final int WEAPONS_X = 80;
    public static final int STORAGE_X = 104;
    public static final int COMPANION_Y = 18;
    public static final int PLAYER_X = 56;
    public static final int PLAYER_Y = 108;
    public static final int HOTBAR_Y = PLAYER_Y + 58;

    private static final List<EquipmentSlot> ARMOR =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final List<Identifier> ARMOR_ICONS = List.of(
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);
    private static final Identifier PRIMARY_WEAPON_ICON = Identifier.withDefaultNamespace("container/slot/sword");

    private final LivingEntity companion;

    /** The server's menu, over the companion itself. */
    public CompanionMenu(int containerId, Inventory playerInventory, Companion companion) {
        this(containerId, playerInventory, companion, companion.inventory());
    }

    /** The client's menu, built from the entity id the server sent. */
    public CompanionMenu(int containerId, Inventory playerInventory, Integer companionId) {
        this(containerId, playerInventory, find(playerInventory.player, companionId), new CompanionInventory());
    }

    private CompanionMenu(int containerId, Inventory playerInventory, LivingEntity companion, Container inventory) {
        super(Companions.MENU, containerId);
        this.companion = companion;
        for (int i = 0; i < ARMOR.size(); i++) {
            EquipmentSlot slot = ARMOR.get(i);
            addSlot(new ArmorSlot(equipment(companion, slot), companion, slot, 0, ARMOR_X, COMPANION_Y + i * 18, ARMOR_ICONS.get(i)) {
                @Override
                public boolean isHighlightable() {
                    return false;
                }
            });
        }
        addSlot(new WeaponSlot(inventory, CompanionInventory.PRIMARY_WEAPON, WEAPONS_X, COMPANION_Y, PRIMARY_WEAPON_ICON));
        addSlot(new WeaponSlot(
                inventory,
                CompanionInventory.SECONDARY_WEAPON,
                WEAPONS_X,
                COMPANION_Y + 18,
                InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD));
        for (int i = 0; i < CompanionInventory.STORAGE_SIZE; i++) {
            addSlot(new PlainSlot(inventory, i, STORAGE_X + i % 9 * 18, COMPANION_Y + i / 9 * 18));
        }
        // The player's inventory as vanilla lays it out: its three rows, then its hotbar.
        for (int i = 9; i < 36; i++) {
            addSlot(new PlainSlot(playerInventory, i, PLAYER_X + i % 9 * 18, PLAYER_Y + (i / 9 - 1) * 18));
        }
        for (int i = 0; i < 9; i++) {
            addSlot(new PlainSlot(playerInventory, i, PLAYER_X + i * 18, HOTBAR_Y));
        }
    }

    /** Opens the companion's inventory screen for {@code player}. */
    public static void open(ServerPlayer player, Companion companion) {
        player.openMenu(new ExtendedMenuProvider<Integer>() {
            @Override
            public Integer getScreenOpeningData(ServerPlayer target) {
                return companion.getId();
            }

            @Override
            public Component getDisplayName() {
                return companion.getName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return new CompanionMenu(containerId, inventory, companion);
            }
        });
    }

    private static LivingEntity find(Player player, int id) {
        if (player.level().getEntity(id) instanceof LivingEntity entity) {
            return entity;
        }
        throw new IllegalStateException("The companion " + id + " of an opened inventory screen is not on the client");
    }

    /** One equipment slot of {@code entity} as a container, for vanilla's {@link ArmorSlot}. */
    private static Container equipment(LivingEntity entity, EquipmentSlot slot) {
        return new ContainerSingleItem() {
            @Override
            public ItemStack getTheItem() {
                return entity.getItemBySlot(slot);
            }

            @Override
            public void setTheItem(ItemStack stack) {
                entity.setItemSlot(slot, stack);
            }

            @Override
            public void setChanged() {}

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };
    }

    public LivingEntity companion() {
        return companion;
    }

    /** Valid while the companion is alive and within reach, as a horse's inventory. */
    @Override
    public boolean stillValid(Player player) {
        return companion.isAlive() && player.isWithinEntityInteractionRange(companion, 4.0);
    }

    /**
     * Shift-click: from the player, armor to its slot, a weapon or a shield to a free designated
     * weapon slot, anything else to the storage; from the companion, back to the player.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveToCompanion(stack)) {
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

    private boolean moveToCompanion(ItemStack stack) {
        int armor = ARMOR.indexOf(companion.getEquipmentSlotForItem(stack));
        if (armor >= 0 && moveItemStackTo(stack, ARMOR_START + armor, ARMOR_START + armor + 1, false)) {
            return true;
        }
        if (stack.is(CompanionInventory.WEAPONS) && moveItemStackTo(stack, WEAPONS_START, STORAGE_START, false)) {
            return true;
        }
        return moveItemStackTo(stack, STORAGE_START, PLAYER_START, false);
    }

    private static class PlainSlot extends Slot {
        PlainSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean isHighlightable() {
            return false;
        }
    }

    /** A designated weapon slot: one item of {@link CompanionInventory#WEAPONS}. */
    private static class WeaponSlot extends PlainSlot {
        private final Identifier emptyIcon;

        WeaponSlot(Container container, int slot, int x, int y, Identifier emptyIcon) {
            super(container, slot, x, y);
            this.emptyIcon = emptyIcon;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public @Nullable Identifier getNoItemIcon() {
            return emptyIcon;
        }
    }
}
