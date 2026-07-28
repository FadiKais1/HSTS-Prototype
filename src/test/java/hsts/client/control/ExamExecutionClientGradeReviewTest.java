package hsts.client.control;

import hsts.common.ExecutionIdPayload;
import hsts.common.ExecutionSubmissionSummaryDTO;
import hsts.common.PublishedGradeDTO;
import hsts.common.PublishedGradeSummaryDTO;
import hsts.common.PublishSubmissionPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ResponseStatus;
import hsts.common.ReviewSubmissionPayload;
import hsts.common.SubmissionIdPayload;
import hsts.common.SubmissionReviewDTO;
import hsts.common.type.SubmissionStatus;
import org.junit.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ExamExecutionClientGradeReviewTest {
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 5, 10, 30);

    @Test
    public void everyOperationSendsExactRequestAndMapsActualServerPayload()
            throws Exception {
        RecordingSender sender = new RecordingSender();
        ExamExecutionClientController controller =
                new ExamExecutionClientController(sender::send);

        ExecutionSubmissionSummaryDTO summary = managerSummary(501);
        List<ExecutionSubmissionSummaryDTO> summaries = List.of(summary);
        sender.setResponse(Response.success("Loaded", summaries));
        assertEquals(summaries, controller.getExecutionSubmissions(81).join());
        Request listRequest = sender.lastRequest();
        assertEquals(RequestType.LIST_EXECUTION_SUBMISSIONS, listRequest.getType());
        assertEquals(81, ((ExecutionIdPayload) listRequest.getPayload()).getExecutionId());

        SubmissionReviewDTO review = managerReview(501);
        sender.setResponse(Response.success("Loaded", review));
        assertSame(review, controller.getSubmissionForReview(501).join());
        assertSubmissionRequest(
                sender.lastRequest(), RequestType.GET_SUBMISSION_FOR_REVIEW, 501
        );

        ReviewSubmissionPayload reviewPayload = new ReviewSubmissionPayload(
                501, new BigDecimal("94.50"), "Good work", "Manual adjustment", NOW
        );
        sender.setResponse(Response.success("Reviewed", review));
        assertSame(review, controller.reviewSubmissionGrade(reviewPayload).join());
        assertExactPayloadRequest(
                sender.lastRequest(), RequestType.REVIEW_SUBMISSION_GRADE, reviewPayload
        );

        PublishSubmissionPayload publishPayload =
                new PublishSubmissionPayload(501, NOW.plusMinutes(1));
        sender.setResponse(Response.success("Published", review));
        assertSame(review, controller.publishSubmissionGrade(publishPayload).join());
        assertExactPayloadRequest(
                sender.lastRequest(), RequestType.PUBLISH_SUBMISSION_GRADE, publishPayload
        );

        PublishedGradeSummaryDTO publishedSummary = publishedSummary(501);
        sender.setResponse(Response.success("Loaded", List.of(publishedSummary)));
        assertSame(publishedSummary, controller.getMyPublishedGrades().join().get(0));
        Request publishedListRequest = sender.lastRequest();
        assertEquals(RequestType.LIST_MY_PUBLISHED_GRADES, publishedListRequest.getType());
        assertNull(publishedListRequest.getPayload());

        PublishedGradeDTO publishedGrade = publishedGrade(501);
        sender.setResponse(Response.success("Loaded", publishedGrade));
        assertSame(publishedGrade, controller.getMyPublishedGrade(501).join());
        assertSubmissionRequest(
                sender.lastRequest(), RequestType.GET_MY_PUBLISHED_GRADE, 501
        );

        Field requestUserId = Request.class.getDeclaredField("userId");
        requestUserId.setAccessible(true);
        assertEquals(6, sender.requests().size());
        for (Request request : sender.requests()) {
            assertEquals(0, requestUserId.getInt(request));
        }
    }

    @Test
    public void managerAndStudentListsPreserveOrderAndAreImmutable() {
        RecordingSender sender = new RecordingSender();
        ExamExecutionClientController controller =
                new ExamExecutionClientController(sender::send);
        ExecutionSubmissionSummaryDTO firstManager = managerSummary(501);
        ExecutionSubmissionSummaryDTO secondManager = managerSummary(502);
        sender.setResponse(Response.success(
                "Loaded", List.of(firstManager, secondManager)
        ));

        List<ExecutionSubmissionSummaryDTO> managerResult =
                controller.getExecutionSubmissions(81).join();

        assertEquals(List.of(firstManager, secondManager), managerResult);
        assertThrows(UnsupportedOperationException.class, () -> managerResult.clear());

        PublishedGradeSummaryDTO firstStudent = publishedSummary(601);
        PublishedGradeSummaryDTO secondStudent = publishedSummary(602);
        sender.setResponse(Response.success(
                "Loaded", List.of(firstStudent, secondStudent)
        ));

        List<PublishedGradeSummaryDTO> studentResult =
                controller.getMyPublishedGrades().join();

        assertEquals(List.of(firstStudent, secondStudent), studentResult);
        assertThrows(UnsupportedOperationException.class, () -> studentResult.clear());

        sender.setResponse(Response.success("Loaded", List.of()));
        assertEquals(List.of(), controller.getExecutionSubmissions(81).join());
        assertEquals(List.of(), controller.getMyPublishedGrades().join());
    }

    @Test
    public void malformedManagerAndStudentListsAreRejected() {
        RecordingSender sender = new RecordingSender();
        ExamExecutionClientController controller =
                new ExamExecutionClientController(sender::send);

        sender.setResponse(Response.success("Loaded", "not a list"));
        assertFutureError(
                () -> controller.getExecutionSubmissions(81),
                IllegalStateException.class,
                "Invalid execution-submissions response from server"
        );

        sender.setResponse(Response.success(
                "Loaded", List.of(managerSummary(501), publishedSummary(601))
        ));
        assertFutureError(
                () -> controller.getExecutionSubmissions(81),
                IllegalStateException.class,
                "Invalid execution-submissions response from server"
        );

        List<Object> managerWithNull = new ArrayList<>();
        managerWithNull.add(managerSummary(501));
        managerWithNull.add(null);
        sender.setResponse(Response.success("Loaded", managerWithNull));
        assertFutureError(
                () -> controller.getExecutionSubmissions(81),
                IllegalStateException.class,
                "Invalid execution-submissions response from server"
        );

        sender.setResponse(Response.success(
                "Loaded", List.of(publishedSummary(601), managerSummary(501))
        ));
        assertFutureError(
                controller::getMyPublishedGrades,
                IllegalStateException.class,
                "Invalid published-grades response from server"
        );

        List<Object> studentWithNull = new ArrayList<>();
        studentWithNull.add(publishedSummary(601));
        studentWithNull.add(null);
        sender.setResponse(Response.success("Loaded", studentWithNull));
        assertFutureError(
                controller::getMyPublishedGrades,
                IllegalStateException.class,
                "Invalid published-grades response from server"
        );
    }

    @Test
    public void detailAndMutationResponsesRequireTheirExactDtoTypes() {
        RecordingSender sender = new RecordingSender();
        ExamExecutionClientController controller =
                new ExamExecutionClientController(sender::send);
        ReviewSubmissionPayload reviewPayload = new ReviewSubmissionPayload(
                501, new BigDecimal("94.50"), "Good work", "Adjustment", NOW
        );
        PublishSubmissionPayload publishPayload =
                new PublishSubmissionPayload(501, NOW);

        sender.setResponse(Response.success("Loaded", publishedGrade(501)));
        assertInvalidManagerDetail(() -> controller.getSubmissionForReview(501));
        assertInvalidManagerDetail(() -> controller.reviewSubmissionGrade(reviewPayload));
        assertInvalidManagerDetail(() -> controller.publishSubmissionGrade(publishPayload));

        sender.setResponse(Response.success("Loaded", managerReview(501)));
        assertFutureError(
                () -> controller.getMyPublishedGrade(501),
                IllegalStateException.class,
                "Invalid published-grade response from server"
        );

        sender.setResponse(Response.success("Loaded", null));
        assertInvalidManagerDetail(() -> controller.getSubmissionForReview(501));
        assertFutureError(
                () -> controller.getMyPublishedGrade(501),
                IllegalStateException.class,
                "Invalid published-grade response from server"
        );
        assertFutureError(
                controller::getMyPublishedGrades,
                IllegalStateException.class,
                "Invalid published-grades response from server"
        );
    }

    @Test
    public void serverAndNullResponseErrorsCompleteExceptionallyWithSafeMessages() {
        RecordingSender sender = new RecordingSender();
        ExamExecutionClientController controller =
                new ExamExecutionClientController(sender::send);

        sender.setResponse(null);
        assertFutureError(
                controller::getMyPublishedGrades,
                IllegalStateException.class,
                "No response from server"
        );

        sender.setResponse(Response.error("Submission version conflict"));
        assertFutureError(
                () -> controller.getSubmissionForReview(501),
                IllegalStateException.class,
                "Submission version conflict"
        );

        sender.setResponse(new Response(ResponseStatus.ERROR, "  ", null));
        assertFutureError(
                () -> controller.getMyPublishedGrade(501),
                IllegalStateException.class,
                "Request failed"
        );

        sender.setResponse(new Response(ResponseStatus.ERROR, null, null));
        assertFutureError(
                () -> controller.getExecutionSubmissions(81),
                IllegalStateException.class,
                "Request failed"
        );
    }

    @Test
    public void missingMutationPayloadsFailBeforeTheRequestSenderIsCalled() {
        RecordingSender sender = new RecordingSender();
        ExamExecutionClientController controller =
                new ExamExecutionClientController(sender::send);

        assertFutureError(
                () -> controller.reviewSubmissionGrade(null),
                IllegalStateException.class,
                "Grade review data is missing"
        );
        assertFutureError(
                () -> controller.publishSubmissionGrade(null),
                IllegalStateException.class,
                "Grade publication data is missing"
        );
        assertEquals(0, sender.requests().size());
    }

    @Test
    public void requestSenderFailurePropagatesUnchanged() {
        IllegalStateException failure = new IllegalStateException("Network failure");
        ExamExecutionClientController controller =
                new ExamExecutionClientController(request -> {
                    throw failure;
                });

        CompletionException result = assertThrows(
                CompletionException.class,
                () -> controller.getMyPublishedGrades().join()
        );

        assertSame(failure, result.getCause());
    }

    private static void assertSubmissionRequest(
            Request request, RequestType type, int submissionId
    ) {
        assertEquals(type, request.getType());
        SubmissionIdPayload payload = (SubmissionIdPayload) request.getPayload();
        assertEquals(submissionId, payload.getSubmissionId());
    }

    private static void assertExactPayloadRequest(
            Request request, RequestType type, Object payload
    ) {
        assertEquals(type, request.getType());
        assertSame(payload, request.getPayload());
    }

    private static void assertInvalidManagerDetail(
            Supplier<? extends CompletableFuture<?>> operation
    ) {
        assertFutureError(
                operation,
                IllegalStateException.class,
                "Invalid submission-review response from server"
        );
    }

    private static void assertFutureError(
            Supplier<? extends CompletableFuture<?>> operation,
            Class<? extends Throwable> expectedType,
            String expectedMessage
    ) {
        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> operation.get().join()
        );
        assertEquals(expectedType, exception.getCause().getClass());
        assertEquals(expectedMessage, exception.getCause().getMessage());
    }

    private static ExecutionSubmissionSummaryDTO managerSummary(int submissionId) {
        return new ExecutionSubmissionSummaryDTO(
                submissionId, 81, 40, 3, "Midterm", 1001, "Student",
                SubmissionStatus.SUBMITTED, new BigDecimal("90.00"),
                new BigDecimal("94.50"), NOW.minusHours(1), NOW,
                NOW.plusMinutes(1), NOW.plusMinutes(2)
        );
    }

    private static SubmissionReviewDTO managerReview(int submissionId) {
        return new SubmissionReviewDTO(
                submissionId, 81, 40, 3, "Midterm", 1001, "Student",
                SubmissionStatus.SUBMITTED, new BigDecimal("90.00"),
                new BigDecimal("94.50"), "Good work", "Manual adjustment",
                1002, NOW.minusHours(1), NOW, NOW.plusMinutes(1), 0,
                null, List.of()
        );
    }

    private static PublishedGradeSummaryDTO publishedSummary(int submissionId) {
        return new PublishedGradeSummaryDTO(
                submissionId, 81, 40, 3, "Midterm", "Mathematics",
                new BigDecimal("94.50"), NOW, NOW.plusMinutes(2)
        );
    }

    private static PublishedGradeDTO publishedGrade(int submissionId) {
        return new PublishedGradeDTO(
                submissionId, 81, 40, 3, "Midterm", "Mathematics",
                SubmissionStatus.PUBLISHED, new BigDecimal("94.50"),
                "Good work", NOW, NOW.plusMinutes(1), NOW.plusMinutes(2)
        );
    }

    private static final class RecordingSender {
        private final List<Request> requests = new ArrayList<>();
        private volatile Response response;

        private synchronized Response send(Request request) {
            requests.add(request);
            return response;
        }

        private void setResponse(Response response) {
            this.response = response;
        }

        private synchronized Request lastRequest() {
            return requests.get(requests.size() - 1);
        }

        private synchronized List<Request> requests() {
            return List.copyOf(requests);
        }
    }
}
