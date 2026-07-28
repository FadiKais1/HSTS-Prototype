package hsts.common;

import java.io.Serializable;

public final class AddBotTextSourcePayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int botId;
    private final String displayName;
    private final String text;

    public AddBotTextSourcePayload(int botId, String displayName, String text) {
        this.botId = BotContractSupport.requirePositive(botId, "Bot ID must be positive");
        this.displayName = BotContractSupport.requireNonBlank(
                displayName, "Source display name is required"
        );
        this.text = BotContractSupport.requireNonBlank(text, "Source text is required");
    }

    public int getBotId() { return botId; }
    public String getDisplayName() { return displayName; }
    public String getText() { return text; }
}
