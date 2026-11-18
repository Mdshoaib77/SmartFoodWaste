package com.foodwaste.dao;

import com.foodwaste.model.Message;
import com.foodwaste.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Data access for the MESSAGE table. */
public class MessageDAO {
    private static final String SEL =
        "SELECT m.MessageID, m.SenderAdminID, a.Username AS SenderName, m.RecipientUserID, u.Name AS RecipientName, "
        + "m.Subject, m.Body, m.CreatedAt, m.IsRead FROM message m "
        + "LEFT JOIN admin a ON m.SenderAdminID = a.AdminID JOIN `user` u ON m.RecipientUserID = u.UserID ";

    private static final Db.RowMapper<Message> MAP = rs -> {
        Message m = new Message();
        m.setMessageId(rs.getLong("MessageID"));
        int s = rs.getInt("SenderAdminID");
        m.setSenderAdminId(rs.wasNull() ? null : s);
        String sn = rs.getString("SenderName");
        m.setSenderName(m.getSenderAdminId() == null ? "System" : (sn == null ? "Administrator" : "Admin " + sn));
        m.setRecipientUserId(rs.getInt("RecipientUserID"));
        m.setRecipientName(rs.getString("RecipientName"));
        m.setSubject(rs.getString("Subject"));
        m.setBody(rs.getString("Body"));
        m.setCreatedAt(Db.ts(rs, "CreatedAt"));
        m.setRead(rs.getBoolean("IsRead"));
        return m;
    };

    public long insert(Connection c, Integer senderAdminId, int recipientUserId, String subject, String body) throws SQLException {
        return Db.insert(c, "INSERT INTO message (SenderAdminID, RecipientUserID, Subject, Body, CreatedAt, IsRead) VALUES (?,?,?,?,?,0)",
            senderAdminId, recipientUserId, subject, body, LocalDateTime.now());
    }

    public List<Message> inbox(Connection c, int userId) throws SQLException {
        return Db.list(c, SEL + "WHERE m.RecipientUserID = ? ORDER BY m.CreatedAt DESC, m.MessageID DESC LIMIT 500", MAP, userId);
    }

    public Message findById(Connection c, long id) throws SQLException {
        return Db.one(c, SEL + "WHERE m.MessageID = ?", MAP, id);
    }

    public long unreadCount(Connection c, int userId) throws SQLException {
        return Db.scalar(c, "SELECT COUNT(*) FROM message WHERE RecipientUserID = ? AND IsRead = 0", userId);
    }

    /** Only the recipient's own message can be marked read. */
    public int markRead(Connection c, int userId, long messageId) throws SQLException {
        return Db.update(c, "UPDATE message SET IsRead = 1 WHERE MessageID = ? AND RecipientUserID = ?", messageId, userId);
    }

    public int markAllRead(Connection c, int userId) throws SQLException {
        return Db.update(c, "UPDATE message SET IsRead = 1 WHERE RecipientUserID = ? AND IsRead = 0", userId);
    }

    /** Admin view of recently sent administrator messages (system notifications excluded). */
    public List<Message> recentAdminMessages(Connection c, int limit) throws SQLException {
        return Db.list(c, SEL + "WHERE m.SenderAdminID IS NOT NULL ORDER BY m.MessageID DESC LIMIT ?", MAP, limit);
    }
}
