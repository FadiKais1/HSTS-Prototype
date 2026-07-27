package hsts.server.repository;

import hsts.server.security.PasswordHasher;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Map;

import static hsts.server.repository.ExamRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StudentProfileRepositoryTest {
    @Test
    public void nullAndBlankConfirmationShortCircuitBeforeConnection() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        StudentProfileRepository repository = new StudentProfileRepository(database);

        assertFalse(repository.matchesIdentity(1001, null));
        assertFalse(repository.matchesIdentity(1001, "   "));
        assertEquals(0, database.connectionRequests);
    }

    @Test
    public void matchesRealHashForOnlyAuthenticatedActiveStudentUsingSelect() {
        String confirmation = "test-only-confirmation";
        String encodedHash = PasswordHasher.hash(confirmation);
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        ExamRepositoryJdbcTestSupport.StatementPlan profile = database.plan(
                "FROM student_profiles sp"
        ).queryRows(row("identity_number_hash", encodedHash));

        boolean result = new StudentProfileRepository(database)
                .matchesIdentity(1001, confirmation);

        assertTrue(result);
        assertEquals(Map.of(1, 1001), profile.queryExecutions.get(0));
        String sql = normalized(profile.sql);
        assertTrue(sql.startsWith("SELECT SP.IDENTITY_NUMBER_HASH"));
        assertTrue(sql.contains("JOIN USERS STUDENT"));
        assertTrue(sql.contains("STUDENT.ROLE = 'STUDENT'"));
        assertTrue(sql.contains("STUDENT.STATUS = 'ACTIVE'"));
        assertTrue(sql.contains("WHERE SP.USER_ID = ?"));
        assertFalse(sql.contains(confirmation.toUpperCase()));
        assertNoWrites(database);
    }

    @Test
    public void missingProfileOrMismatchedIdentityReturnsFalse() {
        ExamRepositoryJdbcTestSupport.FakeDatabaseController missingDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        missingDatabase.plan("FROM student_profiles sp").queryRows();

        assertFalse(new StudentProfileRepository(missingDatabase)
                .matchesIdentity(1001, "wrong-confirmation"));

        ExamRepositoryJdbcTestSupport.FakeDatabaseController mismatchDatabase =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        mismatchDatabase.plan("FROM student_profiles sp").queryRows(
                row("identity_number_hash", PasswordHasher.hash("expected-confirmation"))
        );

        assertFalse(new StudentProfileRepository(mismatchDatabase)
                .matchesIdentity(1001, "wrong-confirmation"));
        assertNoWrites(missingDatabase);
        assertNoWrites(mismatchDatabase);
    }

    @Test
    public void sqlFailureUsesPasswordFreeWrapperAndPreservesCause() {
        String confirmation = "private-confirmation";
        SQLException failure = new SQLException("profile read failed");
        ExamRepositoryJdbcTestSupport.FakeDatabaseController database =
                new ExamRepositoryJdbcTestSupport.FakeDatabaseController();
        database.connectionFailure = failure;

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new StudentProfileRepository(database)
                        .matchesIdentity(1001, confirmation)
        );

        assertEquals("Failed to validate student identity", thrown.getMessage());
        assertFalse(thrown.getMessage().contains(confirmation));
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
