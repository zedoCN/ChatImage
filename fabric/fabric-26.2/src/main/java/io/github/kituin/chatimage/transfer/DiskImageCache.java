package io.github.kituin.chatimage.transfer;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;

/** Bounded, content-verified disk cache. Reference digest includes server identity. */
public final class DiskImageCache {
    private static final Object LOCK = new Object();
    private final Path root;
    private final long quota;
    public DiskImageCache(Path root, long quota) { this.root = root; this.quota = quota; }
    private Path path(String reference) throws IOException {
        if (!reference.matches("mcimage://[a-f0-9-]{36}/[a-f0-9]{64}")) throw new IOException("reference");
        return root.resolve(ImageStore.hash(reference.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".bin");
    }
    public byte[] get(String reference, int maxBytes) throws IOException {
        synchronized (LOCK) {
            if (quota <= 0) return null;
            Path p = path(reference);
            if (!Files.isRegularFile(p) || Files.size(p) > maxBytes) return null;
            byte[] bytes = Files.readAllBytes(p);
            if (!reference.endsWith("/" + ImageStore.hash(bytes))) { Files.deleteIfExists(p); return null; }
            try { ImageStore.validate(bytes); } catch (IOException e) { Files.deleteIfExists(p); return null; }
            Files.setLastModifiedTime(p, FileTime.fromMillis(System.currentTimeMillis()));
            return bytes;
        }
    }
    public void put(String reference, byte[] bytes) throws IOException {
        synchronized (LOCK) {
            if (quota <= 0 || bytes.length > quota) return;
            Path target = path(reference);
            if (!reference.endsWith("/" + ImageStore.hash(bytes))) throw new IOException("hash");
            Files.createDirectories(root);
            List<Path> files;
            try (var entries = Files.list(root)) { files = new ArrayList<>(entries.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.bin") && !p.equals(target)).toList()); }
            files.sort(Comparator.comparingLong(p -> { try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0; } }));
            long used = bytes.length;
            for (Path p : files) used += Files.size(p);
            for (Path p : files) { if (used <= quota) break; long size = Files.size(p); Files.delete(p); used -= size; }
            Path temp = Files.createTempFile(root, "cache-", ".tmp");
            try { Files.write(temp, bytes); Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            finally { Files.deleteIfExists(temp); }
        }
    }
}
