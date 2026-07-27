package hsts.server.net;

import hsts.common.ExamAttemptDTO;
import hsts.common.ExamExecutionPreviewDTO;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExecutionCodePayload;
import hsts.common.ExtendSubmissionTimePayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.SaveExamAnswerPayload;
import hsts.common.ScheduleExamExecutionPayload;
import hsts.common.StartExamPayload;
import hsts.common.StudentAnswerDTO;
import hsts.common.SubmissionIdPayload;
import hsts.common.type.ExecutionStatus;
import hsts.common.type.SubmissionStatus;
import hsts.server.control.AuthService;
import hsts.server.control.ExamExecutionService;
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

public class ExamExecutionServerRoutingTest {
    private static final LocalDateTime OPENING =
            LocalDateTime.of(2026, 8, 1, 9, 0);
    private static final LocalDateTime CLOSING = OPENING.plusHours(2);

    @Test
    public void authenticatedRoutesForwardBoundIdentityAndExactPayloads() {
        int authenticatedUserId = 4101;
        RecordingExamExecutionService service = new RecordingExamExecutionService();
        Server server = server(service);
        ScheduleExamExecutionPayload schedulePayload =
                new ScheduleExamExecutionPayload(40, 3, OPENING, CLOSING);
        ExecutionCodePayload codePayload = new ExecutionCodePayload("A1B2");
        StartExamPayload startPayload = new StartExamPayload(81, "confirmation");
        SubmissionIdPayload activePayload = new SubmissionIdPayload(501);
        SaveExamAnswerPayload answerPayload =
                new SaveExamAnswerPayload(501, 17, 3);
        SubmissionIdPayload submitPayload = new SubmissionIdPayload(501);
        ExtendSubmissionTimePayload extensionPayload =
                new ExtendSubmissionTimePayload(501, 10, "Approved need");

        Response schedule = server.handleAuthenticatedRequest(
                new Request(RequestType.SCHEDULE_EXAM_EXECUTION, schedulePayload),
                authenticatedUserId
        );
        Response list = server.handleAuthenticatedRequest(
                new Request(RequestType.LIST_MY_EXAM_EXECUTIONS, null),
                authenticatedUserId
        );
        Response validate = server.handleAuthenticatedRequest(
                new Request(RequestType.VALIDATE_EXECUTION_CODE, codePayload),
                authenticatedUserId
        );
        Response start = server.handleAuthenticatedRequest(
                new Request(RequestType.START_EXAM_ATTEMPT, startPayload),
                authenticatedUserId
        );
        Response active = server.handleAuthenticatedRequest(
                new Request(RequestType.GET_ACTIVE_EXAM_ATTEMPT, activePayload),
                authenticatedUserId
        );
        Response answer = server.handleAuthenticatedRequest(
                new Request(RequestType.SAVE_EXAM_ANSWER, answerPayload),
                authenticatedUserId
        );
        Response submit = server.handleAuthenticatedRequest(
                new Request(RequestType.SUBMIT_EXAM_ATTEMPT, submitPayload),
                authenticatedUserId
        );
        Response extend = server.handleAuthenticatedRequest(
                new Request(RequestType.EXTEND_SUBMISSION_TIME, extensionPayload),
                authenticatedUserId
        );

        assertSuccess(
                schedule,
                "Exam execution scheduled successfully",
                service.summary
        );
        assertSuccess(
                list,
                "Exam executions loaded successfully",
                service.summaries
        );
        assertSuccess(
                validate,
                "Execution code validated successfully",
                service.preview
        );
        assertSuccess(start, "Exam attempt started successfully", service.attempt);
        assertSuccess(active, "Exam attempt loaded successfully", service.attempt);
        assertSuccess(answer, "Answer saved successfully", service.answer);
        assertSuccess(submit, "Exam submitted successfully", service.attempt);
        assertSuccess(extend, "Exam time extended successfully", Boolean.TRUE);

        assertEquals(authenticatedUserId, service.lastAuthenticatedUserId);
        assertSame(schedulePayload, service.schedulePayload);
        assertSame(codePayload, service.codePayload);
        assertSame(startPayload, service.startPayload);
        assertSame(activePayload, service.activePayload);
        assertSame(answerPayload, service.answerPayload);
        assertSame(submitPayload, service.submitPayload);
        assertSame(extensionPayload, service.extensionPayload);
        assertEquals(8, service.routeCalls);
    }

