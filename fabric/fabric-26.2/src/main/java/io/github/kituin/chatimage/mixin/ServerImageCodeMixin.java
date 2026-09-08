package io.github.kituin.chatimage.mixin;

import io.github.kituin.ChatImageCode.ChatImageCode;
import io.github.kituin.chatimage.transfer.ClientTransfers;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep server references out of the upstream HTTP/local-file handler. */
@Mixin(value = ChatImageCode.class, remap = false)
public class ServerImageCodeMixin {
    @Shadow private String url;
    @Inject(method = "checkUrl", at = @At("HEAD"), cancellable = true)
    private void serverImage(String value, CallbackInfo ci) {
        if (value != null && value.startsWith("mcimage://")) {
            this.url = value;
            ClientTransfers.load(value);
            ci.cancel();
        }
    }
}
