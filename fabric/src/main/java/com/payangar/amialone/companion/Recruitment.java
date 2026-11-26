package com.payangar.amialone.companion;

import java.util.Locale;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The rules of recruiting a companion: its price, and how a player pays it. */
public final class Recruitment {
    public static final int MIN_PRICE = 12;
    public static final int MAX_PRICE = 36;

    /**
     * The development switch that picks the way to pay while both are tried: the system property
     * {@code am_i_alone.recruit.payment}, {@code slot} for the payment slot, the button otherwise.
     * Read by the server, which sends the way to the client.
     */
    public static final String PAYMENT_PROPERTY = "am_i_alone.recruit.payment";

    /**
     * How a player pays: with a button that takes the emeralds from their inventory, or by putting
     * them in a payment slot first.
     */
    public enum Payment {
        BUTTON,
        SLOT;

        /** The way the server picks, from {@link #PAYMENT_PROPERTY}. */
        public static Payment current() {
            String value = System.getProperty(PAYMENT_PROPERTY, "");
            return value.toLowerCase(Locale.ROOT).equals("slot") ? SLOT : BUTTON;
        }
    }

    private Recruitment() {}

    /** A new companion's price, uniformly from {@link #MIN_PRICE} to {@link #MAX_PRICE}. */
    public static int drawPrice(RandomSource random) {
        return random.nextIntBetweenInclusive(MIN_PRICE, MAX_PRICE);
    }

    /**
     * The emeralds in the player's 36 inventory slots, the ones the recruitment screen shows: the
     * client and the server count the same.
     */
    public static int emeralds(Inventory inventory) {
        int count = 0;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.EMERALD)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Takes {@code amount} emeralds from the player's 36 inventory slots, which hold that many. */
    public static void takeEmeralds(Inventory inventory, int amount) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE && amount > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.EMERALD)) {
                int taken = Math.min(amount, stack.getCount());
                stack.shrink(taken);
                amount -= taken;
            }
        }
        inventory.setChanged();
    }
}
