package hsts.server.entity;

public class AnswerOption {
    // COMPATIBILITY-ONLY: This is the diagram identity and is interpreted as the
    // normalized option number (1-4), not as a standalone database-generated ID.
    private final int optionId;
    private String optionText;
    private boolean isCorrect;

    public AnswerOption(int optionId, String optionText, boolean isCorrect) {
        if (optionId < 1 || optionId > 4) {
            throw new IllegalArgumentException(
                    "Answer option number must be between 1 and 4"
            );
        }
        this.optionId = optionId;
        this.optionText = requireText(optionText);
        this.isCorrect = isCorrect;
    }

    public int getOptionId() {
        return optionId;
    }

    public String getOptionText() {
        return optionText;
    }

    public boolean isCorrect() {
        return isCorrect;
    }

    public void markAsCorrect() {
        isCorrect = true;
    }

    public void markAsIncorrect() {
        isCorrect = false;
    }

    public void updateText(String optionText) {
        this.optionText = requireText(optionText);
    }

    AnswerOption copy() {
        return new AnswerOption(optionId, optionText, isCorrect);
    }

    private static String requireText(String optionText) {
        if (optionText == null || optionText.trim().isEmpty()) {
            throw new IllegalArgumentException("Answer option text is required");
        }
        return optionText.trim();
    }
}
