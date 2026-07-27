package hsts.server.repository;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DatabaseInitializerSchemaTest {
    // Source-level checks are guardrails and do not replace live MySQL migration tests.
    private static final String INITIALIZER = read(
            "src", "main", "java", "hsts", "server", "repository", "DatabaseInitializer.java"
    );
    private static final String INIT_SQL = read("database", "init.sql");

    @Test
    public void freshSchemaContainsEveryRequiredQuestionBankObjectAndConstraint() {
        assertContainsAll(INIT_SQL,
                "CREATE TABLE IF NOT EXISTS subjects",
                "subject_id INT AUTO_INCREMENT PRIMARY KEY",
                "subject_code VARCHAR(30) NOT NULL UNIQUE",
                "CREATE TABLE IF NOT EXISTS courses",
                "UNIQUE (subject_id, course_code, school_year)",
                "CREATE TABLE IF NOT EXISTS teacher_courses",
                "PRIMARY KEY (teacher_user_id, course_id)",
                "question_id INT AUTO_INCREMENT PRIMARY KEY",
                "answer_option_1 TEXT",
                "answer_option_2 TEXT",
                "answer_option_3 TEXT",
                "answer_option_4 TEXT",
                "correct_option_number INT",
                "course_id INT NULL",
                "created_by_user_id INT NULL",
                "current_version_no INT NOT NULL DEFAULT 1",
                "created_at DATETIME(6) NULL",
                "updated_at DATETIME(6) NULL",
                "CREATE TABLE IF NOT EXISTS question_versions",
                "PRIMARY KEY (question_id, version_no)",
                "CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD'))",
                "CHECK (correct_option_number BETWEEN 1 AND 4)",
                "CREATE TABLE IF NOT EXISTS answer_options",
                "PRIMARY KEY (question_id, version_no, option_number)",
                "FOREIGN KEY (question_id, version_no)",
                "CHECK (option_number BETWEEN 1 AND 4)",
                ") ENGINE=InnoDB"
        );

        assertContainsAll(INITIALIZER,
                "createSubjectsTable()",
                "createCoursesTable()",
                "createTeacherCoursesTable()",
                "migrateQuestionBankColumns()",
                "ALTER TABLE questions MODIFY COLUMN question_id INT NOT NULL AUTO_INCREMENT",
                "createQuestionVersionsTable()",
                "createAnswerOptionsTable()",
                "fk_courses_subject",
                "fk_teacher_courses_user",
                "fk_teacher_courses_course",
                "fk_questions_course",
                "fk_questions_creator",
                "fk_question_versions_question",
                "fk_question_versions_creator",
                "fk_answer_options_version",
                "chk_question_versions_difficulty",
                "chk_question_versions_correct_option",
                "chk_answer_options_option_number"
        );
    }

    @Test
    public void initializerExecutesLegacyWorkBeforeAdditiveMigration() {
        assertAppearsInOrder(INITIALIZER,
                "createQuestionsTable();",
                "migrateQuestionsTable();",
                "seedQuestionsIfEmpty();",
                "normalizeExistingQuestions();",
                "createUsersTable();",
                "seedUsers();",
                "createSubjectsTable();",
                "createCoursesTable();",
                "createTeacherCoursesTable();",
                "migrateQuestionBankColumns();",
                "createQuestionVersionsTable();",
                "createAnswerOptionsTable();",
                "migrateQuestionBankData();",
                "createQuestionBankIndexesAndConstraints();",
                "migrateExamSchema();"
        );
    }

    @Test
    public void questionSeedsNeverOverwriteDuplicateRows() {
        assertContainsAll(INIT_SQL, "INSERT IGNORE INTO questions");
        assertContainsAll(INITIALIZER, "INSERT IGNORE INTO questions");
        assertFalse(normalize(INIT_SQL).contains("ON DUPLICATE KEY UPDATE"));
        assertFalse(normalize(INITIALIZER).contains("ON DUPLICATE KEY UPDATE"));
    }

    @Test
    public void migrationBackfillsOnlyNullsAndCopiesVersionsAndOptionsWithoutOverwrite() {
        for (String source : new String[]{INITIALIZER, INIT_SQL}) {
            assertContainsAll(source,
                    "WHERE course_id IS NULL",
                    "WHERE created_by_user_id IS NULL",
                    "WHERE current_version_no IS NULL",
                    "WHERE created_at IS NULL",
                    "WHERE updated_at IS NULL",
                    "INSERT INTO question_versions",
                    "FROM questions",
                    "WHERE NOT EXISTS",
                    "q.content",
                    "q.topic",
                    "q.type",
                    "q.difficulty",
                    "q.illustration_path",
                    "q.correct_option_number",
                    "INSERT INTO answer_options",
                    "q.answer_option_1",
                    "q.answer_option_2",
                    "q.answer_option_3",
                    "q.answer_option_4",
                    "UNION ALL SELECT 2",
                    "UNION ALL SELECT 3",
                    "UNION ALL SELECT 4"
            );
        }

        assertContainsAll(INITIALIZER,
                "connection.setAutoCommit(false)",
                "connection.commit()",
                "connection.rollback()",
                "connection.setAutoCommit(originalAutoCommit)",
                "originalException.addSuppressed(restorationException)"
        );
    }

    @Test
    public void standaloneSqlPlacesAllDdlBeforeOneAtomicMigrationTransaction() {
        String normalizedSql = normalize(INIT_SQL);
        int transactionStart = normalizedSql.indexOf("START TRANSACTION;");
        int versionInsert = normalizedSql.indexOf("INSERT INTO QUESTION_VERSIONS", transactionStart);
        int optionInsert = normalizedSql.indexOf("INSERT INTO ANSWER_OPTIONS", versionInsert);
        int commit = normalizedSql.indexOf("COMMIT;", optionInsert);

        assertTrue("Missing START TRANSACTION", transactionStart >= 0);
        assertTrue("DDL appears after START TRANSACTION",
                normalizedSql.lastIndexOf("CREATE ") < transactionStart);
        assertTrue("Version insertion must be inside the transaction",
                versionInsert > transactionStart && versionInsert < commit);
        assertTrue("Option insertion must follow version insertion before COMMIT",
                optionInsert > versionInsert && optionInsert < commit);

        String transactionSql = normalizedSql.substring(transactionStart, commit);
        assertFalse(transactionSql.contains("CREATE TABLE"));
        assertFalse(transactionSql.contains("ALTER TABLE"));
        assertFalse(transactionSql.contains("INSERT IGNORE INTO QUESTION_VERSIONS"));
        assertFalse(transactionSql.contains("INSERT IGNORE INTO ANSWER_OPTIONS"));
    }

    @Test
    public void collisionChecksRunBeforeAssignmentsAndQuestionBackfill() {
        assertAppearsInOrder(INITIALIZER,
                "validateCompatibilityRecords(connection, false);",
                "insertCompatibilitySubjectAndCourse(connection);",
                "validateCompatibilityRecords(connection, true);",
                "insertCompatibilityAssignments(connection);",
                "backfillQuestionMetadata(connection);"
        );

        assertContainsAll(INITIALIZER,
                "WHERE user_id = ? OR email = ?",
                "WHERE subject_id = 1 OR subject_code = 'LEGACY'",
                "WHERE course_id = 1",
                "Compatibility user conflict for fixed identifier",
                "Compatibility subject conflict for fixed identifier 1 or code LEGACY",
                "Compatibility course conflict for fixed identifier 1 or code LEGACY-101"
        );

        assertAppearsInOrder(INIT_SQL,
                "INSERT INTO question_bank_migration_guard",
                "INSERT IGNORE INTO teacher_courses",
                "UPDATE questions SET course_id = 1 WHERE course_id IS NULL"
        );
    }

    @Test
    public void compatibilityRowsAndAssignmentsAreDeterministicAndNonDestructive() {
        for (String source : new String[]{INITIALIZER, INIT_SQL}) {
            assertContainsAll(source,
                    "INSERT IGNORE INTO subjects",
                    "'LEGACY'",
                    "'Legacy Prototype'",
                    "INSERT IGNORE INTO courses",
                    "'LEGACY-101'",
                    "'Legacy Prototype Course'",
                    "'General'",
                    "'2026'",
                    "INSERT IGNORE INTO teacher_courses",
                    "(1002, 1)",
                    "(1003, 1)"
            );

            String normalized = normalize(source);
            assertFalse(normalized.contains("DROP TABLE"));
            assertFalse(normalized.contains("TRUNCATE TABLE"));
            assertFalse(normalized.contains("DELETE FROM"));
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
            assertTrue("Missing or out-of-order fragment: " + expectedFragment, currentIndex > previousIndex);
            previousIndex = currentIndex;
        }
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
