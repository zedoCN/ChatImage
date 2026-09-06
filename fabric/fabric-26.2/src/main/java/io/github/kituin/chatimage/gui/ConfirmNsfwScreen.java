package io.github.kituin.chatimage.gui;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

/** Let the 26.2 layout handle wrapping, small windows and confirmation buttons. */
public class ConfirmNsfwScreen extends ConfirmScreen {
    public ConfirmNsfwScreen(BooleanConsumer callback, String link) {
        super(callback, Component.translatable("nsfw.chatimage.open"),
                Component.literal(link).append("\n\n").append(Component.translatable("nsfw.chatimage.warning")),
                CommonComponents.GUI_YES, CommonComponents.GUI_NO);
    }
}
