package com.foodwaste.dao;

import com.foodwaste.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Read-only aggregate queries behind the FR-11 distribution and waste-reduction reports. */
public class ReportDAO {
    public List<String[]> usersByRoleStatus(Connection c) throws SQLException {
        return Db.table(c, "SELECT Role, Status, COUNT(*) AS Users FROM `user` GROUP BY Role, Status ORDER BY Role, Status");
    }

    public List<String[]> foodByStatus(Connection c) throws SQLException {
        return Db.table(c, "SELECT Status, COUNT(*) AS Items, COALESCE(SUM(Quantity),0) AS TotalQuantity "
            + "FROM food GROUP BY Status ORDER BY FIELD(Status,'Available','Requested','Reserved','Picked Up','Completed','Expired')");
    }

    public List<String[]> requestsByStatus(Connection c) throws SQLException {
        return Db.table(c, "SELECT Status, COUNT(*) AS Requests FROM request GROUP BY Status ORDER BY Status");
    }

    public long rescuedQuantity(Connection c) throws SQLException {
        return Db.scalar(c, "SELECT COALESCE(SUM(Quantity),0) FROM food WHERE Status IN ('Picked Up','Completed')");
    }

    public long expiredQuantity(Connection c) throws SQLException {
        return Db.scalar(c, "SELECT COALESCE(SUM(Quantity),0) FROM food WHERE Status = 'Expired'");
    }

    public long rescuedItems(Connection c) throws SQLException {
        return Db.scalar(c, "SELECT COUNT(*) FROM food WHERE Status IN ('Picked Up','Completed')");
    }

    public long expiredItems(Connection c) throws SQLException {
        return Db.scalar(c, "SELECT COUNT(*) FROM food WHERE Status = 'Expired'");
    }

    /** Outcome by priority: items and quantities rescued vs expired. */
    public List<String[]> byPriority(Connection c) throws SQLException {
        return Db.table(c, "SELECT PriorityLevel AS Priority, COUNT(*) AS Total, "
            + "COALESCE(SUM(Status IN ('Picked Up','Completed')),0) AS ItemsRescued, COALESCE(SUM(Status = 'Expired'),0) AS ItemsExpired, "
            + "COALESCE(SUM(CASE WHEN Status IN ('Picked Up','Completed') THEN Quantity ELSE 0 END),0) AS QtyRescued, "
            + "COALESCE(SUM(CASE WHEN Status = 'Expired' THEN Quantity ELSE 0 END),0) AS QtyExpired "
            + "FROM food GROUP BY PriorityLevel ORDER BY FIELD(PriorityLevel,'High','Medium','Low')");
    }

    /** Outcome by pickup area (the district where the food was collected): quantity rescued vs expired. */
    public List<String[]> distributionByArea(Connection c) throws SQLException {
        return Db.table(c, "SELECT f.PickupArea AS Area, COUNT(*) AS Items, "
            + "COALESCE(SUM(CASE WHEN f.Status IN ('Picked Up','Completed') THEN f.Quantity ELSE 0 END),0) AS QtyRescued, "
            + "COALESCE(SUM(CASE WHEN f.Status = 'Expired' THEN f.Quantity ELSE 0 END),0) AS QtyExpired "
            + "FROM food f WHERE f.Status IN ('Picked Up','Completed','Expired') "
            + "GROUP BY f.PickupArea ORDER BY QtyRescued DESC, f.PickupArea");
    }

    public List<String[]> topDonors(Connection c, int limit) throws SQLException {
        return Db.table(c, "SELECT u.Name AS Donor, u.AreaID AS Area, COUNT(*) AS Items, SUM(f.Quantity) AS QuantityDonated "
            + "FROM food f JOIN `user` u ON f.UserID = u.UserID WHERE f.Status IN ('Picked Up','Completed') "
            + "GROUP BY u.UserID, u.Name, u.AreaID ORDER BY QuantityDonated DESC LIMIT ?", limit);
    }

    public List<String[]> topReceivers(Connection c, int limit) throws SQLException {
        return Db.table(c, "SELECT u.Name AS Receiver, u.AreaID AS Area, COUNT(*) AS Pickups, SUM(f.Quantity) AS QuantityReceived "
            + "FROM request r JOIN food f ON r.FoodID = f.FoodID JOIN `user` u ON r.UserID = u.UserID "
            + "WHERE r.Status IN ('Picked Up','Completed') "
            + "GROUP BY u.UserID, u.Name, u.AreaID ORDER BY QuantityReceived DESC LIMIT ?", limit);
    }
}
