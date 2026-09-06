package io.github.kituin.chatimage.widget;

import io.github.kituin.ChatImageCode.ChatImageConfig;
import io.github.kituin.chatimage.client.ChatImageClient;
import net.minecraft.client.gui.components.Tooltip;
import static io.github.kituin.chatimage.client.ChatImageClient.CONFIG;
import static io.github.kituin.chatimage.tool.SimpleUtil.*;
/**
 * @author kitUIN
 */
public class GifSlider extends SettingSliderWidget {
    public GifSlider() {
        super(100, 100, 150, 20, CONFIG.gifSpeed, 1, 20);
        this.updateMessage();
        this.tip = Tooltip.create(createTranslatableComponent("gif.chatimage.tooltip"));
        this.setTooltip(this.tip);
    }
    @Override
    protected void updateMessage() {
        this.setMessage(optionNameValue(createTranslatableComponent("gif.chatimage.gui"), createLiteralComponent(String.valueOf(this.position))));
        ChatImageClient.CONFIG.gifSpeed = this.position;
        ChatImageConfig.saveConfig(ChatImageClient.CONFIG);
    }

}
