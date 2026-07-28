package hsts.server.control;

import hsts.common.ExecutionSubmissionSummaryDTO;
import hsts.common.PublishSubmissionPayload;
import hsts.common.PublishedGradeDTO;
import hsts.common.PublishedGradeSummaryDTO;
import hsts.common.ReviewSubmissionPayload;
import hsts.common.SubmissionReviewDTO;
import hsts.common.type.SubmissionStatus;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.ExamSubmission;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static hsts.server.control.ExamExecutionServiceTestSupport.CLOCK;
import static hsts.server.control.ExamExecutionServiceTestSupport.NOW;
import static hsts.server.control.ExamExecutionServiceTestSupport.user;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamExecutionServiceGradeReviewTest {
    private static final int STUDENT_ID = 1001;
    private static final int TEACHER_ID = 1002;
    private static final int COORDINATOR_ID = 1003;
    private static final int PRINCIPAL_ID = 1004;
    private static final int SUBMISSION_ID = 501;
    private static final int EXECUTION_ID = 81;
    private static final LocalDateTime EXPECTED_UPDATED_AT =
            NOW.minusMinutes(10);
    private static final String SUBMISSION_NOT_FOUND =
            "Submission not found or access denied";
    private static final String CONFLICT =
            "Submission was modified by another user; reload and try again";

    @Test
    public void activeAssignedManagersReadSafeSubmissionProjectionsInRepositoryOrder() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        List<ExecutionSubmissionSummaryDTO> repositoryOrder = new ArrayList<>(List.of(
                executionSummary(502, "Alice Student"),
                executionSummary(501, "Bob Student")
        ));
        submissions.managerSummaries = repositoryOrder;
        SubmissionReviewDTO expectedReview = reviewDto(SubmissionStatus.SUBMITTED);
        submissions.managerReview = expectedReview;
        ExamExecutionService service = service(
                submissions,
                users(
                        user(TEACHER_ID, UserRole.TEACHER, UserStatus.ACTIVE),
                        user(COORDINATOR_ID, UserRole.COORDINATOR, UserStatus.ACTIVE)
                ),
                new ExamExecutionServiceTestSupport.RecordingGradingService()
        );

        List<ExecutionSubmissionSummaryDTO> teacherResults =
                service.getExecutionSubmissions(TEACHER_ID, EXECUTION_ID);
        List<ExecutionSubmissionSummaryDTO> coordinatorResults =
                service.getExecutionSubmissions(COORDINATOR_ID, EXECUTION_ID);
        SubmissionReviewDTO review = service.getSubmissionForReview(
                TEACHER_ID,
                SUBMISSION_ID
        );

        assertEquals(List.of(502, 501), teacherResults.stream()
                .map(ExecutionSubmissionSummaryDTO::getSubmissionId).toList());
        assertEquals(List.of(502, 501), coordinatorResults.stream()
                .map(ExecutionSubmissionSummaryDTO::getSubmissionId).toList());
        repositoryOrder.clear();
        assertEquals(2, teacherResults.size());
        assertThrows(UnsupportedOperationException.class, teacherResults::clear);
        assertSame(expectedReview, review);
        assertEquals(2, submissions.managerSummaryCalls);
        assertEquals(1, submissions.managerReviewCalls);
        assertEquals(TEACHER_ID, submissions.lastManagerId);
        assertEquals(SUBMISSION_ID, submissions.lastSubmissionId);
    }

    @Test
    public void managerReadsRejectUnsupportedInactiveAndMissingUsersBeforeRepositoryAccess() {
        assertManagerReadDenied(
                user(STUDENT_ID, UserRole.STUDENT, UserStatus.ACTIVE),
                IllegalStateException.class,
                "Only teachers and coordinators can manage executions"
        );
        assertManagerReadDenied(
                user(PRINCIPAL_ID, UserRole.PRINCIPAL, UserStatus.ACTIVE),
                IllegalStateException.class,
                "Only teachers and coordinators can manage executions"
        );
        assertManagerReadDenied(
                user(TEACHER_ID, UserRole.TEACHER, UserStatus.BLOCKED),
                IllegalStateException.class,
                "User account is blocked"
        );

        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        ExamExecutionService service = service(
                submissions,
                users(),
                new ExamExecutionServiceTestSupport.RecordingGradingService()
        );
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> service.getExecutionSubmissions(9999, EXECUTION_ID)
        );
        assertEquals("User not found: 9999", missing.getMessage());
        assertEquals(0, submissions.totalCalls());
    }

    @Test
    public void studentPublishedReadsUseOnlyStudentScopedSafeProjections() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        List<PublishedGradeSummaryDTO> repositoryOrder = new ArrayList<>(List.of(
                publishedSummary(502, NOW.minusMinutes(1)),
                publishedSummary(501, NOW.minusMinutes(2))
        ));
        PublishedGradeDTO expectedGrade = publishedGrade();
        submissions.publishedSummaries = repositoryOrder;
        submissions.publishedGrade = expectedGrade;
        ExamExecutionService service = service(
                submissions,
                users(user(STUDENT_ID, UserRole.STUDENT, UserStatus.ACTIVE)),
                new ExamExecutionServiceTestSupport.RecordingGradingService()
        );

        List<PublishedGradeSummaryDTO> grades =
                service.getMyPublishedGrades(STUDENT_ID);
        PublishedGradeDTO grade = service.getMyPublishedGrade(
                STUDENT_ID,
                SUBMISSION_ID
        );

        assertEquals(List.of(502, 501), grades.stream()
                .map(PublishedGradeSummaryDTO::getSubmissionId).toList());
        repositoryOrder.clear();
        assertEquals(2, grades.size());
        assertThrows(UnsupportedOperationException.class, grades::clear);
        assertSame(expectedGrade, grade);
        assertEquals(STUDENT_ID, submissions.lastStudentId);
        assertEquals(SUBMISSION_ID, submissions.lastSubmissionId);
        assertEquals(1, submissions.publishedSummaryCalls);
        assertEquals(1, submissions.publishedGradeCalls);
    }

    @Test
    public void managerRolesCannotUseStudentPublishedGradeApis() {
        for (UserRole role : List.of(UserRole.TEACHER, UserRole.COORDINATOR)) {
            ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                    new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
            ExamExecutionService service = service(
                    submissions,
                    users(user(TEACHER_ID, role, UserStatus.ACTIVE)),
                    new ExamExecutionServiceTestSupport.RecordingGradingService()
            );
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> service.getMyPublishedGrades(TEACHER_ID)
            );
            assertEquals("Only students can take exams", failure.getMessage());
            assertEquals(0, submissions.totalCalls());
        }
    }

    @Test
    public void studentPublishedReadsRejectPrincipalInactiveAndMissingUsers() {
        assertStudentReadDenied(
                user(PRINCIPAL_ID, UserRole.PRINCIPAL, UserStatus.ACTIVE),
                IllegalStateException.class,
                "Only students can take exams"
        );
        assertStudentReadDenied(
                user(STUDENT_ID, UserRole.STUDENT, UserStatus.BLOCKED),
                IllegalStateException.class,
                "User account is blocked"
        );

        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        ExamExecutionService service = service(
                submissions,
                users(),
                new ExamExecutionServiceTestSupport.RecordingGradingService()
        );
        assertEquals(
                "User not found: 9999",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.getMyPublishedGrades(9999)
                ).getMessage()
        );
        assertEquals(0, submissions.totalCalls());
    }

    @Test
    public void scopedDetailMissesUseOneSafeMessage() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        ExamExecutionService service = service(
                submissions,
                users(
                        user(TEACHER_ID, UserRole.TEACHER, UserStatus.ACTIVE),
                        user(STUDENT_ID, UserRole.STUDENT, UserStatus.ACTIVE)
                ),
                new ExamExecutionServiceTestSupport.RecordingGradingService()
        );

        assertEquals(
                SUBMISSION_NOT_FOUND,
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.getSubmissionForReview(
                                TEACHER_ID,
                                SUBMISSION_ID
                        )
                ).getMessage()
        );
        assertEquals(
                SUBMISSION_NOT_FOUND,
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.getMyPublishedGrade(
                                STUDENT_ID,
                                SUBMISSION_ID
                        )
                ).getMessage()
        );
    }

    @Test
    public void reviewUsesAuthenticatedManagerClockTransitionAndAuthoritativeReread() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        ExamSubmission source = awaitingReview(EXPECTED_UPDATED_AT);
        ExamSubmission transitioned = reviewedSubmission(TEACHER_ID, NOW);
        SubmissionReviewDTO authoritative = reviewDto(SubmissionStatus.SUBMITTED);
        submissions.lastSubmissionEntity = source;
        submissions.managerReview = authoritative;
        ExamExecutionServiceTestSupport.RecordingGradingService grading =
                new ExamExecutionServiceTestSupport.RecordingGradingService();
        grading.reviewResult = transitioned;
        ExamExecutionService service = service(
                submissions,
                users(user(TEACHER_ID, UserRole.TEACHER, UserStatus.ACTIVE)),
                grading
        );
        ReviewSubmissionPayload payload = new ReviewSubmissionPayload(
                SUBMISSION_ID,
                new BigDecimal("65.00"),
                "Good work",
                "Accepted alternate method",
                EXPECTED_UPDATED_AT
        );

        SubmissionReviewDTO result = service.reviewSubmissionGrade(
                TEACHER_ID,
                payload
        );

        assertEquals(1, submissions.managerEntityCalls);
        assertEquals(TEACHER_ID, submissions.lastManagerId);
        assertEquals(SUBMISSION_ID, submissions.lastSubmissionId);
        assertEquals(1, grading.reviewCalls);
        assertSame(source, grading.lastSubmission);
        assertEquals(TEACHER_ID, grading.lastManagerId);
        assertEquals(payload.getFinalScore(), grading.lastFinalScore);
        assertEquals(payload.getFeedback(), grading.lastFeedback);
        assertEquals(payload.getAdjustmentReason(), grading.lastAdjustmentReason);
        assertEquals(NOW, grading.lastTime);
        assertEquals(1, submissions.persistReviewCalls);
        assertSame(transitioned, submissions.lastPersistedReview);
        assertSame(authoritative, result);
        assertEquals(1, submissions.managerReviewCalls);
    }

    @Test
    public void publicationUsesAuthenticatedManagerClockTransitionAndAuthoritativeReread() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        ExamSubmission source = reviewedSubmission(TEACHER_ID, EXPECTED_UPDATED_AT);
        ExamSubmission transitioned = publishedSubmission(COORDINATOR_ID, NOW);
        SubmissionReviewDTO authoritative = reviewDto(SubmissionStatus.PUBLISHED);
        submissions.lastSubmissionEntity = source;
        submissions.managerReview = authoritative;
        ExamExecutionServiceTestSupport.RecordingGradingService grading =
                new ExamExecutionServiceTestSupport.RecordingGradingService();
        grading.publicationResult = transitioned;
        ExamExecutionService service = service(
                submissions,
                users(user(
                        COORDINATOR_ID,
                        UserRole.COORDINATOR,
                        UserStatus.ACTIVE
                )),
                grading
        );

        SubmissionReviewDTO result = service.publishSubmissionGrade(
                COORDINATOR_ID,
                new PublishSubmissionPayload(SUBMISSION_ID, EXPECTED_UPDATED_AT)
        );

        assertEquals(1, submissions.managerEntityCalls);
        assertEquals(1, grading.publicationCalls);
        assertSame(source, grading.lastSubmission);
        assertEquals(COORDINATOR_ID, grading.lastManagerId);
        assertEquals(NOW, grading.lastTime);
        assertEquals(1, submissions.persistPublicationCalls);
        assertSame(transitioned, submissions.lastPersistedPublication);
        assertSame(authoritative, result);
        assertEquals(1, submissions.managerReviewCalls);
    }

    @Test
    public void mutationPayloadValidationOccursBeforeEntityLoadOrTransition() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        ExamExecutionServiceTestSupport.RecordingGradingService grading =
                new ExamExecutionServiceTestSupport.RecordingGradingService();
        ExamExecutionService service = service(
                submissions,
                users(user(TEACHER_ID, UserRole.TEACHER, UserStatus.ACTIVE)),
                grading
        );

        assertEquals(
                "Submission review data is missing",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.reviewSubmissionGrade(TEACHER_ID, null)
                ).getMessage()
        );
        assertEquals(
                "Submission publication data is missing",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.publishSubmissionGrade(TEACHER_ID, null)
                ).getMessage()
        );
        assertEquals(
                "Final score is required",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.reviewSubmissionGrade(
                                TEACHER_ID,
                                new ReviewSubmissionPayload(
                                        SUBMISSION_ID,
                                        null,
                                        null,
                                        null,
                                        EXPECTED_UPDATED_AT
                                )
                        )
                ).getMessage()
        );
        assertEquals(
                "Expected submission update timestamp is required",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.reviewSubmissionGrade(
                                TEACHER_ID,
                                new ReviewSubmissionPayload(
                                        SUBMISSION_ID,
                                        new BigDecimal("60.00"),
                                        null,
                                        null,
                                        null
                                )
                        )
                ).getMessage()
        );
        assertEquals(
                "Expected submission update timestamp is required",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.publishSubmissionGrade(
                                TEACHER_ID,
                                new PublishSubmissionPayload(SUBMISSION_ID, null)
                        )
                ).getMessage()
        );
        assertEquals(0, submissions.totalCalls());
        assertEquals(0, grading.reviewCalls);
        assertEquals(0, grading.publicationCalls);
    }

    @Test
    public void staleReviewAndPublicationStopBeforeGradingAndPersistence() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        submissions.lastSubmissionEntity = awaitingReview(EXPECTED_UPDATED_AT);
        ExamExecutionServiceTestSupport.RecordingGradingService grading =
                new ExamExecutionServiceTestSupport.RecordingGradingService();
        ExamExecutionService service = service(
                submissions,
                users(user(TEACHER_ID, UserRole.TEACHER, UserStatus.ACTIVE)),
                grading
        );
        LocalDateTime stale = EXPECTED_UPDATED_AT.minusNanos(1);

        assertEquals(
                CONFLICT,
                assertThrows(
                        IllegalStateException.class,
                        () -> service.reviewSubmissionGrade(
                                TEACHER_ID,
                                new ReviewSubmissionPayload(
                                        SUBMISSION_ID,
                                        new BigDecimal("60.00"),
                                        null,
                                        null,
                                        stale
                                )
                        )
                ).getMessage()
        );
        assertEquals(
                CONFLICT,
                assertThrows(
                        IllegalStateException.class,
                        () -> service.publishSubmissionGrade(
                                TEACHER_ID,
                                new PublishSubmissionPayload(SUBMISSION_ID, stale)
                        )
                ).getMessage()
        );
        assertEquals(2, submissions.managerEntityCalls);
        assertEquals(0, grading.reviewCalls);
        assertEquals(0, grading.publicationCalls);
        assertEquals(0, submissions.persistReviewCalls);
        assertEquals(0, submissions.persistPublicationCalls);
    }

    @Test
    public void gradingDomainErrorsRemainUnchanged() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        submissions.lastSubmissionEntity = awaitingReview(EXPECTED_UPDATED_AT);
        ExamExecutionService service = serviceWithGrading(
                submissions,
                users(user(TEACHER_ID, UserRole.TEACHER, UserStatus.ACTIVE)),
                new GradingService()
        );

        assertEquals(
                "Manual score change reason is required",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.reviewSubmissionGrade(
                                TEACHER_ID,
                                new ReviewSubmissionPayload(
                                        SUBMISSION_ID,
                                        new BigDecimal("65.00"),
                                        "Feedback",
                                        null,
                                        EXPECTED_UPDATED_AT
                                )
                        )
                ).getMessage()
        );
        assertEquals(0, submissions.persistReviewCalls);

        submissions.lastSubmissionEntity = awaitingReview(EXPECTED_UPDATED_AT);
        assertEquals(
                "Submission must be reviewed before publication",
                assertThrows(
                        IllegalStateException.class,
                        () -> service.publishSubmissionGrade(
                                TEACHER_ID,
                                new PublishSubmissionPayload(
                                        SUBMISSION_ID,
                                        EXPECTED_UPDATED_AT
                                )
                        )
                ).getMessage()
        );
        assertEquals(0, submissions.persistPublicationCalls);
    }

    @Test
    public void repositoryFailuresAndMissingPostWriteRereadsAreNotHidden() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        submissions.lastSubmissionEntity = awaitingReview(EXPECTED_UPDATED_AT);
        ExamExecutionServiceTestSupport.RecordingGradingService grading =
                new ExamExecutionServiceTestSupport.RecordingGradingService();
        grading.reviewResult = reviewedSubmission(TEACHER_ID, NOW);
        ExamExecutionService service = service(
                submissions,
                users(user(TEACHER_ID, UserRole.TEACHER, UserStatus.ACTIVE)),
                grading
        );
        RuntimeException persistenceFailure = new IllegalStateException(
                "Failed to persist submission review"
        );
        submissions.persistReviewFailure = persistenceFailure;

        assertSame(
                persistenceFailure,
                assertThrows(
                        IllegalStateException.class,
                        () -> service.reviewSubmissionGrade(
                                TEACHER_ID,
                                new ReviewSubmissionPayload(
                                        SUBMISSION_ID,
                                        new BigDecimal("65.00"),
                                        "Feedback",
                                        "Reason",
                                        EXPECTED_UPDATED_AT
                                )
                        )
                )
        );
        assertEquals(0, submissions.managerReviewCalls);

        submissions.persistReviewFailure = null;
        submissions.lastSubmissionEntity = awaitingReview(EXPECTED_UPDATED_AT);
        assertEquals(
                "Submission review could not be reloaded: 501",
                assertThrows(
                        IllegalStateException.class,
                        () -> service.reviewSubmissionGrade(
                                TEACHER_ID,
                                new ReviewSubmissionPayload(
                                        SUBMISSION_ID,
                                        new BigDecimal("65.00"),
                                        "Feedback",
                                        "Reason",
                                        EXPECTED_UPDATED_AT
                                )
                        )
                ).getMessage()
        );
    }

    @Test
    public void publicationPersistenceFailuresAndMissingRereadsAreNotHidden() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        submissions.lastSubmissionEntity = reviewedSubmission(
                TEACHER_ID,
                EXPECTED_UPDATED_AT
        );
        ExamExecutionServiceTestSupport.RecordingGradingService grading =
                new ExamExecutionServiceTestSupport.RecordingGradingService();
        grading.publicationResult = publishedSubmission(TEACHER_ID, NOW);
        ExamExecutionService service = service(
                submissions,
                users(user(TEACHER_ID, UserRole.TEACHER, UserStatus.ACTIVE)),
                grading
        );
        RuntimeException persistenceFailure = new IllegalStateException(
                "Failed to persist submission publication"
        );
        submissions.persistPublicationFailure = persistenceFailure;

        assertSame(
                persistenceFailure,
                assertThrows(
                        IllegalStateException.class,
                        () -> service.publishSubmissionGrade(
                                TEACHER_ID,
                                new PublishSubmissionPayload(
                                        SUBMISSION_ID,
                                        EXPECTED_UPDATED_AT
                                )
                        )
                )
        );
        assertEquals(0, submissions.managerReviewCalls);

        submissions.persistPublicationFailure = null;
        submissions.lastSubmissionEntity = reviewedSubmission(
                TEACHER_ID,
                EXPECTED_UPDATED_AT
        );
        assertEquals(
                "Published submission could not be reloaded: 501",
                assertThrows(
                        IllegalStateException.class,
                        () -> service.publishSubmissionGrade(
                                TEACHER_ID,
                                new PublishSubmissionPayload(
                                        SUBMISSION_ID,
                                        EXPECTED_UPDATED_AT
                                )
                        )
                ).getMessage()
        );
    }

    @Test
    public void compatibilityConstructorKeepsReadsUsableAndGradesFailClearly() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        submissions.managerSummaries = List.of(executionSummary(501, "Student"));
        submissions.lastSubmissionEntity = awaitingReview(EXPECTED_UPDATED_AT);
        ExamExecutionService service = new ExamExecutionService(
                new ExamExecutionServiceTestSupport.RecordingExecutionRepository(),
                submissions,
                new ExamExecutionServiceTestSupport.RecordingEnrollmentRepository(),
                new ExamExecutionServiceTestSupport.RecordingProfileRepository(),
                users(user(TEACHER_ID, UserRole.TEACHER, UserStatus.ACTIVE))
        );

        assertEquals(1, service.getExecutionSubmissions(
                TEACHER_ID,
                EXECUTION_ID
        ).size());
        assertEquals(
                "Exam grading dependencies are not configured",
                assertThrows(
                        IllegalStateException.class,
                        () -> service.reviewSubmissionGrade(
                                TEACHER_ID,
                                new ReviewSubmissionPayload(
                                        SUBMISSION_ID,
                                        new BigDecimal("60.00"),
                                        null,
                                        null,
                                        EXPECTED_UPDATED_AT
                                )
                        )
                ).getMessage()
        );
        assertEquals(0, submissions.managerEntityCalls);
    }

    @Test
    public void positiveIdentifiersAreRequiredBeforeScopedRepositoryReads() {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        ExamExecutionService service = service(
                submissions,
                users(
                        user(TEACHER_ID, UserRole.TEACHER, UserStatus.ACTIVE),
                        user(STUDENT_ID, UserRole.STUDENT, UserStatus.ACTIVE)
                ),
                new ExamExecutionServiceTestSupport.RecordingGradingService()
        );

        assertEquals(
                "Execution ID must be positive",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.getExecutionSubmissions(TEACHER_ID, 0)
                ).getMessage()
        );
        assertEquals(
                "Submission ID must be positive",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.getSubmissionForReview(TEACHER_ID, -1)
                ).getMessage()
        );
        assertEquals(
                "Submission ID must be positive",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.getMyPublishedGrade(STUDENT_ID, 0)
                ).getMessage()
        );
        assertEquals(0, submissions.totalCalls());
    }

    private static void assertManagerReadDenied(
            hsts.server.entity.User deniedUser,
            Class<? extends RuntimeException> errorType,
            String message
    ) {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        ExamExecutionService service = service(
                submissions,
                users(deniedUser),
                new ExamExecutionServiceTestSupport.RecordingGradingService()
        );
        RuntimeException failure = assertThrows(
                errorType,
                () -> service.getExecutionSubmissions(
                        deniedUser.getUserId(),
                        EXECUTION_ID
                )
        );
        assertEquals(message, failure.getMessage());
        assertEquals(0, submissions.totalCalls());
    }

    private static void assertStudentReadDenied(
            hsts.server.entity.User deniedUser,
            Class<? extends RuntimeException> errorType,
            String message
    ) {
        ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions =
                new ExamExecutionServiceTestSupport.RecordingSubmissionRepository();
        ExamExecutionService service = service(
                submissions,
                users(deniedUser),
                new ExamExecutionServiceTestSupport.RecordingGradingService()
        );
        RuntimeException failure = assertThrows(
                errorType,
                () -> service.getMyPublishedGrades(deniedUser.getUserId())
        );
        assertEquals(message, failure.getMessage());
        assertEquals(0, submissions.totalCalls());
    }

    private static ExamExecutionService service(
            ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions,
            ExamExecutionServiceTestSupport.RecordingUserRepository users,
            ExamExecutionServiceTestSupport.RecordingGradingService grading
    ) {
        return serviceWithGrading(submissions, users, grading);
    }

    private static ExamExecutionService serviceWithGrading(
            ExamExecutionServiceTestSupport.RecordingSubmissionRepository submissions,
            ExamExecutionServiceTestSupport.RecordingUserRepository users,
            GradingService grading
    ) {
        return new ExamExecutionService(
                new ExamExecutionServiceTestSupport.RecordingExecutionRepository(),
                submissions,
                new ExamExecutionServiceTestSupport.RecordingEnrollmentRepository(),
                new ExamExecutionServiceTestSupport.RecordingProfileRepository(),
                users,
                new ExamExecutionServiceTestSupport.RecordingExamRepository(),
                grading,
                CLOCK
        );
    }

    private static ExamExecutionServiceTestSupport.RecordingUserRepository users(
            hsts.server.entity.User... users
    ) {
        return new ExamExecutionServiceTestSupport.RecordingUserRepository(users);
    }

    private static ExamSubmission awaitingReview(LocalDateTime updatedAt) {
        return submission(
                SubmissionStatus.SUBMITTED,
                new BigDecimal("60.00"),
                new BigDecimal("60.00"),
                null,
                null,
                null,
                null,
                null,
                null,
                updatedAt
        );
    }

    private static ExamSubmission reviewedSubmission(int reviewerId,
                                                      LocalDateTime reviewedAt) {
        return submission(
                SubmissionStatus.SUBMITTED,
                new BigDecimal("60.00"),
                new BigDecimal("65.00"),
                "Good work",
                "Accepted alternate method",
                reviewerId,
                reviewedAt,
                null,
                null,
                reviewedAt
        );
    }

    private static ExamSubmission publishedSubmission(int publisherId,
                                                       LocalDateTime publishedAt) {
        return submission(
                SubmissionStatus.PUBLISHED,
                new BigDecimal("60.00"),
                new BigDecimal("65.00"),
                "Good work",
                "Accepted alternate method",
                TEACHER_ID,
                EXPECTED_UPDATED_AT,
                publisherId,
                publishedAt,
                publishedAt
        );
    }

    private static ExamSubmission submission(
            SubmissionStatus status,
            BigDecimal automaticScore,
            BigDecimal finalScore,
            String feedback,
            String reason,
            Integer reviewerId,
            LocalDateTime reviewedAt,
            Integer publisherId,
            LocalDateTime publishedAt,
            LocalDateTime updatedAt
    ) {
        LocalDateTime startedAt = NOW.minusHours(2);
        LocalDateTime submittedAt = NOW.minusHours(1);
        return ExamSubmission.rehydrate(
                SUBMISSION_ID,
                EXECUTION_ID,
                40,
                3,
                STUDENT_ID,
                startedAt,
                submittedAt,
                status,
                75,
                0,
                null,
                60,
                automaticScore,
                finalScore,
                feedback,
                reason,
                reviewerId,
                reviewedAt,
                publisherId,
                publishedAt,
                startedAt,
                updatedAt,
                List.of()
        );
    }

    private static ExecutionSubmissionSummaryDTO executionSummary(
            int submissionId,
            String studentName
    ) {
        return new ExecutionSubmissionSummaryDTO(
                submissionId,
                EXECUTION_ID,
                40,
                3,
                "Historical Final",
                STUDENT_ID,
                studentName,
                SubmissionStatus.SUBMITTED,
                new BigDecimal("60.00"),
                new BigDecimal("65.00"),
                NOW.minusHours(2),
                NOW.minusHours(1),
                EXPECTED_UPDATED_AT,
                null
        );
    }

    private static SubmissionReviewDTO reviewDto(SubmissionStatus status) {
        return new SubmissionReviewDTO(
                SUBMISSION_ID,
                EXECUTION_ID,
                40,
                3,
                "Historical Final",
                STUDENT_ID,
                "Development Student",
                status,
                new BigDecimal("60.00"),
                new BigDecimal("65.00"),
                "Good work",
                "Accepted alternate method",
                TEACHER_ID,
                NOW.minusHours(2),
                NOW.minusHours(1),
                EXPECTED_UPDATED_AT,
                status == SubmissionStatus.PUBLISHED ? COORDINATOR_ID : 0,
                status == SubmissionStatus.PUBLISHED ? NOW : null,
                List.of()
        );
    }

    private static PublishedGradeSummaryDTO publishedSummary(
            int submissionId,
            LocalDateTime publishedAt
    ) {
        return new PublishedGradeSummaryDTO(
                submissionId,
                EXECUTION_ID,
                40,
                3,
                "Historical Final",
                "Mathematics",
                new BigDecimal("65.00"),
                NOW.minusHours(1),
                publishedAt
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
                NOW.minusHours(1),
                EXPECTED_UPDATED_AT,
                NOW
        );
    }
}
