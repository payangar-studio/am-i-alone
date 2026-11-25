package com.payangar.amialone.client.companion;

import com.payangar.amialone.CommonEntrypoint;
import com.payangar.amialone.companion.CompanionGroup;
import com.payangar.amialone.companion.CompanionIdentity;
import com.payangar.amialone.companion.CompanionInventory;
import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.CompanionMenu.Tab;
import com.payangar.amialone.companion.Companions;
import com.payangar.amialone.vendor.gui.Draw;
import com.payangar.amialone.vendor.gui.Theme;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A companion's screen: the companion in 3D following the mouse, its armor and designated weapons;
 * right of them its name, its armor and health bars and the tab's lines: its owner (Companion), the
 * debug options (Debug); the player's inventory below. Its storage slides out from under the panel
 * in a drawer, on the Companion tab only. Scaffolding drawn with the kit's colours and a few sprites
 * of the mod.
 *
 * <p>A slot's position is fixed, so the storage's slots sit where the open drawer shows them, and
 * the client's menu opens the storage only once the drawer is fully out: until then vanilla neither
 * draws nor reaches them. The server is told at the click, as for the tabs.
 */
public class CompanionScreen extends AbstractContainerScreen<CompanionMenu> {
    private static final String KEY = "container.am_i_alone.companion.";
    private static final int PANEL_Y = CompanionMenu.PANEL_Y;
    private static final int PADDING = CompanionMenu.PADDING;
    private static final int WIDTH = CompanionMenu.PANEL_WIDTH;
    /** Down to the hotbar's boxes, then the padding and the border. */
    private static final int HEIGHT = CompanionMenu.HOTBAR_Y - 1 + 18 + PADDING;
    /** The tabs stand above the panel, as folder tabs; the chosen one opens into it. */
    private static final int TABS_X = 6;
    private static final int TAB_HEIGHT = PANEL_Y;
    private static final int TAB_MARGIN = 8;
    /** The box of the companion's 3D view, between the armor and the weapons. */
    private static final int VIEW_X = 26;
    private static final int VIEW_Y = CompanionMenu.COMPANION_Y - 1;
    private static final int VIEW_WIDTH = 52;
    private static final int VIEW_HEIGHT = 72;
    /**
     * The column right of the weapons, a padding away from their boxes: its name, its bars, the
     * tab's lines. Its width, which sizes the panel's, holds a long name with its symbol, "Rowena
     * Brightwater" (98 pixels, then 4 and up to 8): 113, odd so that the player's inventory is
     * centred to the pixel. A longer name ends with an ellipsis.
     */
    private static final int INFO_X = CompanionMenu.WEAPONS_X - 1 + 18 + PADDING;
    private static final int INFO_WIDTH = WIDTH - PADDING - INFO_X;
    private static final int NAME_Y = PANEL_Y + 8;
    private static final int BARS_Y = NAME_Y + 12;
    private static final int OWNER_Y = BARS_Y + 24;
    private static final Identifier FEMALE = sprite("female");
    private static final Identifier MALE = sprite("male");
    private static final Identifier CHEST = sprite("chest");
    /**
     * The storage's drawer, as high as the panel and hidden under it when closed: a padding around
     * its slots' boxes, on the left from the panel's right border, which covers its first columns.
     */
    private static final int DRAWER_WIDTH = CompanionMenu.STORAGE_X - 1 - CompanionMenu.DRAWER_X
            + CompanionMenu.STORAGE_COLUMNS * 18 + PADDING;
    private static final int DRAWER_HEIGHT =
            CompanionInventory.STORAGE_SIZE / CompanionMenu.STORAGE_COLUMNS * 18 + 2 * PADDING;
    private static final int DRAWER_Y = CompanionMenu.DRAWER_Y;
    private static final int DRAWER_OPEN_X = CompanionMenu.DRAWER_X;
    private static final int DRAWER_CLOSED_X = WIDTH - DRAWER_WIDTH;
    private static final int SLIDE_MILLIS = 200;
    /** The handle, its left border on the drawer's right border, which it opens. */
    private static final int HANDLE_X = DRAWER_WIDTH - 1;
    private static final int HANDLE_Y = 14;
    private static final int HANDLE_WIDTH = 21;
    private static final int HANDLE_HEIGHT = 26;
    /** From the panel's left edge to the open handle's right edge. */
    private static final int OPEN_WIDTH = DRAWER_OPEN_X + HANDLE_X + HANDLE_WIDTH;
    /** What a narrow screen keeps on each side. */
    private static final int MARGIN = 4;

