package hsts.server.entity;

import hsts.common.type.SubmissionStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ExamSubmission {
    private static final BigDecimal MAXIMUM_SCORE = new BigDecimal("100.00");

    private final int submissionId;
    private final int executionId;
    private final int examId;
    private final int examVersionNo;
    private final int studentUserId;
    private final LocalDateTime startedAt;
    private final int allocatedDurationMinutes;
    private final LocalDateTime createdAt;

    private LocalDateTime submittedAt;
    private SubmissionStatus status;
    private int extraMinutes;
    private String extensionReason;
    private Integer actualDurationMinutes;
    private BigDecimal automaticScore;
    private BigDecimal finalScore;
    private String teacherFeedback;
    private String manualChangeReason;
    private Integer reviewedByUserId;
    private LocalDateTime reviewedAt;
    private Integer publishedByUserId;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;

    // RELATIONSHIP-DERIVED: A submission contains zero or more student answers.
    private final List<StudentAnswer> studentAnswers;

    // COMPATIBILITY-ONLY: Required by the existing User hierarchy relationship test.
    public ExamSubmission() {
        this.submissionId = 0;
        this.executionId = 0;
        this.examId = 0;
        this.examVersionNo = 0;
        this.studentUserId = 0;
        this.startedAt = null;
        this.allocatedDurationMinutes = 0;
        this.createdAt = null;
        this.status = SubmissionStatus.IN_PROGRESS;
        this.updatedAt = null;
        this.studentAnswers = new ArrayList<>();
    }

    private ExamSubmission(int submissionId, int executionId, int examId,
                           int examVersionNo, int studentUserId,
                           LocalDateTime startedAt, LocalDateTime submittedAt,
                           SubmissionStatus status,
                           int allocatedDurationMinutes, int extraMinutes,
                           String extensionReason,
                           Integer actualDurationMinutes,
                           BigDecimal automaticScore, BigDecimal finalScore,
                           String teacherFeedback, String manualChangeReason,
                           Integer reviewedByUserId, LocalDateTime reviewedAt,
                           Integer publishedByUserId, LocalDateTime publishedAt,
                           LocalDateTime createdAt, LocalDateTime updatedAt,
                           List<StudentAnswer> studentAnswers) {
        if (submissionId < 0) {
            throw new IllegalArgumentException("Submission ID cannot be negative");
        }
        requirePositive(executionId, "Execution ID must be positive");
        requirePositive(examId, "Exam ID must be positive");
        requirePositive(examVersionNo, "Exam version must be positive");
        requirePositive(studentUserId, "Student user ID must be positive");
        if (allocatedDurationMinutes <= 0) {
            throw new IllegalArgumentException("Allocated duration must be positive");
        }
        if (extraMinutes < 0) {
            throw new IllegalArgumentException("Extra minutes cannot be negative");
        }
        if (actualDurationMinutes != null && actualDurationMinutes < 0) {
            throw new IllegalArgumentException("Actual duration cannot be negative");
        }
        this.submissionId = submissionId;
        this.executionId = executionId;
        this.examId = examId;
        this.examVersionNo = examVersionNo;
        this.studentUserId = studentUserId;
        this.startedAt = Objects.requireNonNull(
                startedAt,
                "Submission start timestamp is required"
        );
        this.submittedAt = submittedAt;
        this.status = Objects.requireNonNull(status, "Submission status is required");
        this.allocatedDurationMinutes = allocatedDurationMinutes;
        this.extraMinutes = extraMinutes;
        this.extensionReason = normalizeOptionalText(extensionReason);
        this.actualDurationMinutes = actualDurationMinutes;
        this.automaticScore = normalizeScore(automaticScore);
        this.finalScore = normalizeScore(finalScore);
        this.teacherFeedback = normalizeOptionalText(teacherFeedback);
        this.manualChangeReason = normalizeOptionalText(manualChangeReason);
        this.reviewedByUserId = reviewedByUserId;
        this.reviewedAt = reviewedAt;
        this.publishedByUserId = publishedByUserId;
        this.publishedAt = publishedAt;
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Submission creation timestamp is required"
        );
        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "Submission update timestamp is required"
        );
        if (createdAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "Submission creation cannot precede its start"
            );
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Submission update timestamp cannot precede creation"
            );
        }
        this.studentAnswers = copyAndValidateAnswers(studentAnswers, submissionId);
        validateLifecycleState();
    }

    public static ExamSubmission start(int executionId, int examId,
                                       int examVersionNo, int studentUserId,
                                       LocalDateTime startedAt,
                                       int allocatedDurationMinutes) {
        return new ExamSubmission(
                0,
                executionId,
                examId,
                examVersionNo,
                studentUserId,
                startedAt,
                null,
                SubmissionStatus.IN_PROGRESS,
                allocatedDurationMinutes,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                startedAt,
                startedAt,
                List.of()
        );
    }

    public static ExamSubmission rehydrate(
            int submissionId,
            int executionId,
            int examId,
            int examVersionNo,
            int studentUserId,
            LocalDateTime startedAt,
            LocalDateTime submittedAt,
            SubmissionStatus status,
            int allocatedDurationMinutes,
            int extraMinutes,
            String extensionReason,
            Integer actualDurationMinutes,
            BigDecimal automaticScore,
            BigDecimal finalScore,
            String teacherFeedback,
            String manualChangeReason,
            Integer reviewedByUserId,
            LocalDateTime reviewedAt,
            Integer publishedByUserId,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<StudentAnswer> studentAnswers
    ) {
        if (submissionId <= 0) {
            throw new IllegalArgumentException("Persisted submission ID must be positive");
        }
        return new ExamSubmission(
                submissionId,
                executionId,
                examId,
                examVersionNo,
                studentUserId,
                startedAt,
                submittedAt,
                status,
                allocatedDurationMinutes,
                extraMinutes,
                extensionReason,
                actualDurationMinutes,
                automaticScore,
                finalScore,
                teacherFeedback,
                manualChangeReason,
                reviewedByUserId,
                reviewedAt,
                publishedByUserId,
                publishedAt,
                createdAt,
                updatedAt,
                studentAnswers
        );
    }

    public int getSubmissionId() { return submissionId; }
    public int getExecutionId() { return executionId; }
    public int getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public int getStudentUserId() { return studentUserId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public SubmissionStatus getStatus() { return status; }
    public int getAllocatedDurationMinutes() { return allocatedDurationMinutes; }
    public int getExtraMinutes() { return extraMinutes; }
    public String getExtensionReason() { return extensionReason; }
    public Integer getActualDurationMinutes() { return actualDurationMinutes; }
    public String getTeacherFeedback() { return teacherFeedback; }
    public String getManualChangeReason() { return manualChangeReason; }
    public Integer getReviewedByUserId() { return reviewedByUserId; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public Integer getPublishedByUserId() { return publishedByUserId; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public LocalDateTime getOriginalDeadline() {
        requireInitialized();
        return startedAt.plusMinutes(allocatedDurationMinutes);
    }

    public LocalDateTime getEffectiveDeadline() {
        return getOriginalDeadline().plusMinutes(extraMinutes);
    }

    public List<StudentAnswer> getStudentAnswers() {
        return studentAnswers.stream().map(StudentAnswer::copy).toList();
    }

    public boolean isEditable(LocalDateTime serverTime) {
        requireInitialized();
        LocalDateTime now = Objects.requireNonNull(serverTime, "Server time is required");
        return status == SubmissionStatus.IN_PROGRESS
                && now.isBefore(getEffectiveDeadline());
    }

    public void saveAnswer(int questionId, int questionVersionNo,
                           int selectedOptionNumber, LocalDateTime savedAt) {
        requireEditableAt(savedAt);
        StudentAnswer existing = findAnswer(questionId);
        if (existing != null) {
            if (existing.getQuestionVersionNo() != questionVersionNo) {
                throw new IllegalArgumentException(
                        "Question answer version does not match the submission"
                );
            }
            int previousOption = existing.getSelectedOptionId();
            existing.selectOption(selectedOptionNumber, savedAt);
            if (previousOption != existing.getSelectedOptionId()) {
                touch(savedAt);
            }
            return;
        }
        studentAnswers.add(StudentAnswer.select(
                submissionId,
                questionId,
                questionVersionNo,
                selectedOptionNumber,
                savedAt
        ));
        sortAnswers();
        touch(savedAt);
    }

    public void saveAnswer(StudentAnswer answer, LocalDateTime savedAt) {
        Objects.requireNonNull(answer, "Student answer is required");
        if (answer.getSubmissionId() != submissionId) {
            throw new IllegalArgumentException(
                    "Student answer belongs to a different submission"
            );
        }
        saveAnswer(
                answer.getQuestionId(),
                answer.getQuestionVersionNo(),
                answer.getSelectedOptionId(),
                savedAt
        );
    }

    public void extendTime(int addedMinutes, String reason,
                           LocalDateTime extendedAt) {
        requireEditableAt(extendedAt);
        if (addedMinutes <= 0) {
            throw new IllegalArgumentException("Extra minutes must be positive");
        }
        String normalizedReason = requireText(reason, "Extension reason is required");
        extraMinutes = Math.addExact(extraMinutes, addedMinutes);
        extensionReason = normalizedReason;
        touch(extendedAt);
    }

    public SubmissionStatus submitManually(LocalDateTime submittedAt) {
        requireInProgress();
        LocalDateTime timestamp = requireMutationTimestamp(submittedAt);
        SubmissionStatus finalStatus = timestamp.isAfter(getEffectiveDeadline())
                ? SubmissionStatus.AUTO_SUBMITTED
                : SubmissionStatus.SUBMITTED;
        finalizeAt(finalStatus, timestamp);
        return status;
    }

    public boolean autoSubmit(LocalDateTime serverTime) {
        requireInitialized();
        LocalDateTime timestamp = Objects.requireNonNull(
                serverTime,
                "Server time is required"
        );
        if (status != SubmissionStatus.IN_PROGRESS
                || getEffectiveDeadline().isAfter(timestamp)) {
            return false;
        }
        requireMutationTimestamp(timestamp);
        finalizeAt(SubmissionStatus.AUTO_SUBMITTED, timestamp);
        return true;
    }

    public void recordAnswerGrade(int questionId, boolean correct,
                                  BigDecimal earnedScore,
                                  LocalDateTime gradedAt) {
        requireFinalizedForGrading();
        StudentAnswer answer = findAnswer(questionId);
        if (answer == null) {
            throw new IllegalArgumentException("Student answer not found: " + questionId);
        }
        LocalDateTime timestamp = requireMutationTimestamp(gradedAt);
        boolean wasGraded = answer.isGraded();
        answer.recordGrade(correct, earnedScore, timestamp);
        if (!wasGraded) {
            touch(timestamp);
        }
    }

    public void recordAutomaticScore(BigDecimal score, LocalDateTime gradedAt) {
        requireFinalizedForGrading();
        BigDecimal normalized = normalizeRequiredScore(score);
        LocalDateTime timestamp = requireMutationTimestamp(gradedAt);
        if (!Objects.equals(automaticScore, normalized)
                || reviewedAt == null && !Objects.equals(finalScore, normalized)) {
            automaticScore = normalized;
            if (reviewedAt == null) {
                finalScore = normalized;
            }
            touch(timestamp);
        }
    }

    public void recordTeacherReview(int reviewerUserId, BigDecimal reviewedScore,
                                    String feedback, String changeReason,
                                    LocalDateTime reviewTime) {
        requireFinalizedForGrading();
        requirePositive(reviewerUserId, "Reviewer user ID must be positive");
        if (automaticScore == null) {
            throw new IllegalStateException("Automatic grading must be recorded first");
        }
        BigDecimal normalizedScore = normalizeRequiredScore(reviewedScore);
        String normalizedReason = normalizeOptionalText(changeReason);
        if (normalizedScore.compareTo(automaticScore) != 0
                && normalizedReason == null) {
            throw new IllegalArgumentException(
                    "Manual score change reason is required"
            );
        }
        LocalDateTime timestamp = requireMutationTimestamp(reviewTime);
        finalScore = normalizedScore;
        teacherFeedback = normalizeOptionalText(feedback);
        manualChangeReason = normalizedReason;
        reviewedByUserId = reviewerUserId;
        reviewedAt = timestamp;
        touch(timestamp);
    }

    public void publish(int publisherUserId, LocalDateTime publicationTime) {
        if (status == SubmissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("In-progress submissions cannot be published");
        }
        if (status == SubmissionStatus.PUBLISHED) {
            return;
        }
        if (reviewedByUserId == null || reviewedAt == null || finalScore == null) {
            throw new IllegalStateException("Submission must be reviewed before publication");
        }
        requirePositive(publisherUserId, "Publisher user ID must be positive");
        LocalDateTime timestamp = requireMutationTimestamp(publicationTime);
        status = SubmissionStatus.PUBLISHED;
        publishedByUserId = publisherUserId;
        publishedAt = timestamp;
        touch(timestamp);
    }

    public Optional<BigDecimal> getAutomaticScoreValue() {
        return Optional.ofNullable(automaticScore);
    }

    public Optional<BigDecimal> getServerFinalScore() {
        return Optional.ofNullable(finalScore);
    }

    public Optional<BigDecimal> getPublishedFinalScore() {
        return status == SubmissionStatus.PUBLISHED && publishedAt != null
                ? Optional.ofNullable(finalScore)
                : Optional.empty();
    }

    public boolean isResultPublished() {
        return status == SubmissionStatus.PUBLISHED && publishedAt != null;
    }

    // COMPATIBILITY-ONLY: The Assignment 2 diagram exposes score as double.
    public double calculateFinalScore() {
        BigDecimal score = finalScore == null ? automaticScore : finalScore;
        return score == null ? 0.0 : score.doubleValue();
    }

    // COMPATIBILITY-ONLY: Generic status assignment would bypass timestamps,
    // grading, review, and publication invariants.
    public void changeSubmissionStatus(SubmissionStatus status) {
        throw new IllegalStateException(
                "Submission status changes require a controlled lifecycle operation"
        );
    }

    ExamSubmission copy() {
        if (executionId == 0) {
            return new ExamSubmission();
        }
        return new ExamSubmission(
                submissionId,
                executionId,
                examId,
                examVersionNo,
                studentUserId,
                startedAt,
                submittedAt,
                status,
                allocatedDurationMinutes,
                extraMinutes,
                extensionReason,
                actualDurationMinutes,
                automaticScore,
                finalScore,
                teacherFeedback,
                manualChangeReason,
                reviewedByUserId,
                reviewedAt,
                publishedByUserId,
                publishedAt,
                createdAt,
                updatedAt,
                studentAnswers
        );
    }

    private void finalizeAt(SubmissionStatus finalStatus, LocalDateTime timestamp) {
        submittedAt = timestamp;
        status = finalStatus;
        actualDurationMinutes = Math.toIntExact(Math.max(
                0L,
                Duration.between(startedAt, timestamp).toMinutes()
        ));
        touch(timestamp);
    }

    private void requireEditableAt(LocalDateTime serverTime) {
        requireInProgress();
        LocalDateTime now = Objects.requireNonNull(serverTime, "Server time is required");
        if (!now.isBefore(getEffectiveDeadline())) {
            throw new IllegalStateException("Exam time has expired");
        }
        requireMutationTimestamp(now);
    }

    private void requireInProgress() {
        requireInitialized();
        if (status != SubmissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Exam attempt already submitted");
        }
    }

    private void requireFinalizedForGrading() {
        requireInitialized();
        if (status == SubmissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Exam attempt is not finalized");
        }
        if (status == SubmissionStatus.PUBLISHED) {
            throw new IllegalStateException("Published results are immutable");
        }
    }

    private void requireInitialized() {
        if (executionId == 0) {
            throw new IllegalStateException(
                    "Compatibility-only submission has no lifecycle state"
            );
        }
    }

    private void validateLifecycleState() {
        boolean finalized = status != SubmissionStatus.IN_PROGRESS;
        if (!finalized) {
            if (submittedAt != null || actualDurationMinutes != null
                    || automaticScore != null || finalScore != null
                    || reviewedByUserId != null || reviewedAt != null
                    || publishedByUserId != null || publishedAt != null) {
                throw new IllegalArgumentException(
                        "In-progress submission contains finalization data"
                );
            }
            return;
        }
        if (submittedAt == null || submittedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "Finalized submission requires a valid submission timestamp"
            );
        }
        if (actualDurationMinutes == null) {
            throw new IllegalArgumentException(
                    "Finalized submission requires actual duration"
            );
        }
        boolean partialReview = (reviewedByUserId == null) != (reviewedAt == null);
        if (partialReview) {
            throw new IllegalArgumentException("Review metadata must be complete");
        }
        if (reviewedByUserId != null) {
            requirePositive(reviewedByUserId, "Reviewer user ID must be positive");
            if (automaticScore == null || finalScore == null) {
                throw new IllegalArgumentException(
                        "Reviewed submission requires automatic and final scores"
                );
            }
            if (finalScore.compareTo(automaticScore) != 0
                    && manualChangeReason == null) {
                throw new IllegalArgumentException(
                        "Manual score change reason is required"
                );
            }
        } else if (teacherFeedback != null || manualChangeReason != null) {
            throw new IllegalArgumentException(
                    "Unreviewed submission cannot contain review details"
            );
        }
        boolean partialPublication = (publishedByUserId == null) != (publishedAt == null);
        if (partialPublication) {
            throw new IllegalArgumentException("Publication metadata must be complete");
        }
        if (status == SubmissionStatus.PUBLISHED) {
            if (publishedByUserId == null || reviewedByUserId == null
                    || finalScore == null) {
                throw new IllegalArgumentException(
                        "Published submission requires reviewed result metadata"
                );
            }
            requirePositive(publishedByUserId, "Publisher user ID must be positive");
        } else if (publishedByUserId != null) {
            throw new IllegalArgumentException(
                    "Unpublished submission cannot contain publication metadata"
            );
        }
    }

    private StudentAnswer findAnswer(int questionId) {
        return studentAnswers.stream()
                .filter(answer -> answer.getQuestionId() == questionId)
                .findFirst()
                .orElse(null);
    }

    private void sortAnswers() {
        studentAnswers.sort(Comparator.comparingInt(StudentAnswer::getQuestionId));
    }

    private void touch(LocalDateTime timestamp) {
        if (updatedAt == null || timestamp.isAfter(updatedAt)) {
            updatedAt = timestamp;
        }
    }

    private LocalDateTime requireMutationTimestamp(LocalDateTime timestamp) {
        LocalDateTime required = Objects.requireNonNull(
                timestamp,
                "Submission mutation timestamp is required"
        );
        if (updatedAt != null && required.isBefore(updatedAt)) {
            throw new IllegalArgumentException(
                    "Submission mutation timestamp cannot precede current state"
            );
        }
        return required;
    }

    private static List<StudentAnswer> copyAndValidateAnswers(
            List<StudentAnswer> answers,
            int submissionId
    ) {
        if (answers == null) {
            throw new IllegalArgumentException("Student answers are required");
        }
        List<StudentAnswer> copies = new ArrayList<>(answers.size());
        Set<Integer> questionIds = new HashSet<>();
        for (StudentAnswer answer : answers) {
            if (answer == null) {
                throw new IllegalArgumentException("Student answer is required");
            }
            if (answer.getSubmissionId() != submissionId) {
                throw new IllegalArgumentException(
                        "Student answer belongs to a different submission"
                );
            }
            if (!questionIds.add(answer.getQuestionId())) {
                throw new IllegalArgumentException(
                        "Duplicate student answer: " + answer.getQuestionId()
                );
            }
            copies.add(answer.copy());
        }
        copies.sort(Comparator.comparingInt(StudentAnswer::getQuestionId));
        return copies;
    }

    private static void requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal normalizeRequiredScore(BigDecimal score) {
        BigDecimal normalized = normalizeScore(score);
        if (normalized == null) {
            throw new IllegalArgumentException("Submission score is required");
        }
        return normalized;
    }

    private static BigDecimal normalizeScore(BigDecimal score) {
        if (score == null) {
            return null;
        }
        if (score.compareTo(BigDecimal.ZERO) < 0
                || score.compareTo(MAXIMUM_SCORE) > 0) {
            throw new IllegalArgumentException(
                    "Submission score must be between 0 and 100"
            );
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }
}
