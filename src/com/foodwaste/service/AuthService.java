package com.foodwaste.service;

import com.foodwaste.dao.ActivityLogDAO;
import com.foodwaste.dao.AdminDAO;
import com.foodwaste.dao.AreaDAO;
import com.foodwaste.dao.ThrottleDAO;
import com.foodwaste.dao.UserDAO;
import com.foodwaste.model.Admin;
import com.foodwaste.model.User;
import com.foodwaste.util.Constants;
import com.foodwaste.util.Fmt;
import com.foodwaste.util.PasswordUtil;
import java.time.LocalDateTime;
import java.util.List;

/** FR-01 / FR-02: registration, authentication (with login throttling), password change and role checks. */
public class AuthService {
    /** Seeded on a brand-new database only. The password is flagged "must change" and the login screen never shows it. */
    public static final String DEFAULT_ADMIN_USER = "admin";
    static final String DEFAULT_ADMIN_PASS = "admin123";

    private static final String KIND_USER = "User";
    private static final String KIND_ADMIN = "Admin";

    private final UserDAO userDAO = new UserDAO();
    private final AdminDAO adminDAO = new AdminDAO();
    private final AreaDAO areaDAO = new AreaDAO();
    private final ThrottleDAO throttleDAO = new ThrottleDAO();
    private final ActivityLogDAO logDAO = new ActivityLogDAO();

    /** Creates the default administrator on a brand-new database. Returns true if one was created. */
    public boolean ensureDefaultAdmin() {
        return Tx.tx(c -> {
            if (adminDAO.count(c) > 0) return false;
            int id = adminDAO.insert(c, DEFAULT_ADMIN_USER, PasswordUtil.hash(DEFAULT_ADMIN_PASS), "Administrator", true, null);
            logDAO.insert(c, "System", id, "ADMIN_SEEDED", "Default administrator account created");
            return true;
        });
    }

    /** The standard area list (single source: the areas table) for registration drop-downs. */
    public List<String> areas() {
        return Tx.read(areaDAO::findAll);
    }

    /**
     * Donors are active immediately; Receiver/NGO accounts stay Pending until an administrator approves them.
     * capacity is required (and must be above zero) for Receiver/NGO and ignored for Donors.
     */
    public User register(String name, String contact, String password, String role, String area, Integer capacity) {
        final String n = Check.text(name, "Name", 100);
        final String ct = Check.text(contact, "Contact", 100);
        final String rawArea = Check.text(area, "Area", 50);
        Check.password(password);
        if (!Constants.ROLE_DONOR.equals(role) && !Constants.ROLE_RECEIVER.equals(role))
            throw new ServiceException("Role must be Donor or Receiver/NGO.");
        final boolean receiver = Constants.ROLE_RECEIVER.equals(role);
        final Integer cap = receiver ? Integer.valueOf(Check.capacity(capacity)) : null;
        final String hash = PasswordUtil.hash(password);
        final String status = receiver ? Constants.USER_PENDING : Constants.USER_APPROVED;
        return Tx.tx(c -> {
            String official = areaDAO.findOfficial(c, rawArea);
            if (official == null) throw new ServiceException("Please choose an area from the list ('" + rawArea + "' is not a known area).");
            if (userDAO.findByContact(c, ct) != null)
                throw new ServiceException("An account with this contact already exists.");
            User u = new User();
            u.setName(n); u.setContact(ct); u.setPasswordHash(hash); u.setRole(role); u.setAreaId(official);
            u.setCapacity(cap); u.setStatus(status);
            u.setUserId(userDAO.insert(c, u));
            logDAO.insert(c, "User", u.getUserId(), "REGISTER", role + " account registered (" + status + ")");
            return u;
        });
    }

    public User loginUser(String contact, String password) {
        final String ct = contact == null ? "" : contact.trim();
        checkNotLocked(KIND_USER, ct);
        User u = Tx.read(c -> userDAO.findByContact(c, ct));
        if (u == null || !PasswordUtil.verify(password, u.getPasswordHash())) {
            failed(KIND_USER, ct, u == null ? null : u.getUserId());
            throw new ServiceException("Invalid contact or password.");
        }
        if (Constants.USER_PENDING.equals(u.getStatus()))
            throw new ServiceException("Your account is still pending administrator approval.");
        if (Constants.USER_REJECTED.equals(u.getStatus()))
            throw new ServiceException("Your account was rejected by the administrator.");
        final int id = u.getUserId();
        Tx.tx(c -> {
            throttleDAO.clear(c, KIND_USER, key(ct));
            logDAO.insert(c, "User", id, "LOGIN", u.getRole());
            return null;
        });
        return u;
    }

