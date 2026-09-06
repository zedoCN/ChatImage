package io.github.kituin.chatimage.mixin;

import com.google.common.collect.Lists;
import io.github.kituin.chatimage.tool.ChatImageStyle;
import io.github.kituin.ChatImageCode.ChatImageBoolean;
import io.github.kituin.ChatImageCode.ChatImageCode;
import io.github.kituin.ChatImageCode.ChatImageCodeTool;
import net.minecraft.network.chat.HoverEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;

import static io.github.kituin.ChatImageCode.ChatImageCode.pattern;
import static io.github.kituin.ChatImageCode.ChatImageCodeInstance.LOGGER;
import static io.github.kituin.ChatImageCode.ChatImageCodeInstance.createBuilder;

import static io.github.kituin.chatimage.tool.SimpleUtil.*;

/**
 * 注入修改文本显示,自动将CICode转换为可鼠标悬浮格式文字
 *
 * @author kitUIN
 */
@Mixin(net.minecraft.client.gui.components.ChatComponent.class)
public class ChatComponentMixin {
    @Shadow
    @Final
    private net.minecraft.client.Minecraft minecraft;

    @ModifyVariable(at = @At("HEAD"),
            method = "addMessage",
            argsOnly = true)
    public net.minecraft.network.chat.Component addMessage(net.minecraft.network.chat.Component message) {
        if (io.github.kituin.chatimage.client.ChatImageClient.CONFIG.experimentalTextComponentCompatibility) {
            try {
                StringBuilder sb = new StringBuilder();
                net.minecraft.network.chat.Component temp = chatImage$flattenTree(message, sb, false);
                ChatImageBoolean allString = new ChatImageBoolean(true);
                ChatImageCodeTool.sliceMsg(sb.toString(), true, allString, (e) -> LOGGER.error("slice msg error: {}", e));
                if (!allString.isValue()) message = temp;
            } catch (Exception e) {
                LOGGER.warn("experimentalTextComponentCompatibility 转换失败:{}", e.getMessage());
            }
        }
        return chatimage$replaceMessage(message);
    }

    @Unique
    private net.minecraft.network.chat.ComponentContents chatImage$getContents(net.minecraft.network.chat.Component text) {
        return text.getContents();
    }

    @Unique
    private String chatImage$getText(
            net.minecraft.network.chat.ComponentContents text
    ) {
        if (text instanceof net.minecraft.network.chat.contents.PlainTextContents)
            return ((net.minecraft.network.chat.contents.PlainTextContents) text).text();
        return "";
    }

