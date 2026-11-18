package com.foodwaste.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

/**
 * Reads db.properties (working directory) and hands out JDBC connections from a small fixed-size pool.
 * Calling close() on a pooled connection returns it to the pool (after rolling back anything unfinished),
 * so callers keep the usual try-with-resources pattern. At most POOL_SIZE connections are ever open, which
 * keeps the desktop app well inside MySQL's connection limit even with many concurrent users.
 */
public final class DBConnection {
    private static final String DEFAULT_URL =
        "jdbc:mysql://localhost:3306/smart_food_waste?useSSL=false&allowPublicKeyRetrieval=true"
        + "&serverTimezone=UTC&characterEncoding=UTF-8";
    private static final int DEFAULT_POOL_SIZE = 10;
    private static final long WAIT_MILLIS = 15_000;

    private static String url = DEFAULT_URL;
    private static String user = "root";
    private static String password = "";
    private static int poolSize = DEFAULT_POOL_SIZE;

    private static final Object LOCK = new Object();
    private static final Deque<Connection> IDLE = new ArrayDeque<>();
    private static int open;          // connections created and not yet physically closed (idle + borrowed)

    static {
        Properties p = new Properties();
        File f = new File("db.properties");
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
            } catch (IOException e) {
                System.err.println("Could not read db.properties: " + e.getMessage());
            }
        }
        url = p.getProperty("db.url", url);
        user = p.getProperty("db.user", user);
        password = p.getProperty("db.password", password);
        poolSize = parsePool(p.getProperty("db.pool.size"));
        try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (ClassNotFoundException ignored) { /* JDBC4 auto-load */ }
    }

    private DBConnection() { }

    private static int parsePool(String s) {
        try {
            int n = Integer.parseInt(s == null ? "" : s.trim());
            return n >= 2 && n <= 100 ? n : DEFAULT_POOL_SIZE;
        } catch (NumberFormatException e) {
            return DEFAULT_POOL_SIZE;
        }
    }

    /** Points the application at another database (used by the test runner). Closes all idle connections. */
    public static void configure(String newUrl, String newUser, String newPassword) {
        synchronized (LOCK) {
            url = newUrl;
            user = newUser;
            password = newPassword;
            while (!IDLE.isEmpty()) {
                closeQuietly(IDLE.poll());
                open--;
            }
        }
    }

    public static String url() { return url; }

    public static String user() { return user; }

    public static String password() { return password; }

    /** Borrows a connection; blocks up to 15 s when all pooled connections are busy. */
    public static Connection get() throws SQLException {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        synchronized (LOCK) {
            while (true) {
                Connection raw = IDLE.poll();
                if (raw != null) {
                    if (usable(raw)) return wrap(raw);
                    closeQuietly(raw);
                    open--;
                    continue;
                }
                if (open < poolSize) {
                    open++;
                    try {
                        return wrap(DriverManager.getConnection(url, user, password));
                    } catch (SQLException | RuntimeException e) {
                        open--;
                        throw e;
                    }
                }
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) throw new SQLException("All " + poolSize + " database connections are busy. Please try again.");
                try {
                    LOCK.wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted while waiting for a database connection.");
                }
            }
        }
    }

    private static boolean usable(Connection c) {
        try { return !c.isClosed() && c.isValid(2); } catch (SQLException e) { return false; }
    }

    private static Connection wrap(final Connection raw) {
        InvocationHandler h = new InvocationHandler() {
            private boolean released;
            @Override public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                if ("close".equals(m.getName())) {
                    if (!released) {
                        released = true;
                        release(raw);
                    }
                    return null;
                }
                if ("isClosed".equals(m.getName()) && released) return Boolean.TRUE;
                if (released) throw new SQLException("Connection already returned to the pool.");
                try {
                    return m.invoke(raw, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        };
        return (Connection) Proxy.newProxyInstance(DBConnection.class.getClassLoader(), new Class<?>[] {Connection.class}, h);
    }

    private static void release(Connection raw) {
        boolean healthy = true;
        try {
            if (!raw.getAutoCommit()) {
                raw.rollback();               // never hand back half-finished work
                raw.setAutoCommit(true);
            }
        } catch (SQLException e) {
            healthy = false;
        }
        synchronized (LOCK) {
            if (healthy && !isStale(raw)) {
                IDLE.push(raw);
            } else {
                closeQuietly(raw);
                open--;
            }
            LOCK.notifyAll();
        }
    }

    private static boolean isStale(Connection raw) {
        try { return raw.isClosed(); } catch (SQLException e) { return true; }
    }

    private static void closeQuietly(Connection c) {
        try { c.close(); } catch (SQLException ignored) { /* nothing to do */ }
    }

    /** Opens and closes a connection; used at start-up to give a clear error message. */
    public static void test() throws SQLException {
        try (Connection c = get()) { c.isValid(2); }
    }

    public static String describe() { return url + "  (user: " + user + ", pool: " + poolSize + ")"; }
}
