package hsts.server.repository;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseController {
    public Connection getConnection() throws SQLException {
        return DatabaseConnection.getConnection();
    }
}