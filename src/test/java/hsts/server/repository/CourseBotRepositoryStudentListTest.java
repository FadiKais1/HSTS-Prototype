package hsts.server.repository;

import hsts.common.type.BotSourceStatus;
import hsts.server.entity.CourseBot;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;

import static hsts.server.repository.BotRepositoryJdbcTestSupport.query;
import static hsts.server.repository.BotRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CourseBotRepositoryStudentListTest {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 7, 1, 10, 0);

    @Test
    public void activeStudentListingRequiresRoleEnrollmentAndLoadsActiveSources() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("JOIN student_courses sc", botRow()),
                        query("bs.status = 'ACTIVE'", sourceRow())
                );

        List<CourseBot> bots = new CourseBotRepository(database).findActiveForStudent(1001);

        assertEquals(1, bots.size());
        assertEquals(1001, database.executed.get(0).parameters.get(1));
        assertTrue(database.executed.get(0).actualSql.contains("u.role = 'STUDENT'"));
        assertTrue(database.executed.get(0).actualSql.contains("u.status = 'ACTIVE'"));
        assertTrue(database.executed.get(0).actualSql.contains("cb.status = 'ACTIVE'"));
        assertTrue(database.executed.get(0).actualSql.contains("ORDER BY c.name ASC"));
        assertEquals(BotSourceStatus.ACTIVE,
                bots.get(0).getSources().get(0).getStatus());
        assertThrows(UnsupportedOperationException.class, () -> bots.clear());
        database.assertConsumed();
    }

    @Test
    public void emptyStudentListingAndJdbcFailureFollowRepositoryConventions() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController empty =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("JOIN student_courses sc")
                );
        assertTrue(new CourseBotRepository(empty).findActiveForStudent(1001).isEmpty());
        empty.assertConsumed();

        BotRepositoryJdbcTestSupport.FakeDatabaseController unused =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController();
        assertThrows(IllegalArgumentException.class,
                () -> new CourseBotRepository(unused).findActiveForStudent(0));
        assertTrue(unused.executed.isEmpty());
    }

    private static java.util.Map<String, Object> botRow() {
        return row(
                "bot_id", 7, "course_id", 1, "name", "Legacy Bot",
                "status", "ACTIVE", "created_by_user_id", 1002,
                "external_provider", null, "external_bot_id", null,
                "created_at", TIME, "updated_at", TIME
        );
    }

    private static java.util.Map<String, Object> sourceRow() {
        return row(
                "source_id", 9, "bot_id", 7, "source_type", "FREE_TEXT",
                "display_name", "Notes", "extracted_text", "Limits",
                "content_sha256", "a".repeat(64), "question_id", null,
                "question_version_no", null, "added_by_user_id", 1002,
                "status", "ACTIVE", "external_source_id", null,
                "created_at", TIME, "removed_at", null
        );
    }
}
