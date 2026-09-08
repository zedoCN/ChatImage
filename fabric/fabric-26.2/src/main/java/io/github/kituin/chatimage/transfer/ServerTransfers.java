package io.github.kituin.chatimage.transfer;

import com.google.gson.*;
import io.github.kituin.chatimage.ChatImage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;

/** All disk, validation and transfer state run on one bounded worker, never the tick thread. */
public final class ServerTransfers {
    public static final class Config {
        public boolean enabled = true;
        public String compressionFormat = "webp";
        public String compressionLevel = "medium";
        public int maxFileBytes = 5 * 1024 * 1024;
        public long maxStorageBytes = 512L * 1024 * 1024;
        public int retentionHours = 168;
        public int uploadIntervalSeconds = 5;
    }
    private record Upload(String id, int size, ByteArrayOutputStream bytes, long started, long progress) {}
    private record Download(String id, String hash, byte[] bytes, long started, long progress, int nextOffset) {}
    private static final Map<MinecraftServer, ServerTransfers> INSTANCES = new ConcurrentHashMap<>();
    private final MinecraftServer server;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), r -> { Thread t = new Thread(r, "ChatImage storage"); t.setDaemon(true); return t; });
    private final Map<UUID, Upload> uploads = new HashMap<>();
    private final Map<UUID, Download> downloads = new HashMap<>();
    private final Map<UUID, Long> lastUpload = new HashMap<>();
    private long lastCleanup;
    private final Config config;
    private final ImageStore store;

    private ServerTransfers(MinecraftServer server) throws IOException {
        this.server = server;
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path file = FabricLoader.getInstance().getConfigDir().resolve("chatimage-server.json");
        if (!Files.exists(file)) Files.writeString(file, gson.toJson(new Config()));
        config = gson.fromJson(Files.readString(file), Config.class);
        if (config == null || config.maxFileBytes < 8 || config.maxFileBytes > ImageStore.HARD_MAX_BYTES
                || config.maxStorageBytes < config.maxFileBytes || config.retentionHours < 1
                || config.uploadIntervalSeconds < 0 || !Set.of("webp", "none").contains(config.compressionFormat)
                || !Set.of("low", "medium", "high").contains(config.compressionLevel)) throw new IOException("Invalid chatimage-server.json limits");
        store = new ImageStore(server.getWorldPath(LevelResource.ROOT).resolve("chatimage-images"),
                config.maxStorageBytes, config.retentionHours * 3_600_000L, Clock.systemUTC());
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try { INSTANCES.put(server, new ServerTransfers(server)); }
            catch (Exception e) { ChatImage.LOGGER.error("ChatImage server storage unavailable", e); }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            var instance = INSTANCES.get(server);
            if (instance != null && System.currentTimeMillis() - instance.lastCleanup > 60_000) {
                instance.lastCleanup = System.currentTimeMillis();
                instance.submit(() -> {
                    long now = System.currentTimeMillis();
                    instance.uploads.values().removeIf(u -> now - u.progress > 90_000 || now - u.started > 1_800_000);
                    instance.downloads.values().removeIf(d -> now - d.progress > 90_000 || now - d.started > 1_800_000);
                    try { instance.store.cleanup(); } catch (IOException e) { ChatImage.LOGGER.warn("Image cleanup failed", e); }
                });
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            var instance = INSTANCES.remove(server);
            if (instance != null) {
                instance.worker.shutdown();
                try { instance.worker.awaitTermination(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                instance.worker.shutdownNow();
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var instance = INSTANCES.get(server);
            if (instance != null) instance.submit(() -> {
                UUID id = handler.player.getUUID(); instance.uploads.remove(id); instance.downloads.remove(id); instance.lastUpload.remove(id);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(TransferPayload.ID, (packet, context) -> {
            var instance = INSTANCES.get(context.server());
            if (instance == null) return;
            if (!instance.submit(() -> instance.receive(context.player(), packet.json())))
                instance.reply(context.player(), "error", "", "reason", "busy");
        });
    }
    private boolean submit(Runnable run) {
        try { worker.execute(run); return true; } catch (RejectedExecutionException e) { return false; }
    }
    private void reply(ServerPlayer player, String op, String id, Object... fields) {
        JsonObject json = new JsonObject(); json.addProperty("op", op); json.addProperty("id", id);
        for (int i = 0; i < fields.length; i += 2) json.addProperty(fields[i].toString(), fields[i + 1].toString());
        server.execute(() -> {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player && ServerPlayNetworking.canSend(player, TransferPayload.ID))
                ServerPlayNetworking.send(player, new TransferPayload(json.toString()));
        });
    }
    private long inFlight() {
        return uploads.values().stream().mapToLong(Upload::size).sum() + downloads.values().stream().mapToLong(d -> d.bytes.length).sum();
    }
    private void receive(ServerPlayer player, String raw) {
        String id = "";
        UUID who = player.getUUID();
        try {
            long now = System.currentTimeMillis();
            uploads.values().removeIf(u -> now - u.progress > 90_000 || now - u.started > 1_800_000);
            downloads.values().removeIf(d -> now - d.progress > 90_000 || now - d.started > 1_800_000);
            JsonObject p = JsonParser.parseString(raw).getAsJsonObject();
            id = p.get("id").getAsString();
            if (!id.matches("[a-f0-9-]{36}")) throw new IOException("request");
            String op = p.get("op").getAsString();
            if (op.equals("hello")) {
                reply(player, "caps", id, "server", store.serverId, "enabled", config.enabled, "max", config.maxFileBytes); return;
            }
            if (!config.enabled) throw new IOException("disabled");
            switch (op) {
                case "begin" -> {
                    int size = p.get("size").getAsInt();
                    if (size < 8 || size > config.maxFileBytes) throw new IOException("size");
                    if (uploads.containsKey(who)) throw new IOException("busy");
                    if (now - lastUpload.getOrDefault(who, 0L) < config.uploadIntervalSeconds * 1000L) throw new IOException("rate");
                    if (inFlight() + size > 64L * 1024 * 1024) throw new IOException("busy");
                    lastUpload.put(who, now);
                    uploads.put(who, new Upload(id, size, new ByteArrayOutputStream(size), now, now));
                    reply(player, "ready", id);
                }
                case "chunk" -> {
                    Upload u = uploads.get(who);
                    if (u == null || !u.id.equals(id)) throw new IOException("request");
                    byte[] bytes = Base64.getDecoder().decode(p.get("data").getAsString());
                    if (bytes.length != Math.min(TransferPayload.CHUNK, u.size - u.bytes.size()) || p.get("offset").getAsInt() != u.bytes.size()
                            || u.bytes.size() + bytes.length > u.size) throw new IOException("sequence");
                    u.bytes.writeBytes(bytes);
                    if (u.bytes.size() == u.size) {
                        uploads.remove(who);
                        byte[] original = u.bytes.toByteArray();
                        ImageStore.validate(original);
                        byte[] stored = ImageCompression.compress(original, config.compressionFormat, config.compressionLevel);
                        String hash = store.put(stored);
                        reply(player, "stored", id, "reference", "mcimage://" + store.serverId + "/" + hash,
                                "sourceHash", ImageStore.hash(original), "originalBytes", original.length, "storedBytes", stored.length);
                    } else {
                        uploads.put(who, new Upload(u.id, u.size, u.bytes, u.started, now));
                        reply(player, "next", id, "offset", u.bytes.size());
                    }
                }
                case "get" -> {
                    if (!store.serverId.equals(p.get("server").getAsString())) throw new IOException("server");
                    if (downloads.containsKey(who) || inFlight() + config.maxFileBytes > 64L * 1024 * 1024) throw new IOException("busy");
                    String hash = p.get("hash").getAsString();
                    byte[] data = store.get(hash);
                    downloads.put(who, new Download(id, hash, data, now, now, 0));
                    reply(player, "data_begin", id, "size", data.length, "hash", hash);
                }
                case "read" -> {
                    Download d = downloads.get(who);
                    if (d == null || !d.id.equals(id)) throw new IOException("request");
                    int offset = p.get("offset").getAsInt();
                    if (offset != d.nextOffset || offset < 0 || offset >= d.bytes.length || offset % TransferPayload.CHUNK != 0) throw new IOException("sequence");
                    int end = Math.min(offset + TransferPayload.CHUNK, d.bytes.length);
                    reply(player, "data", id, "offset", offset, "data", Base64.getEncoder().encodeToString(Arrays.copyOfRange(d.bytes, offset, end)));
                    if (end == d.bytes.length) downloads.remove(who);
                    else downloads.put(who, new Download(d.id, d.hash, d.bytes, d.started, now, end));
                }
                case "cancel" -> { uploads.remove(who); downloads.remove(who); }
                default -> throw new IOException("request");
            }
        } catch (Exception e) {
            uploads.remove(who); downloads.remove(who);
            String reason = e instanceof IOException ? e.getMessage() : "request";
            if (!Set.of("busy", "disabled", "size", "rate", "sequence", "format", "pixels", "quota", "missing", "hash", "server", "reference", "request", "compression").contains(reason == null ? "" : reason)) reason = "storage";
            reply(player, "error", id, "reason", reason);
        }
    }
}