    private final HealthBars bars = new HealthBars();
    /** Whether the player wants the storage open: the drawer slides towards it. */
    private boolean storageWanted;
    /** How far the drawer is out, from 0 (closed) to 1 (open). */
    private float slide;
    private long lastFrame = -1;
    /**
     * How far left of its place the open drawer stands: 0 unless the screen is too narrow for the
     * panel and the drawer side by side, and the open drawer then overlaps the panel.
     */
    private int drawerShift;

    public CompanionScreen(CompanionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        inventoryLabelX = CompanionMenu.PLAYER_X;
        inventoryLabelY = CompanionMenu.PLAYER_Y - 12;
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(CommonEntrypoint.MOD_ID, "companion/" + name);
    }

    private static Component title(Tab tab) {
        return Component.translatable(KEY + "tab." + tab.name().toLowerCase(Locale.ROOT));
    }

    @Override
    protected void init() {
        super.init();
        layout();
    }

    /**
     * Places the screen: centred, and moved left as the drawer opens so that the panel and the
     * open drawer fit, as vanilla's recipe book moves the inventory. On a screen too narrow even
     * for that, the panel keeps a margin and the open drawer overlaps it.
     */
    private void layout() {
        int centred = (width - imageWidth) / 2;
        int open = Math.max(MARGIN, Math.min(centred, width - MARGIN - OPEN_WIDTH));
        drawerShift = Math.min(0, width - MARGIN - OPEN_WIDTH - open);
        leftPos = centred + Math.round((open - centred) * eased());
    }

    /** Moves the drawer towards where the player wants it; here the storage opens once it is out. */
    private void slide() {
        long now = Util.getMillis();
        float step = lastFrame < 0 ? 0 : (now - lastFrame) / (float) SLIDE_MILLIS;
        lastFrame = now;
        slide = Math.clamp(slide + (storageWanted ? step : -step), 0f, 1f);
        if (storageWanted && slide == 1 && !menu.storageOpen()) {
            menu.clickMenuButton(minecraft.player, CompanionMenu.OPEN_STORAGE_BUTTON);
        }
        layout();
    }

    private float eased() {
        return slide * slide * (3 - 2 * slide);
    }

    private int tabWidth(Tab tab) {
        return font.width(title(tab)) + 2 * TAB_MARGIN;
    }

    /** The tab under the mouse, if any. */
    private @Nullable Tab tabAt(double mouseX, double mouseY) {
        if (mouseY < topPos || mouseY >= topPos + TAB_HEIGHT) {
            return null;
        }
        int x = leftPos + TABS_X;
        for (Tab tab : menu.tabs()) {
            int width = tabWidth(tab);
            if (mouseX >= x && mouseX < x + width) {
                return tab;
            }
            x += width + 1;
        }
        return null;
    }

    /** The drawer's left edge, from the panel's. */
    private int drawerX() {
        return DRAWER_CLOSED_X + Math.round((DRAWER_OPEN_X + drawerShift - DRAWER_CLOSED_X) * eased());
    }

    /** Whether the drawer is drawn over the panel: open, on a screen too narrow for both. */
    private boolean drawerOnTop() {
        return drawerShift < 0 && menu.storageOpen();
    }

    /** Whether the mouse is on the drawer, which exists on the Companion tab only. */
    private boolean overDrawer(double mouseX, double mouseY) {
        return menu.tab() == Tab.COMPANION
                && slide > 0
                && within(mouseX, mouseY, leftPos + drawerX(), topPos + DRAWER_Y, DRAWER_WIDTH, DRAWER_HEIGHT);
    }

    /** Whether the mouse is on the handle's part out of the drawer. */
    private boolean onHandle(double mouseX, double mouseY) {
        return menu.tab() == Tab.COMPANION
                && within(
                        mouseX,
                        mouseY,
                        leftPos + drawerX() + HANDLE_X + 1,
                        topPos + DRAWER_Y + HANDLE_Y,
                        HANDLE_WIDTH - 1,
                        HANDLE_HEIGHT);
    }

