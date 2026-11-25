package com.payangar.amialone.client.companion;

import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.vendor.gui.Draw;
import com.payangar.amialone.vendor.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
        extractCompanionFollowsMouse(
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

    /**
     * Vanilla's {@code InventoryScreen.extractEntityInInventoryFollowsMouse}, without the name tag:
     * a player always shows their name, and the view would cut it. Vanilla's has no option for it.
     */
    private static void extractCompanionFollowsMouse(
            GuiGraphicsExtractor graphics,
            int x0,
            int y0,
            int x1,
            int y1,
            int size,
            float offsetY,
            float mouseX,
            float mouseY,
            LivingEntity entity) {
        float xAngle = (float) Math.atan(((x0 + x1) / 2.0F - mouseX) / 40.0F);
        float yAngle = (float) Math.atan(((y0 + y1) / 2.0F - mouseY) / 40.0F);
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf xRotation = new Quaternionf().rotateX(yAngle * 20.0F * (float) (Math.PI / 180.0));
        rotation.mul(xRotation);
        EntityRenderState state =
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, 1.0F);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        state.nameTag = null;
        state.scoreText = null;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180.0F + xAngle * 20.0F;
            living.yRot = xAngle * 20.0F;
            living.xRot = living.pose != Pose.FALL_FLYING ? -yAngle * 20.0F : 0.0F;
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1.0F;
        }
        Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + offsetY, 0.0F);
        graphics.entity(state, size, translation, rotation, xRotation, x0, y0, x1, y1);
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
