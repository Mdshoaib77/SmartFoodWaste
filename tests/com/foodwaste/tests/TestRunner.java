package com.foodwaste.tests;

import com.foodwaste.config.DBConnection;
import com.foodwaste.model.Admin;
import com.foodwaste.model.Food;
import com.foodwaste.model.Message;
import com.foodwaste.model.Request;
import com.foodwaste.model.User;
import com.foodwaste.service.AdminService;
import com.foodwaste.service.AuthService;
import com.foodwaste.service.ExpiryService;
import com.foodwaste.service.FoodService;
import com.foodwaste.service.MessageService;
import com.foodwaste.service.PriorityCalculator;
import com.foodwaste.service.RequestService;
import com.foodwaste.util.Constants;
import com.foodwaste.util.Csv;
import com.foodwaste.util.PasswordUtil;
import com.foodwaste.util.TimeSlot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain-Java test runner (JDK 8, no libraries). Covers the 8 proposal test areas (Section 5.1) plus capacity, messages,
 * cancellation, stale requests, edit/delete, monitoring filters, CSV reports and concurrency.
 * Run with run-tests.bat / run-tests.sh. Uses the separate <db>_test schema and writes TEST_REPORT.md.
 */
public final class TestRunner {
    private static final AuthService AUTH = Env.AUTH;
    private static final AdminService ADMINS = Env.ADMINS;
    private static final FoodService FOODS = Env.FOODS;
    private static final RequestService REQ = new RequestService();
    private static final MessageService MSG = new MessageService();

    private TestRunner() { }

    public static void main(String[] args) throws Exception {
        String reportPath = args.length > 0 ? args[0] : "TEST_REPORT.md";
        System.out.println("Smart Food Waste - test run");
        try {
            Env.setup();
        } catch (Exception e) {
            System.out.println("Cannot prepare the test database: " + e);
            System.out.println("Check db.properties and that MySQL is running. Nothing was tested.");
            System.exit(2);
        }
        System.out.println("Test database: " + Env.dbName + " on " + Env.productVersion);
        pureLogic();
        registration();
        authentication();
        priority();
        requestProcessing();
        pickupVerification();
        expiryHandling();
        staleRequests();
        accessControl();
        capacityMatching();
        pickupArea();
        messages();
        donorEditDelete();
        adminMonitoring();
        reporting();
        concurrency();
        security();

        String env = "- Date: " + LocalDateTime.now().withNano(0) + "\n- Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")\n"
            + "- Database: " + Env.productVersion + ", schema `" + Env.dbName + "` rebuilt from database.sql before the run\n"
            + "- Compiled with `javac --release 8`\n";
        T.writeReport(reportPath, env);
        int failed = T.failures();
        System.out.println("\nTOTAL " + T.RESULTS.size() + "  PASSED " + (T.RESULTS.size() - failed) + "  FAILED " + failed + "   (report: " + reportPath + ")");
        DBConnection.configure(DBConnection.url(), DBConnection.user(), DBConnection.password());
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ pure logic (no database)

    private static void pureLogic() {
        T.area("Pure logic (no database)");
        LocalDateTime now = LocalDateTime.of(2026, 11, 10, 12, 0, 0);
        T.eq("Priority 3h59m left", "High", PriorityCalculator.calculate(now.plusHours(3).plusMinutes(59), now));
        T.eq("Priority 3h59m59s left", "High", PriorityCalculator.calculate(now.plusHours(3).plusMinutes(59).plusSeconds(59), now));
        T.eq("Priority exactly 4h00m left", "Medium", PriorityCalculator.calculate(now.plusHours(4), now));
        T.eq("Priority 11h59m left", "Medium", PriorityCalculator.calculate(now.plusHours(11).plusMinutes(59), now));
        T.eq("Priority exactly 12h00m left", "Low", PriorityCalculator.calculate(now.plusHours(12), now));
        T.eq("Priority 3 days left", "Low", PriorityCalculator.calculate(now.plusDays(3), now));
        T.eq("Priority already past expiry", "High", PriorityCalculator.calculate(now.minusMinutes(5), now));
        String h = PasswordUtil.hash("secret1");
        T.yes("Password hash is not the plain text and verifies", "hash != plain, verify(correct)=true, verify(wrong)=false",
            !h.contains("secret1") && PasswordUtil.verify("secret1", h) && !PasswordUtil.verify("secret2", h));
        T.yes("Two hashes of one password differ (random salt)", "different hash strings", !h.equals(PasswordUtil.hash("secret1")));
        T.eq("TimeSlot normalises 16:00 - 18:00", "16:00-18:00", TimeSlot.normalise(" 16:00 - 18:00 "));
        T.yes("TimeSlot rejects bad formats", "5pm, 18:00-16:00, 25:00-26:00, empty, 16:00-16:00 all invalid",
            !TimeSlot.isValid("5pm") && !TimeSlot.isValid("18:00-16:00") && !TimeSlot.isValid("25:00-26:00") && !TimeSlot.isValid("")
            && !TimeSlot.isValid("16:00-16:00") && !TimeSlot.isValid(null));
        T.eq("CSV quotes comma and quote", "\"a,\"\"b\"\"\"", Csv.field("a,\"b\""));
        T.eq("CSV neutralises formula injection", "'=SUM(A1)", Csv.field("=SUM(A1)"));
    }

    // ------------------------------------------------------------------ 1. Registration

    private static void registration() {
        T.area("Registration");
        Env.reset();
        User d = AUTH.register("Donor One", "d1@test.local", "secret1", Constants.ROLE_DONOR, "Bogura", null);
        T.eq("Donor registers: role Donor", "Donor", d.getRole());
        T.eq("Donor registers: Approved at once", "Approved", d.getStatus());
        User r = AUTH.register("NGO One", "n1@test.local", "secret1", Constants.ROLE_RECEIVER, "Bogura", 50);
        T.eq("Receiver/NGO registers: role", "Receiver/NGO", r.getRole());
        T.eq("NGO remains Pending until approved", "Pending", r.getStatus());
        T.eq("Receiver capacity stored", "50", Env.str("SELECT Capacity FROM `user` WHERE UserID = ?", r.getUserId()));
        T.yes("Donor has no capacity stored", "Capacity NULL for donor", Env.str("SELECT Capacity FROM `user` WHERE UserID = ?", d.getUserId()) == null);
        T.denied("NGO without capacity is rejected", "capacity", () -> AUTH.register("N", "n2@test.local", "secret1", Constants.ROLE_RECEIVER, "Dhaka", null));
        T.denied("NGO with capacity 0 is rejected", "greater than zero", () -> AUTH.register("N", "n3@test.local", "secret1", Constants.ROLE_RECEIVER, "Dhaka", 0));
        T.denied("NGO with negative capacity is rejected", "greater than zero", () -> AUTH.register("N", "n4@test.local", "secret1", Constants.ROLE_RECEIVER, "Dhaka", -5));
        T.denied("Area outside the standard list is rejected", "area", () -> AUTH.register("D", "d9@test.local", "secret1", Constants.ROLE_DONOR, "Atlantis", null));
        User d2 = AUTH.register("Donor Two", "d2@test.local", "secret1", Constants.ROLE_DONOR, "  BOGURA  ", null);
        T.eq("Area matched case-insensitively + trimmed to the official name", "Bogura", d2.getAreaId());
        T.denied("Duplicate contact is rejected", "already exists", () -> AUTH.register("X", "d1@test.local", "secret1", Constants.ROLE_DONOR, "Dhaka", null));
        T.denied("Password shorter than 6 characters is rejected", "at least 6", () -> AUTH.register("X", "x1@test.local", "abc", Constants.ROLE_DONOR, "Dhaka", null));
        T.denied("Empty name is rejected", "name is required", () -> AUTH.register("  ", "x2@test.local", "secret1", Constants.ROLE_DONOR, "Dhaka", null));
        T.denied("Invalid role is rejected", "role", () -> AUTH.register("X", "x3@test.local", "secret1", "Administrator", "Dhaka", null));
        T.yes("Registration is written to the activity log", "REGISTER entries exist", Env.logCount("REGISTER") >= 3);
        T.yes("Area list is provided from the areas table", "contains Bogura, Rajshahi, Dhaka", AUTH.areas().containsAll(java.util.Arrays.asList("Bogura", "Rajshahi", "Dhaka")));

        // administrator registration / role management
        Admin a = Env.admin();
        T.yes("Default administrator exists (admin) and must change password", "admin login ok, MustChangePassword=true", a.isMustChangePassword());
        Admin a2 = ADMINS.createAdmin(a, "second.admin", "adminpw1");
        T.yes("Admin creates another administrator", "new admin id > 0", a2.getAdminId() > 0);
        T.eq("New administrator can sign in", "second.admin", AUTH.loginAdmin("second.admin", "adminpw1").getUsername());
        T.denied("Duplicate administrator username rejected", "already exists", () -> ADMINS.createAdmin(a, "second.admin", "adminpw1"));
        T.denied("Administrator password shorter than 6 rejected", "at least 6", () -> ADMINS.createAdmin(a, "third", "123"));
        T.denied("Administrator username with spaces/symbols rejected", "username", () -> ADMINS.createAdmin(a, "bad name!", "adminpw1"));
        T.denied("A Donor cannot create an administrator", "access denied", () -> ADMINS.createAdmin(fakeAdminFrom(d), "hacker", "hackerpw"));
        T.eq("Seeding twice does not create a second default admin", "false", String.valueOf(AUTH.ensureDefaultAdmin()));
    }

