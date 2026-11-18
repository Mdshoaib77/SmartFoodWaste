package com.foodwaste.dao;

import com.foodwaste.model.Pickup;
import com.foodwaste.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** Data access for the PICKUP table. */
public class PickupDAO {
    private static final Db.RowMapper<Pickup> MAP = rs -> {
        Pickup p = new Pickup();
        p.setPickupId(rs.getInt("PickupID"));
        p.setRequestId(rs.getInt("RequestID"));
        p.setPickupTime(Db.ts(rs, "PickupTime"));
        p.setVerificationCode(rs.getString("VerificationCode"));
        p.setStatus(rs.getString("Status"));
        return p;
    };

    public int insert(Connection c, int requestId, String code) throws SQLException {
        return Db.insert(c,
            "INSERT INTO pickup (RequestID, VerificationCode, Status, GeneratedAt) VALUES (?,?,'Pending',?)",
            requestId, code, LocalDateTime.now());
    }

    public Pickup findByRequest(Connection c, int requestId) throws SQLException {
        return Db.one(c, "SELECT PickupID, RequestID, PickupTime, VerificationCode, Status FROM pickup WHERE RequestID = ?",
            MAP, requestId);
    }

    public int markPickedUp(Connection c, int requestId, LocalDateTime when) throws SQLException {
        return Db.update(c, "UPDATE pickup SET Status = 'Picked Up', PickupTime = ? WHERE RequestID = ? AND Status = 'Pending'",
            when, requestId);
    }

    public int setStatus(Connection c, int requestId, String status) throws SQLException {
        return Db.update(c, "UPDATE pickup SET Status = ? WHERE RequestID = ?", status, requestId);
    }

    /** FR-09: pending pickups of food that has just expired. Must run before FoodDAO.expireOverdue. */
    public int expireOverdue(Connection c, LocalDateTime now) throws SQLException {
        return Db.update(c,
            "UPDATE pickup p JOIN request r ON p.RequestID = r.RequestID JOIN food f ON r.FoodID = f.FoodID "
            + "SET p.Status = 'Expired' WHERE p.Status = 'Pending' "
            + "AND f.Status IN ('Available','Requested','Reserved') AND f.ExpiryTime <= ?", now);
    }
}
