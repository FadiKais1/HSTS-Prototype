package hsts.server.net;

import hsts.common.CreateExamPayload;
import hsts.common.ExamDTO;
import hsts.common.ExamQuestionSelectionPayload;
import hsts.common.ExamSummaryDTO;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.type.ExamStatus;
import hsts.server.control.AuthService;
import hsts.server.control.ExamManagementService;
import hsts.server.support.InMemoryQuestionRepository;
import hsts.server.support.InMemoryUserRepository;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ExamServerRoutingTest {
    @Test
    public void authenticatedRoutesForwardConnectionIdentityAndReturnExactResponses() {
        int authenticatedUserId = 7101;
        RecordingExamManagementService service = new RecordingExamManagementService();
        List<ExamSummaryDTO> teacherExams = List.of(summary(11, authenticatedUserId));
        List<ExamSummaryDTO> pendingExams = List.of(summary(12, authenticatedUserId));
        ExamDTO teacherExam = exam(11, authenticatedUserId);
        ExamDTO createdExam = exam(13, authenticatedUserId);
        ExamDTO pendingExam = exam(12, authenticatedUserId);
        CreateExamPayload createPayload = createPayload();
        service.setTeacherExams(teacherExams);
        service.setPendingExams(pendingExams);
        service.setTeacherExam(teacherExam);
        service.setCreatedExam(createdExam);
        service.setCoordinatorExam(pendingExam);
        Server server = server(service);

        Response listMine = server.handleAuthenticatedRequest(
                new Request(RequestType.LIST_MY_EXAMS, null),
                authenticatedUserId
        );
        Response getMine = server.handleAuthenticatedRequest(
                new Request(RequestType.GET_MY_EXAM, 11),
                authenticatedUserId
        );
        Response create = server.handleAuthenticatedRequest(
                new Request(RequestType.CREATE_EXAM, createPayload),
                authenticatedUserId
        );
        Response listPending = server.handleAuthenticatedRequest(
                new Request(RequestType.LIST_PENDING_EXAMS, null),
                authenticatedUserId
        );
        Response getPending = server.handleAuthenticatedRequest(
                new Request(RequestType.GET_PENDING_EXAM, 12),
                authenticatedUserId
        );

        assertSuccess(listMine, "Exams loaded successfully", teacherExams);
        assertSuccess(getMine, "Exam loaded successfully", teacherExam);
        assertSuccess(create, "Exam created successfully", createdExam);
        assertSuccess(listPending, "Pending exams loaded successfully", pendingExams);
        assertSuccess(getPending, "Pending exam loaded successfully", pendingExam);

        assertEquals(authenticatedUserId, service.getLastMyExamsUserId());
        assertEquals(authenticatedUserId, service.getLastTeacherExamUserId());
        assertEquals(11, service.getLastTeacherExamId());
        assertEquals(authenticatedUserId, service.getLastCreateUserId());
        assertSame(createPayload, service.getLastCreatePayload());
        assertEquals(authenticatedUserId, service.getLastPendingExamsUserId());
        assertEquals(authenticatedUserId, service.getLastCoordinatorExamUserId());
        assertEquals(12, service.getLastCoordinatorExamId());
    }

    @Test
    public void wrongOrMissingPayloadsBecomeErrorsWithoutInvokingService() {
        RecordingExamManagementService service = new RecordingExamManagementService();
        Server server = server(service);

        Response listMine = server.handleAuthenticatedRequest(
                new Request(RequestType.LIST_MY_EXAMS, "unexpected"), 7201
        );
        Response getMineMissing = server.handleAuthenticatedRequest(
                new Request(RequestType.GET_MY_EXAM, null), 7201
        );
        Response getMineWrong = server.handleAuthenticatedRequest(
                new Request(RequestType.GET_MY_EXAM, "11"), 7201
        );
        Response createMissing = server.handleAuthenticatedRequest(
                new Request(RequestType.CREATE_EXAM, null), 7201
        );
        Response createWrong = server.handleAuthenticatedRequest(
                new Request(RequestType.CREATE_EXAM, "exam data"), 7201
        );
        Response listPending = server.handleAuthenticatedRequest(
                new Request(RequestType.LIST_PENDING_EXAMS, 12), 7201
        );
        Response getPendingMissing = server.handleAuthenticatedRequest(
                new Request(RequestType.GET_PENDING_EXAM, null), 7201
        );
        Response getPendingWrong = server.handleAuthenticatedRequest(
                new Request(RequestType.GET_PENDING_EXAM, "12"), 7201
        );

        assertError(listMine, "Request payload must be empty");
        assertError(getMineMissing, "Exam ID is required");
        assertError(getMineWrong, "Exam ID is required");
        assertError(createMissing, "Exam creation data is missing");
        assertError(createWrong, "Exam creation data is missing");
        assertError(listPending, "Request payload must be empty");
        assertError(getPendingMissing, "Exam ID is required");
        assertError(getPendingWrong, "Exam ID is required");
        assertEquals(0, service.getTotalCalls());
    }

    @Test
    public void serviceExceptionsFromEveryExamRouteBecomeExactErrorResponses() {
        RuntimeException failure = new IllegalStateException("Exam infrastructure unavailable");
        RecordingExamManagementService service = new RecordingExamManagementService();
        service.setFailure(failure);
        Server server = server(service);

        for (Request request : validExamRequests()) {
            Response response = server.handleAuthenticatedRequest(request, 7301);
            assertError(response, "Exam infrastructure unavailable");
        }
        assertEquals(5, service.getTotalCalls());
    }

    @Test
    public void contextFreeDispatchRejectsEveryExamRouteWithoutCallingService() {
        RecordingExamManagementService service = new RecordingExamManagementService();
        Server server = server(service);

        for (Request request : validExamRequests()) {
            Response response = server.handleRequest(request);
            assertError(response, "Authentication context required");
        }
        assertEquals(0, service.getTotalCalls());
    }

    private static List<Request> validExamRequests() {
        return List.of(
                new Request(RequestType.LIST_MY_EXAMS, null),
                new Request(RequestType.GET_MY_EXAM, 11),
                new Request(RequestType.CREATE_EXAM, createPayload()),
                new Request(RequestType.LIST_PENDING_EXAMS, null),
                new Request(RequestType.GET_PENDING_EXAM, 12)
        );
    }

    private static Server server(ExamManagementService service) {
        return new Server(
                0,
                service,
                new AuthService(new InMemoryUserRepository())
        );
    }

    private static CreateExamPayload createPayload() {
        return new CreateExamPayload(
                7,
                "Midterm",
                60,
                "",
                "Read carefully",
                List.of(new ExamQuestionSelectionPayload(41, 2, 1, 100))
        );
    }

    private static ExamSummaryDTO summary(int examId, int creatorId) {
        return new ExamSummaryDTO(
                examId, "ABC123", 7, "Course", 3, "Subject", creatorId,
                "Creator", 1, "Exam", 60, 100, ExamStatus.DRAFT,
                LocalDateTime.of(2026, 7, 1, 10, 0), null, null, null
        );
    }

    private static ExamDTO exam(int examId, int creatorId) {
        return new ExamDTO(
                examId, "ABC123", 7, "Course", 3, "Subject", creatorId,
                "Creator", 1, "Exam", 60, "", "Read carefully", 100,
                ExamStatus.DRAFT, LocalDateTime.of(2026, 7, 1, 10, 0),
                null, null, null, null, null, List.of()
        );
    }

    private static void assertSuccess(Response response, String message, Object payload) {
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

    private static final class RecordingExamManagementService
            extends ExamManagementService {
        private List<ExamSummaryDTO> teacherExams = List.of();
        private List<ExamSummaryDTO> pendingExams = List.of();
        private ExamDTO teacherExam;
        private ExamDTO createdExam;
        private ExamDTO coordinatorExam;
        private RuntimeException failure;
        private int myExamsCalls;
        private int teacherExamCalls;
        private int createCalls;
        private int pendingExamsCalls;
        private int coordinatorExamCalls;
        private int lastMyExamsUserId;
        private int lastTeacherExamUserId;
        private int lastTeacherExamId;
        private int lastCreateUserId;
        private CreateExamPayload lastCreatePayload;
        private int lastPendingExamsUserId;
        private int lastCoordinatorExamUserId;
        private int lastCoordinatorExamId;

        private RecordingExamManagementService() {
            super(new InMemoryQuestionRepository());
        }

        @Override
        public List<ExamSummaryDTO> getMyExams(int authenticatedUserId) {
            myExamsCalls++;
            lastMyExamsUserId = authenticatedUserId;
            throwIfConfigured();
            return teacherExams;
        }

        @Override
        public ExamDTO getExamForTeacher(int authenticatedUserId, int examId) {
            teacherExamCalls++;
            lastTeacherExamUserId = authenticatedUserId;
            lastTeacherExamId = examId;
            throwIfConfigured();
            return teacherExam;
        }

        @Override
        public ExamDTO createExam(int authenticatedUserId, CreateExamPayload payload) {
            createCalls++;
            lastCreateUserId = authenticatedUserId;
            lastCreatePayload = payload;
            throwIfConfigured();
            return createdExam;
        }

        @Override
        public List<ExamSummaryDTO> getPendingExams(int authenticatedUserId) {
            pendingExamsCalls++;
            lastPendingExamsUserId = authenticatedUserId;
            throwIfConfigured();
            return pendingExams;
        }

        @Override
        public ExamDTO getExamForCoordinator(int authenticatedUserId, int examId) {
            coordinatorExamCalls++;
            lastCoordinatorExamUserId = authenticatedUserId;
            lastCoordinatorExamId = examId;
            throwIfConfigured();
            return coordinatorExam;
        }

        private void throwIfConfigured() {
            if (failure != null) {
                throw failure;
            }
        }

        private void setTeacherExams(List<ExamSummaryDTO> teacherExams) {
            this.teacherExams = teacherExams;
        }

        private void setPendingExams(List<ExamSummaryDTO> pendingExams) {
            this.pendingExams = pendingExams;
        }

        private void setTeacherExam(ExamDTO teacherExam) {
            this.teacherExam = teacherExam;
        }

        private void setCreatedExam(ExamDTO createdExam) {
            this.createdExam = createdExam;
        }

        private void setCoordinatorExam(ExamDTO coordinatorExam) {
            this.coordinatorExam = coordinatorExam;
        }

        private void setFailure(RuntimeException failure) {
            this.failure = failure;
        }

        private int getTotalCalls() {
            return myExamsCalls + teacherExamCalls + createCalls
                    + pendingExamsCalls + coordinatorExamCalls;
        }

        private int getLastMyExamsUserId() {
            return lastMyExamsUserId;
        }

        private int getLastTeacherExamUserId() {
            return lastTeacherExamUserId;
        }

        private int getLastTeacherExamId() {
            return lastTeacherExamId;
        }

        private int getLastCreateUserId() {
            return lastCreateUserId;
        }

        private CreateExamPayload getLastCreatePayload() {
            return lastCreatePayload;
        }

        private int getLastPendingExamsUserId() {
            return lastPendingExamsUserId;
        }

        private int getLastCoordinatorExamUserId() {
            return lastCoordinatorExamUserId;
        }

        private int getLastCoordinatorExamId() {
            return lastCoordinatorExamId;
        }
    }
}
