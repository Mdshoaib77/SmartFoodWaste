package com.foodwaste.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pickup time slot in the fixed format HH:mm-HH:mm (24-hour clock, start before end). */
public final class TimeSlot {
    private static final Pattern P = Pattern.compile("^([01]\\d|2[0-3]):([0-5]\\d)\\s*-\\s*([01]\\d|2[0-3]):([0-5]\\d)$");

    private TimeSlot() { }

    /** Returns the normalised slot ("16:00-18:00") or throws IllegalArgumentException with a user-friendly message. */
    public static String normalise(String text) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Time slot is required (format HH:mm-HH:mm, e.g. 16:00-18:00).");
        Matcher m = P.matcher(s);
        if (!m.matches())
            throw new IllegalArgumentException("Invalid time slot '" + s + "'. Use HH:mm-HH:mm on a 24-hour clock, e.g. 16:00-18:00.");
        int start = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
        int end = Integer.parseInt(m.group(3)) * 60 + Integer.parseInt(m.group(4));
        if (start >= end) throw new IllegalArgumentException("Time slot start must be earlier than its end (e.g. 16:00-18:00).");
        return m.group(1) + ":" + m.group(2) + "-" + m.group(3) + ":" + m.group(4);
    }

    public static boolean isValid(String text) {
        try { normalise(text); return true; } catch (IllegalArgumentException e) { return false; }
    }
}
