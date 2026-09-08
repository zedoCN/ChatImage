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

        BufferedImage large = new BufferedImage(2048, 2048, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2048; y++) for (int x = 0; x < 2048; x++) large.setRGB(x, y, random.nextInt());
        ByteArrayOutputStream largeOutput = new ByteArrayOutputStream(); ImageIO.write(large, "png", largeOutput);
        byte[] largePng = largeOutput.toByteArray();
        check(largePng.length > 10 * 1024 * 1024 && largePng.length <= 20 * 1024 * 1024, "Large PNG fixture exceeds old limit");
        ImageStore.validate(largePng); count++;
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
        ImageStore permanent = new ImageStore(directory, png.length + 10, 0, clock);
        String permanentId = permanent.put(png);
        ImageStore future = new ImageStore(directory, png.length + 10, 0, Clock.offset(clock, Duration.ofDays(36500)));
        future.cleanup();
        check(Arrays.equals(future.get(permanentId), png), "Permanent retention survives restart and cleanup after 100 years");
        rejects(() -> future.put(medium), "Permanent storage retains quota");
        check(Arrays.equals(future.get(permanentId), png), "Quota rejection preserves existing images");
        Path source = Files.createTempFile("chatimage-upload-", ".png");
        Files.write(source, png);
        check(Arrays.equals(UploadPreparation.prepare(source, png.length, true, png.length + 1).bytes(), png), "Under limit uploads original");
        rejects(() -> UploadPreparation.prepare(source, 100, false, png.length + 1), "Disabled precompression");
        rejects(() -> UploadPreparation.prepare(source, 100, true, 10), "Local read budget");
        var prepared = UploadPreparation.prepare(source, 500, true, png.length + 1);
        check(prepared.bytes().length <= 500 && ImageCompression.isWebp(prepared.bytes()), "Oversized image fits after compression and resize");
        check(Arrays.equals(Files.readAllBytes(source), png), "Source file unchanged");
        Files.write(source, gif); rejects(() -> UploadPreparation.prepare(source, 8, true, gif.length + 1), "GIF not flattened");
        Files.write(source, animated); rejects(() -> UploadPreparation.prepare(source, 8, true, animated.length + 1), "Animated WebP not flattened");
        Files.delete(source);
        Path cacheRoot = Files.createTempDirectory("chatimage-cache-");
        String ref = "mcimage://00000000-0000-0000-0000-000000000001/" + ImageStore.hash(png);
        DiskImageCache cache = new DiskImageCache(cacheRoot, png.length + medium.length + 1);
        cache.put(ref, png);
        check(Arrays.equals(new DiskImageCache(cacheRoot, png.length + medium.length + 1).get(ref, png.length), png), "Disk cache survives object restart");
        check(cache.get(ref.replace("000000000001/", "000000000002/"), png.length) == null, "Disk cache isolates server identity");
        check(cache.get(ref, 1) == null, "Disk cache read budget");
        rejects(() -> cache.put(ref, medium), "Disk cache rejects mismatched hash");
        try (var files = Files.list(cacheRoot)) { Files.write(files.findFirst().orElseThrow(), new byte[]{1,2,3}); }
        check(cache.get(ref, png.length) == null, "Corrupt cache becomes miss");
        cache.put(ref, png);
        DiskImageCache smallCache = new DiskImageCache(cacheRoot, medium.length);
        String otherRef = "mcimage://00000000-0000-0000-0000-000000000001/" + ImageStore.hash(medium);
        smallCache.put(otherRef, medium);
        check(smallCache.get(ref, png.length) == null && Arrays.equals(smallCache.get(otherRef, medium.length), medium), "Cache quota evicts old files");
        check(new DiskImageCache(cacheRoot, 0).get(otherRef, medium.length) == null, "Disk cache can be disabled");
        try (var files = Files.list(cacheRoot)) { for (Path p : files.toList()) Files.delete(p); } Files.delete(cacheRoot);
        check(TransferUnits.bytes(1024).equals("1.00 KiB"), "KiB unit boundary");
        check(TransferUnits.bytes(494894).equals("483.29 KiB"), "Two decimal image size");
        check(TransferUnits.speed(2.5 * 1024 * 1024).equals("2.50 MiB/s"), "Adaptive speed unit");
        check(TransferUnits.bytes(0).equals("0.00 B"), "Zero size formatting");
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
