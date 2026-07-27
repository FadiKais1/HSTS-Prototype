package hsts.server.repository;

import hsts.common.CreateExamPayload;
import hsts.common.ExamDTO;
import hsts.common.ExamQuestionDTO;
import hsts.common.ExamQuestionSelectionPayload;
import hsts.common.ExamSummaryDTO;
import hsts.common.type.ExamStatus;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public class ExamRepository {
    private static final String EXAM_CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int EXAM_CODE_LENGTH = 6;
    private static final int MAX_EXAM_CODE_ATTEMPTS = 5;
    private static final int MYSQL_DUPLICATE_KEY_ERROR = 1062;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String EXAM_SUMMARY_SELECT = """
            SELECT e.exam_id,
                   e.exam_code,
                   e.course_id,
                   c.name AS course_name,
                   s.subject_id,
                   s.name AS subject_name,
                   e.created_by_user_id,
                   creator.full_name AS creator_name,
                   ev.version_no,
                   ev.title,
                   ev.duration_minutes,
                   ev.total_score,
                   ev.status,
                   ev.created_at,
                   ev.submitted_at,
                   ev.reviewed_at,
                   ev.rejection_reason
            FROM exams e
            JOIN exam_versions ev
              ON ev.exam_id = e.exam_id
             AND ev.version_no = e.current_version_no
            JOIN courses c ON c.course_id = e.course_id
            JOIN subjects s ON s.subject_id = c.subject_id
            JOIN users creator ON creator.user_id = e.created_by_user_id
            """;

    private static final String TEACHER_EXAM_LIST_SQL = EXAM_SUMMARY_SELECT + """
            JOIN teacher_courses tc
              ON tc.course_id = e.course_id
             AND tc.teacher_user_id = ?
            WHERE e.created_by_user_id = ?
            ORDER BY e.created_at DESC, e.exam_id DESC
            """;

    private static final String COORDINATOR_PENDING_LIST_SQL = EXAM_SUMMARY_SELECT + """
            JOIN subject_coordinators sc
              ON sc.subject_id = s.subject_id
             AND sc.coordinator_user_id = ?
            WHERE ev.status = 'PENDING_APPROVAL'
            ORDER BY ev.submitted_at ASC, e.exam_id ASC
            """;

    private static final String EXAM_DETAIL_SELECT = """
            SELECT e.exam_id,
                   e.exam_code,
                   e.course_id,
                   c.name AS course_name,
                   s.subject_id,
                   s.name AS subject_name,
                   e.created_by_user_id,
                   creator.full_name AS creator_name,
                   ev.version_no,
                   ev.title,
                   ev.duration_minutes,
                   ev.teacher_notes,
                   ev.student_instructions,
                   ev.total_score,
                   ev.status,
                   ev.created_at,
                   ev.submitted_at,
                   ev.reviewed_by_user_id,
                   reviewer.full_name AS reviewer_name,
                   ev.reviewed_at,
                   ev.rejection_reason
            FROM exams e
            JOIN exam_versions ev
              ON ev.exam_id = e.exam_id
             AND ev.version_no = e.current_version_no
            JOIN courses c ON c.course_id = e.course_id
            JOIN subjects s ON s.subject_id = c.subject_id
            JOIN users creator ON creator.user_id = e.created_by_user_id
            LEFT JOIN users reviewer ON reviewer.user_id = ev.reviewed_by_user_id
            """;

    private static final String TEACHER_EXAM_BY_ID_SQL = EXAM_DETAIL_SELECT + """
            JOIN teacher_courses tc
              ON tc.course_id = e.course_id
             AND tc.teacher_user_id = ?
            WHERE e.exam_id = ?
              AND e.created_by_user_id = ?
            """;

    private static final String COORDINATOR_EXAM_BY_ID_SQL = EXAM_DETAIL_SELECT + """
            JOIN subject_coordinators sc
              ON sc.subject_id = s.subject_id
             AND sc.coordinator_user_id = ?
            WHERE e.exam_id = ?
            """;

    private static final String EXAM_QUESTION_SNAPSHOTS_SQL = """
            SELECT evq.question_id,
                   evq.question_version_no,
                   evq.order_number,
                   evq.score,
                   qv.content,
                   qv.topic,
                   qv.difficulty,
                   qv.illustration_path,
                   option_1.option_text AS answer_option_1,
                   option_2.option_text AS answer_option_2,
                   option_3.option_text AS answer_option_3,
                   option_4.option_text AS answer_option_4,
                   qv.correct_option_number
            FROM exam_version_questions evq
            JOIN question_versions qv
              ON qv.question_id = evq.question_id
             AND qv.version_no = evq.question_version_no
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
            WHERE evq.exam_id = ?
              AND evq.exam_version_no = ?
            ORDER BY evq.order_number ASC
            """;

    private static final String LOCK_ASSIGNED_COURSE_SQL = """
            SELECT c.course_id
            FROM courses c
            JOIN teacher_courses tc
              ON tc.course_id = c.course_id
             AND tc.teacher_user_id = ?
            WHERE c.course_id = ?
            FOR UPDATE
            """;

    private static final String LOCK_AVAILABLE_QUESTION_SQL = """
            SELECT q.question_id
            FROM questions q
            JOIN teacher_courses tc
              ON tc.course_id = q.course_id
             AND tc.teacher_user_id = ?
            JOIN question_versions qv
              ON qv.question_id = q.question_id
             AND qv.version_no = ?
            WHERE q.question_id = ?
              AND q.course_id = ?
              AND q.status = 'ACTIVE'
              AND q.current_version_no = ?
            FOR UPDATE
            """;

    private static final String INSERT_EXAM_SQL = """
            INSERT INTO exams (
                exam_code,
                course_id,
                created_by_user_id,
                current_version_no
            )
            VALUES (?, ?, ?, ?)
            """;

    private static final String INSERT_EXAM_VERSION_SQL = """
            INSERT INTO exam_versions (
                exam_id,
                version_no,
                title,
                duration_minutes,
                teacher_notes,
                student_instructions,
                total_score,
                status,
                version_created_by_user_id,
                created_at,
                submitted_at,
                reviewed_by_user_id,
                reviewed_at,
                rejection_reason
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_EXAM_QUESTION_SQL = """
            INSERT INTO exam_version_questions (
                exam_id,
                exam_version_no,
                order_number,
                question_id,
                question_version_no,
                score
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_CURRENT_VERSION_SQL = """
            UPDATE exams
            SET current_version_no = 1
            WHERE exam_id = ?
              AND current_version_no IS NULL
            """;

    private final DatabaseController databaseController;
    private final Supplier<String> examCodeGenerator;

    public ExamRepository() {
        this(new DatabaseController());
    }

    public ExamRepository(DatabaseController databaseController) {
        this(databaseController, ExamRepository::generateExamCode);
    }

    ExamRepository(DatabaseController databaseController, Supplier<String> examCodeGenerator) {
        this.databaseController = databaseController;
        this.examCodeGenerator = examCodeGenerator;
    }

    public List<ExamSummaryDTO> findCreatedByTeacher(int authenticatedUserId) {
        List<ExamSummaryDTO> exams = new ArrayList<>();

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(TEACHER_EXAM_LIST_SQL)) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, authenticatedUserId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    exams.add(mapExamSummary(resultSet));
                }
            }
            return exams;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load teacher exams", e);
        }
    }

    public List<ExamSummaryDTO> findPendingForCoordinator(int authenticatedUserId) {
        List<ExamSummaryDTO> exams = new ArrayList<>();

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     COORDINATOR_PENDING_LIST_SQL
             )) {
            statement.setInt(1, authenticatedUserId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    exams.add(mapExamSummary(resultSet));
                }
            }
            return exams;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load pending exams", e);
        }
    }

    public Optional<ExamDTO> findByIdForTeacher(int authenticatedUserId, int examId) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(TEACHER_EXAM_BY_ID_SQL)) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, examId);
            statement.setInt(3, authenticatedUserId);
            return loadExam(connection, statement);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load teacher exam", e);
        }
    }

    public Optional<ExamDTO> findByIdForCoordinator(int authenticatedUserId, int examId) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     COORDINATOR_EXAM_BY_ID_SQL
             )) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, examId);
            return loadExam(connection, statement);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load coordinator exam", e);
        }
    }

    public int create(int authenticatedUserId, CreateExamPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Exam creation data is missing");
        }

        try (Connection connection = databaseController.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean transactionStarted = false;
            Throwable transactionFailure = null;

            try {
                connection.setAutoCommit(false);
                transactionStarted = true;

                requireAssignedCourse(connection, authenticatedUserId, payload.getCourseId());
                for (ExamQuestionSelectionPayload selection : payload.getQuestions()) {
                    requireAvailableQuestion(
                            connection,
                            authenticatedUserId,
                            payload.getCourseId(),
                            selection
                    );
                }

                BigDecimal totalScore = calculateTotalScore(payload.getQuestions());
                int examId = insertExamWithUniqueCode(
                        connection,
                        authenticatedUserId,
                        payload.getCourseId()
                );
                LocalDateTime createdAt = LocalDateTime.now();
                insertExamVersion(
                        connection,
                        examId,
                        authenticatedUserId,
                        payload,
                        totalScore,
                        createdAt
                );
                insertExamQuestions(connection, examId, payload.getQuestions());
                updateCurrentVersion(connection, examId);
                connection.commit();
                return examId;
            } catch (SQLException e) {
                transactionFailure = e;
                if (transactionStarted) {
                    rollbackWithSuppressed(connection, e);
                }
                throw new IllegalStateException("Failed to create exam", e);
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
            throw new IllegalStateException("Failed to create exam", e);
        }
    }

    private Optional<ExamDTO> loadExam(Connection connection, PreparedStatement statement)
            throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Optional.empty();
            }

            int examId = resultSet.getInt("exam_id");
            int versionNo = resultSet.getInt("version_no");
            List<ExamQuestionDTO> questions = loadExamQuestions(
                    connection,
                    examId,
                    versionNo
            );
            return Optional.of(mapExam(resultSet, questions));
        }
    }

    private List<ExamQuestionDTO> loadExamQuestions(Connection connection, int examId,
                                                    int versionNo) throws SQLException {
        List<ExamQuestionDTO> questions = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(
                EXAM_QUESTION_SNAPSHOTS_SQL
        )) {
            statement.setInt(1, examId);
            statement.setInt(2, versionNo);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    questions.add(mapExamQuestion(resultSet));
                }
            }
        }
        return questions;
    }

    private ExamSummaryDTO mapExamSummary(ResultSet resultSet) throws SQLException {
        return new ExamSummaryDTO(
                resultSet.getInt("exam_id"),
                resultSet.getString("exam_code"),
                resultSet.getInt("course_id"),
                resultSet.getString("course_name"),
                resultSet.getInt("subject_id"),
                resultSet.getString("subject_name"),
                resultSet.getInt("created_by_user_id"),
                resultSet.getString("creator_name"),
                resultSet.getInt("version_no"),
                resultSet.getString("title"),
                resultSet.getInt("duration_minutes"),
                resultSet.getBigDecimal("total_score").doubleValue(),
                ExamStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_at", LocalDateTime.class),
                resultSet.getObject("submitted_at", LocalDateTime.class),
                resultSet.getObject("reviewed_at", LocalDateTime.class),
                resultSet.getString("rejection_reason")
        );
    }

    private ExamDTO mapExam(ResultSet resultSet, List<ExamQuestionDTO> questions)
            throws SQLException {
        return new ExamDTO(
                resultSet.getInt("exam_id"),
                resultSet.getString("exam_code"),
                resultSet.getInt("course_id"),
                resultSet.getString("course_name"),
                resultSet.getInt("subject_id"),
                resultSet.getString("subject_name"),
                resultSet.getInt("created_by_user_id"),
                resultSet.getString("creator_name"),
                resultSet.getInt("version_no"),
                resultSet.getString("title"),
                resultSet.getInt("duration_minutes"),
                resultSet.getString("teacher_notes"),
                resultSet.getString("student_instructions"),
                resultSet.getBigDecimal("total_score").doubleValue(),
                ExamStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_at", LocalDateTime.class),
                resultSet.getObject("submitted_at", LocalDateTime.class),
                resultSet.getObject("reviewed_by_user_id", Integer.class),
                resultSet.getString("reviewer_name"),
                resultSet.getObject("reviewed_at", LocalDateTime.class),
                resultSet.getString("rejection_reason"),
                questions
        );
    }

    private ExamQuestionDTO mapExamQuestion(ResultSet resultSet) throws SQLException {
        return new ExamQuestionDTO(
                resultSet.getInt("question_id"),
                resultSet.getInt("question_version_no"),
                resultSet.getInt("order_number"),
                resultSet.getBigDecimal("score").doubleValue(),
                resultSet.getString("content"),
                resultSet.getString("topic"),
                resultSet.getString("difficulty"),
                resultSet.getString("illustration_path"),
                resultSet.getString("answer_option_1"),
                resultSet.getString("answer_option_2"),
                resultSet.getString("answer_option_3"),
                resultSet.getString("answer_option_4"),
                resultSet.getInt("correct_option_number")
        );
    }

    private void requireAssignedCourse(Connection connection, int authenticatedUserId,
                                       int courseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_ASSIGNED_COURSE_SQL
        )) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, courseId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Course is not assigned to user: " + courseId
                    );
                }
            }
        }
    }

    private void requireAvailableQuestion(Connection connection, int authenticatedUserId,
                                          int courseId,
                                          ExamQuestionSelectionPayload selection)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_AVAILABLE_QUESTION_SQL
        )) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, selection.getQuestionVersionNo());
            statement.setInt(3, selection.getQuestionId());
            statement.setInt(4, courseId);
            statement.setInt(5, selection.getQuestionVersionNo());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Question unavailable for exam: " + selection.getQuestionId()
                    );
                }
            }
        }
    }

    private BigDecimal calculateTotalScore(List<ExamQuestionSelectionPayload> questions) {
        BigDecimal totalScore = BigDecimal.ZERO;
        for (ExamQuestionSelectionPayload question : questions) {
            totalScore = totalScore.add(BigDecimal.valueOf(question.getScore()));
        }
        return totalScore;
    }

    private int insertExamWithUniqueCode(Connection connection, int authenticatedUserId,
                                         int courseId) throws SQLException {
        SQLException lastCollision = null;

        for (int attempt = 0; attempt < MAX_EXAM_CODE_ATTEMPTS; attempt++) {
            try {
                return insertStableExam(
                        connection,
                        examCodeGenerator.get(),
                        authenticatedUserId,
                        courseId
                );
            } catch (SQLException e) {
                if (!isExamCodeCollision(e)) {
                    throw e;
                }
                lastCollision = e;
            }
        }

        throw lastCollision;
    }

    private int insertStableExam(Connection connection, String examCode,
                                 int authenticatedUserId, int courseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EXAM_SQL,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, examCode);
            statement.setInt(2, courseId);
            statement.setInt(3, authenticatedUserId);
            statement.setNull(4, Types.INTEGER);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Exam insert did not affect exactly one row");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new SQLException("Exam insert did not return a generated key");
                }

                int examId = generatedKeys.getInt(1);
                if (examId <= 0) {
                    throw new SQLException("Exam insert returned an invalid generated key");
                }
                return examId;
            }
        }
    }

    private void insertExamVersion(Connection connection, int examId,
                                   int authenticatedUserId, CreateExamPayload payload,
                                   BigDecimal totalScore, LocalDateTime createdAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EXAM_VERSION_SQL
        )) {
            statement.setInt(1, examId);
            statement.setInt(2, 1);
            statement.setString(3, payload.getTitle());
            statement.setInt(4, payload.getDurationMinutes());
            statement.setString(5, payload.getTeacherNotes());
            statement.setString(6, payload.getStudentInstructions());
            statement.setBigDecimal(7, totalScore);
            statement.setString(8, ExamStatus.DRAFT.name());
            statement.setInt(9, authenticatedUserId);
            statement.setObject(10, createdAt);
            statement.setNull(11, Types.TIMESTAMP);
            statement.setNull(12, Types.INTEGER);
            statement.setNull(13, Types.TIMESTAMP);
            statement.setNull(14, Types.VARCHAR);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Exam version insert did not affect exactly one row");
            }
        }
    }

    private void insertExamQuestions(Connection connection, int examId,
                                     List<ExamQuestionSelectionPayload> questions)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EXAM_QUESTION_SQL
        )) {
            for (ExamQuestionSelectionPayload question : questions) {
                statement.setInt(1, examId);
                statement.setInt(2, 1);
                statement.setInt(3, question.getOrderNumber());
                statement.setInt(4, question.getQuestionId());
                statement.setInt(5, question.getQuestionVersionNo());
                statement.setBigDecimal(6, BigDecimal.valueOf(question.getScore()));

                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Exam question insert did not affect exactly one row"
                    );
                }
            }
        }
    }

    private void updateCurrentVersion(Connection connection, int examId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                UPDATE_CURRENT_VERSION_SQL
        )) {
            statement.setInt(1, examId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Exam current version update did not affect exactly one row");
            }
        }
    }

    private boolean isExamCodeCollision(SQLException exception) {
        String message = exception.getMessage();
        return exception.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR
                && "23000".equals(exception.getSQLState())
                && message != null
                && message.toLowerCase(Locale.ROOT).contains("uq_exams_exam_code");
    }

    private static String generateExamCode() {
        StringBuilder code = new StringBuilder(EXAM_CODE_LENGTH);
        for (int index = 0; index < EXAM_CODE_LENGTH; index++) {
            code.append(EXAM_CODE_CHARACTERS.charAt(
                    SECURE_RANDOM.nextInt(EXAM_CODE_CHARACTERS.length())
            ));
        }
        return code.toString();
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
