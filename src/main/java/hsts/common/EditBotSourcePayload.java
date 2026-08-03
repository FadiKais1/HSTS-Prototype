package hsts.common;

import java.io.Serializable;

/**
 * Replaces the content of an existing Course Bot source.
 *
 * <p>Editing removes the current source and adds a new one carrying the revised
 * text, so the record shows who wrote each version and the original remains
 * recoverable. The bot uses only active sources, so the change takes effect at
 * once.</p>
 */
public final class EditBotSourcePayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int botId;
    private final int sourceId;
    private final String displayName;
    private final String text;

    public EditBotSourcePayload(int botId, int sourceId,
                                String displayName, String text) {
        this.botId = botId;
        this.sourceId = sourceId;
        this.displayName = displayName;
        this.text = text;
    }

    public int getBotId() {
        return botId;
    }

    /** The source being replaced, as shown in the management list. */
    public int getSourceId() {
        return sourceId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getText() {
        return text;
    }
}
