package com.foodwaste.service;

import com.foodwaste.util.Constants;
import com.foodwaste.util.TimeSlot;
import java.util.Locale;

/** Input validation shared by the services. Every failure is a ServiceException with a clear message. */
public final class Check {
    private Check() { }

    /** Trims; fails when empty or longer than max. */
    public static String text(String value, String label, int max) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) throw new ServiceException(label + " is required.");
        if (v.length() > max) throw new ServiceException(label + " must be at most " + max + " characters.");
        return v;
    }

    /** Optional text: trimmed, may be empty, never longer than max. */
    public static String optional(String value, String label, int max) {
        String v = value == null ? "" : value.trim();
        if (v.length() > max) throw new ServiceException(label + " must be at most " + max + " characters.");
        return v;
    }

    public static void password(String pw) {
        if (pw == null || pw.length() < Constants.MIN_PASSWORD_LENGTH)
            throw new ServiceException("Password must be at least " + Constants.MIN_PASSWORD_LENGTH + " characters.");
        if (pw.length() > 100) throw new ServiceException("Password must be at most 100 characters.");
    }

    public static String slot(String text) {
        try {
            return TimeSlot.normalise(text);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    /** Capacity must be a whole number greater than zero. */
    public static int capacity(Integer capacity) {
        if (capacity == null) throw new ServiceException("Capacity is required for Receiver/NGO accounts.");
        if (capacity <= 0) throw new ServiceException("Capacity must be greater than zero.");
        if (capacity > 1_000_000) throw new ServiceException("Capacity must be at most 1000000.");
        return capacity;
    }

    /** Area comparison key: trimmed + lower-cased (areas are compared case-insensitively). */
    public static String areaKey(String area) {
        return area == null ? "" : area.trim().toLowerCase(Locale.ROOT);
    }
}
