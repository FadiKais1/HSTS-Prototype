package hsts.server.entity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AnswerOptionTest {
    @Test
    public void constructorAndUpdateTextValidateAndTrimText() {
        AnswerOption option = new AnswerOption(2, "  Second answer  ", false);

        assertEquals(2, option.getOptionId());
        assertEquals("Second answer", option.getOptionText());
        assertFalse(option.isCorrect());

        option.updateText("  Updated answer  ");

        assertEquals("Updated answer", option.getOptionText());
        assertThrows(
                IllegalArgumentException.class,
                () -> option.updateText("  ")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnswerOption(1, null, false)
        );
    }

    @Test
    public void correctnessOperationsPreserveStableIdentity() {
        AnswerOption option = new AnswerOption(3, "Answer", false);

        option.markAsCorrect();
        assertTrue(option.isCorrect());
        assertEquals(3, option.getOptionId());

        option.markAsIncorrect();
        assertFalse(option.isCorrect());
        assertEquals(3, option.getOptionId());
    }

    @Test
    public void optionNumberMustMatchTheFourOptionDomain() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnswerOption(0, "Answer", false)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnswerOption(5, "Answer", false)
        );
    }
}
