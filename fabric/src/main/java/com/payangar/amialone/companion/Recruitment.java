package com.payangar.amialone.companion;

import java.util.Locale;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The rules of recruiting a companion: its price, and how a player pays it. */
public final class Recruitment {
    public static final int MIN_PRICE = 12;
    public static final int MAX_PRICE = 36;
    /**
     * How much the companion's opinion moves the price, as a villager's offer's price multiplier:
     * between their 0.05 (most offers) and 0.2 (armour, tools, books).
     */
    public static final double OPINION_MULTIPLIER = 0.1;

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
     * What {@code player} pays to recruit {@code companion}: its drawn price, lowered or raised by
     * its opinion of them and lowered by Hero of the Village, as a villager's offer
     * ({@code Villager.updateSpecialPrices}); never below 1, with no cap.
     */
    public static int priceFor(Companion companion, Player player) {
        int base = companion.price();
        int price = base;
        int opinion = companion.opinionOf(player);
        if (opinion != 0) {
            price -= Mth.floor(opinion * OPINION_MULTIPLIER);
        }
        MobEffectInstance hero = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        if (hero != null) {
            double heroModifier = 0.3F + 0.0625F * hero.getAmplifier();
            price -= Math.max((int) Math.floor(heroModifier * base), 1);
        }
        return Math.max(price, 1);
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
