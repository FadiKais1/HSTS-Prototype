package hsts.server.repository;

import hsts.server.security.PasswordHasher;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        createQuestionVersionIllustrationsTable();
        migrateQuestionBankData();
        createQuestionBankIndexesAndConstraints();
        migrateCourseBotSchema();
        migrateExamSchema();
        migrateExecutionSchema();
        migrateEncodedIdentifiers();
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
                ) ENGINE=InnoDB
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

    private void createQuestionVersionIllustrationsTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS question_version_illustrations (
                    question_id INT NOT NULL,
                    version_no INT NOT NULL,
                    media_type VARCHAR(30) NOT NULL,
                    content_bytes MEDIUMBLOB NOT NULL,
                    byte_length INT NOT NULL,
                    width_pixels INT NOT NULL,
                    height_pixels INT NOT NULL,
                    content_sha256 CHAR(64) NOT NULL,
                    created_at DATETIME(6) NOT NULL,
                    PRIMARY KEY (question_id, version_no),
                    CONSTRAINT fk_question_version_illustrations_version
                        FOREIGN KEY (question_id, version_no)
                        REFERENCES question_versions (question_id, version_no)
                        ON DELETE RESTRICT,
                    CONSTRAINT chk_question_version_illustrations_media_type
                        CHECK (media_type IN ('image/png', 'image/jpeg')),
                    CONSTRAINT chk_question_version_illustrations_byte_length
                        CHECK (byte_length BETWEEN 1 AND 2097152
                               AND byte_length = OCTET_LENGTH(content_bytes)),
                    CONSTRAINT chk_question_version_illustrations_width
                        CHECK (width_pixels BETWEEN 1 AND 4096),
                    CONSTRAINT chk_question_version_illustrations_height
                        CHECK (height_pixels BETWEEN 1 AND 4096),
                    CONSTRAINT chk_question_version_illustrations_sha256
                        CHECK (content_sha256 REGEXP '^[0-9a-f]{64}$')
                ) ENGINE=InnoDB
                """;
        executeSchemaStatement(
                sql, "Failed to migrate question illustration schema"
        );
    }

    private void migrateCourseBotSchema() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            createCourseBotsTable(connection);
            createBotSourcesTable(connection);
            createBotConversationsTable(connection);
            createBotMessagesTable(connection);
            repairCourseBotParentTimestamps(connection);
        } catch (SQLException | RuntimeException e) {
            throw new IllegalStateException("Failed to migrate course Bot schema", e);
        }
    }

    private void repairCourseBotParentTimestamps(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        Throwable repairFailure = null;
        try {
            connection.setAutoCommit(false);
            executeUpdate(connection, """
                    UPDATE course_bots cb
                    JOIN (
                        SELECT source_events.bot_id,
                               MAX(source_events.event_at) AS latest_source_at
                        FROM (
                            SELECT bot_id, created_at AS event_at
                            FROM bot_sources
                            UNION ALL
                            SELECT bot_id, removed_at AS event_at
                            FROM bot_sources
                            WHERE removed_at IS NOT NULL
                        ) source_events
                        GROUP BY source_events.bot_id
                    ) latest ON latest.bot_id = cb.bot_id
                    SET cb.updated_at = latest.latest_source_at
                    WHERE cb.updated_at < latest.latest_source_at
                    """);
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            repairFailure = exception;
            rollbackWithSuppressed(connection, exception);
            throw exception;
        } finally {
            restoreAutoCommit(connection, originalAutoCommit, repairFailure);
        }
    }

    private void createCourseBotsTable(Connection connection) throws SQLException {
        executeSchemaStatement(connection, """
                CREATE TABLE IF NOT EXISTS course_bots (
                    bot_id INT NOT NULL AUTO_INCREMENT,
                    course_id INT NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    created_by_user_id INT NOT NULL,
                    external_provider VARCHAR(100) NULL,
                    external_bot_id VARCHAR(255) NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                        ON UPDATE CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (bot_id),
                    CONSTRAINT uq_course_bots_course UNIQUE (course_id),
                    KEY idx_course_bots_creator (created_by_user_id),
                    CONSTRAINT fk_course_bots_course
                        FOREIGN KEY (course_id) REFERENCES courses (course_id) ON DELETE RESTRICT,
                    CONSTRAINT fk_course_bots_creator
                        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id) ON DELETE RESTRICT,
                    CONSTRAINT chk_course_bots_name
                        CHECK (CHAR_LENGTH(TRIM(name)) > 0),
                    CONSTRAINT chk_course_bots_status
                        CHECK (status IN ('ACTIVE', 'INACTIVE'))
                ) ENGINE=InnoDB
                """);
    }

    private void createBotSourcesTable(Connection connection) throws SQLException {
        executeSchemaStatement(connection, """
                CREATE TABLE IF NOT EXISTS bot_sources (
                    source_id INT NOT NULL AUTO_INCREMENT,
                    bot_id INT NOT NULL,
                    source_type VARCHAR(30) NOT NULL,
                    display_name VARCHAR(255) NOT NULL,
                    extracted_text MEDIUMTEXT NOT NULL,
                    content_sha256 CHAR(64) NOT NULL,
                    question_id INT NULL,
                    question_version_no INT NULL,
                    added_by_user_id INT NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    external_source_id VARCHAR(255) NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    removed_at DATETIME(6) NULL,
                    active_content_sha256 CHAR(64)
                        GENERATED ALWAYS AS (
                            CASE WHEN status = 'ACTIVE' THEN content_sha256 ELSE NULL END
                        ) STORED,
                    PRIMARY KEY (source_id),
                    CONSTRAINT uq_bot_sources_active_checksum
                        UNIQUE (bot_id, active_content_sha256),
                    KEY idx_bot_sources_bot_status (bot_id, status),
                    KEY idx_bot_sources_added_by_user (added_by_user_id),
                    KEY idx_bot_sources_question_version (question_id, question_version_no),
                    CONSTRAINT fk_bot_sources_bot
                        FOREIGN KEY (bot_id) REFERENCES course_bots (bot_id) ON DELETE RESTRICT,
                    CONSTRAINT fk_bot_sources_added_by_user
                        FOREIGN KEY (added_by_user_id) REFERENCES users (user_id) ON DELETE RESTRICT,
                    CONSTRAINT fk_bot_sources_question_version
                        FOREIGN KEY (question_id, question_version_no)
                        REFERENCES question_versions (question_id, version_no),
                    CONSTRAINT chk_bot_sources_type
                        CHECK (source_type IN ('QUESTION_BANK', 'FREE_TEXT', 'TXT', 'PDF', 'DOCX')),
                    CONSTRAINT chk_bot_sources_status
                        CHECK (status IN ('ACTIVE', 'REMOVED')),
                    CONSTRAINT chk_bot_sources_checksum
                        CHECK (content_sha256 REGEXP '^[0-9a-f]{64}$'),
                    CONSTRAINT chk_bot_sources_question_reference
                        CHECK (
                            (source_type = 'QUESTION_BANK'
                                AND question_id IS NOT NULL
                                AND question_version_no IS NOT NULL)
                            OR
                            (source_type <> 'QUESTION_BANK'
                                AND question_id IS NULL
                                AND question_version_no IS NULL)
                        ),
                    CONSTRAINT chk_bot_sources_removal_state
                        CHECK (
                            (status = 'ACTIVE' AND removed_at IS NULL)
                            OR (status = 'REMOVED' AND removed_at IS NOT NULL)
                        ),
                    CONSTRAINT chk_bot_sources_removed_at
                        CHECK (removed_at IS NULL OR removed_at >= created_at),
                    CONSTRAINT chk_bot_sources_display_name
                        CHECK (CHAR_LENGTH(TRIM(display_name)) > 0),
                    CONSTRAINT chk_bot_sources_extracted_text
                        CHECK (CHAR_LENGTH(TRIM(extracted_text)) > 0)
                ) ENGINE=InnoDB
                """);
    }

    private void createBotConversationsTable(Connection connection) throws SQLException {
        executeSchemaStatement(connection, """
                CREATE TABLE IF NOT EXISTS bot_conversations (
                    conversation_id INT NOT NULL AUTO_INCREMENT,
                    bot_id INT NOT NULL,
                    student_user_id INT NOT NULL,
                    provider_subject_id CHAR(36) NOT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                        ON UPDATE CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (conversation_id),
                    CONSTRAINT uq_bot_conversations_bot_student
                        UNIQUE (bot_id, student_user_id),
                    CONSTRAINT uq_bot_conversations_provider_subject
                        UNIQUE (provider_subject_id),
                    KEY idx_bot_conversations_student (student_user_id),
                    KEY idx_bot_conversations_bot_updated (bot_id, updated_at),
                    CONSTRAINT fk_bot_conversations_bot
                        FOREIGN KEY (bot_id) REFERENCES course_bots (bot_id) ON DELETE RESTRICT,
                    CONSTRAINT fk_bot_conversations_student
                        FOREIGN KEY (student_user_id) REFERENCES users (user_id) ON DELETE RESTRICT,
                    CONSTRAINT chk_bot_conversations_provider_subject
                        CHECK (provider_subject_id REGEXP
                            '^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$')
                ) ENGINE=InnoDB
                """);
    }

    private void createBotMessagesTable(Connection connection) throws SQLException {
        executeSchemaStatement(connection, """
                CREATE TABLE IF NOT EXISTS bot_messages (
                    message_id INT NOT NULL AUTO_INCREMENT,
                    conversation_id INT NOT NULL,
                    sequence_no INT NOT NULL,
                    question_text TEXT NOT NULL,
                    normalized_question VARCHAR(1000) NOT NULL,
                    answer_text MEDIUMTEXT NOT NULL,
                    answer_status VARCHAR(30) NOT NULL,
                    provider_request_id VARCHAR(255) NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (message_id),
                    CONSTRAINT uq_bot_messages_conversation_sequence
                        UNIQUE (conversation_id, sequence_no),
                    KEY idx_bot_messages_conversation_created (conversation_id, created_at),
                    KEY idx_bot_messages_normalized_question (normalized_question(191)),
                    KEY idx_bot_messages_provider_request (provider_request_id),
                    CONSTRAINT fk_bot_messages_conversation
                        FOREIGN KEY (conversation_id)
                        REFERENCES bot_conversations (conversation_id) ON DELETE RESTRICT,
                    CONSTRAINT chk_bot_messages_sequence
                        CHECK (sequence_no > 0),
                    CONSTRAINT chk_bot_messages_answer_status
                        CHECK (answer_status IN ('ANSWERED', 'NO_SUITABLE_ANSWER')),
                    CONSTRAINT chk_bot_messages_question
                        CHECK (CHAR_LENGTH(TRIM(question_text)) > 0),
                    CONSTRAINT chk_bot_messages_normalized_question
                        CHECK (CHAR_LENGTH(TRIM(normalized_question)) > 0),
                    CONSTRAINT chk_bot_messages_answer
                        CHECK (
                            answer_status = 'NO_SUITABLE_ANSWER'
                            OR CHAR_LENGTH(TRIM(answer_text)) > 0
                        )
                ) ENGINE=InnoDB
                """);
    }

    private void executeSchemaStatement(String sql, String errorMessage) {
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(errorMessage, e);
        }
    }

    private void migrateExamSchema() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            createSubjectCoordinatorsTable(connection);
            createExamsTable(connection);
            createExamVersionsTable(connection);
            createExamVersionQuestionsTable(connection);
            createExamSchemaIndexesAndConstraints(connection);
            insertCompatibilityCoordinatorAssignment(connection);
        } catch (SQLException | RuntimeException e) {
            throw new IllegalStateException("Failed to migrate exam schema", e);
        }
    }

    private void migrateExecutionSchema() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            createStudentProfilesTable(connection);
            createStudentCoursesTable(connection);
            createExamExecutionsTable(connection);
            createExamSubmissionsTable(connection);
            createStudentAnswersTable(connection);
            createSubmissionTimeExtensionsTable(connection);
            createExecutionTimeExtensionsTable(connection);
            createNotificationsTable(connection);
            ensureNotificationTypeConstraint(connection);
            createExamExecutionDecilesTable(connection);
            migrateExecutionSchemaColumns(connection);
            backfillExecutionUpdatedAt(connection);
            normalizeExecutionUpdatedAtColumn(connection);
            backfillSubmissionTimestamps(connection);
            normalizeSubmissionTimestampColumns(connection);
            createExecutionSchemaIndexesAndConstraints(connection);
            insertExecutionCompatibilityData(connection);
        } catch (SQLException | RuntimeException e) {
            throw new IllegalStateException("Failed to migrate exam execution schema", e);
        }
    }

    private void createStudentProfilesTable(Connection connection) throws SQLException {
        if (tableExists(connection, "student_profiles")) {
            return;
        }

        executeSchemaStatement(connection, """
                CREATE TABLE student_profiles (
                    user_id INT NOT NULL,
                    identity_number_hash VARCHAR(255) NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (user_id),
                    CONSTRAINT fk_student_profiles_user
                        FOREIGN KEY (user_id) REFERENCES users (user_id)
                ) ENGINE=InnoDB
                """);
    }

    private void createStudentCoursesTable(Connection connection) throws SQLException {
        if (tableExists(connection, "student_courses")) {
            return;
        }

        executeSchemaStatement(connection, """
                CREATE TABLE student_courses (
                    student_user_id INT NOT NULL,
                    course_id INT NOT NULL,
                    enrolled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (student_user_id, course_id),
                    KEY idx_student_courses_course_id (course_id),
                    CONSTRAINT fk_student_courses_student
                        FOREIGN KEY (student_user_id) REFERENCES users (user_id),
                    CONSTRAINT fk_student_courses_course
                        FOREIGN KEY (course_id) REFERENCES courses (course_id)
                ) ENGINE=InnoDB
                """);
    }

    private void createExamExecutionsTable(Connection connection) throws SQLException {
        if (tableExists(connection, "exam_executions")) {
            return;
        }

        executeSchemaStatement(connection, """
                CREATE TABLE exam_executions (
                    execution_id INT NOT NULL AUTO_INCREMENT,
                    execution_code CHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    exam_id INT NOT NULL,
                    exam_version_no INT NOT NULL,
                    opening_time DATETIME NOT NULL,
                    closing_time DATETIME NOT NULL,
                    duration_minutes INT NOT NULL,
                    cumulative_extension_minutes INT NOT NULL DEFAULT 0,
                    status VARCHAR(32) NOT NULL,
                    created_by_user_id INT NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,
                    closed_at DATETIME NULL,
                    average_score DECIMAL(7,2) NULL,
                    median_score DECIMAL(7,2) NULL,
                    started_count INT NOT NULL DEFAULT 0,
                    submitted_count INT NOT NULL DEFAULT 0,
                    auto_submitted_count INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (execution_id),
                    CONSTRAINT uq_exam_executions_code UNIQUE (execution_code),
                    KEY idx_exam_executions_exam_version (exam_id, exam_version_no),
                    KEY idx_exam_executions_status_window
                        (status, opening_time, closing_time),
                    KEY idx_exam_executions_creator (created_by_user_id),
                    CONSTRAINT fk_exam_executions_exam_version
                        FOREIGN KEY (exam_id, exam_version_no)
                        REFERENCES exam_versions (exam_id, version_no),
                    CONSTRAINT fk_exam_executions_creator
                        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id),
                    CONSTRAINT chk_exam_executions_code
                        CHECK (execution_code REGEXP '^[A-Z0-9]{4}$'),
                    CONSTRAINT chk_exam_executions_window
                        CHECK (opening_time < closing_time),
                    CONSTRAINT chk_exam_executions_duration
                        CHECK (duration_minutes > 0),
                    CONSTRAINT chk_exam_executions_extension
                        CHECK (cumulative_extension_minutes >= 0
                            AND cumulative_extension_minutes < duration_minutes),
                    CONSTRAINT chk_exam_executions_status
                        CHECK (status IN ('SCHEDULED', 'OPEN', 'CLOSED')),
                    CONSTRAINT chk_exam_executions_counts
                        CHECK (started_count >= 0
                            AND submitted_count >= 0
                            AND auto_submitted_count >= 0),
                    CONSTRAINT chk_exam_executions_average_score
                        CHECK (average_score IS NULL OR average_score BETWEEN 0 AND 100),
                    CONSTRAINT chk_exam_executions_median_score
                        CHECK (median_score IS NULL OR median_score BETWEEN 0 AND 100)
                ) ENGINE=InnoDB
                """);
    }

    private void createExamSubmissionsTable(Connection connection) throws SQLException {
        if (tableExists(connection, "exam_submissions")) {
            return;
        }

        executeSchemaStatement(connection, """
                CREATE TABLE exam_submissions (
                    submission_id INT NOT NULL AUTO_INCREMENT,
                    execution_id INT NOT NULL,
                    student_user_id INT NOT NULL,
                    started_at DATETIME NOT NULL,
                    submitted_at DATETIME NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,
                    status VARCHAR(32) NOT NULL,
                    allocated_duration_minutes INT NOT NULL,
                    extra_minutes INT NOT NULL DEFAULT 0,
                    extension_reason TEXT NULL,
                    actual_duration_minutes INT NULL,
                    automatic_score DECIMAL(7,2) NULL,
                    final_score DECIMAL(7,2) NULL,
                    teacher_feedback TEXT NULL,
                    manual_change_reason TEXT NULL,
                    reviewed_by_user_id INT NULL,
                    reviewed_at DATETIME NULL,
                    published_by_user_id INT NULL,
                    published_at DATETIME NULL,
                    PRIMARY KEY (submission_id),
                    CONSTRAINT uq_exam_submissions_execution_student
                        UNIQUE (execution_id, student_user_id),
                    KEY idx_exam_submissions_student_status (student_user_id, status),
                    KEY idx_exam_submissions_execution_status (execution_id, status),
                    CONSTRAINT fk_exam_submissions_execution
                        FOREIGN KEY (execution_id) REFERENCES exam_executions (execution_id)
                        ON DELETE CASCADE,
                    CONSTRAINT fk_exam_submissions_student
                        FOREIGN KEY (student_user_id) REFERENCES users (user_id),
                    CONSTRAINT fk_exam_submissions_reviewer
                        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (user_id),
                    CONSTRAINT fk_exam_submissions_publisher
                        FOREIGN KEY (published_by_user_id) REFERENCES users (user_id),
                    CONSTRAINT chk_exam_submissions_status
                        CHECK (status IN
                            ('IN_PROGRESS', 'SUBMITTED', 'AUTO_SUBMITTED', 'PUBLISHED')),
                    CONSTRAINT chk_exam_submissions_allocated_duration
                        CHECK (allocated_duration_minutes > 0),
                    CONSTRAINT chk_exam_submissions_extra_minutes
                        CHECK (extra_minutes >= 0),
                    CONSTRAINT chk_exam_submissions_actual_duration
                        CHECK (actual_duration_minutes IS NULL
                            OR actual_duration_minutes >= 0),
                    CONSTRAINT chk_exam_submissions_automatic_score
                        CHECK (automatic_score IS NULL OR automatic_score BETWEEN 0 AND 100),
                    CONSTRAINT chk_exam_submissions_final_score
                        CHECK (final_score IS NULL OR final_score BETWEEN 0 AND 100)
                ) ENGINE=InnoDB
                """);
    }

    private void createStudentAnswersTable(Connection connection) throws SQLException {
        if (tableExists(connection, "student_answers")) {
            return;
        }

        executeSchemaStatement(connection, """
                CREATE TABLE student_answers (
                    answer_id INT NOT NULL AUTO_INCREMENT,
                    submission_id INT NOT NULL,
                    question_id INT NOT NULL,
                    question_version_no INT NOT NULL,
                    selected_option_number INT NOT NULL,
                    answer_content TEXT NULL,
                    is_correct BOOLEAN NULL,
                    score_received DECIMAL(7,2) NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (answer_id),
                    CONSTRAINT uq_student_answers_submission_question
                        UNIQUE (submission_id, question_id),
                    KEY idx_student_answers_question_version
                        (question_id, question_version_no),
                    CONSTRAINT fk_student_answers_submission
                        FOREIGN KEY (submission_id)
                        REFERENCES exam_submissions (submission_id) ON DELETE CASCADE,
                    CONSTRAINT fk_student_answers_question_version
                        FOREIGN KEY (question_id, question_version_no)
                        REFERENCES question_versions (question_id, version_no),
                    CONSTRAINT chk_student_answers_selected_option
                        CHECK (selected_option_number BETWEEN 1 AND 4),
                    CONSTRAINT chk_student_answers_score
                        CHECK (score_received IS NULL OR score_received >= 0)
                ) ENGINE=InnoDB
                """);
    }

    private void createSubmissionTimeExtensionsTable(Connection connection)
            throws SQLException {
        if (tableExists(connection, "submission_time_extensions")) {
            return;
        }

        executeSchemaStatement(connection, """
                CREATE TABLE submission_time_extensions (
                    extension_id INT NOT NULL AUTO_INCREMENT,
                    submission_id INT NOT NULL,
                    added_minutes INT NOT NULL,
                    reason TEXT NOT NULL,
                    extended_by_user_id INT NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (extension_id),
                    CONSTRAINT fk_submission_time_extensions_submission
                        FOREIGN KEY (submission_id)
                        REFERENCES exam_submissions (submission_id) ON DELETE CASCADE,
                    CONSTRAINT fk_submission_time_extensions_user
                        FOREIGN KEY (extended_by_user_id) REFERENCES users (user_id),
                    CONSTRAINT chk_submission_time_extensions_minutes
                        CHECK (added_minutes > 0)
                ) ENGINE=InnoDB
                """);
    }

    private void createExamExecutionDecilesTable(Connection connection)
            throws SQLException {
        if (tableExists(connection, "exam_execution_deciles")) {
            return;
        }

        executeSchemaStatement(connection, """
                CREATE TABLE exam_execution_deciles (
                    execution_id INT NOT NULL,
                    decile_number INT NOT NULL,
                    submission_count INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (execution_id, decile_number),
                    CONSTRAINT fk_exam_execution_deciles_execution
                        FOREIGN KEY (execution_id)
                        REFERENCES exam_executions (execution_id) ON DELETE CASCADE,
                    CONSTRAINT chk_exam_execution_deciles_number
                        CHECK (decile_number BETWEEN 1 AND 10),
                    CONSTRAINT chk_exam_execution_deciles_count
                        CHECK (submission_count >= 0)
                ) ENGINE=InnoDB
                """);
    }

    private void createExecutionTimeExtensionsTable(Connection connection)
            throws SQLException {
        if (tableExists(connection, "execution_time_extensions")) {
            return;
        }
        executeSchemaStatement(connection, """
                CREATE TABLE execution_time_extensions (
                    extension_id INT NOT NULL AUTO_INCREMENT,
                    execution_id INT NOT NULL,
                    added_minutes INT NOT NULL,
                    reason TEXT NOT NULL,
                    extended_by_user_id INT NOT NULL,
                    created_at DATETIME(6) NOT NULL,
                    PRIMARY KEY (extension_id),
                    KEY idx_execution_time_extensions_execution_created
                        (execution_id, created_at),
                    CONSTRAINT fk_execution_time_extensions_execution
                        FOREIGN KEY (execution_id)
                        REFERENCES exam_executions (execution_id) ON DELETE CASCADE,
                    CONSTRAINT fk_execution_time_extensions_user
                        FOREIGN KEY (extended_by_user_id) REFERENCES users (user_id),
                    CONSTRAINT chk_execution_time_extensions_minutes
                        CHECK (added_minutes > 0)
                ) ENGINE=InnoDB
                """);
    }

    private void createNotificationsTable(Connection connection) throws SQLException {
        if (tableExists(connection, "notifications")) {
            return;
        }
        executeSchemaStatement(connection, """
                CREATE TABLE notifications (
                    notification_id INT NOT NULL AUTO_INCREMENT,
                    recipient_user_id INT NOT NULL,
                    notification_type VARCHAR(32) NOT NULL,
                    title VARCHAR(160) NOT NULL,
                    message TEXT NOT NULL,
                    related_exam_id INT NULL,
                    related_execution_id INT NULL,
                    related_submission_id INT NULL,
                    deduplication_key VARCHAR(160)
                        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    created_at DATETIME(6) NOT NULL,
                    read_at DATETIME(6) NULL,
                    PRIMARY KEY (notification_id),
                    CONSTRAINT uq_notifications_deduplication
                        UNIQUE (deduplication_key),
                    KEY idx_notifications_recipient_created
                        (recipient_user_id, created_at),
                    KEY idx_notifications_recipient_read
                        (recipient_user_id, read_at),
                    CONSTRAINT fk_notifications_recipient
                        FOREIGN KEY (recipient_user_id) REFERENCES users (user_id),
                    CONSTRAINT chk_notifications_type CHECK (notification_type IN
                        ('EXAM_APPROVED', 'EXAM_REJECTED', 'EXAM_SCHEDULED',
                         'EXECUTION_EXTENDED', 'GRADE_PUBLISHED', 'EXAM_SUBMITTED')),
                    CONSTRAINT chk_notifications_read_time
                        CHECK (read_at IS NULL OR read_at >= created_at)
                ) ENGINE=InnoDB
                """);
    }

    private void ensureNotificationTypeConstraint(Connection connection)
            throws SQLException {
        String checkClause = null;
        String lookupSql = """
                SELECT checks.CHECK_CLAUSE
                FROM information_schema.TABLE_CONSTRAINTS table_constraints
                JOIN information_schema.CHECK_CONSTRAINTS checks
                  ON checks.CONSTRAINT_SCHEMA = table_constraints.CONSTRAINT_SCHEMA
                 AND checks.CONSTRAINT_NAME = table_constraints.CONSTRAINT_NAME
                WHERE table_constraints.CONSTRAINT_SCHEMA = DATABASE()
                  AND table_constraints.TABLE_NAME = 'notifications'
                  AND table_constraints.CONSTRAINT_NAME = 'chk_notifications_type'
                """;
        try (PreparedStatement statement = connection.prepareStatement(lookupSql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                checkClause = resultSet.getString("CHECK_CLAUSE");
            }
        }
        // Guard on the most recently added value. Testing an older value would
        // leave databases created before that addition permanently out of date.
        if (checkClause != null && checkClause.contains("EXAM_SUBMITTED")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (checkClause != null) {
                statement.execute("""
                        ALTER TABLE notifications
                        DROP CHECK chk_notifications_type
                        """);
            }
            statement.execute("""
                    ALTER TABLE notifications
                    ADD CONSTRAINT chk_notifications_type CHECK (notification_type IN
                        ('EXAM_APPROVED', 'EXAM_REJECTED', 'EXAM_SCHEDULED',
                         'EXECUTION_EXTENDED', 'GRADE_PUBLISHED', 'EXAM_SUBMITTED'))
                    """);
        }
    }

    private void migrateExecutionSchemaColumns(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "student_profiles", "user_id", "INT NOT NULL");
        addColumnIfMissing(connection, "student_profiles", "identity_number_hash",
                "VARCHAR(255) NOT NULL");
        addColumnIfMissing(connection, "student_profiles", "created_at",
                "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
        addColumnIfMissing(connection, "student_profiles", "updated_at", """
                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                """);

        addColumnIfMissing(connection, "student_courses", "student_user_id", "INT NOT NULL");
        addColumnIfMissing(connection, "student_courses", "course_id", "INT NOT NULL");
        addColumnIfMissing(connection, "student_courses", "enrolled_at",
                "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");

        addColumnIfMissing(connection, "exam_executions", "execution_id",
                "INT NOT NULL AUTO_INCREMENT PRIMARY KEY");
        addColumnIfMissing(connection, "exam_executions", "execution_code",
                "CHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL");
        addColumnIfMissing(connection, "exam_executions", "exam_id", "INT NOT NULL");
        addColumnIfMissing(connection, "exam_executions", "exam_version_no", "INT NOT NULL");
        addColumnIfMissing(connection, "exam_executions", "opening_time", "DATETIME NOT NULL");
        addColumnIfMissing(connection, "exam_executions", "closing_time", "DATETIME NOT NULL");
        addColumnIfMissing(connection, "exam_executions", "duration_minutes", "INT NOT NULL");
        addColumnIfMissing(connection, "exam_executions",
                "cumulative_extension_minutes", "INT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "exam_executions", "status", "VARCHAR(32) NOT NULL");
        addColumnIfMissing(connection, "exam_executions", "created_by_user_id", "INT NOT NULL");
        addColumnIfMissing(connection, "exam_executions", "created_at",
                "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
        addColumnIfMissing(connection, "exam_executions", "updated_at", "TIMESTAMP NULL");
        addColumnIfMissing(connection, "exam_executions", "closed_at", "DATETIME NULL");
        addColumnIfMissing(connection, "exam_executions", "average_score", "DECIMAL(7,2) NULL");
        addColumnIfMissing(connection, "exam_executions", "median_score", "DECIMAL(7,2) NULL");
        addColumnIfMissing(connection, "exam_executions", "started_count",
                "INT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "exam_executions", "submitted_count",
                "INT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "exam_executions", "auto_submitted_count",
                "INT NOT NULL DEFAULT 0");

        addColumnIfMissing(connection, "exam_submissions", "submission_id",
                "INT NOT NULL AUTO_INCREMENT PRIMARY KEY");
        addColumnIfMissing(connection, "exam_submissions", "execution_id", "INT NOT NULL");
        addColumnIfMissing(connection, "exam_submissions", "student_user_id", "INT NOT NULL");
        addColumnIfMissing(connection, "exam_submissions", "started_at", "DATETIME NOT NULL");
        addColumnIfMissing(connection, "exam_submissions", "submitted_at", "DATETIME NULL");
        addColumnIfMissing(connection, "exam_submissions", "created_at", "TIMESTAMP NULL");
        addColumnIfMissing(connection, "exam_submissions", "updated_at", "TIMESTAMP NULL");
        addColumnIfMissing(connection, "exam_submissions", "status", "VARCHAR(32) NOT NULL");
        addColumnIfMissing(connection, "exam_submissions", "allocated_duration_minutes",
                "INT NOT NULL");
        addColumnIfMissing(connection, "exam_submissions", "extra_minutes",
                "INT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, "exam_submissions", "extension_reason", "TEXT NULL");
        addColumnIfMissing(connection, "exam_submissions", "actual_duration_minutes", "INT NULL");
        addColumnIfMissing(connection, "exam_submissions", "automatic_score", "DECIMAL(7,2) NULL");
        addColumnIfMissing(connection, "exam_submissions", "final_score", "DECIMAL(7,2) NULL");
        addColumnIfMissing(connection, "exam_submissions", "teacher_feedback", "TEXT NULL");
        addColumnIfMissing(connection, "exam_submissions", "manual_change_reason", "TEXT NULL");
        addColumnIfMissing(connection, "exam_submissions", "reviewed_by_user_id", "INT NULL");
        addColumnIfMissing(connection, "exam_submissions", "reviewed_at", "DATETIME NULL");
        addColumnIfMissing(connection, "exam_submissions", "published_by_user_id", "INT NULL");
        addColumnIfMissing(connection, "exam_submissions", "published_at", "DATETIME NULL");

        addColumnIfMissing(connection, "student_answers", "answer_id",
                "INT NOT NULL AUTO_INCREMENT PRIMARY KEY");
        addColumnIfMissing(connection, "student_answers", "submission_id", "INT NOT NULL");
        addColumnIfMissing(connection, "student_answers", "question_id", "INT NOT NULL");
        addColumnIfMissing(connection, "student_answers", "question_version_no", "INT NOT NULL");
        addColumnIfMissing(connection, "student_answers", "selected_option_number", "INT NOT NULL");
        addColumnIfMissing(connection, "student_answers", "answer_content", "TEXT NULL");
        addColumnIfMissing(connection, "student_answers", "is_correct", "BOOLEAN NULL");
        addColumnIfMissing(connection, "student_answers", "score_received", "DECIMAL(7,2) NULL");
        addColumnIfMissing(connection, "student_answers", "created_at",
                "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
        addColumnIfMissing(connection, "student_answers", "updated_at", """
                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                """);

        addColumnIfMissing(connection, "submission_time_extensions", "extension_id",
                "INT NOT NULL AUTO_INCREMENT PRIMARY KEY");
        addColumnIfMissing(connection, "submission_time_extensions", "submission_id",
                "INT NOT NULL");
        addColumnIfMissing(connection, "submission_time_extensions", "added_minutes",
                "INT NOT NULL");
        addColumnIfMissing(connection, "submission_time_extensions", "reason", "TEXT NOT NULL");
        addColumnIfMissing(connection, "submission_time_extensions", "extended_by_user_id",
                "INT NOT NULL");
        addColumnIfMissing(connection, "submission_time_extensions", "created_at",
                "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");

        addColumnIfMissing(connection, "exam_execution_deciles", "execution_id", "INT NOT NULL");
        addColumnIfMissing(connection, "exam_execution_deciles", "decile_number", "INT NOT NULL");
        addColumnIfMissing(connection, "exam_execution_deciles", "submission_count",
                "INT NOT NULL DEFAULT 0");
    }

    private void backfillExecutionUpdatedAt(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        boolean transactionStarted = false;
        Throwable migrationFailure = null;

        try {
            connection.setAutoCommit(false);
            transactionStarted = true;
            // closed_at is the latest authoritative lifecycle timestamp when present.
            executeUpdate(connection, """
                    UPDATE exam_executions
                    SET updated_at = COALESCE(closed_at, created_at)
                    WHERE updated_at IS NULL
                    """);
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
    }

    private void normalizeExecutionUpdatedAtColumn(Connection connection)
            throws SQLException {
        if (executionUpdatedAtDefinitionIsRequired(connection)) {
            return;
        }

        executeSchemaStatement(connection, """
                ALTER TABLE exam_executions
                MODIFY COLUMN updated_at TIMESTAMP NOT NULL
                    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                """);
    }

    private boolean executionUpdatedAtDefinitionIsRequired(Connection connection)
            throws SQLException {
        String sql = """
                SELECT DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'exam_executions'
                  AND COLUMN_NAME = 'updated_at'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return false;
            }

            String dataType = resultSet.getString("DATA_TYPE");
            String nullable = resultSet.getString("IS_NULLABLE");
            String defaultValue = resultSet.getString("COLUMN_DEFAULT");
            String extra = resultSet.getString("EXTRA");
            return "timestamp".equalsIgnoreCase(dataType)
                    && "NO".equalsIgnoreCase(nullable)
                    && defaultValue != null
                    && defaultValue.toUpperCase(Locale.ROOT).startsWith("CURRENT_TIMESTAMP")
                    && extra != null
                    && extra.toLowerCase(Locale.ROOT).contains("on update current_timestamp");
        }
    }

    private void backfillSubmissionTimestamps(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        boolean transactionStarted = false;
        Throwable migrationFailure = null;

        try {
            connection.setAutoCommit(false);
            transactionStarted = true;
            // COMPATIBILITY-ONLY: Legacy rows have no complete timestamp history.
            // The approved baseline retains the best available lifecycle timestamps.
            executeUpdate(connection, """
                    UPDATE exam_submissions
                    SET created_at = COALESCE(
                        started_at,
                        submitted_at,
                        CURRENT_TIMESTAMP
                    ),
                        updated_at = updated_at
                    WHERE created_at IS NULL
                    """);
            executeUpdate(connection, """
                    UPDATE exam_submissions
                    SET updated_at = GREATEST(
                        created_at,
                        COALESCE(
                            submitted_at,
                            started_at,
                            created_at,
                            CURRENT_TIMESTAMP
                        )
                    )
                    WHERE updated_at IS NULL
                    """);
            validateSubmissionTimestampOrdering(connection);
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
    }

    private void validateSubmissionTimestampOrdering(Connection connection)
            throws SQLException {
        String sql = """
                SELECT submission_id
                FROM exam_submissions
                WHERE updated_at < created_at
                LIMIT 1
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                throw new SQLException(
                        "Existing submission timestamps have invalid ordering"
                );
            }
        }
    }

    private void normalizeSubmissionTimestampColumns(Connection connection)
            throws SQLException {
        if (!submissionTimestampDefinitionIsRequired(
                connection,
                "created_at",
                false
        )) {
            executeSchemaStatement(connection, """
                    ALTER TABLE exam_submissions
                    MODIFY COLUMN created_at TIMESTAMP NOT NULL
                        DEFAULT CURRENT_TIMESTAMP
                    """);
        }

        if (!submissionTimestampDefinitionIsRequired(
                connection,
                "updated_at",
                true
        )) {
            executeSchemaStatement(connection, """
                    ALTER TABLE exam_submissions
                    MODIFY COLUMN updated_at TIMESTAMP NOT NULL
                        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    """);
        }
    }

    private boolean submissionTimestampDefinitionIsRequired(
            Connection connection,
            String columnName,
            boolean onUpdateRequired
    ) throws SQLException {
        String sql = """
                SELECT DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'exam_submissions'
                  AND COLUMN_NAME = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return false;
                }

                String dataType = resultSet.getString("DATA_TYPE");
                String nullable = resultSet.getString("IS_NULLABLE");
                String defaultValue = resultSet.getString("COLUMN_DEFAULT");
                String extra = resultSet.getString("EXTRA");
                boolean hasOnUpdate = extra != null
                        && extra.toLowerCase(Locale.ROOT)
                        .contains("on update current_timestamp");
                return "timestamp".equalsIgnoreCase(dataType)
                        && "NO".equalsIgnoreCase(nullable)
                        && defaultValue != null
                        && defaultValue.toUpperCase(Locale.ROOT)
                        .startsWith("CURRENT_TIMESTAMP")
                        && hasOnUpdate == onUpdateRequired;
            }
        }
    }

    private void createExecutionSchemaIndexesAndConstraints(Connection connection)
            throws SQLException {
        ensureConstraint(connection, "student_profiles", "PRIMARY", """
                ALTER TABLE student_profiles ADD PRIMARY KEY (user_id)
                """);
        ensureConstraint(connection, "student_profiles", "fk_student_profiles_user", """
                ALTER TABLE student_profiles
                ADD CONSTRAINT fk_student_profiles_user
                FOREIGN KEY (user_id) REFERENCES users (user_id)
                """);

        ensureConstraint(connection, "student_courses", "PRIMARY", """
                ALTER TABLE student_courses ADD PRIMARY KEY (student_user_id, course_id)
                """);
        ensureIndex(connection, "student_courses", "idx_student_courses_course_id", """
                CREATE INDEX idx_student_courses_course_id ON student_courses (course_id)
                """);
        ensureConstraint(connection, "student_courses", "fk_student_courses_student", """
                ALTER TABLE student_courses
                ADD CONSTRAINT fk_student_courses_student
                FOREIGN KEY (student_user_id) REFERENCES users (user_id)
                """);
        ensureConstraint(connection, "student_courses", "fk_student_courses_course", """
                ALTER TABLE student_courses
                ADD CONSTRAINT fk_student_courses_course
                FOREIGN KEY (course_id) REFERENCES courses (course_id)
                """);

        ensureConstraint(connection, "exam_executions", "PRIMARY", """
                ALTER TABLE exam_executions ADD PRIMARY KEY (execution_id)
                """);
        ensureConstraint(connection, "exam_executions", "uq_exam_executions_code", """
                ALTER TABLE exam_executions
                ADD CONSTRAINT uq_exam_executions_code UNIQUE (execution_code)
                """);
        ensureIndex(connection, "exam_executions", "idx_exam_executions_exam_version", """
                CREATE INDEX idx_exam_executions_exam_version
                ON exam_executions (exam_id, exam_version_no)
                """);
        ensureIndex(connection, "exam_executions", "idx_exam_executions_status_window", """
                CREATE INDEX idx_exam_executions_status_window
                ON exam_executions (status, opening_time, closing_time)
                """);
        ensureIndex(connection, "exam_executions", "idx_exam_executions_creator", """
                CREATE INDEX idx_exam_executions_creator
                ON exam_executions (created_by_user_id)
                """);
        ensureConstraint(connection, "exam_executions",
                "fk_exam_executions_exam_version", """
                ALTER TABLE exam_executions
                ADD CONSTRAINT fk_exam_executions_exam_version
                FOREIGN KEY (exam_id, exam_version_no)
                REFERENCES exam_versions (exam_id, version_no)
                """);
        ensureConstraint(connection, "exam_executions", "fk_exam_executions_creator", """
                ALTER TABLE exam_executions
                ADD CONSTRAINT fk_exam_executions_creator
                FOREIGN KEY (created_by_user_id) REFERENCES users (user_id)
                """);
        ensureConstraint(connection, "exam_executions", "chk_exam_executions_code", """
                ALTER TABLE exam_executions
                ADD CONSTRAINT chk_exam_executions_code
                CHECK (execution_code REGEXP '^[A-Z0-9]{4}$')
                """);
        ensureConstraint(connection, "exam_executions", "chk_exam_executions_window", """
                ALTER TABLE exam_executions
                ADD CONSTRAINT chk_exam_executions_window CHECK (opening_time < closing_time)
                """);
        ensureConstraint(connection, "exam_executions", "chk_exam_executions_duration", """
                ALTER TABLE exam_executions
                ADD CONSTRAINT chk_exam_executions_duration CHECK (duration_minutes > 0)
                """);
        ensureConstraint(connection, "exam_executions", "chk_exam_executions_extension", """
                ALTER TABLE exam_executions
                ADD CONSTRAINT chk_exam_executions_extension
                CHECK (cumulative_extension_minutes >= 0
                    AND cumulative_extension_minutes < duration_minutes)
                """);
        ensureConstraint(connection, "exam_executions", "chk_exam_executions_status", """
                ALTER TABLE exam_executions
                ADD CONSTRAINT chk_exam_executions_status
                CHECK (status IN ('SCHEDULED', 'OPEN', 'CLOSED'))
                """);
        ensureConstraint(connection, "exam_executions", "chk_exam_executions_counts", """
                ALTER TABLE exam_executions
                ADD CONSTRAINT chk_exam_executions_counts
                CHECK (started_count >= 0
                    AND submitted_count >= 0
                    AND auto_submitted_count >= 0)
                """);
        ensureConstraint(connection, "exam_executions",
                "chk_exam_executions_average_score", """
                ALTER TABLE exam_executions
                ADD CONSTRAINT chk_exam_executions_average_score
                CHECK (average_score IS NULL OR average_score BETWEEN 0 AND 100)
                """);
        ensureConstraint(connection, "exam_executions",
                "chk_exam_executions_median_score", """
                ALTER TABLE exam_executions
                ADD CONSTRAINT chk_exam_executions_median_score
                CHECK (median_score IS NULL OR median_score BETWEEN 0 AND 100)
                """);

        ensureConstraint(connection, "exam_submissions", "PRIMARY", """
                ALTER TABLE exam_submissions ADD PRIMARY KEY (submission_id)
                """);
        ensureConstraint(connection, "exam_submissions",
                "uq_exam_submissions_execution_student", """
                ALTER TABLE exam_submissions
                ADD CONSTRAINT uq_exam_submissions_execution_student
                UNIQUE (execution_id, student_user_id)
                """);
        ensureIndex(connection, "exam_submissions",
                "idx_exam_submissions_student_status", """
                CREATE INDEX idx_exam_submissions_student_status
                ON exam_submissions (student_user_id, status)
                """);
        ensureIndex(connection, "exam_submissions",
                "idx_exam_submissions_execution_status", """
                CREATE INDEX idx_exam_submissions_execution_status
                ON exam_submissions (execution_id, status)
                """);
        ensureConstraint(connection, "exam_submissions",
                "fk_exam_submissions_execution", """
                ALTER TABLE exam_submissions
                ADD CONSTRAINT fk_exam_submissions_execution
                FOREIGN KEY (execution_id) REFERENCES exam_executions (execution_id)
                ON DELETE CASCADE
                """);
        ensureConstraint(connection, "exam_submissions", "fk_exam_submissions_student", """
                ALTER TABLE exam_submissions
                ADD CONSTRAINT fk_exam_submissions_student
                FOREIGN KEY (student_user_id) REFERENCES users (user_id)
                """);
        ensureConstraint(connection, "exam_submissions", "fk_exam_submissions_reviewer", """
                ALTER TABLE exam_submissions
                ADD CONSTRAINT fk_exam_submissions_reviewer
                FOREIGN KEY (reviewed_by_user_id) REFERENCES users (user_id)
                """);
        ensureConstraint(connection, "exam_submissions", "fk_exam_submissions_publisher", """
                ALTER TABLE exam_submissions
                ADD CONSTRAINT fk_exam_submissions_publisher
                FOREIGN KEY (published_by_user_id) REFERENCES users (user_id)
                """);
        ensureConstraint(connection, "exam_submissions", "chk_exam_submissions_status", """
                ALTER TABLE exam_submissions
                ADD CONSTRAINT chk_exam_submissions_status
                CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'AUTO_SUBMITTED', 'PUBLISHED'))
                """);
        ensureConstraint(connection, "exam_submissions",
                "chk_exam_submissions_allocated_duration", """
                ALTER TABLE exam_submissions
                ADD CONSTRAINT chk_exam_submissions_allocated_duration
                CHECK (allocated_duration_minutes > 0)
                """);
        ensureConstraint(connection, "exam_submissions",
                "chk_exam_submissions_extra_minutes", """
                ALTER TABLE exam_submissions
                ADD CONSTRAINT chk_exam_submissions_extra_minutes CHECK (extra_minutes >= 0)
                """);
        ensureConstraint(connection, "exam_submissions",
                "chk_exam_submissions_actual_duration", """
                ALTER TABLE exam_submissions
                ADD CONSTRAINT chk_exam_submissions_actual_duration
                CHECK (actual_duration_minutes IS NULL OR actual_duration_minutes >= 0)
                """);
        ensureConstraint(connection, "exam_submissions",
                "chk_exam_submissions_automatic_score", """
                ALTER TABLE exam_submissions
                ADD CONSTRAINT chk_exam_submissions_automatic_score
                CHECK (automatic_score IS NULL OR automatic_score BETWEEN 0 AND 100)
                """);
        ensureConstraint(connection, "exam_submissions",
                "chk_exam_submissions_final_score", """
                ALTER TABLE exam_submissions
                ADD CONSTRAINT chk_exam_submissions_final_score
                CHECK (final_score IS NULL OR final_score BETWEEN 0 AND 100)
                """);

        ensureConstraint(connection, "student_answers", "PRIMARY", """
                ALTER TABLE student_answers ADD PRIMARY KEY (answer_id)
                """);
        ensureConstraint(connection, "student_answers",
                "uq_student_answers_submission_question", """
                ALTER TABLE student_answers
                ADD CONSTRAINT uq_student_answers_submission_question
                UNIQUE (submission_id, question_id)
                """);
        ensureIndex(connection, "student_answers",
                "idx_student_answers_question_version", """
                CREATE INDEX idx_student_answers_question_version
                ON student_answers (question_id, question_version_no)
                """);
        ensureConstraint(connection, "student_answers", "fk_student_answers_submission", """
                ALTER TABLE student_answers
                ADD CONSTRAINT fk_student_answers_submission
                FOREIGN KEY (submission_id) REFERENCES exam_submissions (submission_id)
                ON DELETE CASCADE
                """);
        ensureConstraint(connection, "student_answers",
                "fk_student_answers_question_version", """
                ALTER TABLE student_answers
                ADD CONSTRAINT fk_student_answers_question_version
                FOREIGN KEY (question_id, question_version_no)
                REFERENCES question_versions (question_id, version_no)
                """);
        ensureConstraint(connection, "student_answers",
                "chk_student_answers_selected_option", """
                ALTER TABLE student_answers
                ADD CONSTRAINT chk_student_answers_selected_option
                CHECK (selected_option_number BETWEEN 1 AND 4)
                """);
        ensureConstraint(connection, "student_answers", "chk_student_answers_score", """
                ALTER TABLE student_answers
                ADD CONSTRAINT chk_student_answers_score
                CHECK (score_received IS NULL OR score_received >= 0)
                """);

        ensureConstraint(connection, "submission_time_extensions", "PRIMARY", """
                ALTER TABLE submission_time_extensions ADD PRIMARY KEY (extension_id)
                """);
        ensureConstraint(connection, "submission_time_extensions",
                "fk_submission_time_extensions_submission", """
                ALTER TABLE submission_time_extensions
                ADD CONSTRAINT fk_submission_time_extensions_submission
                FOREIGN KEY (submission_id) REFERENCES exam_submissions (submission_id)
                ON DELETE CASCADE
                """);
        ensureConstraint(connection, "submission_time_extensions",
                "fk_submission_time_extensions_user", """
                ALTER TABLE submission_time_extensions
                ADD CONSTRAINT fk_submission_time_extensions_user
                FOREIGN KEY (extended_by_user_id) REFERENCES users (user_id)
                """);
        ensureConstraint(connection, "submission_time_extensions",
                "chk_submission_time_extensions_minutes", """
                ALTER TABLE submission_time_extensions
                ADD CONSTRAINT chk_submission_time_extensions_minutes
                CHECK (added_minutes > 0)
                """);

        ensureConstraint(connection, "exam_execution_deciles", "PRIMARY", """
                ALTER TABLE exam_execution_deciles
                ADD PRIMARY KEY (execution_id, decile_number)
                """);
        ensureConstraint(connection, "exam_execution_deciles",
                "fk_exam_execution_deciles_execution", """
                ALTER TABLE exam_execution_deciles
                ADD CONSTRAINT fk_exam_execution_deciles_execution
                FOREIGN KEY (execution_id) REFERENCES exam_executions (execution_id)
                ON DELETE CASCADE
                """);
        ensureConstraint(connection, "exam_execution_deciles",
                "chk_exam_execution_deciles_number", """
                ALTER TABLE exam_execution_deciles
                ADD CONSTRAINT chk_exam_execution_deciles_number
                CHECK (decile_number BETWEEN 1 AND 10)
                """);
        ensureConstraint(connection, "exam_execution_deciles",
                "chk_exam_execution_deciles_count", """
                ALTER TABLE exam_execution_deciles
                ADD CONSTRAINT chk_exam_execution_deciles_count
                CHECK (submission_count >= 0)
                """);
    }

    private void insertExecutionCompatibilityData(Connection connection) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        boolean transactionStarted = false;
        Throwable migrationFailure = null;

        try {
            connection.setAutoCommit(false);
            transactionStarted = true;
            validateExecutionCompatibilityStudent(connection);
            validateCompatibilityCourse(connection, true);
            insertCompatibilityStudentEnrollment(connection);
            insertCompatibilityStudentProfile(connection);
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
    }

    private void validateExecutionCompatibilityStudent(Connection connection)
            throws SQLException {
        String sql = """
                SELECT user_id, email, role, status
                FROM users
                WHERE user_id = 1001 OR email = 'student@hsts.local'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            int matches = 0;
            boolean exactMatch = false;

            while (resultSet.next()) {
                matches++;
                exactMatch = exactMatch || (
                        resultSet.getInt("user_id") == 1001
                                && "student@hsts.local".equalsIgnoreCase(
                                resultSet.getString("email"))
                                && "STUDENT".equals(resultSet.getString("role"))
                                && "ACTIVE".equals(resultSet.getString("status"))
                );
            }

            if (matches != 1 || !exactMatch) {
                throw new IllegalStateException(
                        "Compatibility student conflict for fixed identifier 1001"
                );
            }
        }
    }

    private void insertCompatibilityStudentEnrollment(Connection connection)
            throws SQLException {
        executeUpdate(connection, """
                INSERT INTO student_courses (student_user_id, course_id)
                SELECT student.user_id, course.course_id
                FROM users student
                JOIN courses course ON course.course_id = 1
                WHERE student.user_id = 1001
                  AND student.role = 'STUDENT'
                  AND student.status = 'ACTIVE'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM student_courses existing_enrollment
                      WHERE existing_enrollment.student_user_id = student.user_id
                        AND existing_enrollment.course_id = course.course_id
                  )
                """);
    }

    private void insertCompatibilityStudentProfile(Connection connection)
            throws SQLException {
        if (studentProfileExists(connection, 1001)) {
            return;
        }

        String identityHash = PasswordHasher.hash("123456789");
        String sql = """
                INSERT INTO student_profiles (user_id, identity_number_hash)
                SELECT student.user_id, ?
                FROM users student
                WHERE student.user_id = 1001
                  AND student.role = 'STUDENT'
                  AND student.status = 'ACTIVE'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM student_profiles existing_profile
                      WHERE existing_profile.user_id = student.user_id
                  )
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, identityHash);
            statement.executeUpdate();
        }
    }

    private boolean studentProfileExists(Connection connection, int userId)
            throws SQLException {
        String sql = "SELECT 1 FROM student_profiles WHERE user_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void createSubjectCoordinatorsTable(Connection connection) throws SQLException {
        if (tableExists(connection, "subject_coordinators")) {
            return;
        }

        executeSchemaStatement(connection, """
                CREATE TABLE subject_coordinators (
                    subject_id INT NOT NULL,
                    coordinator_user_id INT NOT NULL,
                    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (subject_id, coordinator_user_id),
                    KEY idx_subject_coordinators_coordinator_user_id (coordinator_user_id),
                    CONSTRAINT fk_subject_coordinators_subject
                        FOREIGN KEY (subject_id) REFERENCES subjects (subject_id),
                    CONSTRAINT fk_subject_coordinators_user
                        FOREIGN KEY (coordinator_user_id) REFERENCES users (user_id)
                ) ENGINE=InnoDB
                """);
    }

    private void createExamsTable(Connection connection) throws SQLException {
        if (tableExists(connection, "exams")) {
            return;
        }

        // The nullable current-version pointer is maintained transactionally because MySQL
        // cannot defer the circular foreign key while creating an exam and its first version.
        executeSchemaStatement(connection, """
                CREATE TABLE exams (
                    exam_id INT NOT NULL AUTO_INCREMENT,
                    exam_code CHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    course_id INT NOT NULL,
                    created_by_user_id INT NOT NULL,
                    current_version_no INT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (exam_id),
                    CONSTRAINT uq_exams_exam_code UNIQUE (exam_code),
                    KEY idx_exams_course_id (course_id),
                    KEY idx_exams_created_by_user_id (created_by_user_id),
                    CONSTRAINT fk_exams_course
                        FOREIGN KEY (course_id) REFERENCES courses (course_id),
                    CONSTRAINT fk_exams_creator
                        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id),
                    CONSTRAINT chk_exams_exam_code
                        CHECK (exam_code REGEXP '^[A-Z0-9]{6}$'),
                    CONSTRAINT chk_exams_current_version
                        CHECK (current_version_no IS NULL OR current_version_no > 0)
                ) ENGINE=InnoDB
                """);
    }

    private void createExamVersionsTable(Connection connection) throws SQLException {
        if (tableExists(connection, "exam_versions")) {
            return;
        }

        executeSchemaStatement(connection, """
                CREATE TABLE exam_versions (
                    exam_id INT NOT NULL,
                    version_no INT NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    duration_minutes INT NOT NULL,
                    teacher_notes TEXT NOT NULL,
                    student_instructions TEXT NOT NULL,
                    total_score DECIMAL(7,2) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    version_created_by_user_id INT NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    submitted_at DATETIME NULL,
                    reviewed_by_user_id INT NULL,
                    reviewed_at DATETIME NULL,
                    rejection_reason TEXT NULL,
                    PRIMARY KEY (exam_id, version_no),
                    KEY idx_exam_versions_status (status),
                    KEY idx_exam_versions_reviewed_by_user_id (reviewed_by_user_id),
                    KEY idx_exam_versions_submitted_at (submitted_at),
                    CONSTRAINT fk_exam_versions_exam
                        FOREIGN KEY (exam_id) REFERENCES exams (exam_id) ON DELETE CASCADE,
                    CONSTRAINT fk_exam_versions_creator
                        FOREIGN KEY (version_created_by_user_id) REFERENCES users (user_id),
                    CONSTRAINT fk_exam_versions_reviewer
                        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (user_id),
                    CONSTRAINT chk_exam_versions_version
                        CHECK (version_no > 0),
                    CONSTRAINT chk_exam_versions_duration
                        CHECK (duration_minutes > 0),
                    CONSTRAINT chk_exam_versions_total_score
                        CHECK (total_score = 100.00),
                    CONSTRAINT chk_exam_versions_status
                        CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED'))
                ) ENGINE=InnoDB
                """);
    }

    private void createExamVersionQuestionsTable(Connection connection) throws SQLException {
        if (tableExists(connection, "exam_version_questions")) {
            return;
        }

        executeSchemaStatement(connection, """
                CREATE TABLE exam_version_questions (
                    exam_id INT NOT NULL,
                    exam_version_no INT NOT NULL,
                    order_number INT NOT NULL,
                    question_id INT NOT NULL,
                    question_version_no INT NOT NULL,
                    score DECIMAL(7,2) NOT NULL,
                    PRIMARY KEY (exam_id, exam_version_no, order_number),
                    CONSTRAINT uq_exam_version_questions_question UNIQUE
                        (exam_id, exam_version_no, question_id),
                    KEY idx_exam_version_questions_question_version
                        (question_id, question_version_no),
                    CONSTRAINT fk_exam_version_questions_exam_version
                        FOREIGN KEY (exam_id, exam_version_no)
                        REFERENCES exam_versions (exam_id, version_no) ON DELETE CASCADE,
                    CONSTRAINT fk_exam_version_questions_question_version
                        FOREIGN KEY (question_id, question_version_no)
                        REFERENCES question_versions (question_id, version_no),
                    CONSTRAINT chk_exam_version_questions_order
                        CHECK (order_number > 0),
                    CONSTRAINT chk_exam_version_questions_question_version
                        CHECK (question_version_no > 0),
                    CONSTRAINT chk_exam_version_questions_score
                        CHECK (score > 0)
                ) ENGINE=InnoDB
                """);
    }

    private void createExamSchemaIndexesAndConstraints(Connection connection)
            throws SQLException {
        ensureConstraint(connection, "subject_coordinators", "PRIMARY", """
                ALTER TABLE subject_coordinators
                ADD PRIMARY KEY (subject_id, coordinator_user_id)
                """);
        ensureIndex(connection, "subject_coordinators",
                "idx_subject_coordinators_coordinator_user_id", """
                CREATE INDEX idx_subject_coordinators_coordinator_user_id
                ON subject_coordinators (coordinator_user_id)
                """);
        ensureConstraint(connection, "subject_coordinators",
                "fk_subject_coordinators_subject", """
                ALTER TABLE subject_coordinators
                ADD CONSTRAINT fk_subject_coordinators_subject
                FOREIGN KEY (subject_id) REFERENCES subjects (subject_id)
                """);
        ensureConstraint(connection, "subject_coordinators",
                "fk_subject_coordinators_user", """
                ALTER TABLE subject_coordinators
                ADD CONSTRAINT fk_subject_coordinators_user
                FOREIGN KEY (coordinator_user_id) REFERENCES users (user_id)
                """);

        ensureConstraint(connection, "exams", "PRIMARY", """
                ALTER TABLE exams ADD PRIMARY KEY (exam_id)
                """);
        ensureConstraint(connection, "exams", "uq_exams_exam_code", """
                ALTER TABLE exams
                ADD CONSTRAINT uq_exams_exam_code UNIQUE (exam_code)
                """);
        ensureIndex(connection, "exams", "idx_exams_course_id",
                "CREATE INDEX idx_exams_course_id ON exams (course_id)");
        ensureIndex(connection, "exams", "idx_exams_created_by_user_id", """
                CREATE INDEX idx_exams_created_by_user_id ON exams (created_by_user_id)
                """);
        ensureConstraint(connection, "exams", "fk_exams_course", """
                ALTER TABLE exams
                ADD CONSTRAINT fk_exams_course
                FOREIGN KEY (course_id) REFERENCES courses (course_id)
                """);
        ensureConstraint(connection, "exams", "fk_exams_creator", """
                ALTER TABLE exams
                ADD CONSTRAINT fk_exams_creator
                FOREIGN KEY (created_by_user_id) REFERENCES users (user_id)
                """);
        ensureConstraint(connection, "exams", "chk_exams_exam_code", """
                ALTER TABLE exams
                ADD CONSTRAINT chk_exams_exam_code
                CHECK (exam_code REGEXP '^[A-Z0-9]{6}$')
                """);
        ensureConstraint(connection, "exams", "chk_exams_current_version", """
                ALTER TABLE exams
                ADD CONSTRAINT chk_exams_current_version
                CHECK (current_version_no IS NULL OR current_version_no > 0)
                """);

        ensureConstraint(connection, "exam_versions", "PRIMARY", """
                ALTER TABLE exam_versions ADD PRIMARY KEY (exam_id, version_no)
                """);
        ensureIndex(connection, "exam_versions", "idx_exam_versions_status",
                "CREATE INDEX idx_exam_versions_status ON exam_versions (status)");
        ensureIndex(connection, "exam_versions", "idx_exam_versions_reviewed_by_user_id", """
                CREATE INDEX idx_exam_versions_reviewed_by_user_id
                ON exam_versions (reviewed_by_user_id)
                """);
        ensureIndex(connection, "exam_versions", "idx_exam_versions_submitted_at", """
                CREATE INDEX idx_exam_versions_submitted_at ON exam_versions (submitted_at)
                """);
        ensureConstraint(connection, "exam_versions", "fk_exam_versions_exam", """
                ALTER TABLE exam_versions
                ADD CONSTRAINT fk_exam_versions_exam
                FOREIGN KEY (exam_id) REFERENCES exams (exam_id) ON DELETE CASCADE
                """);
        ensureConstraint(connection, "exam_versions", "fk_exam_versions_creator", """
                ALTER TABLE exam_versions
                ADD CONSTRAINT fk_exam_versions_creator
                FOREIGN KEY (version_created_by_user_id) REFERENCES users (user_id)
                """);
        ensureConstraint(connection, "exam_versions", "fk_exam_versions_reviewer", """
                ALTER TABLE exam_versions
                ADD CONSTRAINT fk_exam_versions_reviewer
                FOREIGN KEY (reviewed_by_user_id) REFERENCES users (user_id)
                """);
        ensureConstraint(connection, "exam_versions", "chk_exam_versions_version", """
                ALTER TABLE exam_versions
                ADD CONSTRAINT chk_exam_versions_version CHECK (version_no > 0)
                """);
        ensureConstraint(connection, "exam_versions", "chk_exam_versions_duration", """
                ALTER TABLE exam_versions
                ADD CONSTRAINT chk_exam_versions_duration CHECK (duration_minutes > 0)
                """);
        ensureConstraint(connection, "exam_versions", "chk_exam_versions_total_score", """
                ALTER TABLE exam_versions
                ADD CONSTRAINT chk_exam_versions_total_score CHECK (total_score = 100.00)
                """);
        ensureConstraint(connection, "exam_versions", "chk_exam_versions_status", """
                ALTER TABLE exam_versions
                ADD CONSTRAINT chk_exam_versions_status
                CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED'))
                """);

        ensureConstraint(connection, "exam_version_questions", "PRIMARY", """
                ALTER TABLE exam_version_questions
                ADD PRIMARY KEY (exam_id, exam_version_no, order_number)
                """);
        ensureConstraint(connection, "exam_version_questions",
                "uq_exam_version_questions_question", """
                ALTER TABLE exam_version_questions
                ADD CONSTRAINT uq_exam_version_questions_question
                UNIQUE (exam_id, exam_version_no, question_id)
                """);
        ensureIndex(connection, "exam_version_questions",
                "idx_exam_version_questions_question_version", """
                CREATE INDEX idx_exam_version_questions_question_version
                ON exam_version_questions (question_id, question_version_no)
                """);
        ensureConstraint(connection, "exam_version_questions",
                "fk_exam_version_questions_exam_version", """
                ALTER TABLE exam_version_questions
                ADD CONSTRAINT fk_exam_version_questions_exam_version
                FOREIGN KEY (exam_id, exam_version_no)
                REFERENCES exam_versions (exam_id, version_no) ON DELETE CASCADE
                """);
        ensureConstraint(connection, "exam_version_questions",
                "fk_exam_version_questions_question_version", """
                ALTER TABLE exam_version_questions
                ADD CONSTRAINT fk_exam_version_questions_question_version
                FOREIGN KEY (question_id, question_version_no)
                REFERENCES question_versions (question_id, version_no)
                """);
        ensureConstraint(connection, "exam_version_questions",
                "chk_exam_version_questions_order", """
                ALTER TABLE exam_version_questions
                ADD CONSTRAINT chk_exam_version_questions_order CHECK (order_number > 0)
                """);
        ensureConstraint(connection, "exam_version_questions",
                "chk_exam_version_questions_question_version", """
                ALTER TABLE exam_version_questions
                ADD CONSTRAINT chk_exam_version_questions_question_version
                CHECK (question_version_no > 0)
                """);
        ensureConstraint(connection, "exam_version_questions",
                "chk_exam_version_questions_score", """
                ALTER TABLE exam_version_questions
                ADD CONSTRAINT chk_exam_version_questions_score CHECK (score > 0)
                """);
    }

    private void insertCompatibilityCoordinatorAssignment(Connection connection)
            throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        boolean transactionStarted = false;
        Throwable migrationFailure = null;

        try {
            connection.setAutoCommit(false);
            transactionStarted = true;
            validateCompatibilityUser(
                    connection,
                    1003,
                    "coordinator@hsts.local",
                    "COORDINATOR"
            );
            validateCompatibilitySubject(connection, true);
            executeUpdate(connection, """
                    INSERT INTO subject_coordinators (
                        subject_id,
                        coordinator_user_id
                    )
                    SELECT
                        subject_record.subject_id,
                        coordinator.user_id
                    FROM subjects subject_record
                    JOIN users coordinator ON coordinator.user_id = 1003
                    WHERE subject_record.subject_id = 1
                      AND subject_record.subject_code = 'LEGACY'
                      AND coordinator.email = 'coordinator@hsts.local'
                      AND coordinator.role = 'COORDINATOR'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM subject_coordinators existing_assignment
                          WHERE existing_assignment.subject_id = subject_record.subject_id
                            AND existing_assignment.coordinator_user_id = coordinator.user_id
                      )
                    """);
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
    }

    private void executeSchemaStatement(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        try (ResultSet resultSet = metaData.getTables(
                connection.getCatalog(),
                null,
                tableName,
                new String[]{"TABLE"}
        )) {
            return resultSet.next();
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
                ) ENGINE=InnoDB
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

    // ------------------------------------------------------------------
    // Encoded business identifiers (requirements 33, 34, 38, 39)
    //
    // questions.question_code  = 3 digit question number + 2 digit course number
    // exams.exam_code          = 2 digit exam number + 2 digit course number
    //                            + 2 digit subject number
    //
    // subjects.subject_number and courses.course_number are supplied by the
    // external school administration system (requirement 19). Existing rows that
    // predate this migration are given numbers here so that the encoded
    // identifiers can be derived; new schools should load the real numbers.
    // ------------------------------------------------------------------

    private void migrateEncodedIdentifiers() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            addColumnIfMissing(
                    connection, "subjects", "subject_number",
                    "CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL"
            );
            addColumnIfMissing(
                    connection, "courses", "course_number",
                    "CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL"
            );
            addColumnIfMissing(
                    connection, "questions", "question_code",
                    "CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NULL"
            );

            backfillOrganisationNumbers(connection, "subjects", "subject_id", "subject_number");
            backfillOrganisationNumbers(connection, "courses", "course_id", "course_number");
            backfillQuestionCodes(connection);
            backfillExamCodes(connection);

            ensureEncodedIdentifierConstraints(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to migrate encoded identifiers", e);
        }
    }

    /**
     * Gives every subject or course a two digit number, preserving any number
     * already present and never reusing one.
     */
    private void backfillOrganisationNumbers(Connection connection, String tableName,
                                             String idColumn, String numberColumn)
            throws SQLException {
        Set<String> taken = new HashSet<>();
        List<Integer> unnumbered = new ArrayList<>();

        String selectSql = "SELECT " + idColumn + ", " + numberColumn
                + " FROM " + tableName + " ORDER BY " + idColumn;

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(selectSql)) {
            while (resultSet.next()) {
                String number = resultSet.getString(numberColumn);
                if (number == null || number.isBlank()) {
                    unnumbered.add(resultSet.getInt(idColumn));
                } else {
                    taken.add(number);
                }
            }
        }

        if (unnumbered.isEmpty()) {
            return;
        }

        String updateSql = "UPDATE " + tableName + " SET " + numberColumn + " = ?"
                + " WHERE " + idColumn + " = ?";

        try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
            int candidate = 1;
            for (int rowId : unnumbered) {
                while (candidate <= 99 && taken.contains(twoDigits(candidate))) {
                    candidate++;
                }
                if (candidate > 99) {
                    throw new IllegalStateException(
                            "Cannot assign a two digit " + numberColumn + ": "
                                    + tableName + " already uses all 100 numbers"
                    );
                }

                String assigned = twoDigits(candidate);
                statement.setString(1, assigned);
                statement.setInt(2, rowId);
                statement.executeUpdate();
                taken.add(assigned);
                candidate++;
            }
        }
    }

    /**
     * Assigns the five digit identifier to every question that does not already
     * carry one, continuing the numbering already in use within each course.
     */
    private void backfillQuestionCodes(Connection connection) throws SQLException {
        Map<Integer, Integer> highestNumberPerCourse = new HashMap<>();

        String usedSql = """
                SELECT course_id, question_code
                FROM questions
                WHERE question_code IS NOT NULL AND course_id IS NOT NULL
                """;

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(usedSql)) {
            while (resultSet.next()) {
                int courseId = resultSet.getInt("course_id");
                String code = resultSet.getString("question_code");
                if (code == null || code.length() != 5) {
                    continue;
                }
                try {
                    int questionNumber = Integer.parseInt(code.substring(0, 3));
                    highestNumberPerCourse.merge(courseId, questionNumber, Math::max);
                } catch (NumberFormatException ignored) {
                    // A malformed legacy value cannot reserve a number.
                }
            }
        }

        String pendingSql = """
                SELECT q.question_id, q.course_id, c.course_number
                FROM questions q
                JOIN courses c ON c.course_id = q.course_id
                WHERE q.question_code IS NULL
                  AND c.course_number IS NOT NULL
                ORDER BY q.course_id, q.question_id
                """;

        String updateSql = "UPDATE questions SET question_code = ? WHERE question_id = ?";

        try (Statement selectStatement = connection.createStatement();
             ResultSet resultSet = selectStatement.executeQuery(pendingSql);
             PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
            while (resultSet.next()) {
                int questionId = resultSet.getInt("question_id");
                int courseId = resultSet.getInt("course_id");
                String courseNumber = resultSet.getString("course_number");

                int questionNumber = highestNumberPerCourse.getOrDefault(courseId, 0) + 1;
                if (questionNumber > 999) {
                    throw new IllegalStateException(
                            "Course " + courseId + " already holds 999 questions;"
                                    + " the three digit question number is exhausted"
                    );
                }
                highestNumberPerCourse.put(courseId, questionNumber);

                updateStatement.setString(1, threeDigits(questionNumber) + courseNumber);
                updateStatement.setInt(2, questionId);
                updateStatement.executeUpdate();
            }
        }
    }

    /**
     * Re-encodes any exam whose code predates requirements 38 and 39, keeping
     * codes that already have the required six digit shape.
     */
    private void backfillExamCodes(Connection connection) throws SQLException {
        Map<Integer, Integer> highestNumberPerCourse = new HashMap<>();
        List<int[]> pending = new ArrayList<>();
        Map<Integer, String[]> courseAndSubjectNumbers = new HashMap<>();

        String selectSql = """
                SELECT e.exam_id, e.exam_code, e.course_id,
                       c.course_number, s.subject_number
                FROM exams e
                JOIN courses c ON c.course_id = e.course_id
                JOIN subjects s ON s.subject_id = c.subject_id
                ORDER BY e.course_id, e.exam_id
                """;

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(selectSql)) {
            while (resultSet.next()) {
                int examId = resultSet.getInt("exam_id");
                int courseId = resultSet.getInt("course_id");
                String examCode = resultSet.getString("exam_code");
                String courseNumber = resultSet.getString("course_number");
                String subjectNumber = resultSet.getString("subject_number");

                if (courseNumber == null || subjectNumber == null) {
                    continue;
                }
                courseAndSubjectNumbers.put(courseId, new String[]{courseNumber, subjectNumber});

                String expectedSuffix = courseNumber + subjectNumber;
                boolean alreadyEncoded = examCode != null
                        && examCode.length() == 6
                        && examCode.chars().allMatch(Character::isDigit)
                        && examCode.endsWith(expectedSuffix);

                if (alreadyEncoded) {
                    highestNumberPerCourse.merge(
                            courseId,
                            Integer.parseInt(examCode.substring(0, 2)),
                            Math::max
                    );
                } else {
                    pending.add(new int[]{examId, courseId});
                }
            }
        }

        if (pending.isEmpty()) {
            return;
        }

        String updateSql = "UPDATE exams SET exam_code = ? WHERE exam_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
            for (int[] row : pending) {
                int examId = row[0];
                int courseId = row[1];
                String[] numbers = courseAndSubjectNumbers.get(courseId);

                int examNumber = highestNumberPerCourse.getOrDefault(courseId, 0) + 1;
                if (examNumber > 99) {
                    throw new IllegalStateException(
                            "Course " + courseId + " already holds 99 exams;"
                                    + " the two digit exam number is exhausted"
                    );
                }
                highestNumberPerCourse.put(courseId, examNumber);

                statement.setString(1, twoDigits(examNumber) + numbers[0] + numbers[1]);
                statement.setInt(2, examId);
                statement.executeUpdate();
            }
        }
    }

    private void ensureEncodedIdentifierConstraints(Connection connection) throws SQLException {
        if (!indexExists(connection, "subjects", "uq_subjects_subject_number")) {
            executeIgnoringDuplicateKey(
                    connection,
                    "ALTER TABLE subjects ADD CONSTRAINT uq_subjects_subject_number"
                            + " UNIQUE (subject_number)"
            );
        }
        if (!indexExists(connection, "courses", "uq_courses_course_number")) {
            executeIgnoringDuplicateKey(
                    connection,
                    "ALTER TABLE courses ADD CONSTRAINT uq_courses_course_number"
                            + " UNIQUE (course_number)"
            );
        }
        if (!indexExists(connection, "questions", "uq_questions_question_code")) {
            executeIgnoringDuplicateKey(
                    connection,
                    "ALTER TABLE questions ADD CONSTRAINT uq_questions_question_code"
                            + " UNIQUE (question_code)"
            );
        }

        ensureConstraint(
                connection, "subjects", "chk_subjects_subject_number",
                "ALTER TABLE subjects ADD CONSTRAINT chk_subjects_subject_number"
                        + " CHECK (subject_number IS NULL OR subject_number REGEXP '^[0-9]{2}$')"
        );
        ensureConstraint(
                connection, "courses", "chk_courses_course_number",
                "ALTER TABLE courses ADD CONSTRAINT chk_courses_course_number"
                        + " CHECK (course_number IS NULL OR course_number REGEXP '^[0-9]{2}$')"
        );
        ensureConstraint(
                connection, "questions", "chk_questions_question_code",
                "ALTER TABLE questions ADD CONSTRAINT chk_questions_question_code"
                        + " CHECK (question_code IS NULL OR question_code REGEXP '^[0-9]{5}$')"
        );

        tightenExamCodeConstraint(connection);
    }

    /**
     * Narrows the exam code constraint from the historic alphanumeric shape to
     * the six digit shape required by requirement 38. The constraint is only
     * replaced once every stored code conforms, so a database that still holds
     * un-encodable rows keeps working rather than failing to start.
     */
    private void tightenExamCodeConstraint(Connection connection) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM exams WHERE exam_code NOT REGEXP '^[0-9]{6}$'";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(countSql)) {
            if (resultSet.next() && resultSet.getInt(1) > 0) {
                return;
            }
        }

        try (Statement statement = connection.createStatement()) {
            if (constraintExists(connection, "exams", "chk_exams_exam_code")) {
                statement.execute("ALTER TABLE exams DROP CHECK chk_exams_exam_code");
            }
            statement.execute(
                    "ALTER TABLE exams ADD CONSTRAINT chk_exams_exam_code"
                            + " CHECK (exam_code REGEXP '^[0-9]{6}$')"
            );
        }
    }

    private void executeIgnoringDuplicateKey(Connection connection, String sql)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            if (!"42000".equals(e.getSQLState()) && !"23000".equals(e.getSQLState())) {
                throw e;
            }
        }
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private static String threeDigits(int value) {
        if (value < 10) {
            return "00" + value;
        }
        return value < 100 ? "0" + value : Integer.toString(value);
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
