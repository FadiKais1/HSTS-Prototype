package hsts.server.repository;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExamSubmissionTimestampMigrationTest {
    // Source-level checks are guardrails and do not replace live MySQL migration tests.
    private static final String INITIALIZER = read(
            "src", "main", "java", "hsts", "server", "repository",
            "DatabaseInitializer.java"
    );
    private static final String INIT_SQL = read("database", "init.sql");

    @Test
    public void freshSchemaDefinesAuthoritativeSubmissionTimestamps() {
        String table = between(
                INIT_SQL,
                "CREATE TABLE IF NOT EXISTS exam_submissions",
                "CREATE TABLE IF NOT EXISTS student_answers"
        );

        assertContainsAll(table,
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP",
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP",
                "ON UPDATE CURRENT_TIMESTAMP",
                ") ENGINE=InnoDB"
        );
        assertAppearsInOrder(table,
                "started_at DATETIME NOT NULL",
                "submitted_at DATETIME NULL",
                "created_at TIMESTAMP NOT NULL",
                "updated_at TIMESTAMP NOT NULL",
                "status VARCHAR(32) NOT NULL"
        );

        String createdDefinition = between(
                table,
                "created_at TIMESTAMP",
                "updated_at TIMESTAMP"
        );
        assertFalse(createdDefinition.contains("ON UPDATE"));
    }

    @Test
    public void runtimeMigrationAddsAndChecksEachColumnIndependently() {
        assertContainsAll(INITIALIZER,
                "addColumnIfMissing(connection, \"exam_submissions\", \"created_at\", \"TIMESTAMP NULL\")",
                "addColumnIfMissing(connection, \"exam_submissions\", \"updated_at\", \"TIMESTAMP NULL\")",
                "TABLE_NAME = 'exam_submissions'",
                "AND COLUMN_NAME = ?",
                "statement.setString(1, columnName)"
        );
        assertAppearsInOrder(INITIALIZER,
                "createExamSubmissionsTable(connection);",
                "migrateExecutionSchemaColumns(connection);",
                "backfillSubmissionTimestamps(connection);",
                "normalizeSubmissionTimestampColumns(connection);",
                "createExecutionSchemaIndexesAndConstraints(connection);"
        );

        String normalization = between(
                INITIALIZER,
                "private void normalizeSubmissionTimestampColumns",
                "private boolean submissionTimestampDefinitionIsRequired"
        );
        assertContainsAll(normalization,
                "\"created_at\"",
                "false",
                "\"updated_at\"",
                "true"
        );
    }

    @Test
    public void backfillUsesApprovedNullOnlyCompatibilityBaselines() {
        String backfill = between(
                INITIALIZER,
                "private void backfillSubmissionTimestamps",
                "private void validateSubmissionTimestampOrdering"
        );

        assertContainsAll(backfill,
                "SET created_at = COALESCE(",
                "started_at",
                "submitted_at",
                "CURRENT_TIMESTAMP",
                "updated_at = updated_at",
                "WHERE created_at IS NULL",
                "SET updated_at = GREATEST(",
                "created_at",
                "COALESCE(",
                "WHERE updated_at IS NULL"
        );
        assertAppearsInOrder(backfill,
                "SET created_at = COALESCE(",
                "started_at",
                "submitted_at",
                "CURRENT_TIMESTAMP",
                "WHERE created_at IS NULL",
                "SET updated_at = GREATEST(",
                "created_at",
                "submitted_at",
                "started_at",
                "WHERE updated_at IS NULL"
        );
        assertEquals(2, occurrences(normalize(backfill), "UPDATE EXAM_SUBMISSIONS"));
    }

    @Test
    public void backfillIsTransactionalAndFinalDdlRunsAfterCommit() {
        String backfill = between(
                INITIALIZER,
                "private void backfillSubmissionTimestamps",
                "private void validateSubmissionTimestampOrdering"
        );

        assertContainsAll(backfill,
                "boolean originalAutoCommit = connection.getAutoCommit();",
                "connection.setAutoCommit(false);",
                "validateSubmissionTimestampOrdering(connection);",
                "connection.commit();",
                "rollbackWithSuppressed(connection, e);",
                "restoreAutoCommit(connection, originalAutoCommit, migrationFailure);"
        );
        assertFalse(normalize(backfill).contains("ALTER TABLE"));
        assertAppearsInOrder(INITIALIZER,
                "backfillSubmissionTimestamps(connection);",
                "normalizeSubmissionTimestampColumns(connection);"
        );
        assertContainsAll(INITIALIZER,
                "originalException.addSuppressed(rollbackException)",
                "originalException.addSuppressed(restorationException)"
        );
    }

    @Test
    public void finalDefinitionsAndOrderingValidationAreIdempotent() {
        String normalization = between(
                INITIALIZER,
                "private void normalizeSubmissionTimestampColumns",
                "private void createExecutionSchemaIndexesAndConstraints"
        );

        assertContainsAll(normalization,
                "MODIFY COLUMN created_at TIMESTAMP NOT NULL",
                "DEFAULT CURRENT_TIMESTAMP",
                "MODIFY COLUMN updated_at TIMESTAMP NOT NULL",
                "DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP",
                "DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA",
                "hasOnUpdate == onUpdateRequired"
        );
        String createdAlter = between(
                normalization,
                "MODIFY COLUMN created_at",
                "if (!submissionTimestampDefinitionIsRequired("
        );
        assertFalse(createdAlter.contains("ON UPDATE"));

        String orderingCheck = between(
                INITIALIZER,
                "private void validateSubmissionTimestampOrdering",
                "private void normalizeSubmissionTimestampColumns"
        );
        assertContainsAll(orderingCheck,
                "WHERE updated_at < created_at",
                "Existing submission timestamps have invalid ordering"
        );
    }

    @Test
    public void migrationIsAdditiveAndSeedsNoExecutionData() {
        for (String source : new String[]{INIT_SQL, INITIALIZER}) {
            String normalized = normalize(source);
            assertFalse(normalized.contains("DROP TABLE"));
            assertFalse(normalized.contains("TRUNCATE TABLE"));
            assertFalse(normalized.contains("DELETE FROM"));
            assertFalse(normalized.contains("INSERT INTO EXAM_SUBMISSIONS"));
            assertFalse(normalized.contains("INSERT INTO STUDENT_ANSWERS"));
            assertFalse(normalized.contains("INSERT INTO EXAM_EXECUTIONS"));
        }
    }

    private static void assertContainsAll(String source, String... fragments) {
        String normalizedSource = normalize(source);
        for (String fragment : fragments) {
            assertTrue(
                    "Missing fragment: " + fragment,
                    normalizedSource.contains(normalize(fragment))
            );
        }
    }

    private static void assertAppearsInOrder(String source, String... fragments) {
        int position = -1;
        for (String fragment : fragments) {
            int next = source.indexOf(fragment, position + 1);
            assertTrue("Missing or out-of-order fragment: " + fragment, next > position);
            position = next;
        }
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue("Missing start marker: " + startMarker, start >= 0);
        assertTrue("Missing end marker: " + endMarker, end > start);
        return source.substring(start, end);
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        int position = 0;
        while ((position = source.indexOf(value, position)) >= 0) {
            count++;
            position += value.length();
        }
        return count;
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private static String read(String... pathParts) {
        try {
            return Files.readString(Path.of("", pathParts), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read schema source", e);
        }
    }
}
