package hsts.server.repository;

import hsts.common.ReportTargetOptionDTO;
import hsts.server.entity.Report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ReportRepository {
    private static final String EXECUTION_COLUMNS = """
            execution.execution_id,
            execution.exam_id,
            execution.exam_version_no,
            execution.execution_code,
            version.title AS exam_title,
            exam.course_id,
            course.name AS course_name,
            execution.opening_time,
            execution.closing_time,
            execution.started_count,
            execution.submitted_count,
            execution.auto_submitted_count
            """;

    private static final String EXECUTION_JOINS = """
            FROM exam_executions execution
            JOIN exams exam ON exam.exam_id = execution.exam_id
            LEFT JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN courses course ON course.course_id = exam.course_id
            """;

    private static final String BY_AUTHOR_SQL = """
            SELECT %s
            %s
            WHERE exam.created_by_user_id = ?
            ORDER BY execution.opening_time ASC,
                     execution.execution_id ASC
            """.formatted(EXECUTION_COLUMNS, EXECUTION_JOINS);

    private static final String BY_COURSE_SQL = """
            SELECT %s
            %s
            WHERE exam.course_id = ?
            ORDER BY execution.opening_time ASC,
                     execution.execution_id ASC
            """.formatted(EXECUTION_COLUMNS, EXECUTION_JOINS);

    private static final String BY_STUDENT_SQL = """
            SELECT %s
            %s
            WHERE EXISTS (
                SELECT 1
                FROM exam_submissions student_submission
                WHERE student_submission.execution_id = execution.execution_id
                  AND student_submission.student_user_id = ?
                  AND student_submission.status = 'PUBLISHED'
                  AND student_submission.final_score IS NOT NULL
            )
            ORDER BY execution.opening_time ASC,
                     execution.execution_id ASC
            """.formatted(EXECUTION_COLUMNS, EXECUTION_JOINS);

    private static final String BY_EXECUTION_SQL = """
            SELECT %s
            %s
            WHERE execution.execution_id = ?
            """.formatted(EXECUTION_COLUMNS, EXECUTION_JOINS);

    private static final String PUBLISHED_SCORES_SQL = """
            SELECT submission.final_score
            FROM exam_submissions submission
            WHERE submission.execution_id = ?
              AND submission.status = 'PUBLISHED'
              AND submission.final_score IS NOT NULL
            ORDER BY submission.final_score ASC,
                     submission.submission_id ASC
            """;

    private static final String LOCK_EXECUTION_SQL = """
            SELECT execution_id
            FROM exam_executions
            WHERE execution_id = ?
            FOR UPDATE
            """;

    private static final String UPDATE_STATISTICS_SQL = """
            UPDATE exam_executions
            SET average_score = ?,
                median_score = ?
            WHERE execution_id = ?
            """;

    private static final String DELETE_DECILES_SQL = """
            DELETE FROM exam_execution_deciles
            WHERE execution_id = ?
            """;

    private static final String INSERT_DECILE_SQL = """
            INSERT INTO exam_execution_deciles (
                execution_id,
                decile_number,
                submission_count
            )
            VALUES (?, ?, ?)
            """;

    private static final int[] LOWER_BOUNDS =
            {0, 10, 20, 30, 40, 50, 60, 70, 80, 90};
    private static final int[] UPPER_BOUNDS =
            {9, 19, 29, 39, 49, 59, 69, 79, 89, 100};

    private final DatabaseController databaseController;

    /**
     * Teachers and coordinators the Principal may report on. Coordinators are
     * included because they author exams too, and getTeacherExamsReport already
     * accepts either role.
     */
    private static final String REPORT_TEACHERS_SQL = """
            SELECT user_id, full_name, email
            FROM users
            WHERE role IN ('TEACHER', 'COORDINATOR')
              AND status = 'ACTIVE'
            ORDER BY full_name
            """;

    private static final String REPORT_COURSES_SQL = """
            SELECT course.course_id, course.name, course.course_code,
                   subject.name AS subject_name
            FROM courses course
            JOIN subjects subject ON subject.subject_id = course.subject_id
            ORDER BY subject.name, course.name
            """;

    private static final String REPORT_STUDENTS_SQL = """
            SELECT user_id, full_name, email
            FROM users
            WHERE role = 'STUDENT'
              AND status = 'ACTIVE'
            ORDER BY full_name
            """;

    private static final String REPORT_EXECUTIONS_SQL = """
            SELECT execution.execution_id, execution.execution_code,
                   version.title AS exam_title, course.name AS course_name
            FROM exam_executions execution
            JOIN exam_versions version
              ON version.exam_id = execution.exam_id
             AND version.version_no = execution.exam_version_no
            JOIN exams exam ON exam.exam_id = execution.exam_id
            JOIN courses course ON course.course_id = exam.course_id
            ORDER BY execution.created_at DESC, execution.execution_id DESC
            """;

    public ReportRepository() {
        this(new DatabaseController());
    }

    public ReportRepository(DatabaseController databaseController) {
        if (databaseController == null) {
            throw new IllegalArgumentException("Database controller is required");
        }
        this.databaseController = databaseController;
    }

    public List<ReportTargetOptionDTO> findReportTeachers() {
        return findTargets(REPORT_TEACHERS_SQL, "user_id", "full_name", "email");
    }

    public List<ReportTargetOptionDTO> findReportStudents() {
        return findTargets(REPORT_STUDENTS_SQL, "user_id", "full_name", "email");
    }

    public List<ReportTargetOptionDTO> findReportCourses() {
        return findTargets(REPORT_COURSES_SQL, "course_id", "name", "course_code");
    }

    public List<ReportTargetOptionDTO> findReportExecutions() {
        return findTargets(
                REPORT_EXECUTIONS_SQL, "execution_id", "exam_title", "execution_code"
        );
    }

    private List<ReportTargetOptionDTO> findTargets(String sql, String idColumn,
                                                    String nameColumn,
                                                    String detailColumn) {
        List<ReportTargetOptionDTO> options = new ArrayList<>();
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                options.add(new ReportTargetOptionDTO(
                        resultSet.getInt(idColumn),
                        resultSet.getString(nameColumn),
                        resultSet.getString(detailColumn)
                ));
            }
            return List.copyOf(options);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load report targets", exception);
        }
    }

    public List<Report.ExecutionStatistics> findByExamAuthor(int teacherUserId) {
        requirePositive(teacherUserId, "Teacher user ID must be positive");
        return findMany(BY_AUTHOR_SQL, teacherUserId,
                "Failed to load teacher report statistics");
    }

    public List<Report.ExecutionStatistics> findByCourse(int courseId) {
        requirePositive(courseId, "Course ID must be positive");
        return findMany(BY_COURSE_SQL, courseId,
                "Failed to load course report statistics");
    }

    public List<Report.ExecutionStatistics> findByStudent(int studentUserId) {
        requirePositive(studentUserId, "Student user ID must be positive");
        return findMany(BY_STUDENT_SQL, studentUserId,
                "Failed to load student report statistics");
    }

    public Optional<Report.ExecutionStatistics> findByExecution(int executionId) {
        requirePositive(executionId, "Execution ID must be positive");
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(BY_EXECUTION_SQL)) {
            statement.setInt(1, executionId);
            List<Report.ExecutionStatistics> values = loadExecutions(connection, statement);
            if (values.isEmpty()) {
                return Optional.empty();
            }
            if (values.size() != 1) {
                throw new IllegalStateException("Duplicate report execution: " + executionId);
            }
            return Optional.of(values.get(0));
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to load execution report statistics", exception
            );
        }
    }

    public void refreshExecutionStatistics(int executionId) {
        requirePositive(executionId, "Execution ID must be positive");
        try (Connection connection = databaseController.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean transactionStarted = false;
            boolean committed = false;
            Throwable failure = null;
            try {
                connection.setAutoCommit(false);
                transactionStarted = true;
                refreshExecutionStatistics(connection, executionId);
                connection.commit();
                committed = true;
            } catch (SQLException exception) {
                failure = exception;
                if (transactionStarted && !committed) {
                    rollbackWithSuppressed(connection, exception);
                }
                throw new IllegalStateException(
                        "Failed to refresh execution statistics", exception
                );
            } catch (RuntimeException exception) {
                failure = exception;
                if (transactionStarted && !committed) {
                    rollbackWithSuppressed(connection, exception);
                }
                throw exception;
            } finally {
                restoreAutoCommit(connection, originalAutoCommit, failure);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to refresh execution statistics", exception
            );
        }
    }

    void refreshExecutionStatistics(Connection connection, int executionId)
            throws SQLException {
        requirePositive(executionId, "Execution ID must be positive");
        lockExecution(connection, executionId);
        CalculatedStatistics statistics = calculate(loadPublishedScores(
                connection, executionId
        ));
        updateStatistics(connection, executionId, statistics);
        replaceBands(connection, executionId, statistics.bandCounts);
    }

    private List<Report.ExecutionStatistics> findMany(String sql, int parameter,
                                                       String failureMessage) {
        try (Connection connection = databaseController.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, parameter);
            return loadExecutions(connection, statement);
        } catch (SQLException exception) {
            throw new IllegalStateException(failureMessage, exception);
        }
    }

    private List<Report.ExecutionStatistics> loadExecutions(
            Connection connection, PreparedStatement statement
    ) throws SQLException {
        List<ExecutionRow> rows = new ArrayList<>();
        Set<Integer> executionIds = new HashSet<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ExecutionRow row = mapExecutionRow(resultSet);
                if (!executionIds.add(row.executionId)) {
                    throw new IllegalStateException(
                            "Duplicate report execution: " + row.executionId
                    );
                }
                rows.add(row);
            }
        }

        List<Report.ExecutionStatistics> values = new ArrayList<>(rows.size());
        for (ExecutionRow row : rows) {
            values.add(toStatistics(row, calculate(loadPublishedScores(
                    connection, row.executionId
            ))));
        }
        return List.copyOf(values);
    }

    private ExecutionRow mapExecutionRow(ResultSet resultSet) throws SQLException {
        return new ExecutionRow(
                resultSet.getInt("execution_id"),
                resultSet.getInt("exam_id"),
                resultSet.getInt("exam_version_no"),
                resultSet.getString("execution_code"),
                resultSet.getString("exam_title"),
                resultSet.getInt("course_id"),
                resultSet.getString("course_name"),
                resultSet.getObject("opening_time", LocalDateTime.class),
                resultSet.getObject("closing_time", LocalDateTime.class),
                resultSet.getInt("started_count"),
                resultSet.getInt("submitted_count"),
                resultSet.getInt("auto_submitted_count")
        );
    }

    private List<BigDecimal> loadPublishedScores(Connection connection,
                                                  int executionId)
            throws SQLException {
        List<BigDecimal> scores = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                PUBLISHED_SCORES_SQL
        )) {
            statement.setInt(1, executionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    BigDecimal score = resultSet.getBigDecimal("final_score");
                    if (score == null
                            || score.compareTo(BigDecimal.ZERO) < 0
                            || score.compareTo(new BigDecimal("100.00")) > 0) {
                        throw new IllegalStateException(
                                "Published report score is invalid: " + executionId
                        );
                    }
                    scores.add(score);
                }
            }
        }
        return List.copyOf(scores);
    }

    private CalculatedStatistics calculate(List<BigDecimal> scores) {
        int[] bandCounts = new int[10];
        if (scores.isEmpty()) {
            return new CalculatedStatistics(null, null, bandCounts);
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal score : scores) {
            sum = sum.add(score);
            bandCounts[bandIndex(score)]++;
        }
        BigDecimal average = sum.divide(
                BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP
        );
        int middle = scores.size() / 2;
        BigDecimal median = scores.size() % 2 == 1
                ? scores.get(middle).setScale(2, RoundingMode.HALF_UP)
                : scores.get(middle - 1).add(scores.get(middle))
                        .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return new CalculatedStatistics(average, median, bandCounts);
    }

    private int bandIndex(BigDecimal score) {
        int integerPart = score.intValue();
        return integerPart >= 90 ? 9 : integerPart / 10;
    }

    private void lockExecution(Connection connection, int executionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                LOCK_EXECUTION_SQL
        )) {
            statement.setInt(1, executionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException(
                            "Exam execution not found: " + executionId
                    );
                }
                if (resultSet.next()) {
                    throw new IllegalStateException(
                            "Duplicate exam execution: " + executionId
                    );
                }
            }
        }
    }

    private void updateStatistics(Connection connection, int executionId,
                                  CalculatedStatistics statistics)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                UPDATE_STATISTICS_SQL
        )) {
            setNullableDecimal(statement, 1, statistics.average);
            setNullableDecimal(statement, 2, statistics.median);
            statement.setInt(3, executionId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "Execution statistics update did not affect exactly one row"
                );
            }
        }
    }

    private void replaceBands(Connection connection, int executionId,
                              int[] bandCounts) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(DELETE_DECILES_SQL)) {
            delete.setInt(1, executionId);
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(INSERT_DECILE_SQL)) {
            for (int index = 0; index < bandCounts.length; index++) {
                insert.setInt(1, executionId);
                insert.setInt(2, index + 1);
                insert.setInt(3, bandCounts[index]);
                if (insert.executeUpdate() != 1) {
                    throw new SQLException(
                            "Execution score band insert did not affect exactly one row"
                    );
                }
            }
        }
    }

    private void setNullableDecimal(PreparedStatement statement, int index,
                                    BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private static void requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void rollbackWithSuppressed(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private void restoreAutoCommit(Connection connection, boolean originalAutoCommit,
                                   Throwable failure) throws SQLException {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException restorationFailure) {
            if (failure != null) {
                failure.addSuppressed(restorationFailure);
                return;
            }
            throw restorationFailure;
        }
    }

    private List<Report.ScoreBand> toBands(int[] bandCounts) {
        List<Report.ScoreBand> bands = new ArrayList<>(10);
        for (int index = 0; index < 10; index++) {
            bands.add(new Report.ScoreBand(
                    LOWER_BOUNDS[index], UPPER_BOUNDS[index], bandCounts[index]
            ));
        }
        return List.copyOf(bands);
    }

    private Report.ExecutionStatistics toStatistics(
            ExecutionRow row, CalculatedStatistics calculated
    ) {
        int publishedCount = 0;
        for (int count : calculated.bandCounts) {
            publishedCount += count;
        }
        return new Report.ExecutionStatistics(
                row.executionId, row.examId, row.examVersionNo,
                row.executionCode, row.examTitle, row.courseId, row.courseName,
                row.openingTime, row.closingTime, publishedCount,
                calculated.average, calculated.median,
                toBands(calculated.bandCounts), row.startedCount,
                row.submittedCount, row.autoSubmittedCount
        );
    }

    private record CalculatedStatistics(BigDecimal average, BigDecimal median,
                                        int[] bandCounts) {
    }

    private record ExecutionRow(
            int executionId,
            int examId,
            int examVersionNo,
            String executionCode,
            String examTitle,
            int courseId,
            String courseName,
            LocalDateTime openingTime,
            LocalDateTime closingTime,
            int startedCount,
            int submittedCount,
            int autoSubmittedCount
    ) {
    }
}
