package io.github.kituin.chatimage.paste;

import io.github.kituin.chatimage.ChatImage;
import io.github.kituin.chatimage.client.ChatImageClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/** Read AppKit's pasteboard in a helper process; never initialize AWT on GLFW's macOS main thread. */
public class MacPasteCompat implements IPasteCompat {
    private static final String SCRIPT = """
        ObjC.import('AppKit');
        function run(argv) {
            var pasteboard = $.NSPasteboard.generalPasteboard;
            var data = pasteboard.dataForType('public.png');
            if (!data || data.isNil()) {
                var tiff = pasteboard.dataForType('public.tiff');
                if (!tiff || tiff.isNil()) return '';
                var bitmap = $.NSBitmapImageRep.imageRepWithData(tiff);
                data = bitmap.representationUsingTypeProperties($.NSBitmapImageFileTypePNG, $({}));
            }
            if (!data || data.isNil() || data.length > Number(argv[1])) return '';
            return data.writeToFileAtomically($(argv[0]), true) ? 'ok' : '';
        }
        """;

    @Override
    public String doPaste() {
        Path temporary = null;
        Process process = null;
        try {
            Path cache = Path.of(ChatImageClient.CONFIG.cachePath).toAbsolutePath();
            Files.createDirectories(cache);
            temporary = Files.createTempFile(cache, "clipboard-", ".png");
            process = new ProcessBuilder("/usr/bin/osascript", "-l", "JavaScript", "-e", SCRIPT,
                    temporary.toString(), "20971520").redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) return null;
            if (process.exitValue() != 0 || !new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim().equals("ok")) return null;
            byte[] bytes = Files.readAllBytes(temporary);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            Path image = cache.resolve(hash + ".png");
            if (!Files.exists(image)) Files.move(temporary, image);
            return "[[CICode,url=" + image.toUri() + "]]";
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            ChatImage.LOGGER.debug("Could not read a clipboard image", exception);
            return null;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (java.io.IOException ignored) { }
        }
    }
}
