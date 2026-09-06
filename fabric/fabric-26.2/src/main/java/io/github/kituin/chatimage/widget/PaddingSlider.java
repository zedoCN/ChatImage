package io.github.kituin.chatimage.widget;

import io.github.kituin.ChatImageCode.ChatImageConfig;
import net.minecraft.client.gui.components.Tooltip;

import net.minecraft.network.chat.Component;

import static io.github.kituin.chatimage.client.ChatImageClient.CONFIG;
import static io.github.kituin.chatimage.tool.SimpleUtil.optionNameValue;
import static io.github.kituin.chatimage.tool.SimpleUtil.createLiteralComponent;


public class PaddingSlider extends SettingSliderWidget {
    protected final Component title;
    protected final PaddingType paddingType;


    public PaddingSlider(Component title, int value, float min, float max, PaddingType paddingType) {
        super(100, 100, 150, 20, value, min, max);
        this.title = title;
        this.paddingType = paddingType;
        this.updateMessage();
        this.tooltip();
}

    @Override
    protected void updateMessage() {
        this.setMessage(optionNameValue(title, createLiteralComponent(String.valueOf(this.position))));
        switch (paddingType) {
            case TOP:
                CONFIG.paddingTop = this.position;
                break;
            case BOTTOM:
                CONFIG.paddingBottom = this.position;
                break;
            case LEFT:
                CONFIG.paddingLeft = this.position;
                break;
            case RIGHT:
                CONFIG.paddingRight = this.position;
                break;
            default:
                return;
        }
        ChatImageConfig.saveConfig(CONFIG);
    }
    private void tooltip() {
        Component text;
        switch (paddingType) {
            case TOP:
                text = Component.translatable("top.padding.chatimage.tooltip");
                break;
            case BOTTOM:
                text = Component.translatable("bottom.padding.chatimage.tooltip");
                break;
            case LEFT:
                text = Component.translatable("left.padding.chatimage.tooltip");
                break;
            case RIGHT:
                text = Component.translatable("right.padding.chatimage.tooltip");
                break;
            default:
                return;
        }
        this.tip = Tooltip.create(text);
        this.setTooltip(this.tip);
    }
    public enum PaddingType {
        LEFT, RIGHT, TOP, BOTTOM

    }
}
