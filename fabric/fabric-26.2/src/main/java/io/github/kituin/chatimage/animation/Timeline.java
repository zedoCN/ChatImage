package io.github.kituin.chatimage.animation;

/** Millisecond timeline independent of render FPS. Zero plays means repeat forever. */
public final class Timeline {
    private final int[] durations;
    private final int plays;
    public Timeline(int[] durations, int plays) {
        if (durations.length == 0 || plays < 0) throw new IllegalArgumentException();
        this.durations = durations.clone(); this.plays = plays;
        for (int i = 0; i < durations.length; i++) this.durations[i] = durations[i] <= 0 ? 100 : durations[i];
    }
    public int indexAt(long elapsedMillis, boolean automatic, int manualFps) {
        if (!automatic) {
            long elapsed = Math.max(0, elapsedMillis);
            long index = elapsed / 1000 * Math.max(1, manualFps) + elapsed % 1000 * Math.max(1, manualFps) / 1000;
            if (plays != 0 && index / durations.length >= plays) return durations.length - 1;
            return (int) (index % durations.length);
        }
        long cycle = 0;
        for (int duration : durations) cycle += automatic ? duration : Math.max(1, 1000 / Math.max(1, manualFps));
        long elapsed = Math.max(0, elapsedMillis);
        if (plays != 0 && elapsed / cycle >= plays) return durations.length - 1;
        elapsed %= cycle;
        for (int i = 0; i < durations.length; i++) {
            elapsed -= automatic ? durations[i] : Math.max(1, 1000 / Math.max(1, manualFps));
            if (elapsed < 0) return i;
        }
        return durations.length - 1;
    }
}
