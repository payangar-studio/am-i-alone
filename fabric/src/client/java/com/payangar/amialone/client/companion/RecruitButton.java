package com.payangar.amialone.client.companion;

import com.payangar.amialone.vendor.gui.Draw;
import com.payangar.amialone.vendor.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Recruit, in the mod's colours: the screen's main action, filled with the accent, its label on
 * the left and its price on the right, an emerald with its count as a villager's offer shows it. A
 * price lowered from {@code basePrice} shows the base struck through in red and the price beside
 * it, as a villager's discounted offer; greyed while the player cannot pay.
 */
public class RecruitButton extends AbstractButton {
    /** Room for the emerald and its count, whose shadow ends on the icon's 18th row. */
    public static final int HEIGHT = 20;
    private static final int MARGIN = 6;
    /** Where an item's count ends, from its icon's left edge: one pixel past the icon. */
    private static final int COUNT_END = 17;
    /** Where a villager's offer draws the lowered count, from the icon. */
    private static final int LOWERED_OFFSET = 14;
    private static final ItemStack EMERALD = new ItemStack(Items.EMERALD);
    /** The red stroke of a villager's discounted offer. */
    private static final Identifier STRIKETHROUGH = Identifier.withDefaultNamespace("container/villager/discount_strikethrough");

    private final Component label;
    private final int price;
    private final int basePrice;
    private final Runnable onPress;

    public RecruitButton(int width, Component label, int price, int basePrice, Runnable onPress) {
        super(0, 0, width, HEIGHT, Component.translatable("container.am_i_alone.companion.recruit.narration", price));
        this.label = label;
        this.price = price;
        this.basePrice = basePrice;
        this.onPress = onPress;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int face = !active ? Theme.SURFACE_DISABLED : isHoveredOrFocused() ? Theme.ACCENT_HOVER : Theme.ACCENT;
        int border = !active ? Theme.BORDER_DISABLED : isHoveredOrFocused() ? Theme.BORDER_HOVER : Theme.BORDER;
        Draw.box(graphics, x, y, width, height, face, border);
        int textY = y + (height - font.lineHeight) / 2 + 1;
        int textColour = active ? Theme.HEADING : Theme.TEXT_DISABLED;
        Draw.text(graphics, font, label, x + MARGIN, textY, textColour);

        // The price on the right, drawn as a villager's offer does (MerchantScreen): the emerald with
        // its count over its bottom right corner; when lowered, that count is the base, struck
        // through, and the price follows, one count further.
        boolean lowered = basePrice != price;
        int iconX = x + width - MARGIN - COUNT_END - (lowered ? LOWERED_OFFSET : 0);
        int iconY = y + 1;
        graphics.item(EMERALD, iconX, iconY);
        graphics.itemDecorations(font, EMERALD, iconX, iconY, String.valueOf(basePrice));
        if (lowered) {
            graphics.itemDecorations(font, EMERALD, iconX + LOWERED_OFFSET, iconY, String.valueOf(price));
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, STRIKETHROUGH, iconX + 7, iconY + 12, 9, 2);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
