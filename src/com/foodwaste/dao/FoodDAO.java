package com.foodwaste.dao;

import com.foodwaste.model.Food;
import com.foodwaste.util.Constants;
import com.foodwaste.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Data access for the FOOD table. */
public class FoodDAO {
    private static final String SEL =
        "SELECT f.*, u.Name AS DonorName FROM food f JOIN `user` u ON f.UserID = u.UserID ";
    private static final String OPEN = "Status IN ('Available','Requested','Reserved')";
    private static final String SAME_AREA = "LOWER(TRIM(f.PickupArea)) = LOWER(TRIM(?))";

    private static final Db.RowMapper<Food> MAP = rs -> {
        Food f = new Food();
        f.setFoodId(rs.getInt("FoodID"));
        f.setFoodName(rs.getString("FoodName"));
        f.setCategory(rs.getString("Category"));
        f.setQuantity(rs.getInt("Quantity"));
        f.setPreparationTime(Db.ts(rs, "PreparationTime"));
        f.setExpiryTime(Db.ts(rs, "ExpiryTime"));
        f.setLocation(rs.getString("Location"));
        f.setSafetyInfo(rs.getString("SafetyInfo"));
        f.setPriorityLevel(rs.getString("PriorityLevel"));
        f.setStatus(rs.getString("Status"));
        f.setUserId(rs.getInt("UserID"));
        f.setCreatedAt(Db.ts(rs, "CreatedAt"));
        f.setDonorName(rs.getString("DonorName"));
        f.setAreaId(rs.getString("PickupArea"));
        return f;
    };

    public int insert(Connection c, Food f) throws SQLException {
        return Db.insert(c,
            "INSERT INTO food (FoodName, Category, Quantity, PreparationTime, ExpiryTime, PickupArea, Location, SafetyInfo,"
            + " PriorityLevel, Status, UserID, CreatedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            f.getFoodName(), f.getCategory(), f.getQuantity(), f.getPreparationTime(), f.getExpiryTime(),
            f.getAreaId(), f.getLocation(), f.getSafetyInfo(), f.getPriorityLevel(), f.getStatus(), f.getUserId(), LocalDateTime.now());
    }

    public Food findById(Connection c, int id) throws SQLException {
        return Db.one(c, SEL + "WHERE f.FoodID = ?", MAP, id);
    }

    public List<Food> findByDonor(Connection c, int donorId) throws SQLException {
        return Db.list(c, SEL + "WHERE f.UserID = ? ORDER BY f.CreatedAt DESC, f.FoodID DESC", MAP, donorId);
    }

    public List<Food> findAll(Connection c) throws SQLException {
        return Db.list(c, SEL + "ORDER BY f.FoodID DESC", MAP);
    }

    /**
     * FR-05 / FR-06: unexpired Available food. Order: High before Medium before Low, then food picked up in the receiver's own
     * area first inside each priority (the area of the FOOD'S PICKUP LOCATION, not the donor's home area), then soonest expiry. nearbyOnly restricts the list to the receiver's own area;
     * maxQuantity (when not null) hides food larger than that quantity (the receiver's capacity).
     */
    public List<Food> findAvailable(Connection c, String areaId, boolean nearbyOnly, LocalDateTime now, Integer maxQuantity)
            throws SQLException {
        String order = "ORDER BY FIELD(f.PriorityLevel,'High','Medium','Low'), (" + SAME_AREA + ") DESC, f.ExpiryTime, f.FoodID";
        String where = "WHERE f.Status = 'Available' AND f.ExpiryTime > ? ";
        if (nearbyOnly) where += "AND " + SAME_AREA + " ";
        if (maxQuantity != null) where += "AND f.Quantity <= ? ";
        // bind order must follow the placeholders in the final SQL text: where-part first, ORDER BY last
        java.util.List<Object> p = new java.util.ArrayList<>();
        p.add(now);
        if (nearbyOnly) p.add(areaId);
        if (maxQuantity != null) p.add(maxQuantity);
        p.add(areaId);
        return Db.list(c, SEL + where + order, MAP, p.toArray());
    }

    /** Atomic guarded status change; returns 0 when the food was not in the expected state. */
    public int transition(Connection c, int foodId, String from, String to) throws SQLException {
        return Db.update(c, "UPDATE food SET Status = ? WHERE FoodID = ? AND Status = ?", to, foodId, from);
    }

    /** Donor edit: only the owner's food that is still Available can be changed. Returns 0 otherwise. */
    public int updateAvailable(Connection c, Food f) throws SQLException {
        return Db.update(c,
            "UPDATE food SET FoodName = ?, Category = ?, Quantity = ?, PreparationTime = ?, ExpiryTime = ?, PickupArea = ?, Location = ?, "
            + "SafetyInfo = ?, PriorityLevel = ? WHERE FoodID = ? AND UserID = ? AND Status = 'Available'",
            f.getFoodName(), f.getCategory(), f.getQuantity(), f.getPreparationTime(), f.getExpiryTime(), f.getAreaId(), f.getLocation(),
            f.getSafetyInfo(), f.getPriorityLevel(), f.getFoodId(), f.getUserId());
    }

    /** Donor delete: only the owner's food that is still Available can be removed. Returns 0 otherwise. */
    public int deleteAvailable(Connection c, int foodId, int donorId) throws SQLException {
        return Db.update(c, "DELETE FROM food WHERE FoodID = ? AND UserID = ? AND Status = 'Available'", foodId, donorId);
    }

    /** FR-04: re-derives the priority of every unfinished item from its remaining time. */
    public void refreshPriorities(Connection c, LocalDateTime now) throws SQLException {
        LocalDateTime h4 = now.plusHours(Constants.HIGH_PRIORITY_HOURS);
        LocalDateTime h12 = now.plusHours(Constants.MEDIUM_PRIORITY_HOURS);
        Db.update(c, "UPDATE food SET PriorityLevel = 'High' WHERE " + OPEN + " AND ExpiryTime < ? AND PriorityLevel <> 'High'", h4);
        Db.update(c, "UPDATE food SET PriorityLevel = 'Medium' WHERE " + OPEN
            + " AND ExpiryTime >= ? AND ExpiryTime < ? AND PriorityLevel <> 'Medium'", h4, h12);
        Db.update(c, "UPDATE food SET PriorityLevel = 'Low' WHERE " + OPEN + " AND ExpiryTime >= ? AND PriorityLevel <> 'Low'", h12);
    }

    /** FR-09: unfinished food whose expiry time has passed (read before expireOverdue so notifications can name them). */
    public List<Food> findOverdue(Connection c, LocalDateTime now) throws SQLException {
        // FOR UPDATE: two running instances serialise here, so each expiry notification is produced exactly once
        return Db.list(c, SEL + "WHERE f." + OPEN + " AND f.ExpiryTime <= ? FOR UPDATE", MAP, now);
    }

    /** FR-09: marks unfinished food whose expiry time has passed as Expired. */
    public int expireOverdue(Connection c, LocalDateTime now) throws SQLException {
        return Db.update(c, "UPDATE food SET Status = 'Expired' WHERE " + OPEN + " AND ExpiryTime <= ?", now);
    }
}
