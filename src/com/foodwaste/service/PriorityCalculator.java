package com.foodwaste.service;

import com.foodwaste.util.Constants;
import java.time.Duration;
import java.time.LocalDateTime;

/** FR-04: High below 4 hours remaining, Medium below 12 hours, Low otherwise (exact, to the second). */
public final class PriorityCalculator {
    private static final Duration HIGH = Duration.ofHours(Constants.HIGH_PRIORITY_HOURS);
    private static final Duration MEDIUM = Duration.ofHours(Constants.MEDIUM_PRIORITY_HOURS);

    private PriorityCalculator() { }

    public static String calculate(LocalDateTime expiry, LocalDateTime now) {
        Duration left = Duration.between(now, expiry);
        if (left.compareTo(HIGH) < 0) return Constants.PRIORITY_HIGH;
        if (left.compareTo(MEDIUM) < 0) return Constants.PRIORITY_MEDIUM;
        return Constants.PRIORITY_LOW;
    }
}
