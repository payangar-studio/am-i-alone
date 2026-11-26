package com.payangar.amialone.gametest;

import com.mojang.datafixers.util.Either;
import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionAttribute;
import com.payangar.amialone.companion.CompanionAttributeGroup;
import com.payangar.amialone.companion.CompanionAttributeValues;
import com.payangar.amialone.companion.CompanionComputedValue;
import com.payangar.amialone.companion.CompanionInventory;
import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.Companions;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The companion's attributes: its attack and its primary weapon's modifiers; the screen's data
 * registries, and the values the server sends.
 */
public class CompanionAttributesServerGameTest {
    private static final String KEY = "container.am_i_alone.companion.attributes.";

    /** A shipped entry, named after its attribute: its group, its order, its format, when it is shown. */
    private record Shipped(String attribute, String group, int order, String format, String shown) {}

    private static final List<Shipped> SHIPPED = List.of(
            new Shipped("attack_damage", "combat", 10, "plain", "always"),
            new Shipped("attack_speed", "combat", 20, "plain", "when_armed"),
            new Shipped("max_health", "defence", 10, "plain", "always"),
            new Shipped("armor", "defence", 20, "plain", "always"),
            new Shipped("armor_toughness", "defence", 30, "plain", "always"),
            new Shipped("knockback_resistance", "defence", 40, "times_ten", "always"),
            new Shipped("max_absorption", "defence", 50, "plain", "when_changed"),
            new Shipped("movement_speed", "movement", 10, "percent_of_default", "always"),
            new Shipped("water_movement_efficiency", "movement", 20, "plain", "when_changed"),
            new Shipped("movement_efficiency", "movement", 30, "plain", "when_changed"),
            new Shipped("jump_strength", "movement", 40, "plain", "when_changed"),
            new Shipped("step_height", "movement", 50, "plain", "when_changed"),
            new Shipped("oxygen_bonus", "hazards", 10, "plain", "when_changed"),
            new Shipped("burning_time", "hazards", 20, "percent", "when_changed"),
            new Shipped("explosion_knockback_resistance", "hazards", 30, "plain", "when_changed"),
            new Shipped("safe_fall_distance", "hazards", 40, "plain", "when_changed"),
            new Shipped("fall_damage_multiplier", "hazards", 50, "percent", "when_changed"));

    /** The shipped entries' colour thresholds; the others have none. */
    private static final Map<String, Double> THRESHOLDS = Map.of("attack_damage", 1.0, "attack_speed", 1.0);

    /**
     * An entry the gametest mod ships, for a value the mod does not compute
     * (src/gametest/resources/data/am_i_alone_gametest/am_i_alone/companion_attribute/).
     */
    private static final Identifier UNKNOWN_VALUE = Identifier.fromNamespaceAndPath("am_i_alone_gametest", "unknown_value");

    /** Enough ticks for the companion to tick once at least: it applies its weapon's modifiers then. */
    private static final int TICKS = 2;

    /**
     * An entry the gametest mod ships, for an attribute of a mod that is not there
     * (src/gametest/resources/data/am_i_alone_gametest/am_i_alone/companion_attribute/).
     */
    private static final Identifier ABSENT = Identifier.fromNamespaceAndPath("am_i_alone_gametest", "absent_mod_mana");

    @GameTest
    public void shippedGroupsLoad(GameTestHelper helper) {
        Registry<CompanionAttributeGroup> groups =
                helper.getLevel().registryAccess().lookupOrThrow(CompanionAttributeGroup.REGISTRY);
        Map<String, CompanionAttributeGroup> shipped = groups.entrySet().stream()
                .filter(entry -> entry.getKey().identifier().getNamespace().equals("am_i_alone"))
                .collect(Collectors.toMap(entry -> entry.getKey().identifier().getPath(), Map.Entry::getValue));
        helper.assertValueEqual(
                shipped,
                Map.of(
                        "combat", new CompanionAttributeGroup(Component.translatable(KEY + "group.combat"), 0),
                        "defence", new CompanionAttributeGroup(Component.translatable(KEY + "group.defence"), 10),
                        "movement", new CompanionAttributeGroup(Component.translatable(KEY + "group.movement"), 20),
                        "hazards", new CompanionAttributeGroup(Component.translatable(KEY + "group.hazards"), 30)),
                "the shipped groups");
        helper.succeed();
    }

