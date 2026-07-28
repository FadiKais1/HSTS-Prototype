package hsts.external;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BotProviderRequest {
    private final String providerSubjectId;
    private final String botName;
    private final String courseName;
    private final List<BotProviderSource> sources;
    private final List<BotProviderTurn> previousTurns;
    private final String questionText;

    public BotProviderRequest(String providerSubjectId, String botName,
                              String courseName, List<BotProviderSource> sources,
                              List<BotProviderTurn> previousTurns,
                              String questionText) {
        this.providerSubjectId = requireUuid(providerSubjectId);
        this.botName = requireText(botName, 255, "Bot name is required");
        this.courseName = requireText(courseName, 255, "Course name is required");
        this.sources = immutableList(
                sources, "Bot provider sources are required",
                "Bot provider sources cannot contain null"
        );
        if (this.sources.isEmpty()) {
            throw new IllegalArgumentException("At least one Bot source is required");
        }
        this.previousTurns = immutableList(
                previousTurns, "Previous Bot turns are required",
                "Previous Bot turns cannot contain null"
        );
        this.questionText = requireText(questionText, 10_000, "Bot question is required");
    }

    public String getProviderSubjectId() { return providerSubjectId; }
    public String getBotName() { return botName; }
    public String getCourseName() { return courseName; }
    public List<BotProviderSource> getSources() { return sources; }
    public List<BotProviderTurn> getPreviousTurns() { return previousTurns; }
    public String getQuestionText() { return questionText; }

    private static String requireUuid(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Provider subject ID must be a UUID");
        }
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Provider subject ID must be a UUID", exception);
        }
    }

    private static String requireText(String value, int maximum, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException("Bot provider request text is too long");
        }
        return normalized;
    }

    private static <T> List<T> immutableList(List<T> values, String nullMessage,
                                              String nullElementMessage) {
        if (values == null) {
            throw new IllegalArgumentException(nullMessage);
        }
        List<T> copy = new ArrayList<>(values.size());
        for (T value : values) {
            if (value == null) {
                throw new IllegalArgumentException(nullElementMessage);
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }
}
