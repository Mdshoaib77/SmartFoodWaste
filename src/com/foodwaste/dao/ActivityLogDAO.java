package com.foodwaste.dao;

import com.foodwaste.model.ActivityLog;
import com.foodwaste.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Data access for the ACTIVITY_LOG audit table. */
public class ActivityLogDAO {
    private static final Db.RowMapper<ActivityLog> MAP = rs -> {
        ActivityLog l = new ActivityLog();
        l.setLogId(rs.getLong("LogID"));
        l.setActorType(rs.getString("ActorType"));
        int id = rs.getInt("ActorID");
        l.setActorId(rs.wasNull() ? null : id);
        l.setAction(rs.getString("Action"));
        l.setDetails(rs.getString("Details"));
        l.setLoggedAt(Db.ts(rs, "LoggedAt"));
        return l;
    };

    /** actorType: Admin, User or System. Joins the caller's transaction so the log commits with the change. */
    public void insert(Connection c, String actorType, Integer actorId, String action, String details) throws SQLException {
        if (details != null && details.length() > 500) details = details.substring(0, 500);
        Db.insert(c, "INSERT INTO activity_log (ActorType, ActorID, Action, Details, LoggedAt) VALUES (?,?,?,?,?)",
            actorType, actorId, action, details, LocalDateTime.now());
    }

    public List<ActivityLog> findRecent(Connection c, int limit) throws SQLException {
        return Db.list(c, "SELECT LogID, ActorType, ActorID, Action, Details, LoggedAt FROM activity_log "
            + "ORDER BY LogID DESC LIMIT ?", MAP, limit);
    }
}
