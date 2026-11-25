package com.payangar.amialone.client.companion;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * An entity's health and armor bars, drawn as the HUD draws the player's ({@code
 * Hud.extractPlayerHealth}, {@code extractArmor}, {@code extractHearts}): vanilla's sprites, so a
 * resource pack applies, and vanilla's rules: rows, half hearts, hardcore, poisoned, withered,
 * frozen, absorption, the low-health wobble, the regeneration bounce, the blink when hit. Only
 * the anchor differs: the HUD's bars grow up from the hotbar, these grow down from {@code top}.
 *
 * <p>It keeps its own blink state, vanilla's {@code Hud} fields, so whoever draws it holds one.
 */
public final class HealthBars {
    private static final Identifier ARMOR_EMPTY = Identifier.withDefaultNamespace("hud/armor_empty");
    private static final Identifier ARMOR_HALF = Identifier.withDefaultNamespace("hud/armor_half");
    private static final Identifier ARMOR_FULL = Identifier.withDefaultNamespace("hud/armor_full");

    private final RandomSource random = RandomSource.create();
    private int lastHealth;
    private int displayHealth;
    private long lastHealthTime;
    private long healthBlinkTime;

    /** The health, in half hearts, the bars were last drawn with. */
    public int lastHealth() {
        return lastHealth;
    }

    /**
     * Draws the bars of {@code entity}, the armor row's top left corner at {@code xLeft, top}.
     *
     * @param tickCount the GUI's tick count, which paces the blink, the wobble and the bounce
     */
    public void extract(GuiGraphicsExtractor graphics, LivingEntity entity, int xLeft, int top, int tickCount) {
        int currentHealth = Mth.ceil(entity.getHealth());
        boolean blink = healthBlinkTime > tickCount && (healthBlinkTime - tickCount) / 3L % 2L == 1L;
        long timeMillis = Util.getMillis();
        if (currentHealth < lastHealth && entity.damageCooldownTime > 0) {
            lastHealthTime = timeMillis;
            healthBlinkTime = tickCount + 20;
        } else if (currentHealth > lastHealth && entity.damageCooldownTime > 0) {
            lastHealthTime = timeMillis;
            healthBlinkTime = tickCount + 10;
        }
        if (timeMillis - lastHealthTime > 1000L) {
            displayHealth = currentHealth;
            lastHealthTime = timeMillis;
        }
        lastHealth = currentHealth;
        int oldHealth = displayHealth;
        random.setSeed(tickCount * 312871);
        float maxHealth = Math.max((float) entity.getAttributeValue(Attributes.MAX_HEALTH), Math.max(oldHealth, currentHealth));
        int totalAbsorption = Mth.ceil(entity.getAbsorptionAmount());
        int numHealthRows = Mth.ceil((maxHealth + totalAbsorption) / 2.0F / 10.0F);
        int healthRowHeight = Math.max(10 - (numHealthRows - 2), 3);
        // The HUD places the hearts' bottom row first and the armor above the top row.
        int yLineBase = top + 10 + (numHealthRows - 1) * healthRowHeight;
        int heartOffsetIndex = -1;
        if (entity.hasEffect(MobEffects.REGENERATION)) {
            heartOffsetIndex = tickCount % Mth.ceil(maxHealth + 5.0F);
        }
        extractArmor(graphics, entity, xLeft, top);
        extractHearts(
                graphics,
                entity,
                xLeft,
                yLineBase,
                healthRowHeight,
                heartOffsetIndex,
                maxHealth,
                currentHealth,
                oldHealth,
                totalAbsorption,
                blink);
    }

