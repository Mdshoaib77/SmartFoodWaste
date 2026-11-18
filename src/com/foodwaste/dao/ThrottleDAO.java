package com.foodwaste.dao;

import com.foodwaste.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** Data access for LOGIN_THROTTLE (failed-login counters and temporary locks). */
public class ThrottleDAO {
    /** Returns the lock expiry when the identity is currently locked, otherwise null. */
    public LocalDateTime lockedUntil(Connection c, String kind, String identity, LocalDateTime now) throws SQLException {
        return Db.one(c, "SELECT LockedUntil FROM login_throttle WHERE Kind = ? AND Identity = ? AND LockedUntil IS NOT NULL AND LockedUntil > ?",
            rs -> Db.ts(rs, "LockedUntil"), kind, identity, now);
    }

    /**
     * Records one failed attempt atomically and returns the new consecutive-failure count.
     * An expired lock starts a fresh count. When the count reaches maxFailures the identity is locked until lockUntil.
     */
    public int recordFailure(Connection c, String kind, String identity, LocalDateTime now, int maxFailures, LocalDateTime lockUntil)
            throws SQLException {
        Db.update(c, "INSERT INTO login_throttle (Kind, Identity, FailedCount, LockedUntil, UpdatedAt) VALUES (?,?,0,NULL,?) "
            + "ON DUPLICATE KEY UPDATE UpdatedAt = UpdatedAt", kind, identity, now);
        // a lock that has run out starts a new series of attempts
        Db.update(c, "UPDATE login_throttle SET FailedCount = 0, LockedUntil = NULL WHERE Kind = ? AND Identity = ? "
            + "AND LockedUntil IS NOT NULL AND LockedUntil <= ?", kind, identity, now);
        // LockedUntil is assigned BEFORE FailedCount so that it still sees the old counter (MySQL evaluates SET left to right)
        Db.update(c, "UPDATE login_throttle SET LockedUntil = CASE WHEN FailedCount + 1 >= ? THEN ? ELSE LockedUntil END, "
            + "FailedCount = FailedCount + 1, UpdatedAt = ? WHERE Kind = ? AND Identity = ?",
            maxFailures, lockUntil, now, kind, identity);
        return (int) Db.scalar(c, "SELECT FailedCount FROM login_throttle WHERE Kind = ? AND Identity = ?", kind, identity);
    }

    public void clear(Connection c, String kind, String identity) throws SQLException {
        Db.update(c, "DELETE FROM login_throttle WHERE Kind = ? AND Identity = ?", kind, identity);
    }
}
