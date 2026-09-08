package io.github.kituin.chatimage.animation;

import io.github.kituin.ChatImageCode.*;
import io.github.kituin.chatimage.transfer.*;
import chatimage.com.madgag.gif.fmsware.GifDecoder;
import org.glavo.webp.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class AnimationDecoder {
    private static final ExecutorService WORKER = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> { var t = new Thread(r, "ChatImage animation"); t.setDaemon(true); return t; });
    public static boolean accepts(byte[] bytes) {
        return ImageCompression.isWebp(bytes) || bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F';
    }
    public static void load(byte[] bytes, String url) {
        try { WORKER.execute(() -> decode(bytes, url)); }
        catch (RejectedExecutionException e) { ClientStorage.AddImageError(url, ChatImageFrame.FrameError.FILE_LOAD_ERROR); }
    }
    private static void decode(byte[] bytes, String url) {
        try {
            ImageStore.validate(bytes);
            List<BufferedImage> images = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            int plays;
            if (ImageCompression.isWebp(bytes)) {
                try (var reader = WebPImageReader.open(new ByteArrayInputStream(bytes))) {
                    plays = reader.getLoopCount();
                    WebPFrame frame;
                    while ((frame = reader.readNextFrame()) != null) {
                        BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        image.setRGB(0, 0, frame.getWidth(), frame.getHeight(), frame.getArgbArray(), 0, frame.getScanlineStride());
                        images.add(image); delays.add(frame.getDurationMillis());
                    }
                }
            } else {
                GifDecoder decoder = new GifDecoder();
                if (decoder.read(new ByteArrayInputStream(bytes)) != GifDecoder.STATUS_OK) throw new IOException();
                String raw = new String(bytes, StandardCharsets.ISO_8859_1);
                boolean hasLoopExtension = raw.contains("NETSCAPE2.0") || raw.contains("ANIMEXTS1.0");
                plays = !hasLoopExtension ? 1 : decoder.getLoopCount() == 0 ? 0 : decoder.getLoopCount() + 1;
                for (int i = 0; i < decoder.getFrameCount(); i++) { images.add(decoder.getFrame(i)); delays.add(decoder.getDelay(i)); }
            }
            if (images.isEmpty()) throw new IOException();
            ChatImageFrame<?> root = new ChatImageFrame<>(images.getFirst());
            for (int i = 1; i < images.size(); i++) root.append(new ChatImageFrame<>(images.get(i)));
            for (BufferedImage image : images) image.flush();
            Playback.attach(root, delays.stream().mapToInt(Integer::intValue).toArray(), plays);
            ClientStorage.AddImage(url, root);
        } catch (Exception | LinkageError e) {
            io.github.kituin.chatimage.ChatImage.LOGGER.debug("Image decoding failed", e);
            ClientStorage.AddImageError(url, ChatImageFrame.FrameError.FILE_LOAD_ERROR);
        }
    }
}
