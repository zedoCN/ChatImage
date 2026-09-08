package io.github.kituin.chatimage.transfer;

import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import com.luciad.imageio.webp.WebPWriteParam;
import com.luciad.imageio.webp.CompressionType;

/** Static-image lossy WebP compression. GIF bytes are preserved, including every animation frame. */
public final class ImageCompression {
    public static boolean isWebp(byte[] b) {
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
    public static boolean animatedWebp(byte[] b) {
        return isWebp(b) && b.length > 20 && b[12] == 'V' && b[15] == 'X' && (b[20] & 2) != 0;
    }
    public static java.awt.image.BufferedImage read(byte[] input) throws IOException {
        if (!isWebp(input)) return ImageIO.read(new ByteArrayInputStream(input));
        var reader = new com.luciad.imageio.webp.WebPImageReaderSpi().createReaderInstance();
        try (var stream = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
            reader.setInput(stream); return reader.read(0);
        } finally { reader.dispose(); }
    }
    public static byte[] compress(byte[] input, String format, String level) throws IOException {
        if (format.equals("none") || animatedWebp(input) || input.length >= 6 && input[0] == 'G' && input[1] == 'I' && input[2] == 'F') return input;
        if (!format.equals("webp")) throw new IOException("compression");
        float quality = switch (level) { case "low" -> 0.85f; case "medium" -> 0.70f; case "high" -> 0.45f; default -> throw new IOException("compression"); };
        var image = read(input);
        if (image == null) throw new IOException("format");
        try { return encode(image, quality); } finally { image.flush(); }
    }
    public static byte[] encode(java.awt.image.BufferedImage image, float quality) throws IOException {
        ImageWriter writer = new com.luciad.imageio.webp.WebPImageWriterSpi().createWriterInstance();
        try (var bytes = new ByteArrayOutputStream(); var output = new MemoryCacheImageOutputStream(bytes)) {
            WebPWriteParam param = (WebPWriteParam) writer.getDefaultWriteParam();
            param.setCompressionType(CompressionType.Lossy);
            param.setCompressionQuality(quality);
            param.setUseSharpYUV(true);
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), param);
            output.flush();
            return bytes.toByteArray();
        } catch (LinkageError e) { throw new IOException("compression", e); }
        finally { writer.dispose(); }
    }
}
