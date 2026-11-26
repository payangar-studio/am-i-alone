package com.payangar.amialone.companion;

import com.payangar.amialone.CommonEntrypoint;
import com.payangar.amialone.debug.Debug;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
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
import net.minecraft.world.phys.AABB;
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
 *
 * <p>It has an opinion of each player made of vanilla's rumours, kept and heard as a villager
 * does: its own {@link GossipContainer}, the rumours villagers pass on to it, the ones it gets when
 * hit or when it sees a villager or a companion killed ({@link ReputationEventHandler}), and the
 * daily forgetting. A mod changing those vanilla pieces changes the companions too. One event is
 * its own: a player who heals it gets a minor positive rumour, +1 for each heart restored.
 */
public class Companion extends Avatar implements ReputationEventHandler {
    private static final String INVENTORY_KEY = CommonEntrypoint.MOD_ID + ":inventory";
    private static final String GOSSIPS_KEY = CommonEntrypoint.MOD_ID + ":gossips";
    private static final String GOSSIP_DECAY_KEY = CommonEntrypoint.MOD_ID + ":last_gossip_decay";
    /** A villager passes rumours on within √5 blocks (vanilla's {@code TradeWithVillager}). */
    private static final double HEARING_DISTANCE_SQUARED = 5.0;
    /** At most one exchange of rumours a minute, as a villager ({@code Villager.gossip}). */
    private static final long GOSSIP_COOLDOWN = 1200L;
    /** Witnesses of a death, as a villager's: the range of its nearest visible entities. */
    private static final double WITNESS_RANGE = 16.0;
    private static final byte ALL_LAYERS = (byte) Arrays.stream(PlayerModelPart.values())
            .mapToInt(PlayerModelPart::getMask)
            .reduce(0, (a, b) -> a | b);

    private final CompanionInventory inventory = new CompanionInventory();
    /** The primary weapon whose modifiers are applied; empty while none are. */
    private ItemStack appliedPrimaryWeapon = ItemStack.EMPTY;
    private final GossipContainer gossips = new GossipContainer();
    private long lastGossipTime;
    private long lastGossipDecayTime;
    /** The player who applied each of its healing effects, the ones a heal is credited to. */
    private final Map<Holder<MobEffect>, UUID> healers = new HashMap<>();
    /** While a thrown potion heals it at once: whether, and the player who threw it, if one did. */
    private boolean healingAtOnce;
    private @Nullable UUID healingAtOnceBy;
    /** The health each player restored that is not yet a whole heart, so not yet a rumour. */
    private final Map<UUID, Float> healthRestored = new HashMap<>();

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

    /** Its rumours about players and other entities, as a villager's. */
    public GossipContainer gossips() {
        return gossips;
    }

    /** Its opinion of {@code player}: the sum of its rumours about them, as a villager's reputation. */
    public int opinionOf(Player player) {
        return gossips.getReputation(player.getUUID(), type -> true);
    }

    /**
     * The rumour an event adds, as a villager adds it ({@code Villager.onReputationEventFrom}): a hit
     * on it, or a villager or companion killed in front of it. It does not trade, and is not cured.
     */
    @Override
    public void onReputationEventFrom(ReputationEventType type, Entity source) {
        if (type == ReputationEventType.VILLAGER_HURT) {
            gossips.add(source.getUUID(), GossipType.MINOR_NEGATIVE, 25);
        } else if (type == ReputationEventType.VILLAGER_KILLED) {
            gossips.add(source.getUUID(), GossipType.MAJOR_NEGATIVE, 25);
        }
    }

    /**
     * Instant health from a thrown or lingering potion is about to heal it: vanilla gives it the
     * thrower nowhere else ({@code HealOrHarmMobEffect.applyInstantaneousEffect}, through the mod's
     * mixin). {@code thrower} is the potion's owner, or the potion when it has none.
     */
    public void beginHealingAtOnce(@Nullable Entity thrower) {
        healingAtOnce = true;
        healingAtOnceBy = playerBehind(thrower);
    }

    public void endHealingAtOnce() {
        healingAtOnce = false;
        healingAtOnceBy = null;
    }

    /**
     * Heals it, and credits the health actually restored to the player behind the heal: the one who
     * threw the potion healing it at once, otherwise the one who applied its instant health (a
     * tipped arrow) or its regeneration. Every 2 health, a heart, is a minor positive rumour of +1.
     */
    @Override
    public void heal(float amount) {
        float before = getHealth();
        super.heal(amount);
        float restored = getHealth() - before;
        if (restored <= 0 || level().isClientSide()) {
            return;
        }
        UUID healer = healingAtOnce ? healingAtOnceBy : effectHealer();
        if (healer != null) {
            float total = healthRestored.getOrDefault(healer, 0.0F) + restored;
            int hearts = (int) (total / 2.0F);
            // Vanilla drops a rumour worth less than 2: the first heart waits for the second.
            int known = gossips.getReputation(healer, type -> type == GossipType.MINOR_POSITIVE);
            if (hearts > 0 && known + hearts >= GossipContainer.DISCARD_THRESHOLD) {
                gossips.add(healer, GossipType.MINOR_POSITIVE, hearts);
                total -= hearts * 2.0F;
            }
            healthRestored.put(healer, total);
        }
    }