    @SuppressWarnings("t")
    @Unique
    private net.minecraft.network.chat.Component chatimage$replaceCode(net.minecraft.network.chat.Component text) {
        String checkedText;
        String key = "";
        net.minecraft.network.chat.MutableComponent player = null;
        boolean isSelf = false;
        net.minecraft.network.chat.MutableComponent originText = text.copy();
        originText.getSiblings().clear();
        net.minecraft.network.chat.Style style = text.getStyle();
        if (chatImage$getContents(text) instanceof net.minecraft.network.chat.contents.PlainTextContents) {
            checkedText = chatImage$getText((net.minecraft.network.chat.contents.PlainTextContents) chatImage$getContents(text));
        } else if (chatImage$getContents(text) instanceof net.minecraft.network.chat.contents.TranslatableContents) {
            net.minecraft.network.chat.contents.TranslatableContents ttc = (net.minecraft.network.chat.contents.TranslatableContents) chatImage$getContents(text);
            key = ttc.getKey();
            Object[] args = ttc.getArgs();
            if (ChatImageCodeTool.checkKey(key)) {
                player = (net.minecraft.network.chat.MutableComponent) args[0];
                isSelf = minecraft.player != null && chatImage$getContents(player).toString().equals(chatImage$getContents(minecraft.player.getName()).toString());
                if (args[1] instanceof String) {
                    checkedText = (String) args[1];
                } else {
                    net.minecraft.network.chat.MutableComponent contents = (net.minecraft.network.chat.MutableComponent) args[1];
                    if (chatImage$getContents(contents) instanceof net.minecraft.network.chat.contents.PlainTextContents) {
                        checkedText = chatImage$getText((net.minecraft.network.chat.contents.PlainTextContents) chatImage$getContents(contents));
                    } else {
                        checkedText = chatImage$getContents(contents).toString();
                    }
                }
            } else {
                List<Object> argTexts = Lists.newArrayList();
                for (Object arg : args) {
                    argTexts.add(arg instanceof net.minecraft.network.chat.Component component ? this.chatimage$replaceMessage(component) : arg);
                }
                return createTranslatableComponent(key, argTexts.toArray()).setStyle(style);
            }
        } else {
            checkedText = chatImage$getContents(text).toString();
        }

        if (io.github.kituin.chatimage.client.ChatImageClient.CONFIG.cqCode)
            checkedText = ChatImageCodeTool.checkCQCode(checkedText);

        ChatImageBoolean allString = new ChatImageBoolean(true);

        List<Object> texts = ChatImageCodeTool.sliceMsg(checkedText, isSelf, allString, (e) -> LOGGER.error("slice msg error: {}", e));
        if (io.github.kituin.chatimage.client.ChatImageClient.CONFIG.checkImageUri)
            io.github.kituin.chatimage.tool.ImageUrls.replace(texts, isSelf, allString);

        if (allString.isValue()) {
            if (style.getHoverEvent() instanceof ChatImageStyle.ShowImage(ChatImageCode action)) {
                action.retry();
                originText.setStyle(ChatImageStyle.clickableStyle(style, action));
            }
            try {
                if (style.getHoverEvent() != null &&
                        style.getHoverEvent() instanceof HoverEvent.ShowText(net.minecraft.network.chat.Component showText) &&
                        chatImage$getContents(showText) instanceof net.minecraft.network.chat.contents.PlainTextContents) {

                    String originalCode = chatImage$getText((net.minecraft.network.chat.contents.PlainTextContents) chatImage$getContents(showText));
                    Matcher matcher = pattern.matcher(originalCode);
                    if (matcher.find()) {
                        originText.setStyle(
                                style.withHoverEvent(
                                        new ChatImageStyle.ShowImage(
                                                createBuilder()
                                                        .fromCode(originalCode)
                                                        .setIsSelf(isSelf)
                                                        .build())));
                    }
                }
            } catch (Exception e) {
                LOGGER.error("返回原样失败:{}", e);
            }
            return originText;
        }
        net.minecraft.network.chat.MutableComponent res = createLiteralComponent("");
        ChatImageCodeTool.buildMsg(texts,
                (obj) -> res.append(createLiteralComponent(obj).setStyle(style)),
                (obj) -> res.append(ChatImageStyle.messageFromCode(obj))
        );
        return player == null ? res : createTranslatableComponent(key, player, res).setStyle(style);
    }

