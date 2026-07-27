package hsts.client.control;

import hsts.client.net.Client;
import hsts.common.ExamAttemptDTO;
import hsts.common.ExamExecutionPreviewDTO;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExecutionCodePayload;
import hsts.common.ExtendSubmissionTimePayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.SaveExamAnswerPayload;
import hsts.common.ScheduleExamExecutionPayload;
import hsts.common.StartExamPayload;
import hsts.common.StudentAnswerDTO;
import hsts.common.SubmissionIdPayload;

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
