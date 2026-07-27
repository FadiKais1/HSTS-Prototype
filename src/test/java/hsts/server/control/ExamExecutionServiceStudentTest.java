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
import static org.junit.Assert.fail;

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
        assertEquals(1, fixture.executions.entityCodeCalls);
        assertEquals(1, fixture.executions.codeCalls);
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
        assertEquals(1, fixture.executions.entityCodeCalls);
        assertEquals(0, fixture.executions.codeCalls);
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

        IllegalArgumentException missing;
        try {
            fixture.service.startOrResumeExam(1001, null);
            fail("Expected missing attempt data to be rejected");
            return;
        } catch (IllegalArgumentException exception) {
            missing = exception;
        }
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
        assertEquals(2, fixture.executions.entityStudentCalls);
        assertEquals(1, fixture.submissions.startCalls);
        assertEquals(1, fixture.submissions.activeCalls);
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
        assertEquals(1, fixture.submissions.activeEntityCalls);
        assertEquals(1, fixture.submissions.activeCalls);

        SaveExamAnswerPayload answerPayload = new SaveExamAnswerPayload(501, 17, 4);
        StudentAnswerDTO saved = fixture.service.saveAnswer(1101, answerPayload);
        assertEquals(17, saved.getQuestionId());
        assertEquals(Integer.valueOf(4), saved.getSelectedOptionNumber());
        assertEquals(NOW, saved.getUpdatedAt());
        assertEquals(1101, fixture.submissions.lastStudentId);
        assertEquals(NOW, fixture.submissions.lastTime);
        assertEquals(2, fixture.submissions.activeEntityCalls);
        assertEquals(1, fixture.submissions.persistAnswerCalls);
        assertEquals(17, fixture.submissions.lastAnswerEntity.getQuestionId());
        assertEquals(4, fixture.submissions.lastAnswerEntity.getQuestionVersionNo());
        assertEquals(4, fixture.submissions.lastAnswerEntity.getSelectedOptionNumber());
        assertTrue(!fixture.submissions.lastAnswerEntity.isGraded());
        assertEquals(1, fixture.exams.exactCalls);
        assertEquals(40, fixture.exams.lastExamId);
        assertEquals(3, fixture.exams.lastVersionNo);

        fixture.submissions.lastSubmissionEntity =
                ExamExecutionServiceTestSupport.submission(
                        1101,
                        java.util.List.of(ExamExecutionServiceTestSupport.answer(
                                91,
                                501,
                                3,
                                NOW.minusMinutes(1)
                        ))
                );
        fixture.submissions.attempt = attempt(SubmissionStatus.IN_PROGRESS);
        ExamAttemptDTO submitted = fixture.service.submitExam(
                1101, new SubmissionIdPayload(501)
        );
        assertEquals(SubmissionStatus.SUBMITTED, submitted.getStatus());
        assertEquals(501, submitted.getSubmissionId());
        assertEquals(1101, fixture.submissions.lastStudentId);
        assertEquals(501, fixture.submissions.lastSubmissionId);
        assertEquals(NOW, fixture.submissions.lastTime);
        assertEquals(1, fixture.submissions.studentEntityCalls);
        assertEquals(1, fixture.submissions.persistStudentCalls);
        assertEquals(1, fixture.grading.calls);
        assertSame(fixture.exams.exact, fixture.grading.lastExam);
        assertEquals(NOW, fixture.grading.lastTime);
        assertEquals(
                "100.00",
                fixture.submissions.lastPersistedStudentSubmission
                        .getAutomaticScoreValue().orElseThrow().toPlainString()
        );
        assertTrue(fixture.submissions.lastPersistedStudentSubmission
                .getStudentAnswers().get(0).isGraded());
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

        IllegalArgumentException missingSubmission = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.submitExam(
                        1201, new SubmissionIdPayload(777)
                )
        );
        assertEquals("Exam attempt not found: 777", missingSubmission.getMessage());
    }

    @Test
    public void finalizationFailuresBeforeOrDuringPersistenceDoNotUseLegacySubmit() {
        Fixture missingExamFixture = studentFixture(1271);
        missingExamFixture.exams.exact = null;
        IllegalStateException missingExam = assertThrows(
                IllegalStateException.class,
                () -> missingExamFixture.service.submitExam(
                        1271, new SubmissionIdPayload(501)
                )
        );
        assertEquals("Exam version does not match the submission", missingExam.getMessage());
        assertEquals(0, missingExamFixture.grading.calls);
        assertEquals(0, missingExamFixture.submissions.persistStudentCalls);

        Fixture gradingFixture = studentFixture(1272);
        RuntimeException gradingFailure = new IllegalStateException("grading failure");
        gradingFixture.grading.failure = gradingFailure;
        assertSame(gradingFailure, assertThrows(
                RuntimeException.class,
                () -> gradingFixture.service.submitExam(
                        1272, new SubmissionIdPayload(501)
                )
        ));
        assertEquals(0, gradingFixture.submissions.persistStudentCalls);

        Fixture persistenceFixture = studentFixture(1273);
        RuntimeException persistenceFailure =
                new IllegalStateException("persistence failure");
        persistenceFixture.submissions.persistStudentFailure = persistenceFailure;
        assertSame(persistenceFailure, assertThrows(
                RuntimeException.class,
                () -> persistenceFixture.service.submitExam(
                        1273, new SubmissionIdPayload(501)
                )
        ));
        assertEquals(1, persistenceFixture.grading.calls);
        assertEquals(1, persistenceFixture.submissions.persistStudentCalls);
    }

    @Test
    public void answerSavingRejectsMissingExactExamUnknownQuestionAndInvalidOption() {
        Fixture fixture = studentFixture(1251);

        fixture.exams.exact = null;
        IllegalArgumentException missingExam = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.saveAnswer(
                        1251, new SaveExamAnswerPayload(501, 17, 2)
                )
        );
        assertEquals("Question not found in exam: 17", missingExam.getMessage());

        fixture.exams.exact = ExamExecutionServiceTestSupport.exactExam();
        IllegalArgumentException unknownQuestion = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.saveAnswer(
                        1251, new SaveExamAnswerPayload(501, 99, 2)
                )
        );
        assertEquals("Question not found in exam: 99", unknownQuestion.getMessage());

        IllegalArgumentException invalidOption = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.saveAnswer(
                        1251, new SaveExamAnswerPayload(501, 17, 5)
                )
        );
        assertEquals("Invalid answer option", invalidOption.getMessage());
        assertEquals(0, fixture.submissions.persistAnswerCalls);
    }

    @Test
    public void compatibilityConstructorRequiresExamRepositoryOnlyForAnswerSaving() {
        Fixture fixture = studentFixture(1261);
        ExamExecutionService compatibilityService = new ExamExecutionService(
                fixture.executions,
                fixture.submissions,
                new ExamExecutionServiceTestSupport.RecordingEnrollmentRepository(),
                fixture.profiles,
                new ExamExecutionServiceTestSupport.RecordingUserRepository(
                        user(1261, UserRole.STUDENT, UserStatus.ACTIVE)
                ),
                ExamExecutionServiceTestSupport.CLOCK
        );
        ExamExecutionPreviewDTO preview = preview(
                ExecutionStatus.OPEN,
                NOW.minusMinutes(1),
                NOW.plusMinutes(30),
                false
        );
        fixture.executions.preview = preview;

        assertSame(
                preview,
                compatibilityService.validateExecutionCode(
                        1261, new ExecutionCodePayload("A1B2")
                )
        );
        IllegalStateException missingRepository = assertThrows(
                IllegalStateException.class,
                () -> compatibilityService.saveAnswer(
                        1261, new SaveExamAnswerPayload(501, 17, 2)
                )
        );
        assertEquals("Exam repository is not configured", missingRepository.getMessage());

        IllegalStateException missingFinalization = assertThrows(
                IllegalStateException.class,
                () -> compatibilityService.submitExam(
                        1261, new SubmissionIdPayload(501)
                )
        );
        assertEquals(
                "Exam finalization dependencies are not configured",
                missingFinalization.getMessage()
        );
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
        RuntimeException propagatedProfileFailure;
        try {
            fixture.service.startOrResumeExam(
                    1301, new StartExamPayload(81, "confirmation")
            );
            fail("Expected profile repository failure to propagate");
            return;
        } catch (RuntimeException exception) {
            propagatedProfileFailure = exception;
        }
        assertSame(profileFailure, propagatedProfileFailure);

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
        ExamExecutionServiceTestSupport.RecordingExamRepository exams =
                new ExamExecutionServiceTestSupport.RecordingExamRepository();
        ExamExecutionServiceTestSupport.RecordingGradingService grading =
                new ExamExecutionServiceTestSupport.RecordingGradingService();
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
                        users,
                        exams,
                        grading
                ),
                executions,
                submissions,
                profiles,
                exams,
                grading
        );
    }

    private static final class Fixture {
        private final ExamExecutionService service;
        private final ExamExecutionServiceTestSupport.RecordingExecutionRepository executions;
        private final ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions;
        private final ExamExecutionServiceTestSupport.RecordingProfileRepository profiles;
        private final ExamExecutionServiceTestSupport.RecordingExamRepository exams;
        private final ExamExecutionServiceTestSupport.RecordingGradingService grading;

        private Fixture(
                ExamExecutionService service,
                ExamExecutionServiceTestSupport.RecordingExecutionRepository executions,
                ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions,
                ExamExecutionServiceTestSupport.RecordingProfileRepository profiles,
                ExamExecutionServiceTestSupport.RecordingExamRepository exams,
                ExamExecutionServiceTestSupport.RecordingGradingService grading
        ) {
            this.service = service;
            this.executions = executions;
            this.submissions = submissions;
            this.profiles = profiles;
            this.exams = exams;
            this.grading = grading;
        }
    }
}
