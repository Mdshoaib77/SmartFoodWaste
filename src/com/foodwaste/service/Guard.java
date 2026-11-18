package com.foodwaste.service;

import com.foodwaste.dao.AdminDAO;
import com.foodwaste.dao.UserDAO;
import com.foodwaste.model.Admin;
import com.foodwaste.model.User;
import com.foodwaste.util.Constants;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Role-based access control for the SERVICE layer (FR-02). Every service method that is restricted to a role calls one of
 * these first. The caller object is never trusted: it is re-loaded from the database, so a rejected, deleted or
 * re-roled account (or a forged object) is refused even when the GUI/console still holds an old session.
 */
public final class Guard {
    private static final AdminDAO ADMINS = new AdminDAO();
    private static final UserDAO USERS = new UserDAO();

    private Guard() { }

    public static Admin admin(Connection c, Admin caller) throws SQLException {
        if (caller == null) throw new ServiceException("Access denied: administrator login required.");
        Admin a = ADMINS.findById(c, caller.getAdminId());
        if (a == null || !a.getUsername().equals(caller.getUsername()))
            throw new ServiceException("Access denied: administrator login required.");
        return a;
    }

    /** Requires an Approved account that really has the given role. */
    public static User user(Connection c, User caller, String role) throws SQLException {
        if (caller == null) throw new ServiceException("Access denied: please sign in.");
        User u = USERS.findById(c, caller.getUserId());
        if (u == null || !u.getRole().equals(role))
            throw new ServiceException("Access denied: this function is only for " + role + " accounts.");
        if (!Constants.USER_APPROVED.equals(u.getStatus()))
            throw new ServiceException("Access denied: your account is not approved.");
        return u;
    }

    /** Any approved Donor or Receiver/NGO. */
    public static User anyUser(Connection c, User caller) throws SQLException {
        if (caller == null) throw new ServiceException("Access denied: please sign in.");
        User u = USERS.findById(c, caller.getUserId());
        if (u == null || !Constants.USER_APPROVED.equals(u.getStatus()))
            throw new ServiceException("Access denied: your account is not approved.");
        return u;
    }
}
