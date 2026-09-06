package io.github.kituin.chatimage.tool;

import io.github.kituin.ChatImageCode.ChatImageCode;
import io.github.kituin.ChatImageCode.exception.InvalidChatImageCodeException;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;

/**
 * @author kitUIN
 */
public class ChatImageStyle {
    public static final com.mojang.serialization.MapCodec<ChatImageCode> MAP_CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(obj -> obj.group(
        com.mojang.serialization.Codec.STRING.fieldOf("url").forGetter(ChatImageCode::getUrl),
        com.mojang.serialization.Codec.BOOL.optionalFieldOf("nsfw", false).forGetter(ChatImageCode::isNsfw)
    ).apply(obj, (url, nsfw) -> new ChatImageCode.Builder().setNsfw(nsfw).setUrlForce(url).build()));
    public static final com.mojang.serialization.Codec<ChatImageCode> CODEC = MAP_CODEC.codec();

    public static record ShowImage(ChatImageCode value) implements HoverEvent {
        public static final com.mojang.serialization.MapCodec<ShowImage> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(obj -> obj.group(
                ChatImageStyle.MAP_CODEC.forGetter(ShowImage::value)
        ).apply(obj, ShowImage::new));


        public ShowImage(ChatImageCode value) {
            this.value = value;
        }

        public Action action() {
            return Action.valueOf("SHOW_IMAGE");
        }

        public ChatImageCode value() {
            return this.value;
        }
    }


    /**
     * 文本 悬浮图片样式
     *
     * @param code {@link ChatImageCode}
     * @return 悬浮图片样式
     */
    public static Style getStyleFromCode(ChatImageCode code) {
        return getStyleFromCode(code, ChatFormatting.GREEN);
    }

    /**
     * 文本 悬浮图片样式
     *
     * @param code  {@link ChatImageCode}
     * @param color 颜色
     * @return 悬浮图片样式
     */
    public static Style getStyleFromCode(ChatImageCode code, ChatFormatting color) {
        Style style = Style.EMPTY.withHoverEvent(
                new ShowImage(code)
        );
        return clickableStyle(style.withColor(color), code);
    }

    public static Style clickableStyle(Style style, ChatImageCode code) {
        if (!code.isNsfw()) return style;
        return style.withClickEvent(new ClickEvent.Custom(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("chatimage", "confirm_nsfw"), java.util.Optional.empty()));
    }

    /**
     * 获取悬浮图片样式的Text消息
     *
     * @param code {@link ChatImageCode}
     * @return {@link MutableComponent}
     */
    public static MutableComponent messageFromCode(ChatImageCode code) {
        MutableComponent t = code.messageFromCode(
                Component::literal,
                Component::translatable,
                MutableComponent::append);
        Style style = ChatImageStyle.getStyleFromCode(code);
        return t.withStyle(style);
    }




}