    /** An Admin object built from a user's id: must be refused because no such administrator exists. */
    private static Admin fakeAdminFrom(User u) {
        Admin a = new Admin();
        a.setAdminId(u.getUserId() + 100000);
        a.setUsername("admin");
        return a;
    }

    // ------------------------------------------------------------------ 2. Authentication

    private static void authentication() {
        T.area("Authentication");
        Env.reset();
        User d = Env.donor("Auth Donor", "Dhaka");
        User pending = AUTH.register("Pending NGO", "pend@test.local", "secret1", Constants.ROLE_RECEIVER, "Dhaka", 10);
        T.eq("Valid donor credentials allow login", d.getUserId(), AUTH.loginUser(d.getContact(), "secret1").getUserId());
        T.denied("Wrong password is rejected", "invalid contact or password", () -> AUTH.loginUser(d.getContact(), "wrong-pw"));
        T.denied("Unknown contact is rejected", "invalid contact or password", () -> AUTH.loginUser("nobody@test.local", "secret1"));
        T.denied("Pending NGO cannot log in", "pending", () -> AUTH.loginUser(pending.getContact(), "secret1"));
        T.eq("Valid administrator credentials allow login", "admin", AUTH.loginAdmin("admin", "admin123").getUsername());
        T.denied("Wrong administrator password is rejected", "invalid administrator", () -> AUTH.loginAdmin("admin", "nope-nope"));
        T.denied("A donor's credentials do not work on the admin login", "invalid administrator", () -> AUTH.loginAdmin(d.getContact(), "secret1"));
        String stored = Env.str("SELECT Password FROM `user` WHERE UserID = ?", d.getUserId());
        T.yes("Password is stored hashed, never plain", "stored value is not 'secret1'", stored != null && !stored.equals("secret1") && !stored.contains("secret1"));
        String adminStored = Env.str("SELECT Password FROM admin WHERE Username = 'admin'");
        T.yes("Admin password is stored hashed", "stored value is not 'admin123'", adminStored != null && !adminStored.contains("admin123"));

        // change password
        T.denied("Change password: wrong old password rejected", "current password is incorrect", () -> AUTH.changeUserPassword(d, "bad-old", "newpass1"));
        T.denied("Change password: new password < 6 rejected", "at least 6", () -> AUTH.changeUserPassword(d, "secret1", "abc"));
        T.denied("Change password: same password rejected", "different", () -> AUTH.changeUserPassword(d, "secret1", "secret1"));
        AUTH.changeUserPassword(d, "secret1", "newpass1");
        T.eq("Change password: new password works", d.getUserId(), AUTH.loginUser(d.getContact(), "newpass1").getUserId());
        T.denied("Change password: old password no longer works", "invalid", () -> AUTH.loginUser(d.getContact(), "secret1"));
        Admin a = Env.admin();
        T.denied("Admin change password: wrong old rejected", "incorrect", () -> AUTH.changeAdminPassword(a, "wrong", "newadmin1"));
        AUTH.changeAdminPassword(a, "admin123", "newadmin1");
        Admin after = AUTH.loginAdmin("admin", "newadmin1");
        T.yes("Admin change password: works and clears the 'default password' flag", "MustChangePassword=false", !after.isMustChangePassword());
        AUTH.changeAdminPassword(after, "newadmin1", "admin123");   // restore for later groups

        // login throttling
        User t = Env.donor("Throttle Donor", "Dhaka");
        for (int i = 0; i < Constants.MAX_FAILED_LOGINS; i++) {
            try { AUTH.loginUser(t.getContact(), "wrong" + i); } catch (RuntimeException expected) { /* counted */ }
        }
        T.denied("After 5 wrong passwords the account is temporarily locked", "locked", () -> AUTH.loginUser(t.getContact(), "secret1"));
        T.yes("Lock is written to the activity log", "LOGIN_LOCKED + LOGIN_FAILED entries", Env.logCount(Constants.ACT_LOGIN_LOCKED) >= 1 && Env.logCount(Constants.ACT_LOGIN_FAILED) >= 5);
        Env.exec("UPDATE login_throttle SET LockedUntil = ? WHERE Kind = 'User'", LocalDateTime.now().minusMinutes(1));
        T.eq("After the lock expires, login works again", t.getUserId(), AUTH.loginUser(t.getContact(), "secret1").getUserId());
        for (int i = 0; i < Constants.MAX_FAILED_LOGINS; i++) {
            try { AUTH.loginAdmin("admin", "bad" + i); } catch (RuntimeException expected) { /* counted */ }
        }
        T.denied("Administrator login is throttled too", "locked", () -> AUTH.loginAdmin("admin", "admin123"));
        Env.exec("DELETE FROM login_throttle");
        T.works("Administrator can sign in again once the lock is cleared", "login ok", () -> AUTH.loginAdmin("admin", "admin123"));
    }

    // ------------------------------------------------------------------ 3. Priority calculation

    private static void priority() {
        T.area("Priority Calculation");
        Env.reset();
        User d = Env.donor("Prio Donor", "Dhaka");
        Food f3 = Env.food(d, "Soup", 5, 3);
        Food f8 = Env.food(d, "Rice", 5, 8);
        Food f20 = Env.food(d, "Bread", 5, 20);
        T.eq("Food with 3 h left is stored as High", "High", f3.getPriorityLevel());
        T.eq("Food with 8 h left is stored as Medium", "Medium", f8.getPriorityLevel());
        T.eq("Food with 20 h left is stored as Low", "Low", f20.getPriorityLevel());
        Env.exec("UPDATE food SET ExpiryTime = ? WHERE FoodID = ?", LocalDateTime.now().plusHours(2), f20.getFoodId());
        ExpiryService.sync();
        T.eq("Background sync raises Low -> High when under 4 h remain", "High", Env.str("SELECT PriorityLevel FROM food WHERE FoodID = ?", f20.getFoodId()));
        Env.exec("UPDATE food SET ExpiryTime = ? WHERE FoodID = ?", LocalDateTime.now().plusHours(6), f3.getFoodId());
        ExpiryService.sync();
        T.eq("Background sync recalculates High -> Medium after edit of expiry", "Medium", Env.str("SELECT PriorityLevel FROM food WHERE FoodID = ?", f3.getFoodId()));
        T.denied("Expiry in the past is rejected at posting", "future", () -> FOODS.postFood(d, "Old", "Meal", 1, LocalDateTime.now().minusHours(5), LocalDateTime.now().minusHours(1), "", "x", ""));
        T.denied("Expiry before preparation is rejected", "after the preparation", () -> FOODS.postFood(d, "Odd", "Meal", 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().minusHours(2), "", "x", ""));
        T.denied("Quantity 0 is rejected", "greater than zero", () -> FOODS.postFood(d, "Zero", "Meal", 0, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(2), "", "x", ""));
        T.denied("Missing food name is rejected", "food name is required", () -> FOODS.postFood(d, " ", "Meal", 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(2), "", "x", ""));
    }

    // ------------------------------------------------------------------ 4. Request processing

