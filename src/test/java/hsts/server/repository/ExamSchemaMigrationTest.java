package hsts.server.repository;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExamSchemaMigrationTest {
    // Source-level guardrails do not replace a live MySQL migration and rerun test.
    private static final String INITIALIZER = read(
            "src", "main", "java", "hsts", "server", "repository",
            "DatabaseInitializer.java"
    );
    private static final String INIT_SQL = read("database", "init.sql");

    @Test
    public void bothInitializationPathsContainAllExamTablesInDependencyOrder() {
        assertAppearsInOrder(INIT_SQL,
                "CREATE TABLE IF NOT EXISTS subject_coordinators",
                "CREATE TABLE IF NOT EXISTS exams",
                "CREATE TABLE IF NOT EXISTS exam_versions",
                "CREATE TABLE IF NOT EXISTS exam_version_questions"
        );
        assertAppearsInOrder(INITIALIZER,
                "createSubjectCoordinatorsTable(connection);",
                "createExamsTable(connection);",
                "createExamVersionsTable(connection);",
                "createExamVersionQuestionsTable(connection);",
                "createExamSchemaIndexesAndConstraints(connection);",
                "insertCompatibilityCoordinatorAssignment(connection);"
        );

        for (String source : new String[]{INIT_SQL, INITIALIZER}) {
            assertContainsAll(source,
                    "subject_coordinators",
                    "exams",
                    "exam_versions",
                    "exam_version_questions"
            );
        }

        for (String tableName : new String[]{
                "subject_coordinators", "exams", "exam_versions",
                "exam_version_questions"
        }) {
            assertTableUsesInnoDb(INIT_SQL, tableName);
            assertTableUsesInnoDb(INITIALIZER, tableName);
        }
    }

    @Test
    public void coordinatorAssignmentsHaveKeysIndexesAndForeignKeys() {
        for (String source : new String[]{INIT_SQL, INITIALIZER}) {
            assertContainsAll(source,
                    "subject_id INT NOT NULL",
                    "coordinator_user_id INT NOT NULL",
                    "assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP",
                    "PRIMARY KEY (subject_id, coordinator_user_id)",
                    "idx_subject_coordinators_coordinator_user_id",
                    "fk_subject_coordinators_subject",
                    "FOREIGN KEY (subject_id) REFERENCES subjects (subject_id)",
                    "fk_subject_coordinators_user",
                    "FOREIGN KEY (coordinator_user_id) REFERENCES users (user_id)"
            );
        }
    }

    @Test
    public void stableExamIdentityUsesSeparateConstrainedBusinessCode() {
        for (String source : new String[]{INIT_SQL, INITIALIZER}) {
            assertContainsAll(source,
                    "exam_id INT NOT NULL AUTO_INCREMENT",
                    "exam_code CHAR(6)",
                    "PRIMARY KEY (exam_id)",
                    "uq_exams_exam_code",
                    "UNIQUE (exam_code)",
                    "CHECK (exam_code REGEXP '^[A-Z0-9]{6}$')",
                    "current_version_no INT NULL",
                    "CHECK (current_version_no IS NULL OR current_version_no > 0)",
                    "idx_exams_course_id",
                    "idx_exams_created_by_user_id",
                    "fk_exams_course",
                    "fk_exams_creator"
            );
            assertFalse(normalize(source).contains("PRIMARY KEY (EXAM_CODE)"));
        }
    }

    @Test
    public void immutableVersionsUseApprovedStatusAndScoreConstraints() {
        for (String source : new String[]{INIT_SQL, INITIALIZER}) {
            assertContainsAll(source,
                    "PRIMARY KEY (exam_id, version_no)",
                    "duration_minutes INT NOT NULL",
                    "total_score DECIMAL(7,2) NOT NULL",
                    "status VARCHAR(32) NOT NULL",
                    "CHECK (version_no > 0)",
                    "CHECK (duration_minutes > 0)",
                    "CHECK (total_score = 100.00)",
                    "CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED'))",
                    "idx_exam_versions_status",
                    "idx_exam_versions_reviewed_by_user_id",
                    "idx_exam_versions_submitted_at",
                    "fk_exam_versions_exam",
                    "fk_exam_versions_creator",
                    "fk_exam_versions_reviewer"
            );
            assertFalse(normalize(source).contains("STATUS ENUM("));
        }
    }

    @Test
    public void examQuestionsReferenceImmutableQuestionVersionsWithoutDeletingThem() {
        for (String source : new String[]{INIT_SQL, INITIALIZER}) {
            String examQuestionDefinition = tableDefinition(
                    source, "exam_version_questions"
            );
            assertContainsAll(source,
                    "PRIMARY KEY (exam_id, exam_version_no, order_number)",
                    "UNIQUE (exam_id, exam_version_no, question_id)",
                    "score DECIMAL(7,2) NOT NULL",
                    "CHECK (order_number > 0)",
                    "CHECK (question_version_no > 0)",
                    "CHECK (score > 0)",
                    "FOREIGN KEY (exam_id, exam_version_no)",
                    "REFERENCES exam_versions (exam_id, version_no) ON DELETE CASCADE",
                    "FOREIGN KEY (question_id, question_version_no)",
                    "REFERENCES question_versions (question_id, version_no)",
                    "idx_exam_version_questions_question_version"
            );
            assertFalse(normalize(examQuestionDefinition).contains(
                    "REFERENCES QUESTION_VERSIONS (QUESTION_ID, VERSION_NO) ON DELETE"
            ));
            assertFalse(normalize(examQuestionDefinition).contains(
                    "REFERENCES QUESTIONS (QUESTION_ID) ON DELETE"
            ));
        }
    }

    private static String tableDefinition(String source, String tableName) {
        String marker = "CREATE TABLE IF NOT EXISTS " + tableName;
        int start = source.lastIndexOf(marker);
        if (start < 0) {
            marker = "CREATE TABLE " + tableName;
            start = source.lastIndexOf(marker);
        }
        int end = source.indexOf("ENGINE=InnoDB", start);
        if (start < 0 || end < 0) {
            throw new AssertionError("Missing table definition: " + tableName);
        }
        return source.substring(start, end + "ENGINE=InnoDB".length());
    }

    @Test
    public void compatibilityAssignmentIsGuardedRoleCheckedAndNonOverwriting() {
        for (String source : new String[]{INIT_SQL, INITIALIZER}) {
            assertContainsAll(source,
                    "INSERT INTO subject_coordinators",
                    "subject_record.subject_id = 1",
                    "subject_record.subject_code = 'LEGACY'",
                    "coordinator.user_id = 1003",
                    "coordinator.email = 'coordinator@hsts.local'",
                    "coordinator.role = 'COORDINATOR'",
                    "NOT EXISTS",
                    "existing_assignment.subject_id = subject_record.subject_id",
                    "existing_assignment.coordinator_user_id = coordinator.user_id"
            );
        }

        assertAppearsInOrder(INITIALIZER,
                "validateCompatibilityUser(",
                "validateCompatibilitySubject(connection, true);",
                "INSERT INTO subject_coordinators"
        );
    }

    @Test
    public void ddlPrecedesTransactionalAssignmentAndTransactionStateIsRestored() {
        String normalizedSql = normalize(INIT_SQL);
        int transactionStart = normalizedSql.indexOf("START TRANSACTION;");
        int assignment = normalizedSql.indexOf("INSERT INTO SUBJECT_COORDINATORS", transactionStart);
        int commit = normalizedSql.indexOf("COMMIT;", assignment);

        assertTrue(transactionStart >= 0);
        assertTrue(normalizedSql.lastIndexOf("CREATE ") < transactionStart);
        assertTrue(assignment > transactionStart && assignment < commit);

        assertAppearsInOrder(INITIALIZER,
                "createSubjectCoordinatorsTable(connection);",
                "createExamsTable(connection);",
                "createExamVersionsTable(connection);",
                "createExamVersionQuestionsTable(connection);",
                "createExamSchemaIndexesAndConstraints(connection);",
                "insertCompatibilityCoordinatorAssignment(connection);"
        );
        assertContainsAll(INITIALIZER,
                "boolean originalAutoCommit = connection.getAutoCommit()",
                "connection.setAutoCommit(false)",
                "connection.commit()",
                "rollbackWithSuppressed(connection, e)",
                "restoreAutoCommit(connection, originalAutoCommit, migrationFailure)",
                "originalException.addSuppressed(rollbackException)",
                "originalException.addSuppressed(restorationException)"
        );
    }

    @Test
    public void migrationIsIdempotentAndContainsNoDestructiveOrSampleExamData() {
        assertContainsAll(INIT_SQL,
                "CREATE TABLE IF NOT EXISTS subject_coordinators",
                "CREATE TABLE IF NOT EXISTS exams",
                "CREATE TABLE IF NOT EXISTS exam_versions",
                "CREATE TABLE IF NOT EXISTS exam_version_questions"
        );
        assertContainsAll(INITIALIZER,
                "tableExists(connection, \"subject_coordinators\")",
                "tableExists(connection, \"exams\")",
                "tableExists(connection, \"exam_versions\")",
                "tableExists(connection, \"exam_version_questions\")",
                "ensureIndex(connection",
                "ensureConstraint(connection"
        );

        for (String source : new String[]{INIT_SQL, INITIALIZER}) {
            String normalized = normalize(source);
            assertFalse(normalized.contains("DROP TABLE"));
            assertFalse(normalized.contains("TRUNCATE TABLE"));
            assertFalse(normalized.contains("REPLACE INTO"));
            assertFalse(normalized.contains("INSERT INTO EXAMS"));
            assertFalse(normalized.contains("INSERT INTO EXAM_VERSIONS"));
            assertFalse(normalized.contains("INSERT INTO EXAM_VERSION_QUESTIONS"));
        }
    }

    private static void assertContainsAll(String source, String... expectedFragments) {
        String normalizedSource = normalize(source);
        for (String expectedFragment : expectedFragments) {
            assertTrue(
                    "Missing schema fragment: " + expectedFragment,
                    normalizedSource.contains(normalize(expectedFragment))
            );
        }
    }

    private static void assertAppearsInOrder(String source, String... expectedFragments) {
        int previousIndex = -1;
        for (String expectedFragment : expectedFragments) {
            int currentIndex = source.indexOf(expectedFragment, previousIndex + 1);
            assertTrue(
                    "Missing or out-of-order fragment: " + expectedFragment,
                    currentIndex > previousIndex
            );
            previousIndex = currentIndex;
        }
    }

    private static void assertTableUsesInnoDb(String source, String tableName) {
        String normalizedSource = normalize(source);
        String tablePrefix = "CREATE TABLE IF NOT EXISTS " + tableName.toUpperCase(Locale.ROOT);
        int tableStart = normalizedSource.indexOf(tablePrefix);
        if (tableStart < 0) {
            tablePrefix = "CREATE TABLE " + tableName.toUpperCase(Locale.ROOT);
            tableStart = normalizedSource.indexOf(tablePrefix);
        }
        assertTrue("Missing table definition: " + tableName, tableStart >= 0);

        int nextTable = normalizedSource.indexOf("CREATE TABLE ", tableStart + tablePrefix.length());
        int tableEnd = nextTable < 0 ? normalizedSource.length() : nextTable;
        String definition = normalizedSource.substring(tableStart, tableEnd);
        assertTrue(
                "Table does not explicitly use InnoDB: " + tableName,
                definition.contains(") ENGINE=INNODB")
        );
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
