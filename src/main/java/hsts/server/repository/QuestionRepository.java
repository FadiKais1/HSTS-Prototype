package hsts.server.repository;

import hsts.server.entity.Question;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuestionRepository {

    public List<Question> findAll() {
        String sql = "SELECT question_id, content, topic, type, difficulty, status FROM questions ORDER BY question_id";
        List<Question> questions = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                questions.add(mapRowToQuestion(resultSet));
            }

            return questions;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load questions", e);
        }
    }

    public Optional<Question> findById(int questionId) {
        String sql = "SELECT question_id, content, topic, type, difficulty, status FROM questions WHERE question_id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, questionId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRowToQuestion(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load question by id", e);
        }
    }

    public boolean updateQuestionContent(int questionId, String newContent) {
        String sql = "UPDATE questions SET content = ? WHERE question_id = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, newContent);
            statement.setInt(2, questionId);

            int updatedRows = statement.executeUpdate();
            return updatedRows == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update question", e);
        }
    }

    private Question mapRowToQuestion(ResultSet resultSet) throws SQLException {
        return new Question(
                resultSet.getInt("question_id"),
                resultSet.getString("content"),
                resultSet.getString("topic"),
                resultSet.getString("type"),
                resultSet.getString("difficulty"),
                resultSet.getString("status")
        );
    }
}
