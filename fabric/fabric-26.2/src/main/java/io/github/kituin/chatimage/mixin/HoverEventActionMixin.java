package io.github.kituin.chatimage.mixin;

import io.github.kituin.chatimage.tool.ChatImageStyle;
import net.minecraft.network.chat.HoverEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Arrays;

/** Register the existing show_chatimage wire format before vanilla builds its action codec. */
@Mixin(HoverEvent.Action.class)
public abstract class HoverEventActionMixin {
    @Shadow @Final @Mutable private static HoverEvent.Action[] $VALUES;

    @Inject(method = "<clinit>", at = @At(value = "FIELD",
            target = "Lnet/minecraft/network/chat/HoverEvent$Action;$VALUES:[Lnet/minecraft/network/chat/HoverEvent$Action;",
            opcode = 179, shift = At.Shift.AFTER))
    private static void chatimage$register(CallbackInfo ci) {
        int ordinal = $VALUES.length;
        HoverEvent.Action action = HoverEventActionAccessor.chatimage$create(
                "SHOW_IMAGE", ordinal, "show_chatimage", true, ChatImageStyle.ShowImage.CODEC);
        $VALUES = Arrays.copyOf($VALUES, ordinal + 1);
        $VALUES[ordinal] = action;
    }
}
