package hsts.server.report;

import hsts.common.ExamStatisticsDTO;
import hsts.common.ReportExportResult;
import hsts.common.ReportSummaryDTO;
import hsts.common.ScoreBandDTO;
import hsts.common.type.ReportExportFormat;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class ReportExportGenerator {
    private static final int MAX_EXPORT_BYTES = 10 * 1024 * 1024;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ReportExportResult generate(ReportSummaryDTO report,
                                       ReportExportFormat format) {
        if (report == null) throw new IllegalArgumentException("Report is required");
        if (format == null) {
            throw new IllegalArgumentException("Report export format is required");
        }
        byte[] bytes = switch (format) {
            case PDF -> createPdf(report);
            case XLSX -> createWorkbook(report);
        };
        if (bytes.length > MAX_EXPORT_BYTES) {
            throw new IllegalStateException("Generated report exceeds the size limit");
        }
        String extension = format == ReportExportFormat.PDF ? ".pdf" : ".xlsx";
        String mediaType = format == ReportExportFormat.PDF
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return new ReportExportResult(safeBasename(report) + extension, mediaType, bytes);
    }

    public ReportExportResult generateComparison(ReportSummaryDTO primary,
                                                  ReportSummaryDTO comparison,
                                                  ReportExportFormat format) {
        if (primary == null || comparison == null) {
            throw new IllegalArgumentException("Both comparison reports are required");
        }
        if (format == null) {
            throw new IllegalArgumentException("Report export format is required");
        }
        byte[] bytes = switch (format) {
            case PDF -> createComparisonPdf(primary, comparison);
            case XLSX -> createComparisonWorkbook(primary, comparison);
        };
        if (bytes.length > MAX_EXPORT_BYTES) {
            throw new IllegalStateException("Generated report exceeds the size limit");
        }
        String extension = format == ReportExportFormat.PDF ? ".pdf" : ".xlsx";
        String mediaType = format == ReportExportFormat.PDF
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return new ReportExportResult(
                safeComparisonBasename(primary, comparison) + extension,
                mediaType,
                bytes
        );
    }

    private byte[] createPdf(ReportSummaryDTO report) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA
            );
            PDType1Font bold = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA_BOLD
            );
            PdfReportWriter writer = new PdfReportWriter(
                    document,
                    regular,
                    bold,
                    report
            );
            writer.writeReport();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate PDF report", exception);
        }
    }

    private byte[] createComparisonPdf(ReportSummaryDTO primary,
                                       ReportSummaryDTO comparison) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA
            );
            PDType1Font bold = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA_BOLD
            );
            addComparisonCover(document, regular, bold, primary, comparison);
            PdfReportWriter primaryWriter = new PdfReportWriter(
                    document,
                    regular,
                    bold,
                    primary
            );
            primaryWriter.writeReport(false);
            PdfReportWriter comparisonWriter = new PdfReportWriter(
                    document,
                    regular,
                    bold,
                    comparison
            );
            comparisonWriter.writeReport(false);
            comparisonWriter.addPageFooters("HSTS Report Comparison");
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to generate PDF comparison report",
                    exception
            );
        }
    }

    private void addComparisonCover(PDDocument document,
                                    PDType1Font regular,
                                    PDType1Font bold,
                                    ReportSummaryDTO primary,
                                    ReportSummaryDTO comparison)
            throws IOException {
        PDRectangle pageSize = new PDRectangle(
                PDRectangle.A4.getHeight(),
                PDRectangle.A4.getWidth()
        );
        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            drawCoverText(stream, bold, "Report Comparison", 32f, 548f,
                    20f, 17, 24, 39);
            drawCoverText(
                    stream,
                    regular,
                    reportTypeLabel(primary.getReportType()),
                    32f,
                    525f,
                    10f,
                    71,
                    85,
                    105
            );

            stream.setNonStrokingColor(239f / 255f, 246f / 255f, 1f);
            stream.addRect(32f, 432f, pageSize.getWidth() - 64f, 72f);
            stream.fill();
            drawCoverText(stream, bold, "Primary target", 46f, 481f,
                    9f, 30, 64, 175);
            drawCoverText(stream, regular,
                    nullable(primary.getTargetDisplayName()), 46f, 461f,
                    12f, 17, 24, 39);
            drawCoverText(stream, bold, "Compared with", 430f, 481f,
                    9f, 30, 64, 175);
            drawCoverText(stream, regular,
                    nullable(comparison.getTargetDisplayName()), 430f, 461f,
                    12f, 17, 24, 39);

            drawCoverText(stream, bold, "Comparison Summary", 32f, 390f,
                    13f, 17, 24, 39);
            float summaryY = 363f;
            for (String line : comparisonSummaryLines(primary, comparison)) {
                drawCoverText(stream, regular, line, 46f, summaryY,
                        10f, 30, 41, 59);
                summaryY -= 24f;
            }
            drawCoverText(
                    stream,
                    regular,
                    "The following pages contain the complete report for each target.",
                    32f,
                    250f,
                    9f,
                    71,
                    85,
                    105
            );
        }
    }

    private static void drawCoverText(PDPageContentStream stream,
                                      PDType1Font font,
                                      String value,
                                      float x,
                                      float y,
                                      float size,
                                      int red,
                                      int green,
                                      int blue) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(red / 255f, green / 255f, blue / 255f);
        stream.setTextMatrix(Matrix.getTranslateInstance(x, y));
        stream.showText(PdfReportWriter.sanitize(value));
        stream.endText();
    }

    private byte[] createWorkbook(ReportSummaryDTO report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = createHeaderStyle(workbook);
            writeReportSheets(
                    workbook,
                    report,
                    header,
                    "Execution Statistics",
                    "Score Distribution"
            );
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate Excel report", exception);
        }
    }

    private byte[] createComparisonWorkbook(ReportSummaryDTO primary,
                                            ReportSummaryDTO comparison) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = createHeaderStyle(workbook);
            writeComparisonOverview(workbook, primary, comparison, header);
            writeReportSheets(
                    workbook,
                    primary,
                    header,
                    "Primary Statistics",
                    "Primary Distribution"
            );
            writeReportSheets(
                    workbook,
                    comparison,
                    header,
                    "Comparison Statistics",
                    "Comparison Distribution"
            );
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to generate Excel comparison report",
                    exception
            );
        }
    }

    private static CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle header = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        header.setFont(headerFont);
        return header;
    }

    private static void writeComparisonOverview(
            XSSFWorkbook workbook,
            ReportSummaryDTO primary,
            ReportSummaryDTO comparison,
            CellStyle header
    ) {
        Sheet overview = workbook.createSheet("Comparison Overview");
        Row title = overview.createRow(0);
        title.createCell(0).setCellValue("Report Comparison");
        title.getCell(0).setCellStyle(header);
        String[] headings = {
                "Side", "Report type", "Target", "Generated", "Executions"
        };
        Row headingRow = overview.createRow(2);
        for (int index = 0; index < headings.length; index++) {
            Cell cell = headingRow.createCell(index);
            cell.setCellValue(headings[index]);
            cell.setCellStyle(header);
        }
        writeOverviewRow(overview.createRow(3), "Primary", primary);
        writeOverviewRow(overview.createRow(4), "Comparison", comparison);
        Row summaryTitle = overview.createRow(6);
        summaryTitle.createCell(0).setCellValue("Comparison Summary");
        summaryTitle.getCell(0).setCellStyle(header);
        CellStyle summaryStyle = workbook.createCellStyle();
        summaryStyle.setWrapText(true);
        int summaryRowIndex = 7;
        for (String line : comparisonSummaryLines(primary, comparison)) {
            Row summaryRow = overview.createRow(summaryRowIndex);
            Cell summaryCell = summaryRow.createCell(0);
            summaryCell.setCellValue(line);
            summaryCell.setCellStyle(summaryStyle);
            overview.addMergedRegion(new CellRangeAddress(
                    summaryRowIndex,
                    summaryRowIndex,
                    0,
                    4
            ));
            summaryRow.setHeightInPoints(22f);
            summaryRowIndex++;
        }
        for (int index = 0; index < headings.length; index++) {
            overview.autoSizeColumn(index);
        }
    }

    private static void writeOverviewRow(Row row, String side,
                                         ReportSummaryDTO report) {
        row.createCell(0).setCellValue(side);
        row.createCell(1).setCellValue(reportTypeLabel(report.getReportType()));
        row.createCell(2).setCellValue(nullable(report.getTargetDisplayName()));
        row.createCell(3).setCellValue(time(report.getGeneratedAt()));
        row.createCell(4).setCellValue(report.getExamStatistics().size());
    }

    private static void writeReportSheets(
            XSSFWorkbook workbook,
            ReportSummaryDTO report,
            CellStyle header,
            String statisticsSheetName,
            String distributionSheetName
    ) {
        Sheet statistics = workbook.createSheet(statisticsSheetName);
        int rowIndex = 0;
        Row metadata = statistics.createRow(rowIndex++);
        metadata.createCell(0).setCellValue(report.getTitle());
        metadata.createCell(1).setCellValue(reportTypeLabel(report.getReportType()));
        metadata.createCell(2).setCellValue(nullable(report.getTargetDisplayName()));
        metadata.createCell(3).setCellValue(time(report.getGeneratedAt()));
        String[] headings = {"Execution ID", "Execution Code", "Exam", "Course",
                "Version", "Opening", "Closing", "Published", "Average", "Median",
                "Started", "Submitted", "Auto-submitted"};
        Row headingRow = statistics.createRow(rowIndex++);
        for (int index = 0; index < headings.length; index++) {
            Cell cell = headingRow.createCell(index);
            cell.setCellValue(headings[index]);
            cell.setCellStyle(header);
        }
        for (ExamStatisticsDTO execution : report.getExamStatistics()) {
            Row row = statistics.createRow(rowIndex++);
            row.createCell(0).setCellValue(execution.getExecutionId());
            row.createCell(1).setCellValue(execution.getExamCode());
            row.createCell(2).setCellValue(execution.getExamTitle());
            row.createCell(3).setCellValue(execution.getCourseName());
            row.createCell(4).setCellValue(execution.getExamVersionNo());
            row.createCell(5).setCellValue(time(execution.getOpeningTime()));
            row.createCell(6).setCellValue(time(execution.getClosingTime()));
            row.createCell(7).setCellValue(execution.getPublishedSubmissionCount());
            setDecimal(row.createCell(8), execution.getAverageScore());
            setDecimal(row.createCell(9), execution.getMedianScore());
            row.createCell(10).setCellValue(execution.getStartedSubmissionCount());
            row.createCell(11).setCellValue(execution.getSubmittedSubmissionCount());
            row.createCell(12).setCellValue(execution.getAutoSubmittedSubmissionCount());
        }
        for (int index = 0; index < headings.length; index++) {
            statistics.autoSizeColumn(index);
        }

        Sheet distribution = workbook.createSheet(distributionSheetName);
        Row distributionHeader = distribution.createRow(0);
        String[] bandHeadings = {
                "Execution ID", "Execution Code", "Lower", "Upper", "Count"
        };
        for (int index = 0; index < bandHeadings.length; index++) {
            Cell cell = distributionHeader.createCell(index);
            cell.setCellValue(bandHeadings[index]);
            cell.setCellStyle(header);
        }
        int bandRow = 1;
        for (ExamStatisticsDTO execution : report.getExamStatistics()) {
            for (ScoreBandDTO band : execution.getScoreBands()) {
                Row row = distribution.createRow(bandRow++);
                row.createCell(0).setCellValue(execution.getExecutionId());
                row.createCell(1).setCellValue(execution.getExamCode());
                row.createCell(2).setCellValue(band.getLowerBoundInclusive());
                row.createCell(3).setCellValue(band.getUpperBoundInclusive());
                row.createCell(4).setCellValue(band.getSubmissionCount());
            }
        }
        for (int index = 0; index < bandHeadings.length; index++) {
            distribution.autoSizeColumn(index);
        }
    }

    private static void setDecimal(Cell cell, BigDecimal value) {
        if (value == null) cell.setCellValue("N/A");
        else cell.setCellValue(value.doubleValue());
    }

    private static List<String> comparisonSummaryLines(
            ReportSummaryDTO primary,
            ReportSummaryDTO comparison
    ) {
        BigDecimal primaryAverage = meanOf(primary, true);
        BigDecimal comparisonAverage = meanOf(comparison, true);
        String primaryName = nullable(primary.getTargetDisplayName());
        String comparisonName = nullable(comparison.getTargetDisplayName());
        String primaryLine = summaryLine(primaryName, primary);
        String comparisonLine = summaryLine(comparisonName, comparison);
        if (primaryAverage == null || comparisonAverage == null) {
            return List.of(primaryLine, comparisonLine);
        }
        BigDecimal difference = primaryAverage.subtract(comparisonAverage);
        int direction = difference.compareTo(BigDecimal.ZERO);
        String differenceLine = direction == 0
                ? "The two averages are equal."
                : primaryName + " averages " + decimal(difference.abs())
                        + (direction > 0 ? " higher than " : " lower than ")
                        + comparisonName + ".";
        return List.of(primaryLine, comparisonLine, differenceLine);
    }

    private static String summaryLine(String name, ReportSummaryDTO report) {
        return name + ": " + report.getExamStatistics().size()
                + " executions, average " + decimal(meanOf(report, true))
                + ", median " + decimal(meanOf(report, false))
                + ", " + submittedTotal(report) + " submissions.";
    }

    private static BigDecimal meanOf(ReportSummaryDTO report, boolean average) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (ExamStatisticsDTO statistics : report.getExamStatistics()) {
            BigDecimal value = average
                    ? statistics.getAverageScore()
                    : statistics.getMedianScore();
            if (value != null) {
                total = total.add(value);
                count++;
            }
        }
        return count == 0
                ? null
                : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static int submittedTotal(ReportSummaryDTO report) {
        return report.getExamStatistics().stream()
                .mapToInt(ExamStatisticsDTO::getSubmittedSubmissionCount)
                .sum();
    }

    private static String safeBasename(ReportSummaryDTO report) {
        String source = report.getTitle() + "-" + nullable(report.getTargetDisplayName());
        String safe = source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return safe.isBlank() ? "hsts-report" : safe.substring(0, Math.min(100, safe.length()));
    }

    private static String safeComparisonBasename(ReportSummaryDTO primary,
                                                 ReportSummaryDTO comparison) {
        String source = "report-comparison-"
                + nullable(primary.getTargetDisplayName())
                + "-vs-"
                + nullable(comparison.getTargetDisplayName());
        String safe = source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return safe.isBlank()
                ? "hsts-report-comparison"
                : safe.substring(0, Math.min(100, safe.length()));
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "N/A" : value.toPlainString();
    }

    private static String time(LocalDateTime value) {
        return value == null ? "N/A" : TIME_FORMAT.format(value);
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private static String reportTypeLabel(hsts.common.type.ReportType type) {
        return switch (type) {
            case TEACHER_EXAMS -> "Teacher exams";
            case COURSE_EXAMS -> "Course exams";
            case STUDENT_EXAMS -> "Student exams";
            case EXAM_EXECUTION -> "Exam execution";
        };
    }

    private enum PdfSection {
        NONE,
        EXECUTIONS,
        DISTRIBUTION
    }

    private static final class PdfReportWriter {
        private static final PDRectangle PAGE_SIZE = new PDRectangle(
                PDRectangle.A4.getHeight(),
                PDRectangle.A4.getWidth()
        );
        private static final float MARGIN = 32f;
        private static final float FOOTER_SPACE = 24f;
        private static final float CONTENT_WIDTH = PAGE_SIZE.getWidth() - 2 * MARGIN;
        private static final float[] EXECUTION_WIDTHS = {
                42f, 120f, 110f, 28f, 88f, 88f,
                42f, 45f, 45f, 42f, 50f, 50f
        };
        private static final String[] EXECUTION_HEADERS = {
                "Code", "Exam", "Course", "Ver", "Opening", "Closing",
                "Published", "Average", "Median", "Started", "Submitted",
                "Auto-submitted"
        };
        private static final float[] DISTRIBUTION_WIDTHS = {
                67f, 71f, 71f, 71f, 71f, 71f, 71f, 71f, 71f, 71f, 71f
        };

        private final PDDocument document;
        private final PDType1Font regular;
        private final PDType1Font bold;
        private final ReportSummaryDTO report;
        private PDPageContentStream stream;
        private PDPage page;
        private float y;
        private int executionRowIndex;
        private int distributionRowIndex;

        private PdfReportWriter(PDDocument document, PDType1Font regular,
                                PDType1Font bold, ReportSummaryDTO report)
                throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            this.report = report;
            newPage(PdfSection.NONE);
        }

        private void writeReport() throws IOException {
            writeReport(true);
        }

        private void writeReport(boolean includeFooters) throws IOException {
            drawTitle(false);
            drawMetadata();
            drawSectionTitle("Execution Statistics");
            drawExecutionHeader();
            for (ExamStatisticsDTO execution : report.getExamStatistics()) {
                ensureSpace(27f, PdfSection.EXECUTIONS);
                drawExecutionRow(execution);
            }

            ensureSpace(52f, PdfSection.NONE);
            y -= 10f;
            drawSectionTitle("Score Distribution");
            drawDistributionHeader();
            for (ExamStatisticsDTO execution : report.getExamStatistics()) {
                ensureSpace(19f, PdfSection.DISTRIBUTION);
                drawDistributionRow(execution);
            }
            closePage();
            if (includeFooters) {
                addPageFooters("HSTS Report Export");
            }
        }

        private void drawTitle(boolean continuation) throws IOException {
            drawText(
                    continuation ? report.getTitle() + " - continued" : report.getTitle(),
                    MARGIN,
                    y,
                    continuation ? 11f : 18f,
                    true,
                    25,
                    45,
                    72
            );
            y -= continuation ? 20f : 30f;
        }

        private void drawMetadata() throws IOException {
            float height = 54f;
            stream.setNonStrokingColor(239f / 255f, 246f / 255f, 1f);
            stream.addRect(MARGIN, y - height, CONTENT_WIDTH, height);
            stream.fill();
            drawMetadataPair("Report type", reportTypeLabel(report.getReportType()),
                    MARGIN + 10f, y - 17f);
            drawMetadataPair("Target", nullable(report.getTargetDisplayName()),
                    MARGIN + 385f, y - 17f);
            drawMetadataPair("Generated", time(report.getGeneratedAt()),
                    MARGIN + 10f, y - 38f);
            drawMetadataPair("Execution count",
                    Integer.toString(report.getExamStatistics().size()),
                    MARGIN + 385f, y - 38f);
            y -= height + 16f;
        }

        private void drawMetadataPair(String label, String value, float x, float baseline)
                throws IOException {
            drawText(label + ":", x, baseline, 8f, true, 55, 65, 81);
            drawText(fitText(value, regular, 8f, 265f), x + 78f, baseline,
                    8f, false, 17, 24, 39);
        }

        private void drawSectionTitle(String title) throws IOException {
            drawText(title, MARGIN, y, 11f, true, 17, 24, 39);
            y -= 18f;
        }

        private void drawExecutionHeader() throws IOException {
            drawTableRow(EXECUTION_HEADERS, EXECUTION_WIDTHS, 24f,
                    true, false, 6.2f);
        }

        private void drawExecutionRow(ExamStatisticsDTO execution) throws IOException {
            String[] values = {
                    execution.getExamCode(),
                    execution.getExamTitle(),
                    execution.getCourseName(),
                    Integer.toString(execution.getExamVersionNo()),
                    compactTime(execution.getOpeningTime()),
                    compactTime(execution.getClosingTime()),
                    Integer.toString(execution.getPublishedSubmissionCount()),
                    decimal(execution.getAverageScore()),
                    decimal(execution.getMedianScore()),
                    Integer.toString(execution.getStartedSubmissionCount()),
                    Integer.toString(execution.getSubmittedSubmissionCount()),
                    Integer.toString(execution.getAutoSubmittedSubmissionCount())
            };
            drawTableRow(values, EXECUTION_WIDTHS, 27f, false,
                    executionRowIndex++ % 2 == 1, 6.8f);
        }

        private void drawDistributionHeader() throws IOException {
            String[] headers = new String[11];
            headers[0] = "Code";
            for (int index = 0; index < 10; index++) {
                headers[index + 1] = bandLabel(index);
            }
            drawTableRow(headers, DISTRIBUTION_WIDTHS, 24f,
                    true, false, 6.5f);
        }

        private void drawDistributionRow(ExamStatisticsDTO execution)
                throws IOException {
            String[] values = new String[11];
            values[0] = execution.getExamCode();
            for (int index = 0; index < 10; index++) {
                values[index + 1] = Integer.toString(
                        execution.getScoreBands().get(index).getSubmissionCount()
                );
            }
            drawTableRow(values, DISTRIBUTION_WIDTHS, 19f, false,
                    distributionRowIndex++ % 2 == 1, 7f);
        }

        private void drawTableRow(String[] values, float[] widths, float height,
                                  boolean header, boolean shaded, float fontSize)
                throws IOException {
            float totalWidth = 0f;
            for (float width : widths) totalWidth += width;
            float bottom = y - height;
            if (header) stream.setNonStrokingColor(
                    30f / 255f, 64f / 255f, 175f / 255f);
            else if (shaded) stream.setNonStrokingColor(
                    248f / 255f, 250f / 255f, 252f / 255f);
            else stream.setNonStrokingColor(1f, 1f, 1f);
            stream.addRect(MARGIN, bottom, totalWidth, height);
            stream.fill();

            stream.setStrokingColor(
                    203f / 255f, 213f / 255f, 225f / 255f);
            stream.setLineWidth(0.45f);
            stream.addRect(MARGIN, bottom, totalWidth, height);
            float x = MARGIN;
            for (int index = 0; index < values.length; index++) {
                if (index > 0) {
                    stream.moveTo(x, bottom);
                    stream.lineTo(x, y);
                }
                String fitted = fitText(
                        values[index],
                        header ? bold : regular,
                        fontSize,
                        widths[index] - 6f
                );
                drawText(fitted, x + 3f, bottom + (height - fontSize) / 2f,
                        fontSize, header,
                        header ? 255 : 17,
                        header ? 255 : 24,
                        header ? 255 : 39);
                x += widths[index];
            }
            stream.stroke();
            y = bottom;
        }

        private void ensureSpace(float required, PdfSection section)
                throws IOException {
            if (y - required >= MARGIN + FOOTER_SPACE) return;
            newPage(section);
            drawTitle(true);
            if (section == PdfSection.EXECUTIONS) {
                drawSectionTitle("Execution Statistics (continued)");
                drawExecutionHeader();
            } else if (section == PdfSection.DISTRIBUTION) {
                drawSectionTitle("Score Distribution (continued)");
                drawDistributionHeader();
            }
        }

        private void newPage(PdfSection section) throws IOException {
            closePage();
            page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_SIZE.getHeight() - MARGIN;
        }

        private void closePage() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private void addPageFooters(String exportLabel) throws IOException {
            int total = document.getNumberOfPages();
            for (int index = 0; index < total; index++) {
                PDPage footerPage = document.getPage(index);
                try (PDPageContentStream footer = new PDPageContentStream(
                        document,
                        footerPage,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                )) {
                    footer.setStrokingColor(
                            203f / 255f, 213f / 255f, 225f / 255f);
                    footer.setLineWidth(0.5f);
                    footer.moveTo(MARGIN, 24f);
                    footer.lineTo(PAGE_SIZE.getWidth() - MARGIN, 24f);
                    footer.stroke();
                    String label = "Page " + (index + 1) + " of " + total
                            + " | " + exportLabel;
                    float width = regular.getStringWidth(label) / 1000f * 7f;
                    footer.beginText();
                    footer.setFont(regular, 7f);
                    footer.setNonStrokingColor(
                            71f / 255f, 85f / 255f, 105f / 255f);
                    footer.setTextMatrix(Matrix.getTranslateInstance(
                            (PAGE_SIZE.getWidth() - width) / 2f,
                            12f
                    ));
                    footer.showText(label);
                    footer.endText();
                }
            }
        }

        private void drawText(String value, float x, float baseline,
                              float size, boolean emphasized,
                              int red, int green, int blue) throws IOException {
            stream.beginText();
            stream.setFont(emphasized ? bold : regular, size);
            stream.setNonStrokingColor(
                    red / 255f,
                    green / 255f,
                    blue / 255f
            );
            stream.setTextMatrix(Matrix.getTranslateInstance(x, baseline));
            stream.showText(sanitize(value));
            stream.endText();
        }

        private static String fitText(String value, PDType1Font font,
                                      float fontSize, float width)
                throws IOException {
            String safe = sanitize(value);
            if (font.getStringWidth(safe) / 1000f * fontSize <= width) {
                return safe;
            }
            String suffix = "...";
            int length = safe.length();
            while (length > 0) {
                String candidate = safe.substring(0, --length).stripTrailing()
                        + suffix;
                if (font.getStringWidth(candidate) / 1000f * fontSize <= width) {
                    return candidate;
                }
            }
            return suffix;
        }

        private static String sanitize(String value) {
            return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        private static String compactTime(LocalDateTime value) {
            return value == null ? "N/A"
                    : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(value);
        }

        private static String bandLabel(int index) {
            int lower = index * 10;
            int upper = index == 9 ? 100 : lower + 9;
            return lower + "-" + upper;
        }
    }
}