    public Admin loginAdmin(String username, String password) {
        final String un = username == null ? "" : username.trim();
        checkNotLocked(KIND_ADMIN, un);
        Admin a = Tx.read(c -> adminDAO.findByUsername(c, un));
        if (a == null || !PasswordUtil.verify(password, a.getPasswordHash())) {
            failed(KIND_ADMIN, un, a == null ? null : a.getAdminId());
            throw new ServiceException("Invalid administrator username or password.");
        }
        final int id = a.getAdminId();
        Tx.tx(c -> {
            throttleDAO.clear(c, KIND_ADMIN, key(un));
            logDAO.insert(c, "Admin", id, "LOGIN", "Administrator signed in");
            return null;
        });
        return a;
    }

    /** Verifies the old password, then stores the new one hashed (minimum 6 characters). */
    public void changeUserPassword(User caller, String oldPassword, String newPassword) {
        Check.password(newPassword);
        final String hash = PasswordUtil.hash(newPassword);
        Tx.tx(c -> {
            User u = Guard.anyUser(c, caller);
            if (!PasswordUtil.verify(oldPassword, u.getPasswordHash())) {
                logDAO.insert(c, "User", u.getUserId(), "PASSWORD_CHANGE_FAILED", "Wrong current password");
                throw new ServiceException("Current password is incorrect.");
            }
            if (PasswordUtil.verify(newPassword, u.getPasswordHash()))
                throw new ServiceException("The new password must be different from the current one.");
            userDAO.updatePassword(c, u.getUserId(), hash);
            logDAO.insert(c, "User", u.getUserId(), "PASSWORD_CHANGED", u.getRole());
            return null;
        });
    }

    public void changeAdminPassword(Admin caller, String oldPassword, String newPassword) {
        Check.password(newPassword);
        final String hash = PasswordUtil.hash(newPassword);
        Tx.tx(c -> {
            Admin a = Guard.admin(c, caller);
            if (!PasswordUtil.verify(oldPassword, a.getPasswordHash())) {
                logDAO.insert(c, "Admin", a.getAdminId(), "PASSWORD_CHANGE_FAILED", "Wrong current password");
                throw new ServiceException("Current password is incorrect.");
            }
            if (PasswordUtil.verify(newPassword, a.getPasswordHash()))
                throw new ServiceException("The new password must be different from the current one.");
            adminDAO.updatePassword(c, a.getAdminId(), hash);
            logDAO.insert(c, "Admin", a.getAdminId(), "PASSWORD_CHANGED", "Administrator");
            return null;
        });
    }

    // ------------------------------------------------------------------ login throttling (FR-02 hardening)

    private static String key(String identity) { return identity.toLowerCase(java.util.Locale.ROOT); }

    private void checkNotLocked(String kind, String identity) {
        final String k = key(identity);
        LocalDateTime until = Tx.read(c -> throttleDAO.lockedUntil(c, kind, k, LocalDateTime.now()));
        if (until != null)
            throw new ServiceException("Too many failed sign-in attempts. This account is locked until " + Fmt.dt(until)
                + ". Please try again later.");
    }

    /** Counts a failed attempt (committed on its own so it survives the exception) and logs lock events. */
    private void failed(String kind, String identity, Integer actorId) {
        final String k = key(identity);
        final String shown = identity.length() > 60 ? identity.substring(0, 60) : identity;
        Tx.tx(c -> {
            LocalDateTime now = LocalDateTime.now();
            int count = throttleDAO.recordFailure(c, kind, k, now, Constants.MAX_FAILED_LOGINS, now.plusMinutes(Constants.LOCK_MINUTES));
            String actor = KIND_ADMIN.equals(kind) ? "Admin" : "User";
            logDAO.insert(c, actor, actorId, Constants.ACT_LOGIN_FAILED, "Failed sign-in for '" + shown + "' (attempt " + count + ")");
            if (count >= Constants.MAX_FAILED_LOGINS)
                logDAO.insert(c, "System", actorId, Constants.ACT_LOGIN_LOCKED, "'" + shown + "' locked for " + Constants.LOCK_MINUTES
                    + " minutes after " + count + " failed sign-ins");
            return null;
        });
    }
}
