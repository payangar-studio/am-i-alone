package com.payangar.amialone.companion;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
 * The companion's screen, on both sides: its armor, its designated weapons, its storage,
 * and the inventory of the player who opened it, behind tabs. The client builds it from the
 * companion's entity id and an empty container, which the server's menu sync fills.
 *
 * <p>The client tells the server the open tab and the drawer's open panel, if any, with vanilla's
 * menu button packet ({@link #clickMenuButton}). The storage is open while the drawer shows the
 * {@link Panel#INVENTORY} panel only; otherwise the storage takes and gives nothing, whatever a
 * client sends. The drawer opens on the {@link Tab#COMPANION} tab only.
 *
 * <p>Its slots are not highlightable: the screen draws its own highlight, not vanilla's sprite.
 */
public class CompanionMenu extends CompanionViewMenu {
    public static final int ARMOR_START = 0;
    public static final int WEAPONS_START = 4;
    public static final int STORAGE_START = 6;
    public static final int PLAYER_START = STORAGE_START + CompanionInventory.STORAGE_SIZE;
    public static final int PLAYER_END = PLAYER_START + 36;

    /**
     * The menu buttons that open the drawer on a panel, or swap its panel, and close it; the tabs'
     * buttons are their ordinals.
     */
    public static final int OPEN_STORAGE_BUTTON = 10;
    public static final int CLOSE_DRAWER_BUTTON = 11;
    public static final int OPEN_ATTRIBUTES_BUTTON = 12;

    /**
     * Where the slots are drawn, in pixels from the screen's top left corner. The tabs stand above
     * the panel, which starts {@code PANEL_Y} down. As in vanilla's containers, a slot's box stands
     * {@code PADDING} pixels from the border, on every side, and its item 1 pixel inside the box.
     * The panel is as wide as the screen's right column needs, which ends {@code PADDING} from the
     * right border.
     */
    public static final int PANEL_Y = 16;
    public static final int PANEL_WIDTH = 224;
    public static final int PADDING = 7;
    public static final int ARMOR_X = PADDING + 1;
    public static final int WEAPONS_X = 80;
    public static final int COMPANION_Y = PANEL_Y + PADDING + 1;
    /** The player's inventory, under the companion, centred in the panel. */
    public static final int PLAYER_X = (PANEL_WIDTH - 9 * 18) / 2 + 1;
    public static final int PLAYER_Y = PANEL_Y + 94;
    public static final int HOTBAR_Y = PLAYER_Y + 58;
    /**
     * The storage's drawer when open: right of the panel, as high, its left edge 3 pixels under the
     * panel's. Its slots, in {@code STORAGE_COLUMNS} columns, sit where the open drawer shows them,
     * their boxes {@code PADDING} pixels from the panel's right border.
     */
    public static final int DRAWER_X = PANEL_WIDTH - 3;
    public static final int DRAWER_Y = PANEL_Y;
    public static final int STORAGE_COLUMNS = 4;
    public static final int STORAGE_X = PANEL_WIDTH - 1 + PADDING + 1;
    public static final int STORAGE_Y = DRAWER_Y + PADDING + 1;

    static final List<EquipmentSlot> ARMOR =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    public static final List<Identifier> ARMOR_ICONS = List.of(
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);
    public static final Identifier PRIMARY_WEAPON_ICON = Identifier.withDefaultNamespace("container/slot/sword");

    /** The drawer's panels, in the order of their handles. */
    public enum Panel {
        /** The storage. */
        INVENTORY(OPEN_STORAGE_BUTTON),
        ATTRIBUTES(OPEN_ATTRIBUTES_BUTTON);

        private final int button;

        Panel(int button) {
            this.button = button;
        }

        /** The menu button that opens the drawer on this panel. */
        public int button() {
            return button;
        }
    }

    /** The tabs, in their order; the ordinal is the menu button that opens one. */
    public enum Tab {
        COMPANION,
        /** Only in a screen the server opened with the debug stick. */
        DEBUG
    }

    /**
     * What the server sends the client when it opens the screen: the companion's entity id, and
     * whether it opened the screen with the debug stick, which gives it the {@link Tab#DEBUG} tab.
     */
    public record Opening(int companionId, boolean debug) {
        public static final StreamCodec<ByteBuf, Opening> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Opening::companionId, ByteBufCodecs.BOOL, Opening::debug, Opening::new);
    }

    private final boolean debug;
    private Tab tab = Tab.COMPANION;
    private @Nullable Panel panel;

    /** The server's menu, over the companion itself; {@code debug} when opened with the debug stick. */
    public CompanionMenu(int containerId, Inventory playerInventory, Companion companion, boolean debug) {
        this(containerId, playerInventory, companion, companion.inventory(), debug);
    }

    /** The client's menu, built from what the server sent. */
    public CompanionMenu(int containerId, Inventory playerInventory, Opening opening) {
        this(
                containerId,
                playerInventory,
                find(playerInventory.player, opening.companionId()),
                new CompanionInventory(),
                opening.debug());
    }

    private CompanionMenu(
            int containerId,
            Inventory playerInventory,
            LivingEntity companion,
            Container inventory,
            boolean debug) {
        super(Companions.MENU, containerId, playerInventory, companion);
        this.debug = debug;
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
            addSlot(new StorageSlot(inventory, i, STORAGE_X + i % STORAGE_COLUMNS * 18, STORAGE_Y + i / STORAGE_COLUMNS * 18));
        }
        addPlayerInventory(playerInventory);
    }

    /**
     * Opens the companion's screen for {@code player}, with its {@link Tab#DEBUG} tab when {@code
     * debug}: the caller decides it, the debug stick in an operator's hand.
     */
    public static void open(ServerPlayer player, Companion companion, boolean debug) {
        player.openMenu(new ExtendedMenuProvider<Opening>() {
            @Override
            public Opening getScreenOpeningData(ServerPlayer target) {
                return new Opening(companion.getId(), debug);
            }

            @Override
            public Component getDisplayName() {
                return companion.getName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return new CompanionMenu(containerId, inventory, companion, debug);
            }
        });
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

    /** Whether the server opened it with the debug stick, which gives it the {@link Tab#DEBUG} tab. */
    public boolean debug() {
        return debug;
    }

    /** The tabs this screen has, in their order. */
    public List<Tab> tabs() {
        return debug ? List.of(Tab.values()) : List.of(Tab.COMPANION);
    }

    public Tab tab() {
        return tab;
    }

    /** The drawer's open panel; none while the drawer is closed. */
    public @Nullable Panel panel() {
        return panel;
    }

    /**
     * Whether the storage is open, the drawer on the {@link Panel#INVENTORY} panel: only then does
     * the storage take and give items.
     */
    public boolean storageOpen() {
        return panel == Panel.INVENTORY;
    }

    /**
     * The client's choice: opens the tab whose ordinal is {@code id}, if this screen has it, or
     * opens the drawer on a panel, swaps its panel, or closes it. Leaving the {@link Tab#COMPANION}
     * tab closes the drawer.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == OPEN_STORAGE_BUTTON || id == OPEN_ATTRIBUTES_BUTTON || id == CLOSE_DRAWER_BUTTON) {
            if (tab != Tab.COMPANION) {
                return false;
            }
            panel = switch (id) {
                case OPEN_STORAGE_BUTTON -> Panel.INVENTORY;
                case OPEN_ATTRIBUTES_BUTTON -> Panel.ATTRIBUTES;
                default -> null;
            };
            return true;
        }
        if (id < 0 || id >= Tab.values().length || !tabs().contains(Tab.values()[id])) {
            return false;
        }
        tab = Tab.values()[id];
        if (tab != Tab.COMPANION) {
            panel = null;
        }
        return true;
    }

    /**
     * Valid while the companion is alive and within reach, as a horse's inventory, and, unless
     * opened with the debug stick, while the player is its owner.
     */
    @Override
    public boolean stillValid(Player player) {
        return companionInReach(player) && (debug || Companion.isOwner(companion(), player));
    }

    /**
     * Shift-click: from the player, armor to its slot, a weapon or a shield to a free designated
     * weapon slot, anything else to the storage while the storage is open only (the drawer on its
     * Inventory panel); from the companion, back to the player.
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
        int armor = ARMOR.indexOf(companion().getEquipmentSlotForItem(stack));
        if (armor >= 0 && moveItemStackTo(stack, ARMOR_START + armor, ARMOR_START + armor + 1, false)) {
            return true;
        }
        if (stack.is(CompanionInventory.WEAPONS) && moveItemStackTo(stack, WEAPONS_START, STORAGE_START, false)) {
            return true;
        }
        // Checked here too: moveItemStackTo merges into a matching stack without asking the slot.
        return storageOpen() && moveItemStackTo(stack, STORAGE_START, PLAYER_START, false);
    }

    /**
     * A storage slot, open while the storage is open only. Otherwise it is hidden on the client and
     * takes and gives nothing; double-click gathering asks {@link #mayPickup} too.
     */
    private class StorageSlot extends PlainSlot {
        StorageSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return storageOpen();
        }

        @Override
        public boolean mayPickup(Player player) {
            return storageOpen();
        }

        @Override
        public boolean isActive() {
            return storageOpen();
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
