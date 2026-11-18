package com.foodwaste.dao;

import com.foodwaste.model.User;
import com.foodwaste.util.Constants;
import com.foodwaste.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Data access for the USER table (table name is back-quoted: USER is a MySQL keyword). */
public class UserDAO {
    private static final String SEL =
        "SELECT UserID, Name, Contact, Password, Role, AreaID, Capacity, Status, VerifiedBy, CreatedAt FROM `user` ";

    private static final Db.RowMapper<User> MAP = rs -> {
        User u = new User();
        u.setUserId(rs.getInt("UserID"));
        u.setName(rs.getString("Name"));
        u.setContact(rs.getString("Contact"));
        u.setPasswordHash(rs.getString("Password"));
        u.setRole(rs.getString("Role"));
        u.setAreaId(rs.getString("AreaID"));
        int cap = rs.getInt("Capacity");
        u.setCapacity(rs.wasNull() ? null : cap);
        u.setStatus(rs.getString("Status"));
        int vb = rs.getInt("VerifiedBy");
        u.setVerifiedBy(rs.wasNull() ? null : vb);
        u.setCreatedAt(Db.ts(rs, "CreatedAt"));
        return u;
    };

    public int insert(Connection c, User u) throws SQLException {
        return Db.insert(c,
            "INSERT INTO `user` (Name, Contact, Password, Role, AreaID, Capacity, Status, CreatedAt) VALUES (?,?,?,?,?,?,?,?)",
            u.getName(), u.getContact(), u.getPasswordHash(), u.getRole(), u.getAreaId(), u.getCapacity(), u.getStatus(),
            LocalDateTime.now());
    }

    public User findById(Connection c, int id) throws SQLException {
        return Db.one(c, SEL + "WHERE UserID = ?", MAP, id);
    }

    public User findByContact(Connection c, String contact) throws SQLException {
        return Db.one(c, SEL + "WHERE Contact = ?", MAP, contact);
    }

    public List<User> findAll(Connection c) throws SQLException {
        return Db.list(c, SEL + "ORDER BY UserID", MAP);
    }

    public List<User> findByRoleAndStatus(Connection c, String role, String status) throws SQLException {
        return Db.list(c, SEL + "WHERE Role = ? AND Status = ? ORDER BY CreatedAt", MAP, role, status);
    }

    /**
     * FR-05: approved Receiver/NGO accounts in the given area (compared trimmed and case-insensitively).
     * When minCapacity is not null only receivers whose capacity can take that quantity are returned.
     */
    public List<User> findApprovedReceiversByArea(Connection c, String areaId, Integer minCapacity) throws SQLException {
        String sql = SEL + "WHERE Role = ? AND Status = ? AND LOWER(TRIM(AreaID)) = LOWER(TRIM(?)) "
            + (minCapacity == null ? "" : "AND Capacity >= ? ") + "ORDER BY Capacity DESC, Name";
        if (minCapacity == null)
            return Db.list(c, sql, MAP, Constants.ROLE_RECEIVER, Constants.USER_APPROVED, areaId);
        return Db.list(c, sql, MAP, Constants.ROLE_RECEIVER, Constants.USER_APPROVED, areaId, minCapacity);
    }

    public int updateStatus(Connection c, int userId, String status, int adminId) throws SQLException {
        return Db.update(c, "UPDATE `user` SET Status = ?, VerifiedBy = ? WHERE UserID = ?", status, adminId, userId);
    }

    public int updatePassword(Connection c, int userId, String hash) throws SQLException {
        return Db.update(c, "UPDATE `user` SET Password = ? WHERE UserID = ?", hash, userId);
    }
}
