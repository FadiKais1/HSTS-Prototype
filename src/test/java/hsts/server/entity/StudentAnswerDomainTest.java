package hsts.server.entity;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StudentAnswerDomainTest {
    private static final LocalDateTime CREATED =
            LocalDateTime.of(2026, 8, 1, 9, 5);

    @Test
    public void selectionPreservesExactSubmissionQuestionAndVersionIdentity() {
        StudentAnswer answer = StudentAnswer.select(501, 17, 4, 2, CREATED);

        assertEquals(0, answer.getAnswerId());
        assertEquals(501, answer.getSubmissionId());
        assertEquals(17, answer.getQuestionId());
        assertEquals(4, answer.getQuestionVersionNo());
        assertEquals(2, answer.getSelectedOptionId());
        assertEquals(CREATED, answer.getCreatedAt());
        assertEquals(CREATED, answer.getUpdatedAt());
        assertTrue(answer.getCorrectness().isEmpty());
        assertTrue(answer.getScoreReceivedValue().isEmpty());
        assertFalse(answer.isGraded());
    }

    @Test
    public void hydrationPreservesExactBigDecimalGradingState() {
        StudentAnswer answer = StudentAnswer.rehydrate(
                9001, 501, 17, 4, 2, null, true,
                new BigDecimal("33.33"), CREATED, CREATED.plusMinutes(30)
        );

        assertEquals(Boolean.TRUE, answer.getCorrectness().orElseThrow());
        assertEquals(0, new BigDecimal("33.33").compareTo(
                answer.getScoreReceivedValue().orElseThrow()
        ));
        assertEquals(33.33, answer.getScoreReceived(), 0.0);
    }

    @Test
    public void optionSelectionIsValidatedAndUpdatesOnlyOnRealChange() {
        StudentAnswer answer = StudentAnswer.select(501, 17, 4, 2, CREATED);
        LocalDateTime changed = CREATED.plusMinutes(1);

        answer.selectOption(3, changed);
        assertEquals(3, answer.getSelectedOptionNumber());
        assertEquals(changed, answer.getUpdatedAt());

        LocalDateTime unchangedAttempt = changed.plusMinutes(1);
        answer.selectOption(3, unchangedAttempt);
        assertEquals(changed, answer.getUpdatedAt());

        assertThrows(
                IllegalArgumentException.class,
                () -> StudentAnswer.select(501, 17, 4, 0, CREATED)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> answer.selectOption(5, changed)
        );
    }

    @Test
    public void gradingIsControlledExactAndMakesAnswerImmutable() {
        StudentAnswer answer = StudentAnswer.select(501, 17, 4, 2, CREATED);
        LocalDateTime gradedAt = CREATED.plusMinutes(30);

        answer.recordGrade(true, new BigDecimal("33.33"), gradedAt);

        assertTrue(answer.isGraded());
        assertTrue(answer.checkCorrectness());
        assertEquals(gradedAt, answer.getUpdatedAt());
        assertEquals(0, new BigDecimal("33.33").compareTo(
                answer.getScoreReceivedValue().orElseThrow()
        ));
        assertThrows(
                IllegalStateException.class,
                () -> answer.selectOption(4, gradedAt.plusSeconds(1))
        );
    }

    @Test
    public void gradingRejectsContradictoryOrInvalidScores() {
        StudentAnswer answer = StudentAnswer.select(501, 17, 4, 2, CREATED);

        assertThrows(
                IllegalArgumentException.class,
                () -> answer.recordGrade(
                        false,
                        new BigDecimal("1.00"),
                        CREATED.plusMinutes(1)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> answer.recordGrade(
                        true,
                        new BigDecimal("100.01"),
                        CREATED.plusMinutes(1)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> StudentAnswer.rehydrate(
                        1, 501, 17, 4, 2, null, true, null,
                        CREATED, CREATED
                )
        );
    }

    @Test
    public void multipleChoiceEntityDoesNotExposeCorrectOptionOrFreeTextMutation() {
        StudentAnswer answer = StudentAnswer.select(501, 17, 4, 2, CREATED);

        assertEquals(null, answer.getAnswerContent());
        assertThrows(
                IllegalArgumentException.class,
                () -> StudentAnswer.rehydrate(
                        1, 501, 17, 4, 2, "free text", null, null,
                        CREATED, CREATED
                )
        );
        assertThrows(IllegalStateException.class, () -> answer.updateAnswer(3));
        assertThrows(IllegalStateException.class, () -> answer.assignScore(5.0));
    }
}
