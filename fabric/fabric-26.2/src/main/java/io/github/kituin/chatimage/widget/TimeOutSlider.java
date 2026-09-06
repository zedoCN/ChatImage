package io.github.kituin.chatimage.widget;

import io.github.kituin.ChatImageCode.ChatImageConfig;
import net.minecraft.client.gui.components.Tooltip;
import static io.github.kituin.chatimage.client.ChatImageClient.CONFIG;
import static io.github.kituin.chatimage.tool.SimpleUtil.*;

public class TimeOutSlider extends SettingSliderWidget {

public TimeOutSlider() {
    super(100, 100, 150, 20, CONFIG.timeout, 3, 60);
    this.updateMessage();
    this.tip = Tooltip.create(createTranslatableComponent("timeout.chatimage.tooltip"));
    this.setTooltip(this.tip);
}
    @Override
    protected void updateMessage() {
        this.setMessage(optionNameValue(
                createTranslatableComponent("timeout.chatimage.gui"),
                createLiteralComponent(String.valueOf(this.position)))
                .append(" ")
                .append(createTranslatableComponent("seconds.chatimage.gui")));
        CONFIG.timeout = this.position;
        ChatImageConfig.saveConfig(CONFIG);
    }
}
