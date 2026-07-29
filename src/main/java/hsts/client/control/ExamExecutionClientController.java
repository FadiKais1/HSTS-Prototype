package hsts.client.control;

import hsts.client.net.Client;
import hsts.common.ExamAttemptDTO;
import hsts.common.ExamExecutionPreviewDTO;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExecutionCodePayload;
import hsts.common.ExecutionIdPayload;
import hsts.common.ExecutionSubmissionSummaryDTO;
import hsts.common.ExtendSubmissionTimePayload;
import hsts.common.ExtendExecutionTimePayload;
import hsts.common.PublishedGradeDTO;
import hsts.common.PublishedExamReviewDTO;
import hsts.common.PublishedGradeSummaryDTO;
import hsts.common.PublishSubmissionPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.ReviewSubmissionPayload;
import hsts.common.SaveExamAnswerPayload;
import hsts.common.ScheduleExamExecutionPayload;
import hsts.common.StartExamPayload;
import hsts.common.StudentAnswerDTO;
import hsts.common.SubmissionIdPayload;
import hsts.common.SubmissionReviewDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ExamExecutionClientController {
    private static final String REQUEST_FAILED = "Request failed";
    private static final String NULL_RESPONSE = "No response from server";

    private final Client client;
    private final Function<Request, Response> requestSender;

    public ExamExecutionClientController(Client client) {
        this.client = Objects.requireNonNull(client, "client");
        this.requestSender = this.client::sendRequest;
    }

    ExamExecutionClientController(Function<Request, Response> requestSender) {
        this.client = null;
        this.requestSender = Objects.requireNonNull(requestSender, "requestSender");
    }

    public CompletableFuture<ExamExecutionSummaryDTO> scheduleExamExecution(
            ScheduleExamExecutionPayload payload
    ) {
        return sendRequest(
                new Request(RequestType.SCHEDULE_EXAM_EXECUTION, payload),
                responsePayload -> requirePayload(
                        responsePayload,
                        ExamExecutionSummaryDTO.class,
                        "Invalid schedule-execution response from server"
                )
        );
    }

    public CompletableFuture<List<ExamExecutionSummaryDTO>> getMyExamExecutions() {
        return sendRequest(
                new Request(RequestType.LIST_MY_EXAM_EXECUTIONS, null),
                responsePayload -> requireListPayload(
                        responsePayload,
                        ExamExecutionSummaryDTO.class,
                        "Invalid execution-list response from server"
                )
        );
    }

    public CompletableFuture<ExamExecutionPreviewDTO> validateExecutionCode(
            ExecutionCodePayload payload
    ) {
        return sendRequest(
                new Request(RequestType.VALIDATE_EXECUTION_CODE, payload),
                responsePayload -> requirePayload(
                        responsePayload,
                        ExamExecutionPreviewDTO.class,
                        "Invalid execution-code response from server"
                )
        );
    }

    public CompletableFuture<ExamAttemptDTO> startExamAttempt(StartExamPayload payload) {
        return sendRequest(
                new Request(RequestType.START_EXAM_ATTEMPT, payload),
                responsePayload -> requirePayload(
                        responsePayload,
                        ExamAttemptDTO.class,
                        "Invalid start-attempt response from server"
                )
        );
    }

    public CompletableFuture<ExamAttemptDTO> getActiveExamAttempt(
            SubmissionIdPayload payload
    ) {
        return sendRequest(
                new Request(RequestType.GET_ACTIVE_EXAM_ATTEMPT, payload),
                responsePayload -> requirePayload(
                        responsePayload,
                        ExamAttemptDTO.class,
                        "Invalid active-attempt response from server"
                )
        );
    }

    public CompletableFuture<StudentAnswerDTO> saveExamAnswer(
            SaveExamAnswerPayload payload
    ) {
        return sendRequest(
                new Request(RequestType.SAVE_EXAM_ANSWER, payload),
                responsePayload -> requirePayload(
                        responsePayload,
                        StudentAnswerDTO.class,
                        "Invalid save-answer response from server"
                )
        );
    }

    public CompletableFuture<ExamAttemptDTO> submitExamAttempt(
            SubmissionIdPayload payload
    ) {
        return sendRequest(
                new Request(RequestType.SUBMIT_EXAM_ATTEMPT, payload),
                responsePayload -> requirePayload(
                        responsePayload,
                        ExamAttemptDTO.class,
                        "Invalid submit-attempt response from server"
                )
        );
    }

    public CompletableFuture<Boolean> extendSubmissionTime(
            ExtendSubmissionTimePayload payload
    ) {
        return sendRequest(
                new Request(RequestType.EXTEND_SUBMISSION_TIME, payload),
                responsePayload -> requirePayload(
                        responsePayload,
                        Boolean.class,
                        "Invalid time-extension response from server"
                )
        );
    }

    public CompletableFuture<ExamExecutionSummaryDTO> extendExecutionTime(
            ExtendExecutionTimePayload payload
    ) {
        return sendRequest(
                new Request(RequestType.EXTEND_EXAM_EXECUTION, payload),
                responsePayload -> requirePayload(
                        responsePayload,
                        ExamExecutionSummaryDTO.class,
                        "Invalid execution-extension response from server"
                )
        );
    }

    public CompletableFuture<List<ExecutionSubmissionSummaryDTO>>
    getExecutionSubmissions(int executionId) {
        return sendRequest(
                new Request(
                        RequestType.LIST_EXECUTION_SUBMISSIONS,
                        new ExecutionIdPayload(executionId)
                ),
                responsePayload -> requireListPayload(
                        responsePayload,
                        ExecutionSubmissionSummaryDTO.class,
                        "Invalid execution-submissions response from server"
                )
        );
    }

    public CompletableFuture<SubmissionReviewDTO> getSubmissionForReview(
            int submissionId
    ) {
        return sendRequest(
                new Request(
                        RequestType.GET_SUBMISSION_FOR_REVIEW,
                        new SubmissionIdPayload(submissionId)
                ),
                responsePayload -> requirePayload(
                        responsePayload,
                        SubmissionReviewDTO.class,
                        "Invalid submission-review response from server"
                )
        );
    }

    public CompletableFuture<SubmissionReviewDTO> reviewSubmissionGrade(
            ReviewSubmissionPayload payload
    ) {
        if (payload == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Grade review data is missing")
            );
        }
        return sendRequest(
                new Request(RequestType.REVIEW_SUBMISSION_GRADE, payload),
                responsePayload -> requirePayload(
                        responsePayload,
                        SubmissionReviewDTO.class,
                        "Invalid submission-review response from server"
                )
        );
    }

    public CompletableFuture<SubmissionReviewDTO> publishSubmissionGrade(
            PublishSubmissionPayload payload
    ) {
        if (payload == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Grade publication data is missing")
            );
        }
        return sendRequest(
                new Request(RequestType.PUBLISH_SUBMISSION_GRADE, payload),
                responsePayload -> requirePayload(
                        responsePayload,
                        SubmissionReviewDTO.class,
                        "Invalid submission-review response from server"
                )
        );
    }

    public CompletableFuture<List<PublishedGradeSummaryDTO>> getMyPublishedGrades() {
        return sendRequest(
                new Request(RequestType.LIST_MY_PUBLISHED_GRADES, null),
                responsePayload -> requireListPayload(
                        responsePayload,
                        PublishedGradeSummaryDTO.class,
                        "Invalid published-grades response from server"
                )
        );
    }

    public CompletableFuture<PublishedGradeDTO> getMyPublishedGrade(int submissionId) {
        return sendRequest(
                new Request(
                        RequestType.GET_MY_PUBLISHED_GRADE,
                        new SubmissionIdPayload(submissionId)
                ),
                responsePayload -> requirePayload(
                        responsePayload,
                        PublishedGradeDTO.class,
                        "Invalid published-grade response from server"
                )
        );
    }

    public CompletableFuture<PublishedExamReviewDTO> getMyPublishedExamReview(
            int submissionId
    ) {
        if (submissionId <= 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Submission ID must be positive")
            );
        }
        return sendRequest(
                new Request(
                        RequestType.GET_MY_PUBLISHED_EXAM_REVIEW,
                        new SubmissionIdPayload(submissionId)
                ),
                responsePayload -> requirePayload(
                        responsePayload,
                        PublishedExamReviewDTO.class,
                        "Invalid published exam-review response from server"
                )
        );
    }

    private <T> CompletableFuture<T> sendRequest(
            Request request,
            Function<Object, T> payloadMapper
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Response response = requestSender.apply(request);
            if (response == null) {
                throw new IllegalStateException(NULL_RESPONSE);
            }
            if (!response.isSuccess()) {
                String message = response.getMessage();
                throw new IllegalStateException(
                        message == null || message.isBlank() ? REQUEST_FAILED : message
                );
            }
            return payloadMapper.apply(response.getPayload());
        });
    }

    private <T> T requirePayload(Object payload, Class<T> payloadType,
                                 String invalidPayloadMessage) {
        if (!payloadType.isInstance(payload)) {
            throw new IllegalStateException(invalidPayloadMessage);
        }
        return payloadType.cast(payload);
    }

    private <T> List<T> requireListPayload(Object payload, Class<T> elementType,
                                           String invalidPayloadMessage) {
        if (!(payload instanceof List<?> rawList)) {
            throw new IllegalStateException(invalidPayloadMessage);
        }

        List<T> typedList = new ArrayList<>(rawList.size());
        for (Object element : rawList) {
            if (!elementType.isInstance(element)) {
                throw new IllegalStateException(invalidPayloadMessage);
            }
            typedList.add(elementType.cast(element));
        }
        return List.copyOf(typedList);
    }
}