    private static void requestProcessing() {
        T.area("Request Processing");
        Env.reset();
        User d = Env.donor("Req Donor", "Bogura");
        User d2 = Env.donor("Other Donor", "Bogura");
        User r = Env.receiver("Req NGO", "Bogura", 100);
        User r2 = Env.receiver("Second NGO", "Bogura", 100);
        Food f = Env.food(d, "Biryani", 40, 3);
        Request q = REQ.submit(r, f.getFoodId(), "16:00-17:00", "need urgently");
        T.eq("Submitted request is Requested", "Requested", Env.reqStatus(q.getRequestId()));
        T.eq("Food becomes Requested", "Requested", Env.foodStatus(f.getFoodId()));
        T.eq("Request stores the correct receiver", r.getUserId(), Env.scalar("SELECT UserID FROM request WHERE RequestID = ?", q.getRequestId()));
        T.eq("Request stores the preferred slot", "16:00-17:00", Env.str("SELECT TimeSlot FROM request WHERE RequestID = ?", q.getRequestId()));
        T.denied("Second receiver cannot request food that is no longer Available", "no longer available", () -> REQ.submit(r2, f.getFoodId(), "16:00-17:00", ""));
        T.denied("Donor account cannot submit a request", "access denied", () -> REQ.submit(d2, f.getFoodId(), "16:00-17:00", ""));
        Food f2 = Env.food(d, "Dal", 10, 5);
        T.denied("Invalid slot text '5pm' rejected", "invalid time slot", () -> REQ.submit(r2, f2.getFoodId(), "5pm", ""));
        T.denied("Slot with end before start rejected", "earlier than its end", () -> REQ.submit(r2, f2.getFoodId(), "18:00-16:00", ""));
        T.denied("Slot with impossible hour rejected", "invalid time slot", () -> REQ.submit(r2, f2.getFoodId(), "25:00-26:00", ""));
        T.denied("Empty slot rejected", "required", () -> REQ.submit(r2, f2.getFoodId(), "", ""));
        T.denied("Unknown food id rejected", "does not exist", () -> REQ.submit(r2, 99999, "16:00-17:00", ""));
        T.denied("Another donor cannot approve this request", "not found among your", () -> REQ.approve(d2, q.getRequestId()));
        T.denied("Counter slot with bad format rejected on approve", "invalid time slot", () -> REQ.approve(d, q.getRequestId(), "later"));
        String code = REQ.approve(d, q.getRequestId(), "18:00-19:00");
        T.yes("Approval returns a 6-digit verification code", "6 digits", code != null && code.matches("\\d{6}"));
        T.eq("Approved request is Reserved", "Reserved", Env.reqStatus(q.getRequestId()));
        T.eq("Food becomes Reserved", "Reserved", Env.foodStatus(f.getFoodId()));
        T.eq("Donor's counter-proposed slot is stored", "18:00-19:00", Env.str("SELECT ApprovedSlot FROM request WHERE RequestID = ?", q.getRequestId()));
        T.eq("Receiver sees the approved slot", "18:00-19:00", REQ.forReceiver(r).get(0).getApprovedSlot());
        T.eq("Pickup record exists with status Pending", "Pending", Env.str("SELECT Status FROM pickup WHERE RequestID = ?", q.getRequestId()));
        T.denied("Approving twice is refused", "not waiting for approval", () -> REQ.approve(d, q.getRequestId()));
        Request q2 = REQ.submit(r2, f2.getFoodId(), "10:00-11:00", "");
        REQ.reject(d, q2.getRequestId());
        T.eq("Rejected request is Rejected", "Rejected", Env.reqStatus(q2.getRequestId()));
        T.eq("Food returns to Available after rejection", "Available", Env.foodStatus(f2.getFoodId()));
        Request q3 = REQ.submit(r2, f2.getFoodId(), "10:00-11:00", "");
        REQ.approve(d, q3.getRequestId());
        T.eq("Approve without counter keeps the receiver's slot", "10:00-11:00", Env.str("SELECT ApprovedSlot FROM request WHERE RequestID = ?", q3.getRequestId()));

        // receiver cancels
        Food f3 = Env.food(d, "Khichuri", 10, 5);
        Request q4 = REQ.submit(r, f3.getFoodId(), "12:00-13:00", "");
        REQ.cancelByReceiver(r, q4.getRequestId());
        T.eq("Receiver cancels a Requested request", "Cancelled", Env.reqStatus(q4.getRequestId()));
        T.eq("Cancel: food is Available again", "Available", Env.foodStatus(f3.getFoodId()));
        T.yes("Cancel: donor is notified", "message 'cancelled' in donor inbox", Env.messagesWith(d, "cancelled") >= 1);
        Request q5 = REQ.submit(r, f3.getFoodId(), "12:00-13:00", "");
        REQ.approve(d, q5.getRequestId());
        REQ.cancelByReceiver(r, q5.getRequestId());
        T.eq("Receiver cancels a Reserved request", "Cancelled", Env.reqStatus(q5.getRequestId()));
        T.eq("Cancel Reserved: food Available again", "Available", Env.foodStatus(f3.getFoodId()));
        T.eq("Cancel Reserved: pickup becomes Cancelled", "Cancelled", Env.str("SELECT Status FROM pickup WHERE RequestID = ?", q5.getRequestId()));
        T.yes("Cancel is written to the activity log", "REQUEST_CANCELLED_BY_RECEIVER logged", Env.logCount("REQUEST_CANCELLED_BY_RECEIVER") >= 2);
        T.denied("Cancelling an already cancelled request is refused", "only requested or reserved", () -> REQ.cancelByReceiver(r, q5.getRequestId()));
        T.denied("Another receiver cannot cancel my request", "not found among your", () -> REQ.cancelByReceiver(r2, q.getRequestId()));
        T.works("Food freed by a cancel can be requested by someone else", "second receiver request succeeds", () -> REQ.submit(r2, f3.getFoodId(), "14:00-15:00", ""));
    }

    // ------------------------------------------------------------------ 5. Pickup verification

    private static void pickupVerification() {
        T.area("Pickup Verification");
        Env.reset();
        User d = Env.donor("Pick Donor", "Rajshahi");
        User r = Env.receiver("Pick NGO", "Rajshahi", 100);
        User r2 = Env.receiver("Other NGO", "Rajshahi", 100);
        Food f = Env.food(d, "Pulao", 20, 6);
        Request q = REQ.submit(r, f.getFoodId(), "15:00-16:00", "");
        T.denied("Cannot confirm pickup before approval", "not awaiting pickup", () -> REQ.confirmPickup(r, q.getRequestId(), "123456"));
        String code = REQ.approve(d, q.getRequestId());
        String wrong = "000000".equals(code) ? "111111" : "000000";
        T.denied("Invalid verification code does not confirm pickup", "invalid verification code", () -> REQ.confirmPickup(r, q.getRequestId(), wrong));
        T.eq("After a wrong code the request is still Reserved", "Reserved", Env.reqStatus(q.getRequestId()));
        T.eq("After a wrong code the food is still Reserved", "Reserved", Env.foodStatus(f.getFoodId()));
        T.yes("Wrong code attempt is logged", "PICKUP_CODE_INVALID logged", Env.logCount("PICKUP_CODE_INVALID") == 1);
        T.denied("Empty code does not confirm pickup", "invalid verification code", () -> REQ.confirmPickup(r, q.getRequestId(), "  "));
        T.denied("Another receiver cannot confirm my pickup", "not found among your", () -> REQ.confirmPickup(r2, q.getRequestId(), code));
        T.denied("Donor cannot complete before pickup", "not been picked up", () -> REQ.complete(d, q.getRequestId()));
        REQ.confirmPickup(r, q.getRequestId(), code);
        T.eq("Valid code confirms pickup: request Picked Up", "Picked Up", Env.reqStatus(q.getRequestId()));
        T.eq("Valid code: food Picked Up", "Picked Up", Env.foodStatus(f.getFoodId()));
        T.yes("Valid code: pickup timestamp recorded", "PickupTime not null", Env.str("SELECT PickupTime FROM pickup WHERE RequestID = ?", q.getRequestId()) != null);
        T.eq("Valid code: pickup status Picked Up", "Picked Up", Env.str("SELECT Status FROM pickup WHERE RequestID = ?", q.getRequestId()));
        T.yes("Donor is notified of the confirmed pickup", "message 'Pickup confirmed'", Env.messagesWith(d, "Pickup confirmed") == 1);
        T.denied("Confirming twice is refused", "not awaiting pickup", () -> REQ.confirmPickup(r, q.getRequestId(), code));
        REQ.complete(d, q.getRequestId());
        T.eq("Donor completes: request Completed", "Completed", Env.reqStatus(q.getRequestId()));
        T.eq("Donor completes: food Completed", "Completed", Env.foodStatus(f.getFoodId()));
        T.eq("Donor completes: pickup Completed", "Completed", Env.str("SELECT Status FROM pickup WHERE RequestID = ?", q.getRequestId()));
        T.denied("Completed request cannot be cancelled by the receiver", "only requested or reserved", () -> REQ.cancelByReceiver(r, q.getRequestId()));
        T.eq("Status sequence is in the log (approve, confirm, complete)", "3", Env.scalar("SELECT COUNT(*) FROM activity_log WHERE Action IN ('REQUEST_APPROVED','PICKUP_CONFIRMED','REQUEST_COMPLETED')"));
        T.eq("Donor can read the code of their own request", code, REQ.pickupFor(d, q.getRequestId()).getVerificationCode());
        T.denied("Receiver cannot read the pickup code via the donor function", "access denied", () -> REQ.pickupFor(r, q.getRequestId()));
    }

    // ------------------------------------------------------------------ 6. Expiry handling

    private static void expiryHandling() {
        T.area("Expiry Handling");
        Env.reset();
        User d = Env.donor("Exp Donor", "Khulna");
        User r = Env.receiver("Exp NGO", "Khulna", 100);
        Food avail = Env.food(d, "Cake", 7, 3);
        Food reserved = Env.food(d, "Curry", 9, 3);
        Food ok = Env.food(d, "Fruit", 4, 30);
        Request q = REQ.submit(r, reserved.getFoodId(), "15:00-16:00", "");
        REQ.approve(d, q.getRequestId());
        Env.exec("UPDATE food SET ExpiryTime = ? WHERE FoodID IN (?, ?)", LocalDateTime.now().minusMinutes(1), avail.getFoodId(), reserved.getFoodId());
        int n = ExpiryService.sync();
        T.eq("Uncollected Available item is marked Expired", "Expired", Env.foodStatus(avail.getFoodId()));
        T.eq("Reserved item that reached expiry is marked Expired", "Expired", Env.foodStatus(reserved.getFoodId()));
        T.eq("Sync reports the number of newly expired items", "2", n);
        T.eq("Its open request becomes Expired", "Expired", Env.reqStatus(q.getRequestId()));
        T.eq("Its pickup becomes Expired", "Expired", Env.str("SELECT Status FROM pickup WHERE RequestID = ?", q.getRequestId()));
        T.eq("Item that has not expired is untouched", "Available", Env.foodStatus(ok.getFoodId()));
        T.yes("Donor is notified about expired food", "2 'expired' notifications", Env.messagesWith(d, "expired") >= 2);
        T.yes("Receiver is notified that the request expired", "message 'Request ... expired'", Env.messagesWith(r, "expired") == 1);
        T.eq("Running the check again expires nothing and does not duplicate notifications", "0", ExpiryService.sync());
        T.eq("Expiry check is logged", "1", Env.logCount("EXPIRY_CHECK"));
        T.denied("Expired food cannot be requested", "expired", () -> REQ.submit(r, avail.getFoodId(), "10:00-11:00", ""));
        T.eq("Expired quantity is counted in reports", "16", Env.scalar("SELECT COALESCE(SUM(Quantity),0) FROM food WHERE Status = 'Expired'"));
        T.yes("Waste report shows expired quantity 16", "report contains 'expired unclaimed)    : 16'", ADMINS.generateReport(Env.admin()).contains("expired unclaimed)    : 16"));
        Food late = Env.food(d, "Soup", 3, 3);
        Request ql = REQ.submit(r, late.getFoodId(), "09:00-10:00", "");
        String code = REQ.approve(d, ql.getRequestId());
        Env.exec("UPDATE food SET ExpiryTime = ? WHERE FoodID = ?", LocalDateTime.now().minusSeconds(5), late.getFoodId());
        T.denied("Pickup is refused once the food has expired", "expired", () -> REQ.confirmPickup(r, ql.getRequestId(), code));
    }

