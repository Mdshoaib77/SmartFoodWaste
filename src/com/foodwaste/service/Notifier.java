package com.foodwaste.service;

import com.foodwaste.dao.MessageDAO;
import java.sql.Connection;
import java.sql.SQLException;

/** Writes automatic system notifications into a user's inbox, inside the caller's transaction (commit/rollback together). */
public final class Notifier {
    private static final MessageDAO MESSAGES = new MessageDAO();

    private Notifier() { }

    public static void send(Connection c, int userId, String subject, String body) throws SQLException {
        MESSAGES.insert(c, null, userId, cut(subject, 150), cut(body, 1000));
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
