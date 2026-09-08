package io.github.kituin.chatimage.transfer;

import com.google.gson.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;

public final class UploadOptions {
    public boolean autoCompress = true;
    public int transferWindow = 8;
    public long diskCacheBytes = 512L * 1024 * 1024;
    public int maxSourceBytes = 64 * 1024 * 1024;
    private static UploadOptions current;
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("chatimage-upload.json"); }
    public static synchronized UploadOptions get() {
        if (current == null) {
            try { current = new Gson().fromJson(Files.readString(path()), UploadOptions.class); }
            catch (Exception ignored) { }
            if (current == null) current = new UploadOptions();
            if (current.maxSourceBytes < 8 || current.maxSourceBytes >= Integer.MAX_VALUE - 8) current.maxSourceBytes = 64 * 1024 * 1024;
        }
        return current;
    }
    public static void save() {
        try { Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(get())); }
        catch (java.io.IOException e) { io.github.kituin.chatimage.ChatImage.LOGGER.warn("Cannot save upload options", e); }
    }
}
