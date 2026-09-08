package io.github.kituin.chatimage.transfer;

import java.io.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import javax.imageio.ImageIO;

/** Works on copies only; the user's source file is never modified. */
public final class UploadPreparation {
    public record Prepared(byte[] bytes, long originalBytes) {}
    public static Prepared prepare(Path file, int limit, boolean automatic, int sourceLimit) throws IOException {
        long size = Files.size(file);
        if (size > sourceLimit) throw new IOException("source");
        byte[] input;
        try (InputStream stream = Files.newInputStream(file)) { input = stream.readNBytes(sourceLimit + 1); }
        if (input.length > sourceLimit) throw new IOException("source");
        ImageStore.validate(input);
        if (input.length <= limit) return new Prepared(input, input.length);
        if (!automatic) throw new IOException("size");
        if (ImageCompression.animatedWebp(input) || input[0] == 'G' && input[1] == 'I' && input[2] == 'F') throw new IOException("animation");
        BufferedImage image = ImageCompression.read(input);
        if (image == null) throw new IOException("format");
        try {
            while (true) {
                for (float quality : new float[]{0.85f, 0.70f, 0.45f, 0.25f}) {
                    byte[] candidate = ImageCompression.encode(image, quality);
                    if (candidate.length <= limit) { ImageStore.validate(candidate); return new Prepared(candidate, input.length); }
                }
                if (image.getWidth() == 1 && image.getHeight() == 1) throw new IOException("size");
                BufferedImage smaller = new BufferedImage(Math.max(1, image.getWidth() / 2), Math.max(1, image.getHeight() / 2), BufferedImage.TYPE_INT_ARGB);
                var graphics = smaller.createGraphics();
                try { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC); graphics.drawImage(image, 0, 0, smaller.getWidth(), smaller.getHeight(), null); }
                finally { graphics.dispose(); }
                image.flush(); image = smaller;
            }
        } finally { image.flush(); }
    }
}
