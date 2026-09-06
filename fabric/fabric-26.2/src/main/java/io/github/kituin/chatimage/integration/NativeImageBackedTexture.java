package io.github.kituin.chatimage.integration;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Uses the game's texture lifecycle and current GPU backend. Construct on the client thread. */
public final class NativeImageBackedTexture extends DynamicTexture {
    public NativeImageBackedTexture(NativeImage image) {
        super(() -> "ChatImage", image);
    }
}
