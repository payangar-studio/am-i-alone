package com.payangar.amialone.gametest;

import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionAttribute;
import com.payangar.amialone.companion.CompanionAttributeGroup;
import com.payangar.amialone.companion.CompanionAttributeValues;
import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.Companions;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
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
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** The companion screen's attributes: their data registries, and the values the server sends. */
public class CompanionAttributesServerGameTest {
    private static final String KEY = "container.am_i_alone.companion.attributes.";

    /** A shipped entry, named after its attribute: its group, its order, its format, when it is shown. */
    private record Shipped(String attribute, String group, int order, String format, String shown) {}

    private static final List<Shipped> SHIPPED = List.of(
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
        helper.assertValueEqual(
                names, SHIPPED.stream().map(Shipped::attribute).collect(Collectors.toSet()), "the shipped entries");
        for (Shipped shipped : SHIPPED) {
            CompanionAttribute entry = attributes.getValue(Identifier.fromNamespaceAndPath("am_i_alone", shipped.attribute()));
            String name = shipped.attribute();
            helper.assertTrue(entry.resolve().isPresent(), name + " does not resolve to an attribute");
            helper.assertValueEqual(entry.attribute().identifier(), Identifier.withDefaultNamespace(name), name + "'s attribute");
            helper.assertValueEqual(entry.group().unwrapKey().orElseThrow().identifier(),
                    Identifier.fromNamespaceAndPath("am_i_alone", shipped.group()), name + "'s group");
            helper.assertValueEqual(entry.order(), shipped.order(), name + "'s order");
            helper.assertValueEqual(
                    entry.label(), Optional.of(Component.translatable(KEY + "label." + name)), name + "'s label");
            helper.assertValueEqual(entry.format().getSerializedName(), shipped.format(), name + "'s format");
            helper.assertValueEqual(entry.shown().getSerializedName(), shipped.shown(), name + "'s visibility");
        }
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
