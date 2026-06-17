package hsts.server.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {
    private static final String DEFAULT_DB_PATH = "data/hsts_prototype.db";

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        String dbPath = System.getProperty("hsts.db.path", DEFAULT_DB_PATH);
        ensureParentDirectoryExists(dbPath);
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private static void ensureParentDirectoryExists(String dbPath) {
        try {
            Path parent = Path.of(dbPath).getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not create database directory", e);
        }
    }
}
