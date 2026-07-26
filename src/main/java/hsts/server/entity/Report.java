package hsts.server.entity;

import hsts.common.type.ReportType;

import java.time.LocalDateTime;

public class Report {
    private int reportId;
    private ReportType type;
    private LocalDateTime generatedAt;
    private String content;
    private String filePath;

    public String getContent() {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
