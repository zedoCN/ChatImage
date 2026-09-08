package io.github.kituin.chatimage;

import io.github.kituin.chatimage.transfer.*;
import io.github.kituin.chatimage.animation.Timeline;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Deterministic storage, image validation, codec and timeline regression checks. */
public final class TransferChecks {
    private static int count;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); count++; }
    private interface IO { void run() throws Exception; }
    private static void rejects(IO action, String name) throws Exception {
        try { action.run(); throw new AssertionError("Accepted " + name); } catch (IOException expected) { count++; }
    }
    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(128, 96, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(42);
        for (int y = 0; y < 96; y++) for (int x = 0; x < 128; x++) image.setRGB(x, y, (x < 8 ? 0x33000000 : 0xff000000) | random.nextInt(0x1000000));
        ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output);
        byte[] png = output.toByteArray(); ImageStore.validate(png); count++;
        byte[] low = ImageCompression.compress(png, "webp", "low"), medium = ImageCompression.compress(png, "webp", "medium"), high = ImageCompression.compress(png, "webp", "high");
        check(ImageCompression.isWebp(medium), "WebP output");
        check(low.length > medium.length && medium.length > high.length, "Compression strength");
        check(Arrays.equals(png, ImageCompression.compress(png, "none", "medium")), "Compression disabled");
        for (byte[] data : List.of(low, medium, high)) ImageStore.validate(data);
        count += 3;
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(medium));
        check(decoded.getWidth() == 128 && decoded.getHeight() == 96, "Dimensions");
        check((decoded.getRGB(0, 0) >>> 24) == 0x33, "Alpha preserved");
        check(decoded.getRGB(64, 48) != image.getRGB(64, 48), "Lossy compression");
        byte[] gif = TransferChecks.class.getResourceAsStream("/timing.gif").readAllBytes();
        byte[] animated = TransferChecks.class.getResourceAsStream("/timing.webp").readAllBytes();
        ImageStore.validate(gif); ImageStore.validate(animated); count += 2;
        check(Arrays.equals(gif, ImageCompression.compress(gif, "webp", "high")), "GIF animation preserved");
        check(Arrays.equals(animated, ImageCompression.compress(animated, "webp", "high")), "Animated WebP preserved");
        try (var reader = org.glavo.webp.WebPImageReader.open(new ByteArrayInputStream(animated))) {
            check(reader.getLoopCount() == 2, "WebP loop metadata");
            List<Integer> delays = new ArrayList<>();
            org.glavo.webp.WebPFrame frame;
            while ((frame = reader.readNextFrame()) != null) delays.add(frame.getDurationMillis());
            check(delays.equals(List.of(100, 400, 200)), "WebP per-frame metadata");
        }
        rejects(() -> ImageStore.validate(new byte[0]), "Empty image");
        rejects(() -> ImageStore.validate("not an image at all".getBytes()), "Invalid format");
        rejects(() -> ImageStore.validate(new byte[ImageStore.HARD_MAX_BYTES + 1]), "Size ceiling");
        Path directory = Files.createTempDirectory("chatimage-check-");
        Clock clock = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);
        ImageStore store = new ImageStore(directory, png.length + 10, 1000, clock);
        String id = store.put(png);
        check(Arrays.equals(store.get(id), png), "Round trip");
        check(store.put(png).equals(id), "Deduplication at quota");
        rejects(() -> store.put(medium), "Storage quota");
        rejects(() -> store.get("../server.properties"), "Path traversal");
        ImageStore restarted = new ImageStore(directory, png.length + 10, 1000, clock);
        check(restarted.serverId.equals(store.serverId) && Arrays.equals(restarted.get(id), png), "Restart persistence");
        ImageStore expired = new ImageStore(directory, png.length + 10, 1000, Clock.offset(clock, Duration.ofSeconds(2)));
        rejects(() -> expired.get(id), "Expiry");
        check(!Files.exists(directory.resolve(id + ".bin")), "Expiry cleanup");
        Timeline timeline = new Timeline(new int[]{100, 400, 200}, 2);
        check(timeline.indexAt(99, true, 10) == 0 && timeline.indexAt(100, true, 10) == 1, "Unequal frame delays");
        check(timeline.indexAt(500, true, 10) == 2 && timeline.indexAt(700, true, 10) == 0, "Loop timing");
        check(timeline.indexAt(1400, true, 10) == 2, "Finite loop stop");
        check(timeline.indexAt(250, false, 10) == 2, "Manual FPS");
        check(new Timeline(new int[]{100, 400, 200}, 0).indexAt(1000, false, 60) == 0, "Exact 60 FPS override");
        Timeline infinite = new Timeline(new int[]{0, 100}, 0);
        check(infinite.indexAt(2050, true, 10) == 0 && infinite.indexAt(2150, true, 10) == 1, "Zero-delay fallback and infinite loop");
        try (var entries = Files.list(directory)) { for (Path p : entries.toList()) Files.delete(p); } Files.delete(directory);
        System.out.println("PASS " + count + " checks; WebP sizes low/medium/high=" + low.length + "/" + medium.length + "/" + high.length);
    }
}
