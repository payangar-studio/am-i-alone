package com.payangar.amialone.companion;

import com.payangar.amialone.CommonEntrypoint;
import com.payangar.amialone.debug.Debug;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

/**
 * A companion on the server: a player-shaped entity that is not a player, saved with its chunk.
 * Clients build it as a player ({@code ClientCompanion}), so the player animation mods animate it.
 *
 * <p>It declares no synched data: any would take an id the client player uses for its own data.
 * Its identity travels as the {@link Companions#IDENTITY} attachment instead.
 *
 * <p>It carries a {@link CompanionInventory} and wears armor in vanilla's equipment slots. It is not
 * a vanilla {@code Container}, so hoppers leave it alone. Its primary designated weapon applies its
 * attribute modifiers, as a player's main hand does, while its own main hand is empty.
 */
public class Companion extends Avatar {
    private static final String INVENTORY_KEY = CommonEntrypoint.MOD_ID + ":inventory";
    private static final byte ALL_LAYERS = (byte) Arrays.stream(PlayerModelPart.values())
            .mapToInt(PlayerModelPart::getMask)
            .reduce(0, (a, b) -> a | b);

    private final CompanionInventory inventory = new CompanionInventory();
    /** The primary weapon whose modifiers are applied; empty while none are. */
    private ItemStack appliedPrimaryWeapon = ItemStack.EMPTY;

