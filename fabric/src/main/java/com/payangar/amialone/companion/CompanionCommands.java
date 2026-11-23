package com.payangar.amialone.companion;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /companion spawn <name>}: creates a companion owned by the player who runs it, next to
 * them. {@code /companion dismiss <name>}: removes a companion for good. Both need the game master
 * permission, like {@code /summon}.
 */
public final class CompanionCommands {
    private CompanionCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Companions companions) {
        dispatcher.register(Commands.literal("companion")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> spawn(
                                        context.getSource(),
                                        companions,
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("dismiss")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        companions.roster(context.getSource().getServer()).companions().stream()
                                                .map(CompanionRecord::name),
                                        builder))
                                .executes(context -> dismiss(
                                        context.getSource(),
                                        companions,
                                        StringArgumentType.getString(context, "name"))))));
    }

    private static int spawn(CommandSourceStack source, Companions companions, String name)
            throws CommandSyntaxException {
        ServerPlayer owner = source.getPlayerOrException();
        try {
            CompanionPlayer body = companions.spawn(owner, name);
            source.sendSuccess(
                    () -> Component.translatable("am_i_alone.companion.spawned", body.getDisplayName()), true);
            return 1;
        } catch (CompanionException e) {
            throw new SimpleCommandExceptionType(e.reason()).create();
        }
    }

    private static int dismiss(CommandSourceStack source, Companions companions, String name)
            throws CommandSyntaxException {
        CompanionRecord record = companions.roster(source.getServer()).byName(name)
                .orElseThrow(() -> new SimpleCommandExceptionType(
                                Component.translatable("am_i_alone.companion.unknown", name))
                        .create());
        companions.dismiss(source.getServer(), record);
        source.sendSuccess(() -> Component.translatable("am_i_alone.companion.dismissed", record.name()), true);
        return 1;
    }
}
