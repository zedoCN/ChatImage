package io.github.kituin.chatimage.animation;

import com.google.gson.GsonBuilder;
import io.github.kituin.ChatImageCode.ChatImageFrame;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;

public final class Playback {
    public static boolean automatic = true;
    public static int manualFps = 10;
    private static final Map<ChatImageFrame<?>, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final class State {
        final Timeline timeline;
        long start = -1;
        State(Timeline timeline) { this.timeline = timeline; }
    }
    private static final class Options { boolean automatic = true; int manualFps = 10; }
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("chatimage-playback.json"); }
    public static void load() {
        try {
            if (!Files.exists(file())) { save(); return; }
            Options options = new GsonBuilder().create().fromJson(Files.readString(file()), Options.class);
            if (options != null) { automatic = options.automatic; manualFps = Math.clamp(options.manualFps, 1, 60); }
        } catch (Exception e) { io.github.kituin.chatimage.ChatImage.LOGGER.warn("Cannot read ChatImage playback options", e); }
    }
    public static void save() {
        try {
            Options options = new Options(); options.automatic = automatic; options.manualFps = manualFps;
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(options));
        } catch (Exception e) { io.github.kituin.chatimage.ChatImage.LOGGER.warn("Cannot save ChatImage playback options", e); }
    }
    public static void attach(ChatImageFrame<?> frame, int[] durations, int plays) { STATES.put(frame, new State(new Timeline(durations, plays))); }
    public static void advance(ChatImageFrame<?> frame) {
        State state = STATES.get(frame);
        if (state == null) return;
        long now = System.nanoTime();
        if (state.start == -1) state.start = now;
        frame.setIndex(state.timeline.indexAt((now - state.start) / 1_000_000L, automatic, manualFps));
    }
}
