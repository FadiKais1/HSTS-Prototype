package hsts.server.entity;

import hsts.common.type.ReportType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class Report {
    private final int reportId;
    private final ReportType reportType;
    private final LocalDateTime generatedAt;
    private final String title;
    private final Integer targetId;
    private final String targetDisplayName;
    private final List<ExecutionStatistics> executionStatistics;

    // COMPATIBILITY-ONLY: Existing skeleton relationship tests require an unloaded
    // Report placeholder. It is immutable and contains no fabricated statistics.
    public Report() {
        this.reportId = 0;
        this.reportType = null;
        this.generatedAt = null;
        this.title = "";
        this.targetId = null;
        this.targetDisplayName = null;
        this.executionStatistics = List.of();
    }

    public Report(int reportId, ReportType reportType, LocalDateTime generatedAt,
                  String title, Integer targetId, String targetDisplayName,
                  List<ExecutionStatistics> executionStatistics) {
        if (reportId < 0) {
            throw new IllegalArgumentException("Report ID cannot be negative");
        }
        if (reportType == null) {
            throw new IllegalArgumentException("Report type is required");
        }
        if (generatedAt == null) {
            throw new IllegalArgumentException("Report generation time is required");
        }
        this.title = requireNonBlank(title, "Report title is required");
        if (targetId != null && targetId <= 0) {
            throw new IllegalArgumentException("Report target ID must be positive");
        }
        if (targetDisplayName != null && targetDisplayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Report target display name cannot be blank");
        }

        this.reportId = reportId;
        this.reportType = reportType;
        this.generatedAt = generatedAt;
        this.targetId = targetId;
        this.targetDisplayName = targetDisplayName == null
                ? null
                : targetDisplayName.trim();
        this.executionStatistics = copyStatistics(executionStatistics);
    }

    public int getReportId() { return reportId; }
    public ReportType getReportType() { return reportType; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public String getTitle() { return title; }
    public Integer getTargetId() { return targetId; }
    public String getTargetDisplayName() { return targetDisplayName; }

    public List<ExecutionStatistics> getExecutionStatistics() {
        return copyStatistics(executionStatistics);
    }

    public String getContent() {
        if (reportType == null) {
            return "";
        }
        return title + " [" + reportType.name() + "]: "
                + executionStatistics.size() + " execution(s)";
    }

    private static List<ExecutionStatistics> copyStatistics(
            List<ExecutionStatistics> statistics
    ) {
        if (statistics == null) {
            throw new IllegalArgumentException("Execution statistics are required");
        }
        List<ExecutionStatistics> copies = new ArrayList<>(statistics.size());
        for (ExecutionStatistics value : statistics) {
            if (value == null) {
                throw new IllegalArgumentException("Execution statistics cannot contain null");
            }
            copies.add(value.copy());
        }
        return List.copyOf(copies);
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public static final class ScoreBand {
        private final int lowerBoundInclusive;
        private final int upperBoundInclusive;
        private final int submissionCount;

        public ScoreBand(int lowerBoundInclusive, int upperBoundInclusive,
                         int submissionCount) {
            if (lowerBoundInclusive < 0 || lowerBoundInclusive > 100) {
                throw new IllegalArgumentException(
                        "Score band lower bound must be between 0 and 100"
                );
            }
            if (upperBoundInclusive < 0 || upperBoundInclusive > 100) {
                throw new IllegalArgumentException(
                        "Score band upper bound must be between 0 and 100"
                );
            }
            if (lowerBoundInclusive > upperBoundInclusive) {
                throw new IllegalArgumentException(
                        "Score band lower bound cannot exceed upper bound"
                );
            }
            if (submissionCount < 0) {
                throw new IllegalArgumentException(
                        "Score band submission count cannot be negative"
                );
            }
            this.lowerBoundInclusive = lowerBoundInclusive;
            this.upperBoundInclusive = upperBoundInclusive;
            this.submissionCount = submissionCount;
        }

        public int getLowerBoundInclusive() { return lowerBoundInclusive; }
        public int getUpperBoundInclusive() { return upperBoundInclusive; }
        public int getSubmissionCount() { return submissionCount; }

        private ScoreBand copy() {
            return new ScoreBand(lowerBoundInclusive, upperBoundInclusive, submissionCount);
        }
    }

    public static final class ExecutionStatistics {
        private static final int[] LOWER_BOUNDS =
                {0, 10, 20, 30, 40, 50, 60, 70, 80, 90};
        private static final int[] UPPER_BOUNDS =
                {9, 19, 29, 39, 49, 59, 69, 79, 89, 100};
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
        private final List<ScoreBand> scoreBands;
        private final int startedSubmissionCount;
        private final int submittedSubmissionCount;
        private final int autoSubmittedSubmissionCount;

        public ExecutionStatistics(int executionId, int examId, int examVersionNo,
                                   String examCode, String examTitle, int courseId,
                                   String courseName, LocalDateTime openingTime,
                                   LocalDateTime closingTime,
                                   int publishedSubmissionCount,
                                   BigDecimal averageScore, BigDecimal medianScore,
                                   List<ScoreBand> scoreBands,
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
            if (openingTime == null || closingTime == null) {
                throw new IllegalArgumentException("Execution times are required");
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
            this.scoreBands = copyAndValidateBands(scoreBands, publishedSubmissionCount);

            this.executionId = executionId;
            this.examId = examId;
            this.examVersionNo = examVersionNo;
            this.courseId = courseId;
            this.openingTime = openingTime;
            this.closingTime = closingTime;
            this.publishedSubmissionCount = publishedSubmissionCount;
            this.averageScore = averageScore;
            this.medianScore = medianScore;
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

        public List<ScoreBand> getScoreBands() {
            List<ScoreBand> copies = new ArrayList<>(scoreBands.size());
            for (ScoreBand band : scoreBands) {
                copies.add(band.copy());
            }
            return List.copyOf(copies);
        }

        public int getStartedSubmissionCount() { return startedSubmissionCount; }
        public int getSubmittedSubmissionCount() { return submittedSubmissionCount; }
        public int getAutoSubmittedSubmissionCount() {
            return autoSubmittedSubmissionCount;
        }

        private ExecutionStatistics copy() {
            return new ExecutionStatistics(
                    executionId, examId, examVersionNo, examCode, examTitle,
                    courseId, courseName, openingTime, closingTime,
                    publishedSubmissionCount, averageScore, medianScore,
                    scoreBands, startedSubmissionCount, submittedSubmissionCount,
                    autoSubmittedSubmissionCount
            );
        }

        private static List<ScoreBand> copyAndValidateBands(
                List<ScoreBand> bands, int publishedCount
        ) {
            if (bands == null) {
                throw new IllegalArgumentException("Score distribution is required");
            }
            if (bands.size() != 10) {
                throw new IllegalArgumentException(
                        "Score distribution must contain ten bands"
                );
            }
            List<ScoreBand> copies = new ArrayList<>(bands.size());
            long total = 0;
            for (int index = 0; index < bands.size(); index++) {
                ScoreBand band = bands.get(index);
                if (band == null) {
                    throw new IllegalArgumentException(
                            "Score distribution cannot contain null"
                    );
                }
                if (band.lowerBoundInclusive != LOWER_BOUNDS[index]
                        || band.upperBoundInclusive != UPPER_BOUNDS[index]) {
                    throw new IllegalArgumentException(
                            "Score distribution bands are not in the required order"
                    );
                }
                total += band.submissionCount;
                copies.add(band.copy());
            }
            if (total != publishedCount) {
                throw new IllegalArgumentException(
                        "Score distribution count must equal published submission count"
                );
            }
            return List.copyOf(copies);
        }

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
            if (score.compareTo(MINIMUM_SCORE) < 0
                    || score.compareTo(MAXIMUM_SCORE) > 0) {
                throw new IllegalArgumentException(
                        "Score must be between 0.00 and 100.00"
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
    }
}
