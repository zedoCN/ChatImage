package io.github.kituin.chatimage.widget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractSliderButton;

import net.minecraft.util.Mth;
import net.minecraft.client.gui.components.Tooltip;
import static io.github.kituin.chatimage.tool.SimpleUtil.createLiteralComponent;


@Environment(EnvType.CLIENT)
public abstract class SettingSliderWidget extends AbstractSliderButton {
    protected final double min;
    protected final double max;
    protected int position;

    protected Tooltip tip;
    public SettingSliderWidget(int x, int y, int width, int height, int value, float min, float max) {
        super(x, y, width, height, createLiteralComponent(""), 0.0);
        this.min = min;
        this.max = max;
        this.value = ((Mth.clamp((float) value, min, max) - min) / (max - min));
        this.applyValue();
    }

    public void applyValue() {
        this.position = (int) Mth.lerp(Mth.clamp(this.value, 0.0, 1.0), this.min, this.max);
    }



    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        super.onClick(click, doubled);
        this.setTooltip(null);
    }
    @Override
    public void onRelease(net.minecraft.client.input.MouseButtonEvent click) {
        super.onRelease(click);
        this.setTooltip(tip);
    }
}
