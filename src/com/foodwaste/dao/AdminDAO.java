package com.foodwaste.dao;

import com.foodwaste.model.Admin;
import com.foodwaste.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Data access for the ADMIN table. */
public class AdminDAO {
    private static final String SEL = "SELECT AdminID, Username, Password, Role, MustChangePassword, CreatedBy, CreatedAt FROM admin ";

    private static final Db.RowMapper<Admin> MAP = rs -> {
        Admin a = new Admin();
        a.setAdminId(rs.getInt("AdminID"));
        a.setUsername(rs.getString("Username"));
        a.setPasswordHash(rs.getString("Password"));
        a.setRole(rs.getString("Role"));
        a.setMustChangePassword(rs.getBoolean("MustChangePassword"));
        int cb = rs.getInt("CreatedBy");
        a.setCreatedBy(rs.wasNull() ? null : cb);
        a.setCreatedAt(Db.ts(rs, "CreatedAt"));
        return a;
    };

    public Admin findByUsername(Connection c, String username) throws SQLException {
        return Db.one(c, SEL + "WHERE Username = ?", MAP, username);
    }

    public Admin findById(Connection c, int id) throws SQLException {
        return Db.one(c, SEL + "WHERE AdminID = ?", MAP, id);
    }

    public List<Admin> findAll(Connection c) throws SQLException {
        return Db.list(c, SEL + "ORDER BY AdminID", MAP);
    }

    public long count(Connection c) throws SQLException {
        return Db.scalar(c, "SELECT COUNT(*) FROM admin");
    }

    public int insert(Connection c, String username, String passwordHash, String role, boolean mustChange, Integer createdBy)
            throws SQLException {
        return Db.insert(c,
            "INSERT INTO admin (Username, Password, Role, MustChangePassword, CreatedBy, CreatedAt) VALUES (?,?,?,?,?,?)",
            username, passwordHash, role, mustChange ? 1 : 0, createdBy, LocalDateTime.now());
    }

    public int updatePassword(Connection c, int adminId, String hash) throws SQLException {
        return Db.update(c, "UPDATE admin SET Password = ?, MustChangePassword = 0 WHERE AdminID = ?", hash, adminId);
    }
}
