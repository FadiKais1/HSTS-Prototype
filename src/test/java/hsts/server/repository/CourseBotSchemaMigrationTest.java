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

public class CourseBotSchemaMigrationTest {
    // Source-level guardrails do not replace a live MySQL migration and rerun test.
    private static final String INITIALIZER = read(
            "src", "main", "java", "hsts", "server", "repository",
            "DatabaseInitializer.java"
    );
    private static final String INIT_SQL = read("database", "init.sql");
    private static final List<String> TABLES = List.of(
            "course_bots", "bot_sources", "bot_conversations", "bot_messages"
    );

    @Test
    public void bothPathsCreateBotTablesAfterDependenciesInExactOrder() {
        assertAppearsInOrder(INIT_SQL,
                "CREATE TABLE IF NOT EXISTS users",
                "CREATE TABLE IF NOT EXISTS courses",
                "CREATE TABLE IF NOT EXISTS question_versions",
                "CREATE TABLE IF NOT EXISTS course_bots",
                "CREATE TABLE IF NOT EXISTS bot_sources",
                "CREATE TABLE IF NOT EXISTS bot_conversations",
                "CREATE TABLE IF NOT EXISTS bot_messages",
                "START TRANSACTION"
        );
        assertAppearsInOrder(INITIALIZER,
                "createQuestionVersionsTable();",
                "createAnswerOptionsTable();",
                "migrateQuestionBankData();",
                "createQuestionBankIndexesAndConstraints();",
                "migrateCourseBotSchema();",
                "migrateExamSchema();"
        );
        assertAppearsInOrder(INITIALIZER,
                "createCourseBotsTable(connection);",
                "createBotSourcesTable(connection);",
                "createBotConversationsTable(connection);",
                "createBotMessagesTable(connection);"
        );

        for (String table : TABLES) {
            assertContainsAll(INIT_SQL, "CREATE TABLE IF NOT EXISTS " + table);
            assertContainsAll(INITIALIZER, "CREATE TABLE IF NOT EXISTS " + table);
            assertContainsAll(tableDefinition(INIT_SQL, table), ") ENGINE=InnoDB");
            assertContainsAll(tableDefinition(INITIALIZER, table), ") ENGINE=InnoDB");
        }
    }

    @Test
    public void freshAndRuntimeTableDefinitionsHaveExactSemanticParity() {
        for (String table : TABLES) {
            assertEquals(
                    "Fresh/runtime drift for " + table,
                    normalize(tableDefinition(INIT_SQL, table)),
                    normalize(tableDefinition(INITIALIZER, table))
            );
        }
    }

    @Test
    public void courseBotsEnforceOneBotPerCourseAndRestrictParentDeletion() {
        for (String source : botSchemaSections()) {
            String table = tableDefinition(source, "course_bots");
            assertContainsAll(table,
                    "bot_id INT NOT NULL AUTO_INCREMENT",
                    "course_id INT NOT NULL",
                    "name VARCHAR(100) NOT NULL",
                    "status VARCHAR(20) NOT NULL",
                    "created_by_user_id INT NOT NULL",
                    "external_provider VARCHAR(100) NULL",
                    "external_bot_id VARCHAR(255) NULL",
                    "created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
                    "updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
                    "ON UPDATE CURRENT_TIMESTAMP(6)",
                    "PRIMARY KEY (bot_id)",
                    "UNIQUE (course_id)",
                    "KEY idx_course_bots_creator (created_by_user_id)",
                    "FOREIGN KEY (course_id) REFERENCES courses (course_id) ON DELETE RESTRICT",
                    "FOREIGN KEY (created_by_user_id) REFERENCES users (user_id) ON DELETE RESTRICT",
                    "CHECK (CHAR_LENGTH(TRIM(name)) > 0)",
                    "CHECK (status IN ('ACTIVE', 'INACTIVE'))"
            );
        }
    }