    private static boolean within(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static boolean isStorageSlot(Slot slot) {
        return slot.index >= CompanionMenu.STORAGE_START && slot.index < CompanionMenu.PLAYER_START;
    }

    /**
     * Whether the drawer, over the panel, covers {@code slot}: the drawer spans the panel's whole
     * height under the tabs, so its left edge decides.
     */
    private boolean covered(Slot slot) {
        return drawerOnTop() && slot.x + 17 > drawerX();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Tab tab = tabAt(event.x(), event.y());
        if (tab != null) {
            selectTab(tab);
            return true;
        }
        if (onHandle(event.x(), event.y())) {
            toggleStorage();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** The drawer and its handle are part of the screen: a click there throws nothing. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return super.hasClickedOutside(mouseX, mouseY, left, top)
                && !overDrawer(mouseX, mouseY)
                && !onHandle(mouseX, mouseY);
    }

    /**
     * Vanilla asks with a slot's position. Only the storage's slots sit at the open drawer's place,
     * which moves left on a narrow screen; there the drawer over the panel hides what it covers.
     */
    @Override
    protected boolean isHovering(int left, int top, int width, int height, double mouseX, double mouseY) {
        if (left >= CompanionMenu.STORAGE_X) {
            return super.isHovering(left + drawerShift, top, width, height, mouseX, mouseY);
        }
        return !(drawerOnTop() && overDrawer(mouseX, mouseY))
                && super.isHovering(left, top, width, height, mouseX, mouseY);
    }

    /**
     * Opens {@code tab} here and tells the server, with vanilla's menu button packet. The menu
     * stays: the item on the cursor and the view do not move. Leaving the Companion tab closes the
     * storage, on both sides.
     */
    public void selectTab(Tab tab) {
        if (tab != menu.tab() && menu.clickMenuButton(minecraft.player, tab.ordinal())) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, tab.ordinal());
            if (tab != Tab.COMPANION) {
                storageWanted = false;
                slide = 0;
                layout();
            }
        }
    }

