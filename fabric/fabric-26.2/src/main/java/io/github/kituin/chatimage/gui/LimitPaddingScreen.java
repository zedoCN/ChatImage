package io.github.kituin.chatimage.gui;

import io.github.kituin.chatimage.widget.LimitSlider;
import io.github.kituin.chatimage.widget.PaddingSlider;
import io.github.kituin.chatimage.client.ChatImageClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static io.github.kituin.chatimage.tool.SimpleUtil.createTranslatableComponent;
import static io.github.kituin.chatimage.tool.SimpleUtil.setScreen;
import static io.github.kituin.chatimage.widget.PaddingSlider.PaddingType.*;

@Environment(EnvType.CLIENT)
public class LimitPaddingScreen extends Screen {
    private final Screen parent;
    public LimitPaddingScreen(Screen screen) {
        super(createTranslatableComponent("padding.chatimage.gui"));
        this.parent = screen;
    }

    protected void init() {
        super.init();
        GridLayout gridWidget = new GridLayout();
        gridWidget.defaultCellSetting().paddingHorizontal(5).paddingBottom(4).alignHorizontallyCenter();
        GridLayout.RowHelper adder = gridWidget.createRowHelper(2);
        adder.addChild(new PaddingSlider(createTranslatableComponent("left.padding.chatimage.gui"),
                ChatImageClient.CONFIG.paddingLeft, 0F, (float) this.width / 2, PaddingSlider.PaddingType.LEFT));
        adder.addChild(new PaddingSlider(createTranslatableComponent("right.padding.chatimage.gui"),
                ChatImageClient.CONFIG.paddingRight, 0F, (float) this.width / 2, PaddingSlider.PaddingType.RIGHT));
        adder.addChild(new PaddingSlider(createTranslatableComponent("top.padding.chatimage.gui"),
                ChatImageClient.CONFIG.paddingTop, 0F, (float) this.height / 2, PaddingSlider.PaddingType.TOP));
        adder.addChild(new PaddingSlider(createTranslatableComponent("bottom.padding.chatimage.gui"),
                ChatImageClient.CONFIG.paddingBottom, 0F, (float) this.height / 2, PaddingSlider.PaddingType.BOTTOM));
        adder.addChild(new LimitSlider(createTranslatableComponent("width.limit.chatimage.gui"),
                ChatImageClient.CONFIG.limitWidth, 1F, this.width, LimitSlider.LimitType.WIDTH));
        adder.addChild(new LimitSlider(createTranslatableComponent("height.limit.chatimage.gui"),
                ChatImageClient.CONFIG.limitHeight, 1F, this.height, LimitSlider.LimitType.HEIGHT));
        adder.addChild(Button.builder(createTranslatableComponent("gui.back"), (button) -> {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(this.parent);
            }
        }).build(), 2);
        gridWidget.arrangeElements();
        FrameLayout.alignInRectangle(gridWidget, 0, this.height / 3 - 12, this.width, this.height, 0.5F, 0.0F);
        gridWidget.visitWidgets(this::addRenderableWidget);



    }
    public void extractRenderState(GuiGraphicsExtractor matrices, int mouseX, int mouseY, float delta) {
        super.extractRenderState(matrices, mouseX, mouseY, delta);
        matrices.centeredText(this.font, title.getString(), this.width / 2, this.height / 4 - 16, 0xffffcccc);
    }

    public <T extends GuiEventListener & Renderable & NarratableEntry> T addDrawableWeight(T element)
    {
        return this.addRenderableWidget(element);
    }
}
