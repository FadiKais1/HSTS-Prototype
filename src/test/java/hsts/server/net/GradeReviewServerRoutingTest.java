package hsts.server.net;

import hsts.common.ExecutionIdPayload;
import hsts.common.ExecutionSubmissionSummaryDTO;
import hsts.common.PublishSubmissionPayload;
import hsts.common.PublishedGradeDTO;
import hsts.common.PublishedGradeSummaryDTO;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.ReviewSubmissionPayload;
import hsts.common.SubmissionIdPayload;
import hsts.common.SubmissionReviewDTO;
import hsts.common.type.SubmissionStatus;
import hsts.server.control.AuthService;
import hsts.server.control.ExamExecutionService;
import hsts.server.control.ExamManagementService;
import hsts.server.support.InMemoryQuestionRepository;
import hsts.server.support.InMemoryUserRepository;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class GradeReviewServerRoutingTest {
    private static final int TEACHER_ID = 1002;
    private static final int COORDINATOR_ID = 1003;
    private static final int STUDENT_ID = 1001;
    private static final int EXECUTION_ID = 81;
    private static final int SUBMISSION_ID = 501;
    private static final LocalDateTime UPDATED_AT =
            LocalDateTime.of(2026, 8, 12, 10, 0);

    @Test
    public void authenticatedRoutesForwardIdentityPayloadsAndExactServiceResults() {
        RecordingExamExecutionService service = new RecordingExamExecutionService();
        Server server = server(service);
        ExecutionIdPayload executionPayload = new ExecutionIdPayload(EXECUTION_ID);
        SubmissionIdPayload reviewDetailPayload =
                new SubmissionIdPayload(SUBMISSION_ID);
        ReviewSubmissionPayload reviewPayload = new ReviewSubmissionPayload(
                SUBMISSION_ID,
                new BigDecimal("65.00"),
                "Good work",
                "Accepted alternate method",
                UPDATED_AT
        );
        PublishSubmissionPayload publicationPayload =
                new PublishSubmissionPayload(SUBMISSION_ID, UPDATED_AT);
        SubmissionIdPayload publishedDetailPayload =
                new SubmissionIdPayload(SUBMISSION_ID);

        Response teacherList = server.handleAuthenticatedRequest(
                new Request(RequestType.LIST_EXECUTION_SUBMISSIONS, executionPayload),
                TEACHER_ID
        );
        Response coordinatorList = server.handleAuthenticatedRequest(
                new Request(RequestType.LIST_EXECUTION_SUBMISSIONS, executionPayload),
                COORDINATOR_ID
        );
        Response managerDetail = server.handleAuthenticatedRequest(
                new Request(
                        RequestType.GET_SUBMISSION_FOR_REVIEW,
                        reviewDetailPayload
                ),
                TEACHER_ID
        );
        Response review = server.handleAuthenticatedRequest(
                new Request(RequestType.REVIEW_SUBMISSION_GRADE, reviewPayload),
                TEACHER_ID
        );
        Response publication = server.handleAuthenticatedRequest(
                new Request(
                        RequestType.PUBLISH_SUBMISSION_GRADE,
                        publicationPayload
                ),
                COORDINATOR_ID
        );
        Response publishedList = server.handleAuthenticatedRequest(
                new Request(RequestType.LIST_MY_PUBLISHED_GRADES, null),
                STUDENT_ID
        );
        Response publishedDetail = server.handleAuthenticatedRequest(
                new Request(
                        RequestType.GET_MY_PUBLISHED_GRADE,
                        publishedDetailPayload
                ),
                STUDENT_ID
        );

        assertSuccess(
                teacherList,
                "Execution submissions loaded successfully",
                service.executionSubmissions
        );
        assertSuccess(
                coordinatorList,
                "Execution submissions loaded successfully",
                service.executionSubmissions
        );
        assertSuccess(
                managerDetail,
                "Submission loaded successfully",
                service.submissionReview
        );
        assertSuccess(
                review,
                "Submission grade reviewed successfully",
                service.submissionReview
        );
        assertSuccess(
                publication,
                "Submission grade published successfully",
                service.submissionReview
        );
        assertSuccess(
                publishedList,
                "Published grades loaded successfully",
                service.publishedGrades
        );
        assertSuccess(
                publishedDetail,
                "Published grade loaded successfully",
                service.publishedGrade
        );

        assertSame(reviewPayload, service.reviewPayload);
        assertSame(publicationPayload, service.publicationPayload);
        assertEquals(EXECUTION_ID, service.lastExecutionId);
        assertEquals(SUBMISSION_ID, service.managerSubmissionId);
        assertEquals(SUBMISSION_ID, service.studentSubmissionId);
        assertEquals(SUBMISSION_ID, service.lastSubmissionId);
        assertEquals(STUDENT_ID, service.lastAuthenticatedUserId);
        assertEquals(7, service.routeCalls);
    }

    @Test
    public void studentRoutesReturnOnlyPublishedGradeProjectionTypes() {
        RecordingExamExecutionService service = new RecordingExamExecutionService();
        Server server = server(service);

        Response list = server.handleAuthenticatedRequest(
                new Request(RequestType.LIST_MY_PUBLISHED_GRADES, null),
                STUDENT_ID
        );
        Response detail = server.handleAuthenticatedRequest(
                new Request(
                        RequestType.GET_MY_PUBLISHED_GRADE,
                        new SubmissionIdPayload(SUBMISSION_ID)
                ),
                STUDENT_ID
        );

        assertSame(service.publishedGrades, list.getPayload());
        assertTrue(((List<?>) list.getPayload()).get(0)
                instanceof PublishedGradeSummaryDTO);
        assertFalse(((List<?>) list.getPayload()).get(0)
                instanceof ExecutionSubmissionSummaryDTO);
        assertTrue(detail.getPayload() instanceof PublishedGradeDTO);
        assertFalse(detail.getPayload() instanceof SubmissionReviewDTO);
    }

    @Test
    public void missingAndWrongPayloadsReturnStableErrorsWithoutServiceCalls() {
        RecordingExamExecutionService service = new RecordingExamExecutionService();
        Server server = server(service);

        assertInvalidRequired(
                server,
                RequestType.LIST_EXECUTION_SUBMISSIONS,
                "Execution request data is invalid"
        );
        assertInvalidRequired(
                server,
                RequestType.GET_SUBMISSION_FOR_REVIEW,
                "Submission request data is invalid"
        );
        assertInvalidRequired(
                server,
                RequestType.REVIEW_SUBMISSION_GRADE,
                "Grade review data is invalid"
        );
        assertInvalidRequired(
                server,
                RequestType.PUBLISH_SUBMISSION_GRADE,
                "Grade publication data is invalid"
        );
        assertError(
                server.handleAuthenticatedRequest(
                        new Request(
                                RequestType.LIST_MY_PUBLISHED_GRADES,
                                "unexpected"
                        ),
                        STUDENT_ID
                ),
                "Published grades request data is invalid"
        );
        assertInvalidRequired(
                server,
                RequestType.GET_MY_PUBLISHED_GRADE,
                "Submission request data is invalid"
        );

        assertEquals(0, service.routeCalls);
    }

    @Test
    public void serviceErrorsBecomeSafeErrorResponses() {
        RecordingExamExecutionService service = new RecordingExamExecutionService();
        service.failure = new IllegalStateException(
                "Submission was modified by another user; reload and try again"
        );
        Server server = server(service);

        for (Request request : validRequests()) {
            assertError(
                    server.handleAuthenticatedRequest(request, TEACHER_ID),
                    "Submission was modified by another user; reload and try again"
            );
        }
        assertEquals(6, service.routeCalls);

        service.failure = new IllegalStateException();
        assertError(
                server.handleAuthenticatedRequest(
                        new Request(
                                RequestType.LIST_EXECUTION_SUBMISSIONS,
                                new ExecutionIdPayload(EXECUTION_ID)
                        ),
                        TEACHER_ID
                ),
                "Request failed"
        );
    }

    @Test
    public void contextFreeDispatchRejectsEveryNewRouteWithoutCallingService() {
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

        assertError(
                server.handleAuthenticatedRequest(
                        new Request(
                                RequestType.LIST_EXECUTION_SUBMISSIONS,
                                new ExecutionIdPayload(EXECUTION_ID)
                        ),
                        TEACHER_ID
                ),
                "Exam execution service is not configured"
        );
    }

    private static void assertInvalidRequired(Server server, RequestType type,
                                              String message) {
        assertError(
                server.handleAuthenticatedRequest(
                        new Request(type, null),
                        TEACHER_ID
                ),
                message
        );
        assertError(
                server.handleAuthenticatedRequest(
                        new Request(type, "wrong payload"),
                        TEACHER_ID
                ),
                message
        );
    }

    private static List<Request> validRequests() {
        return List.of(
                new Request(
                        RequestType.LIST_EXECUTION_SUBMISSIONS,
                        new ExecutionIdPayload(EXECUTION_ID)
                ),
                new Request(
                        RequestType.GET_SUBMISSION_FOR_REVIEW,
                        new SubmissionIdPayload(SUBMISSION_ID)
                ),
                new Request(
                        RequestType.REVIEW_SUBMISSION_GRADE,
                        new ReviewSubmissionPayload(
                                SUBMISSION_ID,
                                new BigDecimal("65.00"),
                                "Good work",
                                "Reason",
                                UPDATED_AT
                        )
                ),
                new Request(
                        RequestType.PUBLISH_SUBMISSION_GRADE,
                        new PublishSubmissionPayload(SUBMISSION_ID, UPDATED_AT)
                ),
                new Request(RequestType.LIST_MY_PUBLISHED_GRADES, null),
                new Request(
                        RequestType.GET_MY_PUBLISHED_GRADE,
                        new SubmissionIdPayload(SUBMISSION_ID)
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

    private static void assertSuccess(Response response, String message,
                                      Object expectedPayload) {
        assertTrue(response.isSuccess());
        assertEquals(ResponseStatus.SUCCESS, response.getStatus());
        assertEquals(message, response.getMessage());
        assertSame(expectedPayload, response.getPayload());
    }

    private static void assertError(Response response, String message) {
        assertFalse(response.isSuccess());
        assertEquals(ResponseStatus.ERROR, response.getStatus());
        assertEquals(message, response.getMessage());
        assertNull(response.getPayload());
    }

    private static final class RecordingExamExecutionService
            extends ExamExecutionService {
        private final List<ExecutionSubmissionSummaryDTO> executionSubmissions =
                List.of(executionSummary());
        private final SubmissionReviewDTO submissionReview = submissionReview();
        private final List<PublishedGradeSummaryDTO> publishedGrades =
                List.of(publishedSummary());
        private final PublishedGradeDTO publishedGrade = publishedGrade();
        private RuntimeException failure;
        private int routeCalls;
        private int lastAuthenticatedUserId;
        private int lastExecutionId;
        private int lastSubmissionId;
        private int managerSubmissionId;
        private int studentSubmissionId;
        private ReviewSubmissionPayload reviewPayload;
        private PublishSubmissionPayload publicationPayload;

        @Override
        public List<ExecutionSubmissionSummaryDTO> getExecutionSubmissions(
                int authenticatedManagerUserId,
                int executionId
        ) {
            record(authenticatedManagerUserId);
            lastExecutionId = executionId;
            return executionSubmissions;
        }

        @Override
        public SubmissionReviewDTO getSubmissionForReview(
                int authenticatedManagerUserId,
            int submissionId
        ) {
            record(authenticatedManagerUserId);
            managerSubmissionId = submissionId;
            lastSubmissionId = submissionId;
            return submissionReview;
        }

        @Override
        public SubmissionReviewDTO reviewSubmissionGrade(
                int authenticatedManagerUserId,
                ReviewSubmissionPayload payload
        ) {
            record(authenticatedManagerUserId);
            reviewPayload = payload;
            lastSubmissionId = payload.getSubmissionId();
            return submissionReview;
        }

        @Override
        public SubmissionReviewDTO publishSubmissionGrade(
                int authenticatedManagerUserId,
                PublishSubmissionPayload payload
        ) {
            record(authenticatedManagerUserId);
            publicationPayload = payload;
            lastSubmissionId = payload.getSubmissionId();
            return submissionReview;
        }

        @Override
        public List<PublishedGradeSummaryDTO> getMyPublishedGrades(
                int authenticatedStudentUserId
        ) {
            record(authenticatedStudentUserId);
            return publishedGrades;
        }

        @Override
        public PublishedGradeDTO getMyPublishedGrade(
                int authenticatedStudentUserId,
            int submissionId
        ) {
            record(authenticatedStudentUserId);
            studentSubmissionId = submissionId;
            lastSubmissionId = submissionId;
            return publishedGrade;
        }

        private void record(int authenticatedUserId) {
            routeCalls++;
            lastAuthenticatedUserId = authenticatedUserId;
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static ExecutionSubmissionSummaryDTO executionSummary() {
        return new ExecutionSubmissionSummaryDTO(
                SUBMISSION_ID,
                EXECUTION_ID,
                40,
                3,
                "Historical Final",
                STUDENT_ID,
                "Development Student",
                SubmissionStatus.SUBMITTED,
                new BigDecimal("60.00"),
                new BigDecimal("65.00"),
                UPDATED_AT.minusHours(2),
                UPDATED_AT.minusHours(1),
                UPDATED_AT,
                null
        );
    }

    private static SubmissionReviewDTO submissionReview() {
        return new SubmissionReviewDTO(
                SUBMISSION_ID,
                EXECUTION_ID,
                40,
                3,
                "Historical Final",
                STUDENT_ID,
                "Development Student",
                SubmissionStatus.SUBMITTED,
                new BigDecimal("60.00"),
                new BigDecimal("65.00"),
                "Good work",
                "Accepted alternate method",
                TEACHER_ID,
                UPDATED_AT.minusHours(2),
                UPDATED_AT.minusHours(1),
                UPDATED_AT,
                0,
                null,
                List.of()
        );
    }

    private static PublishedGradeSummaryDTO publishedSummary() {
        return new PublishedGradeSummaryDTO(
                SUBMISSION_ID,
                EXECUTION_ID,
                40,
                3,
                "Historical Final",
                "Mathematics",
                new BigDecimal("65.00"),
                UPDATED_AT.minusHours(1),
                UPDATED_AT
        );
    }

    private static PublishedGradeDTO publishedGrade() {
        return new PublishedGradeDTO(
                SUBMISSION_ID,
                EXECUTION_ID,
                40,
                3,
                "Historical Final",
                "Mathematics",
                SubmissionStatus.PUBLISHED,
                new BigDecimal("65.00"),
                "Good work",
                UPDATED_AT.minusHours(1),
                UPDATED_AT.minusMinutes(1),
                UPDATED_AT
        );
    }
}
