package io.github.kituin.chatimage.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.kituin.ChatImageCode.ChatImageCode;
import io.github.kituin.ChatImageCode.ChatImageCodeInstance;
import io.github.kituin.ChatImageCode.ChatImageConfig;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;

import static io.github.kituin.ChatImageCode.ChatImageCodeInstance.LOGGER;
import static io.github.kituin.chatimage.client.ChatImageClient.CONFIG;
import static io.github.kituin.chatimage.tool.SimpleUtil.createTranslatableComponent;

public class ChatImageCommand {
    public static int sendChatImage(CommandContext<FabricClientCommandSource> context) {
        String url = StringArgumentType.getString(context, "url");
        if (url.startsWith("file:")) {
            String requestedName;
            try { requestedName = StringArgumentType.getString(context, "name"); }
            catch (IllegalArgumentException ignored) { requestedName = ChatImageCode.DEFAULT_NAME; }
            final String imageName = requestedName;
            io.github.kituin.chatimage.transfer.ClientTransfers.upload(url, reference ->
                context.getSource().getPlayer().connection.sendChat(ChatImageCodeInstance.createBuilder().setUrlForce(reference).setName(imageName).build().toString()));
            return Command.SINGLE_SUCCESS;
        }
        ChatImageCode.Builder builder = ChatImageCodeInstance.createBuilder().setUrlForce(url);
        try {
            String name = StringArgumentType.getString(context, "name");
            builder.setName(name);
        } catch (java.lang.IllegalArgumentException e) {
            LOGGER.info("arg: `name` is omitted, use the default string");
        }
        context.getSource().getPlayer().connection.sendChat(builder.build().toString());
        return Command.SINGLE_SUCCESS;
    }

    public static int help(CommandContext<FabricClientCommandSource> context) {

        context.getSource().sendFeedback(
                getHelpText("/chatimage help", "", "help.chatimage.command")
                        .append(getHelpText("/chatimage send ", "<name> <url>", "send.chatimage.command"))
                        .append(getHelpText("/chatimage url ", "<url>", "url.chatimage.command"))
                        .append(getHelpText("/chatimage upload ", "<path|clipboard>", "transfer.chatimage.help"))
                        .append(getHelpText("/chatimage reload ", "", "reload.chatimage.command"))
        );
        return Command.SINGLE_SUCCESS;
    }

    public static int reloadConfig(CommandContext<FabricClientCommandSource> context) {
        CONFIG = ChatImageConfig.loadConfig();
        context.getSource().sendFeedback(createTranslatableComponent("success.reload.chatimage.command").setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)));
        return Command.SINGLE_SUCCESS;
    }

    private static MutableComponent getHelpText(String help, String arg, String usage) {
        String all = help + arg;
        StringBuilder sb = new StringBuilder(all);
        if (all.length() <= 35) {
            for (int i = 0; i < 35 - all.length(); i++) {
                sb.append(" ");
            }
        }
        MutableComponent text = (MutableComponent) Component.literal(sb.toString());
        MutableComponent info = Component.translatable(usage);
        return text.setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withClickEvent(
                new ClickEvent.SuggestCommand(help)
        )).append(info).append(Component.literal("\n"));
    }

}
