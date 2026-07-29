package hsts.server.control;

import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExtendSubmissionTimePayload;
import hsts.common.ScheduleExamExecutionPayload;
import hsts.common.type.ExecutionStatus;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.Exam;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;

import static hsts.server.control.ExamExecutionServiceTestSupport.NOW;
import static hsts.server.control.ExamExecutionServiceTestSupport.service;
import static hsts.server.control.ExamExecutionServiceTestSupport.summary;
import static hsts.server.control.ExamExecutionServiceTestSupport.user;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamExecutionServiceManagerTest {
    @Test
    public void legacyConstructionAndUmlMethodsRemainCompatible() {
        ExamExecutionService legacy = new ExamExecutionService();

        IllegalStateException missingDependencies = assertThrows(
                IllegalStateException.class,
                () -> legacy.getMyExecutions(1)
        );
        assertEquals(
                "Exam execution dependencies are not configured",
                missingDependencies.getMessage()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> legacy.createExamExecution(
                        1,
                        LocalDateTime.of(2026, 1, 1, 8, 0),
                        LocalDateTime.of(2026, 1, 1, 9, 0)
                )
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> legacy.closeExamExecution(1)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> legacy.validateExecutionCode("A1B2")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> legacy.startExam(1, new Exam())
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> legacy.saveAnswer(1, 2, 3)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> legacy.submitExam(1)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> legacy.autoSubmit(1)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> legacy.extendTime(1, 5, "Reason")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> legacy.validateStudentId(1, 1)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> legacy.startTime(1)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> legacy.calculateRemainingTime(60, NOW, NOW.minusMinutes(5))
        );
    }

    @Test
    public void teacherAndCoordinatorCanListAndViewTheirExecutions() {
        for (UserRole role : new UserRole[]{UserRole.TEACHER, UserRole.COORDINATOR}) {
            int userId = role == UserRole.TEACHER ? 101 : 102;
            ExamExecutionServiceTestSupport.RecordingExecutionRepository executions =
                    new ExamExecutionServiceTestSupport.RecordingExecutionRepository();
            ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                    new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
            ExamExecutionServiceTestSupport.RecordingEnrollmentRepository enrollments =
                    new ExamExecutionServiceTestSupport.RecordingEnrollmentRepository();
            ExamExecutionServiceTestSupport.RecordingProfileRepository profiles =
                    new ExamExecutionServiceTestSupport.RecordingProfileRepository();
            ExamExecutionServiceTestSupport.RecordingUserRepository users =
                    new ExamExecutionServiceTestSupport.RecordingUserRepository(
                            user(userId, role, UserStatus.ACTIVE)
                    );
            List<ExamExecutionSummaryDTO> expected = List.of(summary(81, userId));
            executions.summaries = expected;
            executions.detail = expected.get(0);
            ExamExecutionService service = service(
                    executions, submissions, enrollments, profiles, users
            );

            assertSame(expected, service.getMyExecutions(userId));
            assertSame(expected.get(0), service.getExecutionForManager(userId, 81));
            assertEquals(userId, executions.lastManagerId);
            assertEquals(81, executions.lastExecutionId);
        }
    }

    @Test
    public void managerSummariesUseInjectedServerClockForWindowStatus() {
        ExamExecutionServiceTestSupport.RecordingExecutionRepository executions =
                new ExamExecutionServiceTestSupport.RecordingExecutionRepository();
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        ExamExecutionServiceTestSupport.RecordingUserRepository users =
                new ExamExecutionServiceTestSupport.RecordingUserRepository(
                        user(101, UserRole.TEACHER, UserStatus.ACTIVE)
                );
        executions.summaries = List.of(
                summaryAt(81, NOW.plusMinutes(1), NOW.plusHours(1)),
                summaryAt(82, NOW, NOW.plusHours(1)),
                summaryAt(83, NOW.minusMinutes(1), NOW.plusHours(1)),
                summaryAt(84, NOW.minusHours(1), NOW),
                summaryAt(85, NOW.minusHours(2), NOW.minusMinutes(1))
        );
        ExamExecutionService service = service(
                executions,
                submissions,
                new ExamExecutionServiceTestSupport.RecordingEnrollmentRepository(),
                new ExamExecutionServiceTestSupport.RecordingProfileRepository(),
                users
        );

        List<ExamExecutionSummaryDTO> actual = service.getMyExecutions(101);

        assertEquals(ExecutionStatus.SCHEDULED, actual.get(0).getStatus());
        assertEquals(ExecutionStatus.OPEN, actual.get(1).getStatus());
        assertEquals(ExecutionStatus.OPEN, actual.get(2).getStatus());
        assertEquals(ExecutionStatus.CLOSED, actual.get(3).getStatus());
        assertEquals(ExecutionStatus.CLOSED, actual.get(4).getStatus());
        assertEquals(ExecutionStatus.SCHEDULED,
                executions.summaries.get(2).getStatus());
    }

    @Test
    public void managerAuthorizationRejectsMissingBlockedAndWrongRolesBeforeRepositoryCalls() {
        assertManagerAuthorizationFailure(
                201,
                new ExamExecutionServiceTestSupport.RecordingUserRepository(),
                IllegalArgumentException.class,
                "User not found: 201"
        );
        assertManagerAuthorizationFailure(
                202,
                new ExamExecutionServiceTestSupport.RecordingUserRepository(
                        user(202, UserRole.TEACHER, UserStatus.BLOCKED)
                ),
                IllegalStateException.class,
                "User account is blocked"
        );
        for (UserRole role : new UserRole[]{UserRole.STUDENT, UserRole.PRINCIPAL}) {
            int userId = 210 + role.ordinal();
            assertManagerAuthorizationFailure(
                    userId,
                    new ExamExecutionServiceTestSupport.RecordingUserRepository(
                            user(userId, role, UserStatus.ACTIVE)
                    ),
                    IllegalStateException.class,
                    "Only teachers and coordinators can manage executions"
            );
        }
    }

    @Test
    public void scheduleValidatesInRequiredOrderWithExactMessages() {
        Fixture fixture = managerFixture(301, UserRole.TEACHER);

        assertScheduleFailure(fixture.service, null,
                "Execution scheduling data is missing");
        assertScheduleFailure(fixture.service,
                new ScheduleExamExecutionPayload(40, 3, null, NOW.plusHours(1)),
                "Opening and closing times are required");
        assertScheduleFailure(fixture.service,
                new ScheduleExamExecutionPayload(
                        40, 3, NOW.plusHours(2), NOW.plusHours(2)
                ),
                "Execution closing time must be after opening time");
        assertScheduleFailure(fixture.service,
                new ScheduleExamExecutionPayload(
                        40, 3, NOW.minusNanos(1), NOW.plusHours(1)
                ),
                "Execution opening time cannot be in the past");

        assertEquals(0, fixture.executions.scheduleCalls);
        assertEquals(0, fixture.executions.detailCalls);
    }

    @Test
    public void schedulePassesAuthenticatedManagerAndExactPayloadThenRereads() {
        Fixture fixture = managerFixture(401, UserRole.COORDINATOR);
        ScheduleExamExecutionPayload payload = new ScheduleExamExecutionPayload(
                40, 3, NOW, NOW.plusHours(2)
        );
        ExamExecutionSummaryDTO expected = summary(81, 401);
        fixture.executions.detail = expected;

        ExamExecutionSummaryDTO actual = fixture.service.scheduleExecution(401, payload);

        assertSame(expected, actual);
        assertEquals(401, fixture.executions.lastManagerId);
        assertEquals(40, fixture.executions.lastExamId);
        assertEquals(3, fixture.executions.lastExamVersionNo);
        assertEquals(NOW, fixture.executions.lastOpeningTime);
        assertEquals(NOW.plusHours(2), fixture.executions.lastClosingTime);
        assertEquals(1, fixture.executions.scheduleCalls);
        assertEquals(1, fixture.executions.detailCalls);
        assertEquals(81, fixture.executions.lastExecutionId);
    }

    @Test
    public void missingScheduleRereadAndManagerDetailUseExactMessage() {
        Fixture fixture = managerFixture(501, UserRole.TEACHER);
        IllegalArgumentException scheduleFailure = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.scheduleExecution(
                        501,
                        new ScheduleExamExecutionPayload(
                                40, 3, NOW.plusMinutes(1), NOW.plusHours(1)
                        )
                )
        );
        assertEquals("Execution not found: 81", scheduleFailure.getMessage());

        IllegalArgumentException detailFailure = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.getExecutionForManager(501, 92)
        );
        assertEquals("Execution not found: 92", detailFailure.getMessage());
    }

    @Test
    public void extensionValidatesNormalizesAndPassesAuthenticatedManagerAndClock() {
        Fixture fixture = managerFixture(601, UserRole.COORDINATOR);

        assertExtensionFailure(fixture.service, null,
                "Time extension data is missing");
        assertExtensionFailure(
                fixture.service,
                new ExtendSubmissionTimePayload(501, 0, "Reason"),
                "Extra minutes must be positive"
        );
        assertExtensionFailure(
                fixture.service,
                new ExtendSubmissionTimePayload(501, 10, "  "),
                "Extension reason is required"
        );

        assertTrue(fixture.service.extendStudentTime(
                601,
                new ExtendSubmissionTimePayload(501, 15, "  Approved need  ")
        ));
        assertEquals(601, fixture.submissions.lastManagerId);
        assertEquals(501, fixture.submissions.lastSubmissionId);
        assertEquals(15, fixture.submissions.lastAddedMinutes);
        assertEquals("Approved need", fixture.submissions.lastReason);
        assertEquals(NOW, fixture.submissions.lastTime);
        assertEquals(15, fixture.submissions.lastSubmissionEntity.getExtraMinutes());
        assertEquals("Approved need",
                fixture.submissions.lastSubmissionEntity.getExtensionReason());
        assertEquals(1, fixture.submissions.managerEntityCalls);
        assertEquals(1, fixture.submissions.persistExtensionCalls);

        fixture.submissions.extensionResult = false;
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.extendStudentTime(
                        601,
                        new ExtendSubmissionTimePayload(777, 5, "Reason")
                )
        );
        assertEquals("Exam attempt not found: 777", missing.getMessage());
    }

    @Test
    public void repositoryFailuresPropagateAndExternalDataIsNeverModified() {
        Fixture fixture = managerFixture(701, UserRole.TEACHER);
        RuntimeException failure = new IllegalStateException("execution repository failure");
        fixture.executions.failure = failure;

        assertSame(failure, assertThrows(
                RuntimeException.class,
                () -> fixture.service.getMyExecutions(701)
        ));
        assertEquals(0, fixture.users.updateCalls);
        assertEquals(0, fixture.enrollments.readCalls);
        assertEquals(0, fixture.profiles.calls);
    }

    private static void assertManagerAuthorizationFailure(
            int userId,
            ExamExecutionServiceTestSupport.RecordingUserRepository users,
            Class<? extends RuntimeException> type,
            String message
    ) {
        ExamExecutionServiceTestSupport.RecordingExecutionRepository executions =
                new ExamExecutionServiceTestSupport.RecordingExecutionRepository();
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        ExamExecutionService service = service(
                executions,
                submissions,
                new ExamExecutionServiceTestSupport.RecordingEnrollmentRepository(),
                new ExamExecutionServiceTestSupport.RecordingProfileRepository(),
                users
        );

        RuntimeException failure = assertThrows(type, () -> service.getMyExecutions(userId));
        assertEquals(message, failure.getMessage());
        assertEquals(0, executions.totalCalls());
        assertEquals(0, submissions.totalCalls());
    }

    private static void assertScheduleFailure(ExamExecutionService service,
                                              ScheduleExamExecutionPayload payload,
                                              String message) {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.scheduleExecution(301, payload)
        );
        assertEquals(message, failure.getMessage());
    }

    private static void assertExtensionFailure(ExamExecutionService service,
                                               ExtendSubmissionTimePayload payload,
                                               String message) {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.extendStudentTime(601, payload)
        );
        assertEquals(message, failure.getMessage());
    }

    private static Fixture managerFixture(int userId, UserRole role) {
        ExamExecutionServiceTestSupport.RecordingExecutionRepository executions =
                new ExamExecutionServiceTestSupport.RecordingExecutionRepository();
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        ExamExecutionServiceTestSupport.RecordingEnrollmentRepository enrollments =
                new ExamExecutionServiceTestSupport.RecordingEnrollmentRepository();
        ExamExecutionServiceTestSupport.RecordingProfileRepository profiles =
                new ExamExecutionServiceTestSupport.RecordingProfileRepository();
        ExamExecutionServiceTestSupport.RecordingUserRepository users =
                new ExamExecutionServiceTestSupport.RecordingUserRepository(
                        user(userId, role, UserStatus.ACTIVE)
                );
        return new Fixture(
                service(executions, submissions, enrollments, profiles, users),
                executions,
                submissions,
                enrollments,
                profiles,
                users
        );
    }

    private static ExamExecutionSummaryDTO summaryAt(
            int executionId,
            LocalDateTime opening,
            LocalDateTime closing
    ) {
        return new ExamExecutionSummaryDTO(
                executionId, "A1B2", 40, 3, "EX1234", "Approved Midterm",
                7, "Mathematics", opening, closing, 75,
                ExecutionStatus.SCHEDULED, 101, "Development Teacher",
                NOW.minusDays(1), 0, 0, 0
        );
    }

    private static final class Fixture {
        private final ExamExecutionService service;
        private final ExamExecutionServiceTestSupport.RecordingExecutionRepository executions;
        private final ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions;
        private final ExamExecutionServiceTestSupport.RecordingEnrollmentRepository enrollments;
        private final ExamExecutionServiceTestSupport.RecordingProfileRepository profiles;
        private final ExamExecutionServiceTestSupport.RecordingUserRepository users;

        private Fixture(
                ExamExecutionService service,
                ExamExecutionServiceTestSupport.RecordingExecutionRepository executions,
                ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions,
                ExamExecutionServiceTestSupport.RecordingEnrollmentRepository enrollments,
                ExamExecutionServiceTestSupport.RecordingProfileRepository profiles,
                ExamExecutionServiceTestSupport.RecordingUserRepository users
        ) {
            this.service = service;
            this.executions = executions;
            this.submissions = submissions;
            this.enrollments = enrollments;
            this.profiles = profiles;
            this.users = users;
        }
    }
}
