package hsts.server.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {

    public void initialize() {
        createQuestionsTable();
        seedQuestionsIfEmpty();
    }

    private void createQuestionsTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS questions (
                    question_id INTEGER PRIMARY KEY,
                    content TEXT NOT NULL,
                    topic TEXT,
                    type TEXT,
                    difficulty TEXT,
                    status TEXT
                )
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create questions table", e);
        }
    }

    private void seedQuestionsIfEmpty() {
        String countSql = "SELECT COUNT(*) FROM questions";

        try (Connection connection = DatabaseConnection.getConnection();
             Statement countStatement = connection.createStatement();
             ResultSet resultSet = countStatement.executeQuery(countSql)) {

            int count = resultSet.next() ? resultSet.getInt(1) : 0;
            if (count > 0) {
                return;
            }

            insertQuestion(connection, 1, "What is 2 + 2?", "Algebra", "MULTIPLE_CHOICE", "EASY", "ACTIVE");
            insertQuestion(connection, 2, "Solve: 5x = 20", "Algebra", "OPEN", "EASY", "ACTIVE");
            insertQuestion(connection, 3, "What is the derivative of x^2?", "Calculus", "OPEN", "MEDIUM", "ACTIVE");
            insertQuestion(connection, 4, "What is the capital of France?", "General", "MULTIPLE_CHOICE", "EASY", "ACTIVE");
            insertQuestion(connection, 5, "Explain polymorphism in OOP.", "Programming", "OPEN", "MEDIUM", "ACTIVE");
            insertQuestion(connection, 6, "What is a primary key in a database?", "Databases", "OPEN", "EASY", "ACTIVE");

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed questions table", e);
        }
    }

    private void insertQuestion(Connection connection, int id, String content, String topic, String type,
                                String difficulty, String status) throws SQLException {
        String sql = """
                INSERT INTO questions (question_id, content, topic, type, difficulty, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2, content);
            statement.setString(3, topic);
            statement.setString(4, type);
            statement.setString(5, difficulty);
            statement.setString(6, status);
            statement.executeUpdate();
        }
    }
}
