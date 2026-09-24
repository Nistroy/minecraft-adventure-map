package io.github.nistroy.adventuremap.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /carte} : récupérer sa carte perdue. Une seule à la fois ; la progression, elle, ne se perd jamais. */
public final class MapCommand {
    private MapCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, MapService service) {
        dispatcher.register(Commands.literal("carte").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (service.hasMap(player)) {
                context.getSource().sendFailure(Component.literal("Tu as déjà ta carte dans ton inventaire."));
                return 0;
            }
            service.giveMap(player);
            context.getSource().sendSuccess(() -> Component.literal("Voilà ta carte de l'aventurier."), false);
            return 1;
        }));
    }
}
