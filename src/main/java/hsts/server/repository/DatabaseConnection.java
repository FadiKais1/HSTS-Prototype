package hsts.server.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {
    private static final String DEFAULT_DB_URL =
            "jdbc:mysql://localhost:3306/hsts_prototype?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private static final String DEFAULT_DB_USER = "root";

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        String dbUrl = System.getProperty(
                "hsts.db.url",
                System.getenv().getOrDefault("HSTS_DB_URL", DEFAULT_DB_URL)
        );

        String dbUser = System.getProperty(
                "hsts.db.user",
                System.getenv().getOrDefault("HSTS_DB_USER", DEFAULT_DB_USER)
        );

        String dbPassword = System.getProperty(
                "hsts.db.password",
                System.getenv().getOrDefault("HSTS_DB_PASSWORD", "")
        );

        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }
}