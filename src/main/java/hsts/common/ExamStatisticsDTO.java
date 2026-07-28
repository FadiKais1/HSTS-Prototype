package hsts.common;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ExamStatisticsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final int[] LOWER_BOUNDS = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90};
    private static final int[] UPPER_BOUNDS = {9, 19, 29, 39, 49, 59, 69, 79, 89, 100};
    private static final BigDecimal MINIMUM_SCORE = new BigDecimal("0.00");
    private static final BigDecimal MAXIMUM_SCORE = new BigDecimal("100.00");

    private final int executionId;
    private final int examId;
    private final int examVersionNo;
    private final String examCode;
    private final String examTitle;
    private final int courseId;
    private final String courseName;
    private final LocalDateTime openingTime;
    private final LocalDateTime closingTime;
    private final int publishedSubmissionCount;
    private final BigDecimal averageScore;
    private final BigDecimal medianScore;
    private final List<ScoreBandDTO> scoreBands;
    private final int startedSubmissionCount;
    private final int submittedSubmissionCount;
    private final int autoSubmittedSubmissionCount;

    public ExamStatisticsDTO(int executionId, int examId, int examVersionNo,
                             String examCode, String examTitle, int courseId,
                             String courseName, LocalDateTime openingTime,
                             LocalDateTime closingTime, int publishedSubmissionCount,
                             BigDecimal averageScore, BigDecimal medianScore,
                             List<ScoreBandDTO> scoreBands,
                             int startedSubmissionCount,
                             int submittedSubmissionCount,
                             int autoSubmittedSubmissionCount) {
        requirePositive(executionId, "Execution ID must be positive");
        requirePositive(examId, "Exam ID must be positive");
        requirePositive(examVersionNo, "Exam version number must be positive");
        requirePositive(courseId, "Course ID must be positive");
        this.examCode = requireNonBlank(examCode, "Exam code is required");
        this.examTitle = requireNonBlank(examTitle, "Exam title is required");
        this.courseName = requireNonBlank(courseName, "Course name is required");
        if (openingTime == null) {
            throw new IllegalArgumentException("Opening time is required");
        }
        if (closingTime == null) {
            throw new IllegalArgumentException("Closing time is required");
        }
        if (!closingTime.isAfter(openingTime)) {
            throw new IllegalArgumentException("Closing time must be after opening time");
        }
        requireNonNegative(publishedSubmissionCount,
                "Published submission count cannot be negative");
        requireNonNegative(startedSubmissionCount,
                "Started submission count cannot be negative");
        requireNonNegative(submittedSubmissionCount,
                "Submitted submission count cannot be negative");
        requireNonNegative(autoSubmittedSubmissionCount,
                "Auto-submitted submission count cannot be negative");
        if (submittedSubmissionCount + (long) autoSubmittedSubmissionCount
                > startedSubmissionCount) {
            throw new IllegalArgumentException(
                    "Submitted and auto-submitted counts cannot exceed started count"
            );
        }
        validateScores(publishedSubmissionCount, averageScore, medianScore);

        List<ScoreBandDTO> immutableBands = List.copyOf(scoreBands);
        validateBands(immutableBands, publishedSubmissionCount);

        this.executionId = executionId;
        this.examId = examId;
        this.examVersionNo = examVersionNo;
        this.courseId = courseId;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.publishedSubmissionCount = publishedSubmissionCount;
        this.averageScore = averageScore;
        this.medianScore = medianScore;
        this.scoreBands = immutableBands;
        this.startedSubmissionCount = startedSubmissionCount;
        this.submittedSubmissionCount = submittedSubmissionCount;
        this.autoSubmittedSubmissionCount = autoSubmittedSubmissionCount;
    }

    public int getExecutionId() { return executionId; }
    public int getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public String getExamCode() { return examCode; }
    public String getExamTitle() { return examTitle; }
    public int getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public LocalDateTime getOpeningTime() { return openingTime; }
    public LocalDateTime getClosingTime() { return closingTime; }
    public int getPublishedSubmissionCount() { return publishedSubmissionCount; }
    public BigDecimal getAverageScore() { return averageScore; }
    public BigDecimal getMedianScore() { return medianScore; }
    public List<ScoreBandDTO> getScoreBands() { return scoreBands; }
    public int getStartedSubmissionCount() { return startedSubmissionCount; }
    public int getSubmittedSubmissionCount() { return submittedSubmissionCount; }
    public int getAutoSubmittedSubmissionCount() { return autoSubmittedSubmissionCount; }

    private static void validateScores(int publishedCount, BigDecimal average,
                                       BigDecimal median) {
        if (publishedCount == 0) {
            if (average != null || median != null) {
                throw new IllegalArgumentException(
                        "Scores must be null when no published submissions exist"
                );
            }
            return;
        }
        requireScore(average, "Average score is required");
        requireScore(median, "Median score is required");
    }

    private static void requireScore(BigDecimal score, String nullMessage) {
        if (score == null) {
            throw new IllegalArgumentException(nullMessage);
        }
        if (score.compareTo(MINIMUM_SCORE) < 0 || score.compareTo(MAXIMUM_SCORE) > 0) {
            throw new IllegalArgumentException("Score must be between 0.00 and 100.00");
        }
    }

    private static void validateBands(List<ScoreBandDTO> bands, int publishedCount) {
        if (bands.size() != 10) {
            throw new IllegalArgumentException("Score distribution must contain ten bands");
        }
        long total = 0;
        for (int index = 0; index < bands.size(); index++) {
            ScoreBandDTO band = bands.get(index);
            if (band.getLowerBoundInclusive() != LOWER_BOUNDS[index]
                    || band.getUpperBoundInclusive() != UPPER_BOUNDS[index]) {
                throw new IllegalArgumentException(
                        "Score distribution bands are not in the required order"
                );
            }
            total += band.getSubmissionCount();
        }
        if (total != publishedCount) {
            throw new IllegalArgumentException(
                    "Score distribution count must equal published submission count"
            );
        }
    }

    private static void requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNonNegative(int value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
