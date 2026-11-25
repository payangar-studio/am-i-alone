package com.payangar.amialone.client.companion;

import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.vendor.gui.Draw;
import com.payangar.amialone.vendor.gui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * A companion's inventory screen: the companion in 3D following the mouse, its armor and designated
 * weapons, its storage, and the player's inventory below. Scaffolding drawn with the kit's colours,
 * with no texture of the mod.
 */
public class CompanionScreen extends AbstractContainerScreen<CompanionMenu> {
    private static final int WIDTH = 272;
    private static final int HEIGHT = 190;
    /** The box of the companion's 3D view, between the armor and the weapons. */
    private static final int VIEW_X = 26;
    private static final int VIEW_Y = CompanionMenu.COMPANION_Y - 1;
    private static final int VIEW_WIDTH = 52;
    private static final int VIEW_HEIGHT = 72;

    public CompanionScreen(CompanionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        inventoryLabelX = CompanionMenu.PLAYER_X;
        inventoryLabelY = CompanionMenu.PLAYER_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        Draw.box(graphics, leftPos, topPos, imageWidth, imageHeight, Theme.SURFACE, Theme.BORDER);
        for (Slot slot : menu.slots) {
            sunken(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
        }
        int viewX = leftPos + VIEW_X;
        int viewY = topPos + VIEW_Y;
        sunken(graphics, viewX, viewY, VIEW_WIDTH, VIEW_HEIGHT);
        InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics,
                viewX + 1,
                viewY + 1,
                viewX + VIEW_WIDTH - 1,
                viewY + VIEW_HEIGHT - 1,
                30,
                0.0625F,
                mouseX,
                mouseY,
                menu.companion());
    }

    private static void sunken(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        Draw.fill(graphics, x, y, width, height, Theme.BACKGROUND);
        Draw.bevel(graphics, x, y, width, height, Theme.BEVEL_DARK, Theme.BEVEL_LIGHT);
    }

    /** The kit's highlight under the hovered slot's item, instead of vanilla's sprite. */
    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (slot == hoveredSlot) {
            Draw.fill(graphics, slot.x, slot.y, 16, 16, Theme.SURFACE_HOVER);
        }
        super.extractSlot(graphics, slot, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Draw.text(graphics, font, title, titleLabelX, titleLabelY, imageWidth - 2 * titleLabelX, Theme.TEXT);
        Draw.text(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Theme.TEXT_DIM);
    }
}
