package io.github.kituin.chatimage.transfer;

import java.util.Locale;

public final class TransferUnits {
    public static String bytes(double bytes) {
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        double value = Math.max(0, bytes); int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(Locale.ROOT, "%.2f %s", value, units[unit]);
    }
    public static String speed(double bytesPerSecond) { return bytes(bytesPerSecond) + "/s"; }
}
