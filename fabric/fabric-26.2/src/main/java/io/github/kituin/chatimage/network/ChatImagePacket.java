package io.github.kituin.chatimage.network;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import io.github.kituin.ChatImageCode.ChatImageFrame;
import io.github.kituin.ChatImageCode.ChatImageIndex;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

import static io.github.kituin.ChatImageCode.NetworkHelper.mergeFileBlocks;
import static io.github.kituin.ChatImageCode.ServerStorage.*;
import static io.github.kituin.ChatImageCode.ClientStorage.*;
import static io.github.kituin.chatimage.ChatImage.LOGGER;

public class ChatImagePacket {


    public static Gson gson = new Gson();


    /**
     * 发送给客户端一个网络包(异步)
     *
     * @param player 发送者(ClientPlayerEntity状态为发送给服务器,反之则发送给玩家)
     * @param packet 网络包数据
     */
    public static void sendPacketAsync(ServerPlayer player, CustomPacketPayload packet) {
        CompletableFuture.supplyAsync(() -> {
            ServerPlayNetworking.send(player, packet);
            return null;
        });
    }
    /**
     * 发送给服务器一个网络包(异步)
     *
     * @param packet 网络包数据
     */
    @Environment(EnvType.CLIENT)
    public static void sendPacketAsync(CustomPacketPayload packet) {
        CompletableFuture.supplyAsync(() -> {
            ClientPlayNetworking.send(packet);
            return null;
        });
    }

    /**
     * 发送给服务器一连串网络包(异步)
     *
     * @param handler 处理
     * @param bufs    网络包数据列表
     */
    public static void sendPacketAsync(Function<String, CustomPacketPayload> handler, List<String> bufs) {
        for (String buf : bufs) {
            sendPacketAsync(handler.apply(buf));
        }
    }


    /**
     * 尝试从服务器获取图片
     *
     * @param url 图片url
     */
    public static void loadFromServer(String url) {
        if (Minecraft.getInstance().player != null) {
            sendPacketAsync(new FileInfoChannelPacket(url));
            LOGGER.info("[GetFileChannel-Try]{}", url);
        } else {
            AddImageError(url, ChatImageFrame.FrameError.FILE_NOT_FOUND);
        }
    }


    /**
     * 服务端接收 图片文件分块 的处理
     *
     */
    public static void serverFileChannelReceived(FileChannelPacket packet, ServerPlayNetworking.Context content) {
        MinecraftServer server = content.server();
        String res = packet.message();
        ChatImageIndex title = gson.fromJson(res, ChatImageIndex.class);
        HashMap<Integer, String> blocks = SERVER_BLOCK_CACHE.createBlock(title, res);
        LOGGER.info("[FileChannel->Server:{}/{}]{}", title.index, title.total, title.url);
        if (title.total == blocks.size()) {
            List<String> names = SERVER_BLOCK_CACHE.getUsers(title.url);
            if (names != null) {
                for (String uuid : names) {
                    ServerPlayer serverPlayer = server.getPlayerList().getPlayer(UUID.fromString(uuid));
                    sendPacketAsync(serverPlayer, new FileInfoChannelPacket("true->" + title.url));
                    LOGGER.info("[FileChannel->Client({})]{}", uuid, title.url);
                }
            }
            LOGGER.info("[FileChannel->Server]{}", title.url);
        }
    }

    /**
     * 服务端接收 客户端试图获取图片文件 的处理
     *
     */
    public static void serverGetFileChannelReceived(FileInfoChannelPacket packet, ServerPlayNetworking.Context content) {
        ServerPlayer player = content.player();
        String url = packet.message();
        HashMap<Integer, String> list = SERVER_BLOCK_CACHE.getBlock(url);
        if (list != null) {

            for (Map.Entry<Integer, String> entry : list.entrySet()) {
                    sendPacketAsync(player, new DownloadFileChannelPacket(entry.getValue()));
                LOGGER.debug("[GetFileChannel->Client:{}/{}]{}", entry.getKey(), list.size() - 1, url);
            }
            LOGGER.info("[GetFileChannel->Client]{}", url);
            return;
        }
                    sendPacketAsync(player, new FileInfoChannelPacket("null->" + url));
        LOGGER.error("[GetFileChannel]not found in server:{}", url);
        SERVER_BLOCK_CACHE.tryAddUser(url, player.getStringUUID());
        LOGGER.info("[GetFileChannel]记录uuid:{}", player.getStringUUID());
    }

    /**
     * 客户端接收 无文件 处理
     *
     */
    public static void clientGetFileChannelReceived(FileInfoChannelPacket packet) {
        String data = packet.message();

        String url = data.substring(6);
        LOGGER.info(url);
        if (data.startsWith("null")) {
            LOGGER.info("[GetFileChannel-NULL]{}", url);
            AddImageError(url, ChatImageFrame.FrameError.FILE_NOT_FOUND);
        } else if (data.startsWith("true")) {
            loadFromServer(url);
        }
    }


    /**
     * 客户端接收 下载文件分块 处理
     *
     */
    public static void clientDownloadFileChannelReceived(DownloadFileChannelPacket packet) {
        String res = packet.message();
        ChatImageIndex title = gson.fromJson(res, ChatImageIndex.class);
        HashMap<Integer, ChatImageIndex> blocks = CLIENT_CACHE_MAP.containsKey(title.url) ? CLIENT_CACHE_MAP.get(title.url) : new HashMap<>();
        blocks.put(title.index, title);
        CLIENT_CACHE_MAP.put(title.url, blocks);
        LOGGER.info("[DownloadFile({}/{})]{}", title.index, title.total, title.url);
        if (blocks.size() == title.total) {
            LOGGER.info(String.valueOf(blocks));
            mergeFileBlocks(title.url, blocks);
            LOGGER.info("[DownloadFileChannel-Merge]{}", title.url);
        }
    }
}
