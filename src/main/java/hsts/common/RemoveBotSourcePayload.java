package hsts.common;

import java.io.Serializable;

public final class RemoveBotSourcePayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int botId;
    private final int sourceId;

    public RemoveBotSourcePayload(int botId, int sourceId) {
        this.botId = BotContractSupport.requirePositive(botId, "Bot ID must be positive");
        this.sourceId = BotContractSupport.requirePositive(
                sourceId, "Source ID must be positive"
        );
    }

    public int getBotId() { return botId; }
    public int getSourceId() { return sourceId; }
}
