package hsts.server.repository;

import hsts.common.CourseSummaryDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CourseRepository {
    private final DatabaseController databaseController;

    public CourseRepository() {
        this(new DatabaseController());
    }

    public CourseRepository(DatabaseController databaseController) {
        this.databaseController = databaseController;
    }

    public List<CourseSummaryDTO> findAssignedToTeacher(int userId) {
        String sql = """
                SELECT c.course_id,
                       c.subject_id,
                       c.course_code,
                       c.name AS course_name,
                       s.name AS subject_name,
                       c.grade_level,
                       c.school_year
                FROM teacher_courses tc
                JOIN courses c ON c.course_id = tc.course_id
                JOIN subjects s ON s.subject_id = c.subject_id
                WHERE tc.teacher_user_id = ?
                ORDER BY s.name, c.name, c.course_id
                """;
        List<CourseSummaryDTO> courses = new ArrayList<>();

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    courses.add(mapRowToCourseSummary(resultSet));
                }
            }

            return courses;

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load assigned courses", e);
        }
    }

    public boolean isAssignedToTeacher(int userId, int courseId) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM teacher_courses
                    WHERE teacher_user_id = ? AND course_id = ?
                ) AS assigned
                """;

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, userId);
            statement.setInt(2, courseId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean("assigned");
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to verify course assignment", e);
        }
    }

    public Optional<CourseSummaryDTO> findById(int courseId) {
        String sql = """
                SELECT c.course_id,
                       c.subject_id,
                       c.course_code,
                       c.name AS course_name,
                       s.name AS subject_name,
                       c.grade_level,
                       c.school_year
                FROM courses c
                JOIN subjects s ON s.subject_id = c.subject_id
                WHERE c.course_id = ?
                """;

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, courseId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                CourseSummaryDTO course = mapRowToCourseSummary(resultSet);
                if (resultSet.next()) {
                    throw new IllegalStateException("Duplicate course: " + courseId);
                }
                return Optional.of(course);
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load course", e);
        }
    }

    private CourseSummaryDTO mapRowToCourseSummary(ResultSet resultSet) throws SQLException {
        return new CourseSummaryDTO(
                resultSet.getInt("course_id"),
                resultSet.getInt("subject_id"),
                resultSet.getString("course_code"),
                resultSet.getString("course_name"),
                resultSet.getString("subject_name"),
                resultSet.getString("grade_level"),
                resultSet.getString("school_year")
        );
    }
}
