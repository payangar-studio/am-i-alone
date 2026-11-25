package com.payangar.amialone.companion;

import com.payangar.amialone.CommonEntrypoint;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * What a companion carries besides its armor: 36 slots of storage and its two designated weapons,
 * primary and secondary. No hotbar and no selected slot: what it holds in hand is its brain's
 * choice, not a slot of this container.
 */
public class CompanionInventory extends SimpleContainer {
    public static final int STORAGE_SIZE = 36;
    public static final int PRIMARY_WEAPON = 36;
    public static final int SECONDARY_WEAPON = 37;
    public static final int SIZE = 38;

    /** What a designated weapon slot takes: a weapon or a shield. A datapack adds to it. */
    public static final TagKey<Item> WEAPONS = TagKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(CommonEntrypoint.MOD_ID, "companion_weapons"));

    public CompanionInventory() {
        super(SIZE);
    }

    public static boolean isWeaponSlot(int slot) {
        return slot == PRIMARY_WEAPON || slot == SECONDARY_WEAPON;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return !isWeaponSlot(slot) || stack.is(WEAPONS);
    }

    /** A slot for {@code /item}, which refuses what the slot does not take, and more than one weapon. */
    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        if (slot < 0 || slot >= getContainerSize()) {
            return null;
        }
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return getItem(slot);
            }

            @Override
            public boolean set(ItemStack stack) {
                if (!stack.isEmpty() && (!canPlaceItem(slot, stack) || isWeaponSlot(slot) && stack.getCount() > 1)) {
                    return false;
                }
                setItem(slot, stack);
                return true;
            }
        };
    }

    /** Writes the slots that hold something, with their index, as the player's inventory does. */
    public void save(ValueOutput.TypedOutputList<ItemStackWithSlot> output) {
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty()) {
                output.add(new ItemStackWithSlot(i, stack));
            }
        }
    }

    public void load(ValueInput.TypedInputList<ItemStackWithSlot> input) {
        clearContent();
        for (ItemStackWithSlot item : input) {
            if (item.isValidInContainer(getContainerSize())) {
                setItem(item.slot(), item.stack());
            }
        }
    }
}
