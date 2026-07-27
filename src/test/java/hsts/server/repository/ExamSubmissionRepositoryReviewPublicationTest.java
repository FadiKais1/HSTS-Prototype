package hsts.server.repository;

import hsts.common.type.SubmissionStatus;
import hsts.server.entity.ExamSubmission;
import hsts.server.entity.StudentAnswer;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static hsts.server.repository.ExamSubmissionRepositoryTestSupport.normalized;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionRepositoryReviewPublicationTest {
    private static final String SCOPE_MARKER =
            "subject_coordinators coordinator_assignment";
    private static final String REVIEW_MARKER = "SET status = ?, final_score = ?";
    private static final String PUBLICATION_MARKER = "SET status = ?";
    private static final String ANSWER_MARKER =
            "ORDER BY selection.order_number ASC,";
    private static final LocalDateTime STARTED =
            LocalDateTime.of(2026, 8, 1, 9, 15);
    private static final LocalDateTime SUBMITTED = STARTED.plusMinutes(30);
    private static final LocalDateTime GRADED = SUBMITTED.plusMinutes(1);
    private static final LocalDateTime REVIEWED = GRADED.plusMinutes(1);
    private static final LocalDateTime PUBLISHED = REVIEWED.plusMinutes(1);
    private static final BigDecimal AUTOMATIC = new BigDecimal("60.00");

    @Test
    public void equalScoreReviewPersistsEveryReviewFieldAndRereadsAfterCommit() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamSubmission supplied = reviewedSubmission(
                AUTOMATIC, "Good work", null, 1002
        );
        ExamRepositoryJdbcTestSupport.StatementPlan scope = database.plan(SCOPE_MARKER)
                .queryRows(sourceRow())
                .queryRows(reviewedRow(AUTOMATIC, "Stored feedback", null, 1002));
        ExamRepositoryJdbcTestSupport.StatementPlan update = database.plan(REVIEW_MARKER)
                .updateResults(1);
        database.plan(ANSWER_MARKER).queryRows(gradedAnswerRow());

        ExamSubmission result = new ExamSubmissionRepository(database)
                .persistReview(1002, supplied);

        assertNotSame(supplied, result);
        assertEquals("Stored feedback", result.getTeacherFeedback());
        assertEquals(AUTOMATIC, result.getAutomaticScoreValue().orElseThrow());
        assertEquals(AUTOMATIC, result.getServerFinalScore().orElseThrow());
        assertEquals("Good work", supplied.getTeacherFeedback());
        assertEquals(1002, result.getReviewedByUserId().intValue());
        assertEquals(REVIEWED, result.getReviewedAt());
        assertNull(result.getManualChangeReason());
        StudentAnswer persistedAnswer = result.getStudentAnswers().get(0);
        assertTrue(persistedAnswer.getCorrectness().orElseThrow());
        assertDecimal(
                "60.00",
                persistedAnswer.getScoreReceivedValue().orElseThrow()
        );
        assertEquals(Map.of(1, 1002, 2, 501), scope.queryExecutions.get(0));

        Map<Integer, Object> values = update.updateExecutions.get(0);
        assertEquals("SUBMITTED", values.get(1));
        assertDecimal("60.00", values.get(2));
        assertEquals("Good work", values.get(3));
        assertNull(values.get(4));
        assertEquals(1002, values.get(5));
        assertEquals(REVIEWED, values.get(6));
        assertEquals(REVIEWED, values.get(7));
        assertEquals(501, values.get(8));
        assertEquals("SUBMITTED", values.get(9));
        assertDecimal("60.00", values.get(10));
        assertDecimal("60.00", values.get(11));
        assertEquals(GRADED, values.get(12));
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertTrue(database.autoCommit);
        assertEquals(1, database.connectionRequests);
        assertTrue(database.events.indexOf("commit")
                < database.events.lastIndexOf("query:" + SCOPE_MARKER));
        assertTrue(database.events.indexOf("autoCommit:true")
                < database.events.lastIndexOf("query:" + SCOPE_MARKER));
        assertNoGradingOrCounterSql(database);
    }

    @Test
    public void adjustedReviewPersistsSeparateReasonWithoutTouchingAnswers() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        BigDecimal adjusted = new BigDecimal("65.00");
        ExamSubmission supplied = reviewedSubmission(
                adjusted, "Reviewed response", "Accepted ambiguity", 1002
        );
        database.plan(SCOPE_MARKER)
                .queryRows(sourceRow())
                .queryRows(reviewedRow(
                        adjusted, "Reviewed response", "Accepted ambiguity", 1002
                ));
        ExamRepositoryJdbcTestSupport.StatementPlan update = database.plan(REVIEW_MARKER)
                .updateResults(1);
        ExamRepositoryJdbcTestSupport.StatementPlan answers = database.plan(ANSWER_MARKER)
                .queryRows(gradedAnswerRow());

        ExamSubmission result = new ExamSubmissionRepository(database)
                .persistReview(1002, supplied);

        assertEquals("Accepted ambiguity", result.getManualChangeReason());
        assertDecimal("65.00", update.updateExecutions.get(0).get(2));
        assertEquals("Accepted ambiguity", update.updateExecutions.get(0).get(4));
        assertTrue(answers.sql.contains("student_answers"));
        assertTrue(database.plans.stream().noneMatch(plan ->
                plan.sql != null && normalized(plan.sql).contains("UPDATE STUDENT_ANSWERS")));
        assertNoGradingOrCounterSql(database);
    }

    @Test
    public void publicationPersistsOnlyPublicationStateAndPreservesReviewData() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamSubmission supplied = publishedSubmission(1003);
        Map<Object, Object> reviewed = reviewedRow(
                new BigDecimal("65.00"), "Reviewed response",
                "Accepted ambiguity", 1002
        );
        database.plan(SCOPE_MARKER)
                .queryRows(reviewed)
                .queryRows(publishedRow(1003));
        ExamRepositoryJdbcTestSupport.StatementPlan update = database.plan(
                PUBLICATION_MARKER
        ).updateResults(1);
        database.plan(ANSWER_MARKER).queryRows(gradedAnswerRow());

        ExamSubmission result = new ExamSubmissionRepository(database)
                .persistPublication(1003, supplied);

        assertEquals(SubmissionStatus.PUBLISHED, result.getStatus());
        assertEquals(1003, result.getPublishedByUserId().intValue());
        assertEquals(PUBLISHED, result.getPublishedAt());
        assertEquals("Reviewed response", result.getTeacherFeedback());
        assertEquals("Accepted ambiguity", result.getManualChangeReason());
        assertDecimal("60.00", result.getAutomaticScoreValue().orElseThrow());
        assertDecimal("65.00", result.getServerFinalScore().orElseThrow());
        Map<Integer, Object> values = update.updateExecutions.get(0);
        assertEquals("PUBLISHED", values.get(1));
        assertEquals(1003, values.get(2));
        assertEquals(PUBLISHED, values.get(3));
        assertEquals(PUBLISHED, values.get(4));
        assertEquals(501, values.get(5));
        assertEquals("SUBMITTED", values.get(6));
        assertDecimal("60.00", values.get(7));
        assertDecimal("65.00", values.get(8));
        assertEquals(1002, values.get(9));
        assertEquals(REVIEWED, values.get(10));
        assertEquals("Reviewed response", values.get(11));
        assertEquals("Accepted ambiguity", values.get(12));
        assertEquals(REVIEWED, values.get(13));
        String sql = normalized(update.sql);
        String setClause = sql.substring(0, sql.indexOf(" WHERE "));
        assertFalse(setClause.contains("AUTOMATIC_SCORE ="));
        assertFalse(setClause.contains("FINAL_SCORE ="));
        assertFalse(setClause.contains("TEACHER_FEEDBACK ="));
        assertFalse(setClause.contains("REVIEWED_BY_USER_ID ="));
        assertEquals(1, database.commitCount);
        assertTrue(database.autoCommit);
        assertNoGradingOrCounterSql(database);
    }

    @Test
    public void managerScopeSupportsTeacherCourseAndCoordinatorSubjectOnly() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController teacherDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan lock = teacherDatabase
                .plan("FOR UPDATE")
                .queryRows(sourceRow());
        teacherDatabase.plan(SCOPE_MARKER)
                .queryRows(reviewedRow(AUTOMATIC, null, null, 1002));
        teacherDatabase.plan(REVIEW_MARKER).updateResults(1);
        teacherDatabase.plan(ANSWER_MARKER).queryRows(gradedAnswerRow());
        new ExamSubmissionRepository(teacherDatabase).persistReview(
                1002, reviewedSubmission(AUTOMATIC, null, null, 1002)
        );
        String teacherSql = normalized(lock.sql);
        assertTrue(teacherSql.contains("MANAGER.STATUS = 'ACTIVE'"));
        assertTrue(teacherSql.contains("MANAGER.ROLE = 'TEACHER'"));
        assertTrue(teacherSql.contains("FROM TEACHER_COURSES"));
        assertTrue(teacherSql.contains("TEACHER_ASSIGNMENT.COURSE_ID = EXAM.COURSE_ID"));
        assertTrue(teacherSql.contains("MANAGER.ROLE = 'COORDINATOR'"));
        assertTrue(teacherSql.contains("FROM SUBJECT_COORDINATORS"));
        assertTrue(teacherSql.contains(
                "COORDINATOR_ASSIGNMENT.SUBJECT_ID = COURSE.SUBJECT_ID"
        ));
        assertTrue(teacherSql.contains("FOR UPDATE"));

        ExamRepositoryJdbcTestSupport.FakeDatabaseController coordinatorDatabase =
                successfulReviewDatabase(1003);
        ExamSubmission coordinatorReview = reviewedSubmission(
                AUTOMATIC, null, null, 1003
        );
        ExamSubmission result = new ExamSubmissionRepository(coordinatorDatabase)
                .persistReview(1003, coordinatorReview);
        assertEquals(1003, result.getReviewedByUserId().intValue());
        assertEquals(Map.of(1, 1003, 2, 501),
                coordinatorDatabase.plans.get(0).queryExecutions.get(0));
    }

    @Test
    public void inactiveWrongRoleAndUnauthorizedManagersShareSafeMissingError() {
        for (int managerId : List.of(1002, 1003, 1004)) {
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                    new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
            database.plan(SCOPE_MARKER).queryRows();
            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> new ExamSubmissionRepository(database).persistReview(
                            managerId,
                            reviewedSubmission(AUTOMATIC, null, null, managerId)
                    )
            );
            assertEquals("Exam attempt not found: 501", thrown.getMessage());
            assertEquals(1, database.rollbackCount);
            assertEquals(0, database.commitCount);
        }
    }

    @Test
    public void actorIdentityMismatchFailsBeforeConnectionForBothOperations() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        IllegalArgumentException review = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamSubmissionRepository(database).persistReview(
                        1003, reviewedSubmission(AUTOMATIC, null, null, 1002)
                )
        );
        IllegalArgumentException publication = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamSubmissionRepository(database).persistPublication(
                        1002, publishedSubmission(1003)
                )
        );
        assertEquals("Review actor does not match authenticated manager",
                review.getMessage());
        assertEquals("Publication actor does not match authenticated manager",
                publication.getMessage());
        assertEquals(0, database.connectionRequests);
    }

    @Test
    public void immutableIdentityMismatchAndInvalidSourceStateRollback() {
        ExamSubmission supplied = reviewedSubmission(AUTOMATIC, null, null, 1002);
        ExamRepositoryJdbcTestSupport.FakeDatabaseController identityDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        Map<Object, Object> wrongIdentity = sourceRow();
        wrongIdentity.put("exam_version_no", 4);
        identityDatabase.plan(SCOPE_MARKER).queryRows(wrongIdentity);
        IllegalStateException identity = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(identityDatabase)
                        .persistReview(1002, supplied)
        );
        assertEquals("Submission state does not match persisted attempt",
                identity.getMessage());

        ExamRepositoryJdbcTestSupport.FakeDatabaseController stateDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        Map<Object, Object> invalidState = sourceRow();
        invalidState.put("status", "IN_PROGRESS");
        stateDatabase.plan(SCOPE_MARKER).queryRows(invalidState);
        IllegalStateException state = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(stateDatabase)
                        .persistReview(1002, supplied)
        );
        assertEquals("Submission is not awaiting review", state.getMessage());
        assertEquals(1, identityDatabase.rollbackCount);
        assertEquals(1, stateDatabase.rollbackCount);
    }

    @Test
    public void staleReviewAndPublicationUpdatesDoNotCommit() {
        assertStaleWrite(false);
        assertStaleWrite(true);
    }

    @Test
    public void idempotentPublicationRereadsWithoutOverwritingMetadata() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamSubmission supplied = publishedSubmission(1003);
        database.plan(SCOPE_MARKER)
                .queryRows(publishedRow(1003))
                .queryRows(publishedRow(1003));
        database.plan(ANSWER_MARKER).queryRows(gradedAnswerRow());

        ExamSubmission result = new ExamSubmissionRepository(database)
                .persistPublication(1003, supplied);

        assertEquals(PUBLISHED, result.getPublishedAt());
        assertEquals(1, database.commitCount);
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
    }

    @Test
    public void jdbcFailureRollsBackRestoresAndSuppressesCleanupFailures() {
        SQLException original = new SQLException("review update failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController ordinaryDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ordinaryDatabase.plan(SCOPE_MARKER).queryRows(sourceRow());
        ordinaryDatabase.plan(REVIEW_MARKER).updateFailure(original);

        IllegalStateException ordinary = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(ordinaryDatabase).persistReview(
                        1002, reviewedSubmission(AUTOMATIC, null, null, 1002)
                )
        );
        assertSame(original, ordinary.getCause());
        assertEquals(1, ordinaryDatabase.rollbackCount);
        assertTrue(ordinaryDatabase.autoCommit);

        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(SCOPE_MARKER).queryRows(sourceRow());
        SQLException cleanupOriginal = new SQLException("review update failed again");
        database.plan(REVIEW_MARKER).updateFailure(cleanupOriginal);
        database.rollbackFailure = rollbackFailure;
        database.restorationFailure = restorationFailure;

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamSubmissionRepository(database).persistReview(
                        1002, reviewedSubmission(AUTOMATIC, null, null, 1002)
                )
        );

        assertEquals("Failed to persist submission review", thrown.getMessage());
        assertSame(cleanupOriginal, thrown.getCause());
        assertEquals(2, cleanupOriginal.getSuppressed().length);
        assertSame(rollbackFailure, cleanupOriginal.getSuppressed()[0]);
        assertSame(restorationFailure, cleanupOriginal.getSuppressed()[1]);
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
    }

    private static void assertStaleWrite(boolean publication) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        if (publication) {
            database.plan(SCOPE_MARKER).queryRows(reviewedRow(
                    new BigDecimal("65.00"), "Reviewed response",
                    "Accepted ambiguity", 1002
            ));
            database.plan(PUBLICATION_MARKER).updateResults(0);
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> new ExamSubmissionRepository(database)
                            .persistPublication(1003, publishedSubmission(1003))
            );
            assertEquals("Submission publication state changed", thrown.getMessage());
        } else {
            database.plan(SCOPE_MARKER).queryRows(sourceRow());
            database.plan(REVIEW_MARKER).updateResults(0);
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> new ExamSubmissionRepository(database).persistReview(
                            1002, reviewedSubmission(AUTOMATIC, null, null, 1002)
                    )
            );
            assertEquals("Submission review state changed", thrown.getMessage());
        }
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.autoCommit);
    }

    private static ExamRepositoryJdbcTestSupport.FakeDatabaseController
    successfulReviewDatabase(int reviewerId) {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(SCOPE_MARKER)
                .queryRows(sourceRow())
                .queryRows(reviewedRow(AUTOMATIC, null, null, reviewerId));
        database.plan(REVIEW_MARKER).updateResults(1);
        database.plan(ANSWER_MARKER).queryRows(gradedAnswerRow());
        return database;
    }

    private static ExamSubmission reviewedSubmission(BigDecimal finalScore,
                                                      String feedback,
                                                      String reason,
                                                      int reviewerId) {
        ExamSubmission submission = finalizedSubmission();
        submission.recordTeacherReview(
                reviewerId, finalScore, feedback, reason, REVIEWED
        );
        return submission;
    }

    private static ExamSubmission publishedSubmission(int publisherId) {
        ExamSubmission submission = reviewedSubmission(
                new BigDecimal("65.00"), "Reviewed response",
                "Accepted ambiguity", 1002
        );
        submission.publish(publisherId, PUBLISHED);
        return submission;
    }

    private static ExamSubmission finalizedSubmission() {
        return ExamSubmission.rehydrate(
                501, 81, 40, 3, 1001, STARTED, SUBMITTED,
                SubmissionStatus.SUBMITTED, 75, 0, null, 30,
                AUTOMATIC, AUTOMATIC, null, null, null, null,
                null, null, STARTED, GRADED,
                List.of(StudentAnswer.rehydrate(
                        901, 501, 17, 4, 2, null, true,
                        new BigDecimal("60.00"), STARTED.plusMinutes(5), GRADED
                ))
        );
    }

    private static Map<Object, Object> sourceRow() {
        return resultRow(
                "SUBMITTED", AUTOMATIC, null, null,
                null, null, null, GRADED
        );
    }

    private static Map<Object, Object> reviewedRow(BigDecimal finalScore,
                                                   String feedback,
                                                   String reason,
                                                   int reviewerId) {
        return resultRow(
                "SUBMITTED", finalScore, feedback, reason,
                reviewerId, REVIEWED, null, REVIEWED
        );
    }

    private static Map<Object, Object> publishedRow(int publisherId) {
        Map<Object, Object> row = reviewedRow(
                new BigDecimal("65.00"), "Reviewed response",
                "Accepted ambiguity", 1002
        );
        row.put("status", "PUBLISHED");
        row.put("published_by_user_id", publisherId);
        row.put("published_at", PUBLISHED);
        row.put("updated_at", PUBLISHED);
        return row;
    }

    private static Map<Object, Object> resultRow(
            String status,
            BigDecimal finalScore,
            String feedback,
            String reason,
            Integer reviewerId,
            LocalDateTime reviewedAt,
            Integer publisherId,
            LocalDateTime updatedAt
    ) {
        return row(
                "submission_id", 501,
                "execution_id", 81,
                "exam_id", 40,
                "exam_version_no", 3,
                "student_user_id", 1001,
                "started_at", STARTED,
                "submitted_at", SUBMITTED,
                "status", status,
                "allocated_duration_minutes", 75,
                "extra_minutes", 0,
                "extension_reason", null,
                "actual_duration_minutes", 30,
                "automatic_score", AUTOMATIC,
                "final_score", finalScore,
                "teacher_feedback", feedback,
                "manual_change_reason", reason,
                "reviewed_by_user_id", reviewerId,
                "reviewed_at", reviewedAt,
                "published_by_user_id", publisherId,
                "published_at", publisherId == null ? null : PUBLISHED,
                "created_at", STARTED,
                "updated_at", updatedAt
        );
    }

    private static Map<Object, Object> gradedAnswerRow() {
        return row(
                "answer_id", 901,
                "submission_id", 501,
                "question_id", 17,
                "question_version_no", 4,
                "selected_option_number", 2,
                "answer_content", null,
                "is_correct", true,
                "score_received", new BigDecimal("60.00"),
                "created_at", STARTED.plusMinutes(5),
                "updated_at", GRADED,
                "order_number", 1
        );
    }

    private static void assertNoGradingOrCounterSql(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database
    ) {
        assertTrue(database.plans.stream().noneMatch(plan -> {
            if (plan.sql == null) {
                return false;
            }
            String sql = normalized(plan.sql);
            return sql.contains("CORRECT_OPTION_NUMBER")
                    || sql.contains("STARTED_COUNT =")
                    || sql.contains("SUBMITTED_COUNT =")
                    || sql.contains("AUTO_SUBMITTED_COUNT =")
                    || sql.contains("UPDATE STUDENT_ANSWERS");
        }));
    }

    private static void assertDecimal(String expected, Object actual) {
        assertEquals(0, ((BigDecimal) actual).compareTo(new BigDecimal(expected)));
    }
}
