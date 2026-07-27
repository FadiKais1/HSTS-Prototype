package hsts.server.repository;

import hsts.common.CreateQuestionPayload;
import hsts.common.QuestionDTO;
import hsts.common.QuestionFilterPayload;
import hsts.common.QuestionVersionDTO;
import hsts.common.UpdateQuestionPayload;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import hsts.server.entity.Question;

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
    private static final String QUESTION_COLUMNS = """
            question_id, content, topic, type, difficulty, status, illustration_path,
            answer_option_1, answer_option_2, answer_option_3, answer_option_4, correct_option_number
            """;

    private final DatabaseController databaseController;

    private static final String NORMALIZED_QUESTION_SELECT = """
            SELECT q.question_id,
                   q.course_id,
                   c.subject_id,
                   qv.version_no,
                   qv.content,
                   qv.topic,
                   qv.question_type AS type,
                   qv.difficulty,
                   q.status,
                   qv.illustration_path,
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
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                   option_1.option_text AS answer_option_1,
                   option_2.option_text AS answer_option_2,
                   option_3.option_text AS answer_option_3,
                   option_4.option_text AS answer_option_4,
                   qv.correct_option_number,
                   qv.created_by_user_id,
                   qv.created_at
            FROM question_versions qv
            JOIN questions q ON q.question_id = qv.question_id
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

    public QuestionRepository() {
        this(new DatabaseController());
    }

    public QuestionRepository(DatabaseController databaseController) {
        this.databaseController = databaseController;
    }

    public int create(int createdByUserId, CreateQuestionPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Question data is required");
        }
        if (payload.getDifficulty() == null) {
            throw new IllegalArgumentException("Question difficulty is required");
        }

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
                        payload,
                        createdAt
                );
                insertQuestionVersion(connection, questionId, createdByUserId, payload, createdAt);
                insertAnswerOptions(connection, questionId, payload);
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

    public int updateWithNewVersion(int updatedByUserId, UpdateQuestionPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Question update data is required");
        }

        try (Connection connection = databaseController.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean transactionStarted = false;
            Throwable transactionFailure = null;

            try {
                connection.setAutoCommit(false);
                transactionStarted = true;

                int currentVersionNo = lockCurrentVersion(connection, payload.getQuestionId());
                if (payload.getExpectedVersionNo() > 0
                        && payload.getExpectedVersionNo() != currentVersionNo) {
                    throw new IllegalStateException("Question version conflict");
                }

                int newVersionNo = currentVersionNo + 1;
                LocalDateTime updatedAt = LocalDateTime.now();
                insertUpdatedQuestionVersion(
                        connection,
                        updatedByUserId,
                        payload,
                        newVersionNo,
                        updatedAt
                );
                insertUpdatedAnswerOptions(connection, payload, newVersionNo);
                updateCurrentQuestion(
                        connection,
                        payload,
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

    public boolean updateQuestion(Question question) {
        String sql = """
                UPDATE questions
                SET content = ?,
                    topic = ?,
                    type = 'MULTIPLE_CHOICE',
                    difficulty = ?,
                    status = ?,
                    illustration_path = ?,
                    answer_option_1 = ?,
                    answer_option_2 = ?,
                    answer_option_3 = ?,
                    answer_option_4 = ?,
                    correct_option_number = ?
                WHERE question_id = ?
                """;

        try (Connection connection = databaseController.getConnection();
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

        try (Connection connection = databaseController.getConnection();
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
                resultSet.getInt("version_no")
        );
    }

    private int insertCurrentQuestion(Connection connection, int createdByUserId,
                                      CreateQuestionPayload payload,
                                      LocalDateTime createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                CREATE_QUESTION_SQL,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, payload.getContent());
            statement.setString(2, payload.getTopic());
            statement.setString(3, "MULTIPLE_CHOICE");
            statement.setString(4, payload.getDifficulty().name());
            statement.setString(5, "ACTIVE");
            statement.setString(6, payload.getIllustrationPath());
            statement.setString(7, payload.getAnswerOption1());
            statement.setString(8, payload.getAnswerOption2());
            statement.setString(9, payload.getAnswerOption3());
            statement.setString(10, payload.getAnswerOption4());
            statement.setInt(11, payload.getCorrectOptionNumber());
            statement.setInt(12, payload.getCourseId());
            statement.setInt(13, createdByUserId);
            statement.setInt(14, 1);
            statement.setObject(15, createdAt);
            statement.setObject(16, createdAt);

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
                                       int createdByUserId, CreateQuestionPayload payload,
                                       LocalDateTime createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CREATE_QUESTION_VERSION_SQL)) {
            statement.setInt(1, questionId);
            statement.setInt(2, 1);
            statement.setString(3, payload.getContent());
            statement.setString(4, payload.getTopic());
            statement.setString(5, "MULTIPLE_CHOICE");
            statement.setString(6, payload.getDifficulty().name());
            statement.setString(7, payload.getIllustrationPath());
            statement.setInt(8, payload.getCorrectOptionNumber());
            statement.setInt(9, createdByUserId);
            statement.setObject(10, createdAt);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Question version insert did not affect exactly one row");
            }
        }
    }

    private void insertAnswerOptions(Connection connection, int questionId,
                                     CreateQuestionPayload payload) throws SQLException {
        String[] optionTexts = {
                payload.getAnswerOption1(),
                payload.getAnswerOption2(),
                payload.getAnswerOption3(),
                payload.getAnswerOption4()
        };

        try (PreparedStatement statement = connection.prepareStatement(CREATE_ANSWER_OPTION_SQL)) {
            for (int optionNumber = 1; optionNumber <= optionTexts.length; optionNumber++) {
                statement.setInt(1, questionId);
                statement.setInt(2, 1);
                statement.setInt(3, optionNumber);
                statement.setString(4, optionTexts[optionNumber - 1]);

                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Answer option insert did not affect exactly one row");
                }
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

    private void insertUpdatedQuestionVersion(Connection connection, int updatedByUserId,
                                              UpdateQuestionPayload payload, int newVersionNo,
                                              LocalDateTime updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CREATE_QUESTION_VERSION_SQL)) {
            statement.setInt(1, payload.getQuestionId());
            statement.setInt(2, newVersionNo);
            statement.setString(3, payload.getContent());
            statement.setString(4, payload.getTopic());
            statement.setString(5, "MULTIPLE_CHOICE");
            statement.setString(6, payload.getDifficulty());
            statement.setString(7, payload.getIllustrationPath());
            statement.setInt(8, payload.getCorrectOptionNumber());
            statement.setInt(9, updatedByUserId);
            statement.setObject(10, updatedAt);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Question version insert did not affect exactly one row");
            }
        }
    }

    private void insertUpdatedAnswerOptions(Connection connection, UpdateQuestionPayload payload,
                                            int newVersionNo) throws SQLException {
        String[] optionTexts = {
                payload.getAnswerOption1(),
                payload.getAnswerOption2(),
                payload.getAnswerOption3(),
                payload.getAnswerOption4()
        };

        try (PreparedStatement statement = connection.prepareStatement(CREATE_ANSWER_OPTION_SQL)) {
            for (int optionNumber = 1; optionNumber <= optionTexts.length; optionNumber++) {
                statement.setInt(1, payload.getQuestionId());
                statement.setInt(2, newVersionNo);
                statement.setInt(3, optionNumber);
                statement.setString(4, optionTexts[optionNumber - 1]);

                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Answer option insert did not affect exactly one row");
                }
            }
        }
    }

    private void updateCurrentQuestion(Connection connection, UpdateQuestionPayload payload,
                                       int currentVersionNo, int newVersionNo,
                                       LocalDateTime updatedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_CURRENT_QUESTION_SQL)) {
            statement.setString(1, payload.getContent());
            statement.setString(2, payload.getTopic());
            statement.setString(3, "MULTIPLE_CHOICE");
            statement.setString(4, payload.getDifficulty());
            statement.setString(5, payload.getIllustrationPath());
            statement.setString(6, payload.getAnswerOption1());
            statement.setString(7, payload.getAnswerOption2());
            statement.setString(8, payload.getAnswerOption3());
            statement.setString(9, payload.getAnswerOption4());
            statement.setInt(10, payload.getCorrectOptionNumber());
            statement.setInt(11, newVersionNo);
            statement.setObject(12, updatedAt);
            statement.setInt(13, payload.getQuestionId());
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
                resultSet.getObject("created_at", LocalDateTime.class)
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
}
