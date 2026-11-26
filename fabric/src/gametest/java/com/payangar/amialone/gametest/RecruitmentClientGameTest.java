package com.payangar.amialone.gametest;

import com.payangar.amialone.client.companion.CompanionScreen;
import com.payangar.amialone.client.companion.RecruitScreen;
import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionMenu.Panel;
import com.payangar.amialone.companion.CompanionOwner;
import com.payangar.amialone.companion.Companions;
import com.payangar.amialone.companion.RecruitMenu;
import com.payangar.amialone.companion.Recruitment.Payment;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A companion's recruitment seen from a client: its screen with the button, with its drawer out on
 * the attributes, then with the payment slot, each compared with a reference render of
 * src/gametest/resources/templates/ (recruit_screen.png, recruit_screen_attributes.png,
 * recruit_screen_slot.png), then with a price the companion's opinion lowered
 * (recruit_screen_discount.png); then Recruit, after which the companion's screen opens and the client
 * knows its owner and predicts the right-clicks from it.
 */
public class RecruitmentClientGameTest implements FabricClientGameTest {
    private static final int PRICE = 24;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            UUID id = world.getServer().computeOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                UUID companion = CompanionInventoryClientGameTest.summonEquipped(player);
                player.level().getEntity(companion).setAttached(Companions.PRICE, PRICE);
                player.getInventory().setItem(10, new ItemStack(Items.EMERALD, 30));
                return companion;
            });
            context.waitFor(client -> client.level != null
                    && client.level.getEntity(id) != null
                    && client.level.getEntity(id).getAttached(Companions.IDENTITY) != null);
            context.waitTicks(20);
            boolean compare = System.getProperty("mod.testProfile") == null;

            open(context, world, id, Payment.BUTTON);
            check(context.computeOnClient(client -> screen(client).recruitActive()), "Recruit is greyed with 30 emeralds");
            screenshot(context, compare, "recruit_screen");

            context.runOnClient(client -> screen(client).pressHandle(Panel.ATTRIBUTES));
            context.waitFor(client -> !screen(client).getMenu().attributeValues().isEmpty());
            context.waitTicks(5);
            screenshot(context, compare, "recruit_screen_attributes");
            context.runOnClient(client -> screen(client).pressHandle(Panel.ATTRIBUTES));
            context.runOnClient(client -> screen(client).onClose());
            context.waitFor(client -> !(client.gui.screen() instanceof RecruitScreen));
            waitForServerClose(context, world);

            // With an empty payment slot, Recruit is greyed whatever the inventory holds.
            open(context, world, id, Payment.SLOT);
            check(!context.computeOnClient(client -> screen(client).recruitActive()), "Recruit is active with an empty slot");
            screenshot(context, compare, "recruit_screen_slot");
            context.runOnClient(client -> screen(client).onClose());
            context.waitFor(client -> !(client.gui.screen() instanceof RecruitScreen));
            waitForServerClose(context, world);

            // Cured by the player: the companion thinks 100 of them, so 10 off at 0.1; the drawn
            // price is struck through, the price to pay beside it.
            world.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ((Companion) player.level().getEntity(id)).gossips().add(player.getUUID(), GossipType.MAJOR_POSITIVE, 20);
            });
            open(context, world, id, Payment.BUTTON);
            check(context.computeOnClient(client -> screen(client).getMenu().price()) == PRICE - 10, "the lowered price");
            screenshot(context, compare, "recruit_screen_discount");

            // Recruit: the companion's screen replaces this one, and the owner reaches the client.
            context.runOnClient(client -> screen(client).pressRecruit());
            context.waitForScreen(CompanionScreen.class);
            context.waitFor(client -> Companion.isOwner(client.level.getEntity(id), client.player));
            check(
                    context.computeOnClient(client -> client.player.getInventory().countItem(Items.EMERALD)) == 30 - (PRICE - 10),
                    "the client's emeralds after recruiting");
            check(
                    context.computeOnClient(client -> predicted(client, id)) instanceof InteractionResult.Success success
                            && success.shouldSwing(),
                    "the owner's right-click is not predicted as opening the screen");

            // Another player's companion: the client predicts that nothing happens.
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().level()
                    .getEntity(id)
                    .setAttached(Companions.OWNER, new CompanionOwner(UUID.randomUUID(), "someone_else")));
            context.waitFor(client -> !Companion.isOwner(client.level.getEntity(id), client.player));
            check(
                    context.computeOnClient(client -> predicted(client, id)) instanceof InteractionResult.Success success
                            && !success.shouldSwing(),
                    "another player's companion is not predicted as doing nothing");
        }
    }

    private static void open(ClientGameTestContext context, TestSingleplayerContext world, UUID id, Payment payment) {
        world.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            RecruitMenu.open(player, (Companion) player.level().getEntity(id), payment);
        });
        context.waitForScreen(RecruitScreen.class);
        // The 3D view follows the mouse: a fixed cursor, in the tabs' row, over nothing.
        context.getInput().setCursorPos(634, 46);
        context.waitTicks(5);
    }

    /** Waits for the server to close the menu, which it does once the client's packet arrives. */
    private static void waitForServerClose(ClientGameTestContext context, TestSingleplayerContext world) {
        for (int tick = 0; world.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().containerMenu instanceof RecruitMenu); tick++) {
            check(tick < 100, "the server did not close the menu");
            context.waitTick();
        }
    }

    /** What the client predicts for a right-click on the companion with an empty hand. */
    private static InteractionResult predicted(Minecraft client, UUID id) {
        Entity companion = client.level.getEntity(id);
        return companion.interact(client.player, InteractionHand.MAIN_HAND, companion.position());
    }

    private static void screenshot(ClientGameTestContext context, boolean compare, String name) {
        if (compare) {
            Screenshots.compare(context, name);
        }
    }

    private static void check(boolean condition, String failure) {
        if (!condition) {
            throw new AssertionError(failure);
        }
    }

    private static RecruitScreen screen(Minecraft client) {
        return (RecruitScreen) client.gui.screen();
    }
}
