package com.payangar.amialone.debug;

import com.payangar.amialone.companion.Companion;
import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.companion.Companions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The companion debug stick: scaffolding that tests what has no interaction in the game yet, for
 * operators only (the gamemaster permission, without vanilla's creative requirement). Right-click
 * on a companion opens its inventory screen.
 */
public class DebugStickItem extends Item {
    public DebugStickItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Opens the companion's inventory screen on the server, which decides. The client gives the
     * same answer: it knows its gamemaster permission (the server sends it with the player's
     * permission level), and knows a companion by its synced identity, since it builds companions
     * as players. A success stops it from going on to use the stick; anyone else's click passes, as
     * with a plain stick.
     */
    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!target.hasAttached(Companions.IDENTITY)
                || !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && target instanceof Companion companion) {
            CompanionMenu.open(serverPlayer, companion);
        }
        return InteractionResult.SUCCESS;
    }
}
