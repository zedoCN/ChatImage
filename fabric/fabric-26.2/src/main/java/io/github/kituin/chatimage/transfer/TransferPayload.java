package io.github.kituin.chatimage.transfer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded control messages and base64 chunks, versioned independently of legacy transfers. */
public record TransferPayload(String json) implements CustomPacketPayload {
    public static final int CHUNK = 12 * 1024;
    public static final Type<TransferPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("chatimage", "transfer_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransferPayload> CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeUtf(packet.json, 24000), buf -> new TransferPayload(buf.readUtf(24000)));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
