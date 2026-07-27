package hsts.server.net;

import hsts.common.ExamDTO;
import hsts.common.ExamQuestionSelectionPayload;
import hsts.common.ExamVersionPayload;
import hsts.common.RejectExamPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.UpdateExamPayload;
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

public class ExamWorkflowServerRoutingTest {
    @Test
    public void authenticatedRoutesForwardConnectionIdentityAndExactPayloads() {
        int teacherId = 8101;
        int coordinatorId = 8102;
        RecordingExamManagementService service = new RecordingExamManagementService();
        ExamDTO result = exam();
        service.setResult(result);
        Server server = server(service);
        UpdateExamPayload updatePayload = updatePayload();
        ExamVersionPayload submitPayload = new ExamVersionPayload(31, 2);
        ExamVersionPayload approvePayload = new ExamVersionPayload(31, 2);
        RejectExamPayload rejectPayload = new RejectExamPayload(31, 2, "Revise wording");

        Response update = server.handleAuthenticatedRequest(
                new Request(RequestType.UPDATE_EXAM, updatePayload),
                teacherId
        );
        Response submit = server.handleAuthenticatedRequest(
                new Request(RequestType.SUBMIT_EXAM_FOR_APPROVAL, submitPayload),
                teacherId
        );
        Response approve = server.handleAuthenticatedRequest(
                new Request(RequestType.APPROVE_EXAM, approvePayload),
                coordinatorId
        );
        Response reject = server.handleAuthenticatedRequest(
                new Request(RequestType.REJECT_EXAM, rejectPayload),
                coordinatorId
        );

        assertSuccess(update, "Exam updated successfully", result);
        assertSuccess(submit, "Exam submitted for approval", result);
        assertSuccess(approve, "Exam approved successfully", result);
        assertSuccess(reject, "Exam rejected successfully", result);
        assertEquals(teacherId, service.updateUserId);
        assertSame(updatePayload, service.updatePayload);
        assertEquals(teacherId, service.submitUserId);
        assertSame(submitPayload, service.submitPayload);
        assertEquals(coordinatorId, service.approveUserId);
        assertSame(approvePayload, service.approvePayload);
        assertEquals(coordinatorId, service.rejectUserId);
        assertSame(rejectPayload, service.rejectPayload);
    }

    @Test
    public void coordinatorSubmissionDoesNotAutomaticallyInvokeApproval() {
        int coordinatorId = 8201;
        RecordingExamManagementService service = new RecordingExamManagementService();
        service.setResult(exam());
        ExamVersionPayload payload = new ExamVersionPayload(31, 2);

        Response response = server(service).handleAuthenticatedRequest(
                new Request(RequestType.SUBMIT_EXAM_FOR_APPROVAL, payload),
                coordinatorId
        );

        assertTrue(response.isSuccess());
        assertEquals(1, service.submitCalls);
        assertEquals(0, service.approveCalls);
    }

    @Test
    public void invalidPayloadsDoNotInvokeService() {
        RecordingExamManagementService service = new RecordingExamManagementService();
        Server server = server(service);

        assertInvalidPayloads(server, RequestType.UPDATE_EXAM,
                "Exam update data is missing");
        assertInvalidPayloads(server, RequestType.SUBMIT_EXAM_FOR_APPROVAL,
                "Exam version data is missing");
        assertInvalidPayloads(server, RequestType.APPROVE_EXAM,
                "Exam version data is missing");
        assertInvalidPayloads(server, RequestType.REJECT_EXAM,
                "Exam rejection data is missing");
        assertEquals(0, service.totalCalls());
    }

    @Test
    public void serviceErrorsAndContextFreeDispatchPreserveExactMessages() {
        RecordingExamManagementService failingService = new RecordingExamManagementService();
        failingService.setFailure(new IllegalStateException("Exam workflow unavailable"));
        Server failingServer = server(failingService);

        for (Request request : validRequests()) {
            assertError(
                    failingServer.handleAuthenticatedRequest(request, 8301),
                    "Exam workflow unavailable"
            );
        }
        assertEquals(4, failingService.totalCalls());

        RecordingExamManagementService contextFreeService =
                new RecordingExamManagementService();
        Server contextFreeServer = server(contextFreeService);
        for (Request request : validRequests()) {
            assertError(
                    contextFreeServer.handleRequest(request),
                    "Authentication context required"
            );
        }
        assertEquals(0, contextFreeService.totalCalls());
    }

