package com.payangar.amialone.companion;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * What the screens that show a companion share, on both sides: the companion, the player's
 * inventory under it, and its attribute values, which the server sends the client ({@link
 * CompanionAttributeValues}) at the opening and whenever one changes.
 */
public abstract class CompanionViewMenu extends AbstractContainerMenu {
    private final LivingEntity companion;
    /** On the server, the player it sends the attribute values to; none on the client. */
    private final @Nullable ServerPlayer viewer;
    /** On the server, the attribute and computed values last sent; on the client, the ones last received. */
    private List<CompanionAttributeValues.Value> attributeValues = List.of();
    private List<CompanionAttributeValues.Computed> computedValues = List.of();
    private boolean armed;
    private boolean attributeValuesSent;

    protected CompanionViewMenu(MenuType<?> type, int containerId, Inventory playerInventory, LivingEntity companion) {
        super(type, containerId);
        this.companion = companion;
        this.viewer = playerInventory.player instanceof ServerPlayer player ? player : null;
    }

    /** The companion of an opened screen, on the client, from its entity id. */
    protected static LivingEntity find(Player player, int id) {
        if (player.level().getEntity(id) instanceof LivingEntity entity) {
            return entity;
        }
        throw new IllegalStateException("The companion " + id + " of an opened screen is not on the client");
    }

    /** The player's inventory as vanilla lays it out, under the companion: its three rows, then its hotbar. */
    protected void addPlayerInventory(Inventory playerInventory) {
        for (int i = 9; i < 36; i++) {
            addSlot(new PlainSlot(
                    playerInventory,
                    i,
                    CompanionMenu.PLAYER_X + i % 9 * 18,
                    CompanionMenu.PLAYER_Y + (i / 9 - 1) * 18));
        }
        for (int i = 0; i < 9; i++) {
            addSlot(new PlainSlot(playerInventory, i, CompanionMenu.PLAYER_X + i * 18, CompanionMenu.HOTBAR_Y));
        }
    }

    public LivingEntity companion() {
        return companion;
    }

    /** Whether the companion is alive and within reach of {@code player}, as a horse's inventory. */
    protected boolean companionInReach(Player player) {
        return companion.isAlive() && player.isWithinEntityInteractionRange(companion, 4.0);
    }

    /**
     * The values of the companion's attributes that have an entry: on the client, as the server
     * last sent them; empty until the first message.
     */
    public Map<Holder<Attribute>, CompanionAttributeValues.Value> attributeValues() {
        return attributeValues.stream()
                .collect(Collectors.toUnmodifiableMap(CompanionAttributeValues.Value::attribute, Function.identity()));
    }

    /**
     * The companion's computed values that an entry names: on the client, as the server last sent
     * them; empty until the first message.
     */
    public Map<CompanionComputedValue, CompanionAttributeValues.Computed> computedValues() {
        return computedValues.stream()
                .collect(Collectors.toUnmodifiableMap(CompanionAttributeValues.Computed::value, Function.identity()));
    }

    /** Whether the companion is armed: on the client, as the server last sent it. */
    public boolean armed() {
        return armed;
    }

    /** The client's side: takes the values the server sent for this menu. */
    public void receive(CompanionAttributeValues message) {
        if (message.containerId() == containerId) {
            attributeValues = List.copyOf(message.values());
            computedValues = List.copyOf(message.computed());
            armed = message.armed();
        }
    }

    /**
     * The server's per-tick sync, then the attribute values if they changed. The first one, when
     * the player opens the screen (vanilla calls it as it adds the player's listener), sends them
     * all.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        sendAttributeValues();
    }

    /**
     * Sends the viewer the companion's attribute and computed values and whether it is armed,
     * unless they are the ones it already has.
     * Without a check of the client's channels: the mod is required on both sides, and the side
     * check refuses a client without it.
     */
    private void sendAttributeValues() {
        if (viewer == null || !(companion instanceof Companion server)) {
            return;
        }
        List<CompanionAttributeValues.Value> values = CompanionAttributeValues.of(server);
        List<CompanionAttributeValues.Computed> computed = CompanionAttributeValues.computedOf(server);
        boolean nowArmed = server.armed();
        if (attributeValuesSent
                && values.equals(attributeValues)
                && computed.equals(computedValues)
                && nowArmed == armed) {
            return;
        }
        attributeValues = values;
        computedValues = computed;
        armed = nowArmed;
        attributeValuesSent = true;
        ServerPlayNetworking.send(viewer, new CompanionAttributeValues(containerId, values, computed, nowArmed));
    }

    /** A slot the screen highlights itself: not with vanilla's sprite. */
    protected static class PlainSlot extends Slot {
        protected PlainSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean isHighlightable() {
            return false;
        }
    }
}