    // ------------------------------------------------------------------ stale request auto-cancel

    private static void staleRequests() {
        T.area("Request Processing - stale requests");
        Env.reset();
        User d = Env.donor("Stale Donor", "Pabna");
        User r = Env.receiver("Stale NGO", "Pabna", 100);
        Food f = Env.food(d, "Noodles", 5, 10);
        Food g = Env.food(d, "Juice", 5, 10);
        Food h = Env.food(d, "Cutlet", 5, 10);
        Request old = REQ.submit(r, f.getFoodId(), "10:00-11:00", "");
        Request fresh = REQ.submit(r, g.getFoodId(), "10:00-11:00", "");
        Request reservedOld = REQ.submit(r, h.getFoodId(), "10:00-11:00", "");
        REQ.approve(d, reservedOld.getRequestId());
        LocalDateTime longAgo = LocalDateTime.now().minusHours(Constants.STALE_REQUEST_HOURS).minusMinutes(5);
        Env.exec("UPDATE request SET RequestedAt = ? WHERE RequestID IN (?, ?)", longAgo, old.getRequestId(), reservedOld.getRequestId());
        Env.exec("UPDATE request SET RequestedAt = ? WHERE RequestID = ?", LocalDateTime.now().minusMinutes(90), fresh.getRequestId());
        ExpiryService.sync();
        T.eq("Unanswered request older than the limit is auto-cancelled", "Cancelled", Env.reqStatus(old.getRequestId()));
        T.eq("Stale cancel: food becomes Available again", "Available", Env.foodStatus(f.getFoodId()));
        T.yes("Stale cancel: receiver and donor are notified", "'cancelled automatically' in both inboxes",
            Env.messagesWith(r, "cancelled automatically") == 1 && Env.messagesWith(d, "cancelled automatically") == 1);
        T.eq("Request younger than the limit is untouched", "Requested", Env.reqStatus(fresh.getRequestId()));
        T.eq("Old but already Reserved request is untouched", "Reserved", Env.reqStatus(reservedOld.getRequestId()));
        T.eq("Stale cancel is logged", "1", Env.logCount("REQUEST_AUTO_CANCELLED"));
        T.eq("Limit is the configurable constant", "2", Constants.STALE_REQUEST_HOURS);
    }

    // ------------------------------------------------------------------ 7. Access control

    private static void accessControl() {
        T.area("Access Control");
        Env.reset();
        User d = Env.donor("AC Donor", "Dhaka");
        User r = Env.receiver("AC NGO", "Dhaka", 100);
        Admin a = Env.admin();
        Food f = Env.food(d, "Meal", 5, 6);
        Request q = REQ.submit(r, f.getFoodId(), "10:00-11:00", "");
        T.denied("Donor cannot list all users (admin function)", "access denied", () -> ADMINS.allUsers(fakeAdminFrom(d)));
        T.denied("Receiver cannot read the activity log", "access denied", () -> ADMINS.recentLogs(fakeAdminFrom(r), 10));
        T.denied("Null administrator is refused", "access denied", () -> ADMINS.generateReport(null));
        T.denied("Receiver cannot flag requests", "access denied", () -> ADMINS.flagRequest(fakeAdminFrom(r), q.getRequestId(), true));
        T.denied("Receiver cannot approve NGO accounts", "access denied", () -> ADMINS.verifyReceiver(fakeAdminFrom(r), r.getUserId(), true));
        T.denied("Receiver cannot cancel requests as admin", "access denied", () -> ADMINS.cancelRequest(fakeAdminFrom(r), q.getRequestId()));
        T.denied("Receiver cannot send administrator messages", "access denied", () -> MSG.sendToUser(fakeAdminFrom(r), d.getUserId(), "Hi", "Hi"));
        T.denied("Receiver cannot broadcast", "access denied", () -> MSG.broadcast(fakeAdminFrom(r), Constants.AUDIENCE_EVERYONE, "Hi", "Hi"));
        T.denied("Receiver cannot export CSV", "access denied", () -> ADMINS.exportCsv(fakeAdminFrom(r)));
        Admin forged = new Admin();
        forged.setAdminId(a.getAdminId());
        forged.setUsername("someone-else");
        T.denied("Admin object with a mismatching username is refused", "access denied", () -> ADMINS.allUsers(forged));
        T.denied("Receiver cannot post food", "only for donor", () -> FOODS.postFood(r, "X", "Meal", 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(3), "", "x", ""));
        T.denied("Donor cannot browse as a receiver", "only for receiver", () -> FOODS.availableFor(d, false, false));
        T.denied("Donor cannot submit requests", "only for receiver", () -> REQ.submit(d, f.getFoodId(), "10:00-11:00", ""));
        T.denied("Receiver cannot approve requests", "only for donor", () -> REQ.approve(r, q.getRequestId()));
        T.denied("Receiver cannot reject requests", "only for donor", () -> REQ.reject(r, q.getRequestId()));
        T.denied("Receiver cannot see the donor's nearby-receivers list", "only for donor", () -> FOODS.nearbyReceivers(r));
        T.denied("Null user is refused", "sign in", () -> FOODS.myPosts(null));
        User ghost = new User();
        ghost.setUserId(424242);
        ghost.setRole(Constants.ROLE_DONOR);
        T.denied("Non-existent user id is refused", "only for donor", () -> FOODS.myPosts(ghost));
        User pend = AUTH.register("Pending", "pend2@test.local", "secret1", Constants.ROLE_RECEIVER, "Dhaka", 10);
        T.denied("Pending NGO cannot use receiver functions", "not approved", () -> FOODS.availableFor(pend, false, false));
        User rolePlay = new User();                       // a receiver pretending to be a donor
        rolePlay.setUserId(r.getUserId());
        rolePlay.setRole(Constants.ROLE_DONOR);
        T.denied("Receiver cannot pose as Donor by editing the session object", "only for donor", () -> FOODS.myPosts(rolePlay));
        ADMINS.verifyReceiver(a, pend.getUserId(), false);
        Env.exec("UPDATE `user` SET Status = 'Rejected' WHERE UserID = ?", d.getUserId());
        T.denied("A donor rejected after login loses access immediately", "not approved", () -> FOODS.myPosts(d));
        Env.exec("UPDATE `user` SET Status = 'Approved' WHERE UserID = ?", d.getUserId());
        User d2 = Env.donor("Other Donor", "Dhaka");
        T.denied("Donor cannot edit another donor's food", "not found among your", () -> FOODS.updateFood(d2, f.getFoodId(), "H", "Meal", 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(3), "", "x", ""));
        T.denied("Donor cannot delete another donor's food", "not found among your", () -> FOODS.deleteFood(d2, f.getFoodId()));
        T.denied("Donor cannot read another donor's pickup code", "not found among your", () -> REQ.pickupFor(d2, q.getRequestId()));
        T.denied("Inbox requires a signed-in user", "sign in", () -> MSG.inbox(null));
    }

    // ------------------------------------------------------------------ FR-05 capacity matching

