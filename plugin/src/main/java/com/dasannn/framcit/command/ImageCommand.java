package com.dasannn.framcit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class ImageCommand {

    private ImageCommand() {
    }

    public static void register(Commands commands) {
        LiteralCommandNode<CommandSourceStack> node = Commands.literal("imagen")
            .requires(source -> source.getSender().hasPermission("framcit.imagen"))
            .executes(ctx -> {
                CommandSender sender = ctx.getSource().getSender();
                sender.sendMessage(Component.text("Uso: /imagen <url>"));
                return Command.SINGLE_SUCCESS;
            })
            .then(Commands.argument("url", StringArgumentType.greedyString())
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    String url = StringArgumentType.getString(ctx, "url");
                    sender.sendMessage(Component.text("[FraMCit] Descargando imagen: " + url));
                    return Command.SINGLE_SUCCESS;
                })
            )
            .build();

        commands.register(node, "Convierte una URL de imagen o GIF en un mapa", List.of("img", "image"));
    }
}
