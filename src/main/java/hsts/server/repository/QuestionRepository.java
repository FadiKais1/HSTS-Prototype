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
    private static final String QUESTION_COLUMNS = """
            question_id, content, topic, type, difficulty, status, illustration_path,
            answer_option_1, answer_option_2, answer_option_3, answer_option_4, correct_option_number
            """;

    public List<Question> findAll() {
        String sql = "SELECT " + QUESTION_COLUMNS + " FROM questions ORDER BY question_id";
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
        String sql = "SELECT " + QUESTION_COLUMNS + " FROM questions WHERE question_id = ?";

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

    public boolean updateQuestion(Question question) {
        String sql = """
                UPDATE questions
                SET content = ?, topic = ?, type = 'MULTIPLE_CHOICE', difficulty = ?, status = ?, illustration_path = ?,
                    answer_option_1 = ?, answer_option_2 = ?, answer_option_3 = ?, answer_option_4 = ?, correct_option_number = ?
                WHERE question_id = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, question.getContent());
            statement.setString(2, question.getTopic());
            statement.setString(3, question.getDifficulty());
            statement.setString(4, question.getStatus());
            statement.setString(5, question.getIllustrationPath());
            statement.setString(6, question.getAnswerOption1());
            statement.setString(7, question.getAnswerOption2());
            statement.setString(8, question.getAnswerOption3());
            statement.setString(9, question.getAnswerOption4());
            statement.setInt(10, question.getCorrectOptionNumber());
            statement.setInt(11, question.getQuestionId());

            int updatedRows = statement.executeUpdate();
            return updatedRows == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update question", e);
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
                resultSet.getString("status"),
                resultSet.getString("illustration_path"),
                resultSet.getString("answer_option_1"),
                resultSet.getString("answer_option_2"),
                resultSet.getString("answer_option_3"),
                resultSet.getString("answer_option_4"),
                resultSet.getInt("correct_option_number")
        );
    }
}
