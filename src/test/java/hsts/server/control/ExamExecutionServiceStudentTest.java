package hsts.server.control;

import hsts.common.ExamAttemptDTO;
import hsts.common.ExamExecutionPreviewDTO;
import hsts.common.ExecutionCodePayload;
import hsts.common.SaveExamAnswerPayload;
import hsts.common.StartExamPayload;
import hsts.common.StudentAnswerDTO;
import hsts.common.SubmissionIdPayload;
import hsts.common.type.ExecutionStatus;
import hsts.common.type.SubmissionStatus;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static hsts.server.control.ExamExecutionServiceTestSupport.NOW;
import static hsts.server.control.ExamExecutionServiceTestSupport.attempt;
import static hsts.server.control.ExamExecutionServiceTestSupport.preview;
import static hsts.server.control.ExamExecutionServiceTestSupport.service;
import static hsts.server.control.ExamExecutionServiceTestSupport.user;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamExecutionServiceStudentTest {
    @Test
    public void studentAuthorizationRejectsMissingBlockedAndWrongRolesBeforeRepositoryCalls() {
        assertStudentAuthorizationFailure(
                801,
                new ExamExecutionServiceTestSupport.RecordingUserRepository(),
                IllegalArgumentException.class,
                "User not found: 801"
        );
        assertStudentAuthorizationFailure(
                802,
                new ExamExecutionServiceTestSupport.RecordingUserRepository(
                        user(802, UserRole.STUDENT, UserStatus.BLOCKED)
                ),
                IllegalStateException.class,
                "User account is blocked"
        );
        for (UserRole role : new UserRole[]{
                UserRole.TEACHER, UserRole.COORDINATOR, UserRole.PRINCIPAL
        }) {
            int userId = 810 + role.ordinal();
            assertStudentAuthorizationFailure(
                    userId,
                    new ExamExecutionServiceTestSupport.RecordingUserRepository(
                            user(userId, role, UserStatus.ACTIVE)
                    ),
                    IllegalStateException.class,
                    "Only students can take exams"
            );
        }
    }

    @Test
    public void codeValidationNormalizesCodeAndUsesStudentIdentity() {
        Fixture fixture = studentFixture(901);
        ExamExecutionPreviewDTO expected = preview(
                ExecutionStatus.OPEN,
                NOW.minusMinutes(1),
                NOW.plusMinutes(30),
                false
        );
        fixture.executions.preview = expected;

        ExamExecutionPreviewDTO actual = fixture.service.validateExecutionCode(
                901,
                new ExecutionCodePayload("  a1b2  ")
        );

        assertSame(expected, actual);
        assertEquals(901, fixture.executions.lastStudentId);
        assertEquals("A1B2", fixture.executions.lastCode);
    }

    @Test
    public void codeValidationUsesExactInputAndAvailabilityMessages() {
        Fixture fixture = studentFixture(902);

        assertCodeFailure(fixture.service, null, IllegalArgumentException.class,
                "Execution code is required");
        assertCodeFailure(fixture.service, new ExecutionCodePayload("  "),
                IllegalArgumentException.class, "Execution code is required");
        assertCodeFailure(fixture.service, new ExecutionCodePayload("ABC"),
                IllegalArgumentException.class, "Invalid execution code");
        assertCodeFailure(fixture.service, new ExecutionCodePayload("AB-1"),
                IllegalArgumentException.class, "Invalid execution code");
        assertCodeFailure(fixture.service, new ExecutionCodePayload("ABCDE"),
                IllegalArgumentException.class, "Invalid execution code");
        assertEquals(0, fixture.executions.codeCalls);

        assertCodeFailure(fixture.service, new ExecutionCodePayload("A1B2"),
                IllegalStateException.class, "Execution not available");
        assertEquals(1, fixture.executions.codeCalls);
    }

    @Test
    public void codeValidationEnforcesOpenClosedAndResumableTimingWithInjectedClock() {
        Fixture fixture = studentFixture(902);

        fixture.executions.preview = preview(
                ExecutionStatus.SCHEDULED, NOW.plusNanos(1), NOW.plusHours(1), false
        );
        assertCodeFailure(fixture.service, new ExecutionCodePayload("A1B2"),
                IllegalStateException.class, "Exam is not open yet");

        fixture.executions.preview = preview(
                ExecutionStatus.CLOSED, NOW.minusHours(2), NOW.plusHours(1), true
        );
        assertCodeFailure(fixture.service, new ExecutionCodePayload("A1B2"),
                IllegalStateException.class, "Exam is closed");

        fixture.executions.preview = preview(
                ExecutionStatus.OPEN, NOW.minusHours(2), NOW, false
        );
        assertCodeFailure(fixture.service, new ExecutionCodePayload("A1B2"),
                IllegalStateException.class, "Exam is closed");

        ExamExecutionPreviewDTO resumable = preview(
                ExecutionStatus.OPEN, NOW.minusHours(2), NOW.minusMinutes(1), true
        );
        fixture.executions.preview = resumable;
        assertSame(
                resumable,
                fixture.service.validateExecutionCode(902, new ExecutionCodePayload("A1B2"))
        );
    }

    @Test
    public void identityValidationDoesNotLeakAndStartUsesAuthenticatedStudentAndClock() {
        Fixture fixture = studentFixture(1001);

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.startOrResumeExam(1001, null)
        );
        assertEquals("Exam attempt data is missing", missing.getMessage());

        IllegalArgumentException blank = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.startOrResumeExam(
                        1001, new StartExamPayload(81, "  ")
                )
        );
        assertEquals("Identity confirmation is required", blank.getMessage());
        assertEquals(0, fixture.profiles.calls);

        fixture.profiles.matches = false;
        String secretConfirmation = "private-confirmation";
        IllegalArgumentException mismatch = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.startOrResumeExam(
                        1001, new StartExamPayload(81, secretConfirmation)
                )
        );
        assertEquals("Invalid identity confirmation", mismatch.getMessage());
        assertTrue(!mismatch.getMessage().contains(secretConfirmation));
        assertEquals(secretConfirmation, fixture.profiles.lastConfirmation);
        assertEquals(0, fixture.submissions.startCalls);

        fixture.profiles.matches = true;
        ExamAttemptDTO expected = attempt(SubmissionStatus.IN_PROGRESS);
        fixture.submissions.attempt = expected;
        assertSame(expected, fixture.service.startOrResumeExam(
                1001, new StartExamPayload(81, "  exact input  ")
        ));
        assertEquals(1001, fixture.profiles.lastStudentId);
        assertEquals("  exact input  ", fixture.profiles.lastConfirmation);
        assertEquals(1001, fixture.submissions.lastStudentId);
        assertEquals(81, fixture.submissions.lastExecutionId);
        assertEquals(NOW, fixture.submissions.lastTime);
    }

    @Test
    public void activeAnswerAndSubmitPassAuthenticatedStudentExactPayloadAndClock() {
        Fixture fixture = studentFixture(1101);
        ExamAttemptDTO expectedAttempt = attempt(SubmissionStatus.IN_PROGRESS);
        fixture.submissions.attempt = expectedAttempt;

        assertSame(expectedAttempt, fixture.service.getActiveAttempt(
                1101, new SubmissionIdPayload(501)
        ));
        assertEquals(1101, fixture.submissions.lastStudentId);
        assertEquals(501, fixture.submissions.lastSubmissionId);
        assertEquals(NOW, fixture.submissions.lastTime);

        SaveExamAnswerPayload answerPayload = new SaveExamAnswerPayload(501, 17, 4);
        StudentAnswerDTO expectedAnswer = fixture.submissions.answer;
        assertSame(expectedAnswer, fixture.service.saveAnswer(1101, answerPayload));
        assertSame(answerPayload, fixture.submissions.lastAnswerPayload);
        assertEquals(1101, fixture.submissions.lastStudentId);
        assertEquals(NOW, fixture.submissions.lastTime);

        ExamAttemptDTO submitted = attempt(SubmissionStatus.SUBMITTED);
        fixture.submissions.attempt = submitted;
        assertSame(submitted, fixture.service.submitExam(
                1101, new SubmissionIdPayload(501)
        ));
        assertEquals(1101, fixture.submissions.lastStudentId);
        assertEquals(501, fixture.submissions.lastSubmissionId);
        assertEquals(NOW, fixture.submissions.lastTime);
    }

    @Test
    public void studentPayloadAndMissingAttemptFailuresUseExactMessages() {
        Fixture fixture = studentFixture(1201);

        assertPayloadFailure(
                () -> fixture.service.getActiveAttempt(1201, null),
                "Submission data is missing"
        );
        assertPayloadFailure(
                () -> fixture.service.saveAnswer(1201, null),
                "Answer data is missing"
        );
        assertPayloadFailure(
                () -> fixture.service.submitExam(1201, null),
                "Submission data is missing"
        );

        fixture.submissions.activePresent = false;
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.getActiveAttempt(
                        1201, new SubmissionIdPayload(777)
                )
        );
        assertEquals("Exam attempt not found: 777", missing.getMessage());
    }

    @Test
    public void repositoryFailuresPropagateUnchanged() {
        Fixture fixture = studentFixture(1301);
        RuntimeException executionFailure = new IllegalStateException("execution failure");
        fixture.executions.failure = executionFailure;
        assertSame(executionFailure, assertThrows(
                RuntimeException.class,
                () -> fixture.service.validateExecutionCode(
                        1301, new ExecutionCodePayload("A1B2")
                )
        ));

        RuntimeException profileFailure = new IllegalStateException("profile failure");
        fixture.executions.failure = null;
        fixture.profiles.failure = profileFailure;
        assertSame(profileFailure, assertThrows(
                RuntimeException.class,
                () -> fixture.service.startOrResumeExam(
                        1301, new StartExamPayload(81, "confirmation")
                )
        ));

        RuntimeException submissionFailure = new IllegalStateException("submission failure");
        fixture.profiles.failure = null;
        fixture.submissions.failure = submissionFailure;
        assertSame(submissionFailure, assertThrows(
                RuntimeException.class,
                () -> fixture.service.saveAnswer(
                        1301, new SaveExamAnswerPayload(501, 17, 2)
                )
        ));
    }

    @Test
    public void returnedStudentDtosExposeNoCorrectAnswerOrGradingMembers() {
        assertNoSensitiveMembers(ExamAttemptDTO.class);
        assertNoSensitiveMembers(ExamExecutionPreviewDTO.class);
        assertNoSensitiveMembers(StudentAnswerDTO.class);
    }

    private static void assertStudentAuthorizationFailure(
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

        RuntimeException failure = assertThrows(
                type,
                () -> service.validateExecutionCode(
                        userId, new ExecutionCodePayload("A1B2")
                )
        );
        assertEquals(message, failure.getMessage());
        assertEquals(0, executions.totalCalls());
        assertEquals(0, submissions.totalCalls());
    }

    private static void assertCodeFailure(ExamExecutionService service,
                                          ExecutionCodePayload payload,
                                          Class<? extends RuntimeException> type,
                                          String message) {
        RuntimeException failure = assertThrows(
                type,
                () -> service.validateExecutionCode(902, payload)
        );
        assertEquals(message, failure.getMessage());
    }

    private static void assertPayloadFailure(Runnable operation, String message) {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                operation::run
        );
        assertEquals(message, failure.getMessage());
    }

    private static void assertNoSensitiveMembers(Class<?> type) {
        Set<String> memberNames = Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        memberNames.addAll(Arrays.stream(type.getDeclaredMethods())
                .map(Method::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet()));
        for (String name : memberNames) {
            assertTrue("Unexpected sensitive member: " + name,
                    !name.contains("correct")
                            && !name.contains("score")
                            && !name.contains("grade")
                            && !name.contains("feedback"));
        }
    }

    private static Fixture studentFixture(int studentId) {
        ExamExecutionServiceTestSupport.RecordingExecutionRepository executions =
                new ExamExecutionServiceTestSupport.RecordingExecutionRepository();
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        ExamExecutionServiceTestSupport.RecordingProfileRepository profiles =
                new ExamExecutionServiceTestSupport.RecordingProfileRepository();
        ExamExecutionServiceTestSupport.RecordingUserRepository users =
                new ExamExecutionServiceTestSupport.RecordingUserRepository(
                        user(studentId, UserRole.STUDENT, UserStatus.ACTIVE)
                );
        return new Fixture(
                service(
                        executions,
                        submissions,
                        new ExamExecutionServiceTestSupport.RecordingEnrollmentRepository(),
                        profiles,
                        users
                ),
                executions,
                submissions,
                profiles
        );
    }

    private static final class Fixture {
        private final ExamExecutionService service;
        private final ExamExecutionServiceTestSupport.RecordingExecutionRepository executions;
        private final ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions;
        private final ExamExecutionServiceTestSupport.RecordingProfileRepository profiles;

        private Fixture(
                ExamExecutionService service,
                ExamExecutionServiceTestSupport.RecordingExecutionRepository executions,
                ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions,
                ExamExecutionServiceTestSupport.RecordingProfileRepository profiles
        ) {
            this.service = service;
            this.executions = executions;
            this.submissions = submissions;
            this.profiles = profiles;
        }
    }
}
