package hsts.server.repository;

import hsts.server.security.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class StudentProfileRepository {
    private static final String IDENTITY_HASH_SQL = """
            SELECT sp.identity_number_hash
            FROM student_profiles sp
            JOIN users student
              ON student.user_id = sp.user_id
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            WHERE sp.user_id = ?
            """;

    private final DatabaseController databaseController;

    public StudentProfileRepository() {
        this(new DatabaseController());
    }

    public StudentProfileRepository(DatabaseController databaseController) {
        this.databaseController = databaseController;
    }

    public boolean matchesIdentity(int authenticatedStudentId, String identityConfirmation) {
        if (identityConfirmation == null || identityConfirmation.isBlank()) {
            return false;
        }

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(IDENTITY_HASH_SQL)) {
            statement.setInt(1, authenticatedStudentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        && PasswordHasher.matches(
                                identityConfirmation,
                                resultSet.getString("identity_number_hash")
                        );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to validate student identity", exception);
        }
    }
}
