package hsts.server.repository;

import hsts.common.type.ExamStatus;
import hsts.server.entity.Exam;
import hsts.server.entity.ExamQuestion;
import hsts.server.entity.Question;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamRepositoryWorkflowTransitionTest {
    private static final String TEACHER_LOCK_MARKER = "AND e.created_by_user_id = ?";
    private static final String COORDINATOR_LOCK_MARKER = "JOIN subject_coordinators sc";
    private static final String SUBMIT_MARKER = "submitted_at = ?";
    private static final String APPROVE_MARKER = "rejection_reason = NULL";
    private static final String REJECT_MARKER = "rejection_reason = ?";
    private static final String NOTIFICATION_MARKER = "INSERT INTO notifications";

    @Test
    public void submitLocksCreatorScopeAndUpdatesOnlyCurrentDraftWorkflowFields() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        ExamRepositoryJdbcTestSupport.StatementPlan lock = database.plan(TEACHER_LOCK_MARKER)
                .queryRows(lockedExam(3, ExamStatus.DRAFT));
        ExamRepositoryJdbcTestSupport.StatementPlan submit = database.plan(SUBMIT_MARKER)
                .updateResults(1);

        Exam exam = submittedExam(45, 1002, 3);
        boolean submitted = new ExamRepository(database)
                .persistSubmissionForApproval(1002, exam);

        assertTrue(submitted);
        assertTransactionSucceeded(database);
        assertEquals(Map.of(1, 1002, 2, 45, 3, 1002),
                lock.queryExecutions.get(0));
        assertTeacherLockSql(lock.sql);

        Map<Integer, Object> parameters = submit.updateExecutions.get(0);
        assertEquals("PENDING_APPROVAL", parameters.get(1));
        assertTrue(parameters.get(2) instanceof LocalDateTime);
        assertEquals(45, parameters.get(3));
        assertEquals(3, parameters.get(4));
        String sql = normalized(submit.sql);
        assertTrue(sql.contains("REVIEWED_BY_USER_ID = NULL"));
        assertTrue(sql.contains("REVIEWED_AT = NULL"));
        assertTrue(sql.contains("REJECTION_REASON = NULL"));
        assertTrue(sql.contains("STATUS = 'DRAFT'"));
        assertNoVersionInsert(database);
    }

    @Test
    public void approveAllowsCoordinatorSelfReviewAndPreservesSubmissionTimestamp() {
        int coordinatorId = 1003;
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        ExamRepositoryJdbcTestSupport.StatementPlan lock =
                database.plan(COORDINATOR_LOCK_MARKER)
                        .queryRows(lockedExam(4, ExamStatus.PENDING_APPROVAL));
        ExamRepositoryJdbcTestSupport.StatementPlan approve = database.plan(APPROVE_MARKER)
                .updateResults(1);
        database.plan(NOTIFICATION_MARKER).updateResults(1);

        Exam exam = approvedExam(55, coordinatorId, 4, coordinatorId);
        boolean approved = new ExamRepository(database)
                .persistApproval(coordinatorId, exam);

        assertTrue(approved);
        assertTransactionSucceeded(database);
        assertEquals(Map.of(1, coordinatorId, 2, 55), lock.queryExecutions.get(0));
        String lockSql = normalized(lock.sql);
        assertTrue(lockSql.contains("JOIN SUBJECT_COORDINATORS SC"));
        assertTrue(lockSql.contains("SC.COORDINATOR_USER_ID = ?"));
        assertTrue(lockSql.contains("FOR UPDATE"));
        assertFalse(lockSql.contains("CREATED_BY_USER_ID <>"));
        assertFalse(lockSql.contains("CREATED_BY_USER_ID !="));

        Map<Integer, Object> parameters = approve.updateExecutions.get(0);
        assertEquals("APPROVED", parameters.get(1));
        assertEquals(coordinatorId, parameters.get(2));
        assertTrue(parameters.get(3) instanceof LocalDateTime);
        assertEquals(55, parameters.get(4));
        assertEquals(4, parameters.get(5));
        String updateSql = normalized(approve.sql);
        assertTrue(updateSql.contains("REJECTION_REASON = NULL"));
        assertFalse(updateSql.contains("SUBMITTED_AT ="));
        assertNoVersionInsert(database);
    }

    @Test
    public void rejectPersistsTrimmedReasonAndOnlyUpdatesReviewFields() {
        int coordinatorId = 1003;
        String reason = "  Needs clearer instructions  ";
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan(COORDINATOR_LOCK_MARKER)
                .queryRows(lockedExam(6, ExamStatus.PENDING_APPROVAL));
        ExamRepositoryJdbcTestSupport.StatementPlan reject = database.plan(REJECT_MARKER)
                .updateResults(1);
        database.plan(NOTIFICATION_MARKER).updateResults(1);

        Exam exam = rejectedExam(65, 1002, 6, coordinatorId, reason);
        boolean rejected = new ExamRepository(database)
                .persistRejection(coordinatorId, exam);

        assertTrue(rejected);
        assertTransactionSucceeded(database);
        Map<Integer, Object> parameters = reject.updateExecutions.get(0);
        assertEquals("REJECTED", parameters.get(1));
        assertEquals(coordinatorId, parameters.get(2));
        assertTrue(parameters.get(3) instanceof LocalDateTime);
        assertEquals("Needs clearer instructions", parameters.get(4));
        assertEquals(65, parameters.get(5));
        assertEquals(6, parameters.get(6));
        assertFalse(normalized(reject.sql).contains("SUBMITTED_AT ="));
        assertNoVersionInsert(database);
    }

    @Test
    public void inaccessibleTransitionsReturnFalseWithoutWriting() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController submitDatabase = database();
        submitDatabase.plan(TEACHER_LOCK_MARKER).queryRows();
        assertFalse(new ExamRepository(submitDatabase)
                .persistSubmissionForApproval(1002, submittedExam(45, 1002, 1)));
        assertFalseTransitionCommitted(submitDatabase);

        ExamRepositoryJdbcTestSupport.FakeDatabaseController approveDatabase = database();
        approveDatabase.plan(COORDINATOR_LOCK_MARKER).queryRows();
        assertFalse(new ExamRepository(approveDatabase)
                .persistApproval(1003, approvedExam(45, 1002, 1, 1003)));
        assertFalseTransitionCommitted(approveDatabase);

        ExamRepositoryJdbcTestSupport.FakeDatabaseController rejectDatabase = database();
        rejectDatabase.plan(COORDINATOR_LOCK_MARKER).queryRows();
        assertFalse(new ExamRepository(rejectDatabase)
                .persistRejection(1003, rejectedExam(45, 1002, 1, 1003, "reason")));
        assertFalseTransitionCommitted(rejectDatabase);
    }

    @Test
    public void everyTransitionChecksExpectedVersionBeforeWorkflowStatus() {
        assertTransitionFailure(
                Transition.SUBMIT,
                lockedExam(4, ExamStatus.APPROVED),
                3,
                "Exam version conflict"
        );
        assertTransitionFailure(
                Transition.APPROVE,
                lockedExam(4, ExamStatus.DRAFT),
                3,
                "Exam version conflict"
        );
        assertTransitionFailure(
                Transition.REJECT,
                lockedExam(4, ExamStatus.REJECTED),
                3,
                "Exam version conflict"
        );
    }

    @Test
    public void invalidWorkflowStatusesUseExactMessagesWithoutWriting() {
        for (ExamStatus status : new ExamStatus[]{
                ExamStatus.PENDING_APPROVAL, ExamStatus.APPROVED, ExamStatus.REJECTED
        }) {
            assertTransitionFailure(
                    Transition.SUBMIT,
                    lockedExam(4, status),
                    4,
                    "Exam is not a draft"
            );
        }

        for (ExamStatus status : new ExamStatus[]{
                ExamStatus.DRAFT, ExamStatus.APPROVED, ExamStatus.REJECTED
        }) {
            assertTransitionFailure(
                    Transition.APPROVE,
                    lockedExam(4, status),
                    4,
                    "Exam is not pending approval"
            );
            assertTransitionFailure(
                    Transition.REJECT,
                    lockedExam(4, status),
                    4,
                    "Exam is not pending approval"
            );
        }
    }

    @Test
    public void everyTransitionWrapsJdbcFailureWithExactMessageAndCause() {
        assertJdbcFailure(
                Transition.SUBMIT,
                TEACHER_LOCK_MARKER,
                "Failed to submit exam for approval"
        );
        assertJdbcFailure(
                Transition.APPROVE,
                COORDINATOR_LOCK_MARKER,
                "Failed to approve exam"
        );
        assertJdbcFailure(
                Transition.REJECT,
                COORDINATOR_LOCK_MARKER,
                "Failed to reject exam"
        );
    }

    @Test
    public void transitionCleanupFailuresAreSuppressedOnOriginalDomainFailure() {
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan(TEACHER_LOCK_MARKER)
                .queryRows(lockedExam(4, ExamStatus.APPROVED));
        database.rollbackFailure = rollbackFailure;
        database.restorationFailure = restorationFailure;

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(database).persistSubmissionForApproval(
                        1002,
                        submittedExam(45, 1002, 4)
                )
        );

        assertEquals("Exam is not a draft", failure.getMessage());
        assertEquals(2, failure.getSuppressed().length);
        assertSame(rollbackFailure, failure.getSuppressed()[0]);
        assertSame(restorationFailure, failure.getSuppressed()[1]);
    }

    private static void assertTransitionFailure(Transition transition,
                                                Map<Object, Object> lockedRow,
                                                int expectedVersionNo,
                                                String expectedMessage) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan(transition.lockMarker).queryRows(lockedRow);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> transition.execute(
                        new ExamRepository(database),
                        expectedVersionNo
                )
        );

        assertEquals(expectedMessage, failure.getMessage());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.autoCommit);
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
    }

    private static void assertJdbcFailure(Transition transition, String lockMarker,
                                          String expectedMessage) {
        SQLException original = new SQLException("lock failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database();
        database.plan(lockMarker).queryFailure(original);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> transition.execute(new ExamRepository(database), 4)
        );

        assertEquals(expectedMessage, failure.getMessage());
        assertSame(original, failure.getCause());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.autoCommit);
    }

    private static void assertTeacherLockSql(String sql) {
        String normalized = normalized(sql);
        assertTrue(normalized.contains("JOIN TEACHER_COURSES TC"));
        assertTrue(normalized.contains("TC.TEACHER_USER_ID = ?"));
        assertTrue(normalized.contains("E.CREATED_BY_USER_ID = ?"));
        assertTrue(normalized.contains("FOR UPDATE"));
    }

    private static void assertTransactionSucceeded(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database) {
        assertEquals(1, database.connectionRequests);
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertTrue(database.autoCommit);
        assertEquals(2, database.autoCommitSetCalls);
    }

    private static void assertFalseTransitionCommitted(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database) {
        assertEquals(1, database.connectionRequests);
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertTrue(database.autoCommit);
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
    }

    private static void assertNoVersionInsert(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database) {
        assertTrue(database.plans.stream()
                .filter(plan -> plan.sql != null)
                .noneMatch(plan -> normalized(plan.sql)
                        .contains("INSERT INTO EXAM_VERSIONS")));
        assertTrue(database.plans.stream()
                .filter(plan -> plan.sql != null)
                .noneMatch(plan -> normalized(plan.sql)
                        .contains("INSERT INTO EXAM_VERSION_QUESTIONS")));
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

    private static Exam submittedExam(int examId, int creatorId, int versionNo) {
        Exam exam = draftExam(examId, creatorId, versionNo);
        exam.submitForApproval();
        return exam;
    }

    private static Exam approvedExam(int examId, int creatorId, int versionNo,
                                     int reviewerId) {
        Exam exam = submittedExam(examId, creatorId, versionNo);
        exam.approve(reviewerId);
        return exam;
    }

    private static Exam rejectedExam(int examId, int creatorId, int versionNo,
                                     int reviewerId, String reason) {
        Exam exam = submittedExam(examId, creatorId, versionNo);
        exam.reject(reviewerId, reason);
        return exam;
    }

    private static Exam draftExam(int examId, int creatorId, int versionNo) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 20, 8, 0);
        Question question = new Question(
                17,
                "Question 17",
                "Algebra",
                "MULTIPLE_CHOICE",
                "HARD",
                "ACTIVE",
                "image.png",
                "One",
                "Two",
                "Three",
                "Four",
                3
        );
        return Exam.rehydrate(
                examId,
                "ABC123",
                7,
                creatorId,
                versionNo,
                "Midterm",
                90,
                "Teacher notes",
                "Read carefully",
                ExamStatus.DRAFT,
                createdAt,
                createdAt.plusDays(1),
                null,
                null,
                null,
                null,
                List.of(new ExamQuestion(
                        17,
                        4,
                        1,
                        new BigDecimal("100.00"),
                        question
                ))
        );
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }

    private enum Transition {
        SUBMIT(TEACHER_LOCK_MARKER) {
            @Override
            boolean execute(ExamRepository repository, int expectedVersionNo) {
                return repository.persistSubmissionForApproval(
                        1002,
                        submittedExam(45, 1002, expectedVersionNo)
                );
            }
        },
        APPROVE(COORDINATOR_LOCK_MARKER) {
            @Override
            boolean execute(ExamRepository repository, int expectedVersionNo) {
                return repository.persistApproval(
                        1003,
                        approvedExam(45, 1002, expectedVersionNo, 1003)
                );
            }
        },
        REJECT(COORDINATOR_LOCK_MARKER) {
            @Override
            boolean execute(ExamRepository repository, int expectedVersionNo) {
                return repository.persistRejection(
                        1003,
                        rejectedExam(45, 1002, expectedVersionNo, 1003, "reason")
                );
            }
        };

        private final String lockMarker;

        Transition(String lockMarker) {
            this.lockMarker = lockMarker;
        }

        abstract boolean execute(ExamRepository repository, int expectedVersionNo);
    }
}
