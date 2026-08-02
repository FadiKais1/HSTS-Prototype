package hsts.server.repository;

import hsts.common.ExamExecutionPreviewDTO;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.type.ExecutionStatus;
import hsts.common.type.NotificationType;
import hsts.server.entity.ExamExecution;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public class ExamExecutionRepository {
    private static final String EXECUTION_CODE_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int EXECUTION_CODE_LENGTH = 4;
    private static final int MAX_EXECUTION_CODE_ATTEMPTS = 5;
    private static final int MYSQL_DUPLICATE_KEY_ERROR = 1062;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter NOTIFICATION_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String LOCK_APPROVED_EXAM_VERSION_SQL = """
            SELECT e.exam_id,
                   ev.version_no,
                   ev.duration_minutes,
                   ev.title AS exam_title,
                   e.course_id,
                   course.name AS course_name
            FROM exams e
            JOIN exam_versions ev
              ON ev.exam_id = e.exam_id
             AND ev.version_no = ?
            JOIN courses course ON course.course_id = e.course_id
            JOIN teacher_courses tc
              ON tc.course_id = e.course_id
             AND tc.teacher_user_id = ?
            JOIN users manager
              ON manager.user_id = tc.teacher_user_id
             AND manager.role IN ('TEACHER', 'COORDINATOR')
             AND manager.status = 'ACTIVE'
            WHERE e.exam_id = ?
              AND ev.status = 'APPROVED'
            FOR UPDATE
            """;

    private static final String INSERT_EXECUTION_SQL = """
            INSERT INTO exam_executions (
                execution_code,
                exam_id,
                exam_version_no,
                opening_time,
                closing_time,
                duration_minutes,
                status,
                created_by_user_id,
                created_at,
                closed_at,
                average_score,
                median_score,
                started_count,
                submitted_count,
                auto_submitted_count,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SCHEDULE_NOTIFICATION_RECIPIENTS_SQL = """
            SELECT student.user_id
            FROM student_courses enrollment
            JOIN users student
              ON student.user_id = enrollment.student_user_id
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            WHERE enrollment.course_id = ?
            ORDER BY student.user_id
            """;

    private static final String EXECUTION_ENTITY_SELECT = """
            SELECT execution.execution_id,
                   execution.execution_code,
                   execution.exam_id,
                   execution.exam_version_no,
                   execution.opening_time,
                   execution.closing_time,
                   execution.duration_minutes,
                   execution.cumulative_extension_minutes,
                   execution.status,
                   execution.created_by_user_id,
                   execution.created_at,
                   execution.updated_at,
                   execution.closed_at,
                   execution.average_score,
                   execution.median_score,
                   execution.started_count,
                   execution.submitted_count,
                   execution.auto_submitted_count
            FROM exam_executions execution
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN exams exam ON exam.exam_id = execution.exam_id
            """;

    private static final String MANAGER_EXECUTION_ENTITY_SQL = EXECUTION_ENTITY_SELECT + """
            JOIN users manager
              ON manager.user_id = ?
             AND manager.role IN ('TEACHER', 'COORDINATOR')
             AND manager.status = 'ACTIVE'
            JOIN teacher_courses assignment
              ON assignment.teacher_user_id = manager.user_id
             AND assignment.course_id = exam.course_id
            WHERE execution.created_by_user_id = ?
              AND execution.execution_id = ?
            """;

    private static final String STUDENT_EXECUTION_ENTITY_SELECT = EXECUTION_ENTITY_SELECT + """
            JOIN users student
              ON student.user_id = ?
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            JOIN student_courses enrollment
              ON enrollment.student_user_id = student.user_id
             AND enrollment.course_id = exam.course_id
            """;

    private static final String STUDENT_EXECUTION_ENTITY_SQL =
            STUDENT_EXECUTION_ENTITY_SELECT + """
            WHERE execution.execution_code = ?
            """;

    private static final String STUDENT_EXECUTION_ENTITY_BY_ID_SQL =
            STUDENT_EXECUTION_ENTITY_SELECT + """
            WHERE execution.execution_id = ?
            """;

    private static final String EXECUTION_DECILES_SQL = """
            SELECT decile_number,
                   submission_count
            FROM exam_execution_deciles
            WHERE execution_id = ?
            ORDER BY decile_number ASC
            """;

    private static final String EXECUTION_SUMMARY_SELECT = """
            SELECT execution.execution_id,
                   execution.execution_code,
                   execution.exam_id,
                   execution.exam_version_no,
                   exam.exam_code,
                   version.title AS exam_title,
                   exam.course_id,
                   course.name AS course_name,
                   execution.opening_time,
                   execution.closing_time,
                   execution.duration_minutes,
                   execution.cumulative_extension_minutes,
                   execution.status,
                   execution.created_by_user_id,
                   creator.full_name AS creator_name,
                   execution.created_at,
                   execution.started_count,
                   execution.submitted_count,
                   execution.auto_submitted_count
            FROM exam_executions execution
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN courses course ON course.course_id = exam.course_id
            JOIN users creator ON creator.user_id = execution.created_by_user_id
            JOIN users manager
              ON manager.user_id = ?
             AND manager.role IN ('TEACHER', 'COORDINATOR')
             AND manager.status = 'ACTIVE'
            JOIN teacher_courses assignment
              ON assignment.teacher_user_id = manager.user_id
             AND assignment.course_id = exam.course_id
            """;

    private static final String MANAGER_EXECUTION_LIST_SQL = EXECUTION_SUMMARY_SELECT + """
            WHERE execution.created_by_user_id = ?
            ORDER BY execution.created_at DESC, execution.execution_id DESC
            """;

    private static final String MANAGER_EXECUTION_DETAIL_SQL = EXECUTION_SUMMARY_SELECT + """
            WHERE execution.created_by_user_id = ?
              AND execution.execution_id = ?
            """;

    private static final String PRINCIPAL_EXECUTION_LIST_SQL = """
            SELECT execution.execution_id, execution.execution_code,
                   execution.exam_id, execution.exam_version_no,
                   exam.exam_code, version.title AS exam_title,
                   exam.course_id, course.name AS course_name,
                   execution.opening_time, execution.closing_time,
                   execution.duration_minutes, execution.status,
                   execution.created_by_user_id,
                   creator.full_name AS creator_name, execution.created_at,
                   execution.started_count, execution.submitted_count,
                   execution.auto_submitted_count
            FROM exam_executions execution
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN courses course ON course.course_id = exam.course_id
            JOIN users creator ON creator.user_id = execution.created_by_user_id
            ORDER BY execution.created_at DESC, execution.execution_id DESC
            """;

    private static final String STUDENT_EXECUTION_PREVIEW_SQL = """
            SELECT execution.execution_id,
                   execution.execution_code,
                   execution.exam_id,
                   execution.exam_version_no,
                   version.title AS exam_title,
                   exam.course_id,
                   course.name AS course_name,
                   execution.opening_time,
                   execution.closing_time,
                   execution.duration_minutes,
                   execution.status,
                   CASE
                       WHEN submission.submission_id IS NOT NULL
                        AND submission.status = 'IN_PROGRESS' THEN 1
                       ELSE 0
                   END AS resumable
            FROM exam_executions execution
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN courses course ON course.course_id = exam.course_id
            JOIN users student
              ON student.user_id = ?
             AND student.role = 'STUDENT'
             AND student.status = 'ACTIVE'
            JOIN student_courses enrollment
              ON enrollment.student_user_id = student.user_id
             AND enrollment.course_id = exam.course_id
            LEFT JOIN exam_submissions submission
              ON submission.execution_id = execution.execution_id
             AND submission.student_user_id = ?
            WHERE execution.execution_code = ?
            """;

    private final DatabaseController databaseController;
    private final Supplier<String> executionCodeGenerator;

    public ExamExecutionRepository() {
        this(new DatabaseController());
    }

    public ExamExecutionRepository(DatabaseController databaseController) {
        this(databaseController, ExamExecutionRepository::generateExecutionCode);
    }

    ExamExecutionRepository(DatabaseController databaseController,
                            Supplier<String> executionCodeGenerator) {
        this.databaseController = databaseController;
        this.executionCodeGenerator = executionCodeGenerator;
    }

    public ExamExecution schedule(int authenticatedUserId, int examId,
                                  int examVersionNo, LocalDateTime openingTime,
                                  LocalDateTime closingTime) {
        return schedule(authenticatedUserId, examId, examVersionNo,
                openingTime, closingTime, null);
    }

    /**
     * Schedules an execution using the code chosen by the teacher.
     *
     * <p>A {@code null} code means the server picks one, which keeps the
     * generated behaviour available. A supplied code is used exactly as given:
     * codes are unique across executions, so a clash is reported rather than
     * silently replaced.</p>
     */
    public ExamExecution schedule(int authenticatedUserId, int examId,
                                  int examVersionNo, LocalDateTime openingTime,
                                  LocalDateTime closingTime,
                                  String requestedExecutionCode) {
        if (openingTime == null || closingTime == null) {
            throw new IllegalArgumentException("Opening and closing times are required");
        }
        if (!closingTime.isAfter(openingTime)) {
            throw new IllegalArgumentException(
                    "Execution closing time must be after opening time"
            );
        }

        return persistSchedule(
                authenticatedUserId,
                new SchedulingCommand(
                        examId,
                        examVersionNo,
                        openingTime,
                        closingTime,
                        requestedExecutionCode
                )
        );
    }

    private ExamExecution persistSchedule(int authenticatedManagerId,
                                          SchedulingCommand command) {

        try (Connection connection = databaseController.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean transactionStarted = false;
            Throwable transactionFailure = null;

            try {
                connection.setAutoCommit(false);
                transactionStarted = true;

                ScheduleDetails scheduleDetails = loadApprovedScheduleDetails(
                        connection,
                        authenticatedManagerId,
                        command
                );
                LocalDateTime createdAt = LocalDateTime.now();
                ExamExecution execution = insertWithUniqueCode(
                        connection,
                        authenticatedManagerId,
                        command,
                        scheduleDetails.durationMinutes(),
                        createdAt
                );
                insertScheduleNotifications(
                        connection,
                        execution,
                        scheduleDetails,
                        createdAt
                );
                connection.commit();
                return execution;
            } catch (SQLException exception) {
                transactionFailure = exception;
                if (transactionStarted) {
                    rollbackWithSuppressed(connection, exception);
                }
                throw new IllegalStateException("Failed to schedule exam execution", exception);
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
            throw new IllegalStateException("Failed to schedule exam execution", exception);
        }
    }

    public Optional<ExamExecution> findEntityForManager(
            int authenticatedUserId,
            int executionId
    ) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     MANAGER_EXECUTION_ENTITY_SQL
             )) {
            statement.setInt(1, authenticatedUserId);
            statement.setInt(2, authenticatedUserId);
            statement.setInt(3, executionId);
            return loadEntity(connection, statement);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load manager exam execution entity",
                    exception
            );
        }
    }

    public Optional<ExamExecution> findEntityByCodeForStudent(
            int authenticatedUserId,
            String executionCode
    ) {
        String normalizedCode = normalizeExecutionCode(executionCode);
        if (normalizedCode == null) {
            return Optional.empty();
        }

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     STUDENT_EXECUTION_ENTITY_SQL
             )) {
            statement.setInt(1, authenticatedUserId);
            statement.setString(2, normalizedCode);
            return loadEntity(connection, statement);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load student exam execution entity",
                    exception
            );
        }
    }

    public Optional<ExamExecution> findEntityForStudent(
            int authenticatedStudentUserId,
            int executionId
    ) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     STUDENT_EXECUTION_ENTITY_BY_ID_SQL
             )) {
            statement.setInt(1, authenticatedStudentUserId);
            statement.setInt(2, executionId);
            return loadEntity(connection, statement);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load student exam execution entity",
                    exception
            );
        }
    }

    public List<ExamExecutionSummaryDTO> findCreatedByManager(int authenticatedManagerId) {
        List<ExamExecutionSummaryDTO> executions = new ArrayList<>();

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     MANAGER_EXECUTION_LIST_SQL
             )) {
            statement.setInt(1, authenticatedManagerId);
            statement.setInt(2, authenticatedManagerId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    executions.add(mapSummary(resultSet));
                }
            }
            return executions;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load exam executions", exception);
        }
    }

    public List<ExamExecutionSummaryDTO> findAllForPrincipal() {
        List<ExamExecutionSummaryDTO> executions = new ArrayList<>();
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     PRINCIPAL_EXECUTION_LIST_SQL
             ); ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                executions.add(mapSummary(resultSet));
            }
            return List.copyOf(executions);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load Principal exam executions", exception
            );
        }
    }

    public Optional<ExamExecutionSummaryDTO> findByIdForManager(
            int authenticatedManagerId,
            int executionId
    ) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     MANAGER_EXECUTION_DETAIL_SQL
             )) {
            statement.setInt(1, authenticatedManagerId);
            statement.setInt(2, authenticatedManagerId);
            statement.setInt(3, executionId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapSummary(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load exam execution", exception);
        }
    }

    public Optional<ExamExecutionPreviewDTO> findByCodeForStudent(
            int authenticatedStudentId,
            String normalizedExecutionCode
    ) {
        if (normalizedExecutionCode == null
                || !normalizedExecutionCode.matches("[A-Z0-9]{4}")) {
            return Optional.empty();
        }

        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     STUDENT_EXECUTION_PREVIEW_SQL
             )) {
            statement.setInt(1, authenticatedStudentId);
            statement.setInt(2, authenticatedStudentId);
            statement.setString(3, normalizedExecutionCode);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapPreview(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to validate execution code", exception);
        }
    }

    private ScheduleDetails loadApprovedScheduleDetails(
            Connection connection,
            int authenticatedManagerId,
            SchedulingCommand command
    )
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_APPROVED_EXAM_VERSION_SQL
        )) {
            statement.setInt(1, command.examVersionNo());
            statement.setInt(2, authenticatedManagerId);
            statement.setInt(3, command.examId());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException(
                            "Exam version is not approved or accessible"
                    );
                }
                return new ScheduleDetails(
                        resultSet.getInt("duration_minutes"),
                        resultSet.getString("exam_title"),
                        resultSet.getInt("course_id"),
                        resultSet.getString("course_name")
                );
            }
        }
    }

    private void insertScheduleNotifications(
            Connection connection,
            ExamExecution execution,
            ScheduleDetails details,
            LocalDateTime createdAt
    ) throws SQLException {
        List<Integer> recipients = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                SCHEDULE_NOTIFICATION_RECIPIENTS_SQL
        )) {
            statement.setInt(1, details.courseId());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    recipients.add(resultSet.getInt("user_id"));
                }
            }
        }

        String message = details.examTitle() + " for " + details.courseName()
                + " is scheduled. Execution code: " + execution.getExecutionCode()
                + ". Opens: " + NOTIFICATION_TIME_FORMAT.format(
                        execution.getOpeningTime())
                + ". Closes: " + NOTIFICATION_TIME_FORMAT.format(
                        execution.getClosingTime())
                + ". Duration: " + execution.getDurationMinutes() + " minutes.";
        for (Integer recipientUserId : recipients) {
            NotificationRepository.insert(
                    connection,
                    recipientUserId,
                    NotificationType.EXAM_SCHEDULED,
                    "Exam scheduled",
                    message,
                    execution.getExamId(),
                    execution.getExecutionId(),
                    null,
                    "exam-scheduled:" + execution.getExecutionId()
                            + ":" + recipientUserId,
                    createdAt
            );
        }
    }

    private ExamExecution insertWithUniqueCode(Connection connection,
                                               int authenticatedManagerId,
                                               SchedulingCommand command,
                                               int durationMinutes,
                                               LocalDateTime createdAt)
            throws SQLException {
        SQLException lastCollision = null;
        String requestedCode = command.requestedExecutionCode();
        boolean teacherChoseCode = requestedCode != null && !requestedCode.isBlank();

        // A generated code may clash by chance, so generation is retried. A code
        // the teacher typed must not be silently swapped for another one: the
        // clash is reported so they can choose a different code.
        int attempts = teacherChoseCode ? 1 : MAX_EXECUTION_CODE_ATTEMPTS;

        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                ExamExecution proposedExecution = ExamExecution.schedule(
                        command.examId(),
                        command.examVersionNo(),
                        teacherChoseCode
                                ? requestedCode.trim().toUpperCase(java.util.Locale.ROOT)
                                : executionCodeGenerator.get(),
                        command.openingTime(),
                        command.closingTime(),
                        durationMinutes,
                        authenticatedManagerId,
                        createdAt
                );
                return insertExecution(
                        connection,
                        proposedExecution
                );
            } catch (SQLException exception) {
                if (!isExecutionCodeCollision(exception)) {
                    throw exception;
                }
                lastCollision = exception;
            }
        }

        if (teacherChoseCode) {
            throw new IllegalStateException(
                    "Execution code " + requestedCode.trim().toUpperCase(java.util.Locale.ROOT)
                            + " is already in use. Choose a different code."
            );
        }
        throw lastCollision;
    }

    private ExamExecution insertExecution(Connection connection,
                                          ExamExecution proposedExecution)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_EXECUTION_SQL,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, proposedExecution.getExecutionCode());
            statement.setInt(2, proposedExecution.getExamId());
            statement.setInt(3, proposedExecution.getExamVersionNo());
            statement.setObject(4, proposedExecution.getOpeningTime());
            statement.setObject(5, proposedExecution.getClosingTime());
            statement.setInt(6, proposedExecution.getDurationMinutes());
            statement.setString(7, proposedExecution.getStatus().name());
            statement.setInt(8, proposedExecution.getCreatedByUserId());
            statement.setObject(9, proposedExecution.getCreatedAt());
            statement.setNull(10, Types.TIMESTAMP);
            statement.setNull(11, Types.DECIMAL);
            statement.setNull(12, Types.DECIMAL);
            statement.setInt(13, 0);
            statement.setInt(14, 0);
            statement.setInt(15, 0);
            statement.setObject(16, proposedExecution.getUpdatedAt());

            if (statement.executeUpdate() != 1) {
                throw new SQLException("Execution insert did not affect exactly one row");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new SQLException("Execution insert did not return a generated key");
                }

                int executionId = generatedKeys.getInt(1);
                if (executionId <= 0) {
                    throw new SQLException("Execution insert returned an invalid generated key");
                }
                return ExamExecution.rehydrate(
                        executionId,
                        proposedExecution.getExecutionCode(),
                        proposedExecution.getExamId(),
                        proposedExecution.getExamVersionNo(),
                        proposedExecution.getOpeningTime(),
                        proposedExecution.getClosingTime(),
                        proposedExecution.getDurationMinutes(),
                        proposedExecution.getStatus(),
                        proposedExecution.getCreatedByUserId(),
                        proposedExecution.getCreatedAt(),
                        null,
                        null,
                        null,
                        List.of(),
                        0,
                        0,
                        0,
                        proposedExecution.getUpdatedAt(),
                        List.of()
                );
            }
        }
    }

    private Optional<ExamExecution> loadEntity(Connection connection,
                                               PreparedStatement statement)
            throws SQLException {
        ExecutionEntityData data;
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            data = mapEntityData(resultSet);
        }

        return Optional.of(data.rehydrate(loadDecileDistribution(
                connection,
                data.executionId()
        )));
    }

    private ExecutionEntityData mapEntityData(ResultSet resultSet) throws SQLException {
        int executionId = resultSet.getInt("execution_id");
        return new ExecutionEntityData(
                executionId,
                resultSet.getString("execution_code"),
                resultSet.getInt("exam_id"),
                resultSet.getInt("exam_version_no"),
                resultSet.getObject("opening_time", LocalDateTime.class),
                resultSet.getObject("closing_time", LocalDateTime.class),
                resultSet.getInt("duration_minutes"),
                resultSet.getInt("cumulative_extension_minutes"),
                parseExecutionStatus(resultSet.getString("status"), executionId),
                resultSet.getInt("created_by_user_id"),
                resultSet.getObject("created_at", LocalDateTime.class),
                resultSet.getObject("updated_at", LocalDateTime.class),
                resultSet.getObject("closed_at", LocalDateTime.class),
                resultSet.getBigDecimal("average_score"),
                resultSet.getBigDecimal("median_score"),
                resultSet.getInt("started_count"),
                resultSet.getInt("submitted_count"),
                resultSet.getInt("auto_submitted_count")
        );
    }

    private List<Integer> loadDecileDistribution(Connection connection,
                                                  int executionId)
            throws SQLException {
        List<Integer> distribution = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(
                EXECUTION_DECILES_SQL
        )) {
            statement.setInt(1, executionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                int expectedDecile = 1;
                while (resultSet.next()) {
                    if (resultSet.getInt("decile_number") != expectedDecile) {
                        throw new IllegalArgumentException(
                                "Execution decile distribution is invalid: " + executionId
                        );
                    }
                    distribution.add(resultSet.getInt("submission_count"));
                    expectedDecile++;
                }
            }
        }

        if (!distribution.isEmpty() && distribution.size() != 10) {
            throw new IllegalArgumentException(
                    "Execution decile distribution is invalid: " + executionId
            );
        }
        return List.copyOf(distribution);
    }

    private ExecutionStatus parseExecutionStatus(String value, int executionId) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Execution status is missing: " + executionId
            );
        }
        try {
            return ExecutionStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Execution status is invalid: " + executionId,
                    exception
            );
        }
    }

    private String normalizeExecutionCode(String executionCode) {
        if (executionCode == null) {
            return null;
        }
        String normalized = executionCode.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z0-9]{4}") ? normalized : null;
    }

    private ExamExecutionSummaryDTO mapSummary(ResultSet resultSet) throws SQLException {
        return new ExamExecutionSummaryDTO(
                resultSet.getInt("execution_id"),
                resultSet.getString("execution_code"),
                resultSet.getInt("exam_id"),
                resultSet.getInt("exam_version_no"),
                resultSet.getString("exam_code"),
                resultSet.getString("exam_title"),
                resultSet.getInt("course_id"),
                resultSet.getString("course_name"),
                resultSet.getObject("opening_time", LocalDateTime.class),
                resultSet.getObject("closing_time", LocalDateTime.class),
                resultSet.getInt("duration_minutes"),
                resultSet.getInt("cumulative_extension_minutes"),
                ExecutionStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("created_by_user_id"),
                resultSet.getString("creator_name"),
                resultSet.getObject("created_at", LocalDateTime.class),
                resultSet.getInt("started_count"),
                resultSet.getInt("submitted_count"),
                resultSet.getInt("auto_submitted_count")
        );
    }

    private ExamExecutionPreviewDTO mapPreview(ResultSet resultSet) throws SQLException {
        return new ExamExecutionPreviewDTO(
                resultSet.getInt("execution_id"),
                resultSet.getString("execution_code"),
                resultSet.getInt("exam_id"),
                resultSet.getInt("exam_version_no"),
                resultSet.getString("exam_title"),
                resultSet.getInt("course_id"),
                resultSet.getString("course_name"),
                resultSet.getObject("opening_time", LocalDateTime.class),
                resultSet.getObject("closing_time", LocalDateTime.class),
                resultSet.getInt("duration_minutes"),
                ExecutionStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("resumable") == 1
        );
    }

    private boolean isExecutionCodeCollision(SQLException exception) {
        String message = exception.getMessage();
        return exception.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR
                && "23000".equals(exception.getSQLState())
                && message != null
                && message.toLowerCase(Locale.ROOT).contains("uq_exam_executions_code");
    }

    private static String generateExecutionCode() {
        StringBuilder code = new StringBuilder(EXECUTION_CODE_LENGTH);
        for (int index = 0; index < EXECUTION_CODE_LENGTH; index++) {
            code.append(EXECUTION_CODE_CHARACTERS.charAt(
                    SECURE_RANDOM.nextInt(EXECUTION_CODE_CHARACTERS.length())
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

    private record SchedulingCommand(int examId, int examVersionNo,
                                     LocalDateTime openingTime,
                                     LocalDateTime closingTime,
                                     String requestedExecutionCode) {
    }

    private record ScheduleDetails(int durationMinutes, String examTitle,
                                   int courseId, String courseName) {
    }

    private record ExecutionEntityData(
            int executionId,
            String executionCode,
            int examId,
            int examVersionNo,
            LocalDateTime openingTime,
            LocalDateTime closingTime,
            int durationMinutes,
            int cumulativeExtensionMinutes,
            ExecutionStatus status,
            int createdByUserId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime closedAt,
            java.math.BigDecimal averageScore,
            java.math.BigDecimal medianScore,
            int startedCount,
            int submittedCount,
            int autoSubmittedCount
    ) {
        private ExamExecution rehydrate(List<Integer> decileDistribution) {
            return ExamExecution.rehydrate(
                    executionId,
                    executionCode,
                    examId,
                    examVersionNo,
                    openingTime,
                    closingTime,
                    durationMinutes,
                    cumulativeExtensionMinutes,
                    status,
                    createdByUserId,
                    createdAt,
                    closedAt,
                    averageScore,
                    medianScore,
                    decileDistribution,
                    startedCount,
                    submittedCount,
                    autoSubmittedCount,
                    updatedAt,
                    List.of()
            );
        }
    }
}
