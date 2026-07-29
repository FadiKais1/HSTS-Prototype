package hsts.common;

import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PrincipalOversightContractTest {
    @Test
    public void questionProjectionIsSerializableImmutableAndDefensive() throws Exception {
        PrincipalQuestionDTO source = question();
        PrincipalQuestionDTO copy = roundTrip(source);
        assertEquals(11, copy.getQuestionId());
        assertEquals(List.of("One", "Two", "Three", "Four"), copy.getAnswerOptions());
        assertThrows(UnsupportedOperationException.class,
                () -> copy.getAnswerOptions().add("Five"));
        assertTrue(Arrays.stream(PrincipalQuestionDTO.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isFinal(field.getModifiers())));
    }

    @Test
    public void historicalPayloadContainsOnlyQuestionAndVersionIdentity() throws Exception {
        QuestionVersionPayload payload = roundTrip(new QuestionVersionPayload(11, 3));
        assertEquals(11, payload.getQuestionId());
        assertEquals(3, payload.getVersionNo());
        List<String> fields = Arrays.stream(QuestionVersionPayload.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName).toList();
        assertEquals(List.of("questionId", "versionNo"), fields);
        assertFalse(fields.stream().anyMatch(name ->
                name.toLowerCase().contains("user")
                        || name.toLowerCase().contains("teacher")
                        || name.toLowerCase().contains("principal")));

        ExamVersionSelectionPayload exam = roundTrip(
                new ExamVersionSelectionPayload(5, 2)
        );
        assertEquals(5, exam.getExamId());
        assertEquals(2, exam.getVersionNo());
        List<String> examFields = Arrays.stream(
                        ExamVersionSelectionPayload.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName).toList();
        assertEquals(List.of("examId", "versionNo"), examFields);
        assertFalse(examFields.stream().anyMatch(name ->
                name.toLowerCase().contains("user")
                        || name.toLowerCase().contains("teacher")
                        || name.toLowerCase().contains("principal")));
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }

    private static PrincipalQuestionDTO question() {
        LocalDateTime time = LocalDateTime.of(2026, 7, 29, 9, 0);
        return new PrincipalQuestionDTO(
                11, 3, 1, "Course", 1002, "Teacher", "Content", "Topic",
                QuestionType.MULTIPLE_CHOICE, DifficultyLevel.HARD,
                QuestionStatus.ACTIVE, "", List.of("One", "Two", "Three", "Four"),
                3, time, time
        );
    }
}
