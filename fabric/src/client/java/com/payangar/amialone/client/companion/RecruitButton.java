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
 * the left and its price on the right, an emerald and a number. A price lowered from {@code
 * basePrice} shows the base struck through in red before it, as a villager's discounted offer; greyed
 * while the player cannot pay.
 */
public class RecruitButton extends AbstractButton {
    public static final int HEIGHT = 18;
    private static final int MARGIN = 6;
    private static final int ICON_GAP = 1;
    private static final int STRUCK_GAP = 3;
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

        // The price from the right: the number, the base struck through when lowered, the emerald.
        String count = String.valueOf(price);
        int right = x + width - MARGIN;
        int countX = right - font.width(count);
        graphics.text(font, count, countX, textY, textColour, false);
        int iconRight = countX - ICON_GAP;
        if (basePrice != price) {
            String base = String.valueOf(basePrice);
            int baseX = countX - STRUCK_GAP - font.width(base);
            int baseColour = active ? Theme.TEXT : Theme.TEXT_DISABLED;
            graphics.text(font, base, baseX, textY, baseColour, false);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, STRIKETHROUGH, baseX - 1, textY + 3, font.width(base) + 1, 2);
            iconRight = baseX - ICON_GAP;
        }
        graphics.item(EMERALD, iconRight - 16, y + (height - 16) / 2);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