    public Companion(EntityType<? extends Avatar> type, Level level) {
        super(type, level);
        // The skin layers (hat, jacket, sleeves, trousers), hidden by the default 0.
        entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, ALL_LAYERS);
        // Drawn before the entity enters a world, so its spawn reaches clients with it. A
        // companion loaded from its chunk then replaces it with its saved one.
        if (!level.isClientSide()) {
            identity();
            price();
        }
    }

    /** Its identity, drawn the first time it is asked for, then kept with the entity. */
    public CompanionIdentity identity() {
        return getAttachedOrCreate(Companions.IDENTITY, () -> CompanionIdentity.draw(registryAccess(), random));
    }

    /**
     * Its price in emeralds, drawn the first time it is asked for, then kept with the entity: in
     * the constructor for a new companion, at its first recruitment screen for one saved before
     * prices existed.
     */
    public int price() {
        return getAttachedOrCreate(Companions.PRICE, () -> Recruitment.drawPrice(random));
    }

    public CompanionInventory inventory() {
        return inventory;
    }

    @Override
    public ResolvableProfile getProfile() {
        // No player profile: the skin comes from the identity.
        return ResolvableProfile.Static.EMPTY;
    }

    /** A name tag's name, otherwise the drawn name, as for a player. */
    @Override
    public Component getName() {
        if (hasCustomName()) {
            return super.getName();
        }
        String name = identity().fullName();
        return name.isEmpty() ? super.getName() : Component.literal(name);
    }

    /** {@code container.0} to {@code container.37} reach its inventory, as on a player; the rest is vanilla's. */
    @Override
    public @Nullable SlotAccess getSlot(int slot) {
        SlotAccess access = inventory.getSlot(slot);
        return access != null ? access : super.getSlot(slot);
    }

    /**
     * A right-click: it opens its recruitment screen while it has no owner, its screen for its
     * owner. For anyone else nothing happens, and the held item does not act either: a consumed
     * click goes no further, and {@code CONSUME} swings no hand. The click passes on to the held
     * item, as on any entity, when the player sneaks, or holds the debug stick with the gamemaster
     * permission (the stick then opens the screen). The client's {@code ClientCompanion} answers
     * the same from the synced owner, so it predicts no swing and no item use.
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (letsClickPass(player, hand)) {
            return super.interact(player, hand, location);
        }
        boolean owned = hasOwner(this);
        if (owned && !isOwner(this, player)) {
            return InteractionResult.CONSUME.withoutItem();
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (owned) {
                CompanionMenu.open(serverPlayer, this, false);
            } else {
                RecruitMenu.open(serverPlayer, this);
            }
        }
        return InteractionResult.SUCCESS.withoutItem();
    }

    /**
     * Whether a right-click with {@code hand} on a companion goes on to the held item: when the
     * player sneaks, or clicks with the debug stick in that hand and has the gamemaster permission,
     * which the client knows too. A stick in the other hand does not count: the clicking hand's
     * item would act instead.
     */
    public static boolean letsClickPass(Player player, InteractionHand hand) {
        return player.isSecondaryUseActive()
                || player.getItemInHand(hand).is(Debug.DEBUG_STICK)
                        && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** Whether {@code player} owns {@code companion}; on a client, from the synced owner. */
    public static boolean isOwner(Entity companion, Player player) {
        CompanionOwner owner = companion.getAttached(Companions.OWNER);
        return owner != null && owner.is(player);
    }

    /** Whether {@code companion} has an owner: a player recruited it. */
    public static boolean hasOwner(Entity companion) {
        return companion.hasAttached(Companions.OWNER);
    }

    /**
     * The weapon its attack counts: what it holds in its main hand, or else its primary designated
     * weapon.
     */
    public ItemStack attackWeapon() {
        ItemStack hand = getMainHandItem();
        return hand.isEmpty() ? inventory.getItem(CompanionInventory.PRIMARY_WEAPON) : hand;
    }

    /**
     * Whether it is armed: a primary designated weapon, or a weapon of {@link
     * CompanionInventory#WEAPONS} in its main hand.
     */
    public boolean armed() {
        return !inventory.getItem(CompanionInventory.PRIMARY_WEAPON).isEmpty()
                || getMainHandItem().is(CompanionInventory.WEAPONS);
    }

    /**
     * Vanilla's detection of equipment changes, run each server tick, wrapped to apply the primary
     * designated weapon's attribute modifiers as the main hand's, while the main hand is empty: an
     * item in hand counts instead, through vanilla's own path. Its modifiers often share the
     * weapon's ids ({@code base_attack_damage}), so ours go before vanilla applies a new hand item,
     * and come after vanilla removed the previous one.
     */
    @Override
    protected @Nullable Map<EquipmentSlot, ItemStack> collectEquipmentChanges(Map<EquipmentSlot, ItemStack> lastEquipmentItems) {
        ItemStack wanted = getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
                ? inventory.getItem(CompanionInventory.PRIMARY_WEAPON)
                : ItemStack.EMPTY;
        if (!ItemStack.matches(wanted, appliedPrimaryWeapon)) {
            forEachWeaponModifier(appliedPrimaryWeapon, AttributeInstance::removeModifier);
            appliedPrimaryWeapon = ItemStack.EMPTY;
        }
        Map<EquipmentSlot, ItemStack> changes = super.collectEquipmentChanges(lastEquipmentItems);
        if (appliedPrimaryWeapon.isEmpty() && !wanted.isEmpty() && !wanted.isBroken()) {
            forEachWeaponModifier(wanted, (instance, modifier) -> {
                instance.removeModifier(modifier.id());
                instance.addTransientModifier(modifier);
            });
            appliedPrimaryWeapon = wanted.copy();
        }
        return changes;
    }

    /**
     * {@code weapon}'s modifiers in the main hand, its enchantments' attribute effects included, on
     * the companion's attributes that have them: vanilla's {@code ItemStack.forEachModifier}. They
     * are transient, so not saved: a loaded companion applies them at its first tick.
     */
    private void forEachWeaponModifier(ItemStack weapon, BiConsumer<AttributeInstance, AttributeModifier> action) {
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            AttributeInstance instance = getAttribute(attribute);
            if (instance != null) {
                action.accept(instance, modifier);
            }
        });
    }

    /** Sends its new or changed effect to the clients that see it, for their health bar. */
    @Override
    protected void onEffectAdded(MobEffectInstance effect, @Nullable Entity source) {
        super.onEffectAdded(effect, source);
        sendToTrackingPlayers(new ClientboundUpdateMobEffectPacket(getId(), effect, false));
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance effect, boolean doRefreshAttributes, @Nullable Entity source) {
        super.onEffectUpdated(effect, doRefreshAttributes, source);
        sendToTrackingPlayers(new ClientboundUpdateMobEffectPacket(getId(), effect, false));
    }

    @Override
    protected void onEffectsRemoved(Collection<MobEffectInstance> effects) {
        super.onEffectsRemoved(effects);
        for (MobEffectInstance effect : effects) {
            sendToTrackingPlayers(new ClientboundRemoveMobEffectPacket(getId(), effect.getEffect()));
        }
    }

    /** Sends all its effects to a client that starts seeing it, once the client has built it. */
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        for (MobEffectInstance effect : getActiveEffects()) {
            player.connection.send(new ClientboundUpdateMobEffectPacket(getId(), effect, false));
        }
    }

    private void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {
        if (level() instanceof ServerLevel level) {
            level.getChunkSource().sendToTrackingPlayers(this, packet);
        }
    }

    /** Keeps the {@link Companions#ABSORPTION} attachment, which reaches the clients, up to date. */
    @Override
    protected void internalSetAbsorptionAmount(float amount) {
        super.internalSetAbsorptionAmount(amount);
        if (!level().isClientSide() && getAttachedOrElse(Companions.ABSORPTION, 0.0F) != amount) {
            setAttached(Companions.ABSORPTION, amount);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        inventory.save(output.list(INVENTORY_KEY, ItemStackWithSlot.CODEC));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        inventory.load(input.listOrEmpty(INVENTORY_KEY, ItemStackWithSlot.CODEC));
    }

    /**
     * Drops everything it carries, as {@code Player.dropEquipment} does but whatever {@code
     * keepInventory} says: a player keeps their items because they respawn, a companion does not.
     * The items with the curse of vanishing are destroyed first.
     */
    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (vanishes(inventory.getItem(i))) {
                inventory.removeItemNoUpdate(i);
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (vanishes(equipment.get(slot))) {
                equipment.set(slot, ItemStack.EMPTY);
            }
        }
        for (ItemStack stack : inventory.removeAllItems()) {
            ItemEntity drop = createItemStackToDrop(stack, true, false);
            if (drop != null) {
                level.addFreshEntity(drop);
            }
        }
        equipment.dropAll(this);
    }

    private static boolean vanishes(ItemStack stack) {
        return !stack.isEmpty() && EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP);
    }

    @Override
    public void die(DamageSource source) {
        if (!isRemoved() && !dead && level() instanceof ServerLevel level) {
            announceDeath(level);
        }
        super.die(source);
    }

    /**
     * Broadcasts the death message as {@code ServerPlayer.die} does for a player: the same game
     * rule, the same message from the combat tracker, the same visibility of its team. The player
     * list's team broadcasts take a player, so the visibility is applied here.
     */
    private void announceDeath(ServerLevel level) {
        if (!level.getGameRules().get(GameRules.SHOW_DEATH_MESSAGES)) {
            return;
        }
        Component message = getCombatTracker().getDeathMessage();
        PlayerList players = level.getServer().getPlayerList();
        Team team = getTeam();
        Team.Visibility visibility = team == null ? Team.Visibility.ALWAYS : team.getDeathMessageVisibility();
        switch (visibility) {
            case ALWAYS -> players.broadcastSystemMessage(message, false);
            case HIDE_FOR_OTHER_TEAMS -> {
                for (ServerPlayer player : players.getPlayers()) {
                    if (player.getTeam() == team) {
                        player.sendSystemMessage(message);
                    }
                }
            }
            case HIDE_FOR_OWN_TEAM -> {
                for (ServerPlayer player : players.getPlayers()) {
                    if (player.getTeam() != team) {
                        player.sendSystemMessage(message);
                    }
                }
            }
            case NEVER -> {}
        }
    }
}
