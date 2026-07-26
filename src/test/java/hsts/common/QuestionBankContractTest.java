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
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class QuestionBankContractTest {
    @Test
    public void courseSummaryMapsAndSerializesEveryField() throws Exception {
        CourseSummaryDTO restored = roundTrip(new CourseSummaryDTO(
                12, 4, "MATH-7", "Mathematics", "Mathematics",
                "7", "2026"
        ));

        assertEquals(12, restored.getCourseId());
        assertEquals(4, restored.getSubjectId());
        assertEquals("MATH-7", restored.getCourseCode());
        assertEquals("Mathematics", restored.getCourseName());
        assertEquals("Mathematics", restored.getSubjectName());
        assertEquals("7", restored.getGradeLevel());
        assertEquals("2026", restored.getSchoolYear());
    }

    @Test
    public void questionDtoPreservesOldDefaultsAndMapsCompleteConstructor() throws Exception {
        QuestionDTO shortCompatible = new QuestionDTO(
                3, "Content", "Algebra", "MULTIPLE_CHOICE", "EASY", "ACTIVE"
        );
        QuestionDTO compatible = new QuestionDTO(
                3, "Content", "Algebra", "MULTIPLE_CHOICE", "EASY", "ACTIVE",
                "image.png", "One", "Two", "Three", "Four", 2
        );

        assertEquals(0, shortCompatible.getCourseId());
        assertEquals(0, shortCompatible.getSubjectId());
        assertEquals(1, shortCompatible.getVersionNo());
        assertEquals(0, compatible.getCourseId());
        assertEquals(0, compatible.getSubjectId());
        assertEquals(1, compatible.getVersionNo());

        QuestionDTO restored = roundTrip(new QuestionDTO(
                3, "Content", "Algebra", "MULTIPLE_CHOICE", "HARD", "INACTIVE",
                "image.png", "One", "Two", "Three", "Four", 4,
                12, 4, 7
        ));

        assertEquals(3, restored.getQuestionId());
        assertEquals("Content", restored.getContent());
        assertEquals("Algebra", restored.getTopic());
        assertEquals("MULTIPLE_CHOICE", restored.getType());
        assertEquals("HARD", restored.getDifficulty());
        assertEquals("INACTIVE", restored.getStatus());
        assertEquals("image.png", restored.getIllustrationPath());
        assertEquals("One", restored.getAnswerOption1());
        assertEquals("Two", restored.getAnswerOption2());
        assertEquals("Three", restored.getAnswerOption3());
        assertEquals("Four", restored.getAnswerOption4());
        assertEquals(4, restored.getCorrectOptionNumber());
        assertEquals(12, restored.getCourseId());
        assertEquals(4, restored.getSubjectId());
        assertEquals(7, restored.getVersionNo());
    }

    @Test
    public void createQuestionPayloadMapsAndSerializesWithoutClientIdentity() throws Exception {
        CreateQuestionPayload restored = roundTrip(new CreateQuestionPayload(
                12, "Content", "Algebra", DifficultyLevel.MEDIUM, "image.png",
                "One", "Two", "Three", "Four", 3
        ));

        assertEquals(12, restored.getCourseId());
        assertEquals("Content", restored.getContent());
        assertEquals("Algebra", restored.getTopic());
        assertEquals(DifficultyLevel.MEDIUM, restored.getDifficulty());
        assertEquals("image.png", restored.getIllustrationPath());
        assertEquals("One", restored.getAnswerOption1());
        assertEquals("Two", restored.getAnswerOption2());
        assertEquals("Three", restored.getAnswerOption3());
        assertEquals("Four", restored.getAnswerOption4());
        assertEquals(3, restored.getCorrectOptionNumber());
        assertHasNoClientIdentityFields(CreateQuestionPayload.class);
    }

    @Test
    public void updateQuestionPayloadPreservesOldDefaultAndMapsCompleteConstructor() throws Exception {
        UpdateQuestionPayload shortCompatible = new UpdateQuestionPayload(3, "Content");
        UpdateQuestionPayload compatible = new UpdateQuestionPayload(
                3, "Content", "Algebra", "EASY", "ACTIVE", "",
                "One", "Two", "Three", "Four", 1
        );

        assertEquals(0, shortCompatible.getExpectedVersionNo());
        assertEquals(0, compatible.getExpectedVersionNo());

        UpdateQuestionPayload restored = roundTrip(new UpdateQuestionPayload(
                3, "Content", "Algebra", "HARD", "INACTIVE", "image.png",
                "One", "Two", "Three", "Four", 4, 7
        ));

        assertEquals(3, restored.getQuestionId());
        assertEquals("Content", restored.getContent());
        assertEquals("Algebra", restored.getTopic());
        assertEquals("HARD", restored.getDifficulty());
        assertEquals("INACTIVE", restored.getStatus());
        assertEquals("image.png", restored.getIllustrationPath());
        assertEquals("One", restored.getAnswerOption1());
        assertEquals("Two", restored.getAnswerOption2());
        assertEquals("Three", restored.getAnswerOption3());
        assertEquals("Four", restored.getAnswerOption4());
        assertEquals(4, restored.getCorrectOptionNumber());
        assertEquals(7, restored.getExpectedVersionNo());
    }

    @Test
    public void questionFilterMapsSerializesAndAllowsNullFilters() throws Exception {
        QuestionFilterPayload restored = roundTrip(new QuestionFilterPayload(
                12, 4, "Algebra", DifficultyLevel.HARD, QuestionStatus.ACTIVE
        ));

        assertEquals(Integer.valueOf(12), restored.getCourseId());
        assertEquals(Integer.valueOf(4), restored.getSubjectId());
        assertEquals("Algebra", restored.getTopic());
        assertEquals(DifficultyLevel.HARD, restored.getDifficulty());
        assertEquals(QuestionStatus.ACTIVE, restored.getStatus());

        QuestionFilterPayload empty = roundTrip(new QuestionFilterPayload(null, null, null, null, null));
        assertNull(empty.getCourseId());
        assertNull(empty.getSubjectId());
        assertNull(empty.getTopic());
        assertNull(empty.getDifficulty());
        assertNull(empty.getStatus());
        assertHasNoClientIdentityFields(QuestionFilterPayload.class);
    }

    @Test
    public void questionIdPayloadMapsAndSerializes() throws Exception {
        QuestionIdPayload restored = roundTrip(new QuestionIdPayload(42));

        assertEquals(42, restored.getQuestionId());
    }

    @Test
    public void questionVersionMapsAndSerializesEveryField() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 27, 9, 30, 15);
        QuestionVersionDTO restored = roundTrip(new QuestionVersionDTO(
                3, 7, 12, "Content", "Algebra", QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.HARD, "image.png", "One", "Two", "Three", "Four",
                4, 1002, createdAt
        ));

        assertEquals(3, restored.getQuestionId());
        assertEquals(7, restored.getVersionNo());
        assertEquals(12, restored.getCourseId());
        assertEquals("Content", restored.getContent());
        assertEquals("Algebra", restored.getTopic());
        assertEquals(QuestionType.MULTIPLE_CHOICE, restored.getType());
        assertEquals(DifficultyLevel.HARD, restored.getDifficulty());
        assertEquals("image.png", restored.getIllustrationPath());
        assertEquals("One", restored.getAnswerOption1());
        assertEquals("Two", restored.getAnswerOption2());
        assertEquals("Three", restored.getAnswerOption3());
        assertEquals("Four", restored.getAnswerOption4());
        assertEquals(4, restored.getCorrectOptionNumber());
        assertEquals(1002, restored.getCreatedByUserId());
        assertEquals(createdAt, restored.getCreatedAt());
    }

    private static void assertHasNoClientIdentityFields(Class<?> type) {
        assertFalse(Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .anyMatch(name -> name.equals("teacherId") || name.equals("userId")));
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
}
