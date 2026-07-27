package hsts.server.entity;

import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;

public class ExamQuestionAggregateTest {
    @Test
    public void constructionPreservesStableIdentityVersionOrderAndExactScore() {
        ExamQuestion selection = selection(7, 3, 2, "33.33");

        assertEquals(7, selection.getExamQuestionId());
        assertEquals(7, selection.getQuestionId());
        assertEquals(3, selection.getQuestionVersionNo());
        assertEquals(2, selection.getOrderNumber());
        assertEquals(new BigDecimal("33.33"), selection.getScoreValue());
        assertEquals(33.33, selection.getScore(), 0.0);
    }

    @Test
    public void identityVersionOrderAndScoreMustBePositive() {
        Question question = question(7, "Original");

        assertThrows(IllegalArgumentException.class,
                () -> new ExamQuestion(0, 1, 1, BigDecimal.ONE, question));
        assertThrows(IllegalArgumentException.class,
                () -> new ExamQuestion(7, 0, 1, BigDecimal.ONE, question));
        assertThrows(IllegalArgumentException.class,
                () -> new ExamQuestion(7, 1, 0, BigDecimal.ONE, question));
        assertThrows(IllegalArgumentException.class,
                () -> new ExamQuestion(7, 1, 1, BigDecimal.ZERO, question));
        assertThrows(IllegalArgumentException.class,
                () -> new ExamQuestion(7, 1, 1, null, question));
    }

    @Test
    public void snapshotIsRequiredAndIdentityMustMatch() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExamQuestion(7, 1, 1, BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ExamQuestion(
                        8, 1, 1, BigDecimal.ONE, question(7, "Original")
                ));
    }

    @Test
    public void sourceQuestionMutationCannotChangeSelectionSnapshot() {
        Question source = question(7, "Original");
        ExamQuestion selection = new ExamQuestion(
                7, 2, 1, new BigDecimal("100.00"), source
        );

        source.updateContent("Changed");
        source.setAnswerOption1("Changed option");

        assertEquals("Original", selection.getQuestion().getContent());
        assertEquals("One", selection.getQuestion().getAnswerOption1());
    }

    @Test
    public void returnedQuestionIsAnotherDefensiveSnapshot() {
        ExamQuestion selection = selection(7, 2, 1, "100.00");
        Question first = selection.getQuestion();
        Question second = selection.getQuestion();

        assertNotSame(first, second);
        first.updateContent("Escaped mutation");
        first.setCorrectOptionNumber(2);

        assertEquals("Question 7", selection.getQuestion().getContent());
        assertEquals(1, selection.getQuestion().getCorrectOptionNumber());
    }

    @Test
    public void orderAndScoreHaveControlledValidatedUpdates() {
        ExamQuestion selection = selection(7, 2, 1, "40.00");

        selection.updateOrder(3);
        selection.updateScore(new BigDecimal("60.00"));

        assertEquals(3, selection.getOrderNumber());
        assertEquals(new BigDecimal("60.00"), selection.getScoreValue());
        assertThrows(IllegalArgumentException.class, () -> selection.updateOrder(0));
        assertThrows(IllegalArgumentException.class, () -> selection.updateScore(0.0));
        assertThrows(IllegalArgumentException.class,
                () -> selection.updateScore(Double.NaN));
    }

    static ExamQuestion selection(int questionId, int versionNo,
                                  int orderNumber, String score) {
        return new ExamQuestion(
                questionId,
                versionNo,
                orderNumber,
                new BigDecimal(score),
                question(questionId, "Question " + questionId)
        );
    }

    static Question question(int questionId, String content) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        return Question.rehydrate(
                questionId,
                content,
                QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.MEDIUM,
                QuestionStatus.ACTIVE,
                createdAt,
                createdAt,
                "Topic",
                "",
                List.of(
                        new AnswerOption(1, "One", true),
                        new AnswerOption(2, "Two", false),
                        new AnswerOption(3, "Three", false),
                        new AnswerOption(4, "Four", false)
                )
        );
    }
}