    @GameTest
    public void shippedAttributesLoad(GameTestHelper helper) {
        Registry<CompanionAttribute> attributes = helper.getLevel().registryAccess().lookupOrThrow(CompanionAttribute.REGISTRY);
        Set<String> names = attributes.keySet().stream()
                .filter(id -> id.getNamespace().equals("am_i_alone"))
                .map(Identifier::getPath)
                .collect(Collectors.toSet());
        Set<String> shippedNames = SHIPPED.stream().map(Shipped::attribute).collect(Collectors.toCollection(HashSet::new));
        shippedNames.add("attack_range");
        helper.assertValueEqual(names, shippedNames, "the shipped entries");
        for (Shipped shipped : SHIPPED) {
            CompanionAttribute entry = attributes.getValue(Identifier.fromNamespaceAndPath("am_i_alone", shipped.attribute()));
            String name = shipped.attribute();
            helper.assertTrue(entry.resolve().isPresent(), name + " does not resolve to an attribute");
            helper.assertValueEqual(
                    entry.source().left().orElseThrow().identifier(), Identifier.withDefaultNamespace(name), name + "'s attribute");
            helper.assertValueEqual(
                    entry.colourThreshold(), Optional.ofNullable(THRESHOLDS.get(name)), name + "'s colour threshold");
            helper.assertValueEqual(entry.group().unwrapKey().orElseThrow().identifier(),
                    Identifier.fromNamespaceAndPath("am_i_alone", shipped.group()), name + "'s group");
            helper.assertValueEqual(entry.order(), shipped.order(), name + "'s order");
            helper.assertValueEqual(
                    entry.label(), Optional.of(Component.translatable(KEY + "label." + name)), name + "'s label");
            helper.assertValueEqual(entry.format().getSerializedName(), shipped.format(), name + "'s format");
            helper.assertValueEqual(entry.shown().getSerializedName(), shipped.shown(), name + "'s visibility");
        }
        CompanionAttribute range = attributes.getValue(Identifier.fromNamespaceAndPath("am_i_alone", "attack_range"));
        helper.assertValueEqual(
                range,
                new CompanionAttribute(
                        Either.right(Identifier.fromNamespaceAndPath("am_i_alone", "attack_range")),
                        range.group(),
                        30,
                        Optional.of(Component.translatable(KEY + "label.attack_range")),
                        CompanionAttribute.Format.PLAIN,
                        CompanionAttribute.Shown.ALWAYS,
                        Optional.empty()),
                "the attack range's entry");
        helper.assertValueEqual(range.group().unwrapKey().orElseThrow().identifier(),
                Identifier.fromNamespaceAndPath("am_i_alone", "combat"), "the attack range's group");
        helper.assertValueEqual(range.computed(), Optional.of(CompanionComputedValue.ATTACK_RANGE), "the attack range's value");
        helper.succeed();
    }

    /** An entry for an attribute that does not exist loads, with the rest, and resolves to nothing. */
    @GameTest
    public void entryOfAnAbsentAttributeLoadsAndIsIgnored(GameTestHelper helper) {
        CompanionAttribute entry =
                helper.getLevel().registryAccess().lookupOrThrow(CompanionAttribute.REGISTRY).getValue(ABSENT);
        helper.assertTrue(entry != null, "the entry of an absent attribute did not load");
        helper.assertTrue(entry.resolve().isEmpty(), "the absent attribute resolves: " + entry.resolve());
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        helper.assertValueEqual(
                CompanionAttributeValues.of(companion).size(), SHIPPED.size(), "the values read, the absent attribute's ignored");
        helper.succeed();
    }

