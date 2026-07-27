package hsts.server.repository;

import hsts.common.ExamQuestionSelectionPayload;
import hsts.common.UpdateExamPayload;
import hsts.common.type.ExamStatus;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamRepositoryVersionedUpdateTest {
    private static final String TEACHER_LOCK_MARKER = "AND e.created_by_user_id = ?";
    private static final String QUESTION_MARKER = "FROM questions q";
    private static final String VERSION_MARKER = "INSERT INTO exam_versions (";
    private static final String SELECTION_MARKER = "INSERT INTO exam_version_questions (";
    private static final String POINTER_MARKER = "SET current_version_no = ?";

    @Test
    public void nullPayloadFailsBeforeOpeningConnection() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(database).updateWithNewVersion(1002, null)
        );

        assertEquals("Exam update data is missing", failure.getMessage());
        assertEquals(0, database.connectionRequests);
    }

    @Test
    public void draftRejectedAndApprovedVersionsCreateNewDraftSnapshotsAtomically() {
        for (ExamStatus status : new ExamStatus[]{
                ExamStatus.DRAFT, ExamStatus.REJECTED, ExamStatus.APPROVED
        }) {
            boolean originalAutoCommit = status != ExamStatus.APPROVED;
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                    new ExamRepositoryJdbcTestSupport.FakeDatabaseController(
                            originalAutoCommit
                    );
            UpdatePlans plans = successfulUpdatePlans(database, status);
            UpdateExamPayload payload = payload(4);

            int versionNo = new ExamRepository(database)
                    .updateWithNewVersion(1002, payload);

            assertEquals(5, versionNo);
            assertEquals(1, database.connectionRequests);
            assertEquals(1, database.commitCount);
            assertEquals(0, database.rollbackCount);
            assertEquals(originalAutoCommit, database.autoCommit);
            assertEquals(2, database.autoCommitSetCalls);

            assertEquals(Map.of(1, 1002, 2, 45, 3, 1002),
                    plans.lock.queryExecutions.get(0));
            String lockSql = normalized(plans.lock.sql);
            assertTrue(lockSql.contains("JOIN TEACHER_COURSES TC"));
            assertTrue(lockSql.contains("TC.TEACHER_USER_ID = ?"));
            assertTrue(lockSql.contains("E.CREATED_BY_USER_ID = ?"));
            assertTrue(lockSql.contains("EV.VERSION_NO = E.CURRENT_VERSION_NO"));
            assertTrue(lockSql.contains("FOR UPDATE"));

            assertEquals(2, plans.question.queryExecutions.size());
            assertEquals(Map.of(1, 1002, 2, 4, 3, 17, 4, 7, 5, 4),
                    plans.question.queryExecutions.get(0));
            assertEquals(Map.of(1, 1002, 2, 2, 3, 18, 4, 7, 5, 2),
                    plans.question.queryExecutions.get(1));

            Map<Integer, Object> version = plans.version.updateExecutions.get(0);
            assertEquals(45, version.get(1));
            assertEquals(5, version.get(2));
            assertEquals("Updated midterm", version.get(3));
            assertEquals(120, version.get(4));
            assertEquals("New notes", version.get(5));
            assertEquals("New instructions", version.get(6));
            assertEquals(0, ((BigDecimal) version.get(7))
                    .compareTo(new BigDecimal("100.0")));
            assertEquals("DRAFT", version.get(8));
            assertEquals(1002, version.get(9));
            assertTrue(version.get(10) != null);
            for (int index = 11; index <= 14; index++) {
                assertTrue(version.containsKey(index));
                assertEquals(null, version.get(index));
            }

            assertEquals(2, plans.selection.updateExecutions.size());
            assertSelection(plans.selection.updateExecutions.get(0), 45, 5, 2, 17, 4,
                    new BigDecimal("0.1"));
            assertSelection(plans.selection.updateExecutions.get(1), 45, 5, 1, 18, 2,
                    new BigDecimal("99.9"));
            assertEquals(Map.of(1, 5, 2, 45, 3, 4),
                    plans.pointer.updateExecutions.get(0));

            assertAppearsInOrder(database.events,
                    "query:" + TEACHER_LOCK_MARKER,
                    "query:" + QUESTION_MARKER,
                    "query:" + QUESTION_MARKER,
                    "update:" + VERSION_MARKER,
                    "update:" + SELECTION_MARKER,
                    "update:" + SELECTION_MARKER,
                    "update:" + POINTER_MARKER,
                    "commit"
            );
            assertTrue(plans.version.sql.contains("INSERT INTO exam_versions"));
            assertFalse(plans.pointer.sql.contains("exam_versions"));
        }
    }

    @Test
    public void missingStaleAndPendingExamsPreserveExactDomainMessages() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController missingDatabase = database();
        missingDatabase.plan(TEACHER_LOCK_MARKER).queryRows();
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(missingDatabase)
                        .updateWithNewVersion(1002, payload(4))
        );
        assertEquals("Exam not found: 45", missing.getMessage());
        assertDomainRollback(missingDatabase);

        ExamRepositoryJdbcTestSupport.FakeDatabaseController staleDatabase = database();
        staleDatabase.plan(TEACHER_LOCK_MARKER)
                .queryRows(lockedExam(5, ExamStatus.DRAFT));
        IllegalStateException stale = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(staleDatabase)
                        .updateWithNewVersion(1002, payload(4))
        );
        assertEquals("Exam version conflict", stale.getMessage());
        assertDomainRollback(staleDatabase);

        ExamRepositoryJdbcTestSupport.FakeDatabaseController pendingDatabase = database();
        pendingDatabase.plan(TEACHER_LOCK_MARKER)
                .queryRows(lockedExam(4, ExamStatus.PENDING_APPROVAL));
        IllegalStateException pending = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(pendingDatabase)
                        .updateWithNewVersion(1002, payload(4))
        );
        assertEquals("Pending exam cannot be edited", pending.getMessage());
        assertDomainRollback(pendingDatabase);
    }

    @Test
    public void unavailableOrStaleQuestionStopsBeforeNewVersionInsert() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan(TEACHER_LOCK_MARKER)
                .queryRows(lockedExam(4, ExamStatus.DRAFT));
        database.plan(QUESTION_MARKER).queryRows();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(database)
                        .updateWithNewVersion(1002, payload(4))
        );

        assertEquals("Question unavailable for exam: 17", failure.getMessage());
        assertDomainRollback(database);
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
    }

    @Test
    public void pointerConflictRollsBackNewVersionAndQuestionRows() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        UpdatePlans plans = successfulUpdatePlans(database, ExamStatus.DRAFT);
        plans.pointer.updateOutcomes.clear();
        plans.pointer.updateResults(0);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(database)
                        .updateWithNewVersion(1002, payload(4))
        );

        assertEquals("Exam version conflict", failure.getMessage());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.autoCommit);
    }

    @Test
    public void jdbcAndCleanupFailuresPreserveExactWrapperCauseAndSuppressedFailures() {
        SQLException original = new SQLException("exam lock failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan(TEACHER_LOCK_MARKER).queryFailure(original);
        database.rollbackFailure = rollbackFailure;
        database.restorationFailure = restorationFailure;

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(database)
                        .updateWithNewVersion(1002, payload(4))
        );

        assertEquals("Failed to update exam version", failure.getMessage());
        assertSame(original, failure.getCause());
        assertEquals(2, original.getSuppressed().length);
        assertSame(rollbackFailure, original.getSuppressed()[0]);
        assertSame(restorationFailure, original.getSuppressed()[1]);
        assertEquals(1, database.connectionRequests);
        assertEquals(0, database.commitCount);
    }

    private static UpdatePlans successfulUpdatePlans(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database,
            ExamStatus status) {
        UpdatePlans plans = new UpdatePlans();
        plans.lock = database.plan(TEACHER_LOCK_MARKER)
                .queryRows(lockedExam(4, status));
        plans.question = database.plan(QUESTION_MARKER)
                .queryRows(row("question_id", 17))
                .queryRows(row("question_id", 18));
        plans.version = database.plan(VERSION_MARKER).updateResults(1);
        plans.selection = database.plan(SELECTION_MARKER).updateResults(1, 1);
        plans.pointer = database.plan(POINTER_MARKER).updateResults(1);
        return plans;
    }

    private static ExamRepositoryJdbcTestSupport.FakeDatabaseController database() {
        return new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
    }

    private static Map<Object, Object> lockedExam(int versionNo, ExamStatus status) {
        return row(
                "course_id", 7,
                "current_version_no", versionNo,
                "status", status.name()
        );
    }

    private static UpdateExamPayload payload(int expectedVersionNo) {
        return new UpdateExamPayload(
                45,
                expectedVersionNo,
                "Updated midterm",
                120,
                "New notes",
                "New instructions",
                List.of(
                        new ExamQuestionSelectionPayload(17, 4, 2, 0.1),
                        new ExamQuestionSelectionPayload(18, 2, 1, 99.9)
                )
        );
    }

    private static void assertSelection(Map<Integer, Object> parameters,
                                        int examId, int versionNo, int orderNumber,
                                        int questionId, int questionVersionNo,
                                        BigDecimal score) {
        assertEquals(examId, parameters.get(1));
        assertEquals(versionNo, parameters.get(2));
        assertEquals(orderNumber, parameters.get(3));
        assertEquals(questionId, parameters.get(4));
        assertEquals(questionVersionNo, parameters.get(5));
        assertEquals(0, ((BigDecimal) parameters.get(6)).compareTo(score));
    }

    private static void assertDomainRollback(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database) {
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.autoCommit);
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
    }

    private static void assertAppearsInOrder(List<String> events, String... expectedEvents) {
        int previous = -1;
        for (String expected : expectedEvents) {
            int current = events.subList(previous + 1, events.size()).indexOf(expected);
            assertTrue("Missing or out-of-order event: " + expected, current >= 0);
            previous += current + 1;
        }
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }

    private static final class UpdatePlans {
        private ExamRepositoryJdbcTestSupport.StatementPlan lock;
        private ExamRepositoryJdbcTestSupport.StatementPlan question;
        private ExamRepositoryJdbcTestSupport.StatementPlan version;
        private ExamRepositoryJdbcTestSupport.StatementPlan selection;
        private ExamRepositoryJdbcTestSupport.StatementPlan pointer;
    }
}
