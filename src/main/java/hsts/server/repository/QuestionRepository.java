package hsts.server.repository;

import hsts.common.EncodedIdentifiers;
import hsts.common.QuestionDTO;
import hsts.common.QuestionIllustrationDTO;
import hsts.common.QuestionFilterPayload;
import hsts.common.PrincipalQuestionDTO;
import hsts.common.QuestionVersionDTO;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import hsts.server.entity.AnswerOption;
import hsts.server.entity.Question;
import hsts.server.entity.QuestionIllustration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class QuestionRepository {
    private static final int MAX_QUESTION_CODE_ATTEMPTS = 5;
    private static final int MYSQL_DUPLICATE_KEY_ERROR = 1062;

    private static final String QUESTION_COLUMNS = """
            question_id, content, topic, type, difficulty, status, illustration_path,
            answer_option_1, answer_option_2, answer_option_3, answer_option_4, correct_option_number
            """;

    private final DatabaseController databaseController;

    private static final String NORMALIZED_QUESTION_SELECT = """
            SELECT q.question_id,
                   q.question_code,
                   q.course_id,
                   c.subject_id,
                   qv.version_no,
                   qv.content,
                   qv.topic,
                   qv.question_type AS type,
                   qv.difficulty,
                   q.status,
                   qv.illustration_path,
                   qvi.media_type AS illustration_media_type,
                   qvi.content_bytes AS illustration_content_bytes,
                   qvi.byte_length AS illustration_byte_length,
                   qvi.width_pixels AS illustration_width_pixels,
                   qvi.height_pixels AS illustration_height_pixels,
                   qvi.content_sha256 AS illustration_content_sha256,
                   qvi.created_at AS illustration_created_at,
                   option_1.option_text AS answer_option_1,
                   option_2.option_text AS answer_option_2,
                   option_3.option_text AS answer_option_3,
                   option_4.option_text AS answer_option_4,
                   qv.correct_option_number
            FROM questions q
            JOIN question_versions qv
              ON qv.question_id = q.question_id
             AND qv.version_no = q.current_version_no
            JOIN courses c ON c.course_id = q.course_id
            JOIN subjects s ON s.subject_id = c.subject_id
            LEFT JOIN question_version_illustrations qvi
              ON qvi.question_id = qv.question_id
             AND qvi.version_no = qv.version_no
            JOIN teacher_courses tc
              ON tc.course_id = q.course_id
             AND tc.teacher_user_id = ?
             AND q.deleted_at IS NULL
            JOIN answer_options option_1
              ON option_1.question_id = qv.question_id
             AND option_1.version_no = qv.version_no
             AND option_1.option_number = 1
            JOIN answer_options option_2
              ON option_2.question_id = qv.question_id
             AND option_2.version_no = qv.version_no
             AND option_2.option_number = 2
            JOIN answer_options option_3
              ON option_3.question_id = qv.question_id
             AND option_3.version_no = qv.version_no
             AND option_3.option_number = 3
            JOIN answer_options option_4
              ON option_4.question_id = qv.question_id
             AND option_4.version_no = qv.version_no
             AND option_4.option_number = 4
            """;

    private static final String CURRENT_QUESTION_ENTITY_SELECT = """
            SELECT q.question_id,
                   q.status,
                   q.created_at,
                   q.updated_at,
                   q.current_version_no,
                   qv.version_no,
                   qv.content,
                   qv.topic,
                   qv.question_type,
                   qv.difficulty,
                   qv.illustration_path,
                   qvi.media_type AS illustration_media_type,
                   qvi.content_bytes AS illustration_content_bytes,
                   qvi.byte_length AS illustration_byte_length,
                   qvi.width_pixels AS illustration_width_pixels,
                   qvi.height_pixels AS illustration_height_pixels,
                   qvi.content_sha256 AS illustration_content_sha256,
                   qvi.created_at AS illustration_created_at,
                   qv.correct_option_number,
                   option_row.option_number,
                   option_row.option_text
            FROM questions q
            JOIN teacher_courses tc
              ON tc.course_id = q.course_id
             AND tc.teacher_user_id = ?
            LEFT JOIN question_versions qv
              ON qv.question_id = q.question_id
             AND qv.version_no = q.current_version_no
            LEFT JOIN answer_options option_row
              ON option_row.question_id = qv.question_id
             AND option_row.version_no = qv.version_no
            LEFT JOIN question_version_illustrations qvi
              ON qvi.question_id = qv.question_id
             AND qvi.version_no = qv.version_no
            WHERE q.question_id = ?
            ORDER BY option_row.option_number
            """;

    private static final String EXACT_QUESTION_VERSION_ENTITY_SELECT = """
            SELECT q.question_id,
                   q.status,
                   qv.created_at,
                   qv.created_at AS updated_at,
                   qv.version_no,
                   qv.content,
                   qv.topic,
                   qv.question_type,
                   qv.difficulty,
                   qv.illustration_path,
                   qvi.media_type AS illustration_media_type,
                   qvi.content_bytes AS illustration_content_bytes,
                   qvi.byte_length AS illustration_byte_length,
                   qvi.width_pixels AS illustration_width_pixels,
                   qvi.height_pixels AS illustration_height_pixels,
                   qvi.content_sha256 AS illustration_content_sha256,
                   qvi.created_at AS illustration_created_at,
                   qv.correct_option_number,
                   option_row.option_number,
                   option_row.option_text
            FROM questions q
            JOIN teacher_courses tc
              ON tc.course_id = q.course_id
             AND tc.teacher_user_id = ?
            JOIN question_versions qv
              ON qv.question_id = q.question_id
             AND qv.version_no = ?
            LEFT JOIN answer_options option_row
              ON option_row.question_id = qv.question_id
             AND option_row.version_no = qv.version_no
            LEFT JOIN question_version_illustrations qvi
              ON qvi.question_id = qv.question_id
             AND qvi.version_no = qv.version_no
            WHERE q.question_id = ?
            ORDER BY option_row.option_number
            """;

    private static final String CREATE_QUESTION_SQL = """
            INSERT INTO questions (
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
                correct_option_number,
                course_id,
                created_by_user_id,
                current_version_no,
                created_at,
                updated_at,
                question_code
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * Reads the two digit course number and the highest question number already
     * used inside that course (requirements 33, 34).
     */
    /**
     * Hides a question from the bank without removing any data, so exams that
     * already contain it and their graded submissions are unaffected.
     */
    private static final String SOFT_DELETE_QUESTION_SQL = """
            UPDATE questions q
            JOIN teacher_courses tc
              ON tc.course_id = q.course_id
             AND tc.teacher_user_id = ?
            SET q.deleted_at = ?
            WHERE q.question_id = ?
              AND q.deleted_at IS NULL
            """;

    private static final String NEXT_QUESTION_NUMBER_SQL = """
            SELECT c.course_number,
                   COALESCE(
                       -- The question number is the last three digits; the first
                       -- two are the course number.
                       (SELECT MAX(CAST(SUBSTRING(taken.question_code, 3, 3) AS UNSIGNED))
                        FROM questions taken
                        WHERE taken.course_id = c.course_id
                          AND taken.question_code REGEXP '^[0-9]{5}$'),
                       0
                   ) AS highest_question_number
            FROM courses c
            WHERE c.course_id = ?
            """;

    private static final String CREATE_QUESTION_VERSION_SQL = """
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
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String CREATE_ANSWER_OPTION_SQL = """
            INSERT INTO answer_options (
                question_id,
                version_no,
                option_number,
                option_text
            )
            VALUES (?, ?, ?, ?)
            """;

    private static final String CREATE_ILLUSTRATION_SQL = """
            INSERT INTO question_version_illustrations (
                question_id, version_no, media_type, content_bytes,
                byte_length, width_pixels, height_pixels, content_sha256,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String LOCK_QUESTION_VERSION_SQL = """
            SELECT current_version_no
            FROM questions
            WHERE question_id = ?
            FOR UPDATE
            """;

    private static final String UPDATE_CURRENT_QUESTION_SQL = """
            UPDATE questions
            SET content = ?,
                topic = ?,
                type = ?,
                difficulty = ?,
                illustration_path = ?,
                answer_option_1 = ?,
                answer_option_2 = ?,
                answer_option_3 = ?,
                answer_option_4 = ?,
                correct_option_number = ?,
                current_version_no = ?,
                updated_at = ?
            WHERE question_id = ? AND current_version_no = ?
            """;

    private static final String LOCK_ASSIGNED_QUESTION_STATUS_SQL = """
            SELECT q.status
            FROM questions q
            JOIN teacher_courses tc
              ON tc.course_id = q.course_id
             AND tc.teacher_user_id = ?
            WHERE q.question_id = ?
            FOR UPDATE
            """;

    private static final String UPDATE_ASSIGNED_QUESTION_STATUS_SQL = """
            UPDATE questions q
            JOIN teacher_courses tc
              ON tc.course_id = q.course_id
            SET q.status = ?
            WHERE q.question_id = ?
              AND tc.teacher_user_id = ?
            """;

    private static final String QUESTION_VERSION_HISTORY_SQL = """
            SELECT qv.question_id,
                   qv.version_no,
                   q.course_id,
                   qv.content,
                   qv.topic,
                   qv.question_type,
                   qv.difficulty,
                   qv.illustration_path,
                   qvi.media_type AS illustration_media_type,
                   qvi.content_bytes AS illustration_content_bytes,
                   qvi.byte_length AS illustration_byte_length,
                   qvi.width_pixels AS illustration_width_pixels,
                   qvi.height_pixels AS illustration_height_pixels,
                   qvi.content_sha256 AS illustration_content_sha256,
                   qvi.created_at AS illustration_created_at,
                   option_1.option_text AS answer_option_1,
                   option_2.option_text AS answer_option_2,
                   option_3.option_text AS answer_option_3,
                   option_4.option_text AS answer_option_4,
                   qv.correct_option_number,
                   qv.created_by_user_id,
                   qv.created_at
            FROM question_versions qv
            JOIN questions q ON q.question_id = qv.question_id
            LEFT JOIN question_version_illustrations qvi
              ON qvi.question_id = qv.question_id
             AND qvi.version_no = qv.version_no
            JOIN teacher_courses tc
              ON tc.course_id = q.course_id
             AND tc.teacher_user_id = ?
            JOIN answer_options option_1
              ON option_1.question_id = qv.question_id
             AND option_1.version_no = qv.version_no
             AND option_1.option_number = 1
            JOIN answer_options option_2
              ON option_2.question_id = qv.question_id
             AND option_2.version_no = qv.version_no
             AND option_2.option_number = 2
            JOIN answer_options option_3
              ON option_3.question_id = qv.question_id
             AND option_3.version_no = qv.version_no
             AND option_3.option_number = 3
            JOIN answer_options option_4
              ON option_4.question_id = qv.question_id
             AND option_4.version_no = qv.version_no
             AND option_4.option_number = 4
            WHERE qv.question_id = ?
            ORDER BY qv.version_no DESC
            """;

    private static final String PRINCIPAL_QUESTION_SELECT = """
            SELECT q.question_id, q.current_version_no, q.course_id,
                   c.name AS course_name, q.created_by_user_id,
                   creator.full_name AS creator_name, q.status,
                   q.updated_at AS question_updated_at,
                   qv.version_no, qv.content, qv.topic, qv.question_type,
                   qv.difficulty, qv.illustration_path,
                   qvi.media_type AS illustration_media_type,
                   qvi.content_bytes AS illustration_content_bytes,
                   qvi.byte_length AS illustration_byte_length,
                   qvi.width_pixels AS illustration_width_pixels,
                   qvi.height_pixels AS illustration_height_pixels,
                   qvi.content_sha256 AS illustration_content_sha256,
                   qvi.created_at AS illustration_created_at,
                   qv.correct_option_number, qv.created_at AS version_created_at,
                   option_1.option_text AS answer_option_1,
                   option_2.option_text AS answer_option_2,
                   option_3.option_text AS answer_option_3,
                   option_4.option_text AS answer_option_4
            FROM questions q
            JOIN question_versions qv ON qv.question_id = q.question_id
            LEFT JOIN question_version_illustrations qvi
              ON qvi.question_id = qv.question_id
             AND qvi.version_no = qv.version_no
            JOIN courses c ON c.course_id = q.course_id
            JOIN users creator ON creator.user_id = q.created_by_user_id
            JOIN answer_options option_1
              ON option_1.question_id = qv.question_id
             AND option_1.version_no = qv.version_no AND option_1.option_number = 1
            JOIN answer_options option_2
              ON option_2.question_id = qv.question_id
             AND option_2.version_no = qv.version_no AND option_2.option_number = 2
            JOIN answer_options option_3
              ON option_3.question_id = qv.question_id
             AND option_3.version_no = qv.version_no AND option_3.option_number = 3
            JOIN answer_options option_4
              ON option_4.question_id = qv.question_id
             AND option_4.version_no = qv.version_no AND option_4.option_number = 4
            """;

    private static final String PRINCIPAL_CURRENT_QUESTIONS_SQL =
            PRINCIPAL_QUESTION_SELECT + """
            WHERE qv.version_no = q.current_version_no
            ORDER BY c.name ASC, q.question_id ASC
            """;

    private static final String PRINCIPAL_QUESTION_VERSIONS_SQL =
            PRINCIPAL_QUESTION_SELECT + """
            WHERE q.question_id = ?
            ORDER BY qv.version_no DESC
            """;

    private static final String PRINCIPAL_QUESTION_VERSION_SQL =
            PRINCIPAL_QUESTION_SELECT + """
            WHERE q.question_id = ? AND qv.version_no = ?
            """;

    public QuestionRepository() {
        this(new DatabaseController());
    }

    public QuestionRepository(DatabaseController databaseController) {
        this.databaseController = databaseController;
    }

    /**
     * Soft-deletes a question the authenticated teacher is entitled to manage.
     *
     * @return {@code true} when a question was hidden, {@code false} when it
     *         does not exist, is not the teacher's, or was already deleted
     */
    public boolean softDelete(int authenticatedUserId, int questionId) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(SOFT_DELETE_QUESTION_SQL)) {
            statement.setInt(1, authenticatedUserId);
            statement.setObject(2, LocalDateTime.now());
            statement.setInt(3, questionId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete question", e);
        }
    }

    public int create(int authenticatedUserId, int courseId, Question question) {
        if (question == null) {
            throw new IllegalArgumentException("Question data is required");
        }
        return createQuestion(
                authenticatedUserId,
                courseId,
                persistenceData(question)
        );
    }

    private int createQuestion(int createdByUserId, int courseId,
                               QuestionPersistenceData data) {
        try (Connection connection = databaseController.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean transactionStarted = false;
            Throwable transactionFailure = null;

            try {
                connection.setAutoCommit(false);
                transactionStarted = true;

                LocalDateTime createdAt = LocalDateTime.now();
                int questionId = insertCurrentQuestion(
                        connection,
                        createdByUserId,
                        courseId,
                        data,
                        createdAt
                );
                insertQuestionVersion(
                        connection,
                        questionId,
                        createdByUserId,
                        data,
                        createdAt
                );
                insertAnswerOptions(connection, questionId, 1, data);
                insertIllustration(
                        connection, questionId, 1, data.illustration(), createdAt
                );
                connection.commit();
                return questionId;

            } catch (SQLException | RuntimeException e) {
                transactionFailure = e;
                if (transactionStarted) {
                    rollbackWithSuppressed(connection, e);
                }
                throw new IllegalStateException("Failed to create question", e);
            } finally {
                restoreAutoCommit(connection, originalAutoCommit, transactionFailure);
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create question", e);
        }
    }

    public int updateWithNewVersion(int authenticatedUserId, int questionId,
                                    int expectedVersionNo, Question question) {
        if (question == null) {
            throw new IllegalArgumentException("Question update data is required");
        }
        return updateQuestionVersion(
                authenticatedUserId,
                questionId,
                expectedVersionNo,
                persistenceData(question)
        );
    }

    private int updateQuestionVersion(int updatedByUserId, int questionId,
                                      int expectedVersionNo,
                                      QuestionPersistenceData data) {
        try (Connection connection = databaseController.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean transactionStarted = false;
            Throwable transactionFailure = null;

            try {
                connection.setAutoCommit(false);
                transactionStarted = true;

                int currentVersionNo = lockCurrentVersion(connection, questionId);
                if (expectedVersionNo > 0
                        && expectedVersionNo != currentVersionNo) {
                    throw new IllegalStateException("Question version conflict");
                }

                int newVersionNo = currentVersionNo + 1;
                LocalDateTime updatedAt = LocalDateTime.now();
                insertUpdatedQuestionVersion(
                        connection,
                        updatedByUserId,
                        questionId,
                        data,
                        newVersionNo,
                        updatedAt
                );
                insertAnswerOptions(connection, questionId, newVersionNo, data);
                insertIllustration(
                        connection, questionId, newVersionNo, data.illustration(),
                        updatedAt
                );
                updateCurrentQuestion(
                        connection,
                        questionId,
                        data,
                        currentVersionNo,
                        newVersionNo,
                        updatedAt
                );
                connection.commit();
                return newVersionNo;

            } catch (SQLException e) {
                transactionFailure = e;
                if (transactionStarted) {
                    rollbackWithSuppressed(connection, e);
                }
                throw new IllegalStateException("Failed to update question version", e);
            } catch (RuntimeException e) {
                transactionFailure = e;
                if (transactionStarted) {
                    rollbackWithSuppressed(connection, e);
                }
                throw e;
            } finally {
                restoreAutoCommit(connection, originalAutoCommit, transactionFailure);
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update question version", e);
        }
    }

    public boolean updateStatusForTeacher(int authenticatedUserId, int questionId,
                                          QuestionStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Question status is required");
        }

        try (Connection connection = databaseController.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean transactionStarted = false;
            Throwable transactionFailure = null;

            try {
                connection.setAutoCommit(false);
                transactionStarted = true;

                Optional<String> currentStatus = lockAssignedQuestionStatus(
                        connection,
                        authenticatedUserId,
                        questionId
                );
                if (currentStatus.isEmpty()) {
                    connection.commit();
                    return false;
                }
                if (status.name().equals(currentStatus.get())) {
                    connection.commit();
                    return true;
                }

                boolean updated = updateAssignedQuestionStatus(
                        connection,
                        authenticatedUserId,
                        questionId,
                        status
                );
                connection.commit();
                return updated;

            } catch (SQLException e) {
                transactionFailure = e;
                if (transactionStarted) {
                    rollbackWithSuppressed(connection, e);
                }
                throw new IllegalStateException("Failed to update question status", e);
            } catch (RuntimeException e) {
                transactionFailure = e;
                if (transactionStarted) {
                    rollbackWithSuppressed(connection, e);
                }
                throw e;
            } finally {
                restoreAutoCommit(connection, originalAutoCommit, transactionFailure);
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update question status", e);
        }
    }

    public List<QuestionVersionDTO> findVersionsForTeacher(int authenticatedUserId,
                                                            int questionId) {
        List<QuestionVersionDTO> versions = new ArrayList<>();

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     QUESTION_VERSION_HISTORY_SQL
             )) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, questionId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    versions.add(mapRowToQuestionVersionDto(resultSet));
                }
            }

            return versions;

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load question versions", e);
        }
    }

    public List<PrincipalQuestionDTO> findAllForPrincipal() {
        return loadPrincipalQuestions(PRINCIPAL_CURRENT_QUESTIONS_SQL);
    }

    public List<PrincipalQuestionDTO> findVersionsForPrincipal(int questionId) {
        requirePositiveId(questionId, "Question ID must be positive");
        return loadPrincipalQuestions(PRINCIPAL_QUESTION_VERSIONS_SQL, questionId);
    }

    public Optional<PrincipalQuestionDTO> findVersionForPrincipal(
            int questionId, int versionNo
    ) {
        requirePositiveId(questionId, "Question ID must be positive");
        requirePositiveId(versionNo, "Question version must be positive");
        List<PrincipalQuestionDTO> rows = loadPrincipalQuestions(
                PRINCIPAL_QUESTION_VERSION_SQL, questionId, versionNo
        );
        if (rows.size() > 1) {
            throw new IllegalArgumentException(
                    "Duplicate question version projection: " + questionId
            );
        }
        return rows.stream().findFirst();
    }

    private List<PrincipalQuestionDTO> loadPrincipalQuestions(
            String sql, int... parameters
    ) {
        List<PrincipalQuestionDTO> questions = new ArrayList<>();
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setInt(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    questions.add(mapPrincipalQuestion(resultSet));
                }
            }
            return List.copyOf(questions);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load Principal questions", exception);
        }
    }

    private PrincipalQuestionDTO mapPrincipalQuestion(ResultSet resultSet)
            throws SQLException {
        int questionId = resultSet.getInt("question_id");
        int correctOption = resultSet.getInt("correct_option_number");
        if (correctOption < 1 || correctOption > 4) {
            throw new IllegalArgumentException(
                    "Correct answer number is invalid for question: " + questionId
            );
        }
        return new PrincipalQuestionDTO(
                questionId, resultSet.getInt("version_no"),
                resultSet.getInt("course_id"), resultSet.getString("course_name"),
                resultSet.getInt("created_by_user_id"),
                resultSet.getString("creator_name"), resultSet.getString("content"),
                resultSet.getString("topic"),
                parsePersistedEnum(resultSet.getString("question_type"),
                        QuestionType.class, "Question type", questionId),
                parsePersistedEnum(resultSet.getString("difficulty"),
                        DifficultyLevel.class, "Question difficulty", questionId),
                parsePersistedEnum(resultSet.getString("status"),
                        QuestionStatus.class, "Question status", questionId),
                resultSet.getString("illustration_path"),
                List.of(resultSet.getString("answer_option_1"),
                        resultSet.getString("answer_option_2"),
                        resultSet.getString("answer_option_3"),
                        resultSet.getString("answer_option_4")),
                correctOption,
                resultSet.getObject("version_created_at", LocalDateTime.class),
                resultSet.getObject("question_updated_at", LocalDateTime.class),
                QuestionIllustrationJdbcSupport.readDto(resultSet)
        );
    }

    public List<QuestionDTO> findCurrentForTeacher(int authenticatedUserId,
                                                   QuestionFilterPayload filter) {
        StringBuilder sql = new StringBuilder(NORMALIZED_QUESTION_SELECT);
        sql.append("WHERE 1 = 1\n");
        appendFilters(sql, filter);
        sql.append("ORDER BY q.question_id");

        List<QuestionDTO> questions = new ArrayList<>();

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {

            statement.setInt(1, authenticatedUserId);
            bindFilters(statement, 2, filter);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    questions.add(mapRowToQuestionDto(resultSet));
                }
            }

            return questions;

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load assigned questions", e);
        }
    }

    public Optional<QuestionDTO> findCurrentByIdForTeacher(int authenticatedUserId,
                                                            int questionId) {
        String sql = NORMALIZED_QUESTION_SELECT
                + "WHERE q.question_id = ?\n"
                + "ORDER BY q.question_id";

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, questionId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRowToQuestionDto(resultSet));
                }

                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load assigned question by id", e);
        }
    }

    public Optional<Question> findCurrentEntityByIdForTeacher(
            int authenticatedUserId,
            int questionId
    ) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     CURRENT_QUESTION_ENTITY_SELECT
             )) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, questionId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapCurrentQuestionEntity(resultSet, questionId));
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to load assigned question by id",
                    e
            );
        }
    }

    public Optional<Question> findEntityVersionForTeacher(
            int authenticatedTeacherUserId,
            int questionId,
            int versionNo
    ) {
        requirePositiveId(authenticatedTeacherUserId, "Teacher user ID must be positive");
        requirePositiveId(questionId, "Question ID must be positive");
        requirePositiveId(versionNo, "Question version number must be positive");

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     EXACT_QUESTION_VERSION_ENTITY_SELECT
             )) {
            statement.setInt(1, authenticatedTeacherUserId);
            statement.setInt(2, versionNo);
            statement.setInt(3, questionId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapCurrentQuestionEntity(resultSet, questionId));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load assigned question version",
                    exception
            );
        }
    }

    public List<Question> findAll() {
        String sql = "SELECT " + QUESTION_COLUMNS + " FROM questions ORDER BY question_id";
        List<Question> questions = new ArrayList<>();

        try (Connection connection = databaseController.getConnection();
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

        try (Connection connection = databaseController.getConnection();
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

    private void appendFilters(StringBuilder sql, QuestionFilterPayload filter) {
        if (filter == null) {
            return;
        }

        if (filter.getCourseId() != null) {
            sql.append("AND q.course_id = ?\n");
        }
        if (filter.getSubjectId() != null) {
            sql.append("AND c.subject_id = ?\n");
        }
        if (normalizedTopic(filter) != null) {
            sql.append("AND LOWER(qv.topic) LIKE ?\n");
        }
        if (filter.getDifficulty() != null) {
            sql.append("AND qv.difficulty = ?\n");
        }
        if (filter.getStatus() != null) {
            sql.append("AND q.status = ?\n");
        }
    }

    private void bindFilters(PreparedStatement statement, int startIndex,
                             QuestionFilterPayload filter) throws SQLException {
        if (filter == null) {
            return;
        }

        int parameterIndex = startIndex;
        if (filter.getCourseId() != null) {
            statement.setInt(parameterIndex++, filter.getCourseId());
        }
        if (filter.getSubjectId() != null) {
            statement.setInt(parameterIndex++, filter.getSubjectId());
        }

        String topic = normalizedTopic(filter);
        if (topic != null) {
            statement.setString(parameterIndex++, "%" + topic + "%");
        }
        if (filter.getDifficulty() != null) {
            statement.setString(parameterIndex++, filter.getDifficulty().name());
        }
        if (filter.getStatus() != null) {
            statement.setString(parameterIndex, filter.getStatus().name());
        }
    }

    private String normalizedTopic(QuestionFilterPayload filter) {
        String topic = filter.getTopic();
        if (topic == null || topic.trim().isEmpty()) {
            return null;
        }
        return topic.trim().toLowerCase(Locale.ROOT);
    }

    private QuestionDTO mapRowToQuestionDto(ResultSet resultSet) throws SQLException {
        QuestionDTO question = buildQuestionDto(resultSet);
        question.setQuestionCode(resultSet.getString("question_code"));
        return question;
    }

    private QuestionDTO buildQuestionDto(ResultSet resultSet) throws SQLException {
        return new QuestionDTO(
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
                resultSet.getInt("correct_option_number"),
                resultSet.getInt("course_id"),
                resultSet.getInt("subject_id"),
                resultSet.getInt("version_no"),
                QuestionIllustrationJdbcSupport.readDto(resultSet)
        );
    }

    private Question mapCurrentQuestionEntity(ResultSet resultSet, int questionId)
            throws SQLException {
        Object versionValue = resultSet.getObject("version_no");
        if (versionValue == null) {
            throw new IllegalArgumentException(
                    "Current question version is missing: " + questionId
            );
        }

        int hydratedQuestionId = resultSet.getInt("question_id");
        String content = resultSet.getString("content");
        String topic = resultSet.getString("topic");
        QuestionType type = parsePersistedEnum(
                resultSet.getString("question_type"),
                QuestionType.class,
                "Question type",
                questionId
        );
        DifficultyLevel difficulty = parsePersistedEnum(
                resultSet.getString("difficulty"),
                DifficultyLevel.class,
                "Question difficulty",
                questionId
        );
        QuestionStatus status = parsePersistedEnum(
                resultSet.getString("status"),
                QuestionStatus.class,
                "Question status",
                questionId
        );
        String illustrationPath = resultSet.getString("illustration_path");
        QuestionIllustration illustration =
                QuestionIllustrationJdbcSupport.readEntity(resultSet);
        LocalDateTime createdAt = resultSet.getObject(
                "created_at",
                LocalDateTime.class
        );
        LocalDateTime updatedAt = resultSet.getObject(
                "updated_at",
                LocalDateTime.class
        );
        int correctOptionNumber = resultSet.getInt("correct_option_number");
        if (correctOptionNumber < 1 || correctOptionNumber > 4) {
            throw new IllegalArgumentException(
                    "Correct answer number is invalid for question: " + questionId
            );
        }

        List<AnswerOption> options = new ArrayList<>(4);
        do {
            Object optionNumberValue = resultSet.getObject("option_number");
            if (optionNumberValue != null) {
                int optionNumber = resultSet.getInt("option_number");
                options.add(new AnswerOption(
                        optionNumber,
                        resultSet.getString("option_text"),
                        optionNumber == correctOptionNumber
                ));
            }
        } while (resultSet.next());

        if (options.size() != 4) {
            throw new IllegalArgumentException(
                    "Question must contain exactly four answer options: "
                            + questionId
            );
        }

        return Question.rehydrate(
                hydratedQuestionId,
                content,
                type,
                difficulty,
                status,
                createdAt,
                updatedAt,
                topic,
                illustrationPath,
                illustration,
                options
        );
    }

    private static <E extends Enum<E>> E parsePersistedEnum(
            String value,
            Class<E> enumType,
            String label,
            int questionId
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    label + " is missing for question: " + questionId
            );
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    label + " is invalid for question: " + questionId,
                    exception
            );
        }
    }

    private static void requirePositiveId(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static QuestionPersistenceData persistenceData(Question question) {
        List<AnswerOption> options = question.getAnswerOptions();
        if (options.size() != 4) {
            throw new IllegalArgumentException(
                    "Question must contain exactly four answer options"
            );
        }

        String[] optionTexts = new String[4];
        for (AnswerOption option : options) {
            int optionNumber = option.getOptionId();
            if (optionTexts[optionNumber - 1] != null) {
                throw new IllegalArgumentException(
                        "Question contains a duplicate answer option: "
                                + optionNumber
                );
            }
            optionTexts[optionNumber - 1] = option.getOptionText();
        }
        for (String optionText : optionTexts) {
            if (optionText == null) {
                throw new IllegalArgumentException(
                        "Question must contain answer options 1 through 4"
                );
            }
        }

        int correctOptionNumber = question.getCorrectOptionNumber();
        if (correctOptionNumber < 1 || correctOptionNumber > 4) {
            throw new IllegalArgumentException(
                    "Question must have exactly one correct answer"
            );
        }

        return new QuestionPersistenceData(
                question.getContent(),
                question.getTopic(),
                question.getQuestionType().name(),
                question.getDifficultyLevel().name(),
                question.getQuestionStatus().name(),
                question.getIllustrationPath(),
                optionTexts[0],
                optionTexts[1],
                optionTexts[2],
                optionTexts[3],
                correctOptionNumber,
                question.getIllustration()
        );
    }

    private int insertCurrentQuestion(Connection connection, int createdByUserId,
                                      int courseId, QuestionPersistenceData data,
                                      LocalDateTime createdAt) throws SQLException {
        SQLException lastCollision = null;

        for (int attempt = 0; attempt < MAX_QUESTION_CODE_ATTEMPTS; attempt++) {
            try {
                return insertCurrentQuestion(
                        connection,
                        createdByUserId,
                        courseId,
                        data,
                        createdAt,
                        allocateQuestionCode(connection, courseId)
                );
            } catch (SQLException e) {
                if (!isQuestionCodeCollision(e)) {
                    throw e;
                }
                lastCollision = e;
            }
        }

        throw lastCollision;
    }

    /**
     * Builds the next five digit question identifier for a course: the lowest
     * unused question number in that course followed by the two digit course
     * number (requirements 33, 34).
     */
    private String allocateQuestionCode(Connection connection, int courseId)
            throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(NEXT_QUESTION_NUMBER_SQL)) {
            statement.setInt(1, courseId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Cannot build a question identifier: unknown course " + courseId
                    );
                }

                String courseNumber = resultSet.getString("course_number");
                if (!EncodedIdentifiers.isValidOrganisationNumber(courseNumber)) {
                    throw new IllegalStateException(
                            "Course " + courseId + " has no two digit course number,"
                                    + " so no question identifier can be built"
                    );
                }

                int nextQuestionNumber = resultSet.getInt("highest_question_number") + 1;
                if (nextQuestionNumber > EncodedIdentifiers.MAX_QUESTION_NUMBER) {
                    throw new IllegalStateException(
                            "Course " + courseId + " already holds "
                                    + EncodedIdentifiers.MAX_QUESTION_NUMBER
                                    + " questions; the three digit question number"
                                    + " is exhausted"
                    );
                }

                return EncodedIdentifiers.formatQuestionCode(nextQuestionNumber, courseNumber);
            }
        }
    }

    private static boolean isQuestionCodeCollision(SQLException exception) {
        String message = exception.getMessage();
        return exception.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR
                && "23000".equals(exception.getSQLState())
                && message != null
                && message.toLowerCase(Locale.ROOT).contains("uq_questions_question_code");
    }

    private int insertCurrentQuestion(Connection connection, int createdByUserId,
                                      int courseId, QuestionPersistenceData data,
                                      LocalDateTime createdAt, String questionCode)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                CREATE_QUESTION_SQL,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, data.content());
            statement.setString(2, data.topic());
            statement.setString(3, data.type());
            statement.setString(4, data.difficulty());
            statement.setString(5, data.status());
            statement.setString(6, data.illustrationPath());
            statement.setString(7, data.answerOption1());
            statement.setString(8, data.answerOption2());
            statement.setString(9, data.answerOption3());
            statement.setString(10, data.answerOption4());
            statement.setInt(11, data.correctOptionNumber());
            statement.setInt(12, courseId);
            statement.setInt(13, createdByUserId);
            statement.setInt(14, 1);
            statement.setObject(15, createdAt);
            statement.setObject(16, createdAt);
            statement.setString(17, questionCode);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Question insert did not affect exactly one row");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new SQLException("Question insert did not return a generated key");
                }

                int questionId = generatedKeys.getInt(1);
                if (questionId <= 0) {
                    throw new SQLException("Question insert returned an invalid generated key");
                }
                return questionId;
            }
        }
    }

    private void insertQuestionVersion(Connection connection, int questionId,
                                       int createdByUserId,
                                       QuestionPersistenceData data,
                                       LocalDateTime createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CREATE_QUESTION_VERSION_SQL)) {
            statement.setInt(1, questionId);
            statement.setInt(2, 1);
            statement.setString(3, data.content());
            statement.setString(4, data.topic());
            statement.setString(5, data.type());
            statement.setString(6, data.difficulty());
            statement.setString(7, data.illustrationPath());
            statement.setInt(8, data.correctOptionNumber());
            statement.setInt(9, createdByUserId);
            statement.setObject(10, createdAt);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Question version insert did not affect exactly one row");
            }
        }
    }

    private void insertAnswerOptions(Connection connection, int questionId,
                                     int versionNo, QuestionPersistenceData data)
            throws SQLException {
        String[] optionTexts = {
                data.answerOption1(),
                data.answerOption2(),
                data.answerOption3(),
                data.answerOption4()
        };

        try (PreparedStatement statement = connection.prepareStatement(CREATE_ANSWER_OPTION_SQL)) {
            for (int optionNumber = 1; optionNumber <= optionTexts.length; optionNumber++) {
                statement.setInt(1, questionId);
                statement.setInt(2, versionNo);
                statement.setInt(3, optionNumber);
                statement.setString(4, optionTexts[optionNumber - 1]);

                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Answer option insert did not affect exactly one row");
                }
            }
        }
    }

    private void insertIllustration(Connection connection, int questionId,
                                    int versionNo,
                                    QuestionIllustration illustration,
                                    LocalDateTime createdAt)
            throws SQLException {
        if (illustration == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                CREATE_ILLUSTRATION_SQL
        )) {
            statement.setInt(1, questionId);
            statement.setInt(2, versionNo);
            statement.setString(3, illustration.getMediaType());
            statement.setBytes(4, illustration.getContent());
            statement.setInt(5, illustration.getContent().length);
            statement.setInt(6, illustration.getWidth());
            statement.setInt(7, illustration.getHeight());
            statement.setString(8, illustration.getSha256());
            statement.setObject(9, createdAt);
            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "Question illustration insert did not affect exactly one row"
                );
            }
        }
    }

    private int lockCurrentVersion(Connection connection, int questionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_QUESTION_VERSION_SQL)) {
            statement.setInt(1, questionId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Question not found: " + questionId);
                }
                return resultSet.getInt("current_version_no");
            }
        }
    }

    private void insertUpdatedQuestionVersion(Connection connection,
                                              int updatedByUserId,
                                              int questionId,
                                              QuestionPersistenceData data,
                                              int newVersionNo,
                                              LocalDateTime updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CREATE_QUESTION_VERSION_SQL)) {
            statement.setInt(1, questionId);
            statement.setInt(2, newVersionNo);
            statement.setString(3, data.content());
            statement.setString(4, data.topic());
            statement.setString(5, data.type());
            statement.setString(6, data.difficulty());
            statement.setString(7, data.illustrationPath());
            statement.setInt(8, data.correctOptionNumber());
            statement.setInt(9, updatedByUserId);
            statement.setObject(10, updatedAt);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Question version insert did not affect exactly one row");
            }
        }
    }

    private void updateCurrentQuestion(Connection connection, int questionId,
                                       QuestionPersistenceData data,
                                       int currentVersionNo, int newVersionNo,
                                       LocalDateTime updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_CURRENT_QUESTION_SQL)) {
            statement.setString(1, data.content());
            statement.setString(2, data.topic());
            statement.setString(3, data.type());
            statement.setString(4, data.difficulty());
            statement.setString(5, data.illustrationPath());
            statement.setString(6, data.answerOption1());
            statement.setString(7, data.answerOption2());
            statement.setString(8, data.answerOption3());
            statement.setString(9, data.answerOption4());
            statement.setInt(10, data.correctOptionNumber());
            statement.setInt(11, newVersionNo);
            statement.setObject(12, updatedAt);
            statement.setInt(13, questionId);
            statement.setInt(14, currentVersionNo);

            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Question version conflict");
            }
        }
    }

    private Optional<String> lockAssignedQuestionStatus(Connection connection,
                                                        int authenticatedUserId,
                                                        int questionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_ASSIGNED_QUESTION_STATUS_SQL
        )) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, questionId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(resultSet.getString("status"));
            }
        }
    }

    private boolean updateAssignedQuestionStatus(Connection connection,
                                                 int authenticatedUserId,
                                                 int questionId,
                                                 QuestionStatus status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                UPDATE_ASSIGNED_QUESTION_STATUS_SQL
        )) {
            statement.setString(1, status.name());
            statement.setInt(2, questionId);
            statement.setInt(3, authenticatedUserId);
            return statement.executeUpdate() == 1;
        }
    }

    private QuestionVersionDTO mapRowToQuestionVersionDto(ResultSet resultSet)
            throws SQLException {
        return new QuestionVersionDTO(
                resultSet.getInt("question_id"),
                resultSet.getInt("version_no"),
                resultSet.getInt("course_id"),
                resultSet.getString("content"),
                resultSet.getString("topic"),
                QuestionType.valueOf(resultSet.getString("question_type")),
                DifficultyLevel.valueOf(resultSet.getString("difficulty")),
                resultSet.getString("illustration_path"),
                resultSet.getString("answer_option_1"),
                resultSet.getString("answer_option_2"),
                resultSet.getString("answer_option_3"),
                resultSet.getString("answer_option_4"),
                resultSet.getInt("correct_option_number"),
                resultSet.getInt("created_by_user_id"),
                resultSet.getObject("created_at", LocalDateTime.class),
                QuestionIllustrationJdbcSupport.readDto(resultSet)
        );
    }

    private void rollbackWithSuppressed(Connection connection, Throwable originalFailure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    private void restoreAutoCommit(Connection connection, boolean originalAutoCommit,
                                   Throwable originalFailure) throws SQLException {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException restorationFailure) {
            if (originalFailure != null) {
                originalFailure.addSuppressed(restorationFailure);
                return;
            }
            throw restorationFailure;
        }
    }

    private record QuestionPersistenceData(
            String content,
            String topic,
            String type,
            String difficulty,
            String status,
            String illustrationPath,
            String answerOption1,
            String answerOption2,
            String answerOption3,
            String answerOption4,
            int correctOptionNumber,
            QuestionIllustration illustration
    ) {
    }
}
