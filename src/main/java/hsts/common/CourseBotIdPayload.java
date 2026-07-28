package hsts.common;

import java.io.Serializable;

public final class CourseBotIdPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int botId;

    public CourseBotIdPayload(int botId) {
        this.botId = BotContractSupport.requirePositive(botId, "Bot ID must be positive");
    }

    public int getBotId() { return botId; }
}
