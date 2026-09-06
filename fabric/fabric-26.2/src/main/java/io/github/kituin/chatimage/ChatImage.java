package io.github.kituin.chatimage;

import io.github.kituin.ChatImageCode.ChatImageCodeInstance;
import io.github.kituin.chatimage.integration.ChatImageLogger;
import io.github.kituin.chatimage.network.ChatImagePacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.kituin.chatimage.network.DownloadFileChannelPacket;
import io.github.kituin.chatimage.network.FileChannelPacket;
import io.github.kituin.chatimage.network.FileInfoChannelPacket;
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
       PayloadTypeRegistry.serverboundPlay().register(FileChannelPacket.ID, FileChannelPacket.CODEC);
       PayloadTypeRegistry.serverboundPlay().register(FileInfoChannelPacket.ID, FileInfoChannelPacket.CODEC);
       PayloadTypeRegistry.clientboundPlay().register(DownloadFileChannelPacket.ID, DownloadFileChannelPacket.CODEC);
       PayloadTypeRegistry.clientboundPlay().register(FileInfoChannelPacket.ID, FileInfoChannelPacket.CODEC);
       ServerPlayNetworking.registerGlobalReceiver(FileChannelPacket.ID, ChatImagePacket::serverFileChannelReceived);
       ServerPlayNetworking.registerGlobalReceiver(FileInfoChannelPacket.ID, ChatImagePacket::serverGetFileChannelReceived);
    }
}