    private static void capacityMatching() {
        T.area("Receiver Matching (area + capacity)");
        Env.reset();
        User d = Env.donor("Match Donor", "Bogura");
        User small = Env.receiver("Small NGO", "Bogura", 20);
        User big = Env.receiver("Big NGO", "bogura", 200);
        User far = Env.receiver("Far NGO", "Dhaka", 500);
        AUTH.register("Pending NGO", "pm@test.local", "secret1", Constants.ROLE_RECEIVER, "Bogura", 300);
        Food highHere = Env.food(d, "UrgentHere", 15, 2);
        Food medHere = Env.food(d, "MediumHere", 15, 8);
        Food bigLow = Env.food(d, "BigLow", 150, 40);
        User donorFar = Env.donor("Far Donor", "Dhaka");
        Food highFar = Env.food(donorFar, "UrgentFar", 10, 1);
        Food lowFar = Env.food(donorFar, "LowFar", 10, 30);

        List<Food> list = FOODS.availableFor(small, false, false);
        T.eq("Receiver sees all 5 available foods (own area listed first within each priority)", 5, list.size());
        T.eq("Urgent food from the receiver's own area comes first", "UrgentHere", list.get(0).getFoodName());
        T.eq("Urgent food from another area comes after it (High before Medium)", "UrgentFar", list.get(1).getFoodName());
        T.eq("Then Medium of own area", "MediumHere", list.get(2).getFoodName());
        T.eq("Within Low, own-area food precedes other-area food", "BigLow", list.get(3).getFoodName());
        T.eq("Other-area Low food comes last", "LowFar", list.get(4).getFoodName());
        T.yes("Food larger than capacity is marked", "BigLow exceeds capacity 20", FoodService.exceedsCapacity(bigLow, small) && !FoodService.exceedsCapacity(highHere, small));
        T.eq("'My area only' hides other areas", 3, FOODS.availableFor(small, true, false).size());
        T.eq("'Fits capacity only' hides food above capacity", 4, FOODS.availableFor(small, false, true).size());
        T.eq("Both filters together", 2, FOODS.availableFor(small, true, true).size());
        T.eq("Area comparison is case-insensitive ('bogura' receiver sees Bogura food first)", "UrgentHere", FOODS.availableFor(big, false, false).get(0).getFoodName());
        T.denied("Request above capacity is BLOCKED", "capacity is 20", () -> REQ.submit(small, bigLow.getFoodId(), "10:00-11:00", ""));
        T.eq("Blocked request leaves the food Available", "Available", Env.foodStatus(bigLow.getFoodId()));
        T.works("Request within capacity is accepted", "request ok", () -> REQ.submit(small, highHere.getFoodId(), "10:00-11:00", ""));
        T.works("Larger receiver may request the big food", "request ok", () -> REQ.submit(big, bigLow.getFoodId(), "10:00-11:00", ""));

        List<User> all = FOODS.nearbyReceivers(d);
        T.eq("Donor sees approved receivers of the same area only (pending/other area excluded)", 2, all.size());
        T.eq("Nearby list is sorted by capacity (largest first)", "Big NGO", all.get(0).getName());
        T.yes("Nearby list exposes area and capacity", "area Bogura, capacity 200", "Bogura".equals(all.get(0).getAreaId()) && all.get(0).getCapacity() == 200);
        List<User> forBig = FOODS.nearbyReceivers(d, bigLow.getFoodId());
        T.eq("For a 150-unit food only receivers with capacity >= 150 are listed", 1, forBig.size());
        T.eq("... and that receiver is the big NGO", "Big NGO", forBig.get(0).getName());
        T.eq("For a 15-unit food both local receivers qualify", 2, FOODS.nearbyReceivers(d, medHere.getFoodId()).size());
        T.denied("Matching for someone else's food is refused", "not found among your", () -> FOODS.nearbyReceivers(d, highFar.getFoodId()));
        T.yes("The far receiver never appears for the local donor", "Far NGO absent", all.stream().noneMatch(u -> u.getUserId() == far.getUserId()));
    }

    // ------------------------------------------------------------------ pickup area (food-level area matching)

    private static void pickupArea() {
        T.area("Pickup Area (all-Bangladesh matching by the food's pickup district)");
        Env.reset();
        LocalDateTime now = LocalDateTime.now();
        T.eq("Standard area list holds all 64 districts of Bangladesh", 64L, Env.scalar("SELECT COUNT(*) FROM areas"));
        T.yes("List contains far-apart districts (Thakurgaon, Cox's Bazar, Sylhet, Khulna) and no sub-area names",
            "present / absent", AUTH.areas().containsAll(java.util.Arrays.asList("Thakurgaon", "Cox's Bazar", "Sylhet", "Khulna"))
            && !AUTH.areas().contains("Bogura Sadar"));
        User d = Env.donor("Area Donor", "Bogura");
        User rBog = Env.receiver("Bogura NGO", "Bogura", 100);
        User rDha = Env.receiver("Dhaka NGO", "Dhaka", 100);
        Food home = Env.food(d, "HomeFood", 10, 8);
        T.eq("Blank pickup area defaults to the donor's registered area", "Bogura", home.getAreaId());
        Food dhaka = FOODS.postFood(d, "DhakaFood", "Meal", 10, now.minusHours(1), now.plusHours(8), "  dHaka ", "House 12, Road 3, Mirpur", "");
        T.eq("A Bogura donor can post food with another pickup area (stored with the official spelling)", "Dhaka", dhaka.getAreaId());
        T.eq("Address detail stays free text", "House 12, Road 3, Mirpur", dhaka.getLocation());
        T.eq("Pickup area is stored in the database", "Dhaka", Env.str("SELECT PickupArea FROM food WHERE FoodID = ?", dhaka.getFoodId()));
        T.denied("Unknown pickup area is rejected", "pickup area", () -> FOODS.postFood(d, "X", "Meal", 1, now.minusHours(1), now.plusHours(5), "Atlantis", "x", ""));
        T.denied("Old sub-area name is rejected", "pickup area", () -> FOODS.postFood(d, "X", "Meal", 1, now.minusHours(1), now.plusHours(5), "Bogura Sadar", "x", ""));

        List<Food> dhakaView = FOODS.availableFor(rDha, true, false);
        T.eq("Dhaka receiver, 'my area only': exactly the Dhaka-pickup food", "DhakaFood", dhakaView.size() == 1 ? dhakaView.get(0).getFoodName() : "size " + dhakaView.size());
        List<Food> bogView = FOODS.availableFor(rBog, true, false);
        T.eq("Bogura receiver, 'my area only': exactly the Bogura-pickup food (Dhaka food hidden)", "HomeFood", bogView.size() == 1 ? bogView.get(0).getFoodName() : "size " + bogView.size());
        T.eq("Without the filter a receiver sees food of every area", 2, FOODS.availableFor(rBog, false, false).size());
        T.eq("Own pickup area is listed first inside the same priority", "DhakaFood", FOODS.availableFor(rDha, false, false).get(0).getFoodName());

        List<User> forDhaka = FOODS.nearbyReceivers(d, dhaka.getFoodId());
        T.eq("Receivers suggested for a Dhaka-pickup food are the Dhaka receivers", "Dhaka NGO", forDhaka.size() == 1 ? forDhaka.get(0).getName() : "size " + forDhaka.size());
        List<User> forHome = FOODS.nearbyReceivers(d, home.getFoodId());
        T.eq("Receivers suggested for a Bogura-pickup food are the Bogura receivers", "Bogura NGO", forHome.size() == 1 ? forHome.get(0).getName() : "size " + forHome.size());
        T.eq("Without a food the donor's registered area is used", "Bogura NGO", FOODS.nearbyReceivers(d).get(0).getName());

        Food moved = FOODS.updateFood(d, dhaka.getFoodId(), "DhakaFood", "Meal", 10, now.minusHours(2), now.plusHours(8), "Rajshahi", "Station Road", "");
        T.eq("Editing a post can change its pickup area", "Rajshahi", moved.getAreaId());
        T.eq("After the move the Dhaka receiver no longer sees it under 'my area'", 0, FOODS.availableFor(rDha, true, false).size());
        T.denied("Edit with an unknown pickup area is rejected", "pickup area", () -> FOODS.updateFood(d, dhaka.getFoodId(), "DhakaFood", "Meal", 10, now.minusHours(2), now.plusHours(8), "Nowhere", "x", ""));

        Food dh2 = FOODS.postFood(d, "DhakaFood2", "Meal", 10, now.minusHours(1), now.plusHours(8), "Dhaka", "Mirpur 10", "");
        REQ.submit(rDha, dh2.getFoodId(), "10:00-11:00", "");
        com.foodwaste.model.Admin a = Env.admin();
        T.eq("Admin area filter finds the request by its PICKUP area", 1, ADMINS.interactions(a, null, "Dhaka", null, null, false).size());
        T.eq("A Dhaka-pickup request is not listed under the donor's home area (Bogura)", 0, ADMINS.interactions(a, null, "Bogura", null, null, false).size());

        Food done = FOODS.postFood(d, "DhakaDone", "Meal", 10, now.minusHours(1), now.plusHours(8), "Dhaka", "Mirpur 2", "");
        Env.exec("UPDATE food SET Status = 'Completed' WHERE FoodID = ?", done.getFoodId());
        List<String[]> rows = com.foodwaste.service.Tx.read(c -> new com.foodwaste.dao.ReportDAO().distributionByArea(c));
        boolean dhakaRow = false, boguraRow = false;
        for (String[] r : rows) {
            if ("Dhaka".equals(r[0]) && new java.math.BigDecimal(r[2].trim()).intValue() == 10) dhakaRow = true;
            if ("Bogura".equals(r[0])) boguraRow = true;
        }
        T.yes("Report counts rescued food under its pickup area (Dhaka), not the donor's home area", "Dhaka row with 10, no Bogura row", dhakaRow && !boguraRow);
    }

    // ------------------------------------------------------------------ messages / notifications

