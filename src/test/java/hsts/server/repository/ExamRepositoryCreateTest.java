package hsts.server.repository;

import hsts.server.entity.Exam;
import hsts.server.entity.ExamQuestion;
import hsts.server.entity.Question;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamRepositoryCreateTest {
    private static final String COURSE_MARKER = "FROM courses c";
    private static final String QUESTION_MARKER = "FROM questions q";
    private static final String EXAM_MARKER = "INSERT INTO exams (";
    private static final String VERSION_MARKER = "INSERT INTO exam_versions (";
    private static final String SELECTION_MARKER = "INSERT INTO exam_version_questions (";
    private static final String POINTER_MARKER = "UPDATE exams SET current_version_no = 1";

    @Test
    public void nullExamFailsBeforeObtainingConnection() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(database).create(1002, (Exam) null)
        );

        assertEquals("Exam creation data is missing", thrown.getMessage());
        assertEquals(0, database.connectionRequests);
    }

    @Test
    public void createsDraftVersionAndSelectionsAtomicallyUsingAuthenticatedIdentity()
            throws Exception {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController(false);
        SuccessfulPlans plans = successfulPlans(database, 45);
        Exam exam = exam();

        int examId = new ExamRepository(database, (connection, courseId) -> "010101")
                .create(1002, exam);

        assertEquals(45, examId);
        assertEquals(1, database.connectionRequests);
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
        assertFalse(database.autoCommit);
        assertEquals(2, database.autoCommitSetCalls);

        assertEquals(Map.of(1, 1002, 2, 7), plans.course.queryExecutions.get(0));
        assertEquals(2, plans.question.queryExecutions.size());
        assertEquals(Map.of(1, 1002, 2, 2, 3, 18, 4, 7, 5, 2),
                plans.question.queryExecutions.get(0));
        assertEquals(Map.of(1, 1002, 2, 4, 3, 17, 4, 7, 5, 4),
                plans.question.queryExecutions.get(1));

        String courseSql = normalized(plans.course.sql);
        assertTrue(courseSql.contains("JOIN TEACHER_COURSES TC"));
        assertTrue(courseSql.contains("TC.TEACHER_USER_ID = ?"));
        assertTrue(courseSql.contains("FOR UPDATE"));

        String questionSql = normalized(plans.question.sql);
        assertTrue(questionSql.contains("Q.COURSE_ID = ?"));
        assertTrue(questionSql.contains("Q.STATUS = 'ACTIVE'"));
        assertTrue(questionSql.contains("Q.CURRENT_VERSION_NO = ?"));
        assertTrue(questionSql.contains("QV.VERSION_NO = ?"));
        assertTrue(questionSql.contains("TC.TEACHER_USER_ID = ?"));
        assertTrue(questionSql.contains("FOR UPDATE"));

        Map<Integer, Object> stable = plans.exam.updateExecutions.get(0);
        assertTrue(stable.get(1).toString().matches("[A-Z0-9]{6}"));
        assertEquals(7, stable.get(2));
        assertEquals(1002, stable.get(3));
        assertTrue(stable.containsKey(4));
        assertEquals(null, stable.get(4));

        Map<Integer, Object> version = plans.version.updateExecutions.get(0);
        assertEquals(45, version.get(1));
        assertEquals(1, version.get(2));
        assertEquals("Midterm", version.get(3));
        assertEquals(90, version.get(4));
        assertEquals("Teacher only", version.get(5));
        assertEquals("Read carefully", version.get(6));
        assertEquals(0, ((BigDecimal) version.get(7)).compareTo(new BigDecimal("100.0")));
        assertEquals("DRAFT", version.get(8));
        assertEquals(1002, version.get(9));
        assertTrue(version.get(10) != null);
        for (int index = 11; index <= 14; index++) {
            assertTrue(version.containsKey(index));
            assertEquals(null, version.get(index));
        }

        assertEquals(2, plans.selection.updateExecutions.size());
        assertSelection(plans.selection.updateExecutions.get(0), 45, 1, 1, 18, 2,
                new BigDecimal("99.9"));
        assertSelection(plans.selection.updateExecutions.get(1), 45, 1, 2, 17, 4,
                new BigDecimal("0.1"));
        assertEquals(Map.of(1, 45), plans.pointer.updateExecutions.get(0));

        assertAppearsInOrder(database.events,
                "query:" + COURSE_MARKER,
                "query:" + QUESTION_MARKER,
                "query:" + QUESTION_MARKER,
                "update:" + EXAM_MARKER,
                "update:" + VERSION_MARKER,
                "update:" + SELECTION_MARKER,
                "update:" + SELECTION_MARKER,
                "update:" + POINTER_MARKER,
                "commit"
        );
    }

    @Test
    public void unassignedCourseIsDomainErrorBeforeAnyInsertAndRollsBack() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(COURSE_MARKER).queryRows();

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(database).create(1002, exam())
        );

        assertEquals("Course is not assigned to user: 7", thrown.getMessage());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.plans.stream().allMatch(plan -> plan.updateExecutions.isEmpty()));
        assertTrue(database.autoCommit);
    }

    @Test
    public void staleOrUnavailableQuestionIsDomainErrorBeforeAnyInsertAndRollsBack() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(COURSE_MARKER).queryRows(row("course_id", 7));
        database.plan(QUESTION_MARKER).queryRows();

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(database).create(1002, exam())
        );

        assertEquals("Question unavailable for exam: 18", thrown.getMessage());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
        assertTrue(database.plans.stream().allMatch(plan -> plan.updateExecutions.isEmpty()));
    }

    @Test
    public void retriesOnlyNamedExamCodeCollisionAndUsesNextGeneratedCode() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        SuccessfulPlans plans = validationPlans(database);
        SQLException collision = new SQLException(
                "Duplicate entry for key 'exams.uq_exams_exam_code'",
                "23000",
                1062
        );
        plans.exam = database.plan(EXAM_MARKER)
                .updateFailure(collision)
                .updateResults(1)
                .generatedKey(77);
        plans.version = database.plan(VERSION_MARKER).updateResults(1);
        plans.selection = database.plan(SELECTION_MARKER).updateResults(1, 1);
        plans.pointer = database.plan(POINTER_MARKER).updateResults(1);
        Deque<String> codes = new ArrayDeque<>(List.of("010101", "010102"));

        int examId = new ExamRepository(database, (connection, courseId) -> codes.removeFirst())
                .create(1002, exam());

        assertEquals(77, examId);
        assertEquals(2, plans.exam.updateExecutions.size());
        assertEquals("010101", plans.exam.updateExecutions.get(0).get(1));
        assertEquals("010102", plans.exam.updateExecutions.get(1).get(1));
        assertEquals(1, database.commitCount);
        assertEquals(0, database.rollbackCount);
    }

    @Test
    public void unrelatedSqlFailureIsNotRetriedAndKeepsOriginalCause() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        SuccessfulPlans plans = validationPlans(database);
        SQLException failure = new SQLException("connection lost", "08006", 0);
        plans.exam = database.plan(EXAM_MARKER).updateFailure(failure);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(database, (connection, courseId) -> "010101").create(1002, exam())
        );

        assertEquals("Failed to create exam", thrown.getMessage());
        assertSame(failure, thrown.getCause());
        assertEquals(1, plans.exam.updateExecutions.size());
        assertEquals(1, database.rollbackCount);
        assertEquals(0, database.commitCount);
    }

    @Test
    public void missingGeneratedKeyIsCreationFailureAndRollsBack() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        SuccessfulPlans plans = validationPlans(database);
        plans.exam = database.plan(EXAM_MARKER).updateResults(1).noGeneratedKey();

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(database, (connection, courseId) -> "010101").create(1002, exam())
        );

        assertEquals("Failed to create exam", thrown.getMessage());
        assertTrue(thrown.getCause() instanceof SQLException);
        assertEquals(1, database.rollbackCount);
    }

    @Test
    public void everyWriteFailureRollsBackWithoutCommitting() {
        for (String failingMarker : List.of(
                VERSION_MARKER,
                SELECTION_MARKER,
                POINTER_MARKER
        )) {
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                    new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
            SuccessfulPlans plans = validationPlans(database);
            plans.exam = database.plan(EXAM_MARKER).updateResults(1).generatedKey(45);
            plans.version = database.plan(VERSION_MARKER);
            plans.selection = database.plan(SELECTION_MARKER);
            plans.pointer = database.plan(POINTER_MARKER);
            SQLException failure = new SQLException("write failed: " + failingMarker);

            if (VERSION_MARKER.equals(failingMarker)) {
                plans.version.updateFailure(failure);
            } else {
                plans.version.updateResults(1);
                if (SELECTION_MARKER.equals(failingMarker)) {
                    plans.selection.updateFailure(failure);
                } else {
                    plans.selection.updateResults(1, 1);
                    plans.pointer.updateFailure(failure);
                }
            }

            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> new ExamRepository(database, (connection, courseId) -> "010101")
                            .create(1002, exam())
            );

            assertSame(failure, thrown.getCause());
            assertEquals(1, database.rollbackCount);
            assertEquals(0, database.commitCount);
            assertTrue(database.autoCommit);
        }
    }

    @Test
    public void rollbackAndRestorationFailuresAreSuppressedOnOriginalDomainFailure() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan(COURSE_MARKER).queryRows();
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        database.rollbackFailure = rollbackFailure;
        database.restorationFailure = restorationFailure;

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new ExamRepository(database).create(1002, exam())
        );

        assertEquals("Course is not assigned to user: 7", thrown.getMessage());
        assertEquals(2, thrown.getSuppressed().length);
        assertSame(rollbackFailure, thrown.getSuppressed()[0]);
        assertSame(restorationFailure, thrown.getSuppressed()[1]);
    }

    @Test
    public void cleanupFailuresAreSuppressedOnOriginalJdbcCause() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        SQLException original = new SQLException("authorization query failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        database.plan(COURSE_MARKER).queryFailure(original);
        database.rollbackFailure = rollbackFailure;
        database.restorationFailure = restorationFailure;

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ExamRepository(database).create(1002, exam())
        );

        assertEquals("Failed to create exam", thrown.getMessage());
        assertSame(original, thrown.getCause());
        assertEquals(2, original.getSuppressed().length);
        assertSame(rollbackFailure, original.getSuppressed()[0]);
        assertSame(restorationFailure, original.getSuppressed()[1]);
    }

    private static SuccessfulPlans successfulPlans(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database,
            int generatedId) {
        SuccessfulPlans plans = validationPlans(database);
        plans.exam = database.plan(EXAM_MARKER).updateResults(1).generatedKey(generatedId);
        plans.version = database.plan(VERSION_MARKER).updateResults(1);
        plans.selection = database.plan(SELECTION_MARKER).updateResults(1, 1);
        plans.pointer = database.plan(POINTER_MARKER).updateResults(1);
        return plans;
    }

    private static SuccessfulPlans validationPlans(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database) {
        SuccessfulPlans plans = new SuccessfulPlans();
        plans.course = database.plan(COURSE_MARKER).queryRows(row("course_id", 7));
        plans.question = database.plan(QUESTION_MARKER)
                .queryRows(row("question_id", 17))
                .queryRows(row("question_id", 18));
        return plans;
    }

    private static Exam exam() {
        return Exam.createDraft(
                7,
                1002,
                "Midterm",
                90,
                "Teacher only",
                "Read carefully",
                List.of(
                        selection(17, 4, 2, "0.1"),
                        selection(18, 2, 1, "99.9")
                )
        );
    }

    private static ExamQuestion selection(int questionId, int versionNo,
                                          int orderNumber, String score) {
        Question question = new Question(
                questionId,
                "Question " + questionId,
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
        return new ExamQuestion(
                questionId,
                versionNo,
                orderNumber,
                new BigDecimal(score),
                question
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

    private static final class SuccessfulPlans {
        private ExamRepositoryJdbcTestSupport.StatementPlan course;
        private ExamRepositoryJdbcTestSupport.StatementPlan question;
        private ExamRepositoryJdbcTestSupport.StatementPlan exam;
        private ExamRepositoryJdbcTestSupport.StatementPlan version;
        private ExamRepositoryJdbcTestSupport.StatementPlan selection;
        private ExamRepositoryJdbcTestSupport.StatementPlan pointer;
    }
}
