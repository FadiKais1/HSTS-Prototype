package hsts.common;

import hsts.common.type.BotStatus;

import java.io.Serializable;

public final class UpdateCourseBotPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int botId;
    private final String botName;
    private final BotStatus status;

    public UpdateCourseBotPayload(int botId, String botName, BotStatus status) {
        this.botId = BotContractSupport.requirePositive(botId, "Bot ID must be positive");
        this.botName = BotContractSupport.requireNonBlank(botName, "Bot name is required");
        if (status == null) {
            throw new IllegalArgumentException("Bot status is required");
        }
        this.status = status;
    }

    public int getBotId() { return botId; }
    public String getBotName() { return botName; }
    public BotStatus getStatus() { return status; }
}