    private static void messages() {
        T.area("Administrative messages and notifications");
        Env.reset();
        Admin a = Env.admin();
        User d = Env.donor("Msg Donor", "Dhaka");
        User d2 = Env.donor("Msg Donor 2", "Rajshahi");
        User r = AUTH.register("Msg NGO", "msgngo@test.local", "secret1", Constants.ROLE_RECEIVER, "Dhaka", 100);
        ADMINS.verifyReceiver(a, r.getUserId(), true);
        T.yes("Account approval sends a notification to the NGO", "'approved' message", Env.messagesWith(r, "approved") == 1);
        User r2 = AUTH.register("Rej NGO", "rejngo@test.local", "secret1", Constants.ROLE_RECEIVER, "Dhaka", 100);
        ADMINS.verifyReceiver(a, r2.getUserId(), false);
        T.yes("Account rejection sends a notification", "'rejected' message", Env.messagesWith(r2, "rejected") == 1);
        MSG.sendToUser(a, d.getUserId(), "Welcome", "Thank you for donating.");
        T.eq("Donor inbox: unread count 1", 1, MSG.unreadCount(d));
        List<Message> inbox = MSG.inbox(d);
        T.eq("Inbox shows subject", "Welcome", inbox.get(0).getSubject());
        T.eq("Inbox shows sender as the admin", "Admin admin", inbox.get(0).getSenderName());
        MSG.markRead(d, inbox.get(0).getMessageId());
        T.eq("After reading, unread count is 0", 0, MSG.unreadCount(d));
        MSG.sendToUser(a, d.getUserId(), "Second", "Another");
        MSG.markRead(d2, MSG.inbox(d).get(0).getMessageId());
        T.eq("Another user cannot mark my message as read", 1, MSG.unreadCount(d));
        T.eq("Mark all read", 1, MSG.markAllRead(d));
        int n = MSG.broadcast(a, Constants.AUDIENCE_DONORS, "Notice", "System maintenance tonight");
        T.eq("Broadcast to all donors reaches both donors", 2, n);
        T.yes("Broadcast reached each donor once", "1 'Notice' each", Env.messagesWith(d, "Notice") == 1 && Env.messagesWith(d2, "Notice") == 1);
        T.eq("Receiver was not in the donor broadcast", 0, Env.messagesWith(r, "Notice"));
        T.eq("Broadcast to receivers/NGOs reaches the one approved NGO", 1, MSG.broadcast(a, Constants.AUDIENCE_RECEIVERS, "NGO notice", "Meeting"));
        T.eq("Broadcast to everyone reaches 3 approved users", 3, MSG.broadcast(a, Constants.AUDIENCE_EVERYONE, "All", "Hello all"));
        T.denied("Empty subject rejected", "subject is required", () -> MSG.sendToUser(a, d.getUserId(), " ", "x"));
        T.denied("Empty message rejected", "message is required", () -> MSG.sendToUser(a, d.getUserId(), "s", " "));
        T.denied("Unknown audience rejected", "unknown audience", () -> MSG.broadcast(a, "Martians", "s", "m"));
        T.denied("Unknown recipient rejected", "does not exist", () -> MSG.sendToUser(a, 99999, "s", "m"));

        // automatic notifications on request events
        Food f = Env.food(d, "Notif food", 5, 6);
        Request q = REQ.submit(r, f.getFoodId(), "10:00-11:00", "");
        T.yes("New request notifies the donor", "'New pickup request'", Env.messagesWith(d, "New pickup request") == 1);
        REQ.approve(d, q.getRequestId());
        T.yes("Approval notifies the receiver", "'approved'", Env.messagesWith(r, "Request #" + q.getRequestId() + " approved") == 1);
        ADMINS.flagRequest(a, q.getRequestId(), true);
        T.yes("Flagging notifies the receiver and the donor", "'flagged for review' x1 each", Env.messagesWith(r, "flagged") == 1 && Env.messagesWith(d, "flagged") == 1);
        ADMINS.cancelRequest(a, q.getRequestId());
        T.yes("Admin cancel notifies both sides", "'cancelled by administrator' x1 each", Env.messagesWith(r, "cancelled by administrator") == 1 && Env.messagesWith(d, "cancelled by administrator") == 1);
        Request q2 = REQ.submit(r, f.getFoodId(), "10:00-11:00", "");
        REQ.reject(d, q2.getRequestId());
        T.yes("Rejection notifies the receiver", "'rejected'", Env.messagesWith(r, "Request #" + q2.getRequestId() + " rejected") == 1);
        Request q3 = REQ.submit(r, f.getFoodId(), "10:00-11:00", "");
        String code = REQ.approve(d, q3.getRequestId());
        REQ.confirmPickup(r, q3.getRequestId(), code);
        REQ.complete(d, q3.getRequestId());
        T.yes("Pickup confirmed notifies the donor", "'Pickup confirmed'", Env.messagesWith(d, "Pickup confirmed") == 1);
        T.yes("Completion notifies the receiver", "'completed'", Env.messagesWith(r, "completed") == 1);
        T.yes("System notifications show sender 'System'", "System", MSG.inbox(r).stream().anyMatch(m -> "System".equals(m.getSenderName())));
        T.yes("Administrator messages show up in the 'recently sent' list", "at least 4 admin messages", MSG.recentAdminMessages(a, 50).size() >= 4);
    }

    // ------------------------------------------------------------------ donor edit / delete

    private static void donorEditDelete() {
        T.area("Donor food edit / delete");
        Env.reset();
        User d = Env.donor("Edit Donor", "Dhaka");
        User r = Env.receiver("Edit NGO", "Dhaka", 100);
        LocalDateTime now = LocalDateTime.now();
        Food f = Env.food(d, "Original", 10, 20);
        Food edited = FOODS.updateFood(d, f.getFoodId(), "Edited name", "Bakery", 12, now.minusHours(2), now.plusHours(3), "", "New place", "No nuts");
        T.eq("Edit changes the name", "Edited name", edited.getFoodName());
        T.eq("Edit changes the quantity", 12, edited.getQuantity());
        T.eq("Edit recalculates priority from the new expiry", "High", edited.getPriorityLevel());
        T.denied("Edit with invalid quantity rejected", "greater than zero", () -> FOODS.updateFood(d, f.getFoodId(), "N", "C", 0, now.minusHours(2), now.plusHours(3), "", "x", ""));
        T.eq("Delete is allowed for an Available post", "ok", Env.deleteResult(d, Env.food(d, "ToDelete", 3, 5)));
        Request q = REQ.submit(r, f.getFoodId(), "10:00-11:00", "");
        T.denied("Edit of a Requested post is refused", "only be edited while it is available", () -> FOODS.updateFood(d, f.getFoodId(), "X", "C", 1, now.minusHours(2), now.plusHours(3), "", "x", ""));
        T.denied("Delete of a Requested post is refused", "only be deleted while it is available", () -> FOODS.deleteFood(d, f.getFoodId()));
        REQ.approve(d, q.getRequestId());
        T.denied("Edit of a Reserved post is refused", "only be edited", () -> FOODS.updateFood(d, f.getFoodId(), "X", "C", 1, now.minusHours(2), now.plusHours(3), "", "x", ""));
        T.denied("Delete of a Reserved post is refused", "only be deleted", () -> FOODS.deleteFood(d, f.getFoodId()));
        T.eq("Post is still there after refused delete", "Reserved", Env.foodStatus(f.getFoodId()));
        REQ.cancelByReceiver(r, q.getRequestId());
        T.works("After the request is cancelled the post can be edited again", "edit ok", () -> FOODS.updateFood(d, f.getFoodId(), "Again", "C", 5, now.minusHours(2), now.plusHours(3), "", "x", ""));
        T.works("... and deleted", "delete ok", () -> FOODS.deleteFood(d, f.getFoodId()));
        T.eq("Deleted post is gone", 0, Env.scalar("SELECT COUNT(*) FROM food WHERE FoodID = ?", f.getFoodId()));
        T.yes("Edit and delete are logged", "FOOD_EDITED and FOOD_DELETED present", Env.logCount("FOOD_EDITED") >= 2 && Env.logCount("FOOD_DELETED") >= 2);
    }

    // ------------------------------------------------------------------ FR-10 administrative monitoring

