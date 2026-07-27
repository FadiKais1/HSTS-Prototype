package hsts.client.control;

import hsts.client.net.Client;
import hsts.common.CreateExamPayload;
import hsts.common.ExamDTO;
import hsts.common.ExamSummaryDTO;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class ExamClientController {
    private final Function<Request, Response> requestSender;

    public ExamClientController(Client client) {
        this(Objects.requireNonNull(client, "client")::sendRequest);
    }

    ExamClientController(Function<Request, Response> requestSender) {
        this.requestSender = Objects.requireNonNull(requestSender, "requestSender");
    }

    public CompletableFuture<List<ExamSummaryDTO>> getMyExams() {
        return sendRequest(
                new Request(RequestType.LIST_MY_EXAMS, null),
                payload -> requireListPayload(
                        payload,
                        ExamSummaryDTO.class,
                        "Invalid exam list response from server"
                )
        );
    }

    public CompletableFuture<ExamDTO> getMyExam(int examId) {
        return sendRequest(
                new Request(RequestType.GET_MY_EXAM, examId),
                payload -> requirePayload(
                        payload,
                        ExamDTO.class,
                        "Invalid exam response from server"
                )
        );
    }

    public CompletableFuture<ExamDTO> createExam(CreateExamPayload payload) {
        return sendRequest(
                new Request(RequestType.CREATE_EXAM, payload),
                responsePayload -> requirePayload(
                        responsePayload,
                        ExamDTO.class,
                        "Invalid create-exam response from server"
                )
        );
    }

    public CompletableFuture<List<ExamSummaryDTO>> getPendingExams() {
        return sendRequest(
                new Request(RequestType.LIST_PENDING_EXAMS, null),
                payload -> requireListPayload(
                        payload,
                        ExamSummaryDTO.class,
                        "Invalid pending-exam list response from server"
                )
        );
    }

    public CompletableFuture<ExamDTO> getPendingExam(int examId) {
        return sendRequest(
                new Request(RequestType.GET_PENDING_EXAM, examId),
                payload -> requirePayload(
                        payload,
                        ExamDTO.class,
                        "Invalid pending-exam response from server"
                )
        );
    }

    private <T> CompletableFuture<T> sendRequest(Request request,
                                                  Function<Object, T> payloadMapper) {
        return CompletableFuture.supplyAsync(() -> {
            Response response = requestSender.apply(request);
            if (!response.isSuccess()) {
                throw new IllegalStateException(response.getMessage());
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