    @SuppressWarnings("t")
    @Unique
    private net.minecraft.network.chat.Component chatImage$flattenTree(Object originNode, StringBuilder mergedText, boolean openUrlStyle) {
        if (originNode instanceof String) {
            return createLiteralComponent((String) originNode);
        } else if (originNode instanceof Integer) {
            return createLiteralComponent(originNode.toString());
        }
        net.minecraft.network.chat.Component node = (net.minecraft.network.chat.Component) originNode;
        net.minecraft.network.chat.Style tempStyle = node.getStyle();
        if (chatImage$getContents(node) instanceof net.minecraft.network.chat.contents.TranslatableContents) {
            net.minecraft.network.chat.contents.TranslatableContents ttc = (net.minecraft.network.chat.contents.TranslatableContents) chatImage$getContents(node);
            Object[] args = ttc.getArgs();
            List<Object> argsNew = Lists.newArrayList();
            for (Object arg : args) {
                argsNew.add(chatImage$flattenTree(arg, mergedText, false));
            }
            return createTranslatableComponent(ttc.getKey(), argsNew.toArray()).setStyle(tempStyle);
        } else {
            String t = chatImage$getText(chatImage$getContents(node));
            mergedText.append(t);
            if (node.getSiblings().isEmpty()) return node;
            net.minecraft.network.chat.MutableComponent res = null;
            List<net.minecraft.network.chat.Component> children = Lists.newArrayList();
            StringBuilder childSb = new StringBuilder(t);
            for (int i = 0; i < node.getSiblings().size(); i++) {
                net.minecraft.network.chat.Component child_ = node.getSiblings().get(i);
                net.minecraft.network.chat.Component child = chatImage$flattenTree(child_, mergedText, (child_.getStyle().getClickEvent() != null &&
                        child_.getStyle().getClickEvent().action() == net.minecraft.network.chat.ClickEvent.Action.OPEN_URL));
                if (child == null) continue;
                net.minecraft.network.chat.Style childStyle = child.getStyle();
                if (tempStyle == null) tempStyle = childStyle;
                boolean isLiteral = chatImage$getContents(child) instanceof net.minecraft.network.chat.contents.PlainTextContents && !Objects.equals(chatImage$getText(chatImage$getContents(child)), "");
                boolean check = isLiteral &&
                        (chatImage$isSame(childStyle, tempStyle) || openUrlStyle || (childStyle.getClickEvent() != null &&
                                childStyle.getClickEvent().action() == net.minecraft.network.chat.ClickEvent.Action.OPEN_URL));
                if (check) {
                    childSb.append(chatImage$getText(chatImage$getContents(child)));
                    if (child.getSiblings().isEmpty() && i != node.getSiblings().size() - 1) continue;
                }
                net.minecraft.network.chat.MutableComponent tempText = createLiteralComponent(childSb.toString()).setStyle(tempStyle);
                if (res == null) res = tempText;
                else children.add(tempText);
                childSb = new StringBuilder();
                tempStyle = null;
                if (!check) children.add(child);
            }
            for (net.minecraft.network.chat.Component child : children) {
                res.append(child);
            }
            return res;
        }
    }

    @Unique
    private boolean chatImage$isSame(net.minecraft.network.chat.Style childStyle, net.minecraft.network.chat.Style tempStyle) {
        if (childStyle == null || tempStyle == null) return false;
        return childStyle.isBold() == tempStyle.isBold() &&
                Objects.equals(childStyle.getColor(), tempStyle.getColor()) &&
                childStyle.isItalic() == tempStyle.isItalic() &&
                childStyle.isObfuscated() == tempStyle.isObfuscated() &&
                childStyle.isStrikethrough() == tempStyle.isStrikethrough() &&
                childStyle.isUnderlined() == tempStyle.isUnderlined() &&
                Objects.equals(childStyle.getClickEvent(), tempStyle.getClickEvent()) &&
                Objects.equals(childStyle.getHoverEvent(), tempStyle.getHoverEvent()) &&
                Objects.equals(childStyle.getInsertion(), tempStyle.getInsertion()) &&
                Objects.equals(childStyle.getFont(), tempStyle.getFont());
    }

    @Unique
    private net.minecraft.network.chat.Component chatimage$replaceMessage(net.minecraft.network.chat.Component message) {
        try {
            net.minecraft.network.chat.MutableComponent res = (net.minecraft.network.chat.MutableComponent) chatimage$replaceCode(message);
            for (net.minecraft.network.chat.Component t : message.getSiblings()) {
                res.append(chatimage$replaceMessage(t));
            }
            return res;
        } catch (Exception e) {
            LOGGER.warn("识别失败:{}", e.getMessage());
            return message;
        }
    }
}
