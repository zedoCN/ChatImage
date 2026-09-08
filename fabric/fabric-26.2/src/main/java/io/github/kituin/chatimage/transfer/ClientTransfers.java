package io.github.kituin.chatimage.transfer;

import com.google.gson.*;
import io.github.kituin.ChatImageCode.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.*;

/** Client state is confined to the render thread. Uploads require an explicit send or command. */
public final class ClientTransfers {
    private static final Pattern REFERENCE = Pattern.compile("mcimage://([a-f0-9-]{36})/([a-f0-9]{64})");
    private static final Pattern LOCAL = Pattern.compile("file:/+[^,\\]\\s]+");
    private static String serverId;
    private static int maxBytes, window = 1;
    private static boolean enabled, preparing;
    private static volatile long generation;
    private static Pending active;
    private static final ArrayDeque<String> queue = new ArrayDeque<>();
    private static final Map<String, Long> retryAfter = new LinkedHashMap<>();
    private static final Map<String, String> errors = new LinkedHashMap<>();
    private static final class Pending {
        final String id = UUID.randomUUID().toString();
        final long started = System.currentTimeMillis();
        long progress = started;
        byte[] upload;
        String reference;
        int size;
        int sent, transferred, batchWindow = 1;
        boolean processing;
        long lastDisplay;
        ByteArrayOutputStream download;
        Consumer<String> success;
        Pending(byte[] upload, String reference, Consumer<String> success) {
            this.upload = upload; this.reference = reference; this.success = success;
        }
    }
    private static Minecraft mc() { return Minecraft.getInstance(); }
    private static void send(String op, String id, Object... fields) {
        JsonObject p = new JsonObject(); p.addProperty("op", op); p.addProperty("id", id);
        for (int i = 0; i < fields.length; i += 2) p.addProperty(fields[i].toString(), fields[i + 1].toString());
        ClientPlayNetworking.send(new TransferPayload(p.toString()));
    }
    private static void message(String key, Object... args) {
        Component text = Component.translatable("transfer.chatimage." + key, args);
        if ((key.equals("preparing") || key.equals("progress") || key.equals("speed") || key.equals("processing")) && mc().player != null) mc().gui.hud.setOverlayMessage(text, false);
        else mc().gui.hud.getChat().addClientSystemMessage(text);
    }
    private static void reset() {
        generation++; serverId = null; maxBytes = 0; window = 1; enabled = false; active = null; preparing = false;
        queue.clear(); retryAfter.clear(); errors.clear();
    }
    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            reset();
            if (ClientPlayNetworking.canSend(TransferPayload.ID)) send("hello", UUID.randomUUID().toString());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientPlayNetworking.registerGlobalReceiver(TransferPayload.ID, (payload, context) -> receive(payload.json()));
        ClientSendMessageEvents.ALLOW_CHAT.register(ClientTransfers::allowChat);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (active != null) displayProgress();
            if (active != null && (System.currentTimeMillis() - active.progress > 90_000 || System.currentTimeMillis() - active.started > 1_800_000)) fail("timeout");
            if (active == null && !preparing && !queue.isEmpty()) beginDownload(queue.removeFirst());
        });
    }
    public static boolean isReference(String value) { return REFERENCE.matcher(value).matches(); }
    public static void load(String reference) {
        mc().execute(() -> {
            if (!isReference(reference) || ClientStorage.ContainImageAndCheck(reference)) return;
            if (System.currentTimeMillis() < retryAfter.getOrDefault(reference, 0L)) return;
            if (active != null && reference.equals(active.reference) || queue.contains(reference)) return;
            if (queue.size() >= 16) return;
            errors.remove(reference);
            ClientStorage.AddImageError(reference, ChatImageFrame.FrameError.LOADING);
            queue.addLast(reference);
        });
    }
    public static String code(String reference) { return "[[CICode,url=" + reference + "]]"; }
    private static boolean supported() {
        if (serverId == null || !ClientPlayNetworking.canSend(TransferPayload.ID)) { message("unsupported"); return false; }
        if (!enabled) { message("disabled"); return false; }
        return true;
    }
    public static void uploadCommand(String argument) {
        if (argument.equals("clipboard")) {
            String pasted = io.github.kituin.chatimage.paste.PasteToolkit.getPasteCompat().doPaste();
            Matcher matcher = LOCAL.matcher(pasted == null ? "" : pasted);
            if (!matcher.find()) { message("clipboard"); return; }
            argument = matcher.group();
        }
        upload(argument, reference -> {
            String code = code(reference);
            // Display the raw URI separately so the existing CICode renderer cannot eat the copy action.
            mc().gui.hud.getChat().addClientSystemMessage(Component.translatable("transfer.chatimage.result", reference)
                    .append(Component.translatable("transfer.chatimage.copy").withStyle(s -> s.withClickEvent(new ClickEvent.CopyToClipboard(code))))
                    .append(Component.translatable("transfer.chatimage.insert").withStyle(s -> s.withClickEvent(new ClickEvent.SuggestCommand(code)))));
        });
    }
    public static void upload(String input, Consumer<String> done) {
        if (!supported()) return;
        if (active != null || preparing) { message("busy"); return; }
        preparing = true;
        long session = generation;
        final String argument = input;
        int limit = maxBytes;
        message("preparing");
        CompletableFuture.supplyAsync(() -> {
            try {
                String path = argument;
                if (path.startsWith("\"") && path.endsWith("\"")) path = path.substring(1, path.length() - 1);
                Path file = path.startsWith("file:") ? Path.of(URI.create(path)) : Path.of(path);
                if (!Files.isRegularFile(file)) throw new IOException("file");
                return UploadPreparation.prepare(file, limit, UploadOptions.get().autoCompress, UploadOptions.get().maxSourceBytes);
            } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        }).whenComplete((bytes, error) -> mc().execute(() -> {
            if (session != generation) return;
            preparing = false;
            if (error != null) {
                String reason = error.getCause() instanceof IOException ? error.getCause().getMessage() : "file";
                message("limits", TransferUnits.bytes(limit));
                message("error", Component.translatable("transfer.chatimage.reason." + (Set.of("size", "format", "pixels", "animation", "source", "compression").contains(reason == null ? "" : reason) ? reason : "file")));
                return;
            }
            if (bytes.originalBytes() != bytes.bytes().length) message("precompressed", TransferUnits.bytes(bytes.originalBytes()), TransferUnits.bytes(bytes.bytes().length));
            active = new Pending(bytes.bytes(), null, done);
            send("begin", active.id, "size", bytes.bytes().length, "window", Math.max(1, Math.min(window, UploadOptions.get().transferWindow)));
        }));
    }
    public static boolean allowChat(String original) {
        Matcher matcher = LOCAL.matcher(original);
        if (!matcher.find()) return true;
        // One image per chat send keeps references below Minecraft's chat message length limit.
        String local = matcher.group(); int start = matcher.start(), end = matcher.end();
        boolean wrapped = original.lastIndexOf("[[CICode,", start) > original.lastIndexOf("]]", start);
        if (matcher.find()) { message("one"); return false; }
        String prospective = original.substring(0, start) + "mcimage://" + "0".repeat(36) + "/" + "0".repeat(64) + original.substring(end);
        if (prospective.length() + (wrapped ? 0 : 15) > 256) { message("length"); return false; }
        upload(local, reference -> {
            if (mc().player != null) mc().player.connection.sendChat(original.substring(0, start) + (wrapped ? reference : code(reference)) + original.substring(end));
        });
        return false;
    }
    private static DiskImageCache diskCache() {
        return new DiskImageCache(Path.of(io.github.kituin.chatimage.client.ChatImageClient.CONFIG.cachePath).resolve("server-images"), UploadOptions.get().diskCacheBytes);
    }
    private static void beginDownload(String reference) {
        preparing = true; long session = generation;
        CompletableFuture.supplyAsync(() -> {
            try { return diskCache().get(reference, UploadOptions.get().maxSourceBytes); }
            catch (IOException e) { return null; }
        }).whenComplete((bytes, error) -> mc().execute(() -> {
            if (session != generation) return;
            preparing = false;
            if (bytes != null) { FileImageHandler.loadFile(bytes, reference); return; }
            Matcher m = REFERENCE.matcher(reference);
            if (!m.matches() || !enabled || !m.group(1).equals(serverId)) {
                imageError(reference, !enabled ? "disabled" : "server"); return;
            }
            active = new Pending(null, reference, null);
            send("get", active.id, "server", m.group(1), "hash", m.group(2), "window", Math.max(1, Math.min(window, UploadOptions.get().transferWindow)));
        }));
    }
    private static void displayProgress() {
        long now = System.currentTimeMillis();
        if (now - active.lastDisplay < 500) return;
        active.lastDisplay = now;
        if (active.processing) { message("processing"); return; }
        int total = active.upload != null ? active.upload.length : active.size;
        if (total <= 0) return;
        int percent = (int) (100L * active.transferred / total);
        double speed = active.transferred / Math.max(0.1, (now - active.started) / 1000.0);
        message("speed", Component.translatable("transfer.chatimage." + (active.upload != null ? "uploading" : "downloading")), Integer.toString(percent), TransferUnits.speed(speed));
    }
    public static Component errorFor(String reference) {
        String reason = errors.get(reference);
        return reason == null ? null : Component.translatable("transfer.chatimage.error", Component.translatable("transfer.chatimage.reason." + reason));
    }
    private static void imageError(String reference, String reason) {
        if (errors.size() >= 128) { String first = errors.keySet().iterator().next(); errors.remove(first); retryAfter.remove(first); }
        errors.put(reference, reason);
        retryAfter.put(reference, System.currentTimeMillis() + 60_000);
        ClientStorage.AddImageError(reference, ChatImageFrame.FrameError.FILE_LOAD_ERROR);
    }
    private static void fail(String reason) {
        if (active != null) {
            if (ClientPlayNetworking.canSend(TransferPayload.ID)) send("cancel", active.id);
            if (active.reference != null) {
                imageError(active.reference, reason);
            } else message("error", Component.translatable("transfer.chatimage.reason." + reason));
        }
        active = null;
    }
    private static void receive(String raw) {
        try {
            JsonObject p = JsonParser.parseString(raw).getAsJsonObject();
            String op = p.get("op").getAsString();
            if (op.equals("caps")) {
                serverId = UUID.fromString(p.get("server").getAsString()).toString();
                maxBytes = p.get("max").getAsInt();
                window = p.has("window") ? Math.max(1, Math.min(8, p.get("window").getAsInt())) : 1;
                enabled = maxBytes >= 8 && maxBytes < Integer.MAX_VALUE - 8 && p.get("enabled").getAsBoolean(); return;
            }
            if (active == null || !active.id.equals(p.get("id").getAsString())) return;
            active.progress = System.currentTimeMillis();
            switch (op) {
                case "ready", "next" -> {
                    if (active.upload == null) throw new IOException();
                    if (op.equals("ready")) active.batchWindow = p.has("window") ? Math.max(1, Math.min(8, p.get("window").getAsInt())) : 1;
                    int offset = op.equals("ready") ? 0 : p.get("offset").getAsInt();
                    if (offset < 0 || offset >= active.upload.length || offset % TransferPayload.CHUNK != 0) throw new IOException();
                    if (offset != active.sent) throw new IOException();
                    active.transferred = offset;
                    for (int n = 0; n < active.batchWindow && active.sent < active.upload.length; n++) {
                        int start = active.sent, end = Math.min(start + TransferPayload.CHUNK, active.upload.length);
                        send("chunk", active.id, "offset", start, "data", Base64.getEncoder().encodeToString(Arrays.copyOfRange(active.upload, start, end)));
                        active.sent = end;
                    }
                    if (active.sent == active.upload.length) active.processing = true;
                }
                case "processing" -> { if (active.upload == null || active.sent != active.upload.length) throw new IOException(); active.processing = true; }
                case "stored" -> {
                    String reference = p.get("reference").getAsString();
                    if (active.upload == null || (!isReference(reference) || !reference.startsWith("mcimage://" + serverId + "/") || !ImageStore.hash(active.upload).equals(p.get("sourceHash").getAsString()))) throw new IOException();
                    message("stored", TransferUnits.bytes(p.get("originalBytes").getAsLong()), TransferUnits.bytes(p.get("storedBytes").getAsLong()));
                    if (reference.endsWith("/" + ImageStore.hash(active.upload))) {
                        byte[] cached = active.upload;
                        CompletableFuture.runAsync(() -> { try { diskCache().put(reference, cached); } catch (IOException ignored) { } });
                    }
                    Consumer<String> done = active.success; active = null; done.accept(reference);
                }
                case "data_begin" -> {
                    if (active.reference == null) throw new IOException();
                    active.size = p.get("size").getAsInt();
                    if (active.size < 8 || active.size > UploadOptions.get().maxSourceBytes || !active.reference.endsWith("/" + p.get("hash").getAsString())) throw new IOException();
                    active.batchWindow = p.has("window") ? Math.max(1, Math.min(8, p.get("window").getAsInt())) : 1;
                    active.download = new ByteArrayOutputStream(active.size);
                    send("read", active.id, "offset", 0);
                }
                case "data" -> {
                    if (active.download == null) throw new IOException();
                    byte[] bytes = Base64.getDecoder().decode(p.get("data").getAsString());
                    if (bytes.length == 0 || bytes.length > TransferPayload.CHUNK || active.download.size() != p.get("offset").getAsInt()
                            || active.download.size() + bytes.length > active.size) throw new IOException();
                    active.download.writeBytes(bytes);
                    active.transferred = active.download.size();
                    if (active.download.size() == active.size) {
                        byte[] image = active.download.toByteArray(); String reference = active.reference;
                        if (!reference.endsWith("/" + ImageStore.hash(image))) throw new IOException();
                        active = null;
                        long session = generation;
                        CompletableFuture.runAsync(() -> {
                            try {
                                ImageStore.validate(image);
                                try { diskCache().put(reference, image); } catch (IOException ignored) { }
                                if (session == generation) {
                                    FileImageHandler.loadFile(image, reference);
                                }
                            } catch (IOException e) { mc().execute(() -> ClientStorage.AddImageError(reference, ChatImageFrame.FrameError.FILE_LOAD_ERROR)); }
                        });
                    } else if (active.download.size() % (TransferPayload.CHUNK * active.batchWindow) == 0) send("read", active.id, "offset", active.download.size());
                }
                case "error" -> fail(p.get("reason").getAsString());
                default -> throw new IOException();
            }
        } catch (Exception e) { fail("request"); }
    }
}
