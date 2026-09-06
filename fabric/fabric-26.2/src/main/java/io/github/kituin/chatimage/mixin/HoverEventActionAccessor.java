package io.github.kituin.chatimage.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.HoverEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HoverEvent.Action.class)
public interface HoverEventActionAccessor {
    @Invoker("<init>")
    static HoverEvent.Action chatimage$create(String enumName, int ordinal, String serializedName,
                                             boolean allowFromServer, MapCodec<? extends HoverEvent> codec) {
        throw new AssertionError();
    }
}
