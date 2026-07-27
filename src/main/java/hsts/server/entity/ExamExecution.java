package hsts.server.entity;

import hsts.common.type.ExecutionStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ExamExecution {
    private static final BigDecimal MAXIMUM_SCORE = new BigDecimal("100.00");

    private final int executionId;
    private final String executionCode;
    private final int examId;
    private final int examVersionNo;
    private final LocalDateTime openingTime;
    private final LocalDateTime closingTime;
    private final int durationMinutes;
    private final int createdByUserId;
    private final LocalDateTime createdAt;

    private ExecutionStatus status;
    private LocalDateTime closedAt;
    private BigDecimal averageScore;
    private BigDecimal medianScore;
    private List<Integer> decileDistribution;
    private int startedCount;
    private int submittedCount;
    private int autoSubmittedCount;
    private LocalDateTime updatedAt;

    // RELATIONSHIP-DERIVED: Exam execution contains zero or more submissions.
    private final List<ExamSubmission> examSubmissions;

    private ExamExecution(int executionId, String executionCode,
                          int examId, int examVersionNo,
                          LocalDateTime openingTime,
                          LocalDateTime closingTime,
                          int durationMinutes, ExecutionStatus status,
                          int createdByUserId, LocalDateTime createdAt,
                          LocalDateTime closedAt,
                          BigDecimal averageScore,
                          BigDecimal medianScore,
                          List<Integer> decileDistribution,
                          int startedCount, int submittedCount,
                          int autoSubmittedCount,
                          LocalDateTime updatedAt,
                          List<ExamSubmission> examSubmissions) {
        if (executionId < 0) {
            throw new IllegalArgumentException("Execution ID cannot be negative");
        }
        requirePositive(examId, "Exam ID must be positive");
        requirePositive(examVersionNo, "Exam version must be positive");
        requirePositive(createdByUserId, "Execution creator ID must be positive");
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Execution duration must be positive");
        }
        this.executionId = executionId;
        this.executionCode = requireExecutionCode(executionCode);
        this.examId = examId;
        this.examVersionNo = examVersionNo;
        this.openingTime = Objects.requireNonNull(
                openingTime,
                "Execution opening time is required"
        );
        this.closingTime = Objects.requireNonNull(
                closingTime,
                "Execution closing time is required"
        );
        if (!closingTime.isAfter(openingTime)) {
            throw new IllegalArgumentException(
                    "Execution closing time must be after opening time"
            );
        }
        this.durationMinutes = durationMinutes;
        this.status = Objects.requireNonNull(status, "Execution status is required");
        this.createdByUserId = createdByUserId;
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "Execution creation timestamp is required"
        );
        if (createdAt.isAfter(openingTime)) {
            throw new IllegalArgumentException(
                    "Execution cannot be created after its opening time"
            );
        }
        this.closedAt = closedAt;
        this.averageScore = normalizeScore(averageScore);
        this.medianScore = normalizeScore(medianScore);
        this.decileDistribution = copyAndValidateDeciles(decileDistribution);
        validateCounts(startedCount, submittedCount, autoSubmittedCount);
        this.startedCount = startedCount;
        this.submittedCount = submittedCount;
        this.autoSubmittedCount = autoSubmittedCount;
        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "Execution update timestamp is required"
        );
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "Execution update timestamp cannot precede creation"
            );
        }
        this.examSubmissions = copyAndValidateSubmissions(
                examSubmissions,
                executionId,
                examId,
                examVersionNo
        );
        validateLifecycleState();
    }

    public static ExamExecution schedule(int examId, int examVersionNo,
                                         String executionCode,
                                         LocalDateTime openingTime,
                                         LocalDateTime closingTime,
                                         int durationMinutes,
                                         int createdByUserId,
                                         LocalDateTime createdAt) {
        return new ExamExecution(
                0,
                executionCode,
                examId,
                examVersionNo,
                openingTime,
                closingTime,
                durationMinutes,
                ExecutionStatus.SCHEDULED,
                createdByUserId,
                createdAt,
                null,
                null,
                null,
                List.of(),
                0,
                0,
                0,
                createdAt,
                List.of()
        );
    }

    public static ExamExecution rehydrate(
            int executionId,
            String executionCode,
            int examId,
            int examVersionNo,
            LocalDateTime openingTime,
            LocalDateTime closingTime,
            int durationMinutes,
            ExecutionStatus status,
            int createdByUserId,
            LocalDateTime createdAt,
            LocalDateTime closedAt,
            BigDecimal averageScore,
            BigDecimal medianScore,
            List<Integer> decileDistribution,
            int startedCount,
            int submittedCount,
            int autoSubmittedCount,
            LocalDateTime updatedAt,
            List<ExamSubmission> examSubmissions
    ) {
        if (executionId <= 0) {
            throw new IllegalArgumentException("Persisted execution ID must be positive");
        }
        return new ExamExecution(
                executionId,
                executionCode,
                examId,
                examVersionNo,
                openingTime,
                closingTime,
                durationMinutes,
                status,
                createdByUserId,
                createdAt,
                closedAt,
                averageScore,
                medianScore,
                decileDistribution,
                startedCount,
                submittedCount,
                autoSubmittedCount,
                updatedAt,
                examSubmissions
        );
    }

    public int getExecutionId() { return executionId; }
    public String getExecutionCode() { return executionCode; }
    public int getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public LocalDateTime getOpeningTime() { return openingTime; }
    public LocalDateTime getClosingTime() { return closingTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public ExecutionStatus getStatus() { return status; }
    public int getCreatedByUserId() { return createdByUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public BigDecimal getAverageScoreValue() { return averageScore; }
    public BigDecimal getMedianScoreValue() { return medianScore; }
    public int getStartedCount() { return startedCount; }
    public int getSubmittedCount() { return submittedCount; }
    public int getAutoSubmittedCount() { return autoSubmittedCount; }

    // COMPATIBILITY-ONLY: The diagram exposes statistics as double.
    public double getAverageScore() {
        return averageScore == null ? 0.0 : averageScore.doubleValue();
    }

    public double getMedianScore() {
        return medianScore == null ? 0.0 : medianScore.doubleValue();
    }

    public List<Integer> getDecileDistribution() {
        return List.copyOf(decileDistribution);
    }

    public List<ExamSubmission> getExamSubmissions() {
        return examSubmissions.stream().map(ExamSubmission::copy).toList();
    }

    public ExecutionStatus statusAt(LocalDateTime serverTime) {
        LocalDateTime now = Objects.requireNonNull(serverTime, "Server time is required");
        if (status == ExecutionStatus.CLOSED) {
            return ExecutionStatus.CLOSED;
        }
        if (now.isBefore(openingTime)) {
            return ExecutionStatus.SCHEDULED;
        }
        return now.isBefore(closingTime)
                ? ExecutionStatus.OPEN
                : ExecutionStatus.CLOSED;
    }

    public ExecutionStatus evaluateStatus(LocalDateTime serverTime) {
        LocalDateTime timestamp = requireMutationTimestamp(serverTime);
        ExecutionStatus evaluated = statusAt(timestamp);
        if (evaluated == status) {
            return status;
        }
        if (evaluated == ExecutionStatus.CLOSED) {
            status = ExecutionStatus.CLOSED;
            closedAt = timestamp;
        } else {
            status = evaluated;
        }
        touch(timestamp);
        return status;
    }

    public boolean canStartNewAttempt(LocalDateTime serverTime) {
        return statusAt(serverTime) == ExecutionStatus.OPEN;
    }

    public void openExecution(LocalDateTime serverTime) {
        LocalDateTime timestamp = requireMutationTimestamp(serverTime);
        if (status == ExecutionStatus.CLOSED) {
            throw new IllegalStateException("Closed execution cannot be reopened");
        }
        if (timestamp.isBefore(openingTime)) {
            throw new IllegalStateException("Exam is not open yet");
        }
        if (!timestamp.isBefore(closingTime)) {
            throw new IllegalStateException("Exam is closed");
        }
        if (status != ExecutionStatus.OPEN) {
            status = ExecutionStatus.OPEN;
            touch(timestamp);
        }
    }

    public void closeExecution(LocalDateTime serverTime) {
        LocalDateTime timestamp = requireMutationTimestamp(serverTime);
        if (status == ExecutionStatus.CLOSED) {
            return;
        }
        if (timestamp.isBefore(closingTime)) {
            throw new IllegalStateException("Execution start window is still open");
        }
        status = ExecutionStatus.CLOSED;
        closedAt = timestamp;
        touch(timestamp);
    }

    // COMPATIBILITY-ONLY: Diagram method uses the configured opening time rather
    // than consulting a client or workstation clock.
    public void openExecution() {
        openExecution(openingTime);
    }

    // COMPATIBILITY-ONLY: Diagram method closes at the configured window end.
    public void closeExecution() {
        closeExecution(closingTime);
    }

    public boolean validateCode(String code) {
        return code != null
                && code.matches("[A-Z0-9]{4}")
                && executionCode.equals(code);
    }

    public boolean isOpen() {
        return status == ExecutionStatus.OPEN;
    }

    public boolean isOpen(LocalDateTime serverTime) {
        return statusAt(serverTime) == ExecutionStatus.OPEN;
    }

    public void addSubmission(ExamSubmission submission,
                              LocalDateTime associatedAt) {
        Objects.requireNonNull(submission, "Exam submission is required");
        LocalDateTime timestamp = requireMutationTimestamp(associatedAt);
        if (submission.getExecutionId() != executionId
                || submission.getExamId() != examId
                || submission.getExamVersionNo() != examVersionNo) {
            throw new IllegalArgumentException(
                    "Submission identity does not match the execution"
            );
        }
        if (examSubmissions.stream().anyMatch(existing ->
                existing.getStudentUserId() == submission.getStudentUserId())) {
            throw new IllegalArgumentException(
                    "Student already has a submission for this execution"
            );
        }
        examSubmissions.add(submission.copy());
        startedCount = Math.max(startedCount, examSubmissions.size());
        touch(timestamp);
    }

    public void recordStatistics(BigDecimal averageScore,
                                 BigDecimal medianScore,
                                 List<Integer> decileDistribution,
                                 int startedCount,
                                 int submittedCount,
                                 int autoSubmittedCount,
                                 LocalDateTime calculatedAt) {
        BigDecimal normalizedAverage = normalizeRequiredScore(averageScore);
        BigDecimal normalizedMedian = normalizeRequiredScore(medianScore);
        List<Integer> normalizedDeciles = copyAndValidateDeciles(decileDistribution);
        if (normalizedDeciles.size() != 10) {
            throw new IllegalArgumentException(
                    "Execution statistics require all ten deciles"
            );
        }
        validateCounts(startedCount, submittedCount, autoSubmittedCount);
        LocalDateTime timestamp = requireMutationTimestamp(calculatedAt);
        this.averageScore = normalizedAverage;
        this.medianScore = normalizedMedian;
        this.decileDistribution = normalizedDeciles;
        this.startedCount = startedCount;
        this.submittedCount = submittedCount;
        this.autoSubmittedCount = autoSubmittedCount;
        touch(timestamp);
    }

    // COMPATIBILITY-ONLY: Execution-wide extension conflicts with the implemented
    // per-student extension and audit model.
    public void extendTime(int extraMinutes, String reason) {
        throw new IllegalStateException(
                "Time extensions must target an individual submission"
        );
    }

    // COMPATIBILITY-ONLY: The diagram signature supplies no persisted score set.
    public void updateStatistics() {
        throw new IllegalStateException(
                "Execution statistics require persisted grading results"
        );
    }

    private void validateLifecycleState() {
        if (status == ExecutionStatus.CLOSED) {
            if (closedAt == null || closedAt.isBefore(closingTime)) {
                throw new IllegalArgumentException(
                        "Closed execution requires a valid closing timestamp"
                );
            }
        } else if (closedAt != null) {
            throw new IllegalArgumentException(
                    "Open or scheduled execution cannot contain a closing timestamp"
            );
        }
    }

    private void touch(LocalDateTime timestamp) {
        if (timestamp.isAfter(updatedAt)) {
            updatedAt = timestamp;
        }
    }

    private LocalDateTime requireMutationTimestamp(LocalDateTime timestamp) {
        LocalDateTime required = Objects.requireNonNull(
                timestamp,
                "Execution mutation timestamp is required"
        );
        if (required.isBefore(updatedAt)) {
            throw new IllegalArgumentException(
                    "Execution mutation timestamp cannot precede current state"
            );
        }
        return required;
    }

    private static String requireExecutionCode(String executionCode) {
        if (executionCode == null || !executionCode.matches("[A-Z0-9]{4}")) {
            throw new IllegalArgumentException(
                    "Execution code must be four uppercase alphanumeric characters"
            );
        }
        return executionCode;
    }

    private static List<ExamSubmission> copyAndValidateSubmissions(
            List<ExamSubmission> submissions,
            int executionId,
            int examId,
            int examVersionNo
    ) {
        if (submissions == null) {
            throw new IllegalArgumentException("Exam submissions are required");
        }
        List<ExamSubmission> copies = new ArrayList<>(submissions.size());
        Set<Integer> studentIds = new HashSet<>();
        for (ExamSubmission submission : submissions) {
            if (submission == null) {
                throw new IllegalArgumentException("Exam submission is required");
            }
            if (submission.getExecutionId() != executionId
                    || submission.getExamId() != examId
                    || submission.getExamVersionNo() != examVersionNo) {
                throw new IllegalArgumentException(
                        "Submission identity does not match the execution"
                );
            }
            if (!studentIds.add(submission.getStudentUserId())) {
                throw new IllegalArgumentException(
                        "Duplicate student submission: "
                                + submission.getStudentUserId()
                );
            }
            copies.add(submission.copy());
        }
        return copies;
    }

    private static List<Integer> copyAndValidateDeciles(List<Integer> deciles) {
        if (deciles == null) {
            throw new IllegalArgumentException("Decile distribution is required");
        }
        if (!deciles.isEmpty() && deciles.size() != 10) {
            throw new IllegalArgumentException(
                    "Decile distribution must be empty or contain ten values"
            );
        }
        List<Integer> copy = new ArrayList<>(deciles.size());
        for (Integer count : deciles) {
            if (count == null || count < 0) {
                throw new IllegalArgumentException(
                        "Decile submission counts cannot be negative"
                );
            }
            copy.add(count);
        }
        return List.copyOf(copy);
    }

    private static void validateCounts(int startedCount, int submittedCount,
                                       int autoSubmittedCount) {
        if (startedCount < 0 || submittedCount < 0 || autoSubmittedCount < 0) {
            throw new IllegalArgumentException(
                    "Execution submission counts cannot be negative"
            );
        }
        if ((long) submittedCount + autoSubmittedCount > startedCount) {
            throw new IllegalArgumentException(
                    "Finalized submission counts cannot exceed starts"
            );
        }
    }

    private static void requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static BigDecimal normalizeRequiredScore(BigDecimal score) {
        BigDecimal normalized = normalizeScore(score);
        if (normalized == null) {
            throw new IllegalArgumentException("Execution score is required");
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
                    "Execution score must be between 0 and 100"
            );
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }
}
