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
        super(100, 100, 150, 20, io.github.kituin.chatimage.animation.Playback.manualFps, 1, 60);
        this.updateMessage();
        this.tip = Tooltip.create(createTranslatableComponent("gif.chatimage.tooltip"));
        this.setTooltip(this.tip);
    }
    @Override
    public void applyValue() {
        // Round to an integer FPS: float normalization must not lower it each time settings open.
        this.position = (int) Math.round(this.min + Math.clamp(this.value, 0.0, 1.0) * (this.max - this.min));
    }
    @Override
    protected void updateMessage() {
        this.setMessage(optionNameValue(createTranslatableComponent("gif.chatimage.gui"), createLiteralComponent(String.valueOf(this.position))));
        io.github.kituin.chatimage.animation.Playback.manualFps = this.position;
        io.github.kituin.chatimage.animation.Playback.save();
    }

}
