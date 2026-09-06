package io.github.kituin.chatimage.widget;

import io.github.kituin.ChatImageCode.ChatImageConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Tooltip;

import static io.github.kituin.chatimage.client.ChatImageClient.CONFIG;
import static io.github.kituin.chatimage.tool.SimpleUtil.*;

public class LimitSlider extends SettingSliderWidget {
    protected final Component title;
    protected final LimitType limitType;
    public LimitSlider(Component title, int value, float min, float max, LimitType limitType) {
        super(100, 100, 150, 20, value, min, max);
        this.title = title;
        this.limitType = limitType;
        this.updateMessage();
        this.tooltip();
    }
    @Override
    protected void updateMessage() {
        switch (limitType) {
            case WIDTH:
                this.setMessage(optionNameValue(title, CONFIG.limitWidth == 0 ? createTranslatableComponent("default.chatimage.gui") : createLiteralComponent(String.valueOf(this.position))));
                CONFIG.limitWidth = this.position;
                break;
            case HEIGHT:
                this.setMessage(optionNameValue(title, CONFIG.limitHeight == 0 ? createTranslatableComponent("default.chatimage.gui") : createLiteralComponent(String.valueOf(this.position))));
                CONFIG.limitHeight = this.position;
                break;
            default:
                return;
        }
        ChatImageConfig.saveConfig(CONFIG);
    }
    private void tooltip() {
        Component text;
        switch (limitType) {
            case WIDTH:
                text = Component.translatable("width.limit.chatimage.tooltip");
                break;
            case HEIGHT:
                text = Component.translatable("height.limit.chatimage.tooltip");
                break;
            default:
                return;
        }
        this.tip = Tooltip.create(text);
        this.setTooltip(this.tip);
    }
    public enum LimitType {
        WIDTH, HEIGHT

    }
}
