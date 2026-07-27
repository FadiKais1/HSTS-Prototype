package hsts.server.repository;

import hsts.common.ExamAttemptDTO;
import hsts.common.ExtendSubmissionTimePayload;
import hsts.common.SaveExamAnswerPayload;
import hsts.common.StudentAnswerDTO;
import hsts.common.StudentExamQuestionDTO;
import hsts.common.type.ExecutionStatus;
import hsts.common.type.SubmissionStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ExamSubmissionRepository {
    private static final int MYSQL_DUPLICATE_KEY_ERROR = 1062;

    private static final String LOCK_AVAILABLE_EXECUTION_SQL = """
            SELECT execution.execution_id,
                   execution.execution_code,
                   execution.exam_id,
                   execution.exam_version_no,
                   execution.opening_time,
                   execution.closing_time,
                   execution.duration_minutes,
                   execution.status,
                   version.title AS exam_title,
                   version.student_instructions
            FROM exam_executions execution
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN users student
              ON student.user_id = ?
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            JOIN student_courses enrollment
              ON enrollment.student_user_id = student.user_id
             AND enrollment.course_id = exam.course_id
            WHERE execution.execution_id = ?
            FOR UPDATE
            """;

    private static final String LOCK_EXECUTION_SUBMISSION_SQL = """
            SELECT submission.submission_id,
                   submission.started_at,
                   submission.status,
                   submission.allocated_duration_minutes,
                   submission.extra_minutes
            FROM exam_submissions submission
            WHERE submission.execution_id = ?
              AND submission.student_user_id = ?
            FOR UPDATE
            """;

    private static final String INSERT_SUBMISSION_SQL = """
            INSERT INTO exam_submissions (
                execution_id,
                student_user_id,
                started_at,
                submitted_at,
                status,
                allocated_duration_minutes,
                extra_minutes,
                extension_reason,
                actual_duration_minutes,
                automatic_score,
                final_score,
                teacher_feedback,
                manual_change_reason,
                reviewed_by_user_id,
                reviewed_at,
                published_by_user_id,
                published_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INCREMENT_STARTED_COUNT_SQL = """
            UPDATE exam_executions
            SET started_count = started_count + 1
            WHERE execution_id = ?
            """;

    private static final String ACTIVE_ATTEMPT_SQL = """
            SELECT submission.submission_id,
                   submission.started_at,
                   submission.status,
                   submission.allocated_duration_minutes,
                   submission.extra_minutes,
                   execution.execution_id,
                   execution.execution_code,
                   execution.exam_id,
                   execution.exam_version_no,
                   execution.opening_time,
                   execution.closing_time,
                   execution.duration_minutes,
                   execution.status AS execution_status,
                   version.title AS exam_title,
                   version.student_instructions
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN users student
              ON student.user_id = submission.student_user_id
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            WHERE submission.submission_id = ?
              AND submission.student_user_id = ?
              AND submission.status = 'IN_PROGRESS'
            """;

    private static final String LOCK_STUDENT_SUBMISSION_SQL = """
            SELECT submission.submission_id,
                   submission.started_at,
                   submission.status,
                   submission.allocated_duration_minutes,
                   submission.extra_minutes,
                   execution.execution_id,
                   execution.execution_code,
                   execution.exam_id,
                   execution.exam_version_no,
                   execution.opening_time,
                   execution.closing_time,
                   execution.duration_minutes,
                   execution.status AS execution_status,
                   version.title AS exam_title,
                   version.student_instructions
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN users student
              ON student.user_id = submission.student_user_id
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            WHERE submission.submission_id = ?
              AND submission.student_user_id = ?
            FOR UPDATE
            """;

    private static final String LOCK_INTERNAL_SUBMISSION_SQL = """
            SELECT submission.submission_id,
                   submission.started_at,
                   submission.status,
                   submission.allocated_duration_minutes,
                   submission.extra_minutes,
                   execution.execution_id,
                   execution.execution_code,
                   execution.exam_id,
                   execution.exam_version_no,
                   execution.opening_time,
                   execution.closing_time,
                   execution.duration_minutes,
                   execution.status AS execution_status,
                   version.title AS exam_title,
                   version.student_instructions
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            WHERE submission.submission_id = ?
            FOR UPDATE
            """;

    private static final String SAFE_QUESTIONS_SQL = """
            SELECT selection.question_id,
                   selection.question_version_no,
                   selection.order_number,
                   selection.score,
                   version.content,
                   version.topic,
                   version.difficulty,
                   version.illustration_path,
                   option_1.option_text AS answer_option_1,
                   option_2.option_text AS answer_option_2,
                   option_3.option_text AS answer_option_3,
                   option_4.option_text AS answer_option_4
            FROM exam_version_questions selection
            JOIN question_versions version
              ON version.question_id = selection.question_id
             AND version.version_no = selection.question_version_no
            JOIN answer_options option_1
              ON option_1.question_id = version.question_id
             AND option_1.version_no = version.version_no
             AND option_1.option_number = 1
            JOIN answer_options option_2
              ON option_2.question_id = version.question_id
             AND option_2.version_no = version.version_no
             AND option_2.option_number = 2
            JOIN answer_options option_3
              ON option_3.question_id = version.question_id
             AND option_3.version_no = version.version_no
             AND option_3.option_number = 3
            JOIN answer_options option_4
              ON option_4.question_id = version.question_id
             AND option_4.version_no = version.version_no
             AND option_4.option_number = 4
            WHERE selection.exam_id = ?
              AND selection.exam_version_no = ?
            ORDER BY selection.order_number ASC
            """;

    private static final String SAFE_ANSWERS_SQL = """
            SELECT answer.question_id,
                   answer.selected_option_number,
                   answer.updated_at
            FROM student_answers answer
            JOIN exam_submissions submission
              ON submission.submission_id = answer.submission_id
            WHERE answer.submission_id = ?
              AND submission.student_user_id = ?
            ORDER BY answer.question_id ASC
            """;

    private static final String LOCK_EXAM_QUESTION_SQL = """
            SELECT selection.question_version_no
            FROM exam_version_questions selection
            WHERE selection.exam_id = ?
              AND selection.exam_version_no = ?
              AND selection.question_id = ?
            FOR UPDATE
            """;

    private static final String UPSERT_ANSWER_SQL = """
            INSERT INTO student_answers (
                submission_id,
                question_id,
                question_version_no,
                selected_option_number,
                answer_content,
                is_correct,
                score_received,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                question_version_no = VALUES(question_version_no),
                selected_option_number = VALUES(selected_option_number),
                answer_content = NULL,
                is_correct = NULL,
                score_received = NULL,
                updated_at = VALUES(updated_at)
            """;

    private static final String LOCK_GRADING_ROWS_SQL = """
            SELECT selection.question_id,
                   selection.question_version_no,
                   selection.score,
                   version.correct_option_number,
                   answer.answer_id,
                   answer.selected_option_number
            FROM exam_version_questions selection
            JOIN question_versions version
              ON version.question_id = selection.question_id
             AND version.version_no = selection.question_version_no
            LEFT JOIN student_answers answer
              ON answer.submission_id = ?
             AND answer.question_id = selection.question_id
             AND answer.question_version_no = selection.question_version_no
            WHERE selection.exam_id = ?
              AND selection.exam_version_no = ?
            ORDER BY selection.order_number ASC
            FOR UPDATE
            """;

    private static final String UPDATE_GRADED_ANSWER_SQL = """
            UPDATE student_answers
            SET is_correct = ?,
                score_received = ?
            WHERE answer_id = ?
              AND submission_id = ?
            """;

    private static final String FINALIZE_SUBMISSION_SQL = """
            UPDATE exam_submissions
            SET submitted_at = ?,
                status = ?,
                actual_duration_minutes = ?,
                automatic_score = ?,
                final_score = ?
            WHERE submission_id = ?
              AND status = 'IN_PROGRESS'
            """;

    private static final String INCREMENT_SUBMITTED_COUNT_SQL = """
            UPDATE exam_executions
            SET submitted_count = submitted_count + 1
            WHERE execution_id = ?
            """;

    private static final String INCREMENT_AUTO_SUBMITTED_COUNT_SQL = """
            UPDATE exam_executions
            SET auto_submitted_count = auto_submitted_count + 1
            WHERE execution_id = ?
            """;

    private static final String EXPIRED_SUBMISSIONS_SQL = """
            SELECT submission_id
            FROM exam_submissions
            WHERE status = 'IN_PROGRESS'
              AND TIMESTAMPADD(
                    MINUTE,
                    allocated_duration_minutes + extra_minutes,
                    started_at
                  ) <= ?
            ORDER BY submission_id ASC
            """;

    private static final String LOCK_MANAGER_EXTENSION_SQL = """
            SELECT submission.submission_id,
                   submission.started_at,
                   submission.status,
                   submission.allocated_duration_minutes,
                   submission.extra_minutes
            FROM exam_submissions submission
            JOIN exam_executions execution
              ON execution.execution_id = submission.execution_id
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN users manager
              ON manager.user_id = ?
             AND manager.role IN ('TEACHER', 'COORDINATOR')
             AND manager.status = 'ACTIVE'
            JOIN teacher_courses assignment
              ON assignment.teacher_user_id = manager.user_id
             AND assignment.course_id = exam.course_id
            WHERE submission.submission_id = ?
            FOR UPDATE
            """;

    private static final String UPDATE_EXTENSION_SQL = """
            UPDATE exam_submissions
            SET extra_minutes = extra_minutes + ?,
                extension_reason = ?
            WHERE submission_id = ?
              AND status = 'IN_PROGRESS'
            """;

    private static final String INSERT_EXTENSION_AUDIT_SQL = """
            INSERT INTO submission_time_extensions (
                submission_id,
                added_minutes,
                reason,
                extended_by_user_id,
                created_at
            )
            VALUES (?, ?, ?, ?, ?)
            """;

    private final DatabaseController databaseController;

    public ExamSubmissionRepository() {
        this(new DatabaseController());
    }

    public ExamSubmissionRepository(DatabaseController databaseController) {
        this.databaseController = databaseController;
    }

    public ExamAttemptDTO startOrResume(int authenticatedStudentId, int executionId,
                                        LocalDateTime now) {
        return executeInTransaction("Failed to start exam attempt", connection -> {
            LockedExecution execution = lockAvailableExecution(
                    connection,
                    authenticatedStudentId,
                    executionId
            ).orElseThrow(() -> new IllegalStateException("Execution not available"));

            Optional<SubmissionRecord> existing = findExecutionSubmission(
                    connection,
                    execution,
                    authenticatedStudentId
            );
            if (existing.isPresent()) {
                return resumeAttempt(
                        connection,
                        authenticatedStudentId,
                        execution,
                        existing.get(),
                        now
                );
            }

            requireNewAttemptAvailable(execution, now);

            final int submissionId;
            try {
                submissionId = insertSubmission(
                        connection,
                        authenticatedStudentId,
                        execution,
                        now
                );
            } catch (SQLException exception) {
                if (!isSubmissionCollision(exception)) {
                    throw exception;
                }

                Optional<SubmissionRecord> racedSubmission = findExecutionSubmission(
                        connection,
                        execution,
                        authenticatedStudentId
                );
                if (racedSubmission.isEmpty()) {
                    throw exception;
                }
                return resumeAttempt(
                        connection,
                        authenticatedStudentId,
                        execution,
                        racedSubmission.get(),
                        now
                );
            }

            incrementStartedCount(connection, executionId);
            SubmissionRecord created = new SubmissionRecord(
                    submissionId,
                    execution,
                    now,
                    SubmissionStatus.IN_PROGRESS,
                    execution.durationMinutes,
                    0
            );
            return loadAttempt(connection, authenticatedStudentId, created, now);
        });
    }

    public Optional<ExamAttemptDTO> findActiveForStudent(int authenticatedStudentId,
                                                          int submissionId,
                                                          LocalDateTime now) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(ACTIVE_ATTEMPT_SQL)) {
            statement.setInt(1, submissionId);
            statement.setInt(2, authenticatedStudentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                SubmissionRecord submission = mapCompleteSubmission(resultSet);
                return Optional.of(loadAttempt(
                        connection,
                        authenticatedStudentId,
                        submission,
                        now
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load active exam attempt", exception);
        }
    }

    public StudentAnswerDTO saveAnswer(int authenticatedStudentId,
                                       SaveExamAnswerPayload payload,
                                       LocalDateTime now) {
        if (payload == null) {
            throw new IllegalArgumentException("Answer data is missing");
        }

        return executeInTransaction("Failed to save exam answer", connection -> {
            SubmissionRecord submission = lockStudentSubmission(
                    connection,
                    authenticatedStudentId,
                    payload.getSubmissionId()
            ).orElseThrow(() -> new IllegalArgumentException(
                    "Exam attempt not found: " + payload.getSubmissionId()
            ));

            requireInProgress(submission);
            requireBeforeDeadline(submission, now);
            int questionVersionNo = lockExamQuestionVersion(
                    connection,
                    submission,
                    payload.getQuestionId()
            ).orElseThrow(() -> new IllegalArgumentException(
                    "Question not found in exam: " + payload.getQuestionId()
            ));
            if (payload.getSelectedOptionNumber() < 1
                    || payload.getSelectedOptionNumber() > 4) {
                throw new IllegalArgumentException("Invalid answer option");
            }

            upsertAnswer(connection, payload, questionVersionNo, now);
            return new StudentAnswerDTO(
                    payload.getQuestionId(),
                    payload.getSelectedOptionNumber(),
                    now
            );
        });
    }

    public ExamAttemptDTO submit(int authenticatedStudentId, int submissionId,
                                 LocalDateTime now) {
        return executeInTransaction("Failed to submit exam attempt", connection -> {
            SubmissionRecord submission = lockStudentSubmission(
                    connection,
                    authenticatedStudentId,
                    submissionId
            ).orElseThrow(() -> new IllegalArgumentException(
                    "Exam attempt not found: " + submissionId
            ));
            requireInProgress(submission);

            SubmissionStatus finalStatus = now.isAfter(deadline(submission))
                    ? SubmissionStatus.AUTO_SUBMITTED
                    : SubmissionStatus.SUBMITTED;
            SubmissionRecord finalized = finalizeSubmission(
                    connection,
                    submission,
                    finalStatus,
                    now
            );
            return loadAttempt(connection, authenticatedStudentId, finalized, now);
        });
    }

    public List<Integer> findExpiredSubmissionIds(LocalDateTime now) {
        List<Integer> submissionIds = new ArrayList<>();
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     EXPIRED_SUBMISSIONS_SQL
             )) {
            statement.setObject(1, now);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    submissionIds.add(resultSet.getInt("submission_id"));
                }
            }
            return submissionIds;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load expired submissions", exception);
        }
    }

    public boolean autoSubmit(int submissionId, LocalDateTime now) {
        return executeInTransaction("Failed to auto-submit exam attempt", connection -> {
            Optional<SubmissionRecord> locked = lockInternalSubmission(
                    connection,
                    submissionId
            );
            if (locked.isEmpty()
                    || locked.get().status != SubmissionStatus.IN_PROGRESS
                    || deadline(locked.get()).isAfter(now)) {
                return false;
            }

            finalizeSubmission(
                    connection,
                    locked.get(),
                    SubmissionStatus.AUTO_SUBMITTED,
                    now
            );
            return true;
        });
    }

    public boolean extendTime(int authenticatedManagerId,
                              ExtendSubmissionTimePayload payload,
                              LocalDateTime now) {
        if (payload == null) {
            throw new IllegalArgumentException("Time extension data is missing");
        }
        if (payload.getExtraMinutes() <= 0) {
            throw new IllegalArgumentException("Extra minutes must be positive");
        }

        return executeInTransaction("Failed to extend submission time", connection -> {
            Optional<ExtensionTarget> target = lockExtensionTarget(
                    connection,
                    authenticatedManagerId,
                    payload.getSubmissionId()
            );
            if (target.isEmpty()
                    || target.get().status != SubmissionStatus.IN_PROGRESS) {
                return false;
            }
            if (!now.isBefore(target.get().deadline())) {
                throw new IllegalStateException("Exam time has expired");
            }

            updateExtension(connection, payload);
            insertExtensionAudit(connection, authenticatedManagerId, payload, now);
            return true;
        });
    }

    private Optional<LockedExecution> lockAvailableExecution(
            Connection connection,
            int authenticatedStudentId,
            int executionId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_AVAILABLE_EXECUTION_SQL
        )) {
            statement.setInt(1, authenticatedStudentId);
            statement.setInt(2, executionId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new LockedExecution(
                        resultSet.getInt("execution_id"),
                        resultSet.getString("execution_code"),
                        resultSet.getInt("exam_id"),
                        resultSet.getInt("exam_version_no"),
                        resultSet.getString("exam_title"),
                        resultSet.getString("student_instructions"),
                        resultSet.getObject("opening_time", LocalDateTime.class),
                        resultSet.getObject("closing_time", LocalDateTime.class),
                        resultSet.getInt("duration_minutes"),
                        ExecutionStatus.valueOf(resultSet.getString("status"))
                ));
            }
        }
    }

    private Optional<SubmissionRecord> findExecutionSubmission(
            Connection connection,
            LockedExecution execution,
            int authenticatedStudentId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_EXECUTION_SUBMISSION_SQL
        )) {
            statement.setInt(1, execution.executionId);
            statement.setInt(2, authenticatedStudentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SubmissionRecord(
                        resultSet.getInt("submission_id"),
                        execution,
                        resultSet.getObject("started_at", LocalDateTime.class),
                        SubmissionStatus.valueOf(resultSet.getString("status")),
                        resultSet.getInt("allocated_duration_minutes"),
                        resultSet.getInt("extra_minutes")
                ));
            }
        }
    }

    private ExamAttemptDTO resumeAttempt(Connection connection,
                                         int authenticatedStudentId,
                                         LockedExecution execution,
                                         SubmissionRecord submission,
                                         LocalDateTime now) throws SQLException {
        if (submission.status != SubmissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Exam attempt already submitted");
        }
        if (!now.isBefore(deadline(submission))) {
            throw new IllegalStateException("Execution not available");
        }
        return loadAttempt(connection, authenticatedStudentId, submission, now);
    }

    private void requireNewAttemptAvailable(LockedExecution execution, LocalDateTime now) {
        boolean validStatus = execution.status == ExecutionStatus.SCHEDULED
                || execution.status == ExecutionStatus.OPEN;
        if (!validStatus
                || now.isBefore(execution.openingTime)
                || !now.isBefore(execution.closingTime)) {
            throw new IllegalStateException("Execution not available");
        }
    }

    private int insertSubmission(Connection connection, int authenticatedStudentId,
                                 LockedExecution execution, LocalDateTime now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_SUBMISSION_SQL,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setInt(1, execution.executionId);
            statement.setInt(2, authenticatedStudentId);
            statement.setObject(3, now);
            statement.setNull(4, Types.TIMESTAMP);
            statement.setString(5, SubmissionStatus.IN_PROGRESS.name());
            statement.setInt(6, execution.durationMinutes);
            statement.setInt(7, 0);
            statement.setNull(8, Types.LONGVARCHAR);
            statement.setNull(9, Types.INTEGER);
            statement.setNull(10, Types.DECIMAL);
            statement.setNull(11, Types.DECIMAL);
            statement.setNull(12, Types.LONGVARCHAR);
            statement.setNull(13, Types.LONGVARCHAR);
            statement.setNull(14, Types.INTEGER);
            statement.setNull(15, Types.TIMESTAMP);
            statement.setNull(16, Types.INTEGER);
            statement.setNull(17, Types.TIMESTAMP);

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Submission insert did not affect exactly one row");
            }
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new SQLException("Submission insert did not return a generated key");
                }
                int submissionId = generatedKeys.getInt(1);
                if (submissionId <= 0) {
                    throw new SQLException("Submission insert returned an invalid generated key");
                }
                return submissionId;
            }
        }
    }

    private void incrementStartedCount(Connection connection, int executionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INCREMENT_STARTED_COUNT_SQL
        )) {
            statement.setInt(1, executionId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Execution start count update failed");
            }
        }
    }

    private Optional<SubmissionRecord> lockStudentSubmission(
            Connection connection,
            int authenticatedStudentId,
            int submissionId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_STUDENT_SUBMISSION_SQL
        )) {
            statement.setInt(1, submissionId);
            statement.setInt(2, authenticatedStudentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(mapCompleteSubmission(resultSet))
                        : Optional.empty();
            }
        }
    }

    private Optional<SubmissionRecord> lockInternalSubmission(Connection connection,
                                                               int submissionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_INTERNAL_SUBMISSION_SQL
        )) {
            statement.setInt(1, submissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(mapCompleteSubmission(resultSet))
                        : Optional.empty();
            }
        }
    }

    private SubmissionRecord mapCompleteSubmission(ResultSet resultSet)
            throws SQLException {
        LockedExecution execution = new LockedExecution(
                resultSet.getInt("execution_id"),
                resultSet.getString("execution_code"),
                resultSet.getInt("exam_id"),
                resultSet.getInt("exam_version_no"),
                resultSet.getString("exam_title"),
                resultSet.getString("student_instructions"),
                resultSet.getObject("opening_time", LocalDateTime.class),
                resultSet.getObject("closing_time", LocalDateTime.class),
                resultSet.getInt("duration_minutes"),
                ExecutionStatus.valueOf(resultSet.getString("execution_status"))
        );
        return new SubmissionRecord(
                resultSet.getInt("submission_id"),
                execution,
                resultSet.getObject("started_at", LocalDateTime.class),
                SubmissionStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("allocated_duration_minutes"),
                resultSet.getInt("extra_minutes")
        );
    }

    private ExamAttemptDTO loadAttempt(Connection connection, int authenticatedStudentId,
                                       SubmissionRecord submission, LocalDateTime now)
            throws SQLException {
        List<StudentExamQuestionDTO> questions = loadSafeQuestions(
                connection,
                submission.execution.examId,
                submission.execution.examVersionNo
        );
        List<StudentAnswerDTO> answers = loadSafeAnswers(
                connection,
                authenticatedStudentId,
                submission.submissionId
        );
        LocalDateTime deadline = deadline(submission);
        long remainingSeconds = Math.max(0L, Duration.between(now, deadline).getSeconds());

        return new ExamAttemptDTO(
                submission.submissionId,
                submission.execution.executionId,
                submission.execution.executionCode,
                submission.execution.examId,
                submission.execution.examVersionNo,
                submission.execution.examTitle,
                submission.execution.studentInstructions,
                submission.startedAt,
                deadline,
                submission.allocatedDurationMinutes,
                submission.extraMinutes,
                remainingSeconds,
                submission.status,
                questions,
                answers
        );
    }

    private List<StudentExamQuestionDTO> loadSafeQuestions(Connection connection,
                                                           int examId,
                                                           int examVersionNo)
            throws SQLException {
        List<StudentExamQuestionDTO> questions = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SAFE_QUESTIONS_SQL)) {
            statement.setInt(1, examId);
            statement.setInt(2, examVersionNo);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    questions.add(new StudentExamQuestionDTO(
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
                            resultSet.getString("answer_option_4")
                    ));
                }
            }
        }
        return questions;
    }

    private List<StudentAnswerDTO> loadSafeAnswers(Connection connection,
                                                   int authenticatedStudentId,
                                                   int submissionId)
            throws SQLException {
        List<StudentAnswerDTO> answers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SAFE_ANSWERS_SQL)) {
            statement.setInt(1, submissionId);
            statement.setInt(2, authenticatedStudentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    answers.add(new StudentAnswerDTO(
                            resultSet.getInt("question_id"),
                            resultSet.getObject("selected_option_number", Integer.class),
                            resultSet.getObject("updated_at", LocalDateTime.class)
                    ));
                }
            }
        }
        return answers;
    }

    private void requireInProgress(SubmissionRecord submission) {
        if (submission.status != SubmissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Exam attempt already submitted");
        }
    }

    private void requireBeforeDeadline(SubmissionRecord submission, LocalDateTime now) {
        if (!now.isBefore(deadline(submission))) {
            throw new IllegalStateException("Exam time has expired");
        }
    }

    private Optional<Integer> lockExamQuestionVersion(Connection connection,
                                                      SubmissionRecord submission,
                                                      int questionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_EXAM_QUESTION_SQL
        )) {
            statement.setInt(1, submission.execution.examId);
            statement.setInt(2, submission.execution.examVersionNo);
            statement.setInt(3, questionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(resultSet.getInt("question_version_no"))
                        : Optional.empty();
            }
        }
    }

    private void upsertAnswer(Connection connection, SaveExamAnswerPayload payload,
                              int questionVersionNo, LocalDateTime now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_ANSWER_SQL)) {
            statement.setInt(1, payload.getSubmissionId());
            statement.setInt(2, payload.getQuestionId());
            statement.setInt(3, questionVersionNo);
            statement.setInt(4, payload.getSelectedOptionNumber());
            statement.setNull(5, Types.LONGVARCHAR);
            statement.setNull(6, Types.BOOLEAN);
            statement.setNull(7, Types.DECIMAL);
            statement.setObject(8, now);
            statement.setObject(9, now);
            int affectedRows = statement.executeUpdate();
            if (affectedRows < 1 || affectedRows > 2) {
                throw new SQLException("Answer save did not affect one logical row");
            }
        }
    }

    private SubmissionRecord finalizeSubmission(Connection connection,
                                                  SubmissionRecord submission,
                                                  SubmissionStatus finalStatus,
                                                  LocalDateTime now)
            throws SQLException {
        BigDecimal automaticScore = gradeSavedAnswers(connection, submission);
        int actualDurationMinutes = Math.toIntExact(Math.max(
                0L,
                Duration.between(submission.startedAt, now).toMinutes()
        ));
        updateFinalSubmission(
                connection,
                submission,
                finalStatus,
                now,
                actualDurationMinutes,
                automaticScore
        );
        incrementFinalizationCounter(
                connection,
                submission.execution.executionId,
                finalStatus
        );
        return submission.withStatus(finalStatus);
    }

    private BigDecimal gradeSavedAnswers(Connection connection,
                                          SubmissionRecord submission)
            throws SQLException {
        BigDecimal total = BigDecimal.ZERO;
        try (PreparedStatement select = connection.prepareStatement(
                LOCK_GRADING_ROWS_SQL
        ); PreparedStatement update = connection.prepareStatement(
                UPDATE_GRADED_ANSWER_SQL
        )) {
            select.setInt(1, submission.submissionId);
            select.setInt(2, submission.execution.examId);
            select.setInt(3, submission.execution.examVersionNo);

            try (ResultSet resultSet = select.executeQuery()) {
                while (resultSet.next()) {
                    Integer answerId = resultSet.getObject("answer_id", Integer.class);
                    if (answerId == null) {
                        continue;
                    }
                    boolean correct = resultSet.getInt("selected_option_number")
                            == resultSet.getInt("correct_option_number");
                    BigDecimal received = correct
                            ? resultSet.getBigDecimal("score")
                            : BigDecimal.ZERO;
                    if (correct) {
                        total = total.add(received);
                    }

                    update.setBoolean(1, correct);
                    update.setBigDecimal(2, received);
                    update.setInt(3, answerId);
                    update.setInt(4, submission.submissionId);
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("Graded answer update failed");
                    }
                }
            }
        }
        return total;
    }

    private void updateFinalSubmission(Connection connection,
                                       SubmissionRecord submission,
                                       SubmissionStatus finalStatus,
                                       LocalDateTime now,
                                       int actualDurationMinutes,
                                       BigDecimal automaticScore)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                FINALIZE_SUBMISSION_SQL
        )) {
            statement.setObject(1, now);
            statement.setString(2, finalStatus.name());
            statement.setInt(3, actualDurationMinutes);
            statement.setBigDecimal(4, automaticScore);
            statement.setBigDecimal(5, automaticScore);
            statement.setInt(6, submission.submissionId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Exam attempt already submitted");
            }
        }
    }

    private void incrementFinalizationCounter(Connection connection, int executionId,
                                              SubmissionStatus finalStatus)
            throws SQLException {
        String sql = finalStatus == SubmissionStatus.SUBMITTED
                ? INCREMENT_SUBMITTED_COUNT_SQL
                : INCREMENT_AUTO_SUBMITTED_COUNT_SQL;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, executionId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Execution submission count update failed");
            }
        }
    }

    private Optional<ExtensionTarget> lockExtensionTarget(
            Connection connection,
            int authenticatedManagerId,
            int submissionId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_MANAGER_EXTENSION_SQL
        )) {
            statement.setInt(1, authenticatedManagerId);
            statement.setInt(2, submissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ExtensionTarget(
                        resultSet.getObject("started_at", LocalDateTime.class),
                        SubmissionStatus.valueOf(resultSet.getString("status")),
                        resultSet.getInt("allocated_duration_minutes"),
                        resultSet.getInt("extra_minutes")
                ));
            }
        }
    }

    private void updateExtension(Connection connection,
                                 ExtendSubmissionTimePayload payload)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                UPDATE_EXTENSION_SQL
        )) {
            statement.setInt(1, payload.getExtraMinutes());
            statement.setString(2, payload.getReason());
            statement.setInt(3, payload.getSubmissionId());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Submission extension update failed");
            }
        }
    }

    private void insertExtensionAudit(Connection connection, int authenticatedManagerId,
                                      ExtendSubmissionTimePayload payload,
                                      LocalDateTime now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EXTENSION_AUDIT_SQL
        )) {
            statement.setInt(1, payload.getSubmissionId());
            statement.setInt(2, payload.getExtraMinutes());
            statement.setString(3, payload.getReason());
            statement.setInt(4, authenticatedManagerId);
            statement.setObject(5, now);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Submission extension audit insert failed");
            }
        }
    }

    private LocalDateTime deadline(SubmissionRecord submission) {
        return submission.startedAt.plusMinutes(
                (long) submission.allocatedDurationMinutes + submission.extraMinutes
        );
    }

    private boolean isSubmissionCollision(SQLException exception) {
        String message = exception.getMessage();
        return exception.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR
                && "23000".equals(exception.getSQLState())
                && message != null
                && message.toLowerCase(Locale.ROOT)
                .contains("uq_exam_submissions_execution_student");
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

    private static final class LockedExecution {
        private final int executionId;
        private final String executionCode;
        private final int examId;
        private final int examVersionNo;
        private final String examTitle;
        private final String studentInstructions;
        private final LocalDateTime openingTime;
        private final LocalDateTime closingTime;
        private final int durationMinutes;
        private final ExecutionStatus status;

        private LockedExecution(int executionId, String executionCode, int examId,
                                int examVersionNo, String examTitle,
                                String studentInstructions, LocalDateTime openingTime,
                                LocalDateTime closingTime, int durationMinutes,
                                ExecutionStatus status) {
            this.executionId = executionId;
            this.executionCode = executionCode;
            this.examId = examId;
            this.examVersionNo = examVersionNo;
            this.examTitle = examTitle;
            this.studentInstructions = studentInstructions;
            this.openingTime = openingTime;
            this.closingTime = closingTime;
            this.durationMinutes = durationMinutes;
            this.status = status;
        }
    }

    private static final class SubmissionRecord {
        private final int submissionId;
        private final LockedExecution execution;
        private final LocalDateTime startedAt;
        private final SubmissionStatus status;
        private final int allocatedDurationMinutes;
        private final int extraMinutes;

        private SubmissionRecord(int submissionId, LockedExecution execution,
                                 LocalDateTime startedAt, SubmissionStatus status,
                                 int allocatedDurationMinutes, int extraMinutes) {
            this.submissionId = submissionId;
            this.execution = execution;
            this.startedAt = startedAt;
            this.status = status;
            this.allocatedDurationMinutes = allocatedDurationMinutes;
            this.extraMinutes = extraMinutes;
        }

        private SubmissionRecord withStatus(SubmissionStatus newStatus) {
            return new SubmissionRecord(
                    submissionId,
                    execution,
                    startedAt,
                    newStatus,
                    allocatedDurationMinutes,
                    extraMinutes
            );
        }
    }

    private static final class ExtensionTarget {
        private final LocalDateTime startedAt;
        private final SubmissionStatus status;
        private final int allocatedDurationMinutes;
        private final int extraMinutes;

        private ExtensionTarget(LocalDateTime startedAt, SubmissionStatus status,
                                int allocatedDurationMinutes, int extraMinutes) {
            this.startedAt = startedAt;
            this.status = status;
            this.allocatedDurationMinutes = allocatedDurationMinutes;
            this.extraMinutes = extraMinutes;
        }

        private LocalDateTime deadline() {
            return startedAt.plusMinutes((long) allocatedDurationMinutes + extraMinutes);
        }
    }
}
