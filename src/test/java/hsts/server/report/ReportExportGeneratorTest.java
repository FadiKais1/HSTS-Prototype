package hsts.server.report;

import hsts.common.ExamStatisticsDTO;
import hsts.common.ReportExportResult;
import hsts.common.ReportSummaryDTO;
import hsts.common.ScoreBandDTO;
import hsts.common.type.ReportExportFormat;
import hsts.common.type.ReportType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReportExportGeneratorTest {
    @Test
    public void pdfContainsAuthoritativeRowsBandsAndRepeatedPageHeaders()
            throws Exception {
        List<ExamStatisticsDTO> manyRows = new ArrayList<>();
        for (int index = 0; index < 55; index++) manyRows.add(statistics());
        ReportSummaryDTO report = report(manyRows);

        ReportExportResult result = new ReportExportGenerator().generate(
                report, ReportExportFormat.PDF
        );

        assertEquals("application/pdf", result.getMediaType());
        assertTrue(result.getSuggestedFilename().endsWith(".pdf"));
        try (PDDocument document = Loader.loadPDF(result.getBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(document.getNumberOfPages() > 1);
            assertTrue(text.contains("Teacher Exam Results"));
            assertTrue(text.contains("Report type:"));
            assertTrue(text.contains("Teacher exams"));
            assertTrue(text.contains("Execution Statistics"));
            assertTrue(text.contains("Score Distribution"));
            assertTrue(text.contains("RLYQ"));
            assertTrue(text.contains("Algebra Midterm"));
            assertTrue(text.contains("Legacy Course"));
            assertTrue(text.contains("82.50"));
            assertTrue(text.contains("90-100"));
            assertTrue(text.contains("N/A") || text.contains("82.50"));
            assertTrue(occurrences(text, "Code") >= document.getNumberOfPages());
            assertTrue(text.contains("Page 1 of "));
            assertTrue(!text.contains("RLYQ | Algebra"));
            assertTrue(!text.contains("T e a c h e r"));

            BufferedImage rendered = new PDFRenderer(document)
                    .renderImageWithDPI(0, 96);
            assertTrue(rendered.getWidth() > rendered.getHeight());
            assertTrue(nonWhitePixels(rendered) > 10_000);
        }
    }

    @Test
    public void xlsxUsesStructuredNumericCellsAndAllTenBands() throws Exception {
        ReportExportResult result = new ReportExportGenerator().generate(
                report(List.of(statistics())), ReportExportFormat.XLSX
        );

        assertEquals(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                result.getMediaType()
        );
        assertTrue(result.getSuggestedFilename().endsWith(".xlsx"));
        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(result.getBytes()))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertEquals("Execution Statistics", workbook.getSheetAt(0).getSheetName());
            assertEquals("Score Distribution", workbook.getSheetAt(1).getSheetName());
            assertEquals(CellType.NUMERIC,
                    workbook.getSheetAt(0).getRow(2).getCell(8).getCellType());
            assertEquals(82.50,
                    workbook.getSheetAt(0).getRow(2).getCell(8).getNumericCellValue(),
                    0.0);
            assertEquals(11, workbook.getSheetAt(1).getPhysicalNumberOfRows());
            assertEquals(90.0,
                    workbook.getSheetAt(1).getRow(10).getCell(2).getNumericCellValue(),
                    0.0);
            assertEquals(1.0,
                    workbook.getSheetAt(1).getRow(10).getCell(4).getNumericCellValue(),
                    0.0);
        }
    }

    @Test
    public void comparisonExportContainsBothTargetsInPdfAndSeparateExcelSheets()
            throws Exception {
        ReportSummaryDTO primary = report(List.of(statistics()));
        ReportSummaryDTO comparison = new ReportSummaryDTO(
                ReportType.TEACHER_EXAMS,
                "Teacher Exam Results",
                LocalDateTime.of(2026, 8, 5, 12, 31),
                1102,
                "Second Teacher",
                List.of(statistics())
        );
        ReportExportGenerator generator = new ReportExportGenerator();

        ReportExportResult pdf = generator.generateComparison(
                primary,
                comparison,
                ReportExportFormat.PDF
        );
        assertTrue(pdf.getSuggestedFilename().contains("comparison"));
        try (PDDocument document = Loader.loadPDF(pdf.getBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Comparison Summary"));
            assertTrue(text.contains("Development Teacher"));
            assertTrue(text.contains("Second Teacher"));
            assertTrue(text.contains(
                    "Development Teacher: 1 executions, average 82.50, "
                            + "median 82.50, 1 submissions."
            ));
            assertTrue(text.contains("The two averages are equal."));
            assertTrue(text.contains("HSTS Report Comparison"));
            assertTrue(document.getNumberOfPages() >= 3);
        }

        ReportExportResult xlsx = generator.generateComparison(
                primary,
                comparison,
                ReportExportFormat.XLSX
        );
        assertTrue(xlsx.getSuggestedFilename().contains("comparison"));
        try (Workbook workbook = WorkbookFactory.create(
                new ByteArrayInputStream(xlsx.getBytes()))) {
            assertEquals(5, workbook.getNumberOfSheets());
            assertEquals("Comparison Overview", workbook.getSheetAt(0).getSheetName());
            assertEquals("Primary Statistics", workbook.getSheetAt(1).getSheetName());
            assertEquals("Primary Distribution", workbook.getSheetAt(2).getSheetName());
            assertEquals("Comparison Statistics", workbook.getSheetAt(3).getSheetName());
            assertEquals("Comparison Distribution", workbook.getSheetAt(4).getSheetName());
            assertEquals("Development Teacher",
                    workbook.getSheetAt(0).getRow(3).getCell(2).getStringCellValue());
            assertEquals("Second Teacher",
                    workbook.getSheetAt(0).getRow(4).getCell(2).getStringCellValue());
            assertEquals("Comparison Summary",
                    workbook.getSheetAt(0).getRow(6).getCell(0).getStringCellValue());
            assertTrue(workbook.getSheetAt(0).getRow(7).getCell(0)
                    .getStringCellValue().contains(
                            "Development Teacher: 1 executions, average 82.50"
                    ));
            assertEquals("The two averages are equal.",
                    workbook.getSheetAt(0).getRow(9).getCell(0)
                            .getStringCellValue());
            assertEquals(11,
                    workbook.getSheet("Primary Distribution")
                            .getPhysicalNumberOfRows());
            assertEquals(11,
                    workbook.getSheet("Comparison Distribution")
                            .getPhysicalNumberOfRows());
        }
    }

    @Test
    public void nullStatisticsAreExportedAsNaInBothFormats() throws Exception {
        ExamStatisticsDTO empty = new ExamStatisticsDTO(
                82, 41, 1, "NONE", "Empty Exam", 7, "Legacy Course",
                LocalDateTime.of(2026, 8, 2, 9, 0),
                LocalDateTime.of(2026, 8, 2, 11, 0),
                0, null, null, emptyBands(), 0, 0, 0
        );
        ReportSummaryDTO report = report(List.of(empty));
        ReportExportGenerator generator = new ReportExportGenerator();

        try (PDDocument document = Loader.loadPDF(
                generator.generate(report, ReportExportFormat.PDF).getBytes())) {
            assertTrue(occurrences(
                    new PDFTextStripper().getText(document), "N/A"
            ) >= 2);
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(
                generator.generate(report, ReportExportFormat.XLSX).getBytes()))) {
            assertEquals("N/A", workbook.getSheetAt(0).getRow(2).getCell(8)
                    .getStringCellValue());
            assertEquals("N/A", workbook.getSheetAt(0).getRow(2).getCell(9)
                    .getStringCellValue());
        }
    }

    private static ReportSummaryDTO report(List<ExamStatisticsDTO> rows) {
        return new ReportSummaryDTO(
                ReportType.TEACHER_EXAMS, "Teacher Exam Results",
                LocalDateTime.of(2026, 8, 5, 12, 30), null,
                "Development Teacher", rows
        );
    }

    private static ExamStatisticsDTO statistics() {
        List<ScoreBandDTO> bands = emptyBands();
        bands.set(9, new ScoreBandDTO(90, 100, 1));
        return new ExamStatisticsDTO(
                81, 40, 3, "RLYQ", "Algebra Midterm", 7, "Legacy Course",
                LocalDateTime.of(2026, 8, 1, 9, 0),
                LocalDateTime.of(2026, 8, 1, 11, 0),
                1, new BigDecimal("82.50"), new BigDecimal("82.50"), bands,
                2, 1, 1
        );
    }

    private static List<ScoreBandDTO> emptyBands() {
        int[] lower = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90};
        int[] upper = {9, 19, 29, 39, 49, 59, 69, 79, 89, 100};
        List<ScoreBandDTO> bands = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            bands.add(new ScoreBandDTO(lower[index], upper[index], 0));
        }
        return bands;
    }

    private static int occurrences(String value, String target) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    private static long nonWhitePixels(BufferedImage image) {
        long count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00FFFFFF) != 0x00FFFFFF) count++;
            }
        }
        return count;
    }
}