    /** As the HUD's, except that no armor still shows the row, empty: the screen keeps its layout. */
    private static void extractArmor(GuiGraphicsExtractor graphics, LivingEntity entity, int xLeft, int yLineArmor) {
        int armor = entity.getArmorValue();
        for (int i = 0; i < 10; i++) {
            int xo = xLeft + i * 8;
            if (i * 2 + 1 < armor) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_FULL, xo, yLineArmor, 9, 9);
            }
            if (i * 2 + 1 == armor) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_HALF, xo, yLineArmor, 9, 9);
            }
            if (i * 2 + 1 > armor) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_EMPTY, xo, yLineArmor, 9, 9);
            }
        }
    }

    private void extractHearts(
            GuiGraphicsExtractor graphics,
            LivingEntity entity,
            int xLeft,
            int yLineBase,
            int healthRowHeight,
            int heartOffsetIndex,
            float maxHealth,
            int currentHealth,
            int oldHealth,
            int absorption,
            boolean blink) {
        HeartType type = HeartType.of(entity);
        boolean isHardcore = entity.level().getLevelData().isHardcore();
        int healthContainerCount = Mth.ceil(maxHealth / 2.0);
        int absorptionContainerCount = Mth.ceil(absorption / 2.0);
        int maxHealthHalvesCount = healthContainerCount * 2;
        for (int containerIndex = healthContainerCount + absorptionContainerCount - 1; containerIndex >= 0; containerIndex--) {
            int row = containerIndex / 10;
            int column = containerIndex % 10;
            int xo = xLeft + column * 8;
            int yo = yLineBase - row * healthRowHeight;
            if (currentHealth + absorption <= 4) {
                yo += random.nextInt(2);
            }
            if (containerIndex < healthContainerCount && containerIndex == heartOffsetIndex) {
                yo -= 2;
            }
            extractHeart(graphics, HeartType.CONTAINER, xo, yo, isHardcore, blink, false);
            int halves = containerIndex * 2;
            boolean isAbsorptionHeart = containerIndex >= healthContainerCount;
            if (isAbsorptionHeart) {
                int absorptionHalves = halves - maxHealthHalvesCount;
                if (absorptionHalves < absorption) {
                    boolean halfHeart = absorptionHalves + 1 == absorption;
                    extractHeart(
                            graphics,
                            type == HeartType.WITHERED ? type : HeartType.ABSORBING,
                            xo,
                            yo,
                            isHardcore,
                            false,
                            halfHeart);
                }
            }
            if (blink && halves < oldHealth) {
                boolean halfHeart = halves + 1 == oldHealth;
                extractHeart(graphics, type, xo, yo, isHardcore, true, halfHeart);
            }
            if (halves < currentHealth) {
                boolean halfHeart = halves + 1 == currentHealth;
                extractHeart(graphics, type, xo, yo, isHardcore, false, halfHeart);
            }
        }
    }

    private static void extractHeart(
            GuiGraphicsExtractor graphics, HeartType type, int xo, int yo, boolean isHardcore, boolean blinks, boolean half) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, type.sprite(isHardcore, half, blinks), xo, yo, 9, 9);
    }

    /** Vanilla's {@code Hud.HeartType}, private there: the same sprites, under {@code hud/heart/}. */
    private enum HeartType {
        CONTAINER("container"),
        NORMAL(""),
        POISONED("poisoned_"),
        WITHERED("withered_"),
        ABSORBING("absorbing_"),
        FROZEN("frozen_");

        /** Indexed by hardcore * 4 + half * 2 + blink. */
        private final Identifier[] sprites = new Identifier[8];

        HeartType(String prefix) {
            for (int i = 0; i < sprites.length; i++) {
                boolean hardcore = (i & 4) != 0;
                boolean half = (i & 2) != 0;
                boolean blink = (i & 1) != 0;
                // The container has no half sprite: vanilla uses the full one for both.
                String name = prefix.equals("container")
                        ? "container" + (hardcore ? "_hardcore" : "")
                        : prefix + (hardcore ? "hardcore_" : "") + (half ? "half" : "full");
                sprites[i] = Identifier.withDefaultNamespace("hud/heart/" + name + (blink ? "_blinking" : ""));
            }
        }

        Identifier sprite(boolean hardcore, boolean half, boolean blink) {
            return sprites[(hardcore ? 4 : 0) + (half ? 2 : 0) + (blink ? 1 : 0)];
        }

        /** Vanilla's {@code HeartType.forPlayer}, for any living entity. */
        static HeartType of(LivingEntity entity) {
            if (entity.hasEffect(MobEffects.POISON)) {
                return POISONED;
            } else if (entity.hasEffect(MobEffects.WITHER)) {
                return WITHERED;
            } else if (entity.isFullyFrozen()) {
                return FROZEN;
            }
            return NORMAL;
        }
    }
}
