package com.foodwaste.dao;

import com.foodwaste.model.Request;
import com.foodwaste.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Data access for the REQUEST table. */
public class RequestDAO {
    private static final String SEL =
        "SELECT r.*, f.FoodName, f.UserID AS DonorID, d.Name AS DonorName, d.AreaID AS DonorArea, f.PickupArea AS PickupArea, "
        + "u.Name AS ReceiverName, u.AreaID AS ReceiverArea, p.PickupTime AS PickupTime, p.Status AS PickupStatus "
        + "FROM request r JOIN food f ON r.FoodID = f.FoodID "
        + "JOIN `user` d ON f.UserID = d.UserID JOIN `user` u ON r.UserID = u.UserID "
        + "LEFT JOIN pickup p ON p.RequestID = r.RequestID ";

    private static final Db.RowMapper<Request> MAP = rs -> {
        Request r = new Request();
        r.setRequestId(rs.getInt("RequestID"));
        r.setFoodId(rs.getInt("FoodID"));
        r.setUserId(rs.getInt("UserID"));
        r.setTimeSlot(rs.getString("TimeSlot"));
        r.setApprovedSlot(rs.getString("ApprovedSlot"));
        r.setNotes(rs.getString("Notes"));
        r.setStatus(rs.getString("Status"));
        r.setSuspicious(rs.getBoolean("IsSuspicious"));
        r.setRequestedAt(Db.ts(rs, "RequestedAt"));
        r.setUpdatedAt(Db.ts(rs, "UpdatedAt"));
        r.setFoodName(rs.getString("FoodName"));
        r.setDonorId(rs.getInt("DonorID"));
        r.setDonorName(rs.getString("DonorName"));
        r.setDonorArea(rs.getString("DonorArea"));
        r.setPickupArea(rs.getString("PickupArea"));
        r.setReceiverName(rs.getString("ReceiverName"));
        r.setReceiverArea(rs.getString("ReceiverArea"));
        r.setPickupTime(Db.ts(rs, "PickupTime"));
        r.setPickupStatus(rs.getString("PickupStatus"));
        return r;
    };

    public int insert(Connection c, Request r) throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        return Db.insert(c,
            "INSERT INTO request (FoodID, UserID, TimeSlot, Notes, Status, IsSuspicious, RequestedAt, UpdatedAt)"
            + " VALUES (?,?,?,?,?,?,?,?)",
            r.getFoodId(), r.getUserId(), r.getTimeSlot(), r.getNotes(), r.getStatus(), r.isSuspicious() ? 1 : 0, now, now);
    }

    public Request findById(Connection c, int id) throws SQLException {
        return Db.one(c, SEL + "WHERE r.RequestID = ?", MAP, id);
    }

    public List<Request> findByReceiver(Connection c, int userId) throws SQLException {
        return Db.list(c, SEL + "WHERE r.UserID = ? ORDER BY r.RequestedAt DESC, r.RequestID DESC", MAP, userId);
    }

    public List<Request> findByDonor(Connection c, int donorId) throws SQLException {
        return Db.list(c, SEL + "WHERE f.UserID = ? ORDER BY r.RequestedAt DESC, r.RequestID DESC", MAP, donorId);
    }

    public List<Request> findAll(Connection c) throws SQLException {
        return Db.list(c, SEL + "ORDER BY r.RequestedAt DESC, r.RequestID DESC", MAP);
    }

    public List<Request> findSuspicious(Connection c) throws SQLException {
        return Db.list(c, SEL + "WHERE r.IsSuspicious = 1 ORDER BY r.RequestedAt DESC, r.RequestID DESC", MAP);
    }

    /**
     * FR-10 monitoring: donor-receiver interactions filtered by status, area (donor OR receiver area), request date range
     * and the suspicious flag. Every filter that is null/false is skipped; all values are bound as parameters.
     */
    public List<Request> search(Connection c, String status, String area, LocalDateTime from, LocalDateTime toExclusive,
                                boolean suspiciousOnly) throws SQLException {
        StringBuilder sql = new StringBuilder(SEL).append("WHERE 1 = 1 ");
        List<Object> p = new ArrayList<>();
        if (status != null) { sql.append("AND r.Status = ? "); p.add(status); }
        if (area != null) {
            sql.append("AND (LOWER(TRIM(f.PickupArea)) = LOWER(TRIM(?)) OR LOWER(TRIM(u.AreaID)) = LOWER(TRIM(?))) ");
            p.add(area); p.add(area);
        }
        if (from != null) { sql.append("AND r.RequestedAt >= ? "); p.add(from); }
        if (toExclusive != null) { sql.append("AND r.RequestedAt < ? "); p.add(toExclusive); }
        if (suspiciousOnly) sql.append("AND r.IsSuspicious = 1 ");
        sql.append("ORDER BY r.RequestedAt DESC, r.RequestID DESC LIMIT 2000");
        return Db.list(c, sql.toString(), MAP, p.toArray());
    }

    public long countOpenByReceiver(Connection c, int userId) throws SQLException {
        return Db.scalar(c, "SELECT COUNT(*) FROM request WHERE UserID = ? AND Status IN ('Requested','Reserved')", userId);
    }

    /** Guarded status change; returns 0 if the request was not in the expected state. */
    public int transition(Connection c, int requestId, String from, String to) throws SQLException {
        return Db.update(c, "UPDATE request SET Status = ?, UpdatedAt = ? WHERE RequestID = ? AND Status = ?",
            to, LocalDateTime.now(), requestId, from);
    }

    /** Requested -> Reserved in one guarded statement that also stores the slot the donor agreed to / countered with. */
    public int approve(Connection c, int requestId, String approvedSlot) throws SQLException {
        return Db.update(c, "UPDATE request SET Status = 'Reserved', ApprovedSlot = ?, UpdatedAt = ? WHERE RequestID = ? AND Status = 'Requested'",
            approvedSlot, LocalDateTime.now(), requestId);
    }

    public int setSuspicious(Connection c, int requestId, boolean flag) throws SQLException {
        return Db.update(c, "UPDATE request SET IsSuspicious = ?, UpdatedAt = ? WHERE RequestID = ?",
            flag ? 1 : 0, LocalDateTime.now(), requestId);
    }

    /** Stale = still Requested and submitted before the cutoff (donor never answered). */
    public List<Request> findStale(Connection c, LocalDateTime cutoff) throws SQLException {
        return Db.list(c, SEL + "WHERE r.Status = 'Requested' AND r.RequestedAt <= ? ORDER BY r.RequestID", MAP, cutoff);
    }

    /** Open (Requested/Reserved) requests on one food item. */
    public List<Request> findOpenByFood(Connection c, int foodId) throws SQLException {
        return Db.list(c, SEL + "WHERE r.FoodID = ? AND r.Status IN ('Requested','Reserved')", MAP, foodId);
    }

    /** FR-09: open requests whose food has run past its expiry time become Expired. */
    public int expireOverdue(Connection c, LocalDateTime now) throws SQLException {
        return Db.update(c,
            "UPDATE request r JOIN food f ON r.FoodID = f.FoodID SET r.Status = 'Expired', r.UpdatedAt = ? "
            + "WHERE r.Status IN ('Requested','Reserved') AND f.Status IN ('Available','Requested','Reserved') "
            + "AND f.ExpiryTime <= ?", now, now);
    }
}
