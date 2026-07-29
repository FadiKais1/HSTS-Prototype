package hsts.server.entity;

import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class Question {
    private int questionId;
    private String content;
    private QuestionType type;
    private DifficultyLevel difficulty;
    private QuestionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String topic;

    // RELATIONSHIP-DERIVED: Question contains four answer options.
    private final List<AnswerOption> answerOptions;

    // RELATIONSHIP-DERIVED: Question is answered by zero or more student answers.
    private final List<StudentAnswer> studentAnswers;

    // COMPATIBILITY-ONLY: Retained for the working prototype until repository
    // hydration moves to the typed entity path.
    private String illustrationPath;
    private QuestionIllustration illustration;

    public Question(int questionId, String content, String topic, String type,
                    String difficulty, String status, String illustrationPath,
                    String answerOption1, String answerOption2,
                    String answerOption3, String answerOption4,
                    int correctOptionNumber) {
        this(
                questionId,
                requireText(content, "Question content is required"),
                parseEnum(type, QuestionType.class, "Question type"),
                parseEnum(difficulty, DifficultyLevel.class, "Question difficulty"),
                parseEnum(status, QuestionStatus.class, "Question status"),
                null,
                null,
                requireText(topic, "Question topic is required"),
                illustrationPath,
                legacyOptions(
                        answerOption1,
                        answerOption2,
                        answerOption3,
                        answerOption4,
                        correctOptionNumber
                )
        );
    }

    private Question(int questionId, String content, QuestionType type,
                     DifficultyLevel difficulty, QuestionStatus status,
                     LocalDateTime createdAt, LocalDateTime updatedAt,
                     String topic, String illustrationPath,
                     List<AnswerOption> answerOptions) {
        this.questionId = questionId;
        this.content = requireText(content, "Question content is required");
        this.type = requireValue(type, "Question type is required");
        this.difficulty = requireValue(
                difficulty,
                "Question difficulty is required"
        );
        this.status = requireValue(status, "Question status is required");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.topic = requireText(topic, "Question topic is required");
        this.illustrationPath = illustrationPath;
        this.illustration = null;
        this.answerOptions = copyAndValidateOptions(answerOptions);
        this.studentAnswers = new ArrayList<>();
    }

    public static Question rehydrate(
            int questionId,
            String content,
            QuestionType type,
            DifficultyLevel difficulty,
            QuestionStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String topic,
            String illustrationPath,
            List<AnswerOption> answerOptions
    ) {
        return new Question(
                questionId,
                content,
                type,
                difficulty,
                status,
                createdAt,
                updatedAt,
                topic,
                illustrationPath,
                answerOptions
        );
    }

    public static Question rehydrate(
            int questionId, String content, QuestionType type,
            DifficultyLevel difficulty, QuestionStatus status,
            LocalDateTime createdAt, LocalDateTime updatedAt, String topic,
            String illustrationPath, QuestionIllustration illustration,
            List<AnswerOption> answerOptions
    ) {
        Question question = rehydrate(
                questionId, content, type, difficulty, status, createdAt,
                updatedAt, topic, illustrationPath, answerOptions
        );
        question.illustration = illustration == null ? null : illustration.copy();
        return question;
    }

    public int getQuestionId() {
        return questionId;
    }

    // COMPATIBILITY-ONLY: Retained until legacy repository callers migrate.
    public void setQuestionId(int questionId) {
        this.questionId = questionId;
    }

    public String getContent() {
        return content;
    }

    // COMPATIBILITY-ONLY: Delegates to the validated domain operation.
    public void setContent(String content) {
        updateContent(content);
    }

    public String getTopic() {
        return topic;
    }

    // COMPATIBILITY-ONLY: Retained for the working prototype.
    public void setTopic(String topic) {
        String normalized = requireText(topic, "Question topic is required");
        if (!this.topic.equals(normalized)) {
            this.topic = normalized;
            touch();
        }
    }

    // COMPATIBILITY-ONLY: String view required by current repository and DTO mapping.
    public String getType() {
        return type.name();
    }

    // COMPATIBILITY-ONLY: String input required by current callers.
    public void setType(String type) {
        QuestionType parsed = parseEnum(type, QuestionType.class, "Question type");
        if (this.type != parsed) {
            this.type = parsed;
            touch();
        }
    }

    public QuestionType getQuestionType() {
        return type;
    }

    // COMPATIBILITY-ONLY: String view required by current repository and DTO mapping.
    public String getDifficulty() {
        return difficulty.name();
    }

    // COMPATIBILITY-ONLY: String input required by current callers.
    public void setDifficulty(String difficulty) {
        DifficultyLevel parsed = parseEnum(
                difficulty,
                DifficultyLevel.class,
                "Question difficulty"
        );
        if (this.difficulty != parsed) {
            this.difficulty = parsed;
            touch();
        }
    }

    public DifficultyLevel getDifficultyLevel() {
        return difficulty;
    }

    // COMPATIBILITY-ONLY: String view required by current repository and DTO mapping.
    public String getStatus() {
        return status.name();
    }

    // COMPATIBILITY-ONLY: String input required by current callers.
    public void setStatus(String status) {
        QuestionStatus parsed = parseEnum(
                status,
                QuestionStatus.class,
                "Question status"
        );
        if (this.status != parsed) {
            this.status = parsed;
            touch();
        }
    }

    public QuestionStatus getQuestionStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getIllustrationPath() {
        return illustrationPath;
    }

    // COMPATIBILITY-ONLY: Retained for the working prototype.
    public void setIllustrationPath(String illustrationPath) {
        if (!Objects.equals(this.illustrationPath, illustrationPath)) {
            this.illustrationPath = illustrationPath;
            touch();
        }
    }

    public QuestionIllustration getIllustration() {
        return illustration == null ? null : illustration.copy();
    }

    public void setIllustration(QuestionIllustration illustration) {
        this.illustration = illustration == null ? null : illustration.copy();
        touch();
    }

    // COMPATIBILITY-ONLY: Flattened views delegate to the authoritative collection.
    public String getAnswerOption1() {
        return getLegacyOptionText(1);
    }

    public void setAnswerOption1(String answerOption1) {
        setLegacyOptionText(1, answerOption1);
    }

    public String getAnswerOption2() {
        return getLegacyOptionText(2);
    }

    public void setAnswerOption2(String answerOption2) {
        setLegacyOptionText(2, answerOption2);
    }

    public String getAnswerOption3() {
        return getLegacyOptionText(3);
    }

    public void setAnswerOption3(String answerOption3) {
        setLegacyOptionText(3, answerOption3);
    }

    public String getAnswerOption4() {
        return getLegacyOptionText(4);
    }

    public void setAnswerOption4(String answerOption4) {
        setLegacyOptionText(4, answerOption4);
    }

    public int getCorrectOptionNumber() {
        return answerOptions.stream()
                .filter(AnswerOption::isCorrect)
                .mapToInt(AnswerOption::getOptionId)
                .findFirst()
                .orElse(0);
    }

    public void setCorrectOptionNumber(int correctOptionNumber) {
        requireOptionNumber(correctOptionNumber);
        AnswerOption selected = findOption(correctOptionNumber);
        if (selected == null) {
            throw new IllegalArgumentException(
                    "Answer option not found: " + correctOptionNumber
            );
        }
        if (getCorrectOptionNumber() != correctOptionNumber) {
            for (AnswerOption option : answerOptions) {
                if (option.getOptionId() == correctOptionNumber) {
                    option.markAsCorrect();
                } else {
                    option.markAsIncorrect();
                }
            }
            touch();
        }
    }

    public List<AnswerOption> getAnswerOptions() {
        return answerOptions.stream()
                .map(AnswerOption::copy)
                .toList();
    }

    public List<StudentAnswer> getStudentAnswers() {
        return List.copyOf(studentAnswers);
    }

    public void updateContent(String content) {
        String normalized = requireText(content, "Question content is required");
        if (!this.content.equals(normalized)) {
            this.content = normalized;
            touch();
        }
    }

    public void activate() {
        if (status != QuestionStatus.ACTIVE) {
            status = QuestionStatus.ACTIVE;
            touch();
        }
    }

    public void deactivate() {
        if (status != QuestionStatus.INACTIVE) {
            status = QuestionStatus.INACTIVE;
            touch();
        }
    }

    public void addAnswerOption(AnswerOption option) {
        if (option == null) {
            throw new IllegalArgumentException("Answer option is required");
        }
        if (findOption(option.getOptionId()) != null) {
            throw new IllegalArgumentException(
                    "Answer option already exists: " + option.getOptionId()
            );
        }
        if (answerOptions.size() >= 4) {
            throw new IllegalStateException(
                    "Question cannot contain more than four answer options"
            );
        }
        if (option.isCorrect() && getCorrectOptionNumber() != 0) {
            throw new IllegalArgumentException(
                    "Question already has a correct answer"
            );
        }
        answerOptions.add(option.copy());
        answerOptions.sort(Comparator.comparingInt(AnswerOption::getOptionId));
        touch();
    }

    public void removeAnswerOption(int optionId) {
        requireOptionNumber(optionId);
        boolean removed = answerOptions.removeIf(
                option -> option.getOptionId() == optionId
        );
        if (!removed) {
            throw new IllegalArgumentException(
                    "Answer option not found: " + optionId
            );
        }
        touch();
    }

    public boolean isActive() {
        return status == QuestionStatus.ACTIVE;
    }

    private String getLegacyOptionText(int optionId) {
        AnswerOption option = findOption(optionId);
        return option == null ? null : option.getOptionText();
    }

    private void setLegacyOptionText(int optionId, String optionText) {
        AnswerOption option = findOption(optionId);
        if (option == null) {
            addAnswerOption(new AnswerOption(optionId, optionText, false));
            return;
        }
        String previousText = option.getOptionText();
        option.updateText(optionText);
        if (!previousText.equals(option.getOptionText())) {
            touch();
        }
    }

    private AnswerOption findOption(int optionId) {
        return answerOptions.stream()
                .filter(option -> option.getOptionId() == optionId)
                .findFirst()
                .orElse(null);
    }

    private void touch() {
        updatedAt = LocalDateTime.now();
    }

    private static List<AnswerOption> legacyOptions(
            String answerOption1,
            String answerOption2,
            String answerOption3,
            String answerOption4,
            int correctOptionNumber
    ) {
        requireOptionNumber(correctOptionNumber);
        return List.of(
                new AnswerOption(1, answerOption1, correctOptionNumber == 1),
                new AnswerOption(2, answerOption2, correctOptionNumber == 2),
                new AnswerOption(3, answerOption3, correctOptionNumber == 3),
                new AnswerOption(4, answerOption4, correctOptionNumber == 4)
        );
    }

    private static List<AnswerOption> copyAndValidateOptions(
            List<AnswerOption> options
    ) {
        if (options == null) {
            throw new IllegalArgumentException("Answer options are required");
        }
        if (options.size() > 4) {
            throw new IllegalArgumentException(
                    "Question cannot contain more than four answer options"
            );
        }

        List<AnswerOption> copies = new ArrayList<>(options.size());
        boolean[] identities = new boolean[5];
        int correctCount = 0;
        for (AnswerOption option : options) {
            if (option == null) {
                throw new IllegalArgumentException("Answer option is required");
            }
            int optionId = option.getOptionId();
            if (identities[optionId]) {
                throw new IllegalArgumentException(
                        "Answer option already exists: " + optionId
                );
            }
            identities[optionId] = true;
            if (option.isCorrect()) {
                correctCount++;
            }
            copies.add(option.copy());
        }
        if (correctCount > 1) {
            throw new IllegalArgumentException(
                    "Question cannot have more than one correct answer"
            );
        }
        copies.sort(Comparator.comparingInt(AnswerOption::getOptionId));
        return copies;
    }

    private static void requireOptionNumber(int optionNumber) {
        if (optionNumber < 1 || optionNumber > 4) {
            throw new IllegalArgumentException(
                    "Correct answer number must be between 1 and 4"
            );
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static <E extends Enum<E>> E parseEnum(
            String value,
            Class<E> enumType,
            String label
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(enumType, normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    label + " is invalid: " + value.trim(),
                    exception
            );
        }
    }
}
