package hsts.server.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class StudentEnrollmentRepository {
    private static final String ENROLLMENT_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM student_courses sc
                JOIN users student
                  ON student.user_id = sc.student_user_id
                 AND student.role = 'STUDENT'
                 AND student.status = 'ACTIVE'
                WHERE sc.student_user_id = ?
                  AND sc.course_id = ?
            ) AS enrolled
            """;

    private final DatabaseController databaseController;

    public StudentEnrollmentRepository() {
        this(new DatabaseController());
    }

    public StudentEnrollmentRepository(DatabaseController databaseController) {
        this.databaseController = databaseController;
    }

    public boolean isEnrolled(int authenticatedStudentId, int courseId) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(ENROLLMENT_SQL)) {
            statement.setInt(1, authenticatedStudentId);
            statement.setInt(2, courseId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt("enrolled") == 1;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to check student enrollment", exception);
        }
    }
}
