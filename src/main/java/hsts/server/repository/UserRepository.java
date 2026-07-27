package hsts.server.repository;

import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.Coordinator;
import hsts.server.entity.Principal;
import hsts.server.entity.Student;
import hsts.server.entity.Teacher;
import hsts.server.entity.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

public class UserRepository {
    private static final String USER_COLUMNS =
            "user_id, full_name, email, password_hash, role, status";

    private final DatabaseController databaseController;

    public UserRepository() {
        this(new DatabaseController());
    }

    public UserRepository(DatabaseController databaseController) {
        this.databaseController = databaseController;
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE email = ?";

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, normalizedEmail);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRowToUser(resultSet));
                }

                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load user by email", e);
        }
    }

    public Optional<User> findById(int userId) {
        String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE user_id = ?";

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRowToUser(resultSet));
                }

                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load user by id", e);
        }
    }

    public boolean updateStatus(int userId, UserStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status must not be null");
        }

        String sql = "UPDATE users SET status = ? WHERE user_id = ?";

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, status.name());
            statement.setInt(2, userId);

            return statement.executeUpdate() == 1;

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update user status", e);
        }
    }

    private User mapRowToUser(ResultSet resultSet) throws SQLException {
        int userId = resultSet.getInt("user_id");
        String fullName = resultSet.getString("full_name");
        String email = resultSet.getString("email");
        String passwordHash = resultSet.getString("password_hash");
        UserRole role = UserRole.valueOf(resultSet.getString("role"));
        UserStatus status = UserStatus.valueOf(resultSet.getString("status"));

        return switch (role) {
            case STUDENT -> Student.rehydrate(
                    userId, fullName, email, passwordHash, status
            );
            case TEACHER -> Teacher.rehydrate(
                    userId, fullName, email, passwordHash, status
            );
            case COORDINATOR -> Coordinator.rehydrate(
                    userId, fullName, email, passwordHash, status
            );
            case PRINCIPAL -> Principal.rehydrate(
                    userId, fullName, email, passwordHash, status
            );
        };
    }
}
