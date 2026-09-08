package io.github.kituin.chatimage.mixin;

import io.github.kituin.ChatImageCode.FileImageHandler;
import io.github.kituin.chatimage.animation.AnimationDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FileImageHandler.class, remap = false)
public class AnimatedImageMixin {
    @Inject(method = "loadFile([BLjava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private static void animation(byte[] input, String url, CallbackInfo ci) {
        if (AnimationDecoder.accepts(input)) { AnimationDecoder.load(input, url); ci.cancel(); }
    }
}