    /**
     * Opens or closes the storage, on the Companion tab, and tells the server, which opens it at once.
     * Here it opens once the drawer is out, and closes at once.
     */
    public void toggleStorage() {
        if (menu.tab() != Tab.COMPANION) {
            return;
        }
        storageWanted = !storageWanted;
        int button = storageWanted ? CompanionMenu.OPEN_STORAGE_BUTTON : CompanionMenu.CLOSE_STORAGE_BUTTON;
        if (!storageWanted) {
            menu.clickMenuButton(minecraft.player, button);
        }
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    /** The health and armor bars, with their blink state. */
    public HealthBars bars() {
        return bars;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (onHandle(mouseX, mouseY) && menu.getCarried().isEmpty()) {
            Component tooltip = Component.translatable(KEY + (storageWanted ? "inventory.close" : "inventory.open"));
            graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // The first of a frame's drawing: its slots and labels follow with the same position.
        slide();
        super.extractBackground(graphics, mouseX, mouseY, a);
        boolean drawer = menu.tab() == Tab.COMPANION;
        if (drawer && !drawerOnTop()) {
            extractDrawer(graphics, mouseX, mouseY);
        }
        Draw.box(graphics, leftPos, topPos + PANEL_Y, imageWidth, imageHeight - PANEL_Y, Theme.SURFACE, Theme.BORDER);
        extractTabs(graphics, mouseX, mouseY);
        for (Slot slot : menu.slots) {
            if (slot.isActive() && !isStorageSlot(slot)) {
                sunken(graphics, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
            }
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
        extractInfo(graphics);
        if (drawer && drawerOnTop()) {
            extractDrawer(graphics, mouseX, mouseY);
        }
    }

    /**
     * The drawer with its slots' boxes, which slide with it, and its handle: a chest and an arrow
     * towards where a click moves the drawer, lit while the storage is open or under the mouse.
     */
    private void extractDrawer(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = leftPos + drawerX();
        int y = topPos + DRAWER_Y;
        Draw.box(graphics, x, y, DRAWER_WIDTH, DRAWER_HEIGHT, Theme.SURFACE, Theme.BORDER);
        int slotsX = x + CompanionMenu.STORAGE_X - CompanionMenu.DRAWER_X - 1;
        int slotsY = y + CompanionMenu.STORAGE_Y - CompanionMenu.DRAWER_Y - 1;
        for (int i = 0; i < CompanionInventory.STORAGE_SIZE; i++) {
            int column = i % CompanionMenu.STORAGE_COLUMNS;
            int row = i / CompanionMenu.STORAGE_COLUMNS;
            sunken(graphics, slotsX + column * 18, slotsY + row * 18, 18, 18);
        }
        boolean hovered = onHandle(mouseX, mouseY);
        int face = hovered ? Theme.SURFACE_HOVER : Theme.SURFACE;
        int handleX = x + HANDLE_X;
        int handleY = y + HANDLE_Y;
        Draw.box(graphics, handleX, handleY, HANDLE_WIDTH, HANDLE_HEIGHT, face, Theme.BORDER);
        // Open on the drawer's side, as the chosen tab is on the panel's: neither the drawer's border
        // and bevel nor the handle's left ones between them, the handle's top and bottom bevels
        // running to the opening.
        Draw.fill(graphics, handleX - 1, handleY + 1, 1, HANDLE_HEIGHT - 2, Theme.SURFACE);
        Draw.fill(graphics, handleX, handleY + 1, 2, HANDLE_HEIGHT - 2, face);
        Draw.fill(graphics, handleX, handleY + 1, 2, 1, Theme.BEVEL_LIGHT);
        Draw.fill(graphics, handleX, handleY + HANDLE_HEIGHT - 2, 2, 1, Theme.BEVEL_DARK);
        int colour = storageWanted ? Theme.HEADING : hovered ? Theme.TEXT : Theme.TEXT_DIM;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CHEST, handleX + 5, handleY + 4, 11, 10, colour);
        // A 3 by 5 arrow, pointing right to open the storage and left to put it away.
        for (int column = 0; column < 3; column++) {
            int height = 5 - 2 * (storageWanted ? 2 - column : column);
            Draw.fill(graphics, handleX + 9 + column, handleY + 17 + (5 - height) / 2, 1, height, colour);
        }
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

    /**
     * The tabs above the panel: each a box of the kit's colours, the chosen one in the panel's
     * colour and open at the bottom, so it joins the panel.
     */
    private void extractTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Tab hovered = tabAt(mouseX, mouseY);
        int x = leftPos + TABS_X;
        for (Tab tab : menu.tabs()) {
            int width = tabWidth(tab);
            boolean chosen = tab == menu.tab();
            if (chosen) {
                Draw.box(graphics, x, topPos, width, TAB_HEIGHT + 1, Theme.SURFACE, Theme.BORDER);
                Draw.fill(graphics, x + 1, topPos + TAB_HEIGHT, width - 2, 1, Theme.SURFACE);
            } else {
                int face = tab == hovered ? Theme.SURFACE_HOVER : Theme.BAND;
                Draw.box(graphics, x, topPos + 2, width, TAB_HEIGHT - 2, face, Theme.BORDER);
            }
            int colour = chosen ? Theme.HEADING : Theme.TEXT_DIM;
            int textY = topPos + (chosen ? 0 : 2) + (TAB_HEIGHT - (chosen ? 0 : 2) - font.lineHeight) / 2 + 1;
            Draw.text(graphics, font, title(tab), x + TAB_MARGIN, textY, colour);
            x += width + 1;
        }
    }

    private static void sunken(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        Draw.fill(graphics, x, y, width, height, Theme.BACKGROUND);
        Draw.bevel(graphics, x, y, width, height, Theme.BEVEL_DARK, Theme.BEVEL_LIGHT);
    }

    /**
     * The kit's highlight under the hovered slot's item, instead of vanilla's sprite; the storage's
     * slots where the drawer stands, and nothing the drawer covers.
     */
    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        boolean storage = isStorageSlot(slot);
        if (!storage && covered(slot)) {
            return;
        }
        graphics.pose().pushMatrix();
        if (storage) {
            graphics.pose().translate(drawerShift, 0);
        }
        if (slot == hoveredSlot) {
            Draw.fill(graphics, slot.x, slot.y, 16, 16, Theme.SURFACE_HOVER);
        }
        super.extractSlot(graphics, slot, mouseX, mouseY);
        graphics.pose().popMatrix();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Draw.text(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Theme.TEXT_DIM);
    }

    /**
     * The column right of the weapons: its name as the heading, cut to the column, with the symbol
     * of its group; its bars; the tab's lines. Drawn with the panel, so a drawer over the panel
     * covers it.
     */
    private void extractInfo(GuiGraphicsExtractor graphics) {
        int x = leftPos + INFO_X;
        Identifier symbol = symbol();
        int symbolWidth = symbol == FEMALE ? 5 : 8;
        FormattedCharSequence name = Draw.fit(font, title, INFO_WIDTH - (symbol != null ? symbolWidth + 4 : 0));
        graphics.text(font, name, x, topPos + NAME_Y, Theme.HEADING, false);
        if (symbol != null) {
            int symbolX = x + font.width(name) + 4;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, symbol, symbolX, topPos + NAME_Y - 1, symbolWidth, 9);
        }
        bars.extract(graphics, menu.companion(), x, topPos + BARS_Y, minecraft.gui.hud.getGuiTicks());
        int ownerColour = menu.tab() == Tab.DEBUG ? Theme.TEXT : Theme.TEXT_DIM;
        Draw.text(graphics, font, owner(), x, topPos + OWNER_Y, INFO_WIDTH, ownerColour);
    }

    /** The symbol of its group, from its skin: female or male; none without a skin. */
    private @Nullable Identifier symbol() {
        CompanionIdentity identity = menu.companion().getAttached(Companions.IDENTITY);
        if (identity == null) {
            return null;
        }
        return identity.group(menu.companion().registryAccess())
                .map(group -> group == CompanionGroup.WOMAN ? FEMALE : MALE)
                .orElse(null);
    }

    /** Its owner; none until ownership exists. */
    private static Component owner() {
        return Component.translatable(KEY + "owner", Component.translatable(KEY + "owner.none"));
    }
}
