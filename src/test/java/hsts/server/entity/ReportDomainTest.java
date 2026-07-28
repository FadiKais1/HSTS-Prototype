package hsts.server.entity;

import hsts.common.type.ReportType;
import org.junit.Test;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ReportDomainTest {
    private static final LocalDateTime OPENING = LocalDateTime.of(2026, 7, 1, 9, 0);
    private static final LocalDateTime CLOSING = OPENING.plusHours(2);

    @Test
    public void completeReportMapsStateAndPreservesExecutionOrder() {
        Report.ExecutionStatistics first = statistics(11, 2);
        Report.ExecutionStatistics second = statistics(12, 2);
        Report report = new Report(
                0, ReportType.TEACHER_EXAMS, CLOSING, " Teacher report ",
                41, " Teacher One ", List.of(first, second)
        );

        assertEquals(0, report.getReportId());
        assertEquals(ReportType.TEACHER_EXAMS, report.getReportType());
        assertEquals(CLOSING, report.getGeneratedAt());
        assertEquals("Teacher report", report.getTitle());
        assertEquals(Integer.valueOf(41), report.getTargetId());
        assertEquals("Teacher One", report.getTargetDisplayName());
        assertEquals(11, report.getExecutionStatistics().get(0).getExecutionId());
        assertEquals(12, report.getExecutionStatistics().get(1).getExecutionId());
        assertEquals(
                "Teacher report [TEACHER_EXAMS]: 2 execution(s)",
                report.getContent()
        );
    }

    @Test
    public void nestedStateIsDeeplyCopiedAndImmutable() {
        List<Report.ScoreBand> mutableBands = new ArrayList<>(bands(2));
        Report.ExecutionStatistics supplied = new Report.ExecutionStatistics(
                11, 21, 3, "EX0001", "Exam", 31, "Course",
                OPENING, CLOSING, 2, new BigDecimal("50.00"),
                new BigDecimal("50.000"), mutableBands, 3, 1, 1
        );
        List<Report.ExecutionStatistics> mutableStatistics =
                new ArrayList<>(List.of(supplied));
        Report report = new Report(
                0, ReportType.EXAM_EXECUTION, CLOSING, "Execution",
                null, null, mutableStatistics
        );

        mutableBands.clear();
        mutableStatistics.clear();
        Report.ExecutionStatistics exposed = report.getExecutionStatistics().get(0);
        assertNotSame(supplied, exposed);
        assertEquals(10, exposed.getScoreBands().size());
        assertNotSame(exposed.getScoreBands().get(0), supplied.getScoreBands().get(0));
        assertThrows(UnsupportedOperationException.class,
                () -> report.getExecutionStatistics().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> exposed.getScoreBands().clear());
        assertEquals(new BigDecimal("50.000"), exposed.getMedianScore());
        assertEquals(3, exposed.getMedianScore().scale());
    }

    @Test
    public void emptyAndCompatibilityReportsAreSafe() {
        Report empty = new Report(
                0, ReportType.COURSE_EXAMS, CLOSING, "Course report",
                null, null, List.of()
        );
        Report compatibility = new Report();

        assertTrue(empty.getExecutionStatistics().isEmpty());
        assertEquals("Course report [COURSE_EXAMS]: 0 execution(s)", empty.getContent());
        assertEquals(0, compatibility.getReportId());
        assertNull(compatibility.getReportType());
        assertTrue(compatibility.getExecutionStatistics().isEmpty());
        assertEquals("", compatibility.getContent());
    }

    @Test
    public void domainRejectsInvalidStatisticsAndNullCollections() {
        assertThrows(IllegalArgumentException.class,
                () -> new Report(
                        0, ReportType.EXAM_EXECUTION, CLOSING, "Report",
                        null, null, null
                ));
        List<Report.ExecutionStatistics> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> new Report(
                        0, ReportType.EXAM_EXECUTION, CLOSING, "Report",
                        null, null, withNull
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new Report.ExecutionStatistics(
                        1, 2, 1, "EX0001", "Exam", 3, "Course",
                        OPENING, CLOSING, 1, BigDecimal.ONE, BigDecimal.ONE,
                        bands(0), 1, 0, 0
                ));
        List<Report.ScoreBand> wrongOrder = new ArrayList<>(bands(0));
        wrongOrder.set(0, new Report.ScoreBand(1, 9, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Report.ExecutionStatistics(
                        1, 2, 1, "EX0001", "Exam", 3, "Course",
                        OPENING, CLOSING, 0, null, null,
                        wrongOrder, 1, 0, 0
                ));
    }

    @Test
    public void reportDomainHasNoSerializationOrForbiddenDependencies() {
        assertFalse(Serializable.class.isAssignableFrom(Report.class));
        assertFalse(Serializable.class.isAssignableFrom(Report.ScoreBand.class));
        assertFalse(Serializable.class.isAssignableFrom(Report.ExecutionStatistics.class));

        List<Class<?>> domainTypes = List.of(
                Report.class,
                Report.ScoreBand.class,
                Report.ExecutionStatistics.class
        );
        List<String> forbiddenPrefixes = List.of(
                "hsts.common.ExamStatisticsDTO",
                "java.sql.",
                "hsts.server.repository.",
                "javafx.",
                "ocsf.",
                "hsts.client.",
                "java.io.File",
                "java.nio.file."
        );
        for (Class<?> type : domainTypes) {
            for (Field field : type.getDeclaredFields()) {
                String fieldType = field.getType().getName();
                assertFalse(forbiddenPrefixes.stream().anyMatch(fieldType::startsWith));
            }
        }
    }

    private static Report.ExecutionStatistics statistics(int executionId,
                                                          int publishedCount) {
        return new Report.ExecutionStatistics(
                executionId, 21, 3, "EX0001", "Exam", 31, "Course",
                OPENING, CLOSING, publishedCount, new BigDecimal("50.00"),
                new BigDecimal("50.00"), bands(publishedCount), 3, 1, 1
        );
    }

    private static List<Report.ScoreBand> bands(int publishedCount) {
        int[] lower = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90};
        int[] upper = {9, 19, 29, 39, 49, 59, 69, 79, 89, 100};
        List<Report.ScoreBand> bands = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            bands.add(new Report.ScoreBand(
                    lower[index], upper[index], index == 5 ? publishedCount : 0
            ));
        }
        return bands;
    }
}