    /** An entry for a value the mod does not compute loads, with the rest, and resolves to nothing. */
    @GameTest
    public void entryOfAnUnknownComputedValueLoadsAndIsIgnored(GameTestHelper helper) {
        CompanionAttribute entry =
                helper.getLevel().registryAccess().lookupOrThrow(CompanionAttribute.REGISTRY).getValue(UNKNOWN_VALUE);
        helper.assertTrue(entry != null, "the entry of an unknown computed value did not load");
        helper.assertTrue(entry.resolve().isEmpty() && entry.computed().isEmpty(), "the unknown value resolves: " + entry);
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        helper.assertValueEqual(
                CompanionAttributeValues.computedOf(companion).stream().map(CompanionAttributeValues.Computed::value).toList(),
                List.of(CompanionComputedValue.ATTACK_RANGE),
                "the computed values, the unknown one ignored");
        helper.succeed();
    }

    /**
     * The attack range follows the weapon that counts, the main hand's item, or else the primary
     * weapon: up to the interaction range bare-handed and with a sword, which has no attack range
     * component; from 2 to 4.5 with a spear, its survival reach.
     */
    @GameTest
    public void attackRangeFollowsTheWeapon(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        assertRange(helper, companion, 0.0, 3.0, "bare-handed");
        primary(companion, new ItemStack(Items.DIAMOND_SWORD));
        assertRange(helper, companion, 0.0, 3.0, "a primary sword");
        primary(companion, new ItemStack(Items.DIAMOND_SPEAR));
        assertRange(helper, companion, 2.0, 4.5, "a primary spear");
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        assertRange(helper, companion, 0.0, 3.0, "a sword in hand, a primary spear");
        primary(companion, new ItemStack(Items.DIAMOND_SWORD));
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
        assertRange(helper, companion, 2.0, 4.5, "a spear in hand, a primary sword");
        helper.succeed();
    }