    @Test
    public void sourcesUseExactVersionReferencesAndGeneratedActiveChecksumUniqueness() {
        for (String source : botSchemaSections()) {
            String table = tableDefinition(source, "bot_sources");
            assertContainsAll(table,
                    "extracted_text MEDIUMTEXT NOT NULL",
                    "content_sha256 CHAR(64) NOT NULL",
                    "question_id INT NULL",
                    "question_version_no INT NULL",
                    "external_source_id VARCHAR(255) NULL",
                    "active_content_sha256 CHAR(64)",
                    "GENERATED ALWAYS AS",
                    "CASE WHEN status = 'ACTIVE' THEN content_sha256 ELSE NULL END",
                    "STORED",
                    "UNIQUE (bot_id, active_content_sha256)",
                    "KEY idx_bot_sources_bot_status (bot_id, status)",
                    "KEY idx_bot_sources_added_by_user (added_by_user_id)",
                    "KEY idx_bot_sources_question_version (question_id, question_version_no)",
                    "FOREIGN KEY (question_id, question_version_no)",
                    "REFERENCES question_versions (question_id, version_no)",
                    "CHECK (source_type IN ('QUESTION_BANK', 'FREE_TEXT', 'TXT', 'PDF', 'DOCX'))",
                    "CHECK (status IN ('ACTIVE', 'REMOVED'))",
                    "CHECK (content_sha256 REGEXP '^[0-9a-f]{64}$')",
                    "source_type = 'QUESTION_BANK'",
                    "question_id IS NOT NULL",
                    "question_version_no IS NOT NULL",
                    "source_type <> 'QUESTION_BANK'",
                    "question_id IS NULL",
                    "question_version_no IS NULL",
                    "status = 'ACTIVE' AND removed_at IS NULL",
                    "status = 'REMOVED' AND removed_at IS NOT NULL",
                    "removed_at IS NULL OR removed_at >= created_at",
                    "CHECK (CHAR_LENGTH(TRIM(display_name)) > 0)",
                    "CHECK (CHAR_LENGTH(TRIM(extracted_text)) > 0)"
            );
            assertFalse(normalize(table).contains(
                    "REFERENCES QUESTION_VERSIONS (QUESTION_ID, VERSION_NO) ON DELETE"));
            assertFalse(normalize(table).contains("INSERT IGNORE"));
        }
    }

    @Test
    public void conversationsKeepOnlyRequiredStudentOwnershipAndUuidSubject() {
        for (String source : botSchemaSections()) {
            String table = tableDefinition(source, "bot_conversations");
            assertContainsAll(table,
                    "conversation_id INT NOT NULL AUTO_INCREMENT",
                    "bot_id INT NOT NULL",
                    "student_user_id INT NOT NULL",
                    "provider_subject_id CHAR(36) NOT NULL",
                    "created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
                    "updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
                    "UNIQUE (bot_id, student_user_id)",
                    "UNIQUE (provider_subject_id)",
                    "KEY idx_bot_conversations_student (student_user_id)",
                    "KEY idx_bot_conversations_bot_updated (bot_id, updated_at)",
                    "FOREIGN KEY (bot_id) REFERENCES course_bots (bot_id) ON DELETE RESTRICT",
                    "FOREIGN KEY (student_user_id) REFERENCES users (user_id) ON DELETE RESTRICT",
                    "provider_subject_id REGEXP",
                    "^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$"
            );
        }
    }

    @Test
    public void messagesEnforceOrderingStatusesAndAnonymousContentShape() {
        for (String source : botSchemaSections()) {
            String table = tableDefinition(source, "bot_messages");
            assertContainsAll(table,
                    "message_id INT NOT NULL AUTO_INCREMENT",
                    "conversation_id INT NOT NULL",
                    "sequence_no INT NOT NULL",
                    "question_text TEXT NOT NULL",
                    "normalized_question VARCHAR(1000) NOT NULL",
                    "answer_text MEDIUMTEXT NOT NULL",
                    "answer_status VARCHAR(30) NOT NULL",
                    "provider_request_id VARCHAR(255) NULL",
                    "created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
                    "UNIQUE (conversation_id, sequence_no)",
                    "KEY idx_bot_messages_conversation_created (conversation_id, created_at)",
                    "KEY idx_bot_messages_normalized_question (normalized_question(191))",
                    "FOREIGN KEY (conversation_id)",
                    "REFERENCES bot_conversations (conversation_id) ON DELETE RESTRICT",
                    "CHECK (sequence_no > 0)",
                    "CHECK (answer_status IN ('ANSWERED', 'NO_SUITABLE_ANSWER'))",
                    "CHECK (CHAR_LENGTH(TRIM(question_text)) > 0)",
                    "CHECK (CHAR_LENGTH(TRIM(normalized_question)) > 0)",
                    "answer_status = 'NO_SUITABLE_ANSWER'",
                    "OR CHAR_LENGTH(TRIM(answer_text)) > 0"
            );
        }
    }