    private static void adminMonitoring() {
        T.area("Administrative monitoring (FR-10)");
        Env.reset();
        Admin a = Env.admin();
        User dA = Env.donor("Donor Dhaka", "Dhaka");
        User dB = Env.donor("Donor Rajshahi", "Rajshahi");
        User rA = Env.receiver("NGO Dhaka", "Dhaka", 100);
        User rB = Env.receiver("NGO Rajshahi", "Rajshahi", 100);
        Request q1 = REQ.submit(rA, Env.food(dA, "F1", 5, 6).getFoodId(), "10:00-11:00", "n1");
        Food f2 = Env.food(dB, "F2", 5, 6);
        Request q2 = REQ.submit(rB, f2.getFoodId(), "10:00-11:00", "n2");
        REQ.approve(dB, q2.getRequestId());
        Request q3 = REQ.submit(rA, Env.food(dA, "F3", 5, 6).getFoodId(), "12:00-13:00", "");
        String code = REQ.approve(dA, q3.getRequestId(), "12:30-13:30");
        REQ.confirmPickup(rA, q3.getRequestId(), code);
        REQ.complete(dA, q3.getRequestId());
        Request q4 = REQ.submit(rB, Env.food(dB, "F4", 5, 6).getFoodId(), "14:00-15:00", "");
        REQ.cancelByReceiver(rB, q4.getRequestId());

        T.eq("Unfiltered monitoring shows all 4 interactions", 4, ADMINS.interactions(a, null, null, null, null, false).size());
        T.eq("Filter by status Requested", 1, ADMINS.interactions(a, "Requested", null, null, null, false).size());
        T.eq("Filter by status Reserved", 1, ADMINS.interactions(a, "Reserved", null, null, null, false).size());
        T.eq("Filter by status Completed", 1, ADMINS.interactions(a, "Completed", null, null, null, false).size());
        T.eq("Filter 'All' behaves like no filter", 4, ADMINS.interactions(a, "All", "All", null, null, false).size());
        T.eq("Filter by area Dhaka (donor or receiver)", 2, ADMINS.interactions(a, null, "Dhaka", null, null, false).size());
        T.eq("Area filter is case-insensitive", 2, ADMINS.interactions(a, null, "  rajshahi ", null, null, false).size());
        T.eq("Status + area together", 1, ADMINS.interactions(a, "Cancelled", "Rajshahi", null, null, false).size());
        LocalDate today = LocalDate.now();
        T.eq("Date filter today..today", 4, ADMINS.interactions(a, null, null, today, today, false).size());
        T.eq("Date filter yesterday only", 0, ADMINS.interactions(a, null, null, today.minusDays(1), today.minusDays(1), false).size());
        Env.exec("UPDATE request SET RequestedAt = ? WHERE RequestID = ?", LocalDateTime.now().minusDays(5), q1.getRequestId());
        T.eq("Date filter finds the request made 5 days ago", 1, ADMINS.interactions(a, null, null, today.minusDays(5), today.minusDays(5), false).size());
        T.eq("Open-ended 'from' date", 3, ADMINS.interactions(a, null, null, today.minusDays(1), null, false).size());
        T.denied("'to' before 'from' is rejected", "cannot be before", () -> ADMINS.interactions(a, null, null, today, today.minusDays(2), false));
        Request done = ADMINS.interactions(a, "Completed", null, null, null, false).get(0);
        T.yes("Interaction shows donor, receiver, food, status", "Donor Dhaka / NGO Dhaka / F3 / Completed",
            "Donor Dhaka".equals(done.getDonorName()) && "NGO Dhaka".equals(done.getReceiverName()) && "F3".equals(done.getFoodName()) && "Completed".equals(done.getStatus()));
        T.yes("Interaction shows timestamps and approved slot", "RequestedAt, UpdatedAt, PickupTime set, slot 12:30-13:30",
            done.getRequestedAt() != null && done.getUpdatedAt() != null && done.getPickupTime() != null && "12:30-13:30".equals(done.getApprovedSlot()));
        T.yes("Interaction shows donor and receiver area", "Dhaka / Dhaka", "Dhaka".equals(done.getDonorArea()) && "Dhaka".equals(done.getReceiverArea()));

        T.eq("No suspicious requests yet", 0, ADMINS.suspiciousRequests(a).size());
        ADMINS.flagRequest(a, q2.getRequestId(), true);
        T.eq("Admin flags a request as suspicious", 1, ADMINS.suspiciousRequests(a).size());
        T.eq("'Suspicious only' filter", 1, ADMINS.interactions(a, null, null, null, null, true).size());
        ADMINS.flagRequest(a, q2.getRequestId(), false);
        T.eq("Admin removes the flag", 0, ADMINS.suspiciousRequests(a).size());
        User rC = Env.receiver("Busy NGO", "Dhaka", 500);
        List<Request> busy = new ArrayList<>();
        for (int i = 0; i < 4; i++) busy.add(REQ.submit(rC, Env.food(dA, "Bulk" + i, 5, 6).getFoodId(), "10:00-11:00", ""));
        T.yes("Receiver with 3 open requests: the 4th is auto-flagged", "first 3 normal, 4th suspicious",
            !busy.get(0).isSuspicious() && !busy.get(2).isSuspicious() && busy.get(3).isSuspicious());
        T.eq("Auto-flagged request appears in the suspicious list", 1, ADMINS.suspiciousRequests(a).size());

        ADMINS.cancelRequest(a, q1.getRequestId());
        T.eq("Admin cancels a Requested request", "Cancelled", Env.reqStatus(q1.getRequestId()));
        ADMINS.cancelRequest(a, q2.getRequestId());
        T.eq("Admin cancels a Reserved request", "Cancelled", Env.reqStatus(q2.getRequestId()));
        T.eq("Admin cancel releases the food", "Available", Env.foodStatus(f2.getFoodId()));
        T.denied("Admin cannot cancel a Completed request", "only requested or reserved", () -> ADMINS.cancelRequest(a, q3.getRequestId()));
        T.denied("Admin cancel of unknown request refused", "does not exist", () -> ADMINS.cancelRequest(a, 99999));
        T.yes("Activity log can be viewed and contains admin actions", "REQUEST_CANCELLED and REQUEST_FLAGGED present",
            ADMINS.recentLogs(a, 500).stream().anyMatch(l -> "REQUEST_CANCELLED".equals(l.getAction()))
            && ADMINS.recentLogs(a, 500).stream().anyMatch(l -> "REQUEST_FLAGGED".equals(l.getAction())));
        T.yes("Every log entry has a timestamp", "no NULL LoggedAt", Env.scalar("SELECT COUNT(*) FROM activity_log WHERE LoggedAt IS NULL") == 0);
        T.eq("Pending NGO list works", 0, ADMINS.pendingReceivers(a).size());
        User p = AUTH.register("Wait NGO", "wait@test.local", "secret1", Constants.ROLE_RECEIVER, "Dhaka", 5);
        T.eq("Pending NGO appears in the verification list", 1, ADMINS.pendingReceivers(a).size());
        ADMINS.verifyReceiver(a, p.getUserId(), true);
        T.eq("Verified NGO can log in", p.getUserId(), AUTH.loginUser(p.getContact(), "secret1").getUserId());
        T.denied("Verifying a non-pending account is refused", "not pending", () -> ADMINS.verifyReceiver(a, p.getUserId(), true));
        T.denied("Verifying a donor as NGO is refused", "not a receiver", () -> ADMINS.verifyReceiver(a, dA.getUserId(), true));
        T.eq("Administrator list shows administrators", 1, ADMINS.allAdmins(a).size());
        T.eq("Area list available to admin", true, ADMINS.areas(a).size() >= 10);
    }

    // ------------------------------------------------------------------ 8. Reporting

    private static void reporting() {
        T.area("Reporting (FR-11)");
        Env.reset();
        Admin a = Env.admin();
        T.yes("Report on an empty database does not crash", "text report generated", ADMINS.generateReport(a).contains("Waste-reduction rate"));
        User dA = Env.donor("Top Donor", "Dhaka");
        User dB = Env.donor("Small Donor", "Rajshahi");
        User r = Env.receiver("Top NGO", "Dhaka", 500);
        Food f1 = Env.food(dA, "Rescued A", 10, 2);        // High
        Food f2 = Env.food(dA, "Rescued B", 20, 8);        // Medium
        Food f3 = Env.food(dB, "Wasted C", 6, 3);          // High, expires
        for (Food f : new Food[] {f1, f2}) {
            Request q = REQ.submit(r, f.getFoodId(), "10:00-11:00", "");
            String code = REQ.approve(dA, q.getRequestId());
            REQ.confirmPickup(r, q.getRequestId(), code);
            REQ.complete(dA, q.getRequestId());
        }
        Env.exec("UPDATE food SET ExpiryTime = ? WHERE FoodID = ?", LocalDateTime.now().minusMinutes(2), f3.getFoodId());
        ExpiryService.sync();
        String text = ADMINS.generateReport(a);
        T.yes("Text report: rescued quantity 30", "contains 'rescued (picked up / completed): 30'", text.contains("rescued (picked up / completed): 30"));
        T.yes("Text report: expired quantity 6", "contains 'expired unclaimed)    : 6'", text.contains("expired unclaimed)    : 6"));
        T.yes("Text report: waste-reduction rate 83.3%", "contains 83.3%", text.contains("83.3%"));
        T.yes("Text report: by priority, by area, top donors and top receivers sections", "sections 5-8 present",
            text.contains("by priority") && text.contains("by pickup area") && text.contains("Top donors") && text.contains("Top receivers"));
        T.yes("Text report names the top donor and top receiver", "Top Donor and Top NGO in report", text.contains("Top Donor") && text.contains("Top NGO"));
        String dist = ADMINS.distributionCsv(a);
        String waste = ADMINS.wasteReductionCsv(a);
        T.yes("Distribution CSV has all sections", "users, food, requests, area, priority, donors, receivers",
            dist.contains("Users by role and status") && dist.contains("Food by status") && dist.contains("Requests by status")
            && dist.contains("Distribution by pickup area") && dist.contains("Distribution by priority") && dist.contains("Top donors") && dist.contains("Top receivers"));
        T.yes("Distribution CSV names Top Donor / Top NGO", "names present", dist.contains("Top Donor") && dist.contains("Top NGO"));
        T.yes("Waste-reduction CSV: rescued 30 / expired 6 / rate 83.3%", "Quantity rescued,30; Quantity expired,6; 83.3%",
            waste.contains("Quantity rescued,30") && waste.contains("Quantity expired,6") && waste.contains("83.3%"));
        T.yes("Waste-reduction CSV: by priority and by area sections", "sections present", waste.contains("By priority") && waste.contains("By area"));
        T.yes("CSV by priority shows High rescued 10 and expired 6", "High row has 10 and 6", waste.contains("High,2,1,1,10,6"));
        T.yes("CSV by area shows Dhaka rescued 30, Rajshahi expired 6", "Dhaka,2,30,0 and Rajshahi,1,0,6", waste.contains("Dhaka,2,30,0") && waste.contains("Rajshahi,1,0,6"));
        T.works("CSV files are written to ./reports", "two .csv files exist", () -> {
            List<Path> files = ADMINS.exportCsv(a);
            if (files.size() != 2) throw new IllegalStateException("expected 2 files, got " + files.size());
            for (Path pth : files) {
                if (!Files.exists(pth) || Files.size(pth) < 50) throw new IllegalStateException("missing/empty " + pth);
                if (!pth.getFileName().toString().endsWith(".csv")) throw new IllegalStateException("not csv " + pth);
                Files.delete(pth);
            }
        });
        T.works("Text report can be saved", "txt file exists", () -> {
            Path pth = ADMINS.saveReport(a, text);
            if (!Files.exists(pth)) throw new IllegalStateException("not saved");
            Files.delete(pth);
        });
    }

