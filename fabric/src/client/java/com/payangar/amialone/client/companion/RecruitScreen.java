package com.payangar.amialone.client.companion;

import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.CompanionMenu.Panel;
import com.payangar.amialone.companion.Recruitment.Payment;
import com.payangar.amialone.companion.RecruitMenu;
import com.payangar.amialone.vendor.gui.Draw;
import com.payangar.amialone.vendor.gui.Theme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A companion's recruitment screen: the companion's screen read-only, under a Recruitment tab. Its
 * armor and designated weapons are icons with their tooltips, where the companion's screen has
 * slots; its drawer has its attributes only; under its bars, its price and Recruit, with the
 * payment slot left of Recruit when that is the way to pay. Recruit is greyed while the player
 * cannot pay.
 */
public class RecruitScreen extends CompanionViewScreen<RecruitMenu> {
    private static final List<Identifier> EQUIPMENT_ICONS = List.of(
            CompanionMenu.ARMOR_ICONS.get(0),
            CompanionMenu.ARMOR_ICONS.get(1),
            CompanionMenu.ARMOR_ICONS.get(2),
            CompanionMenu.ARMOR_ICONS.get(3),
            CompanionMenu.PRIMARY_WEAPON_ICON,
            InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
    /** Recruit, level with the payment slot's box, right of it when there is one. */
    private static final int BUTTON_Y = RecruitMenu.PAYMENT_Y - 1;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 4;

    /** The drawer's panel left open when a recruitment screen last closed, for the session. */
    private static @Nullable Panel remembered;

    private @Nullable Button recruit;
    /** Whether Recruit was last made active; none before the first frame. */
    private @Nullable Boolean shownCanPay;

    public RecruitScreen(RecruitMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        recruit = addRenderableWidget(Button.builder(Component.translatable(KEY + "recruit"), button -> pressRecruit())
                .bounds(0, 0, buttonWidth(), BUTTON_HEIGHT)
                .build());
        shownCanPay = null;
        placeRecruit();
    }

    /** Asks the server to recruit, which checks the payment again. */
    public void pressRecruit() {
        if (menu.canPay(minecraft.player)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RecruitMenu.RECRUIT_BUTTON);
        }
    }

    private int buttonX() {
        return menu.payment() == Payment.SLOT ? INFO_X + 18 + BUTTON_GAP : INFO_X;
    }

    private int buttonWidth() {
        return INFO_X + INFO_WIDTH - buttonX();
    }

    /** Follows the screen, which moves as the drawer slides, and greys Recruit while it cannot pay. */
    private void placeRecruit() {
        if (recruit == null) {
            return;
        }
        recruit.setPosition(leftPos + buttonX(), topPos + BUTTON_Y);
        boolean canPay = menu.canPay(minecraft.player);
        if (shownCanPay == null || shownCanPay != canPay) {
            shownCanPay = canPay;
            recruit.active = canPay;
            String need = menu.payment() == Payment.SLOT ? "recruit.need_slot" : "recruit.need";
            recruit.setTooltip(canPay ? null : Tooltip.create(Component.translatable(KEY + need, menu.price())));
        }
    }

    /** Whether Recruit is active: the player can pay. */
    public boolean recruitActive() {
        return recruit != null && recruit.active;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        placeRecruit();
    }

    @Override
    protected List<Component> tabTitles() {
        return List.of(Component.translatable(KEY + "tab.recruitment"));
    }

    @Override
    protected int selectedTab() {
        return 0;
    }

    @Override
    protected List<Panel> panels() {
        return List.of(Panel.ATTRIBUTES);
    }

    @Override
    protected boolean drawerAvailable() {
        return true;
    }

    @Override
    protected @Nullable Panel remembered() {
        return remembered;
    }

    @Override
    protected void remember(@Nullable Panel panel) {
        remembered = panel;
    }

    /** Its price, under the hearts. */
    @Override
    protected void extractLines(GuiGraphicsExtractor graphics, int x, int y) {
        Draw.text(graphics, font, Component.translatable(KEY + "recruit.price", menu.price()), x, y, INFO_WIDTH, Theme.TEXT);
    }

    /**
     * Its armor and designated weapons as the companion's screen places its slots, in sunken boxes:
     * the item, or the empty slot's icon.
     */
    @Override
    protected void extractEquipment(GuiGraphicsExtractor graphics) {
        List<ItemStack> equipment = menu.equipment();
        for (int i = 0; i < equipment.size(); i++) {
            int x = leftPos + equipmentX(i);
            int y = topPos + equipmentY(i);
            sunken(graphics, x - 1, y - 1, 18, 18);
            ItemStack stack = equipment.get(i);
            if (stack.isEmpty()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EQUIPMENT_ICONS.get(i), x, y, 16, 16);
            } else {
                graphics.item(stack, x, y);
                graphics.itemDecorations(font, stack, x, y);
            }
        }
    }

    /** The hovered piece of equipment's tooltip, as a slot's, when no slot is hovered. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null || !menu.getCarried().isEmpty()) {
            return;
        }
        List<ItemStack> equipment = menu.equipment();
        for (int i = 0; i < equipment.size(); i++) {
            ItemStack stack = equipment.get(i);
            if (!stack.isEmpty() && isHovering(equipmentX(i), equipmentY(i), 16, 16, mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
        }
    }

    private static int equipmentX(int index) {
        return index < CompanionMenu.ARMOR_ICONS.size() ? CompanionMenu.ARMOR_X : CompanionMenu.WEAPONS_X;
    }

    private static int equipmentY(int index) {
        int row = index < CompanionMenu.ARMOR_ICONS.size() ? index : index - CompanionMenu.ARMOR_ICONS.size();
        return CompanionMenu.COMPANION_Y + row * 18;
    }
}
