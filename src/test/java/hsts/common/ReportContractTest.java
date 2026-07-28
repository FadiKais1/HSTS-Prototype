package hsts.common;

import hsts.common.type.ReportType;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ReportContractTest {
    private static final LocalDateTime OPENING = LocalDateTime.of(2026, 7, 1, 9, 0);
    private static final LocalDateTime CLOSING = OPENING.plusHours(2);

    @Test
    public void completeContractsMapEveryFieldAndPreserveScale() {
        BigDecimal average = new BigDecimal("73.2500");
        BigDecimal median = new BigDecimal("72.500");
        ExamStatisticsDTO statistics = statistics(3, average, median, counts(0, 1, 0, 0, 0, 0, 0, 2, 0, 0));
        ReportSummaryDTO report = new ReportSummaryDTO(
                ReportType.TEACHER_EXAMS,
                " Teacher report ",
                CLOSING,
                41,
                " Teacher One ",
                List.of(statistics)
        );
        ReportTargetPayload target = new ReportTargetPayload(41);

        assertEquals(101, statistics.getExecutionId());
        assertEquals(201, statistics.getExamId());
        assertEquals(3, statistics.getExamVersionNo());
        assertEquals("EX0001", statistics.getExamCode());
        assertEquals("Algebra Final", statistics.getExamTitle());
        assertEquals(301, statistics.getCourseId());
        assertEquals("Algebra", statistics.getCourseName());
        assertEquals(OPENING, statistics.getOpeningTime());
        assertEquals(CLOSING, statistics.getClosingTime());
        assertEquals(3, statistics.getPublishedSubmissionCount());
        assertEquals(average, statistics.getAverageScore());
        assertEquals(4, statistics.getAverageScore().scale());
        assertEquals(median, statistics.getMedianScore());
        assertEquals(3, statistics.getMedianScore().scale());
        assertEquals(5, statistics.getStartedSubmissionCount());
        assertEquals(2, statistics.getSubmittedSubmissionCount());
        assertEquals(1, statistics.getAutoSubmittedSubmissionCount());

        assertEquals(ReportType.TEACHER_EXAMS, report.getReportType());
        assertEquals("Teacher report", report.getTitle());
        assertEquals(CLOSING, report.getGeneratedAt());
        assertEquals(Integer.valueOf(41), report.getTargetId());
        assertEquals("Teacher One", report.getTargetDisplayName());
        assertEquals(List.of(statistics), report.getExamStatistics());
        assertEquals(41, target.getTargetId());
    }

    @Test
    public void serializationRoundTripsAllContracts() throws Exception {
        ScoreBandDTO band = new ScoreBandDTO(90, 100, 2);
        ScoreBandDTO bandCopy = roundTrip(band);
        assertEquals(90, bandCopy.getLowerBoundInclusive());
        assertEquals(100, bandCopy.getUpperBoundInclusive());
        assertEquals(2, bandCopy.getSubmissionCount());

        ExamStatisticsDTO statistics = statistics(
                2, new BigDecimal("51.500"), new BigDecimal("50.00"),
                counts(0, 0, 0, 0, 1, 1, 0, 0, 0, 0)
        );
        ExamStatisticsDTO statisticsCopy = roundTrip(statistics);
        assertEquals(new BigDecimal("51.500"), statisticsCopy.getAverageScore());
        assertEquals(3, statisticsCopy.getAverageScore().scale());
        assertEquals(10, statisticsCopy.getScoreBands().size());

        ReportSummaryDTO reportCopy = roundTrip(new ReportSummaryDTO(
                ReportType.EXAM_EXECUTION, "Execution", CLOSING,
                null, null, List.of(statistics)
        ));
        assertNull(reportCopy.getTargetId());
        assertNull(reportCopy.getTargetDisplayName());
        assertEquals(ReportType.EXAM_EXECUTION, reportCopy.getReportType());
        assertEquals(1, reportCopy.getExamStatistics().size());

        assertEquals(77, roundTrip(new ReportTargetPayload(77)).getTargetId());
    }

    @Test
    public void zeroResultExecutionRequiresNullScoresAndTenEmptyBands() {
        ExamStatisticsDTO statistics = statistics(0, null, null, counts());

        assertNull(statistics.getAverageScore());
        assertNull(statistics.getMedianScore());
        assertEquals(10, statistics.getScoreBands().size());
        assertTrue(statistics.getScoreBands().stream()
                .allMatch(band -> band.getSubmissionCount() == 0));
    }

    @Test
    public void oddAndEvenCalculatedMediansAreAcceptedWithoutRounding() {
        ExamStatisticsDTO odd = statistics(
                3, new BigDecimal("66.666"), new BigDecimal("75.00"),
                counts(0, 0, 0, 0, 0, 0, 0, 3, 0, 0)
        );
        ExamStatisticsDTO even = statistics(
                4, new BigDecimal("62.50"), new BigDecimal("62.5000"),
                counts(0, 0, 0, 0, 0, 0, 4, 0, 0, 0)
        );

        assertEquals(new BigDecimal("75.00"), odd.getMedianScore());
        assertEquals(new BigDecimal("62.5000"), even.getMedianScore());
        assertEquals(4, even.getMedianScore().scale());
    }

    @Test
    public void scoreBandsHaveExactRequiredBoundariesAndImmutableOrder() {
        List<ScoreBandDTO> bands = bands(counts());
        int[] expectedLower = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90};
        int[] expectedUpper = {9, 19, 29, 39, 49, 59, 69, 79, 89, 100};

        for (int index = 0; index < bands.size(); index++) {
            assertEquals(expectedLower[index], bands.get(index).getLowerBoundInclusive());
            assertEquals(expectedUpper[index], bands.get(index).getUpperBoundInclusive());
        }
        assertThrows(UnsupportedOperationException.class,
                () -> statistics(0, null, null, counts()).getScoreBands().clear());
    }

    @Test
    public void malformedDistributionsAndScoresAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExamStatisticsDTO(
                        1, 2, 1, "CODE01", "Exam", 3, "Course",
                        OPENING, CLOSING, 0, null, null,
                        bands(counts()).subList(0, 9), 1, 0, 0
                ));

        List<ScoreBandDTO> wrongOrder = new ArrayList<>(bands(counts()));
        wrongOrder.set(0, new ScoreBandDTO(1, 9, 0));
        assertThrows(IllegalArgumentException.class,
                () -> createWithBands(0, null, null, wrongOrder));
        assertThrows(IllegalArgumentException.class,
                () -> statistics(2, BigDecimal.ONE, BigDecimal.ONE,
                        counts(1, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> statistics(1, new BigDecimal("100.01"), BigDecimal.ONE,
                        counts(1, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> statistics(1, BigDecimal.ONE, new BigDecimal("-0.01"),
                        counts(1, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> statistics(0, BigDecimal.ZERO, null, counts()));
    }

    @Test
    public void invalidIdentifiersTimesAndCountersAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScoreBandDTO(-1, 9, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ScoreBandDTO(10, 9, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ScoreBandDTO(0, 9, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new ExamStatisticsDTO(
                        0, 2, 1, "CODE01", "Exam", 3, "Course",
                        OPENING, CLOSING, 0, null, null,
                        bands(counts()), 1, 0, 0
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new ExamStatisticsDTO(
                        1, 2, 1, "CODE01", "Exam", 3, "Course",
                        OPENING, OPENING, 0, null, null,
                        bands(counts()), 1, 0, 0
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new ExamStatisticsDTO(
                        1, 2, 1, "CODE01", "Exam", 3, "Course",
                        OPENING, CLOSING, 0, null, null,
                        bands(counts()), -1, 0, 0
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new ExamStatisticsDTO(
                        1, 2, 1, "CODE01", "Exam", 3, "Course",
                        OPENING, CLOSING, 0, null, null,
                        bands(counts()), 1, 1, 1
                ));
    }

    @Test
    public void collectionInputsAndOutputsAreDefensiveAndRejectNulls() {
        List<ScoreBandDTO> mutableBands = new ArrayList<>(bands(counts()));
        ExamStatisticsDTO statistics = createWithBands(0, null, null, mutableBands);
        mutableBands.clear();
        assertEquals(10, statistics.getScoreBands().size());
        assertThrows(UnsupportedOperationException.class,
                () -> statistics.getScoreBands().clear());

        List<ExamStatisticsDTO> mutableStatistics = new ArrayList<>(List.of(statistics));
        ReportSummaryDTO report = new ReportSummaryDTO(
                ReportType.COURSE_EXAMS, "Course", CLOSING,
                null, null, mutableStatistics
        );
        mutableStatistics.clear();
        assertEquals(1, report.getExamStatistics().size());
        assertThrows(UnsupportedOperationException.class,
                () -> report.getExamStatistics().clear());

        assertThrows(NullPointerException.class,
                () -> createWithBands(0, null, null, null));
        List<ScoreBandDTO> bandsWithNull = new ArrayList<>(bands(counts()));
        bandsWithNull.set(1, null);
        assertThrows(NullPointerException.class,
                () -> createWithBands(0, null, null, bandsWithNull));
        assertThrows(NullPointerException.class,
                () -> new ReportSummaryDTO(
                        ReportType.COURSE_EXAMS, "Course", CLOSING,
                        null, null, null
                ));
    }

    @Test
    public void reportAllowsNullableTargetAndEmptyStatistics() {
        ReportSummaryDTO report = new ReportSummaryDTO(
                ReportType.EXAM_EXECUTION, "Empty", CLOSING,
                null, null, List.of()
        );
        assertTrue(report.getExamStatistics().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new ReportSummaryDTO(
                        ReportType.EXAM_EXECUTION, "Empty", CLOSING,
                        0, null, List.of()
                ));
    }

    @Test
    public void contractsExposeNoSettersOrForbiddenFields() {
        List<Class<?>> contracts = List.of(
                ReportTargetPayload.class,
                ScoreBandDTO.class,
                ExamStatisticsDTO.class,
                ReportSummaryDTO.class
        );
        List<String> forbiddenFragments = List.of(
                "password", "identitynumber", "correctanswer", "selectedanswer",
                "answercorrectness", "feedback", "adjustmentreason", "reviewerid",
                "publisherid", "authenticateduserid", "actorid"
        );

        for (Class<?> contract : contracts) {
            assertTrue(Serializable.class.isAssignableFrom(contract));
            for (Method method : contract.getDeclaredMethods()) {
                assertFalse(method.getName().startsWith("set"));
            }
            for (Field field : contract.getDeclaredFields()) {
                String normalized = field.getName().toLowerCase(Locale.ROOT);
                assertFalse(forbiddenFragments.stream().anyMatch(normalized::contains));
                assertFalse(field.getType().getName().startsWith("hsts.server.entity"));
            }
        }

        assertArrayEquals(
                new String[]{"targetId"},
                Arrays.stream(ReportTargetPayload.class.getDeclaredFields())
                        .filter(field -> !field.isSynthetic()
                                && !field.getName().equals("serialVersionUID"))
                        .map(Field::getName)
                        .toArray(String[]::new)
        );
    }

    private static ExamStatisticsDTO statistics(int publishedCount,
                                                 BigDecimal average,
                                                 BigDecimal median,
                                                 int[] counts) {
        return createWithBands(publishedCount, average, median, bands(counts));
    }

    private static ExamStatisticsDTO createWithBands(int publishedCount,
                                                      BigDecimal average,
                                                      BigDecimal median,
                                                      List<ScoreBandDTO> bands) {
        return new ExamStatisticsDTO(
                101, 201, 3, "EX0001", "Algebra Final", 301, "Algebra",
                OPENING, CLOSING, publishedCount, average, median, bands,
                5, 2, 1
        );
    }

    private static List<ScoreBandDTO> bands(int[] counts) {
        if (counts.length != 10) {
            throw new IllegalArgumentException("Test counts must have ten values");
        }
        int[] lower = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90};
        int[] upper = {9, 19, 29, 39, 49, 59, 69, 79, 89, 100};
        List<ScoreBandDTO> bands = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            bands.add(new ScoreBandDTO(lower[index], upper[index], counts[index]));
        }
        return bands;
    }

    private static int[] counts(int... values) {
        if (values.length == 0) {
            return new int[10];
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
