package hsts.server.entity;

import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class QuestionDomainTest {
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 1, 2, 3, 4, 5);
    private static final LocalDateTime UPDATED_AT =
            LocalDateTime.of(2026, 1, 3, 3, 4, 5);

    @Test
    public void legacyConstructorNormalizesTypedStateAndFlattenedOptions() {
        Question question = new Question(
                7,
                "  What is seven?  ",
                "  Arithmetic  ",
                "  multiple_choice  ",
                "  hard  ",
                "  active  ",
                "image.png",
                "  One  ",
                "  Two  ",
                "  Three  ",
                "  Four  ",
                3
        );

        assertEquals("What is seven?", question.getContent());
        assertEquals("Arithmetic", question.getTopic());
        assertEquals("MULTIPLE_CHOICE", question.getType());
        assertEquals(QuestionType.MULTIPLE_CHOICE, question.getQuestionType());
        assertEquals("HARD", question.getDifficulty());
        assertEquals(DifficultyLevel.HARD, question.getDifficultyLevel());
        assertEquals("ACTIVE", question.getStatus());
        assertEquals(QuestionStatus.ACTIVE, question.getQuestionStatus());
        assertEquals("One", question.getAnswerOption1());
        assertEquals("Two", question.getAnswerOption2());
        assertEquals("Three", question.getAnswerOption3());
        assertEquals("Four", question.getAnswerOption4());
        assertEquals(3, question.getCorrectOptionNumber());
        assertEquals(List.of(1, 2, 3, 4), optionIds(question));
        assertNull(question.getCreatedAt());
        assertNull(question.getUpdatedAt());
    }

    @Test
    public void legacyConstructorRejectsMissingAndUnknownEnumText() {
        assertThrows(
                IllegalArgumentException.class,
                () -> legacyQuestion(null, "EASY", "ACTIVE")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> legacyQuestion("  ", "EASY", "ACTIVE")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> legacyQuestion("ESSAY", "EASY", "ACTIVE")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> legacyQuestion("MULTIPLE_CHOICE", null, "ACTIVE")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> legacyQuestion("MULTIPLE_CHOICE", "UNKNOWN", "ACTIVE")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> legacyQuestion("MULTIPLE_CHOICE", "EASY", "ARCHIVED")
        );
    }

    @Test
    public void legacyStringSettersUpdateTheSameTypedState() {
        Question question = legacyQuestion("MULTIPLE_CHOICE", "EASY", "ACTIVE");

        question.setType(" multiple_choice ");
        question.setDifficulty(" medium ");
        question.setStatus(" inactive ");

        assertSame(QuestionType.MULTIPLE_CHOICE, question.getQuestionType());
        assertEquals("MULTIPLE_CHOICE", question.getType());
        assertSame(DifficultyLevel.MEDIUM, question.getDifficultyLevel());
        assertEquals("MEDIUM", question.getDifficulty());
        assertSame(QuestionStatus.INACTIVE, question.getQuestionStatus());
        assertEquals("INACTIVE", question.getStatus());
        assertThrows(
                IllegalArgumentException.class,
                () -> question.setStatus("ARCHIVED")
        );
    }

    @Test
    public void updateContentValidatesTrimsAndPreservesCreationTime() {
        Question question = hydratedQuestion(defaultOptions());

        question.updateContent("  Updated content  ");

        assertEquals("Updated content", question.getContent());
        assertEquals(CREATED_AT, question.getCreatedAt());
        assertTrue(question.getUpdatedAt().isAfter(UPDATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> question.updateContent(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> question.updateContent("   ")
        );
    }

    @Test
    public void statusOperationsUseAuthoritativeStateAndPreserveCreationTime() {
        Question question = hydratedQuestion(defaultOptions());

        assertTrue(question.isActive());
        question.deactivate();
        assertFalse(question.isActive());
        assertSame(QuestionStatus.INACTIVE, question.getQuestionStatus());
        assertEquals("INACTIVE", question.getStatus());
        LocalDateTime deactivatedAt = question.getUpdatedAt();

        question.activate();
        assertTrue(question.isActive());
        assertSame(QuestionStatus.ACTIVE, question.getQuestionStatus());
        assertEquals("ACTIVE", question.getStatus());
        assertTrue(!question.getUpdatedAt().isBefore(deactivatedAt));
        assertEquals(CREATED_AT, question.getCreatedAt());
    }

    @Test
    public void hydrationOrdersAndDefensivelyCopiesOptions() {
        AnswerOption third = new AnswerOption(3, "Three", true);
        List<AnswerOption> supplied = new ArrayList<>(List.of(
                third,
                new AnswerOption(1, "One", false),
                new AnswerOption(2, "Two", false)
        ));

        Question question = hydratedQuestion(supplied);
        supplied.clear();
        third.updateText("Changed outside");
        List<AnswerOption> returned = question.getAnswerOptions();

        assertEquals(List.of(1, 2, 3), optionIds(question));
        assertEquals("Three", question.getAnswerOption3());
        assertThrows(
                UnsupportedOperationException.class,
                () -> returned.add(new AnswerOption(4, "Four", false))
        );
        returned.get(0).updateText("Changed returned copy");
        assertEquals("One", question.getAnswerOption1());
    }

    @Test
    public void hydrationRejectsNullDuplicateAndContradictoryOptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> hydratedQuestion(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> hydratedQuestion(List.of(
                        new AnswerOption(1, "First", false),
                        new AnswerOption(1, "Duplicate", false)
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> hydratedQuestion(List.of(
                        new AnswerOption(1, "First", true),
                        new AnswerOption(2, "Second", true)
                ))
        );
    }

    @Test
    public void domainOptionMutationsKeepLegacyViewsSynchronized() {
        Question question = hydratedQuestion(new ArrayList<>(List.of(
                new AnswerOption(1, "One", true),
                new AnswerOption(3, "Three", false)
        )));

        question.addAnswerOption(new AnswerOption(2, "  Two  ", false));
        assertEquals(List.of(1, 2, 3), optionIds(question));
        assertEquals("Two", question.getAnswerOption2());

        question.setAnswerOption2("  Updated two  ");
        assertEquals("Updated two", question.getAnswerOption2());
        assertEquals("Updated two", question.getAnswerOptions().get(1).getOptionText());

        question.setCorrectOptionNumber(2);
        assertEquals(2, question.getCorrectOptionNumber());
        assertFalse(question.getAnswerOptions().get(0).isCorrect());
        assertTrue(question.getAnswerOptions().get(1).isCorrect());

        question.removeAnswerOption(3);
        assertNull(question.getAnswerOption3());
        assertThrows(
                IllegalArgumentException.class,
                () -> question.removeAnswerOption(3)
        );
    }

    @Test
    public void optionMutationRejectsNullDuplicatesAndMoreThanFourOptions() {
        Question question = hydratedQuestion(new ArrayList<>());

        assertThrows(
                IllegalArgumentException.class,
                () -> question.addAnswerOption(null)
        );
        question.addAnswerOption(new AnswerOption(1, "One", true));
        assertThrows(
                IllegalArgumentException.class,
                () -> question.addAnswerOption(new AnswerOption(1, "Again", false))
        );
        question.addAnswerOption(new AnswerOption(2, "Two", false));
        question.addAnswerOption(new AnswerOption(3, "Three", false));
        question.addAnswerOption(new AnswerOption(4, "Four", false));

        assertEquals(4, question.getAnswerOptions().size());
    }

    private static Question legacyQuestion(String type, String difficulty, String status) {
        return new Question(
                11,
                "Content",
                "Topic",
                type,
                difficulty,
                status,
                "",
                "One",
                "Two",
                "Three",
                "Four",
                1
        );
    }

    private static Question hydratedQuestion(List<AnswerOption> options) {
        return Question.rehydrate(
                11,
                "Content",
                QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.EASY,
                QuestionStatus.ACTIVE,
                CREATED_AT,
                UPDATED_AT,
                "Topic",
                "",
                options
        );
    }

    private static List<AnswerOption> defaultOptions() {
        return List.of(
                new AnswerOption(1, "One", true),
                new AnswerOption(2, "Two", false),
                new AnswerOption(3, "Three", false),
                new AnswerOption(4, "Four", false)
        );
    }

    private static List<Integer> optionIds(Question question) {
        return question.getAnswerOptions().stream()
                .map(AnswerOption::getOptionId)
                .toList();
    }
}
