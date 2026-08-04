package hsts.server.control;

import hsts.common.CourseSummaryDTO;
import hsts.common.ExamStatisticsDTO;
import hsts.common.ReportExportPayload;
import hsts.common.ReportExportResult;
import hsts.common.ReportSummaryDTO;
import hsts.common.type.ReportExportFormat;
import hsts.common.type.ReportType;
import hsts.common.type.UserStatus;
import hsts.server.entity.Coordinator;
import hsts.server.entity.Principal;
import hsts.server.entity.Report;
import hsts.server.entity.Student;
import hsts.server.entity.Teacher;
import hsts.server.entity.User;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.ReportRepository;
import hsts.server.repository.UserRepository;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ReportServiceTest {
    private static final String DENIED = "Report target not found or access denied";
    private static final Instant NOW = Instant.parse("2026-09-10T08:15:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Jerusalem"));

    @Test
    public void teacherAndCoordinatorReceiveOnlyTheirOwnAuthoredStatistics() {
        RecordingReportRepository reports = new RecordingReportRepository();
        Report.ExecutionStatistics first = statistics(21, "80.00", "80.00");
        Report.ExecutionStatistics second = statistics(22, "70.50", "70.50");
        reports.byAuthor.put(1002, List.of(first, second));
        reports.byAuthor.put(1003, List.of());
        ReportService service = service(reports, users(
                teacher(1002, "Teacher One", UserStatus.ACTIVE),
                coordinator(1003, "Coordinator One", UserStatus.ACTIVE)
        ), new RecordingCourseRepository());

        ReportSummaryDTO teacher = service.getMyAuthoredExamsReport(1002);
        ReportSummaryDTO coordinator = service.getMyAuthoredExamsReport(1003);

        assertReport(teacher, ReportType.TEACHER_EXAMS, "Authored Exam Reports",
                1002, "Teacher One", 2);
        assertEquals(List.of(21, 22), teacher.getExamStatistics().stream()
                .map(ExamStatisticsDTO::getExecutionId).toList());
        assertEquals(new BigDecimal("80.00"),
                teacher.getExamStatistics().get(0).getAverageScore());
        assertEquals(10, teacher.getExamStatistics().get(0).getScoreBands().size());
        assertReport(coordinator, ReportType.TEACHER_EXAMS,
                "Authored Exam Reports", 1003, "Coordinator One", 0);
        assertEquals(List.of(1002, 1003), reports.authorRequests);
    }

    @Test
    public void ownReportRejectsPrincipalStudentBlockedAndMissingActors() {
        ReportService service = service(new RecordingReportRepository(), users(
                principal(2001, "Principal", UserStatus.ACTIVE),
                student(2002, "Student", UserStatus.ACTIVE),
                teacher(2003, "Blocked", UserStatus.BLOCKED)
        ), new RecordingCourseRepository());

        assertMessage(() -> service.getMyAuthoredExamsReport(2001),
                "Report access requires teacher or coordinator role");
        assertMessage(() -> service.getMyAuthoredExamsReport(2002),
                "Report access requires teacher or coordinator role");
        assertMessage(() -> service.getMyAuthoredExamsReport(2003),
                "User account is blocked");
        assertMessage(() -> service.getMyAuthoredExamsReport(9999),
                "User not found: 9999");
    }

    @Test
    public void principalComparisonReportsValidateAuthoritativeTargetsAndAllowEmptyData() {
        RecordingReportRepository reports = new RecordingReportRepository();
        RecordingCourseRepository courses = new RecordingCourseRepository();
        courses.courses.put(31, new CourseSummaryDTO(
                31, 4, "BIO-101", "Biology", "Sciences", "10", "2026"
        ));
        ReportService service = service(reports, users(
                principal(3001, "Principal", UserStatus.ACTIVE),
                teacher(3002, "Selected Teacher", UserStatus.ACTIVE),
                coordinator(3003, "Selected Coordinator", UserStatus.ACTIVE),
                student(3004, "Selected Student", UserStatus.ACTIVE)
        ), courses);

        assertReport(service.getTeacherExamsReport(3001, 3002),
                ReportType.TEACHER_EXAMS, "Teacher Exam Reports",
                3002, "Selected Teacher", 0);
        assertReport(service.getTeacherExamsReport(3001, 3003),
                ReportType.TEACHER_EXAMS, "Teacher Exam Reports",
                3003, "Selected Coordinator", 0);
        assertReport(service.getCourseExamsReport(3001, 31),
                ReportType.COURSE_EXAMS, "Course Exam Reports",
                31, "Biology", 0);
        assertReport(service.getStudentExamsReport(3001, 3004),
                ReportType.STUDENT_EXAMS, "Student Exam Reports",
                3004, "Selected Student", 0);
    }

    @Test
    public void comparisonReportsRequirePrincipalAndConcealInvalidTargets() {
        RecordingCourseRepository courses = new RecordingCourseRepository();
        ReportService service = service(new RecordingReportRepository(), users(
                principal(4001, "Principal", UserStatus.ACTIVE),
                teacher(4002, "Teacher", UserStatus.ACTIVE),
                student(4003, "Student", UserStatus.ACTIVE),
                principal(4004, "Other Principal", UserStatus.ACTIVE),
                student(4005, "Blocked Student", UserStatus.BLOCKED)
        ), courses);

        assertMessage(() -> service.getTeacherExamsReport(4002, 4002),
                "Report comparison requires principal role");
        assertMessage(() -> service.getTeacherExamsReport(4001, 4003), DENIED);
        assertMessage(() -> service.getTeacherExamsReport(4001, 4004), DENIED);
        assertMessage(() -> service.getStudentExamsReport(4001, 4005), DENIED);
        assertMessage(() -> service.getCourseExamsReport(4001, 0), DENIED);
        assertMessage(() -> service.getCourseExamsReport(4001, 99), DENIED);
        assertMessage(() -> service.getStudentExamsReport(4001, 9999), DENIED);
    }

    @Test
    public void executionReportAllowsPrincipalAndExactExamAuthorOnly() {
        RecordingReportRepository reports = new RecordingReportRepository();
        Report.ExecutionStatistics statistics = statistics(51, "65.25", "65.25");
        reports.byExecution.put(51, statistics);
        reports.byAuthor.put(5002, List.of(statistics));
        reports.byAuthor.put(5003, List.of());
        ReportService service = service(reports, users(
                principal(5001, "Principal", UserStatus.ACTIVE),
                teacher(5002, "Author", UserStatus.ACTIVE),
                coordinator(5003, "Other Manager", UserStatus.ACTIVE),
                student(5004, "Student", UserStatus.ACTIVE)
        ), new RecordingCourseRepository());

        ReportSummaryDTO principal = service.getExamExecutionReport(5001, 51);
        ReportSummaryDTO author = service.getExamExecutionReport(5002, 51);

        assertReport(principal, ReportType.EXAM_EXECUTION,
                "Exam Execution Report", 51, "Exam 51", 1);
        assertReport(author, ReportType.EXAM_EXECUTION,
                "Exam Execution Report", 51, "Exam 51", 1);
        assertMessage(() -> service.getExamExecutionReport(5003, 51), DENIED);
        assertMessage(() -> service.getExamExecutionReport(5004, 51), DENIED);
        assertMessage(() -> service.getExamExecutionReport(5001, 99), DENIED);
    }

    @Test
    public void mappingUsesFixedClockAndCreatesImmutableDtoCollections() {
        RecordingReportRepository reports = new RecordingReportRepository();
        reports.byAuthor.put(6001, List.of(statistics(61, "33.30", "33.30")));
        ReportSummaryDTO result = service(reports, users(
                teacher(6001, "Teacher", UserStatus.ACTIVE)
        ), new RecordingCourseRepository()).getMyAuthoredExamsReport(6001);

        assertEquals(LocalDateTime.ofInstant(NOW, CLOCK.getZone()), result.getGeneratedAt());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getExamStatistics().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getExamStatistics().get(0).getScoreBands().clear());
    }

    @Test
    public void repositoryFailuresPropagateAndLegacyMethodsFailSafely() {
        RecordingReportRepository reports = new RecordingReportRepository();
        reports.failure = new IllegalStateException("repository unavailable");
        ReportService service = service(reports, users(
                teacher(7001, "Teacher", UserStatus.ACTIVE)
        ), new RecordingCourseRepository());

        assertMessage(() -> service.getMyAuthoredExamsReport(7001),
                "repository unavailable");
        assertSafeLegacy(() -> service.generateTeacherExamsReport(7001));
        assertSafeLegacy(() -> service.generateCourseExamsReport(1));
        assertSafeLegacy(() -> service.generateStudentExamsReport(1));
        assertSafeLegacy(() -> service.generateExamExecutionReport(1));
        assertMessage(() -> service.exportReportToPDF(1),
                "Report file export is not available");
        assertMessage(() -> service.exportReportToExcel(1),
                "Report file export is not available");
    }

    @Test
    public void exportReconstructsAuthorizedReportFromRepositoryData() {
        RecordingReportRepository reports = new RecordingReportRepository();
        reports.byAuthor.put(8001, List.of(statistics(71, "91.25", "91.25")));
        ReportService service = service(reports, users(
                teacher(8001, "Exporting Teacher", UserStatus.ACTIVE)
        ), new RecordingCourseRepository());

        ReportExportResult result = service.exportReport(
                8001,
                new ReportExportPayload(
                        ReportType.TEACHER_EXAMS, null, ReportExportFormat.PDF
                )
        );

        assertTrue(result.getSuggestedFilename().endsWith(".pdf"));
        assertEquals("application/pdf", result.getMediaType());
        assertEquals("%PDF", new String(result.getBytes(), 0, 4));
        assertEquals(List.of(8001), reports.authorRequests);
        assertMessage(() -> service.exportReport(
                        8001,
                        new ReportExportPayload(
                                ReportType.TEACHER_EXAMS, 8002,
                                ReportExportFormat.PDF
                        )),
                "Report comparison requires principal role");
    }

    @Test
    public void principalComparisonExportRebuildsBothAuthorizedTargets() {
        RecordingReportRepository reports = new RecordingReportRepository();
        reports.byAuthor.put(8101, List.of(statistics(72, "81.00", "81.00")));
        reports.byAuthor.put(8102, List.of(statistics(73, "76.50", "76.50")));
        ReportService service = service(reports, users(
                principal(8100, "Principal", UserStatus.ACTIVE),
                teacher(8101, "Primary Teacher", UserStatus.ACTIVE),
                teacher(8102, "Comparison Teacher", UserStatus.ACTIVE)
        ), new RecordingCourseRepository());

        ReportExportResult result = service.exportReport(
                8100,
                new ReportExportPayload(
                        ReportType.TEACHER_EXAMS,
                        8101,
                        8102,
                        ReportExportFormat.XLSX
                )
        );

        assertTrue(result.getSuggestedFilename().contains("comparison"));
        assertTrue(result.getSuggestedFilename().endsWith(".xlsx"));
        assertEquals(List.of(8101, 8102), reports.authorRequests);

        assertMessage(() -> service.exportReport(
                        8101,
                        new ReportExportPayload(
                                ReportType.TEACHER_EXAMS,
                                null,
                                8102,
                                ReportExportFormat.PDF
                        )),
                "Report comparison requires principal role");
    }

    private static ReportService service(RecordingReportRepository reports,
                                         RecordingUserRepository users,
                                         RecordingCourseRepository courses) {
        return new ReportService(reports, users, courses, CLOCK);
    }

    private static RecordingUserRepository users(User... users) {
        return new RecordingUserRepository(users);
    }

    private static Report.ExecutionStatistics statistics(int executionId,
                                                          String average,
                                                          String median) {
        List<Report.ScoreBand> bands = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            bands.add(new Report.ScoreBand(
                    index * 10,
                    index == 9 ? 100 : index * 10 + 9,
                    index == 6 ? 1 : 0
            ));
        }
        LocalDateTime opening = LocalDateTime.of(2026, 9, 1, 9, 0)
                .plusDays(executionId);
        return new Report.ExecutionStatistics(
                executionId, 10 + executionId, 2, "E" + executionId,
                "Exam " + executionId, 31, "Biology", opening,
                opening.plusHours(2), 1, new BigDecimal(average),
                new BigDecimal(median), bands, 2, 1, 0
        );
    }

    private static Teacher teacher(int id, String name, UserStatus status) {
        return Teacher.rehydrate(id, name, "t" + id + "@hsts.local", "hash", status);
    }

    private static Coordinator coordinator(int id, String name, UserStatus status) {
        return Coordinator.rehydrate(id, name, "c" + id + "@hsts.local", "hash", status);
    }

    private static Student student(int id, String name, UserStatus status) {
        return Student.rehydrate(id, name, "s" + id + "@hsts.local", "hash", status);
    }

    private static Principal principal(int id, String name, UserStatus status) {
        return Principal.rehydrate(id, name, "p" + id + "@hsts.local", "hash", status);
    }

    private static void assertReport(ReportSummaryDTO report, ReportType type,
                                     String title, int targetId, String displayName,
                                     int count) {
        assertEquals(type, report.getReportType());
        assertEquals(title, report.getTitle());
        assertEquals(Integer.valueOf(targetId), report.getTargetId());
        assertEquals(displayName, report.getTargetDisplayName());
        assertEquals(count, report.getExamStatistics().size());
    }

    private static void assertMessage(Runnable action, String message) {
        RuntimeException exception = assertThrows(RuntimeException.class, action::run);
        assertEquals(message, exception.getMessage());
    }

    private static void assertSafeLegacy(Runnable action) {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                action::run
        );
        assertEquals(IllegalStateException.class, exception.getClass());
        assertEquals("Authenticated report context is required", exception.getMessage());
    }

    private static final class RecordingUserRepository extends UserRepository {
        private final Map<Integer, User> users = new LinkedHashMap<>();

        private RecordingUserRepository(User... initialUsers) {
            for (User user : initialUsers) {
                users.put(user.getUserId(), user);
            }
        }

        @Override
        public Optional<User> findById(int userId) {
            return Optional.ofNullable(users.get(userId));
        }
    }

    private static final class RecordingCourseRepository extends CourseRepository {
        private final Map<Integer, CourseSummaryDTO> courses = new LinkedHashMap<>();

        @Override
        public Optional<CourseSummaryDTO> findById(int courseId) {
            return Optional.ofNullable(courses.get(courseId));
        }
    }

    private static final class RecordingReportRepository extends ReportRepository {
        private final Map<Integer, List<Report.ExecutionStatistics>> byAuthor =
                new LinkedHashMap<>();
        private final Map<Integer, Report.ExecutionStatistics> byExecution =
                new LinkedHashMap<>();
        private final List<Integer> authorRequests = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public List<Report.ExecutionStatistics> findByExamAuthor(int userId) {
            failIfNeeded();
            authorRequests.add(userId);
            return byAuthor.getOrDefault(userId, List.of());
        }

        @Override
        public List<Report.ExecutionStatistics> findByCourse(int courseId) {
            failIfNeeded();
            return List.of();
        }

        @Override
        public List<Report.ExecutionStatistics> findByStudent(int studentUserId) {
            failIfNeeded();
            return List.of();
        }

        @Override
        public Optional<Report.ExecutionStatistics> findByExecution(int executionId) {
            failIfNeeded();
            return Optional.ofNullable(byExecution.get(executionId));
        }

        private void failIfNeeded() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
