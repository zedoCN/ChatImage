package io.github.kituin.chatimage.gui;

import io.github.kituin.ChatImageCode.ChatImageConfig;
import io.github.kituin.chatimage.widget.GifSlider;
import io.github.kituin.chatimage.widget.TimeOutSlider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.FrameLayout;
import static io.github.kituin.chatimage.client.ChatImageClient.CONFIG;
import static io.github.kituin.chatimage.tool.SimpleUtil.*;

@Environment(EnvType.CLIENT)
public class ConfigScreen extends Screen {
    private final Screen parent;

    public ConfigScreen(Screen screen) {
        super(createTranslatableComponent("config.chatimage.category"));
        parent = screen;
    }

    public ConfigScreen() {
        this(null);
    }
    public void extractRenderState(GuiGraphicsExtractor matrices, int mouseX, int mouseY, float delta) {
        super.extractRenderState(matrices, mouseX, mouseY, delta);
        matrices.centeredText(this.font, title.getString(), this.width / 2, this.height / 4 - 16, 0xffffcccc);
    }
    protected void init() {
        super.init();
        GridLayout gridWidget = new GridLayout();
        gridWidget.defaultCellSetting().paddingHorizontal(5).paddingBottom(4).alignHorizontallyCenter();
        GridLayout.RowHelper adder = gridWidget.createRowHelper(2);
        adder.addChild(Button.builder(getNsfw(CONFIG.nsfw), (button) -> {
            CONFIG.nsfw = !CONFIG.nsfw;
            button.setMessage(getNsfw(CONFIG.nsfw));
            ChatImageConfig.saveConfig(CONFIG);
        }).tooltip(Tooltip.create(Component.translatable("nsfw.chatimage.tooltip"))).build());
        adder.addChild(Button.builder(getEnable("animation.chatimage.auto", io.github.kituin.chatimage.animation.Playback.automatic), button -> {
            io.github.kituin.chatimage.animation.Playback.automatic = !io.github.kituin.chatimage.animation.Playback.automatic;
            io.github.kituin.chatimage.animation.Playback.save();
            button.setMessage(getEnable("animation.chatimage.auto", io.github.kituin.chatimage.animation.Playback.automatic));
        }).tooltip(Tooltip.create(Component.translatable("animation.chatimage.tooltip"))).build());
        adder.addChild(new GifSlider());
        adder.addChild(new TimeOutSlider());
        adder.addChild(Button.builder(Component.translatable("padding.chatimage.gui"), (button) -> {
            if (this.minecraft != null) {
                setScreen(this.minecraft, new LimitPaddingScreen(this));
            }
        }).tooltip(Tooltip.create(Component.translatable("padding.chatimage.tooltip"))).build());
        adder.addChild(Button.builder(getCq(CONFIG.cqCode), (button) -> {
            CONFIG.cqCode = !CONFIG.cqCode;
            button.setMessage(getCq(CONFIG.cqCode));
            ChatImageConfig.saveConfig(CONFIG);
        }).tooltip(Tooltip.create(Component.translatable("cq.chatimage.tooltip"))).build());
        adder.addChild(Button.builder(getUri(CONFIG.checkImageUri), (button) -> {
            CONFIG.checkImageUri = !CONFIG.checkImageUri;
            button.setMessage(getUri(CONFIG.checkImageUri));
            ChatImageConfig.saveConfig(CONFIG);
        }).build());
        adder.addChild(Button.builder(getDrag(CONFIG.dragUseCicode), (button) -> {
            CONFIG.dragUseCicode = !CONFIG.dragUseCicode;
            button.setMessage(getDrag(CONFIG.dragUseCicode));
            ChatImageConfig.saveConfig(CONFIG);
        }).tooltip(Tooltip.create(Component.translatable("drag.chatimage.tooltip"))).build());
        adder.addChild(Button.builder(getDragImage(CONFIG.dragImage), (button) -> {
            CONFIG.dragImage = !CONFIG.dragImage;
            button.setMessage(getDragImage(CONFIG.dragImage));
            ChatImageConfig.saveConfig(CONFIG);
        }).tooltip(Tooltip.create(Component.translatable("image.drag.chatimage.tooltip"))).build());
        adder.addChild(Button.builder(getExperimentalTextComponentCompatibility(CONFIG.experimentalTextComponentCompatibility), (button) -> {
            CONFIG.experimentalTextComponentCompatibility = !CONFIG.experimentalTextComponentCompatibility;
            button.setMessage(getExperimentalTextComponentCompatibility(CONFIG.experimentalTextComponentCompatibility));
            ChatImageConfig.saveConfig(CONFIG);
        }).tooltip(Tooltip.create(Component.translatable("experimental.component.chatimage.tooltip"))).build());
        adder.addChild(Button.builder(Component.translatable("gui.back"), (button) -> {
            if (this.minecraft != null) {
                setScreen(this.minecraft, this.parent);
            }
        }).build(), 2);
        gridWidget.arrangeElements();
        FrameLayout.alignInRectangle(gridWidget, 0, this.height / 3 - 12, this.width, this.height, 0.5F, 0.0F);
        gridWidget.visitWidgets(this::addRenderableWidget);
    }

    private MutableComponent getCq(boolean enable) {
        return getEnable("cq.chatimage.gui", enable);
    }

    private MutableComponent getNsfw(boolean enable) {
        return getEnable("nsfw.chatimage.gui", !enable);
    }

    private MutableComponent getDrag(boolean enable) {
        return getEnable("drag.chatimage.gui", enable);
    }
    private MutableComponent getDragImage(boolean enable) {
        return getEnable("image.drag.chatimage.gui", enable);
    }
    private MutableComponent getExperimentalTextComponentCompatibility(boolean enable) {
        return getEnable("experimental.component.chatimage.gui", enable);
    }
    private MutableComponent getUri(boolean enable) {
        return getEnable("uri.chatimage.gui", enable);
    }

    public static MutableComponent getEnable(String key, boolean enable) {
        return optionNameValue(createTranslatableComponent(key), createTranslatableComponent((enable ? "open" : "close") + ".chatimage.common"));
    }

    public <T extends GuiEventListener & Renderable & NarratableEntry> T addDrawableWeight(T element)
    {
        return this.addRenderableWidget(element);
    }
}
