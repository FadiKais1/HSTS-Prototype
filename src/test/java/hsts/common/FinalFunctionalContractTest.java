package hsts.common;

import hsts.common.type.NotificationType;
import hsts.common.type.ReportExportFormat;
import hsts.common.type.ReportType;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class FinalFunctionalContractTest {
    @Test
    public void mutationPayloadsContainNoActorIdentityOrPath() {
        ExtendExecutionTimePayload extension = new ExtendExecutionTimePayload(
                81, 20, "Accessibility accommodation"
        );
        ReportExportPayload export = new ReportExportPayload(
                ReportType.COURSE_EXAMS, 7, ReportExportFormat.XLSX
        );
        NotificationIdPayload notification = new NotificationIdPayload(45);

        assertEquals(81, extension.getExecutionId());
        assertEquals(20, extension.getAddedMinutes());
        assertEquals("Accessibility accommodation", extension.getReason());
        assertEquals(ReportType.COURSE_EXAMS, export.getReportType());
        assertEquals(Integer.valueOf(7), export.getTargetId());
        assertEquals(ReportExportFormat.XLSX, export.getFormat());
        assertEquals(45, notification.getNotificationId());

        Set<String> fieldNames = Arrays.stream(ExtendExecutionTimePayload.class
                        .getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());
        fieldNames.addAll(Arrays.stream(ReportExportPayload.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet()));
        fieldNames.addAll(Arrays.stream(NotificationIdPayload.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet()));
        assertFalse(fieldNames.stream().anyMatch(name -> {
            String lower = name.toLowerCase();
            return lower.contains("teacher") || lower.contains("student")
                    || lower.contains("manager") || lower.contains("actor")
                    || lower.contains("user") || lower.contains("path");
        }));
    }

    @Test
    public void exportResultDefensivelyCopiesBytes() {
        byte[] source = {1, 2, 3, 4};
        ReportExportResult result = new ReportExportResult(
                "report.pdf", "application/pdf", source
        );
        source[0] = 99;
        byte[] firstRead = result.getBytes();
        firstRead[1] = 88;

        assertArrayEquals(new byte[]{1, 2, 3, 4}, result.getBytes());
        assertNotSame(firstRead, result.getBytes());
    }

    @Test
    public void notificationContractRoundTripsAndHasExactTypes() throws Exception {
        assertArrayEquals(new NotificationType[]{
                NotificationType.EXAM_APPROVED,
                NotificationType.EXAM_REJECTED,
                NotificationType.EXAM_SCHEDULED,
                NotificationType.EXECUTION_EXTENDED,
                NotificationType.GRADE_PUBLISHED
        }, NotificationType.values());

        LocalDateTime created = LocalDateTime.of(2026, 8, 1, 10, 0);
        NotificationDTO original = new NotificationDTO(
                9, NotificationType.EXAM_REJECTED, "Exam rejected",
                "Midterm was rejected. Reason: clarify instructions",
                40, null, null, created, null
        );
        NotificationDTO copy = roundTrip(original);

        assertEquals(9, copy.getNotificationId());
        assertEquals(NotificationType.EXAM_REJECTED, copy.getType());
        assertEquals(original.getMessage(), copy.getMessage());
        assertEquals(Integer.valueOf(40), copy.getRelatedExamId());
        assertEquals(created, copy.getCreatedAt());
        assertFalse(copy.isRead());
    }

    @Test
    public void everyNewTransportContractIsSerializable() {
        assertTrue(java.io.Serializable.class.isAssignableFrom(
                ExtendExecutionTimePayload.class));
        assertTrue(java.io.Serializable.class.isAssignableFrom(
                ReportExportPayload.class));
        assertTrue(java.io.Serializable.class.isAssignableFrom(
                ReportExportResult.class));
        assertTrue(java.io.Serializable.class.isAssignableFrom(NotificationDTO.class));
        assertTrue(java.io.Serializable.class.isAssignableFrom(
                NotificationIdPayload.class));
    }

    private static NotificationDTO roundTrip(NotificationDTO value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (NotificationDTO) input.readObject();
        }
    }
}
