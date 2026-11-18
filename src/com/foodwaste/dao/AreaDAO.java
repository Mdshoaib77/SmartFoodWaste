package com.foodwaste.dao;

import com.foodwaste.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Data access for the standard AREAS list. */
public class AreaDAO {
    public List<String> findAll(Connection c) throws SQLException {
        return Db.list(c, "SELECT AreaName FROM areas ORDER BY AreaName", rs -> rs.getString(1));
    }

    /** Returns the official spelling of an area (matched trimmed and case-insensitively) or null when it is not in the list. */
    public String findOfficial(Connection c, String input) throws SQLException {
        return Db.one(c, "SELECT AreaName FROM areas WHERE LOWER(TRIM(AreaName)) = LOWER(TRIM(?))", rs -> rs.getString(1), input);
    }

    public int insert(Connection c, String name) throws SQLException {
        return Db.update(c, "INSERT INTO areas (AreaName) VALUES (?)", name);
    }
}
