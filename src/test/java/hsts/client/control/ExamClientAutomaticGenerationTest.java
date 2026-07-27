package hsts.client.control;

import hsts.common.ExamDTO;
import hsts.common.GenerateExamPayload;
import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.ExamStatus;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ExamClientAutomaticGenerationTest {
    @Test
    public void generateSendsExactRequestAndReturnsExactDto() {
        RecordingSender sender = new RecordingSender();
        ExamDTO result = exam();
        sender.response = Response.success("Exam generated successfully", result);
        ExamClientController controller = new ExamClientController(sender::send);
        GenerateExamPayload payload = payload();

        assertSame(result, controller.generateExam(payload).join());
        assertEquals(1, sender.calls);
        assertEquals(RequestType.GENERATE_EXAM, sender.request.getType());
        assertSame(payload, sender.request.getPayload());
    }

    @Test
    public void invalidSuccessfulPayloadUsesExactMessage() {
        for (Object invalidPayload : new Object[]{null, "wrong"}) {
            RecordingSender sender = new RecordingSender();
            sender.response = Response.success("Exam generated successfully", invalidPayload);
            ExamClientController controller = new ExamClientController(sender::send);

            assertFutureError(
                    () -> controller.generateExam(payload()).join(),
                    "Invalid generate-exam response from server"
            );
        }
    }

    @Test
    public void exactServerErrorIsPreserved() {
        RecordingSender sender = new RecordingSender();
        sender.response = Response.error("Not enough matching questions");
        ExamClientController controller = new ExamClientController(sender::send);

        assertFutureError(
                () -> controller.generateExam(payload()).join(),
                "Not enough matching questions"
        );
        assertEquals(1, sender.calls);
    }

    private static void assertFutureError(Runnable operation, String expectedMessage) {
        CompletionException exception = assertThrows(CompletionException.class, operation::run);
        assertEquals(IllegalStateException.class, exception.getCause().getClass());
        assertEquals(expectedMessage, exception.getCause().getMessage());
    }

    private static GenerateExamPayload payload() {
        return new GenerateExamPayload(
                7, "Exam", 60, "", "Instructions", "Topic",
                DifficultyLevel.MEDIUM, 3
        );
    }

    private static ExamDTO exam() {
        return new ExamDTO(
                101, "AUT101", 7, "Course", 2, "Subject", 1002,
                "Creator", 1, "Exam", 60, "", "Instructions", 100,
                ExamStatus.DRAFT, LocalDateTime.of(2026, 7, 27, 12, 0),
                null, null, null, null, null, List.of()
        );
    }

    private static final class RecordingSender {
        private Request request;
        private Response response;
        private int calls;

        private Response send(Request request) {
            calls++;
            this.request = request;
            return response;
        }
    }
}
