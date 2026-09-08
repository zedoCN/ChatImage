package io.github.kituin.chatimage;

import io.github.kituin.ChatImageCode.ChatImageCodeInstance;
import io.github.kituin.chatimage.integration.ChatImageLogger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * @author kitUIN
 */
public class ChatImage implements ModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();
    static {
        ChatImageCodeInstance.LOGGER = new ChatImageLogger();
    }

    @Override
    public void onInitialize() {
       PayloadTypeRegistry.serverboundPlay().register(io.github.kituin.chatimage.transfer.TransferPayload.ID, io.github.kituin.chatimage.transfer.TransferPayload.CODEC);
       PayloadTypeRegistry.clientboundPlay().register(io.github.kituin.chatimage.transfer.TransferPayload.ID, io.github.kituin.chatimage.transfer.TransferPayload.CODEC);
       io.github.kituin.chatimage.transfer.ServerTransfers.register();
    }
}
