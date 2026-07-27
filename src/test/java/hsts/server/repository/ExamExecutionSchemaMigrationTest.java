package hsts.server.repository;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExamExecutionSchemaMigrationTest {
    // Source-level checks are guardrails and do not replace live MySQL migration tests.
    private static final String INITIALIZER = read(
            "src", "main", "java", "hsts", "server", "repository",
            "DatabaseInitializer.java"
    );
    private static final String INIT_SQL = read("database", "init.sql");
    private static final List<String> TABLES = List.of(
            "student_profiles",
            "student_courses",
            "exam_executions",
            "exam_submissions",
            "student_answers",
            "submission_time_extensions",
            "exam_execution_deciles"
    );

    @Test
    public void bothInitializationPathsContainAllTablesInDependencyOrder() {
        assertAppearsInOrder(INIT_SQL,
                "CREATE TABLE IF NOT EXISTS student_profiles",
                "CREATE TABLE IF NOT EXISTS student_courses",
                "CREATE TABLE IF NOT EXISTS exam_executions",
                "CREATE TABLE IF NOT EXISTS exam_submissions",
                "CREATE TABLE IF NOT EXISTS student_answers",
                "CREATE TABLE IF NOT EXISTS submission_time_extensions",
                "CREATE TABLE IF NOT EXISTS exam_execution_deciles",
                "START TRANSACTION"
        );
        assertAppearsInOrder(INITIALIZER,
                "createStudentProfilesTable(connection);",
                "createStudentCoursesTable(connection);",
                "createExamExecutionsTable(connection);",
                "createExamSubmissionsTable(connection);",
                "createStudentAnswersTable(connection);",
                "createSubmissionTimeExtensionsTable(connection);",
                "createExamExecutionDecilesTable(connection);",
                "migrateExecutionSchemaColumns(connection);",
                "createExecutionSchemaIndexesAndConstraints(connection);",
                "insertExecutionCompatibilityData(connection);"
        );

        for (String table : TABLES) {
            assertTrue("Fresh SQL is missing " + table,
                    normalize(INIT_SQL).contains(
                            "CREATE TABLE IF NOT EXISTS " + table.toUpperCase(Locale.ROOT)));
            assertTrue("Initializer is missing " + table,
                    normalize(INITIALIZER).contains(
                            "CREATE TABLE " + table.toUpperCase(Locale.ROOT)));
            assertTableUsesInnoDb(INIT_SQL, "CREATE TABLE IF NOT EXISTS ", table);
            assertTableUsesInnoDb(INITIALIZER, "CREATE TABLE ", table);
            assertTrue("Initializer lacks table metadata check for " + table,
                    INITIALIZER.contains("tableExists(connection, \"" + table + "\")"));
        }
    }

    @Test
    public void executionAndSubmissionConstraintsMatchApprovedLifecycles() {
        for (String source : List.of(INIT_SQL, INITIALIZER)) {
            assertContainsAll(source,
                    "UNIQUE (execution_code)",
                    "execution_code REGEXP '^[A-Z0-9]{4}$'",
                    "FOREIGN KEY (exam_id, exam_version_no)",
                    "REFERENCES exam_versions (exam_id, version_no)",
                    "CHECK (opening_time < closing_time)",
                    "CHECK (duration_minutes > 0)",
                    "CHECK (status IN ('SCHEDULED', 'OPEN', 'CLOSED'))",
                    "UNIQUE (execution_id, student_user_id)",
                    "CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'AUTO_SUBMITTED', 'PUBLISHED'))",
                    "CHECK (allocated_duration_minutes > 0)",
                    "CHECK (extra_minutes >= 0)",
                    "automatic_score IS NULL OR automatic_score BETWEEN 0 AND 100",
                    "final_score IS NULL OR final_score BETWEEN 0 AND 100"
            );
        }
    }

    @Test
    public void answersExtensionsAndDecilesHaveRequiredKeysAndChecks() {
        for (String source : List.of(INIT_SQL, INITIALIZER)) {
            assertContainsAll(source,
                    "UNIQUE (submission_id, question_id)",
                    "FOREIGN KEY (question_id, question_version_no)",
                    "REFERENCES question_versions (question_id, version_no)",
                    "CHECK (selected_option_number BETWEEN 1 AND 4)",
                    "CHECK (score_received IS NULL OR score_received >= 0)",
                    "CREATE TABLE " + executionTablePrefix(source)
                            + "submission_time_extensions",
                    "FOREIGN KEY (submission_id) REFERENCES exam_submissions (submission_id)",
                    "CHECK (added_minutes > 0)",
                    "PRIMARY KEY (execution_id, decile_number)",
                    "CHECK (decile_number BETWEEN 1 AND 10)",
                    "CHECK (submission_count >= 0)"
            );
        }
    }

    @Test
    public void cascadeDeletionIsLimitedToOwnedExecutionChildren() {
        for (String source : List.of(INIT_SQL, INITIALIZER)) {
            String executionSchema = executionSchemaSection(source);
            String normalizedExecutionSchema = normalize(executionSchema);
            assertContainsAll(normalizedExecutionSchema,
                    "REFERENCES exam_executions (execution_id) ON DELETE CASCADE",
                    "REFERENCES exam_submissions (submission_id) ON DELETE CASCADE"
            );
            assertFalse(normalizedExecutionSchema.matches(
                    ".*REFERENCES (USERS|COURSES|EXAMS|EXAM_VERSIONS|QUESTIONS|QUESTION_VERSIONS)"
                            + " \\([^)]*\\) ON DELETE CASCADE.*"));
        }
    }

    @Test
    public void compatibilityProfileAndEnrollmentAreGuardedAndNonOverwriting() {
        String identityNumber = String.join("", "123", "456", "789");

        assertFalse(INIT_SQL.contains(identityNumber));
        assertEquals(1, occurrences(INITIALIZER, identityNumber));
        assertContainsAll(INITIALIZER,
                "PasswordHasher.hash(\"" + identityNumber + "\")",
                "studentProfileExists(connection, 1001)",
                "student.role = 'STUDENT'",
                "student.status = 'ACTIVE'",
                "NOT EXISTS (",
                "FROM student_profiles existing_profile",
                "FROM student_courses existing_enrollment"
        );
        assertContainsAll(INIT_SQL,
                "INSERT INTO student_courses",
                "student.user_id = 1001",
                "student.role = 'STUDENT'",
                "student.status = 'ACTIVE'",
                "FROM student_courses existing_enrollment"
        );
        assertFalse(normalize(INIT_SQL).contains("INSERT INTO STUDENT_PROFILES"));
        assertFalse(normalize(INITIALIZER).contains("UPDATE STUDENT_PROFILES"));
        assertFalse(normalize(INITIALIZER).contains("UPDATE STUDENT_COURSES"));
    }

    @Test
    public void runtimeDdlPrecedesCompatibilityTransactionAndRestoresConnectionState() {
        assertAppearsInOrder(INITIALIZER,
                "createExamExecutionDecilesTable(connection);",
                "migrateExecutionSchemaColumns(connection);",
                "backfillExecutionUpdatedAt(connection);",
                "normalizeExecutionUpdatedAtColumn(connection);",
                "createExecutionSchemaIndexesAndConstraints(connection);",
                "insertExecutionCompatibilityData(connection);"
        );
        String compatibilityMethod = between(
                INITIALIZER,
                "private void insertExecutionCompatibilityData",
                "private void validateExecutionCompatibilityStudent"
        );
        assertContainsAll(compatibilityMethod,
                "boolean originalAutoCommit = connection.getAutoCommit();",
                "connection.setAutoCommit(false);",
                "connection.commit();",
                "rollbackWithSuppressed(connection, e);",
                "restoreAutoCommit(connection, originalAutoCommit, migrationFailure);"
        );
        assertAppearsInOrder(INIT_SQL,
                "CREATE TABLE IF NOT EXISTS exam_execution_deciles",
                "START TRANSACTION",
                "INSERT INTO student_courses",
                "COMMIT"
        );
    }

    @Test
    public void initializerMetadataChecksEveryAdditiveObject() {
        assertContainsAll(INITIALIZER,
                "addColumnIfMissing(connection, \"student_profiles\"",
                "addColumnIfMissing(connection, \"student_courses\"",
                "addColumnIfMissing(connection, \"exam_executions\"",
                "addColumnIfMissing(connection, \"exam_submissions\"",
                "addColumnIfMissing(connection, \"student_answers\"",
                "addColumnIfMissing(connection, \"submission_time_extensions\"",
                "addColumnIfMissing(connection, \"exam_execution_deciles\"",
                "ensureIndex(connection, \"exam_executions\"",
                "ensureIndex(connection, \"exam_submissions\"",
                "ensureIndex(connection, \"student_answers\"",
                "ensureConstraint(connection, \"student_profiles\"",
                "ensureConstraint(connection, \"student_courses\"",
                "ensureConstraint(connection, \"exam_executions\"",
                "ensureConstraint(connection, \"exam_submissions\"",
                "ensureConstraint(connection, \"student_answers\"",
                "ensureConstraint(connection, \"submission_time_extensions\"",
                "ensureConstraint(connection, \"exam_execution_deciles\""
        );
    }

    @Test
    public void executionUpdatedAtHasAuthoritativeAutomaticDefinition() {
        String freshExecutionTable = between(
                INIT_SQL,
                "CREATE TABLE IF NOT EXISTS exam_executions",
                "CREATE TABLE IF NOT EXISTS exam_submissions"
        );
        String runtimeExecutionTable = between(
                INITIALIZER,
                "private void createExamExecutionsTable",
                "private void createExamSubmissionsTable"
        );

        for (String definition : List.of(freshExecutionTable, runtimeExecutionTable)) {
            assertContainsAll(definition,
                    "updated_at TIMESTAMP NOT NULL",
                    "DEFAULT CURRENT_TIMESTAMP",
                    "ON UPDATE CURRENT_TIMESTAMP",
                    ") ENGINE=InnoDB"
            );
            assertAppearsInOrder(definition,
                    "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP",
                    "updated_at TIMESTAMP NOT NULL",
                    "closed_at DATETIME NULL"
            );
        }
    }

    @Test
    public void runtimeUpdatedAtMigrationBackfillsThenNormalizesWithoutOverwrite() {
        assertContainsAll(INITIALIZER,
                "addColumnIfMissing(connection, \"exam_executions\", \"updated_at\", \"TIMESTAMP NULL\")",
                "FROM information_schema.COLUMNS",
                "TABLE_NAME = 'exam_executions'",
                "COLUMN_NAME = 'updated_at'",
                "MODIFY COLUMN updated_at TIMESTAMP NOT NULL",
                "DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
        );

        String backfill = between(
                INITIALIZER,
                "private void backfillExecutionUpdatedAt",
                "private void normalizeExecutionUpdatedAtColumn"
        );
        assertContainsAll(backfill,
                "boolean originalAutoCommit = connection.getAutoCommit();",
                "connection.setAutoCommit(false);",
                "SET updated_at = COALESCE(closed_at, created_at)",
                "WHERE updated_at IS NULL",
                "connection.commit();",
                "rollbackWithSuppressed(connection, e);",
                "restoreAutoCommit(connection, originalAutoCommit, migrationFailure);"
        );
        assertFalse(backfill.contains("CURRENT_TIMESTAMP"));

        assertAppearsInOrder(INITIALIZER,
                "addColumnIfMissing(connection, \"exam_executions\", \"updated_at\", \"TIMESTAMP NULL\")",
                "private void backfillExecutionUpdatedAt",
                "private void normalizeExecutionUpdatedAtColumn"
        );
    }

    @Test
    public void updatedAtMigrationIsStructuralAndNonDestructive() {
        String executionMigration = between(
                INITIALIZER,
                "private void migrateExecutionSchema",
                "private void validateExecutionCompatibilityStudent"
        );
        String normalized = normalize(executionMigration);

        assertFalse(normalized.contains("DROP TABLE"));
        assertFalse(normalized.contains("TRUNCATE TABLE"));
        assertFalse(normalized.contains("DELETE FROM"));
        assertFalse(normalized.contains("INSERT INTO EXAM_EXECUTIONS"));
        assertEquals(1, occurrences(normalized, "UPDATE EXAM_EXECUTIONS"));
        assertTrue("Structural checks do not replace a live MySQL migration check",
                INITIALIZER.contains(
                        "SET updated_at = COALESCE(closed_at, created_at)"
                ));
    }

    @Test
    public void migrationIsNonDestructiveAndSeedsNoExecutionOrGradeData() {
        for (String source : List.of(INIT_SQL, INITIALIZER)) {
            String normalized = normalize(source);
            assertFalse(normalized.contains("DROP TABLE"));
            assertFalse(normalized.contains("TRUNCATE TABLE"));
            assertFalse(normalized.contains("DELETE FROM"));
            assertFalse(normalized.contains("INSERT INTO EXAM_EXECUTIONS"));
            assertFalse(normalized.contains("INSERT INTO EXAM_SUBMISSIONS"));
            assertFalse(normalized.contains("INSERT INTO STUDENT_ANSWERS"));
            assertFalse(normalized.contains("INSERT INTO SUBMISSION_TIME_EXTENSIONS"));
            assertFalse(normalized.contains("INSERT INTO EXAM_EXECUTION_DECILES"));
        }
    }

    private static String executionTablePrefix(String source) {
        return source == INIT_SQL ? "IF NOT EXISTS " : "";
    }

    private static String executionSchemaSection(String source) {
        String startMarker = source == INIT_SQL
                ? "CREATE TABLE IF NOT EXISTS student_profiles"
                : "private void createStudentProfilesTable";
        String endMarker = source == INIT_SQL
                ? "CREATE TEMPORARY TABLE"
                : "private void createSubjectCoordinatorsTable";
        return between(source, startMarker, endMarker);
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue("Missing start marker: " + startMarker, start >= 0);
        assertTrue("Missing end marker: " + endMarker, end > start);
        return source.substring(start, end);
    }

    private static void assertTableUsesInnoDb(String source, String prefix,
                                               String tableName) {
        String normalized = normalize(source);
        String marker = normalize(prefix + tableName);
        int start = normalized.indexOf(marker);
        assertTrue("Missing table definition: " + tableName, start >= 0);
        int end = normalized.indexOf(") ENGINE=INNODB", start);
        assertTrue("Table does not explicitly use InnoDB: " + tableName, end > start);
    }

    private static void assertAppearsInOrder(String source, String... fragments) {
        int position = -1;
        for (String fragment : fragments) {
            int next = source.indexOf(fragment, position + 1);
            assertTrue("Missing or out-of-order fragment: " + fragment, next > position);
            position = next;
        }
    }

    private static void assertContainsAll(String source, String... fragments) {
        for (String fragment : fragments) {
            assertTrue("Missing fragment: " + fragment,
                    source.contains(fragment) || source.contains(normalize(fragment)));
        }
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
            throw new IllegalStateException("Failed to read execution schema source", e);
        }
    }
}
