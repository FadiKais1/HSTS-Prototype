package hsts.server.repository;

import hsts.common.type.BotAnswerStatus;
import hsts.common.type.BotStatus;
import hsts.server.entity.BotConversation;
import hsts.server.entity.BotMessage;
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BotConversationRepositoryTest {
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 7, 2, 8, 0);
    private static final String SUBJECT = "d0f5c7a2-970a-4a15-b768-716be09c04af";

    @Test
    public void personalHistoryRequiresActiveStudentEnrollmentAndPreservesMessageOrder() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("u.role = 'STUDENT'", conversationRow(CREATED.plusMinutes(2))),
                        query("ORDER BY bm.sequence_no ASC",
                                messageRow(31, 1, "First", "ANSWERED", "Answer", CREATED.plusMinutes(1)),
                                messageRow(32, 2, "Second", "NO_SUITABLE_ANSWER", "", CREATED.plusMinutes(2)))
                );

        BotConversation history = new BotConversationRepository(database)
                .findHistoryForStudent(1001, 7).orElseThrow();

        assertEquals(1001, database.executed.get(0).parameters.get(1));
        assertEquals(7, database.executed.get(0).parameters.get(2));
        assertEquals(1001, database.executed.get(0).parameters.get(3));
        assertTrue(database.executed.get(0).actualSql.contains("cb.status = 'ACTIVE'"));
        assertTrue(database.executed.get(0).actualSql.contains("u.status = 'ACTIVE'"));
        assertEquals(List.of(1, 2), history.getMessages().stream()
                .map(BotMessage::getSequenceNumber).toList());
        assertEquals("", history.getMessages().get(1).getAnswerText());
        assertThrows(UnsupportedOperationException.class,
                () -> history.getMessages().add(history.getMessages().get(0)));
        database.assertConsumed();
    }

    @Test
    public void missingUnauthorizedOrInactiveHistoryIsConcealed() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("bc.student_user_id = ?")
                );
        assertTrue(new BotConversationRepository(database)
                .findHistoryForStudent(1001, 7).isEmpty());
        database.assertConsumed();
    }

    @Test
    public void getOrCreateReturnsExistingConversationWithoutReplacingSubject() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("WHERE cb.bot_id = ?", row("bot_id", 7, "course_id", 1)),
                        query("FOR UPDATE", conversationRow(CREATED)),
                        query("ORDER BY bm.sequence_no ASC")
                );

        BotConversation conversation = new BotConversationRepository(database)
                .getOrCreateForStudent(1001, activeBot(),
                        "90ad5606-4a2e-439c-a07c-d99bb93fa179", CREATED.plusHours(1));

        assertEquals(SUBJECT, conversation.getProviderSubjectId());
        assertEquals(1, database.commits);
        assertEquals(0, database.executed.stream()
                .filter(step -> step.actualSql.contains("INSERT INTO bot_conversations"))
                .count());
        database.assertConsumed();
    }

    @Test
    public void getOrCreatePersistsPseudonymousSubjectTransactionally() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("WHERE cb.bot_id = ?", row("bot_id", 7, "course_id", 1)),
                        query("FOR UPDATE"),
                        update("INSERT INTO bot_conversations", 1, 21)
                );

        BotConversation conversation = new BotConversationRepository(database)
                .getOrCreateForStudent(1001, activeBot(), SUBJECT, CREATED);

        assertEquals(21, conversation.getConversationId());
        assertEquals(1001, database.executed.get(2).parameters.get(2));
        assertEquals(SUBJECT, database.executed.get(2).parameters.get(3));
        assertEquals(1, database.commits);
        assertTrue(database.autoCommit);
        database.assertConsumed();
    }

    @Test
    public void namedConversationCreationRaceRereadsWinner() {
        SQLException duplicate = new SQLException(
                "Duplicate entry for key 'uq_bot_conversations_bot_student'",
                "23000", 1062
        );
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("WHERE cb.bot_id = ?", row("bot_id", 7, "course_id", 1)),
                        query("FOR UPDATE"),
                        failure("INSERT INTO bot_conversations", duplicate),
                        query("u.role = 'STUDENT'", conversationRow(CREATED)),
                        query("ORDER BY bm.sequence_no ASC")
                );

        BotConversation winner = new BotConversationRepository(database)
                .getOrCreateForStudent(1001, activeBot(), SUBJECT, CREATED);

        assertEquals(21, winner.getConversationId());
        assertEquals(SUBJECT, winner.getProviderSubjectId());
        assertEquals(1, database.rollbacks);
        assertEquals(0, database.commits);
        assertTrue(database.autoCommit);
        database.assertConsumed();
    }

    @Test
    public void appendLocksOwnershipRequiresExactNextSequenceAndUpdatesTimestamp() {
        BotConversation supplied = BotConversation.rehydrate(
                21, 7, 1001, SUBJECT, CREATED, CREATED, List.of()
        );
        BotMessage message = BotMessage.create(
                21, 1, "What is a vector?", "what is a vector?", "A magnitude and direction",
                BotAnswerStatus.ANSWERED, "request-1", CREATED.plusMinutes(1)
        );
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("bc.conversation_id = ?", conversationRow(CREATED)),
                        query("ORDER BY bm.sequence_no ASC"),
                        update("INSERT INTO bot_messages", 1, 31),
                        update("UPDATE bot_conversations", 1, 0),
                        query("ORDER BY bm.sequence_no ASC",
                                messageRow(31, 1, "What is a vector?", "ANSWERED",
                                        "A magnitude and direction", CREATED.plusMinutes(1)))
                );

        BotConversation updated = new BotConversationRepository(database)
                .appendMessage(1001, supplied, message);

        assertEquals(1, updated.getMessages().size());
        assertEquals(CREATED.plusMinutes(1), updated.getUpdatedAt());
        assertEquals(1, database.executed.get(2).parameters.get(2));
        assertEquals("what is a vector?", database.executed.get(2).parameters.get(4));
        assertEquals(CREATED.plusMinutes(1), database.executed.get(3).parameters.get(1));
        assertEquals(1, database.commits);
        database.assertConsumed();
    }

    @Test
    public void sequenceConflictRollsBackWithoutWriting() {
        BotConversation supplied = BotConversation.rehydrate(
                21, 7, 1001, SUBJECT, CREATED, CREATED.plusMinutes(1),
                List.of(BotMessage.rehydrate(
                        31, 21, 1, "First", "first", "Answer",
                        BotAnswerStatus.ANSWERED, null, CREATED.plusMinutes(1)
                ))
        );
        BotMessage wrong = BotMessage.create(
                21, 3, "Third", "third", "Answer", BotAnswerStatus.ANSWERED,
                null, CREATED.plusMinutes(2)
        );
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("bc.conversation_id = ?", conversationRow(CREATED.plusMinutes(1))),
                        query("ORDER BY bm.sequence_no ASC",
                                messageRow(31, 1, "First", "ANSWERED", "Answer",
                                        CREATED.plusMinutes(1)))
                );

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new BotConversationRepository(database)
                        .appendMessage(1001, supplied, wrong));
        assertEquals("Bot message sequence conflict", thrown.getMessage());
        assertEquals(1, database.rollbacks);
        assertEquals(0, database.commits);
        database.assertConsumed();
    }

    @Test
    public void malformedPersistedSequenceIsRejectedByDomainHydration() {
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("u.role = 'STUDENT'", conversationRow(CREATED.plusMinutes(2))),
                        query("ORDER BY bm.sequence_no ASC",
                                messageRow(31, 1, "First", "ANSWERED", "Answer", CREATED.plusMinutes(1)),
                                messageRow(33, 3, "Third", "ANSWERED", "Answer", CREATED.plusMinutes(2)))
                );
        assertThrows(IllegalArgumentException.class,
                () -> new BotConversationRepository(database)
                        .findHistoryForStudent(1001, 7));
    }

    @Test
    public void sqlFailurePreservesCauseAndSuppressesCleanupFailures() {
        SQLException cause = new SQLException("insert failed");
        SQLException rollback = new SQLException("rollback failed");
        SQLException restore = new SQLException("restore failed");
        BotRepositoryJdbcTestSupport.FakeDatabaseController database =
                new BotRepositoryJdbcTestSupport.FakeDatabaseController(
                        query("WHERE cb.bot_id = ?", row("bot_id", 7, "course_id", 1)),
                        query("FOR UPDATE"),
                        failure("INSERT INTO bot_conversations", cause)
                );
        database.rollbackFailure = rollback;
        database.restoreFailure = restore;

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new BotConversationRepository(database)
                        .getOrCreateForStudent(1001, activeBot(), SUBJECT, CREATED));
        assertSame(cause, thrown.getCause());
        assertEquals(List.of(rollback, restore), List.of(cause.getSuppressed()));
        assertFalse(thrown.getMessage().contains("insert failed"));
    }

    private static CourseBot activeBot() {
        return CourseBot.rehydrate(
                7, 1, "Legacy Bot", BotStatus.ACTIVE, 1002,
                null, null, CREATED.minusHours(1), CREATED.minusHours(1), List.of()
        );
    }

    private static Map<String, Object> conversationRow(LocalDateTime updatedAt) {
        return row(
                "conversation_id", 21, "bot_id", 7, "student_user_id", 1001,
                "provider_subject_id", SUBJECT, "created_at", CREATED,
                "updated_at", updatedAt
        );
    }

    private static Map<String, Object> messageRow(
            int messageId, int sequence, String question, String status,
            String answer, LocalDateTime createdAt
    ) {
        return row(
                "message_id", messageId, "conversation_id", 21,
                "sequence_no", sequence, "question_text", question,
                "normalized_question", question.toLowerCase(), "answer_text", answer,
                "answer_status", status, "provider_request_id", null,
                "created_at", createdAt
        );
    }
}