    /**
     * A value is coloured against the entry's threshold, or else the attribute's default, never the
     * companion's base: a max health base raised to 40 reads as a gain, a value at its default as
     * neither; a when_changed entry shows once its value leaves the default, even as its new base.
     * The attack's thresholds: a bare hand's damage reads as neither and its speed as a gain, a
     * sword's attack speed as a gain, an axe's as a loss.
     */
    @GameTest
    public void valuesReadAgainstTheirDefault(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        assertTone(helper, companion, Attributes.MAX_HEALTH, CompanionAttribute.Tone.NEUTRAL, "max health at its default");
        assertTone(helper, companion, Attributes.ARMOR, CompanionAttribute.Tone.NEUTRAL, "no armor");
        assertTone(helper, companion, Attributes.ATTACK_DAMAGE, CompanionAttribute.Tone.NEUTRAL, "bare-handed damage");
        assertTone(helper, companion, Attributes.ATTACK_SPEED, CompanionAttribute.Tone.GOOD, "bare-handed speed");
        CompanionAttribute jump = entry(helper, "jump_strength");
        helper.assertFalse(
                jump.shows(CompanionAttribute.differsFromDefault(Attributes.JUMP_STRENGTH.value(), value(companion, Attributes.JUMP_STRENGTH)), false),
                "jump at its default shown");
        companion.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        companion.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0.6);
        assertTone(helper, companion, Attributes.MAX_HEALTH, CompanionAttribute.Tone.GOOD, "max health base 40");
        helper.assertTrue(
                jump.shows(CompanionAttribute.differsFromDefault(Attributes.JUMP_STRENGTH.value(), value(companion, Attributes.JUMP_STRENGTH)), false),
                "jump base 0.6 hidden");
        helper.startSequence()
                .thenExecute(() -> primary(companion, new ItemStack(Items.DIAMOND_SWORD)))
                .thenExecuteAfter(TICKS, () -> assertTone(
                        helper, companion, Attributes.ATTACK_SPEED, CompanionAttribute.Tone.GOOD, "a sword's speed"))
                .thenExecute(() -> primary(companion, new ItemStack(Items.IRON_AXE)))
                .thenExecuteAfter(TICKS, () -> assertTone(
                        helper, companion, Attributes.ATTACK_SPEED, CompanionAttribute.Tone.BAD, "an axe's speed"))
                .thenSucceed();
    }

    /**
     * Attack speed is shown while the companion is armed only: a primary designated weapon, or a
     * weapon in its main hand; an item in hand that is not a weapon does not count.
     */
    @GameTest
    public void attackSpeedShownWhenArmed(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        CompanionAttribute speed = entry(helper, "attack_speed");
        helper.assertFalse(speed.shows(false, companion.armed()), "attack speed shown unarmed");
        primary(companion, new ItemStack(Items.DIAMOND_SWORD));
        helper.assertTrue(speed.shows(false, companion.armed()), "attack speed hidden with a primary sword");
        primary(companion, ItemStack.EMPTY);
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(speed.shows(false, companion.armed()), "attack speed hidden with a sword in hand");
        companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.APPLE));
        helper.assertFalse(speed.shows(false, companion.armed()), "attack speed shown with an apple in hand");
        helper.assertTrue(entry(helper, "attack_damage").shows(false, false), "attack damage hidden unarmed");
        helper.assertTrue(entry(helper, "attack_range").shows(false, false), "attack range hidden unarmed");
        helper.succeed();
    }

    private static void assertRange(GameTestHelper helper, Companion companion, double min, double max, String when) {
        helper.assertValueEqual(
                CompanionAttributeValues.computedOf(companion),
                List.of(new CompanionAttributeValues.Computed(CompanionComputedValue.ATTACK_RANGE, min, max, 3.0)),
                "the attack range with " + when);
    }

    /** The tone of {@code attribute}'s value as the server sends it, read by its shipped entry. */
    private static void assertTone(
            GameTestHelper helper, Companion companion, Holder<Attribute> attribute, CompanionAttribute.Tone tone, String when) {
        CompanionAttribute entry = entry(helper, attribute.unwrapKey().orElseThrow().identifier().getPath());
        double value = value(companion, attribute);
        helper.assertValueEqual(entry.tone(attribute.value(), value), tone, "the tone of " + value + " with " + when);
    }

    private static CompanionAttribute entry(GameTestHelper helper, String name) {
        return helper.getLevel().registryAccess().lookupOrThrow(CompanionAttribute.REGISTRY)
                .getValue(Identifier.fromNamespaceAndPath("am_i_alone", name));
    }

    /** The value of {@code attribute} the server sends. */
    private static double value(Companion companion, Holder<Attribute> attribute) {
        return CompanionAttributeValues.of(companion).stream()
                .filter(value -> value.attribute().equals(attribute))
                .findFirst()
                .orElseThrow()
                .value();
    }

    /**
     * The open screen's menu sends its player the values of the companion's attributes that have an
     * entry, knockback resistance from a modifier included, which vanilla does not sync; again when
     * one changes, and not when none does.
     */
    @GameTest
    public void menuSendsTheAttributeValues(GameTestHelper helper) {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        ServerPlayer player = CompanionInventoryServerGameTest.connectPlayer(helper, "attribute_viewer", connection);
        try {
            Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
            player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
            modify(companion, Attributes.KNOCKBACK_RESISTANCE, 0.4);
            sent(channel);

            CompanionMenu.open(player, companion, true);
            CompanionMenu menu = (CompanionMenu) player.containerMenu;
            List<CompanionAttributeValues> opening = sent(channel);
            helper.assertValueEqual(opening.size(), 1, "messages at the opening");
            helper.assertValueEqual(opening.getFirst().containerId(), menu.containerId, "the message's menu");
            Map<Holder<Attribute>, CompanionAttributeValues.Value> values = byAttribute(opening.getFirst());
            Set<Holder<Attribute>> listed = SHIPPED.stream()
                    .map(shipped -> (Holder<Attribute>) BuiltInRegistries.ATTRIBUTE
                            .get(Identifier.withDefaultNamespace(shipped.attribute()))
                            .orElseThrow())
                    .collect(Collectors.toSet());
            helper.assertValueEqual(values.keySet(), listed, "the attributes sent");
            helper.assertValueEqual(
                    values.get(Attributes.KNOCKBACK_RESISTANCE),
                    new CompanionAttributeValues.Value(Attributes.KNOCKBACK_RESISTANCE, 0.4, 0.0),
                    "the knockback resistance sent");
            helper.assertValueEqual(
                    values.get(Attributes.ATTACK_DAMAGE),
                    new CompanionAttributeValues.Value(Attributes.ATTACK_DAMAGE, 1.0, 1.0),
                    "the attack damage sent");
            helper.assertValueEqual(
                    opening.getFirst().computed(),
                    List.of(new CompanionAttributeValues.Computed(CompanionComputedValue.ATTACK_RANGE, 0.0, 3.0, 3.0)),
                    "the computed values sent");
            helper.assertFalse(opening.getFirst().armed(), "the unarmed companion sent as armed");
            helper.assertValueEqual(
                    values.get(Attributes.MAX_HEALTH),
                    new CompanionAttributeValues.Value(Attributes.MAX_HEALTH, 20.0, 20.0),
                    "the max health sent");

            menu.broadcastChanges();
            helper.assertValueEqual(sent(channel).size(), 0, "messages without a change");

            modify(companion, Attributes.ARMOR, 5.0);
            menu.broadcastChanges();
            List<CompanionAttributeValues> changed = sent(channel);
            helper.assertValueEqual(changed.size(), 1, "messages after a change");
            helper.assertValueEqual(
                    byAttribute(changed.getFirst()).get(Attributes.ARMOR),
                    new CompanionAttributeValues.Value(Attributes.ARMOR, 5.0, 0.0),
                    "the armor sent after its change");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** Bare-handed, a player's attack: 1 damage, 4 attacks a second. */
    @GameTest
    public void attacksBareHandedAsAPlayer(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        helper.startSequence()
                .thenExecuteAfter(TICKS, () -> assertAttack(helper, companion, 1.0, 4.0, "bare-handed"))
                .thenSucceed();
    }

    /**
     * The primary weapon applies its modifiers as a main hand item: a diamond sword gives 1 + 6
     * damage and 4 - 2.4 attack speed, as on a player. Taking it out restores the bare hand.
     */
    @GameTest
    public void primaryWeaponGivesItsAttack(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        helper.startSequence()
                .thenExecute(() -> command(helper, companion, "item replace entity %s container.36 with diamond_sword"))
                .thenExecuteAfter(TICKS, () -> assertAttack(helper, companion, 7.0, 1.6, "a diamond sword put in by /item"))
                .thenExecute(() -> primary(companion, ItemStack.EMPTY))
                .thenExecuteAfter(TICKS, () -> assertAttack(helper, companion, 1.0, 4.0, "the sword taken out"))
                .thenSucceed();
    }

    /** The secondary weapon slot changes nothing, a sword in it included. */
    @GameTest
    public void secondaryWeaponChangesNothing(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        helper.startSequence()
                .thenExecute(() -> companion.inventory()
                        .setItem(CompanionInventory.SECONDARY_WEAPON, new ItemStack(Items.DIAMOND_SWORD)))
                .thenExecuteAfter(TICKS, () -> assertAttack(helper, companion, 1.0, 4.0, "a sword in the secondary slot"))
                .thenSucceed();
    }

    /** A shield, a bow or a crossbow as the primary weapon: no attack modifier, the bare hand's attack. */
    @GameTest
    public void primaryWithoutAttackModifiersChangesNothing(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        GameTestSequence sequence = helper.startSequence();
        for (Item item : List.of(Items.SHIELD, Items.BOW, Items.CROSSBOW)) {
            sequence.thenExecute(() -> primary(companion, new ItemStack(item)))
                    .thenExecuteAfter(TICKS, () -> assertAttack(helper, companion, 1.0, 4.0, "a primary " + item));
        }
        sequence.thenSucceed();
    }

    /**
     * The primary weapon's modifiers count while the main hand is empty only: an item in hand counts
     * instead, through vanilla's equipment path, never both; an item without modifiers too.
     */
    @GameTest
    public void mainHandItemReplacesThePrimaryWeapon(GameTestHelper helper) {
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        helper.startSequence()
                .thenExecute(() -> primary(companion, new ItemStack(Items.DIAMOND_SWORD)))
                .thenExecuteAfter(TICKS, () -> assertAttack(helper, companion, 7.0, 1.6, "the primary sword, hand empty"))
                .thenExecute(() -> companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD)))
                .thenExecuteAfter(TICKS, () -> assertAttack(helper, companion, 7.0, 1.6, "a sword in hand too"))
                .thenExecute(() -> companion.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY))
                .thenExecuteAfter(TICKS, () -> assertAttack(helper, companion, 7.0, 1.6, "the hand emptied"))
                .thenExecute(() -> command(helper, companion, "item replace entity %s weapon.mainhand with apple"))
                .thenExecuteAfter(TICKS, () -> assertAttack(helper, companion, 1.0, 4.0, "an apple in hand"))
                .thenExecute(() -> companion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)))
                .thenExecuteAfter(TICKS, () -> assertAttack(helper, companion, 6.0, 1.6, "an iron sword in hand"))
                .thenExecute(() -> companion.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY))
                .thenExecuteAfter(TICKS, () -> assertAttack(helper, companion, 7.0, 1.6, "the hand emptied again"))
                .thenSucceed();
    }

    /**
     * The modifiers are transient, as vanilla's equipment ones: the save keeps the weapon, and the
     * loaded companion applies its modifiers again.
     */
    @GameTest
    public void primaryWeaponAttackSurvivesSaving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        Companion[] loaded = new Companion[1];
        helper.startSequence()
                .thenExecute(() -> primary(companion, new ItemStack(Items.DIAMOND_SWORD)))
                .thenExecuteAfter(TICKS, () -> {
                    assertAttack(helper, companion, 7.0, 1.6, "before saving");
                    // The save a chunk writes for its entities, then the load that brings them back.
                    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                    helper.assertTrue(companion.save(output), "the companion is not saved with its chunk");
                    companion.discard();
                    Entity entity = EntityType.loadEntityRecursive(
                            TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()),
                            level,
                            EntitySpawnReason.LOAD,
                            e -> e);
                    helper.assertTrue(entity instanceof Companion, "the save did not load back as a companion: " + entity);
                    loaded[0] = (Companion) entity;
                    level.addFreshEntity(loaded[0]);
                })
                .thenExecuteAfter(TICKS, () -> assertAttack(helper, loaded[0], 7.0, 1.6, "after loading"))
                .thenExecute(() -> loaded[0].discard())
                .thenSucceed();
    }

    private static void assertAttack(GameTestHelper helper, Companion companion, double damage, double speed, String when) {
        double actualDamage = companion.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double actualSpeed = companion.getAttributeValue(Attributes.ATTACK_SPEED);
        helper.assertTrue(
                Math.abs(actualDamage - damage) < 1.0E-6 && Math.abs(actualSpeed - speed) < 1.0E-6,
                "attack damage " + actualDamage + " and speed " + actualSpeed + " with " + when
                        + ", expected " + damage + " and " + speed);
    }

    private static void primary(Companion companion, ItemStack stack) {
        companion.inventory().setItem(CompanionInventory.PRIMARY_WEAPON, stack);
    }

    /** Runs {@code command}, its {@code %s} replaced by the companion's UUID. */
    private static void command(GameTestHelper helper, Companion companion, String command) {
        MinecraftServer server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), command.formatted(companion.getStringUUID()));
    }

    /** The attribute values the server sent over {@code channel} since the last call. */
    private static List<CompanionAttributeValues> sent(EmbeddedChannel channel) {
        List<CompanionAttributeValues> messages = new ArrayList<>();
        for (Object message; (message = channel.readOutbound()) != null; ) {
            if (message instanceof ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof CompanionAttributeValues values) {
                messages.add(values);
            }
        }
        return messages;
    }

    private static Map<Holder<Attribute>, CompanionAttributeValues.Value> byAttribute(CompanionAttributeValues message) {
        return message.values().stream()
                .collect(Collectors.toMap(CompanionAttributeValues.Value::attribute, value -> value));
    }

    private static void modify(Companion companion, Holder<Attribute> attribute, double amount) {
        companion.getAttribute(attribute).addPermanentModifier(new AttributeModifier(
                Identifier.fromNamespaceAndPath("am_i_alone_gametest", "test"), amount, AttributeModifier.Operation.ADD_VALUE));
    }
}