    @Test
    public void missingAndWrongPayloadsDoNotInvokeExecutionService() {
        RecordingExamExecutionService service = new RecordingExamExecutionService();
        Server server = server(service);

        assertInvalid(server, RequestType.SCHEDULE_EXAM_EXECUTION,
                "Execution scheduling data is missing");
        assertError(
                server.handleAuthenticatedRequest(
                        new Request(RequestType.LIST_MY_EXAM_EXECUTIONS, "unexpected"),
                        4201
                ),
                "Request payload must be empty"
        );
        assertInvalid(server, RequestType.VALIDATE_EXECUTION_CODE,
                "Execution code is required");
        assertInvalid(server, RequestType.START_EXAM_ATTEMPT,
                "Exam attempt data is missing");
        assertInvalid(server, RequestType.GET_ACTIVE_EXAM_ATTEMPT,
                "Submission data is missing");
        assertInvalid(server, RequestType.SAVE_EXAM_ANSWER,
                "Answer data is missing");
        assertInvalid(server, RequestType.SUBMIT_EXAM_ATTEMPT,
                "Submission data is missing");
        assertInvalid(server, RequestType.EXTEND_SUBMISSION_TIME,
                "Time extension data is missing");

        assertEquals(0, service.routeCalls);
    }

    @Test
    public void executionServiceErrorsBecomeExactErrorResponses() {
        RecordingExamExecutionService service = new RecordingExamExecutionService();
        service.failure = new IllegalStateException("Execution operation unavailable");
        Server server = server(service);

        for (Request request : validRequests()) {
            assertError(
                    server.handleAuthenticatedRequest(request, 4301),
                    "Execution operation unavailable"
            );
        }
        assertEquals(8, service.routeCalls);
    }

    @Test
    public void contextFreeDispatchRejectsEveryExecutionRoute() {
        RecordingExamExecutionService service = new RecordingExamExecutionService();
        Server server = server(service);

        for (Request request : validRequests()) {
            assertError(
                    server.handleRequest(request),
                    "Authentication context required"
            );
        }
        assertEquals(0, service.routeCalls);
    }

    @Test
    public void compatibleServerWithoutExecutionServiceFailsClearly() {
        Server server = new Server(
                0,
                new ExamManagementService(new InMemoryQuestionRepository()),
                new AuthService(new InMemoryUserRepository())
        );

        Response response = server.handleAuthenticatedRequest(
                new Request(RequestType.LIST_MY_EXAM_EXECUTIONS, null),
                4401
        );

        assertError(response, "Exam execution service is not configured");
    }

    private static void assertInvalid(Server server, RequestType type,
                                      String expectedMessage) {
        assertError(
                server.handleAuthenticatedRequest(new Request(type, null), 4201),
                expectedMessage
        );
        assertError(
                server.handleAuthenticatedRequest(
                        new Request(type, "wrong payload"),
                        4201
                ),
                expectedMessage
        );
    }

    private static List<Request> validRequests() {
        return List.of(
                new Request(
                        RequestType.SCHEDULE_EXAM_EXECUTION,
                        new ScheduleExamExecutionPayload(40, 3, OPENING, CLOSING)
                ),
                new Request(RequestType.LIST_MY_EXAM_EXECUTIONS, null),
                new Request(
                        RequestType.VALIDATE_EXECUTION_CODE,
                        new ExecutionCodePayload("A1B2")
                ),
                new Request(
                        RequestType.START_EXAM_ATTEMPT,
                        new StartExamPayload(81, "confirmation")
                ),
                new Request(
                        RequestType.GET_ACTIVE_EXAM_ATTEMPT,
                        new SubmissionIdPayload(501)
                ),
                new Request(
                        RequestType.SAVE_EXAM_ANSWER,
                        new SaveExamAnswerPayload(501, 17, 3)
                ),
                new Request(
                        RequestType.SUBMIT_EXAM_ATTEMPT,
                        new SubmissionIdPayload(501)
                ),
                new Request(
                        RequestType.EXTEND_SUBMISSION_TIME,
                        new ExtendSubmissionTimePayload(501, 10, "Approved need")
                )
        );
    }

