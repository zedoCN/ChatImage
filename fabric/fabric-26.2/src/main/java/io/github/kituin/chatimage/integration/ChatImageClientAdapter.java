package io.github.kituin.chatimage.integration;

import com.google.common.collect.Maps;
import io.github.kituin.ChatImageCode.ChatImageFrame;
import io.github.kituin.ChatImageCode.IClientAdapter;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static io.github.kituin.chatimage.client.ChatImageClient.CONFIG;
import static io.github.kituin.chatimage.client.ChatImageClient.MOD_ID;
import static io.github.kituin.chatimage.tool.SimpleUtil.createTranslatableComponent;

public class ChatImageClientAdapter implements IClientAdapter {
     private final Map<String, Integer> dynamicIdCounters = Maps.newHashMap();
     public Identifier registerDynamicTexture(String prefix, NativeImageBackedTexture texture) {
         Integer integer = (Integer)this.dynamicIdCounters.get(prefix);
         if (integer == null) {
             integer = 1;
         } else {
             integer = integer + 1;
         }

         this.dynamicIdCounters.put(prefix, integer);
         Identifier identifier = Identifier.withDefaultNamespace(String.format(Locale.ROOT, "dynamic/%s_%d", prefix, integer));
         Minecraft.getInstance().getTextureManager().register(identifier, texture);
         return identifier;
     }

    @Override
    public int getTimeOut() {
        return CONFIG.timeout;
    }

    @Override
    public ChatImageFrame.TextureReader<Identifier> loadTexture(InputStream image) throws IOException {
        NativeImage nativeImage = NativeImage.read(image);
        Minecraft minecraft = Minecraft.getInstance();
        int width = nativeImage.getWidth();
        int height = nativeImage.getHeight();
        java.util.function.Supplier<ChatImageFrame.TextureReader<Identifier>> upload = () -> {
            try {
                Identifier id = registerDynamicTexture(MOD_ID + "/chatimage", new NativeImageBackedTexture(nativeImage));
                return new ChatImageFrame.TextureReader<>(id, width, height);
            } catch (RuntimeException error) {
                nativeImage.close();
                throw error;
            }
        };
        if (minecraft.isSameThread()) return upload.get();
        return minecraft.submit(upload).join();
    }

    @Override
    public void sendToServer(String url, File file, boolean isToServer) {
        // Legacy local paths must never trigger an upload while rendering received chat.
        // Uploads are now explicit and capability-negotiated by ClientTransfers.
    }

    @Override
    public void checkCachePath() {
        File folder = new File(CONFIG.cachePath);
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    @Override
    public int getMaxFileSize() {
        return CONFIG.MaxFileSize;
    }

    @Override
    public Component getProcessMessage(int i) {
        return createTranslatableComponent("process.chatimage.message", i);
    }


}
