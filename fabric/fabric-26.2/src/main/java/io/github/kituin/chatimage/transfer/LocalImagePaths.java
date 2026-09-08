package io.github.kituin.chatimage.transfer;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

/** Converts native paths to escaped CICode URLs, and accepts legacy Windows paste URLs. */
public final class LocalImagePaths {
    private static final java.util.LinkedHashMap<String, Path> pending = new java.util.LinkedHashMap<>();
    public static synchronized String attachment(Path path) {
        while (pending.size() >= 64) pending.remove(pending.keySet().iterator().next());
        String url = "file:///chatimage-pending/" + java.util.UUID.randomUUID();
        pending.put(url, path.toAbsolutePath()); return url;
    }
    public static String shorten(String text) {
        var matcher = java.util.regex.Pattern.compile("file:/+[^,\\]\\s]+").matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(attachment(parse(matcher.group()))));
        matcher.appendTail(result); return result.toString();
    }
    public static String toUrl(Path path) {
        // A comma is legal in a URI but is a CICode field separator.
        return path.toAbsolutePath().toUri().toASCIIString().replace(",", "%2C");
    }
    public static URI normalizeUri(String value) {
        if (!value.contains("\\")) return URI.create(value);
        String path = value.substring("file:".length()).replace('\\', '/');
        // Old Windows paste code concatenated file:/// with a raw drive path.
        if (path.matches("/+[A-Za-z]:/.*")) path = path.replaceFirst("^/+", "/");
        try { return new URI("file", "", path, null); }
        catch (URISyntaxException e) { throw new IllegalArgumentException("Invalid local image URL", e); }
    }
    public static synchronized Path parse(String value) {
        if (value.startsWith("file:///chatimage-pending/")) {
            Path path = pending.get(value);
            if (path == null) throw new IllegalArgumentException("Expired local attachment");
            return path;
        }
        return value.startsWith("file:") ? Path.of(normalizeUri(value)) : Path.of(value);
    }
}
