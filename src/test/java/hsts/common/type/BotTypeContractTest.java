package hsts.common.type;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class BotTypeContractTest {
    @Test
    public void botEnumsHaveExactValuesAndOrder() {
        assertArrayEquals(new BotStatus[]{BotStatus.ACTIVE, BotStatus.INACTIVE},
                BotStatus.values());
        assertArrayEquals(new BotSourceType[]{
                        BotSourceType.QUESTION_BANK, BotSourceType.FREE_TEXT,
                        BotSourceType.TXT, BotSourceType.PDF, BotSourceType.DOCX
                }, BotSourceType.values());
        assertArrayEquals(new BotSourceStatus[]{
                        BotSourceStatus.ACTIVE, BotSourceStatus.REMOVED
                }, BotSourceStatus.values());
        assertArrayEquals(new BotAnswerStatus[]{
                        BotAnswerStatus.ANSWERED,
                        BotAnswerStatus.NO_SUITABLE_ANSWER
                }, BotAnswerStatus.values());
    }
}
