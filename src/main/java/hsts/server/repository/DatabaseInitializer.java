package hsts.server.repository;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {

    public void initialize() {
        createQuestionsTable();
        migrateQuestionsTable();
        seedQuestionsIfEmpty();
        normalizeExistingQuestions();
        createUsersTable();
        seedUsers();
    }

    private void createUsersTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS users (
                    user_id INT PRIMARY KEY,
                    full_name VARCHAR(100) NOT NULL,
                    email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    role VARCHAR(20) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    CONSTRAINT chk_users_role CHECK (role IN ('STUDENT', 'TEACHER', 'COORDINATOR', 'PRINCIPAL')),
                    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
                )
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute(sql);

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create users table", e);
        }
    }

    private void seedUsers() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            insertUser(connection, 1001, "Development Student", "student@hsts.local",
                    "pbkdf2-sha256$210000$s1m+ODv+3/GRzPDxDWDQIQ==$UfEw3AfJ9R+QhlHzQZizsg+AkTubQtLzfpWhIVGxAxw=",
                    "STUDENT", "ACTIVE");
            insertUser(connection, 1002, "Development Teacher", "teacher@hsts.local",
                    "pbkdf2-sha256$210000$GYtRfi/rORWe6PUx0pZRHQ==$aNGS1rjaQfcQ7QiCVmcJlqh1FuBsStSykbPeq8NXE98=",
                    "TEACHER", "ACTIVE");
            insertUser(connection, 1003, "Development Coordinator", "coordinator@hsts.local",
                    "pbkdf2-sha256$210000$z2td5VTlfecXVbzKsSNoNg==$6IRrphVP1zQ7OPFzc75VefR6BoE5DuS+7sAkuketFts=",
                    "COORDINATOR", "ACTIVE");
            insertUser(connection, 1004, "Development Principal", "principal@hsts.local",
                    "pbkdf2-sha256$210000$A9ZKGvD4BYtl7WgvJLOp7Q==$AN9mwp3khV/y69zCc8C539V4DVrtqEcdJ3TlolRQW10=",
                    "PRINCIPAL", "ACTIVE");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed users table", e);
        }
    }

    private void insertUser(Connection connection, int userId, String fullName, String email,
                            String passwordHash, String role, String status) throws SQLException {
        String sql = """
                INSERT IGNORE INTO users (
                    user_id,
                    full_name,
                    email,
                    password_hash,
                    role,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setString(2, fullName);
            statement.setString(3, email);
            statement.setString(4, passwordHash);
            statement.setString(5, role);
            statement.setString(6, status);

            statement.executeUpdate();
        }
    }

    private void createQuestionsTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS questions (
                    question_id INT PRIMARY KEY,
                    content TEXT NOT NULL,
                    topic VARCHAR(100),
                    type VARCHAR(50),
                    difficulty VARCHAR(50),
                    status VARCHAR(50),
                    illustration_path TEXT,
                    answer_option_1 TEXT,
                    answer_option_2 TEXT,
                    answer_option_3 TEXT,
                    answer_option_4 TEXT,
                    correct_option_number INT
                )
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute(sql);

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create questions table", e);
        }
    }

    private void migrateQuestionsTable() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            addColumnIfMissing(connection, "illustration_path", "TEXT");
            addColumnIfMissing(connection, "answer_option_1", "TEXT");
            addColumnIfMissing(connection, "answer_option_2", "TEXT");
            addColumnIfMissing(connection, "answer_option_3", "TEXT");
            addColumnIfMissing(connection, "answer_option_4", "TEXT");
            addColumnIfMissing(connection, "correct_option_number", "INT");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to migrate questions table", e);
        }
    }

    private void addColumnIfMissing(Connection connection, String columnName, String columnType) throws SQLException {
        if (columnExists(connection, columnName)) {
            return;
        }

        String sql = "ALTER TABLE questions ADD COLUMN " + columnName + " " + columnType;

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private boolean columnExists(Connection connection, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        try (ResultSet resultSet = metaData.getColumns(
                connection.getCatalog(),
                null,
                "questions",
                columnName
        )) {
            return resultSet.next();
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

            insertQuestion(connection, 1, "What is 2 + 2?", "Algebra", "EASY", "ACTIVE", "", "3", "4", "5", "6", 2);
            insertQuestion(connection, 2, "Solve: 5x = 20", "Algebra", "EASY", "ACTIVE", "", "x = 2", "x = 4", "x = 5", "x = 20", 2);
            insertQuestion(connection, 3, "What is the derivative of x^2?", "Calculus", "MEDIUM", "ACTIVE", "", "x", "2x", "x^2", "2", 2);
            insertQuestion(connection, 4, "What is the capital of France?", "General", "EASY", "ACTIVE", "", "Rome", "Paris", "Madrid", "Berlin", 2);
            insertQuestion(connection, 5, "Which OOP concept allows the same method name to behave differently?", "Programming", "MEDIUM", "ACTIVE", "", "Encapsulation", "Inheritance", "Polymorphism", "Compilation", 3);
            insertQuestion(connection, 6, "What is a primary key in a database?", "Databases", "EASY", "ACTIVE", "", "A unique identifier", "A duplicated field", "A table name", "A query result", 1);

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed questions table", e);
        }
    }

    private void insertQuestion(Connection connection, int id, String content, String topic, String difficulty,
                                String status, String illustrationPath, String option1, String option2,
                                String option3, String option4, int correctOptionNumber) throws SQLException {
        String sql = """
                INSERT INTO questions (
                    question_id,
                    content,
                    topic,
                    type,
                    difficulty,
                    status,
                    illustration_path,
                    answer_option_1,
                    answer_option_2,
                    answer_option_3,
                    answer_option_4,
                    correct_option_number
                )
                VALUES (?, ?, ?, 'MULTIPLE_CHOICE', ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2, content);
            statement.setString(3, topic);
            statement.setString(4, difficulty);
            statement.setString(5, status);
            statement.setString(6, illustrationPath);
            statement.setString(7, option1);
            statement.setString(8, option2);
            statement.setString(9, option3);
            statement.setString(10, option4);
            statement.setInt(11, correctOptionNumber);

            statement.executeUpdate();
        }
    }

    private void normalizeExistingQuestions() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            updateBaseColumns(connection);

            applyDefaultOptions(connection, 1, "3", "4", "5", "6", 2);
            applyDefaultOptions(connection, 2, "x = 2", "x = 4", "x = 5", "x = 20", 2);
            applyDefaultOptions(connection, 3, "x", "2x", "x^2", "2", 2);
            applyDefaultOptions(connection, 4, "Rome", "Paris", "Madrid", "Berlin", 2);
            applyDefaultOptions(connection, 5, "Encapsulation", "Inheritance", "Polymorphism", "Compilation", 3);
            applyDefaultOptions(connection, 6, "A unique identifier", "A duplicated field", "A table name", "A query result", 1);

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to normalize existing questions", e);
        }
    }

    private void updateBaseColumns(Connection connection) throws SQLException {
        String sql = """
                UPDATE questions
                SET type = 'MULTIPLE_CHOICE',
                    status = COALESCE(NULLIF(status, ''), 'ACTIVE'),
                    difficulty = COALESCE(NULLIF(difficulty, ''), 'EASY'),
                    topic = COALESCE(NULLIF(topic, ''), 'General'),
                    illustration_path = COALESCE(illustration_path, ''),
                    correct_option_number = CASE
                        WHEN correct_option_number BETWEEN 1 AND 4 THEN correct_option_number
                        ELSE 1
                    END
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private void applyDefaultOptions(Connection connection, int id, String option1, String option2,
                                     String option3, String option4, int correctOptionNumber) throws SQLException {
        String sql = """
                UPDATE questions
                SET answer_option_1 = CASE
                        WHEN answer_option_1 IS NULL OR answer_option_1 = '' THEN ?
                        ELSE answer_option_1
                    END,
                    answer_option_2 = CASE
                        WHEN answer_option_2 IS NULL OR answer_option_2 = '' THEN ?
                        ELSE answer_option_2
                    END,
                    answer_option_3 = CASE
                        WHEN answer_option_3 IS NULL OR answer_option_3 = '' THEN ?
                        ELSE answer_option_3
                    END,
                    answer_option_4 = CASE
                        WHEN answer_option_4 IS NULL OR answer_option_4 = '' THEN ?
                        ELSE answer_option_4
                    END,
                    correct_option_number = CASE
                        WHEN correct_option_number IS NULL OR correct_option_number NOT BETWEEN 1 AND 4 THEN ?
                        ELSE correct_option_number
                    END
                WHERE question_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, option1);
            statement.setString(2, option2);
            statement.setString(3, option3);
            statement.setString(4, option4);
            statement.setInt(5, correctOptionNumber);
            statement.setInt(6, id);

            statement.executeUpdate();
        }
    }
}
