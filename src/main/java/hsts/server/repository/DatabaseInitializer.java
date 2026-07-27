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
        createSubjectsTable();
        createCoursesTable();
        createTeacherCoursesTable();
        migrateQuestionBankColumns();
        createQuestionVersionsTable();
        createAnswerOptionsTable();
        migrateQuestionBankData();
        createQuestionBankIndexesAndConstraints();
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

    private void createSubjectsTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS subjects (
                    subject_id INT AUTO_INCREMENT PRIMARY KEY,
                    subject_code VARCHAR(30) NOT NULL UNIQUE,
                    name VARCHAR(100) NOT NULL,
                    description TEXT NULL
                ) ENGINE=InnoDB
                """;

        executeSchemaStatement(sql, "Failed to create subjects table");
    }

    private void createCoursesTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS courses (
                    course_id INT AUTO_INCREMENT PRIMARY KEY,
                    subject_id INT NOT NULL,
                    course_code VARCHAR(30) NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    grade_level VARCHAR(30) NOT NULL,
                    school_year VARCHAR(20) NOT NULL,
                    CONSTRAINT uq_courses_subject_code_year
                        UNIQUE (subject_id, course_code, school_year)
                ) ENGINE=InnoDB
                """;

        executeSchemaStatement(sql, "Failed to create courses table");
    }

    private void createTeacherCoursesTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS teacher_courses (
                    teacher_user_id INT NOT NULL,
                    course_id INT NOT NULL,
                    PRIMARY KEY (teacher_user_id, course_id)
                ) ENGINE=InnoDB
                """;

        executeSchemaStatement(sql, "Failed to create teacher-course assignments table");
    }

    private void migrateQuestionBankColumns() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            addColumnIfMissing(connection, "questions", "course_id", "INT NULL");
            addColumnIfMissing(connection, "questions", "created_by_user_id", "INT NULL");
            addColumnIfMissing(connection, "questions", "current_version_no", "INT NULL");
            addColumnIfMissing(connection, "questions", "created_at", "DATETIME(6) NULL");
            addColumnIfMissing(connection, "questions", "updated_at", "DATETIME(6) NULL");
            makeQuestionIdAutoIncrement(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to add question-bank columns", e);
        }
    }

    private void createQuestionVersionsTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS question_versions (
                    question_id INT NOT NULL,
                    version_no INT NOT NULL,
                    content TEXT NOT NULL,
                    topic VARCHAR(100) NOT NULL,
                    question_type VARCHAR(30) NOT NULL,
                    difficulty VARCHAR(20) NOT NULL,
                    illustration_path TEXT NULL,
                    correct_option_number TINYINT NOT NULL,
                    created_by_user_id INT NOT NULL,
                    created_at DATETIME(6) NOT NULL,
                    PRIMARY KEY (question_id, version_no)
                ) ENGINE=InnoDB
                """;

        executeSchemaStatement(sql, "Failed to create question versions table");
    }

    private void createAnswerOptionsTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS answer_options (
                    question_id INT NOT NULL,
                    version_no INT NOT NULL,
                    option_number TINYINT NOT NULL,
                    option_text TEXT NOT NULL,
                    PRIMARY KEY (question_id, version_no, option_number)
                ) ENGINE=InnoDB
                """;

        executeSchemaStatement(sql, "Failed to create answer options table");
    }

    private void executeSchemaStatement(String sql, String errorMessage) {
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(errorMessage, e);
        }
    }

    private void createQuestionsTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS questions (
                    question_id INT AUTO_INCREMENT PRIMARY KEY,
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
        addColumnIfMissing(connection, "questions", columnName, columnType);
    }

    private void addColumnIfMissing(Connection connection, String tableName,
                                    String columnName, String columnType) throws SQLException {
        if (columnExists(connection, tableName, columnName)) {
            return;
        }

        String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType;

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private boolean columnExists(Connection connection, String columnName) throws SQLException {
        return columnExists(connection, "questions", columnName);
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        try (ResultSet resultSet = metaData.getColumns(
                connection.getCatalog(),
                null,
                tableName,
                columnName
        )) {
            return resultSet.next();
        }
    }

    private void makeQuestionIdAutoIncrement(Connection connection) throws SQLException {
        if (columnIsAutoIncrement(connection, "questions", "question_id")) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE questions MODIFY COLUMN question_id INT NOT NULL AUTO_INCREMENT"
            );
        }
    }

    private boolean columnIsAutoIncrement(Connection connection, String tableName,
                                          String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        try (ResultSet resultSet = metaData.getColumns(
                connection.getCatalog(),
                null,
                tableName,
                columnName
        )) {
            return resultSet.next()
                    && "YES".equalsIgnoreCase(resultSet.getString("IS_AUTOINCREMENT"));
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
                INSERT IGNORE INTO questions (
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

    private void migrateQuestionBankData() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean transactionStarted = false;
            Throwable migrationFailure = null;

            try {
                connection.setAutoCommit(false);
                transactionStarted = true;
                validateCompatibilityRecords(connection, false);
                insertCompatibilitySubjectAndCourse(connection);
                validateCompatibilityRecords(connection, true);
                insertCompatibilityAssignments(connection);
                backfillQuestionMetadata(connection);
                insertInitialQuestionVersions(connection);
                insertInitialAnswerOptions(connection);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                migrationFailure = e;
                if (transactionStarted) {
                    rollbackWithSuppressed(connection, e);
                }
                throw e;
            } finally {
                restoreAutoCommit(connection, originalAutoCommit, migrationFailure);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to migrate question-bank data", e);
        }
    }

    private void validateCompatibilityRecords(Connection connection,
                                              boolean requireSubjectAndCourse) throws SQLException {
        validateCompatibilityUser(
                connection,
                1002,
                "teacher@hsts.local",
                "TEACHER"
        );
        validateCompatibilityUser(
                connection,
                1003,
                "coordinator@hsts.local",
                "COORDINATOR"
        );
        validateCompatibilitySubject(connection, requireSubjectAndCourse);
        validateCompatibilityCourse(connection, requireSubjectAndCourse);
    }

    private void validateCompatibilityUser(Connection connection, int userId,
                                           String email, String role) throws SQLException {
        String sql = """
                SELECT user_id, email, role
                FROM users
                WHERE user_id = ? OR email = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setString(2, email);

            try (ResultSet resultSet = statement.executeQuery()) {
                int matches = 0;
                boolean exactMatch = false;

                while (resultSet.next()) {
                    matches++;
                    exactMatch = exactMatch || (
                            resultSet.getInt("user_id") == userId
                                    && email.equalsIgnoreCase(resultSet.getString("email"))
                                    && role.equals(resultSet.getString("role"))
                    );
                }

                if (matches != 1 || !exactMatch) {
                    throw new IllegalStateException(
                            "Compatibility user conflict for fixed identifier " + userId
                    );
                }
            }
        }
    }

    private void validateCompatibilitySubject(Connection connection,
                                              boolean required) throws SQLException {
        String sql = """
                SELECT subject_id, subject_code, name
                FROM subjects
                WHERE subject_id = 1 OR subject_code = 'LEGACY'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            int matches = 0;
            boolean exactMatch = false;

            while (resultSet.next()) {
                matches++;
                exactMatch = exactMatch || (
                        resultSet.getInt("subject_id") == 1
                                && "LEGACY".equals(resultSet.getString("subject_code"))
                                && "Legacy Prototype".equals(resultSet.getString("name"))
                );
            }

            if (matches > 1 || (matches == 1 && !exactMatch) || (required && matches != 1)) {
                throw new IllegalStateException(
                        "Compatibility subject conflict for fixed identifier 1 or code LEGACY"
                );
            }
        }
    }

    private void validateCompatibilityCourse(Connection connection,
                                             boolean required) throws SQLException {
        String sql = """
                SELECT course_id, subject_id, course_code, name, grade_level, school_year
                FROM courses
                WHERE course_id = 1
                   OR (subject_id = 1 AND course_code = 'LEGACY-101' AND school_year = '2026')
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            int matches = 0;
            boolean exactMatch = false;

            while (resultSet.next()) {
                matches++;
                exactMatch = exactMatch || (
                        resultSet.getInt("course_id") == 1
                                && resultSet.getInt("subject_id") == 1
                                && "LEGACY-101".equals(resultSet.getString("course_code"))
                                && "Legacy Prototype Course".equals(resultSet.getString("name"))
                                && "General".equals(resultSet.getString("grade_level"))
                                && "2026".equals(resultSet.getString("school_year"))
                );
            }

            if (matches > 1 || (matches == 1 && !exactMatch) || (required && matches != 1)) {
                throw new IllegalStateException(
                        "Compatibility course conflict for fixed identifier 1 or code LEGACY-101"
                );
            }
        }
    }

    private void insertCompatibilitySubjectAndCourse(Connection connection) throws SQLException {
        executeUpdate(connection, """
                INSERT IGNORE INTO subjects (
                    subject_id,
                    subject_code,
                    name,
                    description
                )
                VALUES (1, 'LEGACY', 'Legacy Prototype', NULL)
                """);

        executeUpdate(connection, """
                INSERT IGNORE INTO courses (
                    course_id,
                    subject_id,
                    course_code,
                    name,
                    grade_level,
                    school_year
                )
                VALUES (1, 1, 'LEGACY-101', 'Legacy Prototype Course', 'General', '2026')
                """);
    }

    private void insertCompatibilityAssignments(Connection connection) throws SQLException {
        executeUpdate(connection, """
                INSERT IGNORE INTO teacher_courses (teacher_user_id, course_id)
                VALUES (1002, 1), (1003, 1)
                """);
    }

    private void backfillQuestionMetadata(Connection connection) throws SQLException {
        executeUpdate(connection, "UPDATE questions SET course_id = 1 WHERE course_id IS NULL");
        executeUpdate(connection, """
                UPDATE questions
                SET created_by_user_id = 1002
                WHERE created_by_user_id IS NULL
                """);
        executeUpdate(connection, """
                UPDATE questions
                SET current_version_no = 1
                WHERE current_version_no IS NULL
                """);
        executeUpdate(connection, """
                UPDATE questions
                SET created_at = CURRENT_TIMESTAMP(6)
                WHERE created_at IS NULL
                """);
        executeUpdate(connection, """
                UPDATE questions
                SET updated_at = CURRENT_TIMESTAMP(6)
                WHERE updated_at IS NULL
                """);
    }

    private void insertInitialQuestionVersions(Connection connection) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO question_versions (
                    question_id,
                    version_no,
                    content,
                    topic,
                    question_type,
                    difficulty,
                    illustration_path,
                    correct_option_number,
                    created_by_user_id,
                    created_at
                )
                SELECT
                    q.question_id,
                    1,
                    q.content,
                    COALESCE(NULLIF(q.topic, ''), 'General'),
                    COALESCE(NULLIF(q.type, ''), 'MULTIPLE_CHOICE'),
                    CASE
                        WHEN q.difficulty IN ('EASY', 'MEDIUM', 'HARD') THEN q.difficulty
                        ELSE 'EASY'
                    END,
                    q.illustration_path,
                    CASE
                        WHEN q.correct_option_number BETWEEN 1 AND 4 THEN q.correct_option_number
                        ELSE 1
                    END,
                    COALESCE(q.created_by_user_id, 1002),
                    COALESCE(q.created_at, CURRENT_TIMESTAMP(6))
                FROM questions q
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM question_versions existing_version
                    WHERE existing_version.question_id = q.question_id
                      AND existing_version.version_no = 1
                )
                """);
    }

    private void insertInitialAnswerOptions(Connection connection) throws SQLException {
        executeUpdate(connection, """
                INSERT INTO answer_options (
                    question_id,
                    version_no,
                    option_number,
                    option_text
                )
                SELECT
                    q.question_id,
                    1,
                    option_numbers.option_number,
                    CASE option_numbers.option_number
                        WHEN 1 THEN COALESCE(q.answer_option_1, '')
                        WHEN 2 THEN COALESCE(q.answer_option_2, '')
                        WHEN 3 THEN COALESCE(q.answer_option_3, '')
                        WHEN 4 THEN COALESCE(q.answer_option_4, '')
                    END
                FROM questions q
                CROSS JOIN (
                    SELECT 1 AS option_number
                    UNION ALL SELECT 2
                    UNION ALL SELECT 3
                    UNION ALL SELECT 4
                ) option_numbers
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM answer_options existing_option
                    WHERE existing_option.question_id = q.question_id
                      AND existing_option.version_no = 1
                      AND existing_option.option_number = option_numbers.option_number
                )
                """);
    }

    private void executeUpdate(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private void rollbackWithSuppressed(Connection connection, Throwable originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }

    private void restoreAutoCommit(Connection connection, boolean originalAutoCommit,
                                   Throwable originalException) throws SQLException {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException restorationException) {
            if (originalException != null) {
                originalException.addSuppressed(restorationException);
                return;
            }
            throw restorationException;
        }
    }

    private void createQuestionBankIndexesAndConstraints() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            makeCurrentVersionRequired(connection);

            ensureConstraint(connection, "courses", "uq_courses_subject_code_year", """
                    ALTER TABLE courses
                    ADD CONSTRAINT uq_courses_subject_code_year
                    UNIQUE (subject_id, course_code, school_year)
                    """);

            ensureIndex(connection, "teacher_courses", "idx_teacher_courses_course_id",
                    "CREATE INDEX idx_teacher_courses_course_id ON teacher_courses (course_id)");
            ensureIndex(connection, "questions", "idx_questions_course_id",
                    "CREATE INDEX idx_questions_course_id ON questions (course_id)");
            ensureIndex(connection, "questions", "idx_questions_created_by_user_id", """
                    CREATE INDEX idx_questions_created_by_user_id
                    ON questions (created_by_user_id)
                    """);
            ensureIndex(connection, "question_versions", "idx_question_versions_created_by_user_id", """
                    CREATE INDEX idx_question_versions_created_by_user_id
                    ON question_versions (created_by_user_id)
                    """);

            ensureConstraint(connection, "courses", "fk_courses_subject", """
                    ALTER TABLE courses
                    ADD CONSTRAINT fk_courses_subject
                    FOREIGN KEY (subject_id) REFERENCES subjects (subject_id)
                    """);
            ensureConstraint(connection, "teacher_courses", "fk_teacher_courses_user", """
                    ALTER TABLE teacher_courses
                    ADD CONSTRAINT fk_teacher_courses_user
                    FOREIGN KEY (teacher_user_id) REFERENCES users (user_id)
                    """);
            ensureConstraint(connection, "teacher_courses", "fk_teacher_courses_course", """
                    ALTER TABLE teacher_courses
                    ADD CONSTRAINT fk_teacher_courses_course
                    FOREIGN KEY (course_id) REFERENCES courses (course_id)
                    """);
            ensureConstraint(connection, "questions", "fk_questions_course", """
                    ALTER TABLE questions
                    ADD CONSTRAINT fk_questions_course
                    FOREIGN KEY (course_id) REFERENCES courses (course_id)
                    """);
            ensureConstraint(connection, "questions", "fk_questions_creator", """
                    ALTER TABLE questions
                    ADD CONSTRAINT fk_questions_creator
                    FOREIGN KEY (created_by_user_id) REFERENCES users (user_id)
                    """);
            ensureConstraint(connection, "question_versions", "fk_question_versions_question", """
                    ALTER TABLE question_versions
                    ADD CONSTRAINT fk_question_versions_question
                    FOREIGN KEY (question_id) REFERENCES questions (question_id)
                    """);
            ensureConstraint(connection, "question_versions", "fk_question_versions_creator", """
                    ALTER TABLE question_versions
                    ADD CONSTRAINT fk_question_versions_creator
                    FOREIGN KEY (created_by_user_id) REFERENCES users (user_id)
                    """);
            ensureConstraint(connection, "answer_options", "fk_answer_options_version", """
                    ALTER TABLE answer_options
                    ADD CONSTRAINT fk_answer_options_version
                    FOREIGN KEY (question_id, version_no)
                    REFERENCES question_versions (question_id, version_no)
                    """);

            ensureConstraint(connection, "question_versions", "chk_question_versions_difficulty", """
                    ALTER TABLE question_versions
                    ADD CONSTRAINT chk_question_versions_difficulty
                    CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD'))
                    """);
            ensureConstraint(connection, "question_versions", "chk_question_versions_correct_option", """
                    ALTER TABLE question_versions
                    ADD CONSTRAINT chk_question_versions_correct_option
                    CHECK (correct_option_number BETWEEN 1 AND 4)
                    """);
            ensureConstraint(connection, "answer_options", "chk_answer_options_option_number", """
                    ALTER TABLE answer_options
                    ADD CONSTRAINT chk_answer_options_option_number
                    CHECK (option_number BETWEEN 1 AND 4)
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to add question-bank constraints", e);
        }
    }

    private void makeCurrentVersionRequired(Connection connection) throws SQLException {
        if (currentVersionDefinitionIsRequired(connection)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE questions
                    MODIFY COLUMN current_version_no INT NOT NULL DEFAULT 1
                    """);
        }
    }

    private boolean currentVersionDefinitionIsRequired(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        try (ResultSet resultSet = metaData.getColumns(
                connection.getCatalog(),
                null,
                "questions",
                "current_version_no"
        )) {
            if (!resultSet.next()) {
                return false;
            }

            String defaultValue = resultSet.getString("COLUMN_DEF");
            return resultSet.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls
                    && defaultValue != null
                    && "1".equals(defaultValue.replace("'", ""));
        }
    }

    private void ensureIndex(Connection connection, String tableName,
                             String indexName, String createSql) throws SQLException {
        if (indexExists(connection, tableName, indexName)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(createSql);
        }
    }

    private boolean indexExists(Connection connection, String tableName,
                                String indexName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        try (ResultSet resultSet = metaData.getIndexInfo(
                connection.getCatalog(),
                null,
                tableName,
                false,
                false
        )) {
            while (resultSet.next()) {
                if (indexName.equalsIgnoreCase(resultSet.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void ensureConstraint(Connection connection, String tableName,
                                  String constraintName, String createSql) throws SQLException {
        if (constraintExists(connection, tableName, constraintName)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(createSql);
        }
    }

    private boolean constraintExists(Connection connection, String tableName,
                                     String constraintName) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND CONSTRAINT_NAME = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