    // ------------------------------------------------------------------ concurrency + performance (NFR)

    private static void concurrency() throws Exception {
        T.area("Concurrency and performance (NFR: 5+ concurrent users)");
        Env.reset();
        final int racers = 8;
        User d = Env.donor("Race Donor", "Dhaka");
        final List<User> receivers = new ArrayList<>();
        for (int i = 0; i < racers; i++) receivers.add(Env.receiver("Racer " + i, "Dhaka", 100));
        final Food f = Env.food(d, "Contested food", 10, 6);
        final int[] outcome = race(racers, i -> REQ.submit(receivers.get(i), f.getFoodId(), "10:00-11:00", ""));
        T.eq("8 simultaneous users request the SAME food: exactly one wins", 1, outcome[0]);
        T.eq("The other 7 are told it is no longer available", 7, outcome[1]);
        T.eq("Only one request row exists for the food", 1, Env.scalar("SELECT COUNT(*) FROM request WHERE FoodID = ?", f.getFoodId()));
        T.eq("The food ended in status Requested", "Requested", Env.foodStatus(f.getFoodId()));
        T.eq("No unexpected errors in the race", 0, outcome[2]);

        // 5 users confirm the same pickup with the right code at once
        final Request q = REQ.forDonor(d).get(0);
        final String code = REQ.approve(d, q.getRequestId());
        final User winner = receivers.stream().filter(u -> u.getUserId() == q.getUserId()).findFirst().get();
        int[] pick = race(5, i -> { REQ.confirmPickup(winner, q.getRequestId(), code); return null; });
        T.eq("5 simultaneous pickup confirmations: exactly one succeeds", 1, pick[0]);
        T.eq("Request is Picked Up exactly once", "Picked Up", Env.reqStatus(q.getRequestId()));
        T.eq("Exactly one PICKUP_CONFIRMED log entry", 1, Env.logCount("PICKUP_CONFIRMED"));

        // 6 independent donor/receiver pairs working in parallel all succeed
        final List<User> ds = new ArrayList<>(), rs = new ArrayList<>();
        final List<Food> fs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            User dd = Env.donor("Par Donor " + i, "Rajshahi");
            ds.add(dd);
            rs.add(Env.receiver("Par NGO " + i, "Rajshahi", 100));
            fs.add(Env.food(dd, "Par food " + i, 5, 6));
        }
        int[] par = race(6, i -> {
            Request pr = REQ.submit(rs.get(i), fs.get(i).getFoodId(), "10:00-11:00", "");
            String c = REQ.approve(ds.get(i), pr.getRequestId());
            REQ.confirmPickup(rs.get(i), pr.getRequestId(), c);
            REQ.complete(ds.get(i), pr.getRequestId());
            return null;
        });
        T.eq("6 concurrent users run the full workflow on different food: all succeed", 6, par[0]);
        T.eq("... and all 6 items are Completed", 6, Env.scalar("SELECT COUNT(*) FROM food WHERE Status = 'Completed' AND FoodName LIKE 'Par food%'"));

        // approve vs cancel race: final state must always be consistent
        int inconsistent = 0;
        for (int round = 0; round < 15; round++) {
            final User dr = Env.donor("RC Donor " + round, "Khulna");
            final User rr = Env.receiver("RC NGO " + round, "Khulna", 100);
            Food ff = Env.food(dr, "RC food " + round, 5, 6);
            final Request rq = REQ.submit(rr, ff.getFoodId(), "10:00-11:00", "");
            race(2, i -> {
                if (i == 0) REQ.approve(dr, rq.getRequestId());
                else REQ.cancelByReceiver(rr, rq.getRequestId());
                return null;
            });
            String rst = Env.reqStatus(rq.getRequestId()), fst = Env.foodStatus(ff.getFoodId());
            boolean ok = ("Reserved".equals(rst) && "Reserved".equals(fst)) || ("Cancelled".equals(rst) && "Available".equals(fst));
            if (!ok) inconsistent++;
        }
        T.eq("Approve-vs-cancel races (15 rounds) never leave request and food inconsistent", 0, inconsistent);

        // connection pool
        final java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger();
        int[] pool = race(30, i -> {
            for (int k = 0; k < 15; k++) {
                try { MSG.unreadCount(rs.get(i % rs.size())); } catch (RuntimeException e) { failures.incrementAndGet(); }
            }
            return null;
        });
        T.eq("30 threads x 15 queries through the pool: no failures", 0, failures.get() + pool[1] + pool[2]);
        long conns = Env.scalar("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB = ?", Env.dbName);
        T.yes("Connection pool keeps open connections at or below the pool size (10)", "<= 10 open connections, found " + conns, conns <= 10);

        // performance with a few thousand rows
        long t0 = System.currentTimeMillis();
        User pd = Env.donor("Perf Donor", "Dhaka");
        User pr = Env.receiver("Perf NGO", "Dhaka", 1000);
        for (int i = 0; i < 2000; i++)
            Env.exec("INSERT INTO food (FoodName, Category, Quantity, PreparationTime, ExpiryTime, PickupArea, Location, SafetyInfo, PriorityLevel, Status, UserID, CreatedAt) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", "Bulk " + i, "Meal", 5, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(2 + i % 40),
                "Dhaka", "Loc", null, "High", "Available", pd.getUserId(), LocalDateTime.now());
        long t1 = System.currentTimeMillis();
        List<Food> big = FOODS.availableFor(pr, false, false);
        long t2 = System.currentTimeMillis();
        ADMINS.generateReport(Env.admin());
        long t3 = System.currentTimeMillis();
        ADMINS.interactions(Env.admin(), null, null, null, null, false);
        long t4 = System.currentTimeMillis();
        T.yes("Browsing " + big.size() + " available foods returns within 2 seconds", (t2 - t1) + " ms < 2000 ms", (t2 - t1) < 2000);
        T.yes("Generating the full report returns within 2 seconds", (t3 - t2) + " ms < 2000 ms", (t3 - t2) < 2000);
        T.yes("Monitoring query returns within 2 seconds", (t4 - t3) + " ms < 2000 ms", (t4 - t3) < 2000);
        System.out.println("  (seeding 2000 rows took " + (t1 - t0) + " ms)");
    }

    private interface Job { Object run(int index) throws Exception; }

    /** Starts n threads at the same instant. Returns {successes, ServiceException failures, other errors}. */
    private static int[] race(int n, final Job job) throws Exception {
        final java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(n);
        final java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger ok = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger refused = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger other = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(n);
        List<java.util.concurrent.Future<?>> fut = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            fut.add(ex.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    job.run(idx);
                    ok.incrementAndGet();
                } catch (com.foodwaste.service.ServiceException e) {
                    if (e.getMessage() != null && e.getMessage().startsWith("Database error")) other.incrementAndGet();
                    else refused.incrementAndGet();
                } catch (Exception e) {
                    other.incrementAndGet();
                    System.out.println("   unexpected: " + e);
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (java.util.concurrent.Future<?> f : fut) f.get();
        ex.shutdown();
        return new int[] {ok.get(), refused.get(), other.get()};
    }

    // ------------------------------------------------------------------ security

    private static void security() throws Exception {
        T.area("Security (SQL injection, PreparedStatement)");
        Env.reset();
        T.denied("SQL injection in the login field is just a wrong login", "invalid contact or password", () -> AUTH.loginUser("' OR '1'='1", "' OR '1'='1"));
        T.denied("SQL injection in the admin username is just a wrong login", "invalid administrator", () -> AUTH.loginAdmin("admin' --", "x"));
        String nasty = "Robert'); DROP TABLE `user`;-- \"quoted\"";
        User u = AUTH.register(nasty, "inj@test.local", "secret1", Constants.ROLE_DONOR, "Dhaka", null);
        T.eq("Hostile text is stored literally", nasty, Env.str("SELECT Name FROM `user` WHERE UserID = ?", u.getUserId()));
        T.yes("The user table still exists after the injection attempt", "row count >= 1", Env.scalar("SELECT COUNT(*) FROM `user`") >= 1);
        Path src = java.nio.file.Paths.get("src");
        if (Files.isDirectory(src)) {
            final List<String> bad = new ArrayList<>();
            try (java.util.stream.Stream<Path> w = Files.walk(src)) {
                w.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    try {
                        String code = new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8);
                        if (code.contains("createStatement(")) bad.add(p.toString());
                    } catch (java.io.IOException e) {
                        bad.add(p + " unreadable");
                    }
                });
            }
            T.yes("Source scan: no createStatement() anywhere (all SQL uses PreparedStatement)", "0 files, found " + bad.size() + " " + bad, bad.isEmpty());
        } else {
            T.check("Source scan for createStatement", "src folder reachable", "skipped: run from the project root", true);
        }
    }
}
