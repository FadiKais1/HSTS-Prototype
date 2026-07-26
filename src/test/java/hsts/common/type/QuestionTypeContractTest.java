package hsts.common.type;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class QuestionTypeContractTest {
    @Test
    public void difficultyLevelHasExactValues() {
        assertArrayEquals(
                new DifficultyLevel[]{DifficultyLevel.EASY, DifficultyLevel.MEDIUM, DifficultyLevel.HARD},
                DifficultyLevel.values()
        );
    }

    @Test
    public void questionStatusHasExactValues() {
        assertArrayEquals(
                new QuestionStatus[]{QuestionStatus.ACTIVE, QuestionStatus.INACTIVE},
                QuestionStatus.values()
        );
    }

    @Test
    public void questionTypeHasExactValues() {
        assertArrayEquals(
                new QuestionType[]{QuestionType.MULTIPLE_CHOICE},
                QuestionType.values()
        );
    }
}
