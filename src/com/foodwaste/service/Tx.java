package com.foodwaste.service;

import com.foodwaste.config.DBConnection;
import java.sql.Connection;
import java.sql.SQLException;

/** Connection / transaction helper used by every service. */
public final class Tx {
    private Tx() { }

    public interface Work<T> { T run(Connection c) throws SQLException; }

    /** Read-only work on a fresh connection. */
    public static <T> T read(Work<T> work) {
        try (Connection c = DBConnection.get()) {
            return work.run(c);
        } catch (SQLException e) {
            throw new ServiceException("Database error: " + e.getMessage(), e);
        }
    }

    /** Runs the work in one transaction: commit on success, rollback on any failure. */
    public static <T> T tx(Work<T> work) {
        Connection c = null;
        try {
            c = DBConnection.get();
            c.setAutoCommit(false);
            T result = work.run(c);
            c.commit();
            return result;
        } catch (SQLException e) {
            rollback(c);
            throw new ServiceException("Database error: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            rollback(c);
            throw e;
        } finally {
            if (c != null) {
                try { c.close(); } catch (SQLException ignored) { /* nothing to do */ }
            }
        }
    }

    private static void rollback(Connection c) {
        if (c != null) {
            try { c.rollback(); } catch (SQLException ignored) { /* connection already broken */ }
        }
    }
}