    private static void assertInvalidPayloads(Server server, RequestType type,
                                              String expectedMessage) {
        assertError(
                server.handleAuthenticatedRequest(new Request(type, null), 8401),
                expectedMessage
        );
        assertError(
                server.handleAuthenticatedRequest(new Request(type, "wrong payload"), 8401),
                expectedMessage
        );
    }

    private static List<Request> validRequests() {
        return List.of(
                new Request(RequestType.UPDATE_EXAM, updatePayload()),
                new Request(
                        RequestType.SUBMIT_EXAM_FOR_APPROVAL,
                        new ExamVersionPayload(31, 2)
                ),
                new Request(RequestType.APPROVE_EXAM, new ExamVersionPayload(31, 2)),
                new Request(
                        RequestType.REJECT_EXAM,
                        new RejectExamPayload(31, 2, "Revise wording")
                )
        );
    }

    private static UpdateExamPayload updatePayload() {
        return new UpdateExamPayload(
                31,
                2,
                "Revised exam",
                75,
                "Teacher notes",
                "Read carefully",
                List.of(new ExamQuestionSelectionPayload(41, 3, 1, 100))
        );
    }

    private static ExamDTO exam() {
        return new ExamDTO(
                31, "ABC123", 7, "Course", 3, "Subject", 8101,
                "Creator", 2, "Exam", 75, "", "Read carefully", 100,
                ExamStatus.DRAFT, LocalDateTime.of(2026, 7, 1, 10, 0),
                null, null, null, null, null, List.of()
        );
    }

    private static Server server(ExamManagementService service) {
        return new Server(
                0,
                service,
                new AuthService(new InMemoryUserRepository())
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
        private ExamDTO result;
        private RuntimeException failure;
        private int updateCalls;
        private int submitCalls;
        private int approveCalls;
        private int rejectCalls;
        private int updateUserId;
        private int submitUserId;
        private int approveUserId;
        private int rejectUserId;
        private UpdateExamPayload updatePayload;
        private ExamVersionPayload submitPayload;
        private ExamVersionPayload approvePayload;
        private RejectExamPayload rejectPayload;

        private RecordingExamManagementService() {
            super(new InMemoryQuestionRepository());
        }

        @Override
        public ExamDTO updateExam(int authenticatedUserId, UpdateExamPayload payload) {
            updateCalls++;
            updateUserId = authenticatedUserId;
            updatePayload = payload;
            throwIfConfigured();
            return result;
        }

        @Override
        public ExamDTO submitExamForApproval(int authenticatedUserId,
                                             ExamVersionPayload payload) {
            submitCalls++;
            submitUserId = authenticatedUserId;
            submitPayload = payload;
            throwIfConfigured();
            return result;
        }

        @Override
        public ExamDTO approveExam(int authenticatedUserId, ExamVersionPayload payload) {
            approveCalls++;
            approveUserId = authenticatedUserId;
            approvePayload = payload;
            throwIfConfigured();
            return result;
        }

        @Override
        public ExamDTO rejectExam(int authenticatedUserId, RejectExamPayload payload) {
            rejectCalls++;
            rejectUserId = authenticatedUserId;
            rejectPayload = payload;
            throwIfConfigured();
            return result;
        }

        private void throwIfConfigured() {
            if (failure != null) {
                throw failure;
            }
        }

        private void setResult(ExamDTO result) {
            this.result = result;
        }

        private void setFailure(RuntimeException failure) {
            this.failure = failure;
        }

        private int totalCalls() {
            return updateCalls + submitCalls + approveCalls + rejectCalls;
        }
    }
}
