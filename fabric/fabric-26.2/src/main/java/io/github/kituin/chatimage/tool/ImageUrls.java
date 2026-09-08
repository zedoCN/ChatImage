package io.github.kituin.chatimage.tool;

import io.github.kituin.ChatImageCode.ChatImageBoolean;
import io.github.kituin.ChatImageCode.ChatImageCodeInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Preserve complete image URLs, including explicit ports, when recognizing plain chat text. */
public final class ImageUrls {
    private ImageUrls() { }
    private static final Pattern IMAGE = Pattern.compile(
            "(?i)(https?://|file:///)?([\\w-]+(\\.[\\w-]+)*)(:[0-9]{1,5})?(\\/[^\\s]*)?\\.(png!thumbnail|bmp|png|jpe?g|gif|webp|ico)(\\?[^\\s<>\\[\\]]*)?");

    public static void replace(List<Object> parts, boolean isSelf, ChatImageBoolean allString) {
        List<Object> result = new ArrayList<>();
        for (Object part : parts) {
            if (!(part instanceof String text)) { result.add(part); continue; }
            var matcher = IMAGE.matcher(text);
            int end = 0;
            while (matcher.find()) {
                if (matcher.start() > end) result.add(text.substring(end, matcher.start()));
                result.add(ChatImageCodeInstance.createBuilder().setUrl(matcher.group()).setIsSelf(isSelf).build());
                allString.setValue(false);
                end = matcher.end();
            }
            if (end < text.length()) result.add(text.substring(end));
        }
        parts.clear();
        parts.addAll(result);
    }
}
