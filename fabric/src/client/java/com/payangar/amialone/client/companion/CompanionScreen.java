package com.payangar.amialone.client.companion;

import com.payangar.amialone.CommonEntrypoint;
import com.payangar.amialone.companion.CompanionGroup;
import com.payangar.amialone.companion.CompanionIdentity;
import com.payangar.amialone.companion.CompanionInventory;
import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.CompanionMenu.Panel;
import com.payangar.amialone.companion.CompanionMenu.Tab;
import com.payangar.amialone.companion.Companions;
import com.payangar.amialone.vendor.gui.Draw;
import com.payangar.amialone.vendor.gui.Theme;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
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
 * debug options (Debug); the player's inventory below. A drawer slides out from under the panel, on
 * the Companion tab only, with two panels behind two handles: its storage (Inventory) and its
 * attributes (Attributes). Scaffolding drawn with the kit's colours and a few sprites of the mod.
 *
 * <p>A slot's position is fixed, so the storage's slots sit where the open drawer shows them, and
 * the client's menu opens the storage only once the drawer is fully out on the Inventory panel:
 * until then vanilla neither draws nor reaches them. The server is told at the click, as for the
 * tabs.
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
    /**
     * The unselected tabs and handles form a stack behind the panel or the drawer, and behind the
     * selected one: each runs this far under what is in front of it, past its border and bevel, so
     * that its covered edge is hidden, as a folder tab behind a page.
     */
    private static final int TUCK = 3;
    /** The unselected tabs' and handles' border and bevel light, dimmer than the selected one's. */
    private static final int BEHIND_BORDER = Theme.BORDER_DISABLED;
    private static final int BEHIND_BEVEL_LIGHT = 0x12FFFFFF;
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
    /** Between the hearts' last row and the owner line: with one row, 24 under the bars' top. */
    private static final int OWNER_GAP = 5;
    private static final Identifier FEMALE = sprite("female");
    private static final Identifier MALE = sprite("male");
    private static final Identifier CHEST = sprite("chest");
    private static final Identifier ATTRIBUTES = sprite("attributes");
    /**
     * The drawer, as high as the panel and hidden under it when closed: a padding around its
     * storage's slot boxes, on the left from the panel's right border, which covers its first
     * columns.
     */
    private static final int DRAWER_WIDTH = CompanionMenu.STORAGE_X - 1 - CompanionMenu.DRAWER_X
            + CompanionMenu.STORAGE_COLUMNS * 18 + PADDING;
    private static final int DRAWER_HEIGHT =
            CompanionInventory.STORAGE_SIZE / CompanionMenu.STORAGE_COLUMNS * 18 + 2 * PADDING;
    private static final int DRAWER_Y = CompanionMenu.DRAWER_Y;
    private static final int DRAWER_OPEN_X = CompanionMenu.DRAWER_X;
    private static final int DRAWER_CLOSED_X = WIDTH - DRAWER_WIDTH;
    private static final int SLIDE_MILLIS = 200;
    /** The drawer's inside, from its top left corner: its storage's slot boxes, or the attributes. */
    private static final int CONTENT_X = CompanionMenu.STORAGE_X - 1 - CompanionMenu.DRAWER_X;
    private static final int CONTENT_Y = CompanionMenu.STORAGE_Y - 1 - CompanionMenu.DRAWER_Y;
    private static final int CONTENT_WIDTH = CompanionMenu.STORAGE_COLUMNS * 18;
    private static final int CONTENT_HEIGHT = DRAWER_HEIGHT - 2 * PADDING;
    /**
     * The handles, one per panel, stacked, neighbours sharing a border, their left border on the
     * drawer's right border. The open panel's handle stands further out, as the chosen tab stands
     * higher, and is drawn last.
     */
    private static final int HANDLE_X = DRAWER_WIDTH - 1;
    private static final int HANDLE_Y = 14;
    private static final int HANDLE_WIDTH = 21;
    private static final int SELECTED_HANDLE_WIDTH = HANDLE_WIDTH + 2;
    private static final int HANDLE_HEIGHT = 26;
    private static final int HANDLE_PITCH = HANDLE_HEIGHT - 1;
    /** From the panel's left edge to the open panel's handle's right edge. */
    private static final int OPEN_WIDTH = DRAWER_OPEN_X + HANDLE_X + SELECTED_HANDLE_WIDTH;
    /** What a narrow screen keeps on each side. */
    private static final int MARGIN = 4;

    /**
     * The drawer's panel left open when a screen last closed, for the session: the next screen
     * opens with the drawer out on it. None when the drawer was closed.
     */
    private static @Nullable Panel remembered;

    private final HealthBars bars = new HealthBars();
    private final AttributesPanel attributes = new AttributesPanel();
    private final Map<Panel, Handle> handles = new EnumMap<>(Panel.class);
    /** The panel the player wants open, none to close the drawer: the drawer slides towards it. */
    private @Nullable Panel wanted;
    /** The panel the drawer shows, which stays while the drawer closes. */
    private Panel shown = Panel.INVENTORY;
    /** Whether the drawer was restored from {@link #remembered}, once per screen. */
    private boolean restored;
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
        handles.clear();
        for (Panel panel : Panel.values()) {
            handles.put(panel, addRenderableWidget(new Handle(panel)));
        }
        if (!restored) {
            restored = true;
            if (remembered != null && menu.tab() == Tab.COMPANION) {
                setDrawer(remembered);
                slide = 1;
            }
        }
        layout();
    }

    /**
     * Places the screen: centred, and moved left as the drawer opens so that the panel and the
     * open drawer fit, as vanilla's recipe book moves the inventory. On a screen too narrow even
     * for that, the panel keeps a margin and the open drawer overlaps it. The handles follow.
     */
    private void layout() {
        int centred = (width - imageWidth) / 2;
        int open = Math.max(MARGIN, Math.min(centred, width - MARGIN - OPEN_WIDTH));
        drawerShift = Math.min(0, width - MARGIN - OPEN_WIDTH - open);
        leftPos = centred + Math.round((open - centred) * eased());
        for (Handle handle : handles.values()) {
            handle.place();
        }
    }

    /** Moves the drawer towards where the player wants it; here the storage opens once it is out. */
    private void slide() {
        long now = Util.getMillis();
        float step = lastFrame < 0 ? 0 : (now - lastFrame) / (float) SLIDE_MILLIS;
        lastFrame = now;
        slide = Math.clamp(slide + (wanted != null ? step : -step), 0f, 1f);
        if (wanted == Panel.INVENTORY && slide == 1 && !menu.storageOpen()) {
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
            x += width - 1;
        }
        return null;
    }

    /** The drawer's left edge, from the panel's. */
    private int drawerX() {
        return DRAWER_CLOSED_X + Math.round((DRAWER_OPEN_X + drawerShift - DRAWER_CLOSED_X) * eased());
    }

    /** Whether the drawer is drawn over the panel: fully out, on a screen too narrow for both. */
    private boolean drawerOnTop() {
        return drawerShift < 0 && wanted != null && slide == 1;
    }

    /** Whether the drawer is out on the Attributes panel. */
    private boolean attributesShown() {
        return menu.tab() == Tab.COMPANION && wanted == Panel.ATTRIBUTES && slide == 1;
    }

    /** Whether the mouse is on the drawer, which exists on the Companion tab only. */
    private boolean overDrawer(double mouseX, double mouseY) {
        return menu.tab() == Tab.COMPANION
                && slide > 0
                && within(mouseX, mouseY, leftPos + drawerX(), topPos + DRAWER_Y, DRAWER_WIDTH, DRAWER_HEIGHT);
    }

    /** Whether the mouse is on a handle's part out of the drawer. */
    private boolean onHandle(double mouseX, double mouseY) {
        return handles.values().stream().anyMatch(handle -> handle.isMouseOver(mouseX, mouseY));
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
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Escape puts the drawer away first when it overlaps the panel, on a narrow screen, as vanilla's
     * recipe book does; otherwise it closes the screen.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && wanted != null && drawerShift < 0) {
            setDrawer(null);
            return true;
        }
        return super.keyPressed(event);
    }

    /** The wheel scrolls the attributes when they outgrow the drawer. */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (attributesShown() && overDrawer(x, y) && attributes.scroll(menu, CONTENT_HEIGHT, scrollY)) {
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    /**
     * The drawer and its handles are part of the screen: a click there throws nothing. The tabs' row
     * beside the tabs is not: a click there throws, as outside the screen.
     */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        boolean besideTabs = mouseY < top + TAB_HEIGHT && tabAt(mouseX, mouseY) == null;
        return (super.hasClickedOutside(mouseX, mouseY, left, top) || besideTabs)
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
     * drawer, on both sides.
     */
    public void selectTab(Tab tab) {
        if (tab != menu.tab() && menu.clickMenuButton(minecraft.player, tab.ordinal())) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, tab.ordinal());
            if (tab != Tab.COMPANION) {
                wanted = null;
                remembered = null;
                slide = 0;
                layout();
            }
        }
    }

    /**
     * A click on the handle of {@code panel}, on the Companion tab: puts the drawer away if it is
     * out on that panel, otherwise opens it on that panel, or swaps its panel at once if it is out.
     */
    public void pressHandle(Panel panel) {
        if (menu.tab() == Tab.COMPANION) {
            setDrawer(wanted == panel ? null : panel);
        }
    }

    /**
     * Opens the drawer on {@code panel}, or closes it, and tells the server, which opens or closes
     * the storage at once. Here the storage opens once the drawer is out, and closes at once.
     */
    private void setDrawer(@Nullable Panel panel) {
        wanted = panel;
        remembered = panel;
        if (panel != null) {
            shown = panel;
        }
        int button = panel != null ? panel.button() : CompanionMenu.CLOSE_DRAWER_BUTTON;
        if (panel != Panel.INVENTORY) {
            menu.clickMenuButton(minecraft.player, button);
        }
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    /** The health and armor bars, with their blink state. */
    public HealthBars bars() {
        return bars;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // The first of a frame's drawing: its slots, labels and handles follow with the same position.
        slide();
        super.extractBackground(graphics, mouseX, mouseY, a);
        boolean drawer = menu.tab() == Tab.COMPANION;
        if (drawer && !drawerOnTop()) {
            extractDrawer(graphics, mouseX, mouseY);
        }
        extractTabs(graphics, mouseX, mouseY, false);
        Draw.box(graphics, leftPos, topPos + PANEL_Y, imageWidth, imageHeight - PANEL_Y, Theme.SURFACE, Theme.BORDER);
        extractTabs(graphics, mouseX, mouseY, true);
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
     * The drawer with the shown panel, which slides with it: the storage's slot boxes, or the
     * attributes; and its handles: the others behind the drawer, from the back, the open panel's in
     * front.
     */
    private void extractDrawer(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = leftPos + drawerX();
        int y = topPos + DRAWER_Y;
        int anchor = wanted == null ? 0 : wanted.ordinal();
        for (int i : fromTheBack(handles.size(), anchor)) {
            extractHandle(graphics, handles.get(Panel.values()[i]), anchor, mouseX, mouseY);
        }
        if (wanted == null) {
            // None selected: the first is the front of the stack.
            extractHandle(graphics, handles.get(Panel.values()[anchor]), anchor, mouseX, mouseY);
        }
        Draw.box(graphics, x, y, DRAWER_WIDTH, DRAWER_HEIGHT, Theme.SURFACE, Theme.BORDER);
        int contentX = x + CONTENT_X;
        int contentY = y + CONTENT_Y;
        if (shown == Panel.INVENTORY) {
            for (int i = 0; i < CompanionInventory.STORAGE_SIZE; i++) {
                int column = i % CompanionMenu.STORAGE_COLUMNS;
                int row = i / CompanionMenu.STORAGE_COLUMNS;
                sunken(graphics, contentX + column * 18, contentY + row * 18, 18, 18);
            }
        } else {
            boolean hoverable = attributesShown() && menu.getCarried().isEmpty();
            attributes.extract(
                    graphics, font, menu, contentX, contentY, CONTENT_WIDTH, CONTENT_HEIGHT, mouseX, mouseY, hoverable);
        }
        if (wanted != null) {
            extractHandle(graphics, handles.get(wanted), anchor, mouseX, mouseY);
        }
    }

    /**
     * A handle: its panel's icon, and an arrow towards where a click moves the drawer. The open
     * panel's handle is in the drawer's colour and open on its side, as the chosen tab is on the
     * panel's; the others are behind the drawer and each other ({@link #fromTheBack}), darker
     * and dimmer, lit under the mouse or the keyboard's focus. While the drawer is closed every handle points out; once open, the open
     * panel's points back and the others have no arrow.
     */
    private void extractHandle(GuiGraphicsExtractor graphics, Handle handle, int anchor, int mouseX, int mouseY) {
        boolean selected = handle.selected();
        int handleX = handle.getX() - 1;
        int handleY = handle.getY();
        int colour;
        if (selected) {
            Draw.box(graphics, handleX, handleY, SELECTED_HANDLE_WIDTH, HANDLE_HEIGHT, Theme.SURFACE, Theme.BORDER);
            // Neither the drawer's border and bevel nor the handle's left ones between them, the
            // handle's top and bottom bevels running to the opening.
            Draw.fill(graphics, handleX - 1, handleY + 1, 1, HANDLE_HEIGHT - 2, Theme.SURFACE);
            Draw.fill(graphics, handleX, handleY + 1, 2, HANDLE_HEIGHT - 2, Theme.SURFACE);
            Draw.fill(graphics, handleX, handleY + 1, 2, 1, Theme.BEVEL_LIGHT);
            Draw.fill(graphics, handleX, handleY + HANDLE_HEIGHT - 2, 2, 1, Theme.BEVEL_DARK);
            colour = Theme.HEADING;
        } else {
            // Under the drawer, and under its neighbour nearer the anchor, which are drawn over it.
            int i = handle.panel.ordinal();
            int up = i > anchor ? TUCK : 0;
            int down = i < anchor ? TUCK : 0;
            int face = handle.lit(mouseX, mouseY) ? Theme.ROW_HOVER : Theme.BAND;
            behind(graphics, handleX - TUCK, handleY - up, HANDLE_WIDTH + TUCK, HANDLE_HEIGHT + up + down, face);
            colour = handle.lit(mouseX, mouseY) ? Theme.TEXT : Theme.TEXT_DIM;
        }
        // The icon and the arrow centred between the borders, one pixel further out when selected.
        int inset = selected ? 1 : 0;
        Identifier icon = handle.panel == Panel.INVENTORY ? CHEST : ATTRIBUTES;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, handleX + 5 + inset, handleY + 4, 11, 10, colour);
        if (wanted == null || selected) {
            // A 3 by 5 arrow, pointing right to open the drawer and left to put it away.
            for (int column = 0; column < 3; column++) {
                int height = 5 - 2 * (selected ? 2 - column : column);
                Draw.fill(graphics, handleX + 9 + inset + column, handleY + 17 + (5 - height) / 2, 1, height, colour);
            }
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
     * The tabs above the panel, side by side, neighbours meeting on a border: the others, drawn
     * before the panel from the back ({@link #fromTheBack}), darker and dimmer, behind it and each
     * other; the chosen one, drawn after, a box in the panel's colour, open at the bottom, so it
     * joins the panel.
     */
    private void extractTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean chosen) {
        List<Tab> tabs = menu.tabs();
        int anchor = tabs.indexOf(menu.tab());
        int[] xs = new int[tabs.size()];
        int x = leftPos + TABS_X;
        for (int i = 0; i < tabs.size(); i++) {
            xs[i] = x;
            x += tabWidth(tabs.get(i)) - 1;
        }
        if (chosen) {
            extractTab(graphics, menu.tab(), xs[anchor], 0, false);
            return;
        }
        Tab hovered = tabAt(mouseX, mouseY);
        for (int i : fromTheBack(tabs.size(), anchor)) {
            extractTab(graphics, tabs.get(i), xs[i], i - anchor, tabs.get(i) == hovered);
        }
    }

    /**
     * A tab, {@code fromChosen} places after the chosen one (before it when negative); the chosen
     * one at 0.
     */
    private void extractTab(GuiGraphicsExtractor graphics, Tab tab, int x, int fromChosen, boolean hovered) {
        int width = tabWidth(tab);
        boolean chosen = fromChosen == 0;
        if (chosen) {
            Draw.box(graphics, x, topPos, width, TAB_HEIGHT + 1, Theme.SURFACE, Theme.BORDER);
            Draw.fill(graphics, x + 1, topPos + TAB_HEIGHT, width - 2, 1, Theme.SURFACE);
        } else {
            // Under the panel, and under its neighbour nearer the chosen tab, which are drawn over it.
            int left = fromChosen > 0 ? TUCK : 0;
            int right = fromChosen < 0 ? TUCK : 0;
            int face = hovered ? Theme.ROW_HOVER : Theme.BAND;
            behind(graphics, x - left, topPos + 2, width + left + right, TAB_HEIGHT - 2 + TUCK, face);
        }
        int colour = chosen ? Theme.HEADING : Theme.TEXT_DIM;
        int textY = topPos + (chosen ? 0 : 2) + (TAB_HEIGHT - (chosen ? 0 : 2) - font.lineHeight) / 2 + 1;
        Draw.text(graphics, font, title(tab), x + TAB_MARGIN, textY, colour);
    }

    /**
     * The order to draw a row of {@code count} tabs or handles in, from the back, without the one at
     * {@code anchor}, the selected one, or the first when none is: the farthest from it first, so
     * that of two neighbours the one nearer the anchor covers the other's edge.
     */
    private static List<Integer> fromTheBack(int count, int anchor) {
        return IntStream.range(0, count)
                .filter(i -> i != anchor)
                .boxed()
                .sorted(Comparator.comparingInt((Integer i) -> Math.abs(i - anchor)).reversed())
                .toList();
    }

    /**
     * An unselected tab or handle: the kit's raised box, darker and dimmer than the selected one's,
     * for a place behind it.
     */
    private static void behind(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int face) {
        Draw.fill(graphics, x, y, width, height, face);
        Draw.bevel(graphics, x + 1, y + 1, width - 2, height - 2, BEHIND_BEVEL_LIGHT, Theme.BEVEL_DARK);
        Draw.outline(graphics, x, y, width, height, BEHIND_BORDER);
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
        // The tab's lines follow the hearts, whose rows grow with the health and the absorption.
        int linesY = bars.extract(graphics, menu.companion(), x, topPos + BARS_Y, minecraft.gui.hud.getGuiTicks()) + OWNER_GAP;
        int ownerColour = menu.tab() == Tab.DEBUG ? Theme.TEXT : Theme.TEXT_DIM;
        Draw.text(graphics, font, owner(), x, linesY, INFO_WIDTH, ownerColour);
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

    /**
     * A handle as a button, for the mouse, the keyboard and the narrator, over the handle's part
     * out of the drawer; the screen draws it with the drawer. Its message is its tooltip: its
     * panel's name, or, once selected, the action that puts its panel away.
     */
    private final class Handle extends AbstractButton {
        private final Panel panel;

        Handle(Panel panel) {
            super(0, 0, HANDLE_WIDTH - 1, HANDLE_HEIGHT, Component.empty());
            this.panel = panel;
        }

        /** Whether the drawer is out, or going out, on its panel. */
        boolean selected() {
            return wanted == panel;
        }

        /** Whether it is lit: under the mouse, or focused from the keyboard. */
        boolean lit(int mouseX, int mouseY) {
            return isMouseOver(mouseX, mouseY) || isFocused() && minecraft.getLastInputType().isKeyboard();
        }

        /** Follows the drawer, the tab and the open panel. */
        void place() {
            visible = menu.tab() == Tab.COMPANION;
            setRectangle(
                    (selected() ? SELECTED_HANDLE_WIDTH : HANDLE_WIDTH) - 1,
                    HANDLE_HEIGHT,
                    leftPos + drawerX() + HANDLE_X + 1,
                    topPos + DRAWER_Y + HANDLE_Y + panel.ordinal() * HANDLE_PITCH);
            String name = panel.name().toLowerCase(Locale.ROOT);
            Component message = Component.translatable(KEY + name + (selected() ? ".close" : ".open"));
            if (!message.equals(getMessage())) {
                setMessage(message);
                // Without narration: the message narrates it already.
                setTooltip(Tooltip.create(message, null));
            }
        }

        @Override
        public void onPress(InputWithModifiers input) {
            pressHandle(panel);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {}

        /** No tooltip over an item on the cursor, as over the slots. */
        @Override
        protected void extractTooltipForNextRenderPass(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (menu.getCarried().isEmpty()) {
                super.extractTooltipForNextRenderPass(graphics, mouseX, mouseY);
            }
        }

        /** A click leaves no focus behind, which would keep it lit. */
        @Override
        public boolean shouldTakeFocusAfterInteraction() {
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
            if (selected()) {
                output.add(NarratedElementType.HINT, Component.translatable(KEY + "drawer.selected"));
            }
        }
    }
}
