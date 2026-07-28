package hsts.server.repository;

import hsts.common.BotUsageSummaryDTO;
import hsts.common.type.BotSourceType;
import hsts.common.type.BotSourceStatus;
import hsts.common.type.BotStatus;
import hsts.server.entity.BotSource;
import hsts.server.entity.CourseBot;
import org.junit.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static hsts.server.repository.BotRepositoryJdbcTestSupport.failure;
import static hsts.server.repository.BotRepositoryJdbcTestSupport.query;
import static hsts.server.repository.BotRepositoryJdbcTestSupport.row;
import static hsts.server.repository.BotRepositoryJdbcTestSupport.update;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CourseBotRepositoryTest {
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 7, 1, 10, 0);
    private static final LocalDateTime UPDATED = CREATED.plusHours(1);
    private static final String CHECKSUM = "a".repeat(64);

    @Test
    public void teacherReadUsesAssignmentAndHydratesOrderedDeepImmutableSources() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("JOIN teacher_courses tc", botRow()),
                        query("FROM bot_sources bs", sourceRow(9, "ACTIVE", null))
                );

        List<CourseBot> bots = new CourseBotRepository(database).findAssignedToTeacher(1002);

        assertEquals(1, bots.size());
        CourseBot bot = bots.get(0);
        assertEquals(7, bot.getBotId());
        assertEquals(1, bot.getCourseId());
        assertEquals("Legacy Bot", bot.getName());
        assertEquals(1, bot.getSources().size());
        assertEquals(9, bot.getSources().get(0).getSourceId());
        assertEquals(1002, database.executed.get(0).parameters.get(1));
        assertThrows(UnsupportedOperationException.class, () -> bots.add(bot));
        assertThrows(UnsupportedOperationException.class,
                () -> bot.getSources().add(bot.getSources().get(0)));
        assertTrue(database.executed.get(0).actualSql.contains("ORDER BY c.name ASC"));
        database.assertConsumed();
    }

    @Test
    public void studentReadRequiresActiveStudentEnrollmentAndLoadsOnlyActiveSources() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("u.role = 'STUDENT'", botRow()),
                        query("bs.status = 'ACTIVE'", sourceRow(9, "ACTIVE", null))
                );

        CourseBot bot = new CourseBotRepository(database)
                .findActiveByCourseForStudent(1001, 1).orElseThrow();

        assertEquals(1001, database.executed.get(0).parameters.get(1));
        assertEquals(1, database.executed.get(0).parameters.get(2));
        assertTrue(database.executed.get(0).actualSql.contains("cb.status = 'ACTIVE'"));
        assertTrue(database.executed.get(0).actualSql.contains("u.status = 'ACTIVE'"));
        assertEquals(1, bot.getSources().size());
        database.assertConsumed();
    }

    @Test
    public void missingOrUnassignedReadsReturnEmptyWithoutSourceQuery() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("WHERE cb.course_id = ?")
                );
        assertTrue(new CourseBotRepository(database)
                .findByCourseForTeacher(1004, 1).isEmpty());
        database.assertConsumed();
    }

    @Test
    public void creationUsesAuthenticatedCreatorAndOneTransaction() {
        CourseBot supplied = CourseBot.create(1, "  Legacy Bot  ", 1002,
                "provider", "remote-7", CREATED);
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("FROM courses c", row("course_id", 1)),
                        query("FROM course_bots cb"),
                        update("INSERT INTO course_bots", 1, 7),
                        query("WHERE cb.bot_id = ?", botRow()),
                        query("FROM bot_sources bs")
                );

        CourseBot created = new CourseBotRepository(database).create(1002, supplied);

        assertEquals(7, created.getBotId());
        assertEquals(0, supplied.getBotId());
        assertEquals(1002, database.executed.get(2).parameters.get(4));
        assertEquals("Legacy Bot", database.executed.get(2).parameters.get(2));
        assertEquals(1, database.commits);
        assertEquals(0, database.rollbacks);
        assertTrue(database.autoCommit);
        database.assertConsumed();
    }

    @Test
    public void creatorMismatchIsRejectedBeforeConnecting() {
        CourseBot bot = CourseBot.create(1, "Bot", 1003, null, null, CREATED);
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController();
        assertThrows(IllegalArgumentException.class,
                () -> new CourseBotRepository(database).create(1002, bot));
        assertTrue(database.executed.isEmpty());
    }

    @Test
    public void configurationIsCreatorOnlyAndUsesOptimisticTimestamp() {
        CourseBot changed = CourseBot.rehydrate(
                7, 1, "Legacy Bot", BotStatus.ACTIVE, 1002,
                "provider", "remote-7", CREATED, UPDATED, List.of()
        );
        LocalDateTime changedAt = UPDATED.plusMinutes(5);
        changed.rename("Renamed Bot", changedAt);
        Map<String, Object> reread = botRow();
        reread.put("name", "Renamed Bot");
        reread.put("updated_at", changedAt);
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("WHERE cb.bot_id = ?", botRow()),
                        update("UPDATE course_bots", 1, 0),
                        query("WHERE cb.bot_id = ?", reread),
                        query("FROM bot_sources bs")
                );

        CourseBot persisted = new CourseBotRepository(database)
                .persistConfiguration(1002, changed);

        assertEquals("Renamed Bot", persisted.getName());
        assertEquals(changedAt, database.executed.get(1).parameters.get(3));
        assertEquals(UPDATED, database.executed.get(1).parameters.get(7));
        assertEquals(1, database.commits);
        database.assertConsumed();
    }

    @Test
    public void otherAssignedTeacherCannotConfigureSharedBot() {
        CourseBot changed = CourseBot.rehydrate(
                7, 1, "Legacy Bot", BotStatus.INACTIVE, 1002,
                "provider", "remote-7", CREATED, UPDATED.plusMinutes(1), List.of()
        );
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("WHERE cb.bot_id = ?", botRow())
                );
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new CourseBotRepository(database)
                        .persistConfiguration(1003, changed));
        assertEquals("Course Bot not found or access denied", thrown.getMessage());
        assertEquals(1, database.rollbacks);
        database.assertConsumed();
    }

    @Test
    public void courseUniquenessCollisionHasStableMessageAndPreservesCause() {
        SQLException duplicate = new SQLException(
                "Duplicate entry for key 'uq_course_bots_course'", "23000", 1062
        );
        CourseBot bot = CourseBot.create(1, "Bot", 1002, null, null, CREATED);
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("FROM courses c", row("course_id", 1)),
                        query("FROM course_bots cb"),
                        failure("INSERT INTO course_bots", duplicate)
                );

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new CourseBotRepository(database).create(1002, bot));
        assertEquals("Course Bot already exists", thrown.getMessage());
        assertSame(duplicate, thrown.getCause());
        assertEquals(1, database.rollbacks);
        assertTrue(database.autoCommit);
    }

    @Test
    public void questionSourceChecksExactVersionAndSameCourseWithoutCurrentVersion() {
        BotSource source = BotSource.create(
                7, BotSourceType.QUESTION_BANK, "Question 11 v2", "Question text",
                CHECKSUM, 11, 2, 1003, null, UPDATED
        );
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("WHERE cb.bot_id = ?", botRow()),
                        query("qv.version_no = ?", row("question_id", 11)),
                        update("INSERT INTO bot_sources", 1, 9),
                        query("WHERE bs.source_id = ?", sourceRow(9, "ACTIVE", null))
                );

        BotSource created = new CourseBotRepository(database).addSource(1003, source);

        assertEquals(9, created.getSourceId());
        assertEquals(2, database.executed.get(1).parameters.get(1));
        assertEquals(11, database.executed.get(1).parameters.get(2));
        assertEquals(1, database.executed.get(1).parameters.get(3));
        assertFalse(database.executed.get(1).actualSql.contains("current_version_no"));
        assertEquals(1, database.commits);
        database.assertConsumed();
    }

    @Test
    public void namedActiveChecksumCollisionMapsWithoutIgnoringFailure() {
        BotSource source = BotSource.create(
                7, BotSourceType.FREE_TEXT, "Notes", "Course notes", CHECKSUM,
                null, null, 1003, null, UPDATED
        );
        SQLException duplicate = new SQLException(
                "Duplicate entry for key 'uq_bot_sources_active_checksum'", "23000", 1062
        );
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("WHERE cb.bot_id = ?", botRow()),
                        failure("INSERT INTO bot_sources", duplicate)
                );

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new CourseBotRepository(database).addSource(1003, source));

        assertEquals("Bot source already exists", thrown.getMessage());
        assertSame(duplicate, thrown.getCause());
        assertEquals(1, database.rollbacks);
        assertFalse(database.executed.get(1).actualSql.contains("INSERT IGNORE"));
        database.assertConsumed();
    }

    @Test
    public void anyAssignedTeacherCanSoftRemoveSourceWithoutDeletingIt() {
        LocalDateTime removedAt = UPDATED.plusMinutes(10);
        BotSource transitioned = BotSource.rehydrate(
                9, 7, BotSourceType.QUESTION_BANK, "Question 11 v2", "Question text",
                CHECKSUM, 11, 2, 1002, BotSourceStatus.ACTIVE,
                null, UPDATED, null
        );
        transitioned.remove(removedAt);
        Map<String, Object> activeRow = sourceRow(9, "ACTIVE", null);
        activeRow.put("added_by_user_id", 1002);
        Map<String, Object> removedRow = sourceRow(9, "REMOVED", removedAt);
        removedRow.put("added_by_user_id", 1002);
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("WHERE bs.source_id = ?", activeRow),
                        update("UPDATE bot_sources", 1, 0),
                        query("WHERE bs.source_id = ?", removedRow)
                );

        BotSource removed = new CourseBotRepository(database)
                .persistSourceRemoval(1003, transitioned);

        assertEquals(BotSourceStatus.REMOVED, removed.getStatus());
        assertEquals(removedAt, removed.getRemovedAt());
        assertTrue(database.executed.get(1).actualSql.contains("status = 'ACTIVE'"));
        assertFalse(database.executed.get(1).actualSql.toUpperCase().contains("DELETE"));
        assertEquals(1, database.commits);
        database.assertConsumed();
    }

    @Test
    public void anonymousUsageContainsOnlyAggregateDataAndDeterministicQuestions() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("COUNT(bm.message_id)", row(
                                "bot_id", 7, "course_id", 1,
                                "bot_name", "Legacy Bot", "course_name", "Legacy Course",
                                "total_questions", 4, "last_activity_at", UPDATED
                        )),
                        query("GROUP BY bm.normalized_question",
                                row("normalized_question", "limits", "occurrence_count", 3),
                                row("normalized_question", "vectors", "occurrence_count", 1))
                );

        BotUsageSummaryDTO summary = new CourseBotRepository(database)
                .findAnonymousUsageForTeacher(1002, 7, 10);

        assertEquals(4, summary.getTotalQuestions());
        assertEquals(UPDATED, summary.getLastActivityAt());
        assertEquals(List.of("limits", "vectors"), summary.getCommonQuestions().stream()
                .map(value -> value.getQuestionText()).toList());
        String combinedSql = database.executed.stream()
                .map(step -> step.actualSql).reduce("", String::concat);
        assertFalse(combinedSql.contains("student_user_id"));
        assertFalse(combinedSql.contains("provider_subject_id"));
        assertFalse(combinedSql.contains("conversation_id AS"));
        assertEquals(10, database.executed.get(1).parameters.get(3));
        database.assertConsumed();
    }

    @Test
    public void zeroUsageSkipsCommonQuestionQuery() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("COUNT(bm.message_id)", row(
                                "bot_id", 7, "course_id", 1,
                                "bot_name", "Legacy Bot", "course_name", "Legacy Course",
                                "total_questions", 0, "last_activity_at", null
                        ))
                );
        BotUsageSummaryDTO summary = new CourseBotRepository(database)
                .findAnonymousUsageForTeacher(1002, 7, 5);
        assertEquals(0, summary.getTotalQuestions());
        assertNull(summary.getLastActivityAt());
        assertTrue(summary.getCommonQuestions().isEmpty());
        database.assertConsumed();
    }

    @Test
    public void jdbcCauseIsPreserved() {
        SQLException cause = new SQLException("database unavailable");
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        failure("JOIN teacher_courses tc", cause)
                );
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new CourseBotRepository(database).findAssignedToTeacher(1002));
        assertSame(cause, thrown.getCause());
        assertFalse(thrown.getMessage().contains("database unavailable"));
    }

    private static Map<String, Object> botRow() {
        return row(
                "bot_id", 7, "course_id", 1, "name", "Legacy Bot",
                "status", "ACTIVE", "created_by_user_id", 1002,
                "external_provider", "provider", "external_bot_id", "remote-7",
                "created_at", CREATED, "updated_at", UPDATED
        );
    }

    private static Map<String, Object> sourceRow(
            int sourceId, String status, LocalDateTime removedAt
    ) {
        return row(
                "source_id", sourceId, "bot_id", 7, "source_type", "QUESTION_BANK",
                "display_name", "Question 11 v2", "extracted_text", "Question text",
                "content_sha256", CHECKSUM, "question_id", 11,
                "question_version_no", 2, "added_by_user_id", 1003,
                "status", status, "external_source_id", null,
                "created_at", UPDATED, "removed_at", removedAt
        );
    }
}
