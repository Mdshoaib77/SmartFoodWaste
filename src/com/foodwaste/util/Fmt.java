package com.foodwaste.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Fmt {
    public static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Fmt() { }

    public static String dt(LocalDateTime t) { return t == null ? "-" : t.format(DT); }

    public static String remaining(LocalDateTime expiry, LocalDateTime now) {
        if (expiry == null) return "-";
        Duration d = Duration.between(now, expiry);
        if (d.isNegative() || d.isZero()) return "expired";
        return d.toHours() + "h " + String.format("%02d", d.toMinutes() % 60) + "m";
    }

    public static String nz(String s) { return (s == null || s.isEmpty()) ? "-" : s; }
}
