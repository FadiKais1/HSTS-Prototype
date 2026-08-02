package hsts.server.repository;

import hsts.common.type.DifficultyLevel;
import hsts.common.type.ExamStatus;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import hsts.server.entity.AnswerOption;
import hsts.server.entity.Exam;
import hsts.server.entity.ExamQuestion;
import hsts.server.entity.Question;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamRepositoryEntityWriteTest {
    private static final String COURSE = "FROM courses c";
    private static final String QUESTION = "FROM questions q";
    private static final String EXAM_INSERT = "INSERT INTO exams (";
    private static final String VERSION_INSERT = "INSERT INTO exam_versions (";
    private static final String SELECTION_INSERT = "INSERT INTO exam_version_questions (";
    private static final String INITIAL_POINTER = "SET current_version_no = 1";
    private static final String TEACHER_LOCK = "AND e.created_by_user_id = ?";
    private static final String COORDINATOR_LOCK = "JOIN subject_coordinators sc";
    private static final String VERSION_POINTER = "SET current_version_no = ?";
    private static final String NOTIFICATION_INSERT = "INSERT INTO notifications";

    @Test
    public void invalidEntityStateFailsBeforeOpeningAConnection() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database(true);
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(database).create(1002, (Exam) null)
        );
        assertEquals("Exam creation data is missing", missing.getMessage());

        Exam wrongCreator = Exam.createDraft(
                7,
                1003,
                "Midterm",
                90,
                "Teacher notes",
                "Read carefully",
                selections()
        );
        IllegalArgumentException creator = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(database).create(1002, wrongCreator)
        );
        assertEquals(
                "Exam creator does not match authenticated user",
                creator.getMessage()
        );
        assertEquals(0, database.connectionRequests);
    }

    @Test
    public void entityCreateSharesAtomicWritePathAndKeepsAggregateUnsaved() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database(false);
        ExamRepositoryJdbcTestSupport.StatementPlan course = database.plan(COURSE)
                .queryRows(row("course_id", 7));
        ExamRepositoryJdbcTestSupport.StatementPlan question = database.plan(QUESTION)
                .queryRows(row("question_id", 17))
                .queryRows(row("question_id", 18));
        ExamRepositoryJdbcTestSupport.StatementPlan stable = database.plan(EXAM_INSERT)
                .updateResults(1).generatedKey(45);
        ExamRepositoryJdbcTestSupport.StatementPlan version = database.plan(VERSION_INSERT)
                .updateResults(1);
        ExamRepositoryJdbcTestSupport.StatementPlan selections =
                database.plan(SELECTION_INSERT).updateResults(1, 1);
        ExamRepositoryJdbcTestSupport.StatementPlan pointer = database.plan(INITIAL_POINTER)
                .updateResults(1);
        Exam exam = unsavedDraft();

        int examId = new ExamRepository(database, () -> "ABC123")
                .create(1002, exam);

        assertEquals(45, examId);
        assertEquals(0, exam.getExamId());
        assertEquals(null, exam.getExamCode());
        assertEquals(Map.of(1, 1002, 2, 7), course.queryExecutions.get(0));
        assertEquals(Map.of(1, 1002, 2, 4, 3, 17, 4, 7, 5, 4),
                question.queryExecutions.get(0));
        assertEquals("ABC123", stable.updateExecutions.get(0).get(1));
        assertEquals(7, stable.updateExecutions.get(0).get(2));
        assertEquals(1002, stable.updateExecutions.get(0).get(3));
        assertEquals(1, version.updateExecutions.get(0).get(2));
        assertEquals("DRAFT", version.updateExecutions.get(0).get(8));
        assertEquals(0, ((BigDecimal) version.updateExecutions.get(0).get(7))
                .compareTo(new BigDecimal("100.00")));
        assertSelection(selections.updateExecutions.get(0), 45, 1, 1, 17, 4,
                new BigDecimal("40.00"));
        assertSelection(selections.updateExecutions.get(1), 45, 1, 2, 18, 2,
                new BigDecimal("60.00"));
        assertEquals(Map.of(1, 45), pointer.updateExecutions.get(0));
        assertTransaction(database, false);
        assertInOrder(database.events,
                "query:" + COURSE,
                "query:" + QUESTION,
                "query:" + QUESTION,
                "update:" + EXAM_INSERT,
                "update:" + VERSION_INSERT,
                "update:" + SELECTION_INSERT,
                "update:" + SELECTION_INSERT,
                "update:" + INITIAL_POINTER,
                "commit"
        );
    }

    @Test
    public void entityCreatePreservesCollisionRetryAndCleanupFailures() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController collisionDatabase = database(true);
        collisionDatabase.plan(COURSE).queryRows(row("course_id", 7));
        collisionDatabase.plan(QUESTION)
                .queryRows(row("question_id", 17))
                .queryRows(row("question_id", 18));
        SQLException collision = new SQLException(
                "Duplicate entry for key 'uq_exams_exam_code'",
                "23000",
                1062
        );
        ExamRepositoryJdbcTestSupport.StatementPlan stable =
                collisionDatabase.plan(EXAM_INSERT)
                        .updateFailure(collision)
                        .updateResults(1)
                        .generatedKey(46);
        collisionDatabase.plan(VERSION_INSERT).updateResults(1);
        collisionDatabase.plan(SELECTION_INSERT).updateResults(1, 1);
        collisionDatabase.plan(INITIAL_POINTER).updateResults(1);
        ArrayDeque<String> codes = new ArrayDeque<>(List.of("AAAAAA", "BBBBBB"));

        assertEquals(46, new ExamRepository(collisionDatabase, codes::removeFirst)
                .create(1002, unsavedDraft()));
        assertEquals("AAAAAA", stable.updateExecutions.get(0).get(1));
        assertEquals("BBBBBB", stable.updateExecutions.get(1).get(1));

        SQLException insertFailure = new SQLException("insert failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController failed = database(true);
        failed.plan(COURSE).queryRows(row("course_id", 7));
        failed.plan(QUESTION)
                .queryRows(row("question_id", 17))
                .queryRows(row("question_id", 18));
        failed.plan(EXAM_INSERT).updateFailure(insertFailure);
        failed.rollbackFailure = rollbackFailure;
        failed.restorationFailure = restorationFailure;

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(failed, () -> "ABC123")
                        .create(1002, unsavedDraft())
        );
        assertEquals("Failed to create exam", thrown.getMessage());
        assertSame(insertFailure, thrown.getCause());
        assertEquals(List.of(rollbackFailure, restorationFailure),
                List.of(insertFailure.getSuppressed()));
    }

    @Test
    public void entityUpdateLocksAndPersistsOneExactNewDraftVersion() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database = database(true);
        ExamRepositoryJdbcTestSupport.StatementPlan lock = database.plan(TEACHER_LOCK)
                .queryRows(lockedExam(4, ExamStatus.APPROVED));
        database.plan(QUESTION)
                .queryRows(row("question_id", 17))
                .queryRows(row("question_id", 18));
        ExamRepositoryJdbcTestSupport.StatementPlan version = database.plan(VERSION_INSERT)
                .updateResults(1);
        ExamRepositoryJdbcTestSupport.StatementPlan selections =
                database.plan(SELECTION_INSERT).updateResults(1, 1);
        ExamRepositoryJdbcTestSupport.StatementPlan pointer = database.plan(VERSION_POINTER)
                .updateResults(1);
        Exam proposed = persistedDraft(5);

        int newVersionNo = new ExamRepository(database)
                .updateWithNewVersion(1002, 45, 4, proposed);

        assertEquals(5, newVersionNo);
        assertEquals(Map.of(1, 1002, 2, 45, 3, 1002), lock.queryExecutions.get(0));
        assertEquals(45, version.updateExecutions.get(0).get(1));
        assertEquals(5, version.updateExecutions.get(0).get(2));
        assertEquals("DRAFT", version.updateExecutions.get(0).get(8));
        assertSelection(selections.updateExecutions.get(0), 45, 5, 1, 17, 4,
                new BigDecimal("40.00"));
        assertSelection(selections.updateExecutions.get(1), 45, 5, 2, 18, 2,
                new BigDecimal("60.00"));
        assertEquals(Map.of(1, 5, 2, 45, 3, 4), pointer.updateExecutions.get(0));
        assertInOrder(database.events,
                "query:" + TEACHER_LOCK,
                "query:" + QUESTION,
                "query:" + QUESTION,
                "update:" + VERSION_INSERT,
                "update:" + SELECTION_INSERT,
                "update:" + SELECTION_INSERT,
                "update:" + VERSION_POINTER,
                "commit"
        );
        assertTransaction(database, true);
    }

    @Test
    public void entityUpdateRejectsIdentityAndVersionConflictsBeforeWrites() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController noConnection = database(true);
        IllegalStateException versionFailure = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(noConnection)
                        .updateWithNewVersion(1002, 45, 3, persistedDraft(5))
        );
        assertEquals("Exam version conflict", versionFailure.getMessage());
        assertEquals(0, noConnection.connectionRequests);

        ExamRepositoryJdbcTestSupport.FakeDatabaseController stale = database(true);
        stale.plan(TEACHER_LOCK).queryRows(lockedExam(5, ExamStatus.DRAFT));
        IllegalStateException staleFailure = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(stale)
                        .updateWithNewVersion(1002, 45, 4, persistedDraft(5))
        );
        assertEquals("Exam version conflict", staleFailure.getMessage());
        assertEquals(1, stale.rollbackCount);
        assertEquals(0, stale.commitCount);

        ExamRepositoryJdbcTestSupport.FakeDatabaseController wrongCourse = database(true);
        wrongCourse.plan(TEACHER_LOCK).queryRows(lockedExam(4, ExamStatus.APPROVED));
        IllegalArgumentException courseFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(wrongCourse)
                        .updateWithNewVersion(1002, 45, 4, persistedDraft(5, 8))
        );
        assertEquals("Exam course cannot be changed", courseFailure.getMessage());
        assertEquals(1, wrongCourse.rollbackCount);
        assertEquals(0, wrongCourse.commitCount);
    }

    @Test
    public void entityWorkflowPersistenceUsesTransitionedStateWithoutNewVersions() {
        Exam submitted = persistedDraft(3);
        submitted.submitForApproval();
        ExamRepositoryJdbcTestSupport.FakeDatabaseController submitDatabase = database(true);
        submitDatabase.plan(TEACHER_LOCK).queryRows(lockedExam(3, ExamStatus.DRAFT));
        ExamRepositoryJdbcTestSupport.StatementPlan submit = submitDatabase
                .plan("submitted_at = ?").updateResults(1);
        // The submit transaction also asks which coordinators of the subject
        // should be notified. No coordinator rows means nobody is notified,
        // which leaves this test focused on the workflow transition itself.
        submitDatabase.plan("coordinator.coordinator_user_id").queryRows();
        assertTrue(new ExamRepository(submitDatabase)
                .persistSubmissionForApproval(1002, submitted));
        assertEquals(submitted.getSubmittedAt(), submit.updateExecutions.get(0).get(2));
        assertEquals(ExamStatus.PENDING_APPROVAL, submitted.getStatus());
        assertNoVersionWrites(submitDatabase);

        Exam approved = pendingExam();
        approved.approve(1003);
        ExamRepositoryJdbcTestSupport.FakeDatabaseController approveDatabase = database(true);
        approveDatabase.plan(COORDINATOR_LOCK)
                .queryRows(lockedExam(3, ExamStatus.PENDING_APPROVAL));
        ExamRepositoryJdbcTestSupport.StatementPlan approve = approveDatabase
                .plan("rejection_reason = NULL").updateResults(1);
        approveDatabase.plan(NOTIFICATION_INSERT).updateResults(1);
        assertTrue(new ExamRepository(approveDatabase).persistApproval(1003, approved));
        assertEquals(1003, approve.updateExecutions.get(0).get(2));
        assertEquals(approved.getReviewedAt(), approve.updateExecutions.get(0).get(3));
        assertEquals(ExamStatus.APPROVED, approved.getStatus());
        assertNoVersionWrites(approveDatabase);

        Exam rejected = pendingExam();
        rejected.reject(1003, "  Needs revision  ");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController rejectDatabase = database(true);
        rejectDatabase.plan(COORDINATOR_LOCK)
                .queryRows(lockedExam(3, ExamStatus.PENDING_APPROVAL));
        ExamRepositoryJdbcTestSupport.StatementPlan reject = rejectDatabase
                .plan("rejection_reason = ?").updateResults(1);
        rejectDatabase.plan(NOTIFICATION_INSERT).updateResults(1);
        assertTrue(new ExamRepository(rejectDatabase).persistRejection(1003, rejected));
        assertEquals("Needs revision", reject.updateExecutions.get(0).get(4));
        assertEquals(rejected.getReviewedAt(), reject.updateExecutions.get(0).get(3));
        assertEquals(ExamStatus.REJECTED, rejected.getStatus());
        assertNoVersionWrites(rejectDatabase);
    }

    private static Exam unsavedDraft() {
        return Exam.createDraft(
                7,
                1002,
                "Midterm",
                90,
                "Teacher notes",
                "Read carefully",
                selections()
        );
    }

    private static Exam persistedDraft(int versionNo) {
        return persistedDraft(versionNo, 7);
    }

    private static Exam persistedDraft(int versionNo, int courseId) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 20, 8, 0);
        return Exam.rehydrate(
                45,
                "ABC123",
                courseId,
                1002,
                versionNo,
                "Midterm",
                90,
                "Teacher notes",
                "Read carefully",
                ExamStatus.DRAFT,
                createdAt,
                createdAt.plusDays(versionNo),
                null,
                null,
                null,
                null,
                selections()
        );
    }

    private static Exam pendingExam() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 20, 8, 0);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 7, 25, 8, 0);
        return Exam.rehydrate(
                45,
                "ABC123",
                7,
                1002,
                3,
                "Midterm",
                90,
                "Teacher notes",
                "Read carefully",
                ExamStatus.PENDING_APPROVAL,
                createdAt,
                submittedAt,
                submittedAt,
                null,
                null,
                null,
                selections()
        );
    }

    private static List<ExamQuestion> selections() {
        return List.of(
                selection(17, 4, 1, "40.00"),
                selection(18, 2, 2, "60.00")
        );
    }

    private static ExamQuestion selection(int questionId, int versionNo,
                                          int orderNumber, String score) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 7, 10, 8, 0);
        Question question = Question.rehydrate(
                questionId,
                "Question " + questionId,
                QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.HARD,
                QuestionStatus.ACTIVE,
                timestamp,
                timestamp,
                "Algebra",
                "image.png",
                List.of(
                        new AnswerOption(1, "One", false),
                        new AnswerOption(2, "Two", false),
                        new AnswerOption(3, "Three", true),
                        new AnswerOption(4, "Four", false)
                )
        );
        return new ExamQuestion(
                questionId,
                versionNo,
                orderNumber,
                new BigDecimal(score),
                question
        );
    }

    private static Map<Object, Object> lockedExam(int versionNo, ExamStatus status) {
        return row(
                "course_id", 7,
                "current_version_no", versionNo,
                "status", status.name()
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

    private static void assertTransaction(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database,
            boolean originalAutoCommit) {
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertEquals(originalAutoCommit, database.autoCommit);
        assertEquals(2, database.autoCommitSetCalls);
    }

    private static void assertInOrder(List<String> events, String... expected) {
        int previous = -1;
        for (String event : expected) {
            int relative = events.subList(previous + 1, events.size()).indexOf(event);
            assertTrue("Missing or out-of-order event: " + event, relative >= 0);
            previous += relative + 1;
        }
    }

    private static void assertNoVersionWrites(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database) {
        assertFalse(database.events.contains("update:" + VERSION_INSERT));
        assertFalse(database.events.contains("update:" + SELECTION_INSERT));
        assertEquals(1, database.commitCount);
    }

    private static ExamRepositoryJdbcTestSupport.FakeDatabaseController database(
            boolean originalAutoCommit) {
        return new ExamRepositoryJdbcTestSupport.FakeDatabaseController(originalAutoCommit);
    }
}
