package com.payangar.amialone.gametest;

import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.Companions;
import com.payangar.amialone.debug.Debug;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** The companion debug stick: it opens a companion's inventory for an operator only. */
public class DebugStickServerGameTest {
    @GameTest
    public void opensTheInventoryForAnOperatorOnly(GameTestHelper helper) {
        PlayerList players = helper.getLevel().getServer().getPlayerList();
        Companion companion = (Companion) helper.spawn(Companions.TYPE, 1, 2, 1);
        ServerPlayer operator = CompanionInventoryServerGameTest.connectPlayer(helper, "operator");
        ServerPlayer player = CompanionInventoryServerGameTest.connectPlayer(helper, "player");
        // The test server gives operators no permission by default.
        players.op(operator.nameAndId(), Optional.of(LevelBasedPermissionSet.GAMEMASTER), Optional.empty());
        // Read before the players leave, which closes their menus.
        InteractionResult operatorResult;
        InteractionResult playerResult;
        String operatorMenu;
        String playerMenu;
        boolean operatorOpened;
        boolean playerOpened;
        try {
            operatorResult = rightClick(helper, operator, companion);
            playerResult = rightClick(helper, player, companion);
            operatorMenu = String.valueOf(operator.containerMenu);
            playerMenu = String.valueOf(player.containerMenu);
            operatorOpened = operator.containerMenu instanceof CompanionMenu menu && menu.companion() == companion;
            playerOpened = player.containerMenu != player.inventoryMenu;
        } finally {
            players.deop(operator.nameAndId());
            players.remove(operator);
            players.remove(player);
        }
        helper.assertTrue(operatorResult.consumesAction(), "the stick did not take an operator's click: " + operatorResult);
        helper.assertTrue(operatorOpened, "the operator's screen is not the companion's inventory: " + operatorMenu);
        helper.assertValueEqual(playerResult, InteractionResult.PASS, "a player's click");
        helper.assertTrue(!playerOpened, "a player without the permission opened a menu: " + playerMenu);
        helper.succeed();
    }

    private static InteractionResult rightClick(GameTestHelper helper, ServerPlayer player, Companion companion) {
        player.snapTo(helper.absoluteVec(new Vec3(1.5, 2.0, 3.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Debug.DEBUG_STICK));
        return player.interactOn(companion, InteractionHand.MAIN_HAND, companion.position());
    }
}
