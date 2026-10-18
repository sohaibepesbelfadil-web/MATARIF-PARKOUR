package com.matarif.parkour.util;

/** Keeps all timer displays consistent across action bars, titles, messages and leaderboards. */
public final class TimeFormatter {
    private TimeFormatter() { }
    public static String format(long millis) {
        millis = Math.max(0L, millis);
        long minutes = millis / 60_000L;
        long seconds = (millis / 1_000L) % 60L;
        long ms = millis % 1_000L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, ms);
    }
}