    @Test
    public void runtimeMigrationIsCreateOnlyIdempotentAndPreservesSqlCause() {
        String runtime = between(
                INITIALIZER,
                "private void migrateCourseBotSchema()",
                "private void executeSchemaStatement(String sql"
        );
        for (String table : TABLES) {
            assertContainsAll(runtime, "CREATE TABLE IF NOT EXISTS " + table);
        }
        assertContainsAll(runtime,
                "catch (SQLException | RuntimeException e)",
                "throw new IllegalStateException(\"Failed to migrate course Bot schema\", e)"
        );

        String normalized = normalize(runtime);
        assertFalse(normalized.contains("SETAUTOCOMMIT"));
        assertFalse(normalized.contains("START TRANSACTION"));
        for (String table : TABLES) {
            String normalizedTable = table.toUpperCase(Locale.ROOT);
            assertFalse(normalized.contains("INSERT INTO " + normalizedTable));
            assertFalse(normalized.contains("UPDATE " + normalizedTable));
        }
        assertFalse(normalized.contains("DELETE FROM"));
        assertFalse(normalized.contains("DROP TABLE"));
        assertFalse(normalized.contains("TRUNCATE"));
        assertFalse(normalized.contains("REPLACE INTO"));
        assertFalse(normalized.contains("INSERT IGNORE"));
        assertFalse(normalized.contains("ON DUPLICATE KEY UPDATE"));
    }

    @Test
    public void botSchemaContainsNoSeedsSecretsRawFilesOrStudentDetails() {
        for (String source : botSchemaSections()) {
            String normalized = normalize(source);
            for (String table : TABLES) {
                assertFalse("Bot seed DML found for " + table,
                        normalized.contains("INSERT INTO " + table.toUpperCase(Locale.ROOT)));
            }
            for (String forbidden : List.of(
                    "API_KEY", "PROVIDER_SECRET", "PASSWORD", "IDENTITY_HASH",
                    "STUDENT_NAME", "STUDENT_EMAIL", "CORRECT_OPTION", "SCORE",
                    "FILE_PATH", "LOCAL_PATH", "RAW_BYTES", " BLOB"
            )) {
                assertFalse("Forbidden Bot schema fragment: " + forbidden,
                        normalized.contains(forbidden));
            }
        }

        for (String source : botSchemaSections()) {
            assertFalse(normalize(tableDefinition(source, "course_bots"))
                    .contains("STUDENT_USER_ID"));
            assertFalse(normalize(tableDefinition(source, "bot_sources"))
                    .contains("STUDENT_USER_ID"));
            assertTrue(normalize(tableDefinition(source, "bot_conversations"))
                    .contains("STUDENT_USER_ID INT NOT NULL"));
            assertFalse(normalize(tableDefinition(source, "bot_messages"))
                    .contains("STUDENT_USER_ID"));
        }
    }

    private static List<String> botSchemaSections() {
        return List.of(botSchemaSection(INIT_SQL), botSchemaSection(INITIALIZER));
    }

    private static String botSchemaSection(String source) {
        if (source == INIT_SQL) {
            return between(source,
                    "CREATE TABLE IF NOT EXISTS course_bots",
                    "CREATE TABLE IF NOT EXISTS subject_coordinators");
        }
        return between(source,
                "private void migrateCourseBotSchema()",
                "private void executeSchemaStatement(String sql");
    }

    private static String tableDefinition(String source, String tableName) {
        String marker = "CREATE TABLE IF NOT EXISTS " + tableName;
        int start = source.indexOf(marker);
        assertTrue("Missing table definition: " + tableName, start >= 0);
        int end = source.indexOf(") ENGINE=InnoDB", start);
        assertTrue("Incomplete table definition: " + tableName, end > start);
        return source.substring(start, end + ") ENGINE=InnoDB".length());
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue("Missing start marker: " + startMarker, start >= 0);
        assertTrue("Missing end marker: " + endMarker, end > start);
        return source.substring(start, end);
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
        String normalizedSource = normalize(source);
        for (String fragment : fragments) {
            assertTrue("Missing fragment: " + fragment,
                    normalizedSource.contains(normalize(fragment)));
        }
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private static String read(String... pathParts) {
        try {
            return Files.readString(Path.of("", pathParts), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Course Bot schema source", e);
        }
    }
}