    private static Server server(ExamExecutionService service) {
        return new Server(
                0,
                new ExamManagementService(new InMemoryQuestionRepository()),
                new AuthService(new InMemoryUserRepository()),
                service
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

    private static final class RecordingExamExecutionService
            extends ExamExecutionService {
        private final ExamExecutionSummaryDTO summary = new ExamExecutionSummaryDTO(
                81, "A1B2", 40, 3, "EX1234", "Approved Midterm",
                7, "Mathematics", OPENING, CLOSING, 75,
                ExecutionStatus.SCHEDULED, 4101, "Teacher", OPENING.minusDays(1),
                0, 0, 0
        );
        private final List<ExamExecutionSummaryDTO> summaries = List.of(summary);
        private final ExamExecutionPreviewDTO preview = new ExamExecutionPreviewDTO(
                81, "A1B2", 40, 3, "Approved Midterm", 7, "Mathematics",
                OPENING, CLOSING, 75, ExecutionStatus.SCHEDULED, false
        );
        private final ExamAttemptDTO attempt = new ExamAttemptDTO(
                501, 81, "A1B2", 40, 3, "Approved Midterm", "Read carefully",
                OPENING, CLOSING, 75, 0, 3600, SubmissionStatus.IN_PROGRESS,
                List.of(), List.of()
        );
        private final StudentAnswerDTO answer =
                new StudentAnswerDTO(17, 3, OPENING);
        private RuntimeException failure;
        private int routeCalls;
        private int lastAuthenticatedUserId;
        private ScheduleExamExecutionPayload schedulePayload;
        private ExecutionCodePayload codePayload;
        private StartExamPayload startPayload;
        private SubmissionIdPayload activePayload;
        private SaveExamAnswerPayload answerPayload;
        private SubmissionIdPayload submitPayload;
        private ExtendSubmissionTimePayload extensionPayload;

        @Override
        public ExamExecutionSummaryDTO scheduleExecution(
                int authenticatedManagerId,
                ScheduleExamExecutionPayload payload
        ) {
            record(authenticatedManagerId);
            schedulePayload = payload;
            return summary;
        }

        @Override
        public List<ExamExecutionSummaryDTO> getMyExecutions(int authenticatedManagerId) {
            record(authenticatedManagerId);
            return summaries;
        }

        @Override
        public ExamExecutionPreviewDTO validateExecutionCode(
                int authenticatedStudentId,
                ExecutionCodePayload payload
        ) {
            record(authenticatedStudentId);
            codePayload = payload;
            return preview;
        }

        @Override
        public ExamAttemptDTO startOrResumeExam(int authenticatedStudentId,
                                                StartExamPayload payload) {
            record(authenticatedStudentId);
            startPayload = payload;
            return attempt;
        }

        @Override
        public ExamAttemptDTO getActiveAttempt(int authenticatedStudentId,
                                               SubmissionIdPayload payload) {
            record(authenticatedStudentId);
            activePayload = payload;
            return attempt;
        }

        @Override
        public StudentAnswerDTO saveAnswer(int authenticatedStudentId,
                                           SaveExamAnswerPayload payload) {
            record(authenticatedStudentId);
            answerPayload = payload;
            return answer;
        }

        @Override
        public ExamAttemptDTO submitExam(int authenticatedStudentId,
                                         SubmissionIdPayload payload) {
            record(authenticatedStudentId);
            submitPayload = payload;
            return attempt;
        }

        @Override
        public boolean extendStudentTime(int authenticatedManagerId,
                                         ExtendSubmissionTimePayload payload) {
            record(authenticatedManagerId);
            extensionPayload = payload;
            return true;
        }

        private void record(int authenticatedUserId) {
            routeCalls++;
            lastAuthenticatedUserId = authenticatedUserId;
            if (failure != null) {
                throw failure;
            }
        }
    }
}
