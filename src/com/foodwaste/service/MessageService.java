package com.foodwaste.service;

import com.foodwaste.dao.ActivityLogDAO;
import com.foodwaste.dao.MessageDAO;
import com.foodwaste.dao.UserDAO;
import com.foodwaste.model.Admin;
import com.foodwaste.model.Message;
import com.foodwaste.model.User;
import com.foodwaste.util.Constants;
import java.util.List;

/** Administrative messages and the inbox that also shows automatic system notifications. */
public class MessageService {
    private final MessageDAO messageDAO = new MessageDAO();
    private final UserDAO userDAO = new UserDAO();
    private final ActivityLogDAO logDAO = new ActivityLogDAO();

    // ------------------------------------------------------------------ inbox (Donor / Receiver)

    public List<Message> inbox(User user) {
        return Tx.read(c -> messageDAO.inbox(c, Guard.anyUser(c, user).getUserId()));
    }

    public long unreadCount(User user) {
        return Tx.read(c -> messageDAO.unreadCount(c, Guard.anyUser(c, user).getUserId()));
    }

    public void markRead(User user, long messageId) {
        Tx.tx(c -> {
            messageDAO.markRead(c, Guard.anyUser(c, user).getUserId(), messageId);
            return null;
        });
    }

    public int markAllRead(User user) {
        return Tx.tx(c -> messageDAO.markAllRead(c, Guard.anyUser(c, user).getUserId()));
    }

    // ------------------------------------------------------------------ administrator sending

    /** Sends one message to a single approved Donor or Receiver/NGO. */
    public void sendToUser(Admin admin, int userId, String subject, String body) {
        final String s = Check.text(subject, "Subject", 150);
        final String b = Check.text(body, "Message", 1000);
        Tx.tx(c -> {
            Admin a = Guard.admin(c, admin);
            User u = userDAO.findById(c, userId);
            if (u == null) throw new ServiceException("User #" + userId + " does not exist.");
            messageDAO.insert(c, a.getAdminId(), userId, s, b);
            logDAO.insert(c, "Admin", a.getAdminId(), "MESSAGE_SENT", "To user #" + userId + ": " + s);
            return null;
        });
    }

    /** Broadcasts to every approved Donor, Receiver/NGO, or both (audience = Constants.AUDIENCE_*). Returns recipients count. */
    public int broadcast(Admin admin, String audience, String subject, String body) {
        final String s = Check.text(subject, "Subject", 150);
        final String b = Check.text(body, "Message", 1000);
        if (!Constants.AUDIENCE_DONORS.equals(audience) && !Constants.AUDIENCE_RECEIVERS.equals(audience)
            && !Constants.AUDIENCE_EVERYONE.equals(audience))
            throw new ServiceException("Unknown audience '" + audience + "'.");
        return Tx.tx(c -> {
            Admin a = Guard.admin(c, admin);
            int n = 0;
            if (!Constants.AUDIENCE_RECEIVERS.equals(audience)) n += sendAll(c, a, Constants.ROLE_DONOR, s, b);
            if (!Constants.AUDIENCE_DONORS.equals(audience)) n += sendAll(c, a, Constants.ROLE_RECEIVER, s, b);
            logDAO.insert(c, "Admin", a.getAdminId(), "MESSAGE_BROADCAST", audience + " (" + n + " recipients): " + s);
            return n;
        });
    }

    private int sendAll(java.sql.Connection c, Admin a, String role, String s, String b) throws java.sql.SQLException {
        int n = 0;
        for (User u : userDAO.findByRoleAndStatus(c, role, Constants.USER_APPROVED)) {
            messageDAO.insert(c, a.getAdminId(), u.getUserId(), s, b);
            n++;
        }
        return n;
    }

    public List<Message> recentAdminMessages(Admin admin, int limit) {
        return Tx.read(c -> {
            Guard.admin(c, admin);
            return messageDAO.recentAdminMessages(c, limit);
        });
    }
}
