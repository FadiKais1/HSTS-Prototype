package hsts.server.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

public class StudentAnswer {
    private static final BigDecimal MAXIMUM_SCORE = new BigDecimal("100.00");

    private final int answerId;
    private final int submissionId;
    private final int questionId;
    private final int questionVersionNo;
    private final LocalDateTime createdAt;

    // COMPATIBILITY-ONLY: Retained from the Assignment 2 diagram. The current
    // execution flow supports multiple-choice answers only.
    private final String answerContent;

    private int selectedOptionId;
    private Boolean isCorrect;
    private BigDecimal scoreReceived;
    private LocalDateTime updatedAt;

    private StudentAnswer(int answerId, int submissionId, int questionId,
                          int questionVersionNo, int selectedOptionId,
                          String answerContent, Boolean isCorrect,
                          BigDecimal scoreReceived, LocalDateTime createdAt,
                          LocalDateTime updatedAt) {
        if (answerId < 0) {
            throw new IllegalArgumentException("Answer ID cannot be negative");
        }
        if (submissionId < 0) {
            throw new IllegalArgumentException("Submission ID cannot be negative");
        }
        if (questionId <= 0) {
            throw new IllegalArgumentException("Question ID must be positive");
        }
        if (questionVersionNo <= 0) {
            throw new IllegalArgumentException("Question version must be positive");
        }
        requireOptionNumber(selectedOptionId);
        if (answerContent != null && !answerContent.isBlank()) {
            throw new IllegalArgumentException(
                    "Free-text answers are not supported for multiple-choice questions"
            );
        }
        if ((isCorrect == null) != (scoreReceived == null)) {
            throw new IllegalArgumentException(
                    "Answer correctness and score must be recorded together"
            );
        }
        BigDecimal normalizedScore = normalizeScore(scoreReceived);
        if (Boolean.FALSE.equals(isCorrect)
                && normalizedScore != null
                && normalizedScore.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("Incorrect answers must receive zero score");
        }
        this.answerId = answerId;
        this.submissionId = submissionId;
        this.questionId = questionId;
        this.questionVersionNo = questionVersionNo;
        this.selectedOptionId = selectedOptionId;
        this.answerContent = answerContent == null || answerContent.isBlank()
                ? null
                : answerContent;
        this.isCorrect = isCorrect;
        this.scoreReceived = normalizedScore;
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Answer creation timestamp is required"
        );
        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "Answer update timestamp is required"
        );
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Answer update timestamp cannot precede creation"
            );
        }
    }

    public static StudentAnswer select(int submissionId, int questionId,
                                       int questionVersionNo,
                                       int selectedOptionId,
                                       LocalDateTime selectedAt) {
        return new StudentAnswer(
                0,
                submissionId,
                questionId,
                questionVersionNo,
                selectedOptionId,
                null,
                null,
                null,
                selectedAt,
                selectedAt
        );
    }

    public static StudentAnswer rehydrate(int answerId, int submissionId,
                                          int questionId, int questionVersionNo,
                                          int selectedOptionId,
                                          String answerContent,
                                          Boolean isCorrect,
                                          BigDecimal scoreReceived,
                                          LocalDateTime createdAt,
                                          LocalDateTime updatedAt) {
        if (answerId <= 0) {
            throw new IllegalArgumentException("Persisted answer ID must be positive");
        }
        if (submissionId <= 0) {
            throw new IllegalArgumentException("Persisted submission ID must be positive");
        }
        return new StudentAnswer(
                answerId,
                submissionId,
                questionId,
                questionVersionNo,
                selectedOptionId,
                answerContent,
                isCorrect,
                scoreReceived,
                createdAt,
                updatedAt
        );
    }

    public int getAnswerId() {
        return answerId;
    }

    public int getSubmissionId() {
        return submissionId;
    }

    public int getQuestionId() {
        return questionId;
    }

    public int getQuestionVersionNo() {
        return questionVersionNo;
    }

    public String getAnswerContent() {
        return answerContent;
    }

    public int getSelectedOptionId() {
        return selectedOptionId;
    }

    public int getSelectedOptionNumber() {
        return selectedOptionId;
    }

    public Optional<Boolean> getCorrectness() {
        return Optional.ofNullable(isCorrect);
    }

    public Optional<BigDecimal> getScoreReceivedValue() {
        return Optional.ofNullable(scoreReceived);
    }

    // COMPATIBILITY-ONLY: The diagram exposes score as double.
    public double getScoreReceived() {
        return scoreReceived == null ? 0.0 : scoreReceived.doubleValue();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isGraded() {
        return isCorrect != null;
    }

    void selectOption(int optionNumber, LocalDateTime changedAt) {
        requireOptionNumber(optionNumber);
        if (isGraded()) {
            throw new IllegalStateException("Graded answers cannot be changed");
        }
        LocalDateTime timestamp = requireMutationTimestamp(changedAt);
        if (selectedOptionId != optionNumber) {
            selectedOptionId = optionNumber;
            updatedAt = timestamp;
        }
    }

    void recordGrade(boolean correct, BigDecimal earnedScore,
                     LocalDateTime gradedAt) {
        if (isGraded()) {
            BigDecimal normalized = normalizeRequiredScore(earnedScore);
            if (isCorrect == correct && scoreReceived.compareTo(normalized) == 0) {
                return;
            }
            throw new IllegalStateException("Answer has already been graded");
        }
        BigDecimal normalized = normalizeRequiredScore(earnedScore);
        if (!correct && normalized.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("Incorrect answers must receive zero score");
        }
        LocalDateTime timestamp = requireMutationTimestamp(gradedAt);
        isCorrect = correct;
        scoreReceived = normalized;
        updatedAt = timestamp;
    }

    // COMPATIBILITY-ONLY: The diagram signature lacks the required server timestamp.
    public void updateAnswer(int answerChoice) {
        throw new IllegalStateException("Answer updates require a server timestamp");
    }

    // COMPATIBILITY-ONLY: Unknown correctness is represented by Optional on the
    // server; the diagram's primitive result can only report known correctness.
    public boolean checkCorrectness() {
        return Boolean.TRUE.equals(isCorrect);
    }

    // COMPATIBILITY-ONLY: Score alone cannot establish a trustworthy grading result.
    public void assignScore(double score) {
        throw new IllegalStateException(
                "Answer grading requires correctness and a server timestamp"
        );
    }

    StudentAnswer copy() {
        return new StudentAnswer(
                answerId,
                submissionId,
                questionId,
                questionVersionNo,
                selectedOptionId,
                answerContent,
                isCorrect,
                scoreReceived,
                createdAt,
                updatedAt
        );
    }

    private LocalDateTime requireMutationTimestamp(LocalDateTime timestamp) {
        LocalDateTime required = Objects.requireNonNull(
                timestamp,
                "Answer mutation timestamp is required"
        );
        if (required.isBefore(updatedAt)) {
            throw new IllegalArgumentException(
                    "Answer mutation timestamp cannot precede current state"
            );
        }
        return required;
    }

    private static void requireOptionNumber(int optionNumber) {
        if (optionNumber < 1 || optionNumber > 4) {
            throw new IllegalArgumentException("Answer option must be between 1 and 4");
        }
    }

    private static BigDecimal normalizeRequiredScore(BigDecimal score) {
        BigDecimal normalized = normalizeScore(score);
        if (normalized == null) {
            throw new IllegalArgumentException("Answer score is required");
        }
        return normalized;
    }

    private static BigDecimal normalizeScore(BigDecimal score) {
        if (score == null) {
            return null;
        }
        if (score.compareTo(BigDecimal.ZERO) < 0
                || score.compareTo(MAXIMUM_SCORE) > 0) {
            throw new IllegalArgumentException("Answer score must be between 0 and 100");
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }
}
