package hsts.server.repository;

import hsts.common.ExamDTO;
import hsts.common.ExamQuestionDTO;
import hsts.common.ExamSummaryDTO;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.ExamStatus;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import hsts.server.entity.AnswerOption;
import hsts.server.entity.Exam;
import hsts.server.entity.ExamQuestion;
import hsts.server.entity.Question;

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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

    private static final String EXAM_ENTITY_COLUMNS = """
            SELECT e.exam_id,
                   e.exam_code,
                   e.course_id,
                   e.created_by_user_id,
                   e.created_at AS exam_created_at,
                   e.updated_at AS exam_updated_at,
                   ev.version_no,
                   ev.title,
                   ev.duration_minutes,
                   ev.teacher_notes,
                   ev.student_instructions,
                   ev.total_score,
                   ev.status,
                   ev.created_at AS version_created_at,
                   ev.submitted_at,
                   ev.reviewed_by_user_id,
                   ev.reviewed_at,
                   ev.rejection_reason
            """;

    private static final String EXAM_ENTITY_SELECT = EXAM_ENTITY_COLUMNS + """
            FROM exams e
            JOIN courses c ON c.course_id = e.course_id
            LEFT JOIN exam_versions ev
              ON ev.exam_id = e.exam_id
             AND ev.version_no = e.current_version_no
            """;

    private static final String EXACT_EXAM_ENTITY_VERSION_SQL = EXAM_ENTITY_COLUMNS + """
            FROM exams e
            JOIN exam_versions ev
              ON ev.exam_id = e.exam_id
            WHERE e.exam_id = ?
              AND ev.version_no = ?
            """;

    private static final String TEACHER_EXAM_ENTITY_BY_ID_SQL = EXAM_ENTITY_SELECT + """
            JOIN teacher_courses tc
              ON tc.course_id = e.course_id
             AND tc.teacher_user_id = ?
            WHERE e.exam_id = ?
              AND e.created_by_user_id = ?
            """;

    private static final String COORDINATOR_EXAM_ENTITY_BY_ID_SQL = EXAM_ENTITY_SELECT + """
            JOIN subject_coordinators sc
              ON sc.subject_id = c.subject_id
             AND sc.coordinator_user_id = ?
            WHERE e.exam_id = ?
            """;

    private static final String EXAM_ENTITY_QUESTION_SNAPSHOTS_SQL = """
            SELECT evq.question_id,
                   evq.question_version_no,
                   evq.order_number,
                   evq.score,
                   qv.question_id AS snapshot_question_id,
                   qv.version_no AS snapshot_version_no,
                   qv.content,
                   qv.topic,
                   qv.question_type,
                   qv.difficulty,
                   qv.illustration_path,
                   qv.correct_option_number,
                   qv.created_at AS question_version_created_at,
                   q.status AS question_status,
                   ao.option_number,
                   ao.option_text
            FROM exam_version_questions evq
            LEFT JOIN question_versions qv
              ON qv.question_id = evq.question_id
             AND qv.version_no = evq.question_version_no
            LEFT JOIN questions q
              ON q.question_id = evq.question_id
            LEFT JOIN answer_options ao
              ON ao.question_id = evq.question_id
             AND ao.version_no = evq.question_version_no
            WHERE evq.exam_id = ?
              AND evq.exam_version_no = ?
            ORDER BY evq.order_number ASC, ao.option_number ASC
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

    private static final String LOCK_TEACHER_CURRENT_EXAM_SQL = """
            SELECT e.course_id,
                   e.current_version_no,
                   ev.status
            FROM exams e
            JOIN teacher_courses tc
              ON tc.course_id = e.course_id
             AND tc.teacher_user_id = ?
            JOIN exam_versions ev
              ON ev.exam_id = e.exam_id
             AND ev.version_no = e.current_version_no
            WHERE e.exam_id = ?
              AND e.created_by_user_id = ?
            FOR UPDATE
            """;

    private static final String LOCK_COORDINATOR_CURRENT_EXAM_SQL = """
            SELECT e.course_id,
                   e.current_version_no,
                   ev.status
            FROM exams e
            JOIN courses c ON c.course_id = e.course_id
            JOIN subject_coordinators sc
              ON sc.subject_id = c.subject_id
             AND sc.coordinator_user_id = ?
            JOIN exam_versions ev
              ON ev.exam_id = e.exam_id
             AND ev.version_no = e.current_version_no
            WHERE e.exam_id = ?
            FOR UPDATE
            """;

    private static final String UPDATE_CURRENT_VERSION_AFTER_EDIT_SQL = """
            UPDATE exams
            SET current_version_no = ?
            WHERE exam_id = ?
              AND current_version_no = ?
            """;

    private static final String SUBMIT_EXAM_VERSION_SQL = """
            UPDATE exam_versions
            SET status = ?,
                submitted_at = ?,
                reviewed_by_user_id = NULL,
                reviewed_at = NULL,
                rejection_reason = NULL
            WHERE exam_id = ?
              AND version_no = ?
              AND status = 'DRAFT'
            """;

    private static final String APPROVE_EXAM_VERSION_SQL = """
            UPDATE exam_versions
            SET status = ?,
                reviewed_by_user_id = ?,
                reviewed_at = ?,
                rejection_reason = NULL
            WHERE exam_id = ?
              AND version_no = ?
              AND status = 'PENDING_APPROVAL'
            """;

    private static final String REJECT_EXAM_VERSION_SQL = """
            UPDATE exam_versions
            SET status = ?,
                reviewed_by_user_id = ?,
                reviewed_at = ?,
                rejection_reason = ?
            WHERE exam_id = ?
              AND version_no = ?
              AND status = 'PENDING_APPROVAL'
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

    public Optional<Exam> findCurrentEntityForTeacher(int authenticatedUserId, int examId) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     TEACHER_EXAM_ENTITY_BY_ID_SQL
             )) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, examId);
            statement.setInt(3, authenticatedUserId);
            return loadCurrentExamEntity(connection, statement, examId);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load teacher exam entity", e);
        }
    }

    public Optional<Exam> findCurrentEntityForCoordinator(
            int authenticatedUserId,
            int examId
    ) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     COORDINATOR_EXAM_ENTITY_BY_ID_SQL
             )) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, examId);
            return loadCurrentExamEntity(connection, statement, examId);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load coordinator exam entity", e);
        }
    }

    public Optional<Exam> findEntityVersion(int examId, int versionNo) {
        if (examId <= 0) {
            throw new IllegalArgumentException("Exam ID must be positive");
        }
        if (versionNo <= 0) {
            throw new IllegalArgumentException("Exam version must be positive");
        }

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     EXACT_EXAM_ENTITY_VERSION_SQL
             )) {
            statement.setInt(1, examId);
            statement.setInt(2, versionNo);
            return loadExamEntity(
                    connection,
                    statement,
                    examId,
                    false,
                    "Requested exam version is missing: " + examId
                            + " version " + versionNo,
                    "Requested exam version timestamp is missing: " + examId
                            + " version " + versionNo
            );
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load exact exam version", e);
        }
    }

    public int create(int authenticatedUserId, Exam exam) {
        if (exam == null) {
            throw new IllegalArgumentException("Exam creation data is missing");
        }
        requireUnsavedDraft(authenticatedUserId, exam);
        return createExam(
                authenticatedUserId,
                exam.getCourseId(),
                examVersionData(exam)
        );
    }

    public int updateWithNewVersion(int authenticatedUserId, int examId,
                                    int expectedVersionNo, Exam exam) {
        if (exam == null) {
            throw new IllegalArgumentException("Exam update data is missing");
        }
        requirePersistedDraft(authenticatedUserId, examId, expectedVersionNo, exam);
        return updateExamVersion(
                authenticatedUserId,
                examId,
                expectedVersionNo,
                exam.getCurrentVersionNo(),
                exam.getCourseId(),
                examVersionData(exam)
        );
    }

    public boolean persistSubmissionForApproval(int authenticatedUserId, Exam exam) {
        requireWorkflowExam(authenticatedUserId, exam, ExamStatus.PENDING_APPROVAL);
        if (exam.getSubmittedAt() == null) {
            throw new IllegalArgumentException("Submission timestamp is required");
        }
        return persistSubmission(
                authenticatedUserId,
                exam.getExamId(),
                exam.getCurrentVersionNo(),
                exam.getCourseId(),
                exam.getSubmittedAt()
        );
    }

    public boolean persistApproval(int authenticatedUserId, Exam exam) {
        requireReviewedWorkflowExam(authenticatedUserId, exam, ExamStatus.APPROVED);
        return persistApproval(
                authenticatedUserId,
                exam.getExamId(),
                exam.getCurrentVersionNo(),
                exam.getCourseId(),
                exam.getReviewedAt()
        );
    }

    public boolean persistRejection(int authenticatedUserId, Exam exam) {
        requireReviewedWorkflowExam(authenticatedUserId, exam, ExamStatus.REJECTED);
        if (exam.getRejectionReason() == null) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        return persistRejection(
                authenticatedUserId,
                exam.getExamId(),
                exam.getCurrentVersionNo(),
                exam.getCourseId(),
                exam.getRejectionReason(),
                exam.getReviewedAt()
        );
    }

    private Optional<Exam> loadCurrentExamEntity(Connection connection,
                                                  PreparedStatement statement,
                                                  int requestedExamId)
            throws SQLException {
        return loadExamEntity(
                connection,
                statement,
                requestedExamId,
                true,
                "Current exam version is missing: " + requestedExamId,
                "Current exam version timestamp is missing: " + requestedExamId
        );
    }

    private Optional<Exam> loadExamEntity(
            Connection connection,
            PreparedStatement statement,
            int requestedExamId,
            boolean includeLogicalExamUpdatedAt,
            String missingVersionMessage,
            String missingVersionTimestampMessage
    ) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Optional.empty();
            }

            Object versionValue = resultSet.getObject("version_no");
            if (versionValue == null) {
                throw new IllegalArgumentException(missingVersionMessage);
            }

            int hydratedExamId = resultSet.getInt("exam_id");
            int versionNo = resultSet.getInt("version_no");
            List<ExamQuestion> questions = loadExamQuestionEntities(
                    connection,
                    hydratedExamId,
                    versionNo
            );
            BigDecimal persistedTotal = resultSet.getBigDecimal("total_score");
            if (persistedTotal == null) {
                throw new IllegalArgumentException(
                        "Exam total score is missing: " + hydratedExamId
                );
            }

            LocalDateTime createdAt = requireTimestamp(
                    resultSet.getObject("exam_created_at", LocalDateTime.class),
                    "Exam creation timestamp is missing: " + hydratedExamId
            );
            LocalDateTime versionCreatedAt = requireTimestamp(
                    resultSet.getObject("version_created_at", LocalDateTime.class),
                    missingVersionTimestampMessage
            );
            LocalDateTime submittedAt = resultSet.getObject(
                    "submitted_at",
                    LocalDateTime.class
            );
            LocalDateTime reviewedAt = resultSet.getObject(
                    "reviewed_at",
                    LocalDateTime.class
            );
            LocalDateTime updatedAt = includeLogicalExamUpdatedAt
                    ? latestTimestamp(
                            requireTimestamp(
                                    resultSet.getObject(
                                            "exam_updated_at",
                                            LocalDateTime.class
                                    ),
                                    "Exam update timestamp is missing: " + hydratedExamId
                            ),
                            versionCreatedAt,
                            submittedAt,
                            reviewedAt
                    )
                    : latestTimestamp(
                            createdAt,
                            versionCreatedAt,
                            submittedAt,
                            reviewedAt
                    );

            Exam exam = Exam.rehydrate(
                    hydratedExamId,
                    resultSet.getString("exam_code"),
                    resultSet.getInt("course_id"),
                    resultSet.getInt("created_by_user_id"),
                    versionNo,
                    resultSet.getString("title"),
                    resultSet.getInt("duration_minutes"),
                    resultSet.getString("teacher_notes"),
                    resultSet.getString("student_instructions"),
                    parsePersistedEnum(
                            resultSet.getString("status"),
                            ExamStatus.class,
                            "Exam status",
                            hydratedExamId
                    ),
                    createdAt,
                    updatedAt,
                    submittedAt,
                    resultSet.getObject("reviewed_by_user_id", Integer.class),
                    reviewedAt,
                    resultSet.getString("rejection_reason"),
                    questions
            );
            if (exam.getTotalScoreValue().compareTo(persistedTotal) != 0) {
                throw new IllegalArgumentException(
                        "Exam total score does not match selections: " + hydratedExamId
                );
            }
            return Optional.of(exam);
        }
    }

    private List<ExamQuestion> loadExamQuestionEntities(Connection connection, int examId,
                                                         int versionNo)
            throws SQLException {
        List<ExamQuestion> questions = new ArrayList<>();
        Set<Integer> questionIds = new HashSet<>();
        Set<Integer> orderNumbers = new HashSet<>();

        try (PreparedStatement statement = connection.prepareStatement(
                EXAM_ENTITY_QUESTION_SNAPSHOTS_SQL
        )) {
            statement.setInt(1, examId);
            statement.setInt(2, versionNo);

            try (ResultSet resultSet = statement.executeQuery()) {
                ExamQuestionSnapshotBuilder builder = null;
                while (resultSet.next()) {
                    int orderNumber = resultSet.getInt("order_number");
                    int questionId = resultSet.getInt("question_id");
                    int questionVersionNo = resultSet.getInt("question_version_no");

                    if (builder == null || builder.orderNumber != orderNumber) {
                        if (builder != null) {
                            questions.add(builder.build());
                        }
                        if (!orderNumbers.add(orderNumber)) {
                            throw new IllegalArgumentException(
                                    "Duplicate exam question order: " + orderNumber
                            );
                        }
                        if (!questionIds.add(questionId)) {
                            throw new IllegalArgumentException(
                                    "Duplicate exam question: " + questionId
                            );
                        }
                        builder = snapshotBuilder(
                                resultSet,
                                questionId,
                                questionVersionNo,
                                orderNumber
                        );
                    } else if (builder.questionId != questionId
                            || builder.questionVersionNo != questionVersionNo) {
                        throw new IllegalArgumentException(
                                "Duplicate exam question order: " + orderNumber
                        );
                    }
                    builder.addOption(resultSet);
                }
                if (builder != null) {
                    questions.add(builder.build());
                }
            }
        }
        return questions;
    }

    private ExamQuestionSnapshotBuilder snapshotBuilder(ResultSet resultSet, int questionId,
                                                          int questionVersionNo,
                                                          int orderNumber)
            throws SQLException {
        Object snapshotVersionValue = resultSet.getObject("snapshot_version_no");
        Object snapshotQuestionValue = resultSet.getObject("snapshot_question_id");
        if (snapshotVersionValue == null || snapshotQuestionValue == null) {
            throw new IllegalArgumentException(
                    "Selected question version is missing: " + questionId
                            + " version " + questionVersionNo
            );
        }
        if (resultSet.getInt("snapshot_question_id") != questionId
                || resultSet.getInt("snapshot_version_no") != questionVersionNo) {
            throw new IllegalArgumentException(
                    "Selected question version does not match selection: " + questionId
            );
        }

        BigDecimal score = resultSet.getBigDecimal("score");
        if (score == null || score.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Exam question score is invalid: " + questionId
            );
        }
        int correctOptionNumber = resultSet.getInt("correct_option_number");
        if (correctOptionNumber < 1 || correctOptionNumber > 4) {
            throw new IllegalArgumentException(
                    "Correct answer number is invalid for question: " + questionId
            );
        }

        return new ExamQuestionSnapshotBuilder(
                questionId,
                questionVersionNo,
                orderNumber,
                score,
                resultSet.getString("content"),
                resultSet.getString("topic"),
                parsePersistedEnum(
                        resultSet.getString("question_type"),
                        QuestionType.class,
                        "Question type",
                        questionId
                ),
                parsePersistedEnum(
                        resultSet.getString("difficulty"),
                        DifficultyLevel.class,
                        "Question difficulty",
                        questionId
                ),
                parsePersistedEnum(
                        resultSet.getString("question_status"),
                        QuestionStatus.class,
                        "Question status",
                        questionId
                ),
                resultSet.getString("illustration_path"),
                correctOptionNumber,
                requireTimestamp(
                        resultSet.getObject(
                                "question_version_created_at",
                                LocalDateTime.class
                        ),
                        "Selected question version timestamp is missing: " + questionId
                )
        );
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

    private int createExam(int authenticatedUserId, int courseId,
                           ExamVersionPersistenceData data) {
        return executeInTransaction("Failed to create exam", connection -> {
            requireAssignedCourse(connection, authenticatedUserId, courseId);
            for (ExamQuestionPersistenceData selection : data.questions) {
                requireAvailableQuestion(
                        connection,
                        authenticatedUserId,
                        courseId,
                        selection
                );
            }

            int examId = insertExamWithUniqueCode(
                    connection,
                    authenticatedUserId,
                    courseId
            );
            insertExamVersion(
                    connection,
                    examId,
                    1,
                    authenticatedUserId,
                    data
            );
            insertExamQuestions(connection, examId, 1, data.questions);
            updateCurrentVersion(connection, examId);
            return examId;
        });
    }

    private int updateExamVersion(int authenticatedUserId, int examId,
                                  int expectedVersionNo, int proposedVersionNo,
                                  int proposedCourseId,
                                  ExamVersionPersistenceData data) {
        return executeInTransaction("Failed to update exam version", connection -> {
            LockedExam currentExam = lockTeacherCurrentExam(
                    connection,
                    authenticatedUserId,
                    examId
            ).orElseThrow(() -> new IllegalArgumentException(
                    "Exam not found: " + examId
            ));

            requireExpectedVersion(currentExam, expectedVersionNo);
            if (proposedCourseId > 0 && proposedCourseId != currentExam.courseId) {
                throw new IllegalArgumentException("Exam course cannot be changed");
            }
            if (currentExam.status == ExamStatus.PENDING_APPROVAL) {
                throw new IllegalStateException("Pending exam cannot be edited");
            }
            if (data.status != ExamStatus.DRAFT) {
                throw new IllegalArgumentException("New exam version must be a draft");
            }
            int newVersionNo = currentExam.versionNo + 1;
            if (proposedVersionNo > 0 && proposedVersionNo != newVersionNo) {
                throw new IllegalStateException("Exam version conflict");
            }

            for (ExamQuestionPersistenceData selection : data.questions) {
                requireAvailableQuestion(
                        connection,
                        authenticatedUserId,
                        currentExam.courseId,
                        selection
                );
            }

            insertExamVersion(
                    connection,
                    examId,
                    newVersionNo,
                    authenticatedUserId,
                    data
            );
            insertExamQuestions(connection, examId, newVersionNo, data.questions);
            updateCurrentVersionAfterEdit(
                    connection,
                    examId,
                    currentExam.versionNo,
                    newVersionNo
            );
            return newVersionNo;
        });
    }

    private boolean persistSubmission(int authenticatedUserId, int examId,
                                      int expectedVersionNo,
                                      int expectedCourseId,
                                      LocalDateTime submittedAt) {
        return executeInTransaction("Failed to submit exam for approval", connection -> {
            Optional<LockedExam> lockedExam = lockTeacherCurrentExam(
                    connection,
                    authenticatedUserId,
                    examId
            );
            if (lockedExam.isEmpty()) {
                return false;
            }

            LockedExam currentExam = lockedExam.get();
            requireExpectedVersion(currentExam, expectedVersionNo);
            requireExpectedCourse(currentExam, expectedCourseId);
            if (currentExam.status != ExamStatus.DRAFT) {
                throw new IllegalStateException("Exam is not a draft");
            }
            updateSubmittedVersion(
                    connection,
                    examId,
                    currentExam.versionNo,
                    submittedAt
            );
            return true;
        });
    }

    private boolean persistApproval(int authenticatedUserId, int examId,
                                    int expectedVersionNo,
                                    int expectedCourseId,
                                    LocalDateTime reviewedAt) {
        return executeInTransaction("Failed to approve exam", connection -> {
            Optional<LockedExam> lockedExam = lockCoordinatorCurrentExam(
                    connection,
                    authenticatedUserId,
                    examId
            );
            if (lockedExam.isEmpty()) {
                return false;
            }

            LockedExam currentExam = lockedExam.get();
            requireExpectedVersion(currentExam, expectedVersionNo);
            requireExpectedCourse(currentExam, expectedCourseId);
            requirePendingApproval(currentExam);
            updateApprovedVersion(
                    connection,
                    authenticatedUserId,
                    examId,
                    currentExam.versionNo,
                    reviewedAt
            );
            return true;
        });
    }

    private boolean persistRejection(int authenticatedUserId, int examId,
                                     int expectedVersionNo, int expectedCourseId,
                                     String reason,
                                     LocalDateTime reviewedAt) {
        return executeInTransaction("Failed to reject exam", connection -> {
            Optional<LockedExam> lockedExam = lockCoordinatorCurrentExam(
                    connection,
                    authenticatedUserId,
                    examId
            );
            if (lockedExam.isEmpty()) {
                return false;
            }

            LockedExam currentExam = lockedExam.get();
            requireExpectedVersion(currentExam, expectedVersionNo);
            requireExpectedCourse(currentExam, expectedCourseId);
            requirePendingApproval(currentExam);
            updateRejectedVersion(
                    connection,
                    authenticatedUserId,
                    examId,
                    currentExam.versionNo,
                    reason,
                    reviewedAt
            );
            return true;
        });
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
                                          ExamQuestionPersistenceData selection)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_AVAILABLE_QUESTION_SQL
        )) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, selection.questionVersionNo);
            statement.setInt(3, selection.questionId);
            statement.setInt(4, courseId);
            statement.setInt(5, selection.questionVersionNo);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Question unavailable for exam: " + selection.questionId
                    );
                }
            }
        }
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

    private void insertExamVersion(Connection connection, int examId, int versionNo,
                                   int authenticatedUserId,
                                   ExamVersionPersistenceData data)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EXAM_VERSION_SQL
        )) {
            statement.setInt(1, examId);
            statement.setInt(2, versionNo);
            statement.setString(3, data.title);
            statement.setInt(4, data.durationMinutes);
            statement.setString(5, data.teacherNotes);
            statement.setString(6, data.studentInstructions);
            statement.setBigDecimal(7, data.totalScore);
            statement.setString(8, data.status.name());
            statement.setInt(9, authenticatedUserId);
            statement.setObject(10, data.createdAt);
            setNullableTimestamp(statement, 11, data.submittedAt);
            setNullableInteger(statement, 12, data.reviewedByUserId);
            setNullableTimestamp(statement, 13, data.reviewedAt);
            setNullableString(statement, 14, data.rejectionReason);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Exam version insert did not affect exactly one row");
            }
        }
    }

    private void insertExamQuestions(Connection connection, int examId, int versionNo,
                                     List<ExamQuestionPersistenceData> questions)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EXAM_QUESTION_SQL
        )) {
            for (ExamQuestionPersistenceData question : questions) {
                statement.setInt(1, examId);
                statement.setInt(2, versionNo);
                statement.setInt(3, question.orderNumber);
                statement.setInt(4, question.questionId);
                statement.setInt(5, question.questionVersionNo);
                statement.setBigDecimal(6, question.score);

                if (statement.executeUpdate() != 1) {
                    throw new SQLException(
                            "Exam question insert did not affect exactly one row"
                    );
                }
            }
        }
    }

    private Optional<LockedExam> lockTeacherCurrentExam(Connection connection,
                                                         int authenticatedUserId,
                                                         int examId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_TEACHER_CURRENT_EXAM_SQL
        )) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, examId);
            statement.setInt(3, authenticatedUserId);
            return readLockedExam(statement);
        }
    }

    private Optional<LockedExam> lockCoordinatorCurrentExam(Connection connection,
                                                             int authenticatedCoordinatorId,
                                                             int examId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_COORDINATOR_CURRENT_EXAM_SQL
        )) {
            statement.setInt(1, authenticatedCoordinatorId);
            statement.setInt(2, examId);
            return readLockedExam(statement);
        }
    }

    private Optional<LockedExam> readLockedExam(PreparedStatement statement)
            throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            return Optional.of(new LockedExam(
                    resultSet.getInt("course_id"),
                    resultSet.getInt("current_version_no"),
                    ExamStatus.valueOf(resultSet.getString("status"))
            ));
        }
    }

    private void requireExpectedVersion(LockedExam currentExam, int expectedVersionNo) {
        if (currentExam.versionNo != expectedVersionNo) {
            throw new IllegalStateException("Exam version conflict");
        }
    }

    private void requireExpectedCourse(LockedExam currentExam, int expectedCourseId) {
        if (expectedCourseId > 0 && currentExam.courseId != expectedCourseId) {
            throw new IllegalArgumentException("Exam course cannot be changed");
        }
    }

    private void requirePendingApproval(LockedExam currentExam) {
        if (currentExam.status != ExamStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Exam is not pending approval");
        }
    }

    private void updateCurrentVersionAfterEdit(Connection connection, int examId,
                                               int currentVersionNo, int newVersionNo)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                UPDATE_CURRENT_VERSION_AFTER_EDIT_SQL
        )) {
            statement.setInt(1, newVersionNo);
            statement.setInt(2, examId);
            statement.setInt(3, currentVersionNo);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Exam version conflict");
            }
        }
    }

    private void updateSubmittedVersion(Connection connection, int examId, int versionNo,
                                        LocalDateTime submittedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SUBMIT_EXAM_VERSION_SQL
        )) {
            statement.setString(1, ExamStatus.PENDING_APPROVAL.name());
            statement.setObject(2, submittedAt);
            statement.setInt(3, examId);
            statement.setInt(4, versionNo);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Exam is not a draft");
            }
        }
    }

    private void updateApprovedVersion(Connection connection, int coordinatorId,
                                       int examId, int versionNo,
                                       LocalDateTime reviewedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                APPROVE_EXAM_VERSION_SQL
        )) {
            statement.setString(1, ExamStatus.APPROVED.name());
            statement.setInt(2, coordinatorId);
            statement.setObject(3, reviewedAt);
            statement.setInt(4, examId);
            statement.setInt(5, versionNo);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Exam is not pending approval");
            }
        }
    }

    private void updateRejectedVersion(Connection connection, int coordinatorId,
                                       int examId, int versionNo, String reason,
                                       LocalDateTime reviewedAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                REJECT_EXAM_VERSION_SQL
        )) {
            statement.setString(1, ExamStatus.REJECTED.name());
            statement.setInt(2, coordinatorId);
            statement.setObject(3, reviewedAt);
            statement.setString(4, reason);
            statement.setInt(5, examId);
            statement.setInt(6, versionNo);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Exam is not pending approval");
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

    private ExamVersionPersistenceData examVersionData(Exam exam) {
        List<ExamQuestionPersistenceData> questions = exam.getExamQuestions().stream()
                .map(question -> new ExamQuestionPersistenceData(
                        question.getQuestionId(),
                        question.getQuestionVersionNo(),
                        question.getOrderNumber(),
                        question.getScoreValue()
                ))
                .toList();
        return new ExamVersionPersistenceData(
                exam.getTitle(),
                exam.getDurationMinutes(),
                exam.getTeacherNotes(),
                exam.getStudentInstructions(),
                exam.getTotalScoreValue(),
                exam.getStatus(),
                exam.getUpdatedAt(),
                exam.getSubmittedAt(),
                exam.getReviewedByUserId(),
                exam.getReviewedAt(),
                exam.getRejectionReason(),
                questions
        );
    }

    private void requireUnsavedDraft(int authenticatedUserId, Exam exam) {
        if (exam.getExamId() != 0 || exam.getExamCode() != null) {
            throw new IllegalArgumentException("Exam must be unsaved");
        }
        if (exam.getCurrentVersionNo() != 1) {
            throw new IllegalArgumentException("New exam version must be 1");
        }
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new IllegalArgumentException("New exam must be a draft");
        }
        if (exam.getCreatedByUserId() != authenticatedUserId) {
            throw new IllegalArgumentException("Exam creator does not match authenticated user");
        }
    }

    private void requirePersistedDraft(int authenticatedUserId, int examId,
                                       int expectedVersionNo, Exam exam) {
        if (examId <= 0 || exam.getExamId() != examId) {
            throw new IllegalArgumentException("Exam identity does not match update");
        }
        if (exam.getExamCode() == null || exam.getExamCode().isBlank()) {
            throw new IllegalArgumentException("Persisted exam code is required");
        }
        if (exam.getCreatedByUserId() != authenticatedUserId) {
            throw new IllegalArgumentException("Exam creator does not match authenticated user");
        }
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new IllegalArgumentException("New exam version must be a draft");
        }
        if (exam.getCurrentVersionNo() != expectedVersionNo + 1) {
            throw new IllegalStateException("Exam version conflict");
        }
    }

    private void requireWorkflowExam(int authenticatedUserId, Exam exam,
                                     ExamStatus targetStatus) {
        if (exam == null) {
            throw new IllegalArgumentException("Exam workflow data is missing");
        }
        if (exam.getExamId() <= 0 || exam.getCurrentVersionNo() <= 0) {
            throw new IllegalArgumentException("Persisted exam identity is required");
        }
        if (exam.getStatus() != targetStatus) {
            throw new IllegalArgumentException(
                    "Exam must be in " + targetStatus.name() + " state"
            );
        }
        if (targetStatus == ExamStatus.PENDING_APPROVAL
                && exam.getCreatedByUserId() != authenticatedUserId) {
            throw new IllegalArgumentException("Exam creator does not match authenticated user");
        }
    }

    private void requireReviewedWorkflowExam(int authenticatedUserId, Exam exam,
                                             ExamStatus targetStatus) {
        requireWorkflowExam(authenticatedUserId, exam, targetStatus);
        if (exam.getReviewedByUserId() == null
                || exam.getReviewedByUserId() != authenticatedUserId
                || exam.getReviewedAt() == null) {
            throw new IllegalArgumentException(
                    "Exam review metadata does not match authenticated user"
            );
        }
    }

    private static <E extends Enum<E>> E parsePersistedEnum(
            String value,
            Class<E> enumType,
            String label,
            int entityId
    ) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is missing: " + entityId);
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + " is invalid: " + entityId, e);
        }
    }

    private static LocalDateTime requireTimestamp(LocalDateTime timestamp,
                                                  String message) {
        if (timestamp == null) {
            throw new IllegalArgumentException(message);
        }
        return timestamp;
    }

    private static LocalDateTime latestTimestamp(LocalDateTime first,
                                                 LocalDateTime... candidates) {
        LocalDateTime latest = first;
        for (LocalDateTime candidate : candidates) {
            if (candidate != null && candidate.isAfter(latest)) {
                latest = candidate;
            }
        }
        return latest;
    }

    private void setNullableTimestamp(PreparedStatement statement, int index,
                                      LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setObject(index, value);
        }
    }

    private void setNullableInteger(PreparedStatement statement, int index,
                                    Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private void setNullableString(PreparedStatement statement, int index,
                                   String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
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

    private <T> T executeInTransaction(String failureMessage,
                                       TransactionOperation<T> operation) {
        try (Connection connection = databaseController.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean transactionStarted = false;
            Throwable transactionFailure = null;

            try {
                connection.setAutoCommit(false);
                transactionStarted = true;
                T result = operation.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException exception) {
                transactionFailure = exception;
                if (transactionStarted) {
                    rollbackWithSuppressed(connection, exception);
                }
                throw new IllegalStateException(failureMessage, exception);
            } catch (RuntimeException exception) {
                transactionFailure = exception;
                if (transactionStarted) {
                    rollbackWithSuppressed(connection, exception);
                }
                throw exception;
            } finally {
                restoreAutoCommit(connection, originalAutoCommit, transactionFailure);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(failureMessage, exception);
        }
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

    @FunctionalInterface
    private interface TransactionOperation<T> {
        T execute(Connection connection) throws SQLException;
    }

    private static final class ExamVersionPersistenceData {
        private final String title;
        private final int durationMinutes;
        private final String teacherNotes;
        private final String studentInstructions;
        private final BigDecimal totalScore;
        private final ExamStatus status;
        private final LocalDateTime createdAt;
        private final LocalDateTime submittedAt;
        private final Integer reviewedByUserId;
        private final LocalDateTime reviewedAt;
        private final String rejectionReason;
        private final List<ExamQuestionPersistenceData> questions;

        private ExamVersionPersistenceData(String title, int durationMinutes,
                                           String teacherNotes,
                                           String studentInstructions,
                                           BigDecimal totalScore, ExamStatus status,
                                           LocalDateTime createdAt,
                                           LocalDateTime submittedAt,
                                           Integer reviewedByUserId,
                                           LocalDateTime reviewedAt,
                                           String rejectionReason,
                                           List<ExamQuestionPersistenceData> questions) {
            this.title = title;
            this.durationMinutes = durationMinutes;
            this.teacherNotes = teacherNotes;
            this.studentInstructions = studentInstructions;
            this.totalScore = totalScore;
            this.status = status;
            this.createdAt = createdAt;
            this.submittedAt = submittedAt;
            this.reviewedByUserId = reviewedByUserId;
            this.reviewedAt = reviewedAt;
            this.rejectionReason = rejectionReason;
            this.questions = List.copyOf(questions);
        }
    }

    private static final class ExamQuestionPersistenceData {
        private final int questionId;
        private final int questionVersionNo;
        private final int orderNumber;
        private final BigDecimal score;

        private ExamQuestionPersistenceData(int questionId, int questionVersionNo,
                                            int orderNumber, BigDecimal score) {
            this.questionId = questionId;
            this.questionVersionNo = questionVersionNo;
            this.orderNumber = orderNumber;
            this.score = score;
        }
    }

    private static final class ExamQuestionSnapshotBuilder {
        private final int questionId;
        private final int questionVersionNo;
        private final int orderNumber;
        private final BigDecimal score;
        private final String content;
        private final String topic;
        private final QuestionType type;
        private final DifficultyLevel difficulty;
        private final QuestionStatus status;
        private final String illustrationPath;
        private final int correctOptionNumber;
        private final LocalDateTime versionCreatedAt;
        private final List<AnswerOption> options = new ArrayList<>(4);
        private final Set<Integer> optionNumbers = new HashSet<>();

        private ExamQuestionSnapshotBuilder(int questionId, int questionVersionNo,
                                            int orderNumber, BigDecimal score,
                                            String content, String topic,
                                            QuestionType type,
                                            DifficultyLevel difficulty,
                                            QuestionStatus status,
                                            String illustrationPath,
                                            int correctOptionNumber,
                                            LocalDateTime versionCreatedAt) {
            this.questionId = questionId;
            this.questionVersionNo = questionVersionNo;
            this.orderNumber = orderNumber;
            this.score = score;
            this.content = content;
            this.topic = topic;
            this.type = type;
            this.difficulty = difficulty;
            this.status = status;
            this.illustrationPath = illustrationPath;
            this.correctOptionNumber = correctOptionNumber;
            this.versionCreatedAt = versionCreatedAt;
        }

        private void addOption(ResultSet resultSet) throws SQLException {
            Object optionNumberValue = resultSet.getObject("option_number");
            if (optionNumberValue == null) {
                return;
            }
            int optionNumber = resultSet.getInt("option_number");
            if (optionNumber < 1 || optionNumber > 4) {
                throw new IllegalArgumentException(
                        "Answer option number is invalid for question: " + questionId
                );
            }
            if (!optionNumbers.add(optionNumber)) {
                throw new IllegalArgumentException(
                        "Duplicate answer option for question: " + questionId
                                + " option " + optionNumber
                );
            }
            options.add(new AnswerOption(
                    optionNumber,
                    resultSet.getString("option_text"),
                    optionNumber == correctOptionNumber
            ));
        }

        private ExamQuestion build() {
            if (options.size() != 4
                    || !optionNumbers.containsAll(Set.of(1, 2, 3, 4))) {
                throw new IllegalArgumentException(
                        "Question must contain exactly four answer options: " + questionId
                );
            }
            Question snapshot = Question.rehydrate(
                    questionId,
                    content,
                    type,
                    difficulty,
                    status,
                    versionCreatedAt,
                    versionCreatedAt,
                    topic,
                    illustrationPath,
                    options
            );
            return new ExamQuestion(
                    questionId,
                    questionVersionNo,
                    orderNumber,
                    score,
                    snapshot
            );
        }
    }

    private static final class LockedExam {
        private final int courseId;
        private final int versionNo;
        private final ExamStatus status;

        private LockedExam(int courseId, int versionNo, ExamStatus status) {
            this.courseId = courseId;
            this.versionNo = versionNo;
            this.status = status;
        }
    }
}
