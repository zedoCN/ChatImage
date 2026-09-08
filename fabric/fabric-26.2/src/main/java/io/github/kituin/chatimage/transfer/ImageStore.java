package io.github.kituin.chatimage.transfer;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Server-owned, content-addressed image storage. No client-supplied filesystem paths. */
public final class ImageStore {
    private final Path root;
    private final long quota, retentionMillis;
    private final Clock clock;
    public final String serverId;

    public ImageStore(Path root, long quota, long retentionMillis, Clock clock) throws IOException {
        this.root = root; this.quota = quota; this.retentionMillis = retentionMillis; this.clock = clock;
        Files.createDirectories(root);
        Path identity = root.resolve("server-id.txt");
        if (!Files.exists(identity)) Files.writeString(identity, UUID.randomUUID().toString(), StandardOpenOption.CREATE_NEW);
        serverId = UUID.fromString(Files.readString(identity).trim()).toString();
        cleanup();
    }

    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static void validate(byte[] bytes) throws IOException {
        if (ImageCompression.isWebp(bytes)) {
            try (var reader = org.glavo.webp.WebPImageReader.open(new ByteArrayInputStream(bytes))) {
                long pixels = (long) reader.getWidth() * reader.getHeight();
                if (pixels <= 0 || pixels > 16_000_000L || reader.getFrameCount() > 256 || pixels * reader.getFrameCount() > 32_000_000L) throw new IOException("pixels");
                int frames = 0;
                while (reader.readNextFrame() != null) if (++frames > 256 || pixels * frames > 32_000_000L) throw new IOException("pixels");
                if (frames == 0) throw new IOException("format");
            } catch (Exception e) { throw new IOException("format", e); }
            return;
        }
        if (bytes.length < 8) throw new IOException("size");
        if (bytes[0] == 'G' && bytes.length >= 10) {
            long canvas = ((bytes[6] & 255) | (bytes[7] & 255) << 8) * (long) ((bytes[8] & 255) | (bytes[9] & 255) << 8);
            if (canvas <= 0 || canvas > 16_000_000) throw new IOException("pixels");
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("format");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "gif", "webp").contains(format)) throw new IOException("format");
                reader.setInput(input);
                // Bound frame discovery as well as decoded pixel count.
                long pixels = 0;
                for (int i = 0; i <= 256; i++) {
                    int w, h;
                    try { w = reader.getWidth(i); h = reader.getHeight(i); }
                    catch (IndexOutOfBoundsException end) { if (i == 0) throw new IOException("format"); break; }
                    if (i == 256 || w <= 0 || h <= 0 || (long) w * h > 16_000_000L) throw new IOException("pixels");
                    pixels += format.equals("gif") ? ((bytes[6] & 255) | (bytes[7] & 255) << 8) * (long) ((bytes[8] & 255) | (bytes[9] & 255) << 8) : (long) w * h;
                    if (pixels > 32_000_000L) throw new IOException("pixels");
                    var decoded = reader.read(i);
                    if (decoded == null) throw new IOException("format");
                    decoded.flush();
                    if (!format.equals("gif")) break;
                }
            } finally { reader.dispose(); }
        } catch (RuntimeException e) { throw new IOException("format", e); }
    }

    public String put(byte[] bytes) throws IOException {
        validate(bytes);
        cleanup();
        String id = hash(bytes);
        Path target = path(id);
        if (Files.exists(target)) {
            Files.setLastModifiedTime(target, java.nio.file.attribute.FileTime.fromMillis(clock.millis()));
            return id;
        }
        long used = 0;
        try (var entries = Files.newDirectoryStream(root, "*.bin")) {
            for (Path entry : entries) used += Files.size(entry);
        }
        if (used + bytes.length > quota) throw new IOException("quota");
        Path temp = Files.createTempFile(root, "upload-", ".tmp");
        try {
            Files.write(temp, bytes);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            Files.setLastModifiedTime(target, java.nio.file.attribute.FileTime.fromMillis(clock.millis()));
        } finally { Files.deleteIfExists(temp); }
        return id;
    }

    public byte[] get(String id) throws IOException {
        return get(id, Integer.MAX_VALUE - 8L);
    }
    public byte[] get(String id, long maxBytes) throws IOException {
        Path file = path(id);
        if (!Files.isRegularFile(file) || expired(file)) throw new IOException("missing");
        if (Files.size(file) > Math.min(maxBytes, Math.min(quota, Integer.MAX_VALUE - 8L))) throw new IOException("size");
        byte[] data = Files.readAllBytes(file);
        if (!hash(data).equals(id)) throw new IOException("hash");
        return data;
    }

    private Path path(String id) throws IOException {
        if (!id.matches("[a-f0-9]{64}")) throw new IOException("reference");
        return root.resolve(id + ".bin");
    }
    private boolean expired(Path p) throws IOException {
        return retentionMillis > 0 && clock.millis() - Files.getLastModifiedTime(p).toMillis() >= retentionMillis;
    }
    public void cleanup() throws IOException {
        try (var entries = Files.newDirectoryStream(root, "*.bin")) {
            for (Path p : entries) if (expired(p)) Files.deleteIfExists(p);
        }
    }
}
