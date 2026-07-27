package hsts.server.net;

import hsts.common.ExamDTO;
import hsts.common.GenerateExamPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.type.DifficultyLevel;
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

public class AutomaticExamServerRoutingTest {
    @Test
    public void authenticatedRouteForwardsIdentityAndPayloadWithExactSuccess() {
        RecordingService service = new RecordingService();
        ExamDTO result = exam();
        service.result = result;
        Server server = server(service);
        GenerateExamPayload payload = payload();

        Response response = server.handleAuthenticatedRequest(
                new Request(RequestType.GENERATE_EXAM, payload),
                7101
        );

        assertTrue(response.isSuccess());
        assertEquals(ResponseStatus.SUCCESS, response.getStatus());
        assertEquals("Exam generated successfully", response.getMessage());
        assertSame(result, response.getPayload());
        assertEquals(7101, service.lastUserId);
        assertSame(payload, service.lastPayload);
        assertEquals(1, service.calls);
    }

    @Test
    public void wrongPayloadsDoNotInvokeService() {
        RecordingService service = new RecordingService();
        Server server = server(service);

        for (Object payload : new Object[]{null, "wrong"}) {
            Response response = server.handleAuthenticatedRequest(
                    new Request(RequestType.GENERATE_EXAM, payload),
                    7102
            );
            assertError(response, "Automatic exam data is missing");
        }
        assertEquals(0, service.calls);
    }

    @Test
    public void contextFreeRouteRequiresAuthenticationContext() {
        RecordingService service = new RecordingService();
        Response response = server(service).handleRequest(
                new Request(RequestType.GENERATE_EXAM, payload())
        );

        assertError(response, "Authentication context required");
        assertEquals(0, service.calls);
    }

    @Test
    public void serviceFailureBecomesExactErrorResponse() {
        RecordingService service = new RecordingService();
        service.failure = new IllegalStateException("Not enough matching questions");

        Response response = server(service).handleAuthenticatedRequest(
                new Request(RequestType.GENERATE_EXAM, payload()),
                7103
        );

        assertError(response, "Not enough matching questions");
        assertEquals(1, service.calls);
    }

    private static Server server(ExamManagementService service) {
        return new Server(
                0,
                service,
                new AuthService(new InMemoryUserRepository())
        );
    }

    private static GenerateExamPayload payload() {
        return new GenerateExamPayload(
                7, "Exam", 60, "", "Instructions", "Topic",
                DifficultyLevel.MEDIUM, 3
        );
    }

    private static ExamDTO exam() {
        return new ExamDTO(
                91, "AUTO91", 7, "Course", 2, "Subject", 7101,
                "Creator", 1, "Exam", 60, "", "Instructions", 100,
                ExamStatus.DRAFT, LocalDateTime.of(2026, 7, 27, 12, 0),
                null, null, null, null, null, List.of()
        );
    }

    private static void assertError(Response response, String message) {
        assertFalse(response.isSuccess());
        assertEquals(ResponseStatus.ERROR, response.getStatus());
        assertEquals(message, response.getMessage());
        assertNull(response.getPayload());
    }

    private static final class RecordingService extends ExamManagementService {
        private ExamDTO result;
        private RuntimeException failure;
        private int calls;
        private int lastUserId;
        private GenerateExamPayload lastPayload;

        private RecordingService() {
            super(new InMemoryQuestionRepository());
        }

        @Override
        public ExamDTO generateAutomaticExam(int authenticatedUserId,
                                             GenerateExamPayload payload) {
            calls++;
            lastUserId = authenticatedUserId;
            lastPayload = payload;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
