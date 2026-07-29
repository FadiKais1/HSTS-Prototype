package hsts.common;

import hsts.common.type.ReportExportFormat;
import hsts.common.type.ReportType;

import java.io.Serializable;

public final class ReportExportPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ReportType reportType;
    private final Integer targetId;
    private final ReportExportFormat format;

    public ReportExportPayload(ReportType reportType, Integer targetId,
                               ReportExportFormat format) {
        this.reportType = reportType;
        this.targetId = targetId;
        this.format = format;
    }

    public ReportType getReportType() { return reportType; }
    public Integer getTargetId() { return targetId; }
    public ReportExportFormat getFormat() { return format; }
}
