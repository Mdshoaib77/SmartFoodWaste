package com.foodwaste.service;

import com.foodwaste.dao.ActivityLogDAO;
import com.foodwaste.dao.AdminDAO;
import com.foodwaste.dao.AreaDAO;
import com.foodwaste.dao.FoodDAO;
import com.foodwaste.dao.ReportDAO;
import com.foodwaste.dao.RequestDAO;
import com.foodwaste.dao.UserDAO;
import com.foodwaste.model.ActivityLog;
import com.foodwaste.model.Admin;
import com.foodwaste.model.Food;
import com.foodwaste.model.Request;
import com.foodwaste.model.User;
import com.foodwaste.util.Constants;
import com.foodwaste.util.Csv;
import com.foodwaste.util.Fmt;
import com.foodwaste.util.PasswordUtil;
import com.foodwaste.util.TextTable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * FR-10 / FR-11 and administrator management: NGO verification, monitoring, suspicious requests, admin accounts,
 * reports (text + CSV). EVERY method re-checks the caller with Guard.admin, so these functions stay closed to other roles
 * even if a screen is bypassed.
 */
public class AdminService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final UserDAO userDAO = new UserDAO();
    private final AdminDAO adminDAO = new AdminDAO();
    private final AreaDAO areaDAO = new AreaDAO();
    private final FoodDAO foodDAO = new FoodDAO();
    private final RequestDAO requestDAO = new RequestDAO();
    private final ActivityLogDAO logDAO = new ActivityLogDAO();
    private final ReportDAO reportDAO = new ReportDAO();

    public List<User> pendingReceivers(Admin admin) {
        return Tx.read(c -> {
            Guard.admin(c, admin);
            return userDAO.findByRoleAndStatus(c, Constants.ROLE_RECEIVER, Constants.USER_PENDING);
        });
    }

    public List<User> allUsers(Admin admin) { return Tx.read(c -> { Guard.admin(c, admin); return userDAO.findAll(c); }); }

    public List<Food> allFood(Admin admin) { return Tx.read(c -> { Guard.admin(c, admin); return foodDAO.findAll(c); }); }

    public List<Request> allRequests(Admin admin) { return Tx.read(c -> { Guard.admin(c, admin); return requestDAO.findAll(c); }); }

    public List<Request> suspiciousRequests(Admin admin) {
        return Tx.read(c -> { Guard.admin(c, admin); return requestDAO.findSuspicious(c); });
    }

    public List<ActivityLog> recentLogs(Admin admin, int limit) {
        return Tx.read(c -> { Guard.admin(c, admin); return logDAO.findRecent(c, limit); });
    }

    public List<String> areas(Admin admin) { return Tx.read(c -> { Guard.admin(c, admin); return areaDAO.findAll(c); }); }

    /**
     * FR-10: donor-receiver interactions with filters. status = a request status or null for all; area = an official area
     * name (matches donor OR receiver area) or null; fromDate/toDate are inclusive request dates or null.
     */
    public List<Request> interactions(Admin admin, String status, String area, LocalDate fromDate, LocalDate toDate,
                                      boolean suspiciousOnly) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate))
            throw new ServiceException("The 'to' date cannot be before the 'from' date.");
        final String st = blank(status) ? null : status.trim();
        final String ar = blank(area) ? null : area.trim();
        return Tx.read(c -> {
            Guard.admin(c, admin);
            return requestDAO.search(c, st, ar, fromDate == null ? null : fromDate.atStartOfDay(),
                toDate == null ? null : toDate.plusDays(1).atStartOfDay(), suspiciousOnly);
        });
    }

    private static boolean blank(String s) { return s == null || s.trim().isEmpty() || "All".equalsIgnoreCase(s.trim()); }

    /** Approves or rejects a pending Receiver/NGO account and notifies the applicant. */
    public void verifyReceiver(Admin admin, int userId, boolean approve) {
        Tx.tx(c -> {
            Admin a = Guard.admin(c, admin);
            User u = userDAO.findById(c, userId);
            if (u == null || !Constants.ROLE_RECEIVER.equals(u.getRole()))
                throw new ServiceException("User #" + userId + " is not a Receiver/NGO account.");
            if (!Constants.USER_PENDING.equals(u.getStatus()))
                throw new ServiceException("User #" + userId + " is not pending (status: " + u.getStatus() + ").");
            userDAO.updateStatus(c, userId, approve ? Constants.USER_APPROVED : Constants.USER_REJECTED, a.getAdminId());
            logDAO.insert(c, "Admin", a.getAdminId(), approve ? "NGO_APPROVED" : "NGO_REJECTED",
                "User #" + userId + " (" + u.getName() + ")");
            Notifier.send(c, userId, approve ? "Your account was approved" : "Your account was rejected",
                approve ? "An administrator verified your account. You can now browse food and send pickup requests."
                        : "An administrator rejected your registration. Please contact the administrator for details.");
            return null;
        });
    }

    public void flagRequest(Admin admin, int requestId, boolean flag) {
        Tx.tx(c -> {
            Admin a = Guard.admin(c, admin);
            Request r = requestDAO.findById(c, requestId);
            if (r == null) throw new ServiceException("Request #" + requestId + " does not exist.");
            requestDAO.setSuspicious(c, requestId, flag);
            logDAO.insert(c, "Admin", a.getAdminId(), flag ? "REQUEST_FLAGGED" : "REQUEST_UNFLAGGED", "Request #" + requestId);
            if (flag) {
                Notifier.send(c, r.getUserId(), "Request #" + requestId + " flagged for review",
                    "An administrator flagged your request for '" + r.getFoodName() + "' for review. Please contact the administrator if this is a mistake.");
                Notifier.send(c, r.getDonorId(), "Request #" + requestId + " flagged for review",
                    "An administrator flagged the request from " + r.getReceiverName() + " for '" + r.getFoodName() + "' for review.");
            }
            return null;
        });
    }

    /** Cancels an open (Requested/Reserved) request, releases the food back to Available and notifies both sides. */
    public void cancelRequest(Admin admin, int requestId) {
        Tx.tx(c -> {
            Admin a = Guard.admin(c, admin);
            Request r = requestDAO.findById(c, requestId);
            if (r == null) throw new ServiceException("Request #" + requestId + " does not exist.");
            RequestService.cancelOpen(c, r, "admin");
            logDAO.insert(c, "Admin", a.getAdminId(), "REQUEST_CANCELLED", "Request #" + requestId + " cancelled by administrator");
            String text = "An administrator cancelled request #" + requestId + " for '" + r.getFoodName() + "'. The food is Available again.";
            Notifier.send(c, r.getUserId(), "Request #" + requestId + " cancelled by administrator", text);
            Notifier.send(c, r.getDonorId(), "Request #" + requestId + " cancelled by administrator", text);
            return null;
        });
    }

    // ------------------------------------------------------------------ administrator accounts

    public List<Admin> allAdmins(Admin admin) { return Tx.read(c -> { Guard.admin(c, admin); return adminDAO.findAll(c); }); }

    /** Creates another Administrator account (3-50 letters, digits, dot, dash or underscore; password min 6 chars). */
    public Admin createAdmin(Admin admin, String username, String password) {
        final String un = Check.text(username, "Username", 50);
        if (un.length() < 3 || !un.matches("[A-Za-z0-9._-]+"))
            throw new ServiceException("Username must be 3-50 characters: letters, digits, dot, dash or underscore only.");
        Check.password(password);
        final String hash = PasswordUtil.hash(password);
        return Tx.tx(c -> {
            Admin a = Guard.admin(c, admin);
            if (adminDAO.findByUsername(c, un) != null) throw new ServiceException("An administrator named '" + un + "' already exists.");
            Admin n = new Admin();
            n.setUsername(un);
            n.setRole("Administrator");
            n.setAdminId(adminDAO.insert(c, un, hash, "Administrator", false, a.getAdminId()));
            logDAO.insert(c, "Admin", a.getAdminId(), "ADMIN_CREATED", "New administrator '" + un + "' (#" + n.getAdminId() + ")");
            return n;
        });
    }

    // ------------------------------------------------------------------ reports (FR-11)

    private static String rate(long rescued, long expired) {
        return (rescued + expired) == 0 ? "N/A" : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * rescued / (rescued + expired));
    }

    /** FR-11: distribution and waste-reduction report as plain text. */
    public String generateReport(Admin admin) {
        return Tx.read(c -> {
            Guard.admin(c, admin);
            LocalDateTime now = LocalDateTime.now();
            StringBuilder sb = new StringBuilder();
            sb.append("SMART FOOD WASTE SHARING SYSTEM - DISTRIBUTION & WASTE-REDUCTION REPORT\n");
            sb.append("Generated: ").append(Fmt.dt(now)).append("\n\n");
            section(sb, "1. Registered users", TextTable.render(reportDAO.usersByRoleStatus(c)));
            section(sb, "2. Food records by status (items / quantity)", TextTable.render(reportDAO.foodByStatus(c)));
            section(sb, "3. Requests by status", TextTable.render(reportDAO.requestsByStatus(c)));
            long rescued = reportDAO.rescuedQuantity(c);
            long expired = reportDAO.expiredQuantity(c);
            section(sb, "4. Waste reduction",
                "  Quantity rescued (picked up / completed): " + rescued + "\n"
                + "  Quantity wasted  (expired unclaimed)    : " + expired + "\n"
                + "  Waste-reduction rate (rescued / (rescued + expired)): "
                + ((rescued + expired) == 0 ? "N/A (no finished food records yet)" : rate(rescued, expired)) + "\n");
            section(sb, "5. Outcome by priority level (items and quantity, rescued vs expired)", TextTable.render(reportDAO.byPriority(c)));
            section(sb, "6. Outcome by pickup area (quantity rescued vs expired)", TextTable.render(reportDAO.distributionByArea(c)));
            section(sb, "7. Top donors", TextTable.render(reportDAO.topDonors(c, 5)));
            section(sb, "8. Top receivers / NGOs", TextTable.render(reportDAO.topReceivers(c, 5)));
            return sb.toString();
        });
    }

    /** CSV 1: the distribution report (users, food, requests, area, priority, top donors and receivers). */
    public String distributionCsv(Admin admin) {
        return Tx.read(c -> {
            Guard.admin(c, admin);
            StringBuilder sb = new StringBuilder();
            Csv.row(sb, "Distribution report", "Generated " + Fmt.dt(LocalDateTime.now()));
            sb.append("\r\n");
            Csv.section(sb, "Users by role and status", reportDAO.usersByRoleStatus(c));
            Csv.section(sb, "Food by status", reportDAO.foodByStatus(c));
            Csv.section(sb, "Requests by status", reportDAO.requestsByStatus(c));
            Csv.section(sb, "Distribution by pickup area (quantity rescued vs expired)", reportDAO.distributionByArea(c));
            Csv.section(sb, "Distribution by priority", reportDAO.byPriority(c));
            Csv.section(sb, "Top donors", reportDAO.topDonors(c, 10));
            Csv.section(sb, "Top receivers / NGOs", reportDAO.topReceivers(c, 10));
            return sb.toString();
        });
    }

    /** CSV 2: the waste-reduction report (rescued vs expired, rate, by priority and by area). */
    public String wasteReductionCsv(Admin admin) {
        return Tx.read(c -> {
            Guard.admin(c, admin);
            long rq = reportDAO.rescuedQuantity(c), eq = reportDAO.expiredQuantity(c);
            long ri = reportDAO.rescuedItems(c), ei = reportDAO.expiredItems(c);
            List<String[]> summary = new ArrayList<>();
            summary.add(new String[] {"Metric", "Value"});
            summary.add(new String[] {"Items rescued (picked up / completed)", String.valueOf(ri)});
            summary.add(new String[] {"Items expired", String.valueOf(ei)});
            summary.add(new String[] {"Quantity rescued", String.valueOf(rq)});
            summary.add(new String[] {"Quantity expired", String.valueOf(eq)});
            summary.add(new String[] {"Waste-reduction rate (quantity)", rate(rq, eq)});
            StringBuilder sb = new StringBuilder();
            Csv.row(sb, "Waste-reduction report", "Generated " + Fmt.dt(LocalDateTime.now()));
            sb.append("\r\n");
            Csv.section(sb, "Summary", summary);
            Csv.section(sb, "By priority", reportDAO.byPriority(c));
            Csv.section(sb, "By area", reportDAO.distributionByArea(c));
            return sb.toString();
        });
    }

    /** Saves the text report under ./reports and returns the file path. */
    public Path saveReport(Admin admin, String text) {
        return write(admin, "report_", ".txt", text);
    }

    /** Writes both CSV reports under ./reports (UTF-8 with BOM so Excel shows Bangla/Unicode names correctly). */
    public List<Path> exportCsv(Admin admin) {
        List<Path> out = new ArrayList<>();
        out.add(write(admin, "distribution_report_", ".csv", "\uFEFF" + distributionCsv(admin)));
        out.add(write(admin, "waste_reduction_report_", ".csv", "\uFEFF" + wasteReductionCsv(admin)));
        return out;
    }

    private Path write(Admin admin, String prefix, String ext, String text) {
        Tx.read(c -> Guard.admin(c, admin));
        try {
            Path dir = Paths.get("reports");
            Files.createDirectories(dir);
            Path file = dir.resolve(prefix + LocalDateTime.now().format(STAMP) + ext);
            Files.write(file, text.getBytes(StandardCharsets.UTF_8));
            return file;
        } catch (IOException e) {
            throw new ServiceException("Could not save the report: " + e.getMessage(), e);
        }
    }

    private static void section(StringBuilder sb, String title, String body) {
        sb.append(title).append('\n').append(body).append('\n');
    }
}
