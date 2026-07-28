package hsts.common;

import hsts.common.type.BotAnswerStatus;
import hsts.common.type.BotSourceStatus;
import hsts.common.type.BotSourceType;
import hsts.common.type.BotStatus;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BotContractTest {
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 2, 9, 0);
    private static final LocalDateTime UPDATED = CREATED.plusHours(1);

    @Test
    public void dtoMappingsAndSerializationRoundTripsAreComplete() throws Exception {
        BotMessageDTO message = new BotMessageDTO(
                9, 1, " Question? ", " Answer ", BotAnswerStatus.ANSWERED, CREATED
        );
        BotSourceDTO source = new BotSourceDTO(
                4, 2, BotSourceType.QUESTION_BANK, " Version 3 ",
                8, 3, BotSourceStatus.ACTIVE, CREATED, null
        );
        BotHistoryDTO history = new BotHistoryDTO(
                2, 5, " Tutor ", " Mathematics ", List.of(message)
        );
        BotUsageSummaryDTO usage = new BotUsageSummaryDTO(
                2, 5, "Tutor", "Mathematics", 3, UPDATED,
                List.of(new CommonBotQuestionDTO("Fractions?", 2))
        );
        CourseBotSummaryDTO summary = new CourseBotSummaryDTO(
                2, 5, "Tutor", "Mathematics", BotStatus.ACTIVE,
                1, CREATED, UPDATED
        );
        BotQuestionResultDTO result = new BotQuestionResultDTO(message, true);

        assertEquals("Question?", message.getQuestionText());
        assertEquals("Answer", message.getAnswerText());
        assertEquals(Integer.valueOf(8), source.getQuestionId());
        assertEquals(1, history.getMessages().size());
        assertEquals(3, usage.getTotalQuestions());
        assertEquals(1, summary.getActiveSourceCount());
        assertTrue(result.isSuitableAnswer());

        for (Serializable contract : List.of(
                message, source, history, usage, summary, result,
                new CourseIdPayload(5), new CourseBotIdPayload(2),
                new CreateCourseBotPayload(5, "Tutor"),
                new UpdateCourseBotPayload(2, "Tutor", BotStatus.INACTIVE),
                new AddBotTextSourcePayload(2, "Notes", "Text"),
                new UploadBotSourcePayload(2, "notes.pdf", BotSourceType.PDF,
                        new byte[]{1, 2}),
                new BotQuestionVersionReference(8, 3),
                new AddBotQuestionSourcesPayload(
                        2, List.of(new BotQuestionVersionReference(8, 3))
                ),
                new RemoveBotSourcePayload(2, 4),
                new AskCourseBotPayload(5, "Question?")
        )) {
            assertEquals(contract.getClass(), roundTrip(contract).getClass());
        }
    }

    @Test
    public void listsAreOrderedImmutableAndRejectNulls() {
        BotMessageDTO first = message(1, 1);
        BotMessageDTO second = message(2, 2);
        List<BotMessageDTO> supplied = new ArrayList<>(List.of(second, first));
        BotHistoryDTO history = new BotHistoryDTO(2, 5, "Tutor", "Math", supplied);
        supplied.clear();

        assertEquals(2, history.getMessages().get(0).getMessageId());
        assertThrows(UnsupportedOperationException.class,
                () -> history.getMessages().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new BotHistoryDTO(2, 5, "Tutor", "Math", null));
        assertThrows(IllegalArgumentException.class,
                () -> new BotHistoryDTO(2, 5, "Tutor", "Math",
                        java.util.Arrays.asList(first, null)));
    }

    @Test
    public void uploadPayloadDefensivelyCopiesBytesAndValidatesBasenameAndType() {
        byte[] supplied = {1, 2, 3};
        UploadBotSourcePayload payload = new UploadBotSourcePayload(
                2, " notes.docx ", BotSourceType.DOCX, supplied
        );
        supplied[0] = 9;
        byte[] returned = payload.getContent();
        returned[1] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, payload.getContent());
        assertNotSame(payload.getContent(), payload.getContent());
        assertEquals("notes.docx", payload.getFileName());
        assertThrows(IllegalArgumentException.class,
                () -> new UploadBotSourcePayload(
                        2, "../notes.pdf", BotSourceType.PDF, new byte[]{1}
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new UploadBotSourcePayload(
                        2, "notes", BotSourceType.FREE_TEXT, new byte[]{1}
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new UploadBotSourcePayload(
                        2, "notes.pdf", BotSourceType.PDF, new byte[0]
                ));
    }

    @Test
    public void questionReferencesRejectDuplicatesAndRemainOrdered() {
        BotQuestionVersionReference first = new BotQuestionVersionReference(9, 2);
        BotQuestionVersionReference second = new BotQuestionVersionReference(4, 7);
        AddBotQuestionSourcesPayload payload = new AddBotQuestionSourcesPayload(
                2, List.of(first, second)
        );

        assertEquals(9, payload.getQuestions().get(0).getQuestionId());
        assertThrows(UnsupportedOperationException.class,
                () -> payload.getQuestions().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new AddBotQuestionSourcesPayload(2, List.of(first, first)));
        assertThrows(IllegalArgumentException.class,
                () -> new AddBotQuestionSourcesPayload(2, List.of()));
    }

    @Test
    public void sourceAndAnswerCrossFieldRulesAreEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> new BotSourceDTO(
                        1, 2, BotSourceType.QUESTION_BANK, "Question",
                        null, null, BotSourceStatus.ACTIVE, CREATED, null
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new BotSourceDTO(
                        1, 2, BotSourceType.PDF, "PDF", 4, 1,
                        BotSourceStatus.ACTIVE, CREATED, null
                ));
        BotMessageDTO noAnswer = new BotMessageDTO(
                1, 1, "Question", null,
                BotAnswerStatus.NO_SUITABLE_ANSWER, CREATED
        );
        assertEquals("", noAnswer.getAnswerText());
        assertThrows(IllegalArgumentException.class,
                () -> new BotQuestionResultDTO(noAnswer, true));
        assertThrows(IllegalArgumentException.class,
                () -> new BotMessageDTO(
                        1, 1, "Question", " ",
                        BotAnswerStatus.ANSWERED, CREATED
                ));
    }

    @Test
    public void zeroUsageHasNoActivityOrCommonQuestions() {
        BotUsageSummaryDTO empty = new BotUsageSummaryDTO(
                1, 2, "Tutor", "Math", 0, null, List.of()
        );
        assertTrue(empty.getCommonQuestions().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new BotUsageSummaryDTO(
                        1, 2, "Tutor", "Math", 0, UPDATED, List.of()
                ));
    }

    @Test
    public void contractsAreFinalSerializableSetterFreeAndSensitiveFieldFree() {
        List<Class<?>> types = List.of(
                CourseBotSummaryDTO.class, BotSourceDTO.class, BotMessageDTO.class,
                BotHistoryDTO.class, CommonBotQuestionDTO.class,
                BotUsageSummaryDTO.class, BotQuestionResultDTO.class,
                CourseIdPayload.class, CourseBotIdPayload.class,
                CreateCourseBotPayload.class, UpdateCourseBotPayload.class,
                AddBotTextSourcePayload.class, UploadBotSourcePayload.class,
                BotQuestionVersionReference.class,
                AddBotQuestionSourcesPayload.class, RemoveBotSourcePayload.class,
                AskCourseBotPayload.class
        );
        Set<String> forbidden = Set.of(
                "studentid", "studentuserid", "teacherid", "coordinatorid",
                "authenticateduserid", "userid", "password", "passwordhash",
                "identityhash", "apikey", "providerkey", "filepath", "path"
        );
        for (Class<?> type : types) {
            assertTrue(Serializable.class.isAssignableFrom(type));
            assertTrue(Modifier.isFinal(type.getModifiers()));
            for (Method method : type.getDeclaredMethods()) {
                assertFalse(method.getName().startsWith("set"));
            }
            for (Field field : type.getDeclaredFields()) {
                assertFalse(type.getName() + "." + field.getName(),
                        forbidden.contains(field.getName().toLowerCase()));
                if (!Modifier.isStatic(field.getModifiers())) {
                    assertTrue(Modifier.isPrivate(field.getModifiers()));
                    assertTrue(Modifier.isFinal(field.getModifiers()));
                    assertFalse(field.getType().getName().startsWith("hsts.server.entity"));
                }
            }
        }
    }

    private static BotMessageDTO message(int id, int sequence) {
        return new BotMessageDTO(
                id, sequence, "Question " + id, "Answer " + id,
                BotAnswerStatus.ANSWERED, CREATED.plusMinutes(sequence)
        );
    }

    private static Object roundTrip(Serializable value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }
}
