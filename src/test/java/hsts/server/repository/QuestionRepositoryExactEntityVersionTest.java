package hsts.server.repository;

import hsts.server.entity.Question;
import org.junit.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;

import static hsts.server.repository.BotRepositoryJdbcTestSupport.failure;
import static hsts.server.repository.BotRepositoryJdbcTestSupport.query;
import static hsts.server.repository.BotRepositoryJdbcTestSupport.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class QuestionRepositoryExactEntityVersionTest {
    private static final LocalDateTime VERSION_CREATED =
            LocalDateTime.of(2026, 6, 20, 12, 0);

    @Test
    public void exactHistoricalVersionUsesAssignmentAndSharedEntityMapper() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("qv.version_no = ?",
                                optionRow(1, "Old A"), optionRow(2, "Old B"),
                                optionRow(3, "Old C"), optionRow(4, "Old D"))
                );

        Question question = new QuestionRepository(database)
                .findEntityVersionForTeacher(1002, 11, 2).orElseThrow();

        assertEquals(11, question.getQuestionId());
        assertEquals("Historical content", question.getContent());
        assertEquals("HARD", question.getDifficulty());
        assertEquals(4, question.getAnswerOptions().size());
        assertEquals("Old D", question.getAnswerOption4());
        assertEquals(3, question.getCorrectOptionNumber());
        assertEquals(VERSION_CREATED, question.getCreatedAt());
        assertEquals(VERSION_CREATED, question.getUpdatedAt());
        assertEquals(1002, database.executed.get(0).parameters.get(1));
        assertEquals(2, database.executed.get(0).parameters.get(2));
        assertEquals(11, database.executed.get(0).parameters.get(3));
        assertTrue(database.executed.get(0).actualSql.contains("JOIN teacher_courses"));
        assertFalse(database.executed.get(0).actualSql.contains("current_version_no"));
        database.assertConsumed();
    }

    @Test
    public void missingOrUnauthorizedVersionReturnsEmpty() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("qv.version_no = ?")
                );
        assertTrue(new QuestionRepository(database)
                .findEntityVersionForTeacher(1002, 11, 9).isEmpty());
    }

    @Test
    public void malformedIncompleteSnapshotIsRejected() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("qv.version_no = ?", optionRow(1, "Only"))
                );
        assertThrows(IllegalArgumentException.class,
                () -> new QuestionRepository(database)
                        .findEntityVersionForTeacher(1002, 11, 2));
    }

    @Test
    public void jdbcCauseIsPreserved() {
        SQLException cause = new SQLException("read failed");
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        failure("qv.version_no = ?", cause)
                );
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new QuestionRepository(database)
                        .findEntityVersionForTeacher(1002, 11, 2));
        assertSame(cause, thrown.getCause());
    }

    private static Map<String, Object> optionRow(int option, String text) {
        return row(
                "question_id", 11, "status", "ACTIVE",
                "created_at", VERSION_CREATED, "updated_at", VERSION_CREATED,
                "version_no", 2, "content", "Historical content",
                "topic", "Calculus", "question_type", "MULTIPLE_CHOICE",
                "difficulty", "HARD", "illustration_path", "old.png",
                "correct_option_number", 3, "option_number", option,
                "option_text", text
        );
    }
}
