package hsts.server.repository;

import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import hsts.server.entity.AnswerOption;
import hsts.server.entity.Question;
import org.junit.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class QuestionRepositoryEntityWriteTest {
    @Test
    public void entityCreatePersistsCurrentVersionAndFourOptionsInOneTransaction() {
        QuestionRepositoryEntityJdbcTestSupport.Controller database =
                new QuestionRepositoryEntityJdbcTestSupport.Controller(true);
        database.setGeneratedQuestionId(51);
        QuestionRepository repository = new QuestionRepository(database);
        Question question = question(99, QuestionStatus.INACTIVE);

        int questionId = repository.create(1002, 21, question);

        assertEquals(51, questionId);
        assertEquals(99, question.getQuestionId());
        assertEquals(1, database.getConnectionCalls());
        assertTrue(database.isCommitted());
        assertFalse(database.isRolledBack());
        assertTrue(database.isAutoCommit());

        QuestionRepositoryEntityJdbcTestSupport.StatementRecord current =
                database.statement("question");
        QuestionRepositoryEntityJdbcTestSupport.StatementRecord version =
                database.statement("version");
        QuestionRepositoryEntityJdbcTestSupport.StatementRecord options =
                database.statement("option");
        assertEquals(Statement.RETURN_GENERATED_KEYS, current.getGeneratedKeysFlag());
        assertEquals(1, current.getExecutions().size());
        assertEquals(1, version.getExecutions().size());
        assertEquals(4, options.getExecutions().size());

        Map<Integer, Object> currentValues = current.getExecutions().get(0);
        assertEquals("Entity content", currentValues.get(1));
        assertEquals("Geometry", currentValues.get(2));
        assertEquals("MULTIPLE_CHOICE", currentValues.get(3));
        assertEquals("HARD", currentValues.get(4));
        assertEquals("INACTIVE", currentValues.get(5));
        assertEquals("entity.png", currentValues.get(6));
        assertEquals("One", currentValues.get(7));
        assertEquals("Two", currentValues.get(8));
        assertEquals("Three", currentValues.get(9));
        assertEquals("Four", currentValues.get(10));
        assertEquals(3, currentValues.get(11));
        assertEquals(21, currentValues.get(12));
        assertEquals(1002, currentValues.get(13));
        assertEquals(1, currentValues.get(14));

        Map<Integer, Object> versionValues = version.getExecutions().get(0);
        assertEquals(51, versionValues.get(1));
        assertEquals(1, versionValues.get(2));
        assertEquals("Entity content", versionValues.get(3));
        assertEquals("Geometry", versionValues.get(4));
        assertEquals("MULTIPLE_CHOICE", versionValues.get(5));
        assertEquals("HARD", versionValues.get(6));
        assertEquals("entity.png", versionValues.get(7));
        assertEquals(3, versionValues.get(8));
        assertEquals(1002, versionValues.get(9));

        Object timestamp = currentValues.get(15);
        assertTrue(timestamp instanceof LocalDateTime);
        assertSame(timestamp, currentValues.get(16));
        assertSame(timestamp, versionValues.get(10));
        assertOptions(options, 51, 1);
        assertEquals(List.of(
                "setAutoCommit:false",
                "query:question-code",
                "execute:question",
                "execute:version",
                "execute:option",
                "execute:option",
                "execute:option",
                "execute:option",
                "commit",
                "setAutoCommit:true"
        ), database.getEvents());
        assertFalse((current.getSql() + version.getSql() + options.getSql())
                .contains("teacher_courses"));
    }

    @Test
    public void entityCreateFailureRollsBackRestoresAndSuppressesCleanupFailures() {
        SQLException writeFailure = new SQLException("version insert failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        QuestionRepositoryEntityJdbcTestSupport.Controller database =
                new QuestionRepositoryEntityJdbcTestSupport.Controller(true);
        database.failExecution(2, writeFailure);
        database.setRollbackFailure(rollbackFailure);
        database.setRestorationFailure(restorationFailure);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new QuestionRepository(database)
                        .create(1002, 21, question(0, QuestionStatus.ACTIVE))
        );

        assertEquals("Failed to create question", exception.getMessage());
        assertSame(writeFailure, exception.getCause());
        assertEquals(2, writeFailure.getSuppressed().length);
        assertSame(rollbackFailure, writeFailure.getSuppressed()[0]);
        assertSame(restorationFailure, writeFailure.getSuppressed()[1]);
        assertTrue(database.isRolledBack());
        assertFalse(database.isCommitted());
    }

    @Test
    public void malformedOrNullEntityIsRejectedBeforeOpeningAConnection() {
        QuestionRepositoryEntityJdbcTestSupport.Controller database =
                new QuestionRepositoryEntityJdbcTestSupport.Controller(true);
        QuestionRepository repository = new QuestionRepository(database);

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> repository.create(1002, 21, null)
        );
        assertEquals("Question data is required", missing.getMessage());

        Question incomplete = Question.rehydrate(
                0,
                "Incomplete",
                QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.EASY,
                QuestionStatus.ACTIVE,
                null,
                null,
                "General",
                "",
                List.of(
                        new AnswerOption(1, "One", true),
                        new AnswerOption(2, "Two", false),
                        new AnswerOption(3, "Three", false)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.create(1002, 21, incomplete)
        );
        assertEquals(0, database.getConnectionCalls());
    }

    @Test
    public void entityUpdateLocksVersionsAndSynchronizesCompatibilityColumns() {
        QuestionRepositoryEntityJdbcTestSupport.Controller database =
                new QuestionRepositoryEntityJdbcTestSupport.Controller(true);
        database.setCurrentVersion(4);
        QuestionRepository repository = new QuestionRepository(database);
        Question question = question(88, QuestionStatus.INACTIVE);

        int newVersion = repository.updateWithNewVersion(1003, 31, 4, question);

        assertEquals(5, newVersion);
        assertEquals(88, question.getQuestionId());
        QuestionRepositoryEntityJdbcTestSupport.StatementRecord lock =
                database.statement("lock");
        assertTrue(lock.getSql().contains("FOR UPDATE"));
        assertEquals(31, lock.getExecutions().get(0).get(1));

        QuestionRepositoryEntityJdbcTestSupport.StatementRecord version =
                database.statement("version");
        Map<Integer, Object> versionValues = version.getExecutions().get(0);
        assertEquals(31, versionValues.get(1));
        assertEquals(5, versionValues.get(2));
        assertEquals("Entity content", versionValues.get(3));
        assertEquals("Geometry", versionValues.get(4));
        assertEquals("MULTIPLE_CHOICE", versionValues.get(5));
        assertEquals("HARD", versionValues.get(6));
        assertEquals("entity.png", versionValues.get(7));
        assertEquals(3, versionValues.get(8));
        assertEquals(1003, versionValues.get(9));

        QuestionRepositoryEntityJdbcTestSupport.StatementRecord options =
                database.statement("option");
        assertOptions(options, 31, 5);
        QuestionRepositoryEntityJdbcTestSupport.StatementRecord current =
                database.statement("current");
        Map<Integer, Object> currentValues = current.getExecutions().get(0);
        assertEquals("Entity content", currentValues.get(1));
        assertEquals("Geometry", currentValues.get(2));
        assertEquals("MULTIPLE_CHOICE", currentValues.get(3));
        assertEquals("HARD", currentValues.get(4));
        assertEquals("entity.png", currentValues.get(5));
        assertEquals("One", currentValues.get(6));
        assertEquals("Two", currentValues.get(7));
        assertEquals("Three", currentValues.get(8));
        assertEquals("Four", currentValues.get(9));
        assertEquals(3, currentValues.get(10));
        assertEquals(5, currentValues.get(11));
        assertEquals(31, currentValues.get(13));
        assertEquals(4, currentValues.get(14));
        assertFalse(current.getSql().contains("status"));
        assertFalse(current.getSql().contains("course_id"));
        assertFalse(current.getSql().contains("created_by_user_id"));
        assertFalse(current.getSql().contains("created_at"));

        Object timestamp = versionValues.get(10);
        assertTrue(timestamp instanceof LocalDateTime);
        assertSame(timestamp, currentValues.get(12));
        assertEquals(List.of(
                "setAutoCommit:false",
                "query:lock",
                "execute:version",
                "execute:option",
                "execute:option",
                "execute:option",
                "execute:option",
                "execute:current",
                "commit",
                "setAutoCommit:true"
        ), database.getEvents());
    }

    @Test
    public void entityUpdateSupportsLegacyExpectedVersionAndRejectsStaleVersion() {
        QuestionRepositoryEntityJdbcTestSupport.Controller compatible =
                new QuestionRepositoryEntityJdbcTestSupport.Controller(false);
        compatible.setCurrentVersion(8);
        assertEquals(9, new QuestionRepository(compatible).updateWithNewVersion(
                1002,
                31,
                0,
                question(0, QuestionStatus.ACTIVE)
        ));
        assertFalse(compatible.isAutoCommit());

        QuestionRepositoryEntityJdbcTestSupport.Controller stale =
                new QuestionRepositoryEntityJdbcTestSupport.Controller(true);
        stale.setCurrentVersion(5);
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new QuestionRepository(stale).updateWithNewVersion(
                        1002,
                        31,
                        4,
                        question(0, QuestionStatus.ACTIVE)
                )
        );
        assertEquals("Question version conflict", exception.getMessage());
        assertTrue(stale.isRolledBack());
        assertFalse(stale.isCommitted());
        assertEquals(List.of(
                "setAutoCommit:false",
                "query:lock",
                "rollback",
                "setAutoCommit:true"
        ), stale.getEvents());
    }

    @Test
    public void entityUpdateFailurePreservesCauseAndSuppressedCleanupFailures() {
        SQLException writeFailure = new SQLException("option insert failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restorationFailure = new SQLException("restore failed");
        QuestionRepositoryEntityJdbcTestSupport.Controller database =
                new QuestionRepositoryEntityJdbcTestSupport.Controller(true);
        database.setCurrentVersion(4);
        database.failExecution(3, writeFailure);
        database.setRollbackFailure(rollbackFailure);
        database.setRestorationFailure(restorationFailure);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new QuestionRepository(database).updateWithNewVersion(
                        1002,
                        31,
                        4,
                        question(0, QuestionStatus.ACTIVE)
                )
        );

        assertEquals("Failed to update question version", exception.getMessage());
        assertSame(writeFailure, exception.getCause());
        assertEquals(2, writeFailure.getSuppressed().length);
        assertSame(rollbackFailure, writeFailure.getSuppressed()[0]);
        assertSame(restorationFailure, writeFailure.getSuppressed()[1]);
        assertTrue(database.isRolledBack());
        assertFalse(database.isCommitted());
    }

    private static Question question(int questionId, QuestionStatus status) {
        return Question.rehydrate(
                questionId,
                "Entity content",
                QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.HARD,
                status,
                LocalDateTime.of(2025, 1, 2, 3, 4),
                LocalDateTime.of(2025, 5, 6, 7, 8),
                "Geometry",
                "entity.png",
                List.of(
                        new AnswerOption(4, "Four", false),
                        new AnswerOption(2, "Two", false),
                        new AnswerOption(3, "Three", true),
                        new AnswerOption(1, "One", false)
                )
        );
    }

    private static void assertOptions(
            QuestionRepositoryEntityJdbcTestSupport.StatementRecord options,
            int questionId,
            int versionNo
    ) {
        String[] expectedTexts = {"One", "Two", "Three", "Four"};
        for (int index = 0; index < expectedTexts.length; index++) {
            Map<Integer, Object> values = options.getExecutions().get(index);
            assertEquals(questionId, values.get(1));
            assertEquals(versionNo, values.get(2));
            assertEquals(index + 1, values.get(3));
            assertEquals(expectedTexts[index], values.get(4));
        }
    }
}
