package hsts.server.repository;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class QuestionIllustrationSchemaTest {
    private static final String SQL = read("database/init.sql");
    private static final String INITIALIZER = read(
            "src/main/java/hsts/server/repository/DatabaseInitializer.java"
    );

    @Test
    public void freshAndRuntimeSchemasContainImmutableExactVersionTable() {
        for (String source : new String[]{SQL, INITIALIZER}) {
            assertContains(source,
                    "question_version_illustrations",
                    "PRIMARY KEY (question_id, version_no)",
                    "FOREIGN KEY (question_id, version_no)",
                    "REFERENCES question_versions (question_id, version_no)",
                    "media_type VARCHAR(30) NOT NULL",
                    "content_bytes MEDIUMBLOB NOT NULL",
                    "content_sha256 CHAR(64) NOT NULL",
                    "ENGINE=InnoDB");
        }
    }

    @Test
    public void schemaBoundsTypesAndChecksumWithoutSeedingOrCascadeDelete() {
        for (String source : new String[]{SQL, INITIALIZER}) {
            String normalized = source.replaceAll("\\s+", " ");
            assertContains(normalized,
                    "media_type IN ('image/png', 'image/jpeg')",
                    "byte_length BETWEEN 1 AND 2097152",
                    "width_pixels BETWEEN 1 AND 4096",
                    "height_pixels BETWEEN 1 AND 4096",
                    "content_sha256 REGEXP '^[0-9a-f]{64}$'",
                    "ON DELETE RESTRICT");
            assertFalse(normalized.contains(
                    "INSERT INTO question_version_illustrations"
            ));
        }
    }

    @Test
    public void runtimeCreationPrecedesQuestionDataMigration() {
        assertTrue(INITIALIZER.indexOf("createQuestionVersionIllustrationsTable();")
                < INITIALIZER.indexOf("migrateQuestionBankData();"));
        assertTrue(SQL.indexOf("CREATE TABLE IF NOT EXISTS question_version_illustrations")
                < SQL.indexOf("START TRANSACTION"));
    }

    private static void assertContains(String source, String... fragments) {
        for (String fragment : fragments) {
            assertTrue("Missing: " + fragment, source.contains(fragment));
        }
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
