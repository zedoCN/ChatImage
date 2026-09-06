package io.github.kituin.chatimage.tool;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.*;

public class SimpleUtil {

    public static void setScreen(Minecraft minecraft, Screen screen) {
        minecraft.gui.setScreen(screen);
    }
    public static MutableComponent createTranslatableComponent(String text){
        return Component.translatable(text);
    }
    public static MutableComponent createTranslatableComponent(String key, Object... args){
        return Component.translatable(key, args);
    }


    public static MutableComponent createLiteralComponent(String text){
        return Component.literal(text);
    }
    public static MutableComponent optionNameValue(Component text, Component value) {
        return net.minecraft.network.chat.CommonComponents.optionNameValue(text,value);
    }

}
