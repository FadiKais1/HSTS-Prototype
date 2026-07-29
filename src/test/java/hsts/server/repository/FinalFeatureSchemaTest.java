package hsts.server.repository;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FinalFeatureSchemaTest {
    @Test
    public void freshSchemaContainsNonDestructiveInnoDbExtensionAndNotificationTables()
            throws Exception {
        String sql = normalized(Files.readString(Path.of("database/init.sql")));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS EXECUTION_TIME_EXTENSIONS"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS NOTIFICATIONS"));
        assertTrue(sql.contains("CUMULATIVE_EXTENSION_MINUTES INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("CHECK (ADDED_MINUTES > 0)"));
        assertTrue(sql.contains("CHECK (READ_AT IS NULL OR READ_AT >= CREATED_AT)"));
        assertTrue(sql.contains("UNIQUE (DEDUPLICATION_KEY)"));
        assertTrue(section(sql, "CREATE TABLE IF NOT EXISTS EXECUTION_TIME_EXTENSIONS",
                "CREATE TABLE IF NOT EXISTS NOTIFICATIONS").contains("ENGINE=INNODB"));
        assertTrue(section(sql, "CREATE TABLE IF NOT EXISTS NOTIFICATIONS",
                "CREATE TABLE IF NOT EXISTS EXAM_EXECUTION_DECILES")
                .contains("ENGINE=INNODB"));
        assertFalse(sql.contains("INSERT INTO NOTIFICATIONS"));
        assertFalse(sql.contains("INSERT INTO EXECUTION_TIME_EXTENSIONS"));
    }

    @Test
    public void runtimeInitializerMatchesTypesConstraintsAndAdditiveMigration()
            throws Exception {
        String source = normalized(Files.readString(Path.of(
                "src/main/java/hsts/server/repository/DatabaseInitializer.java")));

        for (String required : new String[]{
                "CREATE TABLE EXECUTION_TIME_EXTENSIONS",
                "CREATE TABLE NOTIFICATIONS",
                "DATETIME(6) NOT NULL",
                "EXAM_APPROVED", "EXAM_REJECTED", "EXAM_SCHEDULED",
                "EXECUTION_EXTENDED",
                "GRADE_PUBLISHED", "CHK_NOTIFICATIONS_READ_TIME",
                "IDX_NOTIFICATIONS_RECIPIENT_CREATED",
                "IDX_NOTIFICATIONS_RECIPIENT_READ"
        }) assertTrue("Missing runtime schema token: " + required,
                source.contains(required));
        assertTrue(source.contains("ADDCOLUMNIFMISSING(CONNECTION, \"EXAM_EXECUTIONS\""));
        assertTrue(source.contains("\"CUMULATIVE_EXTENSION_MINUTES\", \"INT NOT NULL DEFAULT 0\""));
        assertFalse(source.contains("DROP TABLE NOTIFICATIONS"));
        assertFalse(source.contains("TRUNCATE NOTIFICATIONS"));
    }

    private static String section(String source, String start, String end) {
        return source.substring(source.indexOf(start), source.indexOf(end));
    }

    private static String normalized(String value) {
        return value.replaceAll("\\s+", " ").trim().toUpperCase();
    }
}
