package hsts.server.control;

import hsts.common.CourseSummaryDTO;
import hsts.common.ExamStatisticsDTO;
import hsts.common.ReportSummaryDTO;
import hsts.common.ReportTargetsDTO;
import hsts.common.ReportExportPayload;
import hsts.common.ReportExportResult;
import hsts.common.ScoreBandDTO;
import hsts.common.type.ReportType;
import hsts.common.type.UserRole;
import hsts.server.entity.Report;
import hsts.server.entity.User;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.ReportRepository;
import hsts.server.repository.UserRepository;
import hsts.server.report.ReportExportGenerator;

import java.io.File;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class ReportService {
    private static final String TARGET_DENIED =
            "Report target not found or access denied";

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final Clock clock;
    private final ReportExportGenerator exportGenerator = new ReportExportGenerator();

    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         CourseRepository courseRepository,
                         Clock clock) {
        this.reportRepository = Objects.requireNonNull(
                reportRepository,
                "reportRepository"
        );
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.courseRepository = Objects.requireNonNull(
                courseRepository,
                "courseRepository"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ReportSummaryDTO getMyAuthoredExamsReport(int authenticatedUserId) {
        User actor = requireActiveActor(authenticatedUserId);
        requireTeacherOrCoordinator(actor);
        return buildReport(
                ReportType.TEACHER_EXAMS,
                "Authored Exam Reports",
                actor.getUserId(),
                actor.getFullName(),
                reportRepository.findByExamAuthor(actor.getUserId())
        );
    }

    public ReportSummaryDTO getTeacherExamsReport(int authenticatedPrincipalId,
                                                   int teacherUserId) {
        requirePrincipal(authenticatedPrincipalId);
        User target = requireTargetUser(teacherUserId);
        if (!target.isActive()
                || (target.getRole() != UserRole.TEACHER
                && target.getRole() != UserRole.COORDINATOR)) {
            throw targetDenied();
        }
        return buildReport(
                ReportType.TEACHER_EXAMS,
                "Teacher Exam Reports",
                target.getUserId(),
                target.getFullName(),
                reportRepository.findByExamAuthor(target.getUserId())
        );
    }

    public ReportSummaryDTO getCourseExamsReport(int authenticatedPrincipalId,
                                                  int courseId) {
        requirePrincipal(authenticatedPrincipalId);
        requirePositiveTarget(courseId);
        CourseSummaryDTO target = courseRepository.findById(courseId)
                .orElseThrow(ReportService::targetDenied);
        return buildReport(
                ReportType.COURSE_EXAMS,
                "Course Exam Reports",
                target.getCourseId(),
                target.getCourseName(),
                reportRepository.findByCourse(target.getCourseId())
        );
    }

    public ReportSummaryDTO getStudentExamsReport(int authenticatedPrincipalId,
                                                   int studentUserId) {
        requirePrincipal(authenticatedPrincipalId);
        User target = requireTargetUser(studentUserId);
        if (!target.isActive() || target.getRole() != UserRole.STUDENT) {
            throw targetDenied();
        }
        return buildReport(
                ReportType.STUDENT_EXAMS,
                "Student Exam Reports",
                target.getUserId(),
                target.getFullName(),
                reportRepository.findByStudent(target.getUserId())
        );
    }

    public ReportSummaryDTO getExamExecutionReport(int authenticatedUserId,
                                                    int executionId) {
        User actor = requireActiveActor(authenticatedUserId);
        requirePositiveTarget(executionId);
        Report.ExecutionStatistics statistics = reportRepository
                .findByExecution(executionId)
                .orElseThrow(ReportService::targetDenied);

        if (actor.getRole() == UserRole.TEACHER
                || actor.getRole() == UserRole.COORDINATOR) {
            boolean authored = reportRepository.findByExamAuthor(actor.getUserId())
                    .stream()
                    .anyMatch(value -> value.getExecutionId() == executionId);
            if (!authored) {
                throw targetDenied();
            }
        } else if (actor.getRole() != UserRole.PRINCIPAL) {
            throw targetDenied();
        }

        return buildReport(
                ReportType.EXAM_EXECUTION,
                "Exam Execution Report",
                executionId,
                statistics.getExamTitle(),
                List.of(statistics)
        );
    }

    public ReportExportResult exportReport(int authenticatedUserId,
                                           ReportExportPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Report export data is required");
        }
        if (payload.getReportType() == null) {
            throw new IllegalArgumentException("Report type is required");
        }
        if (payload.getFormat() == null) {
            throw new IllegalArgumentException("Report export format is required");
        }
        if (payload.isComparison()) {
            requirePrincipal(authenticatedUserId);
        }
        ReportSummaryDTO report = loadExportReport(
                authenticatedUserId,
                payload.getReportType(),
                payload.getTargetId()
        );
        if (!payload.isComparison()) {
            return exportGenerator.generate(report, payload.getFormat());
        }
        ReportSummaryDTO comparison = loadExportReport(
                authenticatedUserId,
                payload.getReportType(),
                requireExportTarget(payload.getComparisonTargetId())
        );
        return exportGenerator.generateComparison(
                report,
                comparison,
                payload.getFormat()
        );
    }

    private ReportSummaryDTO loadExportReport(int authenticatedUserId,
                                              ReportType reportType,
                                              Integer targetId) {
        return switch (reportType) {
            case TEACHER_EXAMS -> targetId == null
                    ? getMyAuthoredExamsReport(authenticatedUserId)
                    : getTeacherExamsReport(authenticatedUserId, targetId);
            case COURSE_EXAMS -> getCourseExamsReport(
                    authenticatedUserId,
                    requireExportTarget(targetId)
            );
            case STUDENT_EXAMS -> getStudentExamsReport(
                    authenticatedUserId,
                    requireExportTarget(targetId)
            );
            case EXAM_EXECUTION -> getExamExecutionReport(
                    authenticatedUserId,
                    requireExportTarget(targetId)
            );
        };
    }

    public Report generateTeacherExamsReport(int teacherId) {
        throw legacyContextRequired();
    }

    public Report generateCourseExamsReport(int courseId) {
        throw legacyContextRequired();
    }

    public Report generateStudentExamsReport(int studentId) {
        throw legacyContextRequired();
    }

    public Report generateExamExecutionReport(int executionId) {
        throw legacyContextRequired();
    }

    public File exportReportToPDF(int reportId) {
        throw exportUnavailable();
    }

    public File exportReportToExcel(int reportId) {
        throw exportUnavailable();
    }

    private ReportSummaryDTO buildReport(
            ReportType type,
            String title,
            int targetId,
            String targetDisplayName,
            List<Report.ExecutionStatistics> statistics
    ) {
        LocalDateTime generatedAt = LocalDateTime.now(clock);
        Report report = new Report(
                0,
                type,
                generatedAt,
                title,
                targetId,
                targetDisplayName,
                statistics
        );
        return toDto(report);
    }

    private int requireExportTarget(Integer targetId) {
        if (targetId == null || targetId <= 0) {
            throw new IllegalArgumentException("Report target ID must be positive");
        }
        return targetId;
    }

    private ReportSummaryDTO toDto(Report report) {
        List<ExamStatisticsDTO> statistics = report.getExecutionStatistics()
                .stream()
                .map(this::toDto)
                .toList();
        return new ReportSummaryDTO(
                report.getReportType(),
                report.getTitle(),
                report.getGeneratedAt(),
                report.getTargetId(),
                report.getTargetDisplayName(),
                statistics
        );
    }

    private ExamStatisticsDTO toDto(Report.ExecutionStatistics statistics) {
        List<ScoreBandDTO> bands = statistics.getScoreBands().stream()
                .map(band -> new ScoreBandDTO(
                        band.getLowerBoundInclusive(),
                        band.getUpperBoundInclusive(),
                        band.getSubmissionCount()
                ))
                .toList();
        return new ExamStatisticsDTO(
                statistics.getExecutionId(),
                statistics.getExamId(),
                statistics.getExamVersionNo(),
                statistics.getExamCode(),
                statistics.getExamTitle(),
                statistics.getCourseId(),
                statistics.getCourseName(),
                statistics.getOpeningTime(),
                statistics.getClosingTime(),
                statistics.getPublishedSubmissionCount(),
                statistics.getAverageScore(),
                statistics.getMedianScore(),
                bands,
                statistics.getStartedSubmissionCount(),
                statistics.getSubmittedSubmissionCount(),
                statistics.getAutoSubmittedSubmissionCount()
        );
    }

    private User requireActiveActor(int userId) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found: " + userId
                ));
        if (!actor.isActive()) {
            throw new IllegalStateException("User account is blocked");
        }
        return actor;
    }

    private void requireTeacherOrCoordinator(User actor) {
        if (actor.getRole() != UserRole.TEACHER
                && actor.getRole() != UserRole.COORDINATOR) {
            throw new IllegalStateException(
                    "Report access requires teacher or coordinator role"
            );
        }
    }

    /**
     * Everything the Principal may run a report about, by name.
     *
     * <p>The Reports page used to require a numeric id typed by hand. This backs
     * pickers instead, so the Principal chooses "Rania Haddad" rather than
     * remembering that she is user 1005.</p>
     */
    public ReportTargetsDTO getReportTargets(int authenticatedPrincipalId) {
        requirePrincipal(authenticatedPrincipalId);
        return new ReportTargetsDTO(
                reportRepository.findReportTeachers(),
                reportRepository.findReportCourses(),
                reportRepository.findReportStudents(),
                reportRepository.findReportExecutions()
        );
    }

    private User requirePrincipal(int userId) {
        User actor = requireActiveActor(userId);
        if (actor.getRole() != UserRole.PRINCIPAL) {
            throw new IllegalStateException("Report comparison requires principal role");
        }
        return actor;
    }

    private User requireTargetUser(int userId) {
        requirePositiveTarget(userId);
        return userRepository.findById(userId)
                .orElseThrow(ReportService::targetDenied);
    }

    private void requirePositiveTarget(int targetId) {
        if (targetId <= 0) {
            throw targetDenied();
        }
    }

    private static IllegalStateException targetDenied() {
        return new IllegalStateException(TARGET_DENIED);
    }

    private static IllegalStateException legacyContextRequired() {
        return new IllegalStateException(
                "Authenticated report context is required"
        );
    }

    private static IllegalStateException exportUnavailable() {
        return new IllegalStateException("Report file export is not available");
    }
}
