package hsts.server.entity;

import hsts.common.type.BotAnswerStatus;
import hsts.common.type.BotSourceStatus;
import hsts.common.type.BotSourceType;
import hsts.common.type.BotStatus;
import org.junit.Test;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CourseBotDomainTest {
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 2, 3, 10, 0);
    private static final String HASH = "a".repeat(64);

    @Test
    public void courseBotCreationAndMutationsPreserveCreationTime() {
        CourseBot bot = CourseBot.create(
                5, " Tutor ", 11, null, null, CREATED
        );
        bot.rename(" Algebra Tutor ", CREATED.plusMinutes(1));
        bot.deactivate(CREATED.plusMinutes(2));
        bot.activate(CREATED.plusMinutes(3));

        assertEquals(0, bot.getBotId());
        assertEquals("Algebra Tutor", bot.getName());
        assertEquals(BotStatus.ACTIVE, bot.getStatus());
        assertEquals(CREATED, bot.getCreatedAt());
        assertEquals(CREATED.plusMinutes(3), bot.getUpdatedAt());
        assertThrows(IllegalArgumentException.class,
                () -> bot.rename("Old", CREATED.minusSeconds(1)));
    }

    @Test
    public void courseBotHydrationAndSourcesAreDeepDefensive() {
        BotSource source = persistedSource(7, BotSourceStatus.ACTIVE, null);
        List<BotSource> supplied = new ArrayList<>(List.of(source));
        CourseBot bot = CourseBot.rehydrate(
                2, 5, "Tutor", BotStatus.ACTIVE, 11,
                "provider", "bot-2", CREATED, CREATED.plusMinutes(1), supplied
        );
        supplied.clear();
        BotSource returned = bot.getSources().get(0);
        returned.remove(CREATED.plusMinutes(2));

        assertEquals(BotSourceStatus.ACTIVE, bot.getSources().get(0).getStatus());
        assertThrows(UnsupportedOperationException.class,
                () -> bot.getSources().clear());
        assertNotSame(returned, bot.getSources().get(0));
        assertThrows(IllegalArgumentException.class,
                () -> CourseBot.rehydrate(
                        2, 5, "Tutor", BotStatus.ACTIVE, 11,
                        null, null, CREATED, CREATED.plusMinutes(1),
                        List.of(source, source)
                ));
    }

    @Test
    public void courseBotAddsAndReplacesOnlyMatchingSourceSnapshots() {
        CourseBot bot = CourseBot.rehydrate(
                2, 5, "Tutor", BotStatus.ACTIVE, 11,
                null, null, CREATED, CREATED, List.of()
        );
        BotSource source = persistedSource(7, BotSourceStatus.ACTIVE, null);
        bot.addSource(source, CREATED.plusMinutes(1));
        BotSource removed = source.copy();
        removed.remove(CREATED.plusMinutes(2));
        bot.replaceSource(removed, CREATED.plusMinutes(2));

        assertEquals(BotSourceStatus.REMOVED, bot.getSources().get(0).getStatus());
        assertThrows(IllegalArgumentException.class,
                () -> bot.addSource(source, CREATED.plusMinutes(3)));
        assertThrows(IllegalArgumentException.class,
                () -> bot.replaceSource(source, CREATED.plusMinutes(3)));
    }

    @Test
    public void botSourceValidatesReferencesChecksumAndRemovalTransition() {
        BotSource question = BotSource.create(
                2, BotSourceType.QUESTION_BANK, "Question 9 v3", "Content",
                HASH, 9, 3, 11, null, CREATED
        );
        BotSource pdf = BotSource.create(
                2, BotSourceType.PDF, "Notes", "Extracted", HASH,
                null, null, 11, null, CREATED
        );
        question.remove(CREATED.plusMinutes(1));
        question.remove(CREATED.plusMinutes(1));

        assertEquals(BotSourceStatus.REMOVED, question.getStatus());
        assertEquals(BotSourceType.PDF, pdf.getSourceType());
        assertThrows(IllegalStateException.class,
                () -> question.remove(CREATED.plusMinutes(2)));
        assertThrows(IllegalArgumentException.class,
                () -> BotSource.create(
                        2, BotSourceType.PDF, "Notes", "Text", HASH,
                        9, 1, 11, null, CREATED
                ));
        assertThrows(IllegalArgumentException.class,
                () -> BotSource.create(
                        2, BotSourceType.FREE_TEXT, "Notes", "Text", "ABC",
                        null, null, 11, null, CREATED
                ));
    }

    @Test
    public void botMessageCreationHydrationAndAnswerRulesAreImmutable() {
        BotMessage created = BotMessage.create(
                3, 1, " Question ", " question ", " Answer ",
                BotAnswerStatus.ANSWERED, " request-1 ", CREATED
        );
        BotMessage persisted = BotMessage.rehydrate(
                8, 3, 2, "Other?", "other", null,
                BotAnswerStatus.NO_SUITABLE_ANSWER, null, CREATED.plusMinutes(1)
        );

        assertEquals(0, created.getMessageId());
        assertEquals("Question", created.getQuestionText());
        assertEquals("question", created.getNormalizedQuestion());
        assertEquals("Answer", created.getAnswerText());
        assertEquals("", persisted.getAnswerText());
        assertEquals(8, persisted.copy().getMessageId());
        assertThrows(IllegalArgumentException.class,
                () -> BotMessage.create(
                        3, 0, "Question", "question", "Answer",
                        BotAnswerStatus.ANSWERED, null, CREATED
                ));
        assertThrows(IllegalArgumentException.class,
                () -> BotMessage.create(
                        3, 1, "Question", " ", "Answer",
                        BotAnswerStatus.ANSWERED, null, CREATED
                ));
    }

    @Test
    public void conversationHydrationPreservesContiguousOrderAndDefensiveCopies() {
        String subject = UUID.randomUUID().toString();
        BotMessage first = message(4, 1, CREATED);
        BotMessage second = message(5, 2, CREATED.plusMinutes(1));
        List<BotMessage> supplied = new ArrayList<>(List.of(first, second));
        BotConversation conversation = BotConversation.rehydrate(
                3, 2, 21, subject, CREATED, CREATED.plusMinutes(1), supplied
        );
        supplied.clear();

        assertEquals(4, conversation.getMessages().get(0).getMessageId());
        assertNotSame(first, conversation.getMessages().get(0));
        assertThrows(UnsupportedOperationException.class,
                () -> conversation.getMessages().clear());
        assertThrows(IllegalArgumentException.class,
                () -> BotConversation.rehydrate(
                        3, 2, 21, subject, CREATED, CREATED.plusMinutes(1),
                        List.of(second, first)
                ));
        assertThrows(IllegalArgumentException.class,
                () -> BotConversation.create(2, 21, "not-a-uuid", CREATED));
    }

    @Test
    public void conversationAppendEnforcesIdentitySequenceAndTimestamp() {
        String subject = UUID.randomUUID().toString();
        BotConversation conversation = BotConversation.rehydrate(
                3, 2, 21, subject, CREATED, CREATED, List.of()
        );
        BotMessage first = message(4, 1, CREATED.plusMinutes(1));
        conversation.appendMessage(first, CREATED.plusMinutes(1));

        assertEquals(1, conversation.getMessages().size());
        assertEquals(CREATED.plusMinutes(1), conversation.getUpdatedAt());
        assertThrows(IllegalArgumentException.class,
                () -> conversation.appendMessage(
                        message(5, 3, CREATED.plusMinutes(2)), CREATED.plusMinutes(2)
                ));
        assertThrows(IllegalArgumentException.class,
                () -> conversation.appendMessage(
                        BotMessage.rehydrate(
                                6, 99, 2, "Q", "q", "A",
                                BotAnswerStatus.ANSWERED, null, CREATED.plusMinutes(2)
                        ), CREATED.plusMinutes(2)
                ));
    }

    @Test
    public void botEntitiesAreFinalNonSerializableAndArchitectureClean() {
        for (Class<?> type : List.of(
                CourseBot.class, BotSource.class,
                BotConversation.class, BotMessage.class
        )) {
            assertTrue(java.lang.reflect.Modifier.isFinal(type.getModifiers()));
            assertFalse(Serializable.class.isAssignableFrom(type));
            for (Field field : type.getDeclaredFields()) {
                String dependency = field.getType().getName();
                assertFalse(dependency.startsWith("hsts.common.")
                        && !dependency.startsWith("hsts.common.type."));
                assertFalse(dependency.startsWith("hsts.server.repository"));
                assertFalse(dependency.startsWith("javafx."));
                assertFalse(dependency.startsWith("hsts.ocsf"));
                assertFalse(dependency.startsWith("hsts.client"));
                String name = field.getName().toLowerCase();
                assertFalse(name.contains("password") || name.contains("apikey")
                        || name.contains("providerkey") || name.contains("filepath"));
            }
        }
    }

    private static BotSource persistedSource(int sourceId,
                                             BotSourceStatus status,
                                             LocalDateTime removedAt) {
        return BotSource.rehydrate(
                sourceId, 2, BotSourceType.FREE_TEXT, "Notes", "Text", HASH,
                null, null, 11, status, null, CREATED, removedAt
        );
    }

    private static BotMessage message(int messageId, int sequence,
                                      LocalDateTime createdAt) {
        return BotMessage.rehydrate(
                messageId, 3, sequence, "Question " + sequence,
                "question " + sequence, "Answer " + sequence,
                BotAnswerStatus.ANSWERED, null, createdAt
        );
    }
}
