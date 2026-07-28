package hsts.server.repository;

import org.junit.Test;

import java.sql.SQLException;

import static hsts.server.repository.BotRepositoryJdbcTestSupport.failure;
import static hsts.server.repository.BotRepositoryJdbcTestSupport.query;
import static hsts.server.repository.BotRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionRepositoryCourseLockoutTest {
    @Test
    public void exactStudentCourseInProgressQueryBindsIdentityAndScope() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("submission.status = 'IN_PROGRESS'", row("in_progress", 1))
                );

        assertTrue(new ExamSubmissionRepository(database)
                .existsInProgressForStudentAndCourse(1001, 7));
        assertEquals(1001, database.executed.get(0).parameters.get(1));
        assertEquals(7, database.executed.get(0).parameters.get(2));
        String sql = database.executed.get(0).actualSql;
        assertTrue(sql.contains("JOIN exam_executions"));
        assertTrue(sql.contains("JOIN exams"));
        assertFalse(sql.contains("question"));
        assertFalse(sql.contains("answer"));
        database.assertConsumed();
    }

    @Test
    public void finalizedOrUnrelatedStateIsReportedFalseByAuthoritativeExistsResult() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("submission.status = 'IN_PROGRESS'", row("in_progress", 0))
                );
        assertFalse(new ExamSubmissionRepository(database)
                .existsInProgressForStudentAndCourse(1001, 8));
        database.assertConsumed();
    }

    @Test
    public void invalidIdsAvoidConnectionsAndSqlCauseIsPreserved() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController unused =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamSubmissionRepository repository = new ExamSubmissionRepository(unused);
        assertThrows(IllegalArgumentException.class,
                () -> repository.existsInProgressForStudentAndCourse(0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> repository.existsInProgressForStudentAndCourse(1001, 0));
        assertTrue(unused.executed.isEmpty());

        SQLException cause = new SQLException("offline");
        BotRepositoryJdbcTestSupport.FakeDatabaseController failing =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        failure("submission.status = 'IN_PROGRESS'", cause)
                );
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ExamSubmissionRepository(failing)
                        .existsInProgressForStudentAndCourse(1001, 1));
        assertSame(cause, error.getCause());
    }
}