    /** The player behind a heal over time: its instant health from an arrow first, else its regeneration. */
    private @Nullable UUID effectHealer() {
        if (hasEffect(MobEffects.INSTANT_HEALTH)) {
            return healers.get(MobEffects.INSTANT_HEALTH);
        }
        return hasEffect(MobEffects.REGENERATION) ? healers.get(MobEffects.REGENERATION) : null;
    }

    /**
     * Remembers who applied a healing effect: the player behind {@code source}, or no one when a
     * creature or a dispenser did. Vanilla updates effects every so often without a source: that
     * forgets no one.
     */
    private void rememberHealer(MobEffectInstance effect, @Nullable Entity source) {
        Holder<MobEffect> type = effect.getEffect();
        if (source == null || !(type.is(MobEffects.INSTANT_HEALTH) || type.is(MobEffects.REGENERATION))) {
            return;
        }
        UUID player = playerBehind(source);
        if (player != null) {
            healers.put(type, player);
        } else {
            healers.remove(type);
        }
    }

    /** The player behind {@code source}: the player itself, or the owner of a potion, an arrow or a cloud. */
    private static @Nullable UUID playerBehind(@Nullable Entity source) {
        if (source instanceof Player player) {
            return player.getUUID();
        }
        if (source instanceof TraceableEntity traceable && traceable.getOwner() instanceof Player owner) {
            return owner.getUUID();
        }
        return null;
    }

    /** Being hurt by an entity is an event for itself, as for a villager ({@code Villager.setLastHurtByMob}). */
    @Override
    public void setLastHurtByMob(@Nullable LivingEntity hurtBy) {
        if (hurtBy != null && level() instanceof ServerLevel level) {
            level.onReputationEvent(ReputationEventType.VILLAGER_HURT, hurtBy, this);
        }
        super.setLastHurtByMob(hurtBy);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && isAlive()) {
            maybeDecayGossip(level);
            if (tickCount % 20 == 0) {
                listenToVillagers(level);
            }
        }
    }

    /** Forgets a little once a day of game time, as a villager ({@code Villager.maybeDecayGossip}). */
    private void maybeDecayGossip(ServerLevel level) {
        long now = level.getGameTime();
        if (lastGossipDecayTime == 0L) {
            lastGossipDecayTime = now;
        } else if (now >= lastGossipDecayTime + 24000L) {
            gossips.decay();
            lastGossipDecayTime = now;
        }
    }

    /**
     * Hears a villager close by, as a villager hears another ({@code TradeWithVillager}): one idle
     * or meeting, so when its schedule lets it talk, within √5 blocks, at most once a minute.
     */
    private void listenToVillagers(ServerLevel level) {
        long now = level.getGameTime();
        if (lastGossipTime != 0L && now >= lastGossipTime && now < lastGossipTime + GOSSIP_COOLDOWN) {
            return;
        }
        AABB around = getBoundingBox().inflate(Math.sqrt(HEARING_DISTANCE_SQUARED));
        for (Villager villager : level.getEntitiesOfClass(Villager.class, around, Villager::isAlive)) {
            if (distanceToSqr(villager) <= HEARING_DISTANCE_SQUARED && talks(villager)) {
                gossips.transferFrom(villager.getGossips(), random, 10);
                lastGossipTime = now;
                return;
            }
        }
    }

    private static boolean talks(Villager villager) {
        return villager.getBrain().getActiveNonCoreActivity()
                .filter(activity -> activity == Activity.IDLE || activity == Activity.MEET)
                .isPresent();
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
        rememberHealer(effect, source);
        sendToTrackingPlayers(new ClientboundUpdateMobEffectPacket(getId(), effect, false));
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance effect, boolean doRefreshAttributes, @Nullable Entity source) {
        super.onEffectUpdated(effect, doRefreshAttributes, source);
        rememberHealer(effect, source);
        sendToTrackingPlayers(new ClientboundUpdateMobEffectPacket(getId(), effect, false));
    }

    @Override
    protected void onEffectsRemoved(Collection<MobEffectInstance> effects) {
        super.onEffectsRemoved(effects);
        for (MobEffectInstance effect : effects) {
            healers.remove(effect.getEffect());
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
        output.store(GOSSIPS_KEY, GossipContainer.CODEC, gossips);
        output.putLong(GOSSIP_DECAY_KEY, lastGossipDecayTime);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        inventory.load(input.listOrEmpty(INVENTORY_KEY, ItemStackWithSlot.CODEC));
        gossips.clear();
        input.read(GOSSIPS_KEY, GossipContainer.CODEC).ifPresent(gossips::putAll);
        lastGossipDecayTime = input.getLongOr(GOSSIP_DECAY_KEY, 0L);
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
            Entity murderer = source.getEntity();
            if (murderer != null) {
                tellWitnesses(level, murderer);
            }
        }
        super.die(source);
    }

    /**
     * Tells the villagers and companions that see it die that {@code murderer} killed it, as a dying
     * villager tells its witnesses: for them it is a villager killed.
     */
    private void tellWitnesses(ServerLevel level, Entity murderer) {
        AABB around = getBoundingBox().inflate(WITNESS_RANGE);
        for (LivingEntity witness : level.getEntitiesOfClass(LivingEntity.class, around, entity -> entity != this
                && entity instanceof ReputationEventHandler
                && entity.isAlive()
                && distanceToSqr(entity) <= WITNESS_RANGE * WITNESS_RANGE
                && entity.hasLineOfSight(this))) {
            level.onReputationEvent(ReputationEventType.VILLAGER_KILLED, murderer, (ReputationEventHandler) witness);
        }
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
