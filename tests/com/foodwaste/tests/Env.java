package com.foodwaste.tests;

import com.foodwaste.config.DBConnection;
import com.foodwaste.model.Admin;
import com.foodwaste.model.Food;
import com.foodwaste.model.User;
import com.foodwaste.service.AdminService;
import com.foodwaste.service.AuthService;
import com.foodwaste.service.FoodService;
import com.foodwaste.service.Tx;
import com.foodwaste.util.Constants;
import com.foodwaste.util.Db;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test database set-up (a separate *_test schema, never the real data) and small data helpers. */
final class Env {
    static final AuthService AUTH = new AuthService();
    static final AdminService ADMINS = new AdminService();
    static final FoodService FOODS = new FoodService();
    static String dbName;
    static String productVersion = "?";
    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    private Env() { }

    /** Points the application at smart_food_waste_test and rebuilds the schema from database.sql. */
    static void setup() throws Exception {
        Properties p = new Properties();
        File f = new File("db.properties");
        if (f.exists()) try (InputStream in = new FileInputStream(f)) { p.load(in); }
        String url = p.getProperty("db.url", "jdbc:mysql://localhost:3306/smart_food_waste?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8");
        String user = p.getProperty("db.user", "root");
        String pw = p.getProperty("db.password", "");
        Matcher m = Pattern.compile("^(.*?/)([^/?]+)(\\?.*)?$").matcher(url);
        if (!m.matches()) throw new IllegalStateException("Cannot read the database name from db.url: " + url);
        String base = m.group(1), query = m.group(3) == null ? "" : m.group(3);
        String real = m.group(2);
        dbName = real.endsWith("_test") ? real : real + "_test";
        // database.sql always names smart_food_waste: rename it so ONLY the test schema is dropped / created
        String sql = new String(Files.readAllBytes(Paths.get("database.sql")), StandardCharsets.UTF_8).replace("smart_food_waste", dbName);
        try (Connection c = DriverManager.getConnection(base + query, user, pw)) {
            productVersion = c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion();
            for (String stmt : split(sql)) {
                try (PreparedStatement ps = c.prepareStatement(stmt)) { ps.execute(); }
            }
        }
        DBConnection.configure(base + dbName + query, user, pw);
    }

    /** Splits the script into statements: drops "--" comment lines, cuts at ';'. (database.sql has no procedures.) */
    private static List<String> split(String script) {
        StringBuilder cur = new StringBuilder();
        List<String> out = new java.util.ArrayList<>();
        for (String line : script.split("\r?\n")) {
            String t = line.trim();
            if (t.startsWith("--") || t.isEmpty()) continue;
            cur.append(line).append('\n');
            if (t.endsWith(";")) {
                String s = cur.toString().trim();
                out.add(s.substring(0, s.length() - 1));
                cur.setLength(0);
            }
        }
        return out;
    }

    /** Empties every table and re-seeds the default administrator. */
    static void reset() {
        Tx.tx(c -> {
            for (String t : new String[] {"login_throttle", "message", "activity_log", "pickup", "request", "food", "`user`", "admin"})
                Db.update(c, "DELETE FROM " + t);
            return null;
        });
        AUTH.ensureDefaultAdmin();
    }

    static void exec(String sql, Object... params) {
        Tx.tx(c -> Db.update(c, sql, params));
    }

    static long scalar(String sql, Object... params) {
        return Tx.read(c -> Db.scalar(c, sql, params));
    }

    static String str(String sql, Object... params) {
        return Tx.read(c -> Db.one(c, sql, rs -> rs.getString(1), params));
    }

    static Admin admin() {
        return AUTH.loginAdmin("admin", "admin123");
    }

    static String contact() { return "t" + SEQ.incrementAndGet() + "@test.local"; }

    static User donor(String name, String area) {
        return AUTH.register(name, contact(), "secret1", Constants.ROLE_DONOR, area, null);
    }

    /** Registers a Receiver/NGO and approves it as the administrator. */
    static User receiver(String name, String area, int capacity) {
        User r = AUTH.register(name, contact(), "secret1", Constants.ROLE_RECEIVER, area, capacity);
        ADMINS.verifyReceiver(admin(), r.getUserId(), true);
        return AUTH.loginUser(r.getContact(), "secret1");
    }

    static Food food(User donor, String name, int qty, int hoursLeft) {
        LocalDateTime now = LocalDateTime.now();
        return FOODS.postFood(donor, name, "Cooked meal", qty, now.minusHours(1), now.plusHours(hoursLeft), "", "Test location", "Keep cool");
    }

    /** Deletes a donor's food and returns "ok" (the service throws ServiceException otherwise). */
    static String deleteResult(User donor, Food f) {
        FOODS.deleteFood(donor, f.getFoodId());
        return "ok";
    }

    static String foodStatus(int id) { return str("SELECT Status FROM food WHERE FoodID = ?", id); }

    static String reqStatus(int id) { return str("SELECT Status FROM request WHERE RequestID = ?", id); }

    static long unread(User u) { return scalar("SELECT COUNT(*) FROM message WHERE RecipientUserID = ? AND IsRead = 0", u.getUserId()); }

    static long messagesWith(User u, String subjectPart) {
        return scalar("SELECT COUNT(*) FROM message WHERE RecipientUserID = ? AND Subject LIKE ?", u.getUserId(), "%" + subjectPart + "%");
    }

    static long logCount(String action) { return scalar("SELECT COUNT(*) FROM activity_log WHERE Action = ?", action); }
}
