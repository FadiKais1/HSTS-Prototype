package hsts.common;

import hsts.common.type.ReportType;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public final class ReportSummaryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ReportType reportType;
    private final String title;
    private final LocalDateTime generatedAt;
    private final Integer targetId;
    private final String targetDisplayName;
    private final List<ExamStatisticsDTO> examStatistics;

    public ReportSummaryDTO(ReportType reportType, String title,
                            LocalDateTime generatedAt, Integer targetId,
                            String targetDisplayName,
                            List<ExamStatisticsDTO> examStatistics) {
        if (reportType == null) {
            throw new IllegalArgumentException("Report type is required");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Report title is required");
        }
        if (generatedAt == null) {
            throw new IllegalArgumentException("Report generation time is required");
        }
        if (targetId != null && targetId <= 0) {
            throw new IllegalArgumentException("Report target ID must be positive");
        }
        if (targetDisplayName != null && targetDisplayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Report target display name cannot be blank");
        }

        this.reportType = reportType;
        this.title = title.trim();
        this.generatedAt = generatedAt;
        this.targetId = targetId;
        this.targetDisplayName = targetDisplayName == null
                ? null
                : targetDisplayName.trim();
        this.examStatistics = List.copyOf(examStatistics);
    }

    public ReportType getReportType() { return reportType; }
    public String getTitle() { return title; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public Integer getTargetId() { return targetId; }
    public String getTargetDisplayName() { return targetDisplayName; }
    public List<ExamStatisticsDTO> getExamStatistics() { return examStatistics; }
}
