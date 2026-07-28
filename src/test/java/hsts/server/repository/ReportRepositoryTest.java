package hsts.server.repository;

import hsts.server.entity.Report;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ReportRepositoryTest {
    private static final LocalDateTime OPENING =
            LocalDateTime.of(2026, 7, 1, 9, 0);
    private static final LocalDateTime CLOSING = OPENING.plusHours(2);
    private static final String SCORES_MARKER = "SELECT submission.final_score";
    private static final String LOCK_MARKER = "SELECT execution_id FROM exam_executions";
    private static final String UPDATE_MARKER = "SET average_score = ?";
    private static final String DELETE_MARKER = "DELETE FROM exam_execution_deciles";
    private static final String INSERT_MARKER = "INSERT INTO exam_execution_deciles";

    @Test
    public void teacherReadUsesExamAuthorshipExactVersionAndPublishedFinalScores() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        ExamRepositoryJdbcTestSupport.StatementPlan executions = database
                .plan("exam.created_by_user_id = ?")
                .queryRows(executionRow(12, OPENING.plusDays(1)), executionRow(11, OPENING));
        database.plan(SCORES_MARKER)
                .queryRows(row("final_score", new BigDecimal("40.00")))
                .queryRows(row("final_score", new BigDecimal("20.00")));

        List<Report.ExecutionStatistics> values =
                new ReportRepository(database).findByExamAuthor(1002);

        assertEquals(2, values.size());
        assertEquals(12, values.get(0).getExecutionId());
        assertEquals(11, values.get(1).getExecutionId());
        assertEquals(3, values.get(0).getExamVersionNo());
        assertEquals("Historical Exam", values.get(0).getExamTitle());
        assertEquals(new BigDecimal("40.00"), values.get(0).getAverageScore());
        assertEquals(Map.of(1, 1002), executions.queryExecutions.get(0));
        String sql = normalize(executions.sql);
        assertTrue(sql.contains("EXAM.CREATED_BY_USER_ID = ?"));
        assertFalse(sql.contains("EXECUTION.CREATED_BY_USER_ID = ?"));
        assertTrue(sql.contains("VERSION.VERSION_NO = EXECUTION.EXAM_VERSION_NO"));
        assertFalse(sql.contains("CURRENT_VERSION_NO"));
        assertTrue(sql.contains("ORDER BY EXECUTION.OPENING_TIME ASC"));
        assertThrows(UnsupportedOperationException.class, values::clear);
        assertPublishedScoreSql(database.plans.get(1).sql);
    }

    @Test
    public void courseAndStudentFiltersUseCourseAndPublishedOwnership() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController courseDatabase = database();
        ExamRepositoryJdbcTestSupport.StatementPlan course = courseDatabase
                .plan("exam.course_id = ?")
                .queryRows();
        assertTrue(new ReportRepository(courseDatabase).findByCourse(31).isEmpty());
        assertEquals(Map.of(1, 31), course.queryExecutions.get(0));

        ExamRepositoryJdbcTestSupport.FakeDatabaseController studentDatabase = database();
        ExamRepositoryJdbcTestSupport.StatementPlan student = studentDatabase
                .plan("student_submission.student_user_id = ?")
                .queryRows(executionRow(11, OPENING));
        studentDatabase.plan(SCORES_MARKER)
                .queryRows(row("final_score", new BigDecimal("75.00")));
        List<Report.ExecutionStatistics> values =
                new ReportRepository(studentDatabase).findByStudent(1001);

        assertEquals(1, values.size());
        assertEquals(Map.of(1, 1001), student.queryExecutions.get(0));
        String sql = normalize(student.sql);
        assertTrue(sql.contains("STUDENT_SUBMISSION.STATUS = 'PUBLISHED'"));
        assertTrue(sql.contains("STUDENT_SUBMISSION.FINAL_SCORE IS NOT NULL"));
        assertTrue(sql.contains("EXISTS ("));
    }

    @Test
    public void zeroPublishedExecutionAndMissingDetailAreRepresentedSafely() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController zeroDatabase = database();
        zeroDatabase.plan("execution.execution_id = ?")
                .queryRows(executionRow(11, OPENING));
        zeroDatabase.plan(SCORES_MARKER).queryRows();

        Report.ExecutionStatistics value = new ReportRepository(zeroDatabase)
                .findByExecution(11).orElseThrow();
        assertEquals(0, value.getPublishedSubmissionCount());
        assertNull(value.getAverageScore());
        assertNull(value.getMedianScore());
        assertEquals(10, value.getScoreBands().size());
        assertTrue(value.getScoreBands().stream()
                .allMatch(band -> band.getSubmissionCount() == 0));

        ExamRepositoryJdbcTestSupport.FakeDatabaseController missingDatabase = database();
        missingDatabase.plan("execution.execution_id = ?").queryRows();
        assertTrue(new ReportRepository(missingDatabase)
                .findByExecution(99).isEmpty());
    }

    @Test
    public void exactDecimalMedianAndAllBandBoundariesAreCalculated() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan("execution.execution_id = ?")
                .queryRows(executionRow(11, OPENING));
        database.plan(SCORES_MARKER).queryRows(
                score("0.00"), score("9.99"), score("10.00"),
                score("89.99"), score("90.00"), score("100.00")
        );

        Report.ExecutionStatistics value = new ReportRepository(database)
                .findByExecution(11).orElseThrow();

        assertEquals(new BigDecimal("50.00"), value.getAverageScore());
        assertEquals(new BigDecimal("50.00"), value.getMedianScore());
        assertEquals(2, value.getAverageScore().scale());
        assertEquals(2, value.getMedianScore().scale());
        assertEquals(List.of(2, 1, 0, 0, 0, 0, 0, 0, 1, 2),
                value.getScoreBands().stream()
                        .map(Report.ScoreBand::getSubmissionCount)
                        .toList());

        ExamRepositoryJdbcTestSupport.FakeDatabaseController oddDatabase = database();
        oddDatabase.plan("execution.execution_id = ?")
                .queryRows(executionRow(11, OPENING));
        oddDatabase.plan(SCORES_MARKER).queryRows(
                score("10.00"), score("25.50"), score("90.00")
        );
        assertEquals(new BigDecimal("25.50"), new ReportRepository(oddDatabase)
                .findByExecution(11).orElseThrow().getMedianScore());
    }

    @Test
    public void duplicateMalformedAndSqlFailuresAreRejected() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController duplicateDatabase = database();
        duplicateDatabase.plan("exam.created_by_user_id = ?")
                .queryRows(executionRow(11, OPENING), executionRow(11, OPENING));
        assertThrows(IllegalStateException.class,
                () -> new ReportRepository(duplicateDatabase).findByExamAuthor(1002));

        ExamRepositoryJdbcTestSupport.FakeDatabaseController malformedDatabase = database();
        Map<Object, Object> malformed = executionRow(11, OPENING);
        malformed.put("exam_title", null);
        malformedDatabase.plan("execution.execution_id = ?").queryRows(malformed);
        malformedDatabase.plan(SCORES_MARKER).queryRows();
        assertThrows(IllegalArgumentException.class,
                () -> new ReportRepository(malformedDatabase).findByExecution(11));

        SQLException original = new SQLException("report read failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController failedDatabase = database();
        failedDatabase.plan("exam.course_id = ?").queryFailure(original);
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new ReportRepository(failedDatabase).findByCourse(31)
        );
        assertSame(original, failure.getCause());
    }

    @Test
    public void refreshLocksCalculatesReplacesTenBandsAndCommitsLast() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = refreshDatabase(
                score("10.00"), score("20.00"), score("30.00"), score("40.00")
        );

        new ReportRepository(database).refreshExecutionStatistics(11);

        ExamRepositoryJdbcTestSupport.StatementPlan update = plan(database, UPDATE_MARKER);
        assertEquals(new BigDecimal("25.00"), update.updateExecutions.get(0).get(1));
        assertEquals(new BigDecimal("25.00"), update.updateExecutions.get(0).get(2));
        assertEquals(11, update.updateExecutions.get(0).get(3));
        ExamRepositoryJdbcTestSupport.StatementPlan insert = plan(database, INSERT_MARKER);
        assertEquals(10, insert.updateExecutions.size());
        for (int index = 0; index < 10; index++) {
            assertEquals(11, insert.updateExecutions.get(index).get(1));
            assertEquals(index + 1, insert.updateExecutions.get(index).get(2));
        }
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertTrue(database.autoCommit);
        assertTrue(database.events.indexOf("query:" + LOCK_MARKER)
                < database.events.indexOf("query:" + SCORES_MARKER));
        assertTrue(database.events.lastIndexOf("update:" + INSERT_MARKER)
                < database.events.indexOf("commit"));
        assertEquals(1, plan(database, DELETE_MARKER).updateExecutions.size());
    }

    @Test
    public void zeroRefreshPersistsNullsAndTenZeros() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = refreshDatabase();
        new ReportRepository(database).refreshExecutionStatistics(11);

        Map<Integer, Object> update = plan(database, UPDATE_MARKER).updateExecutions.get(0);
        assertTrue(update.containsKey(1));
        assertNull(update.get(1));
        assertNull(update.get(2));
        assertTrue(plan(database, INSERT_MARKER).updateExecutions.stream()
                .allMatch(values -> Integer.valueOf(0).equals(values.get(3))));
    }

    @Test
    public void repeatedRefreshIsIdempotentAndLeavesExactlyTenRowsEachTime() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan(LOCK_MARKER)
                .queryRows(row("execution_id", 11))
                .queryRows(row("execution_id", 11));
        database.plan(SCORES_MARKER)
                .queryRows(score("25.00"), score("75.00"))
                .queryRows(score("25.00"), score("75.00"));
        database.plan(UPDATE_MARKER).updateResults(1, 1);
        database.plan(DELETE_MARKER).updateResults(3, 10);
        database.plan(INSERT_MARKER);
        ReportRepository repository = new ReportRepository(database);

        repository.refreshExecutionStatistics(11);
        repository.refreshExecutionStatistics(11);

        assertEquals(2, database.commitCount);
        assertEquals(2, plan(database, UPDATE_MARKER).updateExecutions.size());
        assertEquals(2, plan(database, DELETE_MARKER).updateExecutions.size());
        assertEquals(20, plan(database, INSERT_MARKER).updateExecutions.size());
        assertEquals(
                plan(database, UPDATE_MARKER).updateExecutions.get(0),
                plan(database, UPDATE_MARKER).updateExecutions.get(1)
        );
    }

    @Test
    public void invalidIdsFailBeforeConnectingAndMissingExecutionRollsBack() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController invalidDatabase = database();
        ReportRepository repository = new ReportRepository(invalidDatabase);
        assertThrows(IllegalArgumentException.class, () -> repository.findByCourse(0));
        assertThrows(IllegalArgumentException.class,
                () -> repository.findByExamAuthor(-1));
        assertThrows(IllegalArgumentException.class,
                () -> repository.findByStudent(0));
        assertThrows(IllegalArgumentException.class,
                () -> repository.findByExecution(0));
        assertThrows(IllegalArgumentException.class,
                () -> repository.refreshExecutionStatistics(0));
        assertEquals(0, invalidDatabase.connectionRequests);

        ExamRepositoryJdbcTestSupport.FakeDatabaseController missingDatabase = database();
        missingDatabase.plan(LOCK_MARKER).queryRows();
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> new ReportRepository(missingDatabase)
                        .refreshExecutionStatistics(88)
        );
        assertEquals("Exam execution not found: 88", missing.getMessage());
        assertEquals(1, missingDatabase.rollbackCount);
        assertEquals(0, missingDatabase.commitCount);
        assertTrue(missingDatabase.autoCommit);
    }

    @Test
    public void refreshFailureRollsBackRestoresAndSuppressesCleanupFailures() {
        SQLException original = new SQLException("statistics update failed");
        SQLException rollback = new SQLException("rollback failed");
        SQLException restoration = new SQLException("restore failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan(LOCK_MARKER).queryRows(row("execution_id", 11));
        database.plan(SCORES_MARKER).queryRows(score("50.00"));
        database.plan(UPDATE_MARKER).updateFailure(original);
        database.rollbackFailure = rollback;
        database.restorationFailure = restoration;

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new ReportRepository(database).refreshExecutionStatistics(11)
        );

        assertSame(original, failure.getCause());
        assertEquals(2, original.getSuppressed().length);
        assertSame(rollback, original.getSuppressed()[0]);
        assertSame(restoration, original.getSuppressed()[1]);
        assertEquals(0, database.commitCount);
    }

    private static ExamRepositoryJdbcTestSupport.FakeDatabaseController database() {
        return new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
    }

    private static ExamRepositoryJdbcTestSupport.FakeDatabaseController refreshDatabase(
            Map<Object, Object>... scores
    ) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan(LOCK_MARKER).queryRows(row("execution_id", 11));
        database.plan(SCORES_MARKER).queryRows(scores);
        database.plan(UPDATE_MARKER).updateResults(1);
        database.plan(DELETE_MARKER).updateResults(0);
        database.plan(INSERT_MARKER);
        return database;
    }

    private static ExamRepositoryJdbcTestSupport.StatementPlan plan(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database,
            String marker
    ) {
        return database.plans.stream()
                .filter(value -> marker.equals(value.marker))
                .findFirst()
                .orElseThrow();
    }

    private static Map<Object, Object> executionRow(int executionId,
                                                     LocalDateTime opening) {
        return row(
                "execution_id", executionId,
                "exam_id", 21,
                "exam_version_no", 3,
                "execution_code", "AB12",
                "exam_title", "Historical Exam",
                "course_id", 31,
                "course_name", "Algebra",
                "opening_time", opening,
                "closing_time", opening.plusHours(2),
                "started_count", 6,
                "submitted_count", 4,
                "auto_submitted_count", 2
        );
    }

    private static Map<Object, Object> score(String value) {
        return row("final_score", new BigDecimal(value));
    }

    private static void assertPublishedScoreSql(String sql) {
        String normalized = normalize(sql);
        assertTrue(normalized.contains("SUBMISSION.STATUS = 'PUBLISHED'"));
        assertTrue(normalized.contains("SUBMISSION.FINAL_SCORE IS NOT NULL"));
        assertFalse(normalized.contains("AUTOMATIC_SCORE"));
        assertFalse(normalized.contains("CURRENT_VERSION_NO"));
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }
}
