package hsts.server.repository;

import org.junit.Test;

import java.sql.SQLException;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StudentEnrollmentRepositoryTest {
    @Test
    public void exactStudentCoursePairRequiresActiveStudentAndUsesOnlySelect() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan enrollment = database.plan(
                "FROM student_courses sc"
        ).queryRows(row("enrolled", 1));

        boolean result = new StudentEnrollmentRepository(database).isEnrolled(1001, 7);

        assertTrue(result);
        assertEquals(Map.of(1, 1001, 2, 7), enrollment.queryExecutions.get(0));
        String sql = normalized(enrollment.sql);
        assertTrue(sql.startsWith("SELECT EXISTS"));
        assertTrue(sql.contains("JOIN USERS STUDENT"));
        assertTrue(sql.contains("STUDENT.ROLE = 'STUDENT'"));
        assertTrue(sql.contains("STUDENT.STATUS = 'ACTIVE'"));
        assertTrue(sql.contains("SC.STUDENT_USER_ID = ?"));
        assertTrue(sql.contains("SC.COURSE_ID = ?"));
        assertNoWrites(database);
    }

    @Test
    public void missingBlockedOrNonstudentScopeReturnsFalse() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.plan("FROM student_courses sc").queryRows(row("enrolled", 0));

        assertFalse(new StudentEnrollmentRepository(database).isEnrolled(1001, 7));
        assertNoWrites(database);
    }

    @Test
    public void sqlFailureUsesExactWrapperAndPreservesCause() {
        SQLException failure = new SQLException("read failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.connectionFailure = failure;

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new StudentEnrollmentRepository(database).isEnrolled(1001, 7)
        );

        assertEquals("Failed to check student enrollment", thrown.getMessage());
        assertSame(failure, thrown.getCause());
    }

    private static void assertNoWrites(
            ExamRepositoryJdbcTestSupport.FakeDatabaseController database
    ) {
        assertTrue(database.plans.stream()
                .allMatch(plan -> plan.updateExecutions.isEmpty()));
        assertTrue(database.events.stream().noneMatch(event -> event.startsWith("update:")));
    }

    private static String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }
}
