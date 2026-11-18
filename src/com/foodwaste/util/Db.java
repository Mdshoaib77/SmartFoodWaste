package com.foodwaste.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Tiny JDBC helper. Every statement is a PreparedStatement with bound parameters. */
public final class Db {
    private Db() { }

    public interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object o = params[i];
            if (o == null) ps.setNull(i + 1, Types.NULL);
            else if (o instanceof LocalDateTime) ps.setTimestamp(i + 1, Timestamp.valueOf((LocalDateTime) o));
            else ps.setObject(i + 1, o);
        }
    }

    public static <T> List<T> list(Connection c, String sql, RowMapper<T> m, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(m.map(rs));
                return out;
            }
        }
    }

    public static <T> T one(Connection c, String sql, RowMapper<T> m, Object... p) throws SQLException {
        List<T> l = list(c, sql, m, p);
        return l.isEmpty() ? null : l.get(0);
    }

    public static int update(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            return ps.executeUpdate();
        }
    }

    /** Executes an INSERT and returns the generated key. */
    public static int insert(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, p);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { return k.next() ? k.getInt(1) : -1; }
        }
    }

    public static long scalar(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
        }
    }

    /** Runs a query and returns it as string rows; element 0 holds the column headings. */
    public static List<String[]> table(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                List<String[]> out = new ArrayList<>();
                String[] head = new String[n];
                for (int i = 0; i < n; i++) head[i] = md.getColumnLabel(i + 1);
                out.add(head);
                while (rs.next()) {
                    String[] row = new String[n];
                    for (int i = 0; i < n; i++) row[i] = Fmt.nz(rs.getString(i + 1));
                    out.add(row);
                }
                return out;
            }
        }
    }

    public static LocalDateTime ts(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }
}
