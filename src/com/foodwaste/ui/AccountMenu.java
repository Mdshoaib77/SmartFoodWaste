package com.foodwaste.ui;

import com.foodwaste.model.Admin;
import com.foodwaste.model.Message;
import com.foodwaste.model.User;
import com.foodwaste.service.AuthService;
import com.foodwaste.service.MessageService;
import java.util.List;

/** Console screens shared by several roles: change password and the message inbox. */
final class AccountMenu {
    private static final AuthService AUTH = new AuthService();
    private static final MessageService MESSAGES = new MessageService();

    private AccountMenu() { }

    static void changePassword(User user) {
        String old = ConsoleIO.askPassword("Current password");
        String pw = ConsoleIO.askPassword("New password (min 6 characters)");
        if (!pw.equals(ConsoleIO.askPassword("Confirm new password"))) { ConsoleIO.error("Passwords do not match."); return; }
        AUTH.changeUserPassword(user, old, pw);
        ConsoleIO.success("Password changed.");
    }

    static void changePassword(Admin admin) {
        String old = ConsoleIO.askPassword("Current password");
        String pw = ConsoleIO.askPassword("New password (min 6 characters)");
        if (!pw.equals(ConsoleIO.askPassword("Confirm new password"))) { ConsoleIO.error("Passwords do not match."); return; }
        AUTH.changeAdminPassword(admin, old, pw);
        ConsoleIO.success("Password changed.");
    }

    /** Shows the inbox (newest first) and marks everything shown as read. */
    static void inbox(User user) {
        List<Message> list = MESSAGES.inbox(user);
        Views.messages(list);
        if (!list.isEmpty() && MESSAGES.markAllRead(user) > 0) ConsoleIO.info("All messages are now marked as read.");
    }

    static String inboxLabel(User user) {
        long n = MESSAGES.unreadCount(user);
        return n > 0 ? "Messages / notifications (" + n + " unread)" : "Messages / notifications";
    }
}
