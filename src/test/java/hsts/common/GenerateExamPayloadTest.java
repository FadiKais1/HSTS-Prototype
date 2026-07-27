package hsts.common;

import hsts.common.type.DifficultyLevel;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class GenerateExamPayloadTest {
    @Test
    public void constructorMapsEveryField() {
        GenerateExamPayload payload = payload();

        assertEquals(17, payload.getCourseId());
        assertEquals("Automatic midterm", payload.getTitle());
        assertEquals(75, payload.getDurationMinutes());
        assertEquals("Teacher notes", payload.getTeacherNotes());
        assertEquals("Read carefully", payload.getStudentInstructions());
        assertEquals("Calculus", payload.getTopic());
        assertEquals(DifficultyLevel.HARD, payload.getDifficulty());
        assertEquals(8, payload.getQuestionCount());
    }

    @Test
    public void serializationRoundTripPreservesEveryField() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(payload());
        }

        GenerateExamPayload result;
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            result = (GenerateExamPayload) input.readObject();
        }

        assertEquals(17, result.getCourseId());
        assertEquals("Automatic midterm", result.getTitle());
        assertEquals(75, result.getDurationMinutes());
        assertEquals("Teacher notes", result.getTeacherNotes());
        assertEquals("Read carefully", result.getStudentInstructions());
        assertEquals("Calculus", result.getTopic());
        assertEquals(DifficultyLevel.HARD, result.getDifficulty());
        assertEquals(8, result.getQuestionCount());
    }

    @Test
    public void contractContainsOnlyApprovedDataAndNoIdentityField() {
        Set<String> fields = Arrays.stream(GenerateExamPayload.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "courseId",
                "title",
                "durationMinutes",
                "teacherNotes",
                "studentInstructions",
                "topic",
                "difficulty",
                "questionCount"
        ), fields);
    }

    private static GenerateExamPayload payload() {
        return new GenerateExamPayload(
                17,
                "Automatic midterm",
                75,
                "Teacher notes",
                "Read carefully",
                "Calculus",
                DifficultyLevel.HARD,
                8
        );
    }
}
