package hsts.server.net;

import hsts.common.ReportSummaryDTO;
import hsts.common.ReportTargetPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.type.ReportType;
import hsts.server.control.AuthService;
import hsts.server.control.ExamManagementService;
import hsts.server.control.ReportService;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.ReportRepository;
import hsts.server.repository.UserRepository;
import hsts.server.support.InMemoryQuestionRepository;
import hsts.server.support.InMemoryUserRepository;
import org.junit.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ReportServerRoutingTest {
    @Test
    public void authenticatedRoutesForwardConnectionIdentityTargetsAndMessages() {
        RecordingReportService service = new RecordingReportService();
        Server server = server(service);
        int authenticatedUserId = 9001;

        assertSuccess(server.handleAuthenticatedRequest(new Request(
                RequestType.GET_MY_AUTHORED_EXAMS_REPORT, null
        ), authenticatedUserId), "Authored exam report loaded", service.result);
        assertSuccess(target(server, RequestType.GET_TEACHER_EXAMS_REPORT,
                authenticatedUserId, 1002), "Teacher exam report loaded", service.result);
        assertSuccess(target(server, RequestType.GET_COURSE_EXAMS_REPORT,
                authenticatedUserId, 31), "Course exam report loaded", service.result);
        assertSuccess(target(server, RequestType.GET_STUDENT_EXAMS_REPORT,
                authenticatedUserId, 1001), "Student exam report loaded", service.result);
        assertSuccess(target(server, RequestType.GET_EXAM_EXECUTION_REPORT,
                authenticatedUserId, 81), "Exam execution report loaded", service.result);

        assertEquals(List.of(9001, 9001, 9001, 9001, 9001), service.actorIds);
        assertEquals(List.of(1002, 31, 1001, 81), service.targetIds);
    }

    @Test
    public void reportPayloadValidationUsesExactMessagesWithoutInvokingService() {
        RecordingReportService service = new RecordingReportService();
        Server server = server(service);

        assertError(server.handleAuthenticatedRequest(new Request(
                RequestType.GET_MY_AUTHORED_EXAMS_REPORT, "wrong"
        ), 1), "Authored exam report payload must be empty");

        for (RequestType type : targetTypes()) {
            assertError(server.handleAuthenticatedRequest(
                    new Request(type, null), 1
            ), "Report target payload is required");
            assertError(server.handleAuthenticatedRequest(
                    new Request(type, Integer.valueOf(1)), 1
            ), "Report target payload is required");
        }
        assertEquals(0, service.calls);
    }

    @Test
    public void contextFreeDispatchRejectsEveryReportRouteWithoutCallingService() {
        RecordingReportService service = new RecordingReportService();
        Server server = server(service);

        assertError(server.handleRequest(new Request(
                RequestType.GET_MY_AUTHORED_EXAMS_REPORT, null
        )), "Authentication context required");
        for (RequestType type : targetTypes()) {
            assertError(server.handleRequest(new Request(
                    type, new ReportTargetPayload(1)
            )), "Authentication context required");
        }
        assertEquals(0, service.calls);
    }

    @Test
    public void serviceMessagesArePreservedAndBlankMessagesUseSafeFallback() {
        RecordingReportService service = new RecordingReportService();
        Server server = server(service);
        service.failure = new IllegalStateException("Report denied");
        assertError(server.handleAuthenticatedRequest(new Request(
                RequestType.GET_MY_AUTHORED_EXAMS_REPORT, null
        ), 1), "Report denied");

        service.failure = new IllegalStateException("  ");
        assertError(target(server, RequestType.GET_COURSE_EXAMS_REPORT, 1, 31),
                "Request failed");
    }

    @Test
    public void compatibleServerWithoutReportServiceFailsClearly() {
        Server server = new Server(
                0,
                new ExamManagementService(new InMemoryQuestionRepository()),
                new AuthService(new InMemoryUserRepository())
        );

        assertError(server.handleAuthenticatedRequest(new Request(
                RequestType.GET_MY_AUTHORED_EXAMS_REPORT, null
        ), 1), "Report service is not configured");
    }

    private static Response target(Server server, RequestType type, int actorId,
                                   int targetId) {
        return server.handleAuthenticatedRequest(
                new Request(type, new ReportTargetPayload(targetId)),
                actorId
        );
    }

    private static List<RequestType> targetTypes() {
        return List.of(
                RequestType.GET_TEACHER_EXAMS_REPORT,
                RequestType.GET_COURSE_EXAMS_REPORT,
                RequestType.GET_STUDENT_EXAMS_REPORT,
                RequestType.GET_EXAM_EXECUTION_REPORT
        );
    }

    private static Server server(ReportService reportService) {
        return new Server(
                0,
                new ExamManagementService(new InMemoryQuestionRepository()),
                new AuthService(new InMemoryUserRepository()),
                null,
                reportService
        );
    }

    private static void assertSuccess(Response response, String message,
                                      Object payload) {
        assertTrue(response.isSuccess());
        assertEquals(ResponseStatus.SUCCESS, response.getStatus());
        assertEquals(message, response.getMessage());
        assertSame(payload, response.getPayload());
    }

    private static void assertError(Response response, String message) {
        assertFalse(response.isSuccess());
        assertEquals(ResponseStatus.ERROR, response.getStatus());
        assertEquals(message, response.getMessage());
        assertNull(response.getPayload());
    }

    private static final class RecordingReportService extends ReportService {
        private final ReportSummaryDTO result = new ReportSummaryDTO(
                ReportType.TEACHER_EXAMS,
                "Report",
                LocalDateTime.of(2026, 9, 1, 10, 0),
                1,
                "Target",
                List.of()
        );
        private final List<Integer> actorIds = new java.util.ArrayList<>();
        private final List<Integer> targetIds = new java.util.ArrayList<>();
        private RuntimeException failure;
        private int calls;

        private RecordingReportService() {
            super(
                    new ReportRepository(),
                    new UserRepository(),
                    new CourseRepository(),
                    Clock.systemUTC()
            );
        }

        @Override
        public ReportSummaryDTO getMyAuthoredExamsReport(int actorId) {
            record(actorId);
            return result;
        }

        @Override
        public ReportSummaryDTO getTeacherExamsReport(int actorId, int targetId) {
            return record(actorId, targetId);
        }

        @Override
        public ReportSummaryDTO getCourseExamsReport(int actorId, int targetId) {
            return record(actorId, targetId);
        }

        @Override
        public ReportSummaryDTO getStudentExamsReport(int actorId, int targetId) {
            return record(actorId, targetId);
        }

        @Override
        public ReportSummaryDTO getExamExecutionReport(int actorId, int targetId) {
            return record(actorId, targetId);
        }

        private ReportSummaryDTO record(int actorId, int targetId) {
            targetIds.add(targetId);
            record(actorId);
            return result;
        }

        private void record(int actorId) {
            calls++;
            actorIds.add(actorId);
            if (failure != null) {
                throw failure;
            }
        }
    }
}
