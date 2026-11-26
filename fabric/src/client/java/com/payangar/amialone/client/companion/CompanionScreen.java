package com.payangar.amialone.client.companion;

import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.CompanionMenu.Panel;
import com.payangar.amialone.companion.CompanionMenu.Tab;
import com.payangar.amialone.companion.CompanionOwner;
import com.payangar.amialone.companion.Companions;
import com.payangar.amialone.vendor.gui.Draw;
import com.payangar.amialone.vendor.gui.Theme;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * A companion's screen, for its owner or an operator with the debug stick: its armor and designated
 * weapons in slots, its owner (Companion tab) or the debug options (Debug tab) under its bars, and,
 * on the Companion tab only, a drawer with its storage (Inventory) and its attributes (Attributes).
 *
 * <p>The client's menu opens the storage only once the drawer is fully out on the Inventory panel:
 * until then vanilla neither draws nor reaches its slots. The server is told at the click, as for
 * the tabs.
 */
public class CompanionScreen extends CompanionViewScreen<CompanionMenu> {
    /** The drawer's panel left open when a companion's screen last closed, for the session. */
    private static @Nullable Panel remembered;

    public CompanionScreen(CompanionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected List<Component> tabTitles() {
        return menu.tabs().stream()
                .map(tab -> (Component) Component.translatable(KEY + "tab." + tab.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    @Override
    protected int selectedTab() {
        return menu.tabs().indexOf(menu.tab());
    }

    @Override
    protected void tabClicked(int index) {
        selectTab(menu.tabs().get(index));
    }

    @Override
    protected List<Panel> panels() {
        return List.of(Panel.values());
    }

    /** The drawer belongs to the Companion tab. */
    @Override
    protected boolean drawerAvailable() {
        return menu.tab() == Tab.COMPANION;
    }

    @Override
    protected @Nullable Panel remembered() {
        return remembered;
    }

    @Override
    protected void remember(@Nullable Panel panel) {
        remembered = panel;
    }

    /**
     * Tells the server, which opens or closes the storage at once. Here the storage opens once the
     * drawer is out ({@link #drawerOut}), and closes at once.
     */
    @Override
    protected void drawerChanged(@Nullable Panel panel) {
        int button = panel != null ? panel.button() : CompanionMenu.CLOSE_DRAWER_BUTTON;
        if (panel != Panel.INVENTORY) {
            menu.clickMenuButton(minecraft.player, button);
        }
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    protected void drawerOut(Panel panel) {
        if (panel == Panel.INVENTORY && !menu.storageOpen()) {
            menu.clickMenuButton(minecraft.player, CompanionMenu.OPEN_STORAGE_BUTTON);
        }
    }

    @Override
    protected boolean inDrawer(Slot slot) {
        return slot.index >= CompanionMenu.STORAGE_START && slot.index < CompanionMenu.PLAYER_START;
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
                dropDrawer();
            }
        }
    }

    /** Its owner, brighter on the Debug tab, where it is the tab's content. */
    @Override
    protected void extractLines(GuiGraphicsExtractor graphics, int x, int y) {
        int colour = menu.tab() == Tab.DEBUG ? Theme.TEXT : Theme.TEXT_DIM;
        Draw.text(graphics, font, owner(), x, y, INFO_WIDTH, colour);
    }

    /** Its owner's name, as they were called when they recruited it; none before. */
    private Component owner() {
        CompanionOwner owner = menu.companion().getAttached(Companions.OWNER);
        Component name = owner != null ? Component.literal(owner.name()) : Component.translatable(KEY + "owner.none");
        return Component.translatable(KEY + "owner", name);
    }
}
