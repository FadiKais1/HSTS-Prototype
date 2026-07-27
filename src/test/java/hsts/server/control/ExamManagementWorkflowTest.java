package hsts.server.control;

import hsts.common.ExamDTO;
import hsts.common.ExamQuestionSelectionPayload;
import hsts.common.ExamVersionPayload;
import hsts.common.QuestionDTO;
import hsts.common.RejectExamPayload;
import hsts.common.UpdateExamPayload;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.ExamStatus;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.AnswerOption;
import hsts.server.entity.Exam;
import hsts.server.entity.ExamQuestion;
import hsts.server.entity.Question;
import hsts.server.entity.User;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.ExamRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.repository.UserRepository;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ExamManagementWorkflowTest {
    @Test
    public void activeTeacherAndCoordinatorCanUpdateAndSubmit() {
        for (UserRole role : new UserRole[]{UserRole.TEACHER, UserRole.COORDINATOR}) {
            int userId = role == UserRole.TEACHER ? 1101 : 1102;

            RecordingExamRepository updateExams = new RecordingExamRepository();
            RecordingQuestionRepository updateQuestions = new RecordingQuestionRepository();
            updateExams.setTeacherExamBefore(exam(45, 7, 4, ExamStatus.DRAFT, userId));
            ExamDTO updated = exam(45, 7, 5, ExamStatus.DRAFT, userId);
            updateExams.setTeacherExamAfter(updated);
            updateQuestions.addQuestion(question(17, 7, 4, "ACTIVE"));
            ExamManagementService updateService = service(
                    updateExams,
                    updateQuestions,
                    user(userId, role, UserStatus.ACTIVE)
            );

            assertSame(updated, updateService.updateExam(userId, validUpdatePayload()));
            assertEquals(userId, updateExams.getLastUpdateUserId());

            RecordingExamRepository submitExams = new RecordingExamRepository();
            ExamDTO pending = exam(45, 7, 4, ExamStatus.PENDING_APPROVAL, userId);
            submitExams.setTeacherExamAfter(pending);
            ExamManagementService submitService = service(
                    submitExams,
                    new RecordingQuestionRepository(),
                    user(userId, role, UserStatus.ACTIVE)
            );

            assertSame(
                    pending,
                    submitService.submitExamForApproval(
                            userId,
                            new ExamVersionPayload(45, 4)
                    )
            );
            assertEquals(userId, submitExams.getLastSubmitUserId());
            assertEquals(45, submitExams.getLastSubmitExamId());
            assertEquals(4, submitExams.getLastSubmitExpectedVersion());
            assertEquals(ExamStatus.PENDING_APPROVAL,
                    submitExams.getLastSubmitExam().getStatus());
            assertEquals(4, submitExams.getLastSubmitExam().getCurrentVersionNo());
            assertEquals(0, submitExams.getUpdateCalls());
        }
    }

    @Test
    public void coordinatorCanApproveAndRejectSelfAuthoredExamWithoutCreatingVersion() {
        int coordinatorId = 1201;
        RecordingExamRepository approveExams = new RecordingExamRepository();
        ExamDTO approved = exam(55, 7, 4, ExamStatus.APPROVED, coordinatorId);
        approveExams.setCoordinatorExamAfter(approved);
        ExamManagementService approveService = service(
                approveExams,
                new RecordingQuestionRepository(),
                user(coordinatorId, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );

        assertSame(
                approved,
                approveService.approveExam(
                        coordinatorId,
                        new ExamVersionPayload(55, 4)
                )
        );
        assertEquals(coordinatorId, approveExams.getLastApproveUserId());
        assertEquals(55, approveExams.getLastApproveExamId());
        assertEquals(4, approveExams.getLastApproveExpectedVersion());
        assertEquals(ExamStatus.APPROVED,
                approveExams.getLastApproveExam().getStatus());
        assertEquals(Integer.valueOf(coordinatorId),
                approveExams.getLastApproveExam().getReviewedByUserId());
        assertEquals(0, approveExams.getUpdateCalls());

        RecordingExamRepository rejectExams = new RecordingExamRepository();
        ExamDTO rejected = exam(56, 7, 4, ExamStatus.REJECTED, coordinatorId);
        rejectExams.setCoordinatorExamAfter(rejected);
        ExamManagementService rejectService = service(
                rejectExams,
                new RecordingQuestionRepository(),
                user(coordinatorId, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );

        assertSame(
                rejected,
                rejectService.rejectExam(
                        coordinatorId,
                        new RejectExamPayload(56, 4, "  Needs revision  ")
                )
        );
        assertEquals(coordinatorId, rejectExams.getLastRejectUserId());
        assertEquals(56, rejectExams.getLastRejectExamId());
        assertEquals(4, rejectExams.getLastRejectExpectedVersion());
        assertEquals("Needs revision", rejectExams.getLastRejectReason());
        assertEquals(ExamStatus.REJECTED,
                rejectExams.getLastRejectExam().getStatus());
        assertEquals(Integer.valueOf(coordinatorId),
                rejectExams.getLastRejectExam().getReviewedByUserId());
        assertEquals(0, rejectExams.getUpdateCalls());
    }

    @Test
    public void unauthorizedBlockedAndMissingUsersPerformNoWorkflowOperation() {
        for (UserRole role : new UserRole[]{UserRole.STUDENT, UserRole.PRINCIPAL}) {
            int userId = 1300 + role.ordinal();
            RecordingExamRepository exams = new RecordingExamRepository();
            ExamManagementService service = service(
                    exams,
                    new RecordingQuestionRepository(),
                    user(userId, role, UserStatus.ACTIVE)
            );

            IllegalStateException update = assertThrows(
                    IllegalStateException.class,
                    () -> service.updateExam(userId, validUpdatePayload())
            );
            assertEquals(
                    "Question management requires teacher or coordinator role",
                    update.getMessage()
            );

            IllegalStateException approve = assertThrows(
                    IllegalStateException.class,
                    () -> service.approveExam(userId, new ExamVersionPayload(45, 4))
            );
            assertEquals("Only coordinators can review exams", approve.getMessage());
            assertEquals(0, exams.getTotalWorkflowCalls());
        }

        RecordingExamRepository blockedExams = new RecordingExamRepository();
        ExamManagementService blockedService = service(
                blockedExams,
                new RecordingQuestionRepository(),
                user(1310, UserRole.COORDINATOR, UserStatus.BLOCKED)
        );
        IllegalStateException blocked = assertThrows(
                IllegalStateException.class,
                () -> blockedService.rejectExam(
                        1310,
                        new RejectExamPayload(45, 4, "Reason")
                )
        );
        assertEquals("User account is blocked", blocked.getMessage());
        assertEquals(0, blockedExams.getTotalWorkflowCalls());

        RecordingExamRepository missingExams = new RecordingExamRepository();
        ExamManagementService missingService = service(
                missingExams,
                new RecordingQuestionRepository()
        );
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> missingService.submitExamForApproval(
                        404,
                        new ExamVersionPayload(45, 4)
                )
        );
        assertEquals("User not found: 404", missing.getMessage());
        assertEquals(0, missingExams.getTotalWorkflowCalls());
    }

    @Test
    public void nullWorkflowPayloadsUseExactMessagesAfterAuthorization() {
        RecordingExamRepository exams = new RecordingExamRepository();
        ExamManagementService service = service(
                exams,
                new RecordingQuestionRepository(),
                user(1401, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );

        assertEquals(
                "Exam update data is missing",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.updateExam(1401, null)
                ).getMessage()
        );
        assertEquals(
                "Exam version data is missing",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.submitExamForApproval(1401, null)
                ).getMessage()
        );
        assertEquals(
                "Exam version data is missing",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.approveExam(1401, null)
                ).getMessage()
        );
        assertEquals(
                "Exam rejection data is missing",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.rejectExam(1401, null)
                ).getMessage()
        );
        assertEquals(0, exams.getTotalWorkflowCalls());
    }

    @Test
    public void updateValidationRetainsCreateOrderAndExactMessages() {
        RecordingExamRepository exams = new RecordingExamRepository();
        exams.setTeacherExamBefore(exam(45, 7, 4, ExamStatus.DRAFT, 1501));
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        ExamManagementService service = service(
                exams,
                questions,
                user(1501, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        assertUpdateFailure(service, updatePayload(" ", 0, " ", List.of()),
                "Exam title is required");
        assertUpdateFailure(service, updatePayload("Title", 0, " ", List.of()),
                "Exam duration must be positive");
        assertUpdateFailure(service, updatePayload("Title", 60, " ", List.of()),
                "Student instructions are required");
        assertUpdateFailure(service, updatePayload("Title", 60, "Instructions", List.of()),
                "At least one question is required");
        assertUpdateFailure(service, updatePayload("Title", 60, "Instructions", List.of(
                selection(17, 4, 2, 100)
        )), "Question order must start at 1 and be contiguous");
        assertUpdateFailure(service, updatePayload("Title", 60, "Instructions", List.of(
                selection(17, 4, 1, 50),
                selection(17, 4, 2, 50)
        )), "Duplicate question: 17");
        assertUpdateFailure(service, updatePayload("Title", 60, "Instructions", List.of(
                selection(17, 4, 1, Double.NaN)
        )), "Question score must be positive and finite");
        assertUpdateFailure(service, updatePayload("Title", 60, "Instructions", List.of(
                selection(17, 4, 1, 99.99)
        )), "Exam total score must equal 100");

        assertEquals(0, questions.getFindCalls());
        assertEquals(0, exams.getUpdateCalls());
    }

    @Test
    public void updateUsesImmutableCurrentCourseAndRejectsUnavailableQuestionStates() {
        assertUnavailableQuestion(null);
        assertUnavailableQuestion(question(17, 8, 4, "ACTIVE"));
        assertUnavailableQuestion(question(17, 7, 4, "INACTIVE"));
        assertUnavailableQuestion(question(17, 7, 5, "ACTIVE"));
    }

    @Test
    public void pendingExamCannotBeEditedThroughEntityUpdatePath() {
        RecordingExamRepository exams = new RecordingExamRepository();
        exams.setTeacherExamBefore(exam(45, 7, 4, ExamStatus.PENDING_APPROVAL, 1552));
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        questions.addQuestion(question(17, 7, 4, "ACTIVE"));
        ExamManagementService service = service(
                exams,
                questions,
                user(1552, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.updateExam(1552, validUpdatePayload())
        );

        assertEquals("Pending exam cannot be edited", failure.getMessage());
        assertEquals(0, exams.getUpdateCalls());
    }

    @Test
    public void rejectedExamStartsANewDraftVersionAndClearsReviewMetadata() {
        int userId = 1554;
        RecordingExamRepository exams = new RecordingExamRepository();
        exams.setTeacherExamBefore(exam(45, 7, 4, ExamStatus.REJECTED, userId));
        exams.setTeacherExamAfter(exam(45, 7, 5, ExamStatus.DRAFT, userId));
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        questions.addQuestion(question(17, 7, 4, "ACTIVE"));
        ExamManagementService service = service(
                exams,
                questions,
                user(userId, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        service.updateExam(userId, validUpdatePayload());

        Exam draft = exams.getLastUpdateExam();
        assertEquals(5, draft.getCurrentVersionNo());
        assertEquals(ExamStatus.DRAFT, draft.getStatus());
        assertNull(draft.getSubmittedAt());
        assertNull(draft.getReviewedByUserId());
        assertNull(draft.getReviewedAt());
        assertNull(draft.getRejectionReason());
        assertEquals(0, exams.getPayloadUpdateCalls());
    }

    @Test
    public void submitUsesAggregateCompletenessAndScoreValidation() {
        for (List<ExamQuestion> selections : List.of(
                List.<ExamQuestion>of(),
                List.of(ExamQuestion.select(
                        4,
                        1,
                        new BigDecimal("99.00"),
                        questionEntity(question(17, 7, 4, "ACTIVE"))
                ))
        )) {
            RecordingExamRepository exams = new RecordingExamRepository();
            LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 10, 0);
            exams.setTeacherExamEntity(Exam.rehydrate(
                    45,
                    "ABC123",
                    7,
                    1553,
                    4,
                    "Exam",
                    60,
                    "",
                    "Instructions",
                    ExamStatus.DRAFT,
                    createdAt,
                    createdAt.plusMinutes(1),
                    null,
                    null,
                    null,
                    null,
                    selections
            ));
            ExamManagementService service = service(
                    exams,
                    new RecordingQuestionRepository(),
                    user(1553, UserRole.TEACHER, UserStatus.ACTIVE)
            );

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> service.submitExamForApproval(
                            1553,
                            new ExamVersionPayload(45, 4)
                    )
            );

            assertEquals(
                    selections.isEmpty()
                            ? "At least one question is required"
                            : "Exam total score must equal 100",
                    failure.getMessage()
            );
            assertEquals(0, exams.submitCalls);
        }
    }

    @Test
    public void updateNormalizesTextPreservesExpectedVersionSelectionsAndIdentityThenRereads() {
        int userId = 1601;
        RecordingExamRepository exams = new RecordingExamRepository();
        exams.setTeacherExamBefore(exam(45, 7, 4, ExamStatus.APPROVED, userId));
        ExamDTO updated = exam(45, 7, 5, ExamStatus.DRAFT, userId);
        exams.setTeacherExamAfter(updated);
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        questions.addQuestion(question(18, 7, 2, "ACTIVE"));
        questions.addQuestion(question(17, 7, 4, "ACTIVE"));
        List<ExamQuestionSelectionPayload> selections = List.of(
                selection(18, 2, 2, 66.67),
                selection(17, 4, 1, 33.33)
        );
        UpdateExamPayload payload = new UpdateExamPayload(
                45, 4, "  Updated title  ", 90, null,
                "  Read carefully  ", selections
        );
        ExamManagementService service = service(
                exams,
                questions,
                user(userId, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        ExamDTO result = service.updateExam(userId, payload);

        assertSame(updated, result);
        assertEquals(userId, exams.getLastUpdateUserId());
        assertEquals(List.of(userId, userId, userId, userId),
                questions.getRequestedUserIds());
        assertEquals(List.of(18, 17, 18, 17), questions.getRequestedQuestionIds());
        assertEquals(1, exams.getTeacherDetailCalls());

        Exam normalized = exams.getLastUpdateExam();
        assertEquals(45, normalized.getExamId());
        assertEquals("ABC123", normalized.getExamCode());
        assertEquals(7, normalized.getCourseId());
        assertEquals(userId, normalized.getCreatedByUserId());
        assertEquals(5, normalized.getCurrentVersionNo());
        assertEquals(4, exams.getLastUpdateExpectedVersion());
        assertEquals(ExamStatus.DRAFT, normalized.getStatus());
        assertEquals("Updated title", normalized.getTitle());
        assertEquals(90, normalized.getDurationMinutes());
        assertEquals("", normalized.getTeacherNotes());
        assertEquals("Read carefully", normalized.getStudentInstructions());
        assertEquals(LocalDateTime.of(2026, 7, 1, 10, 0), normalized.getCreatedAt());
        assertEquals(0, exams.getPayloadUpdateCalls());

        List<ExamQuestion> normalizedQuestions = normalized.getExamQuestions();
        assertEquals(2, normalizedQuestions.size());
        assertExamQuestion(normalizedQuestions.get(0), 17, 4, 1, "33.33");
        assertExamQuestion(normalizedQuestions.get(1), 18, 2, 2, "66.67");
    }

    @Test
    public void updateWorkflowAndInfrastructureFailuresPropagateUnchanged() {
        for (RuntimeException failure : new RuntimeException[]{
                new IllegalStateException("Pending exam cannot be edited"),
                new IllegalStateException("Exam version conflict"),
                new IllegalStateException("Failed to update exam version")
        }) {
            RecordingExamRepository exams = new RecordingExamRepository();
            exams.setTeacherExamBefore(exam(45, 7, 4, ExamStatus.DRAFT, 1701));
            exams.setUpdateFailure(failure);
            RecordingQuestionRepository questions = new RecordingQuestionRepository();
            questions.addQuestion(question(17, 7, 4, "ACTIVE"));
            ExamManagementService service = service(
                    exams,
                    questions,
                    user(1701, UserRole.TEACHER, UserStatus.ACTIVE)
            );

            assertSame(failure, assertThrows(
                    RuntimeException.class,
                    () -> service.updateExam(1701, validUpdatePayload())
            ));
        }
    }

    @Test
    public void transitionRepositoryCallsRereadExactResultAndPropagateFailures() {
        int coordinatorId = 1801;
        RuntimeException failure = new IllegalStateException("Exam version conflict");
        RecordingExamRepository failingExams = new RecordingExamRepository();
        failingExams.setSubmitFailure(failure);
        ExamManagementService failingService = service(
                failingExams,
                new RecordingQuestionRepository(),
                user(coordinatorId, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );
        assertSame(failure, assertThrows(
                RuntimeException.class,
                () -> failingService.submitExamForApproval(
                        coordinatorId,
                        new ExamVersionPayload(45, 4)
                )
        ));
        assertEquals(0, failingExams.getTeacherDetailCalls());

        RecordingExamRepository approveExams = new RecordingExamRepository();
        RuntimeException approveFailure = new IllegalStateException(
                "Exam is not pending approval"
        );
        approveExams.setApproveFailure(approveFailure);
        ExamManagementService approveService = service(
                approveExams,
                new RecordingQuestionRepository(),
                user(coordinatorId, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );
        assertSame(approveFailure, assertThrows(
                RuntimeException.class,
                () -> approveService.approveExam(
                        coordinatorId,
                        new ExamVersionPayload(45, 4)
                )
        ));
        assertEquals(0, approveExams.getCoordinatorDetailCalls());
    }

    @Test
    public void falseTransitionResultsUseExactNotFoundMessageWithoutReread() {
        for (Transition transition : Transition.values()) {
            RecordingExamRepository exams = new RecordingExamRepository();
            transition.configureFalse(exams);
            ExamManagementService service = service(
                    exams,
                    new RecordingQuestionRepository(),
                    user(1901, UserRole.COORDINATOR, UserStatus.ACTIVE)
            );

            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> transition.execute(service, 1901)
            );

            assertEquals("Exam not found: 45", failure.getMessage());
            assertEquals(0, exams.getTeacherDetailCalls());
            assertEquals(0, exams.getCoordinatorDetailCalls());
            assertEquals(0, exams.getUpdateCalls());
        }
    }

    @Test
    public void rejectionRequiresNonblankReasonBeforeRepositoryCall() {
        RecordingExamRepository exams = new RecordingExamRepository();
        ExamManagementService service = service(
                exams,
                new RecordingQuestionRepository(),
                user(2001, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );

        for (String reason : new String[]{null, "", "   "}) {
            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.rejectExam(
                            2001,
                            new RejectExamPayload(45, 4, reason)
                    )
            );
            assertEquals("Rejection reason is required", failure.getMessage());
        }
        assertEquals(0, exams.getRejectCalls());
    }

    private static void assertUpdateFailure(ExamManagementService service,
                                            UpdateExamPayload payload,
                                            String expectedMessage) {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateExam(1501, payload)
        );
        assertEquals(expectedMessage, failure.getMessage());
    }

    private static void assertUnavailableQuestion(QuestionDTO availableQuestion) {
        RecordingExamRepository exams = new RecordingExamRepository();
        exams.setTeacherExamBefore(exam(45, 7, 4, ExamStatus.DRAFT, 1551));
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        if (availableQuestion != null) {
            questions.addQuestion(availableQuestion);
        }
        ExamManagementService service = service(
                exams,
                questions,
                user(1551, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateExam(1551, validUpdatePayload())
        );

        assertEquals("Question unavailable for exam: 17", failure.getMessage());
        assertEquals(0, exams.getUpdateCalls());
        assertEquals(List.of(1551), questions.getRequestedUserIds());
    }

    private static ExamManagementService service(RecordingExamRepository exams,
                                                 RecordingQuestionRepository questions,
                                                 User... users) {
        return new ExamManagementService(
                questions,
                new CourseRepository(),
                new RecordingUserRepository(users),
                exams
        );
    }

    private static User user(int userId, UserRole role, UserStatus status) {
        return new User(
                userId,
                "Development User",
                "user" + userId + "@hsts.local",
                "stored-hash",
                role,
                status
        );
    }

    private static UpdateExamPayload validUpdatePayload() {
        return new UpdateExamPayload(
                45, 4, "Updated title", 90, "Notes", "Instructions",
                List.of(selection(17, 4, 1, 100))
        );
    }

    private static UpdateExamPayload updatePayload(String title, int duration,
                                                   String instructions,
                                                   List<ExamQuestionSelectionPayload> questions) {
        return new UpdateExamPayload(
                45, 4, title, duration, "Notes", instructions, questions
        );
    }

    private static ExamQuestionSelectionPayload selection(int questionId, int versionNo,
                                                           int orderNumber, double score) {
        return new ExamQuestionSelectionPayload(questionId, versionNo, orderNumber, score);
    }

    private static QuestionDTO question(int questionId, int courseId, int versionNo,
                                        String status) {
        return new QuestionDTO(
                questionId, "Content", "Topic", "MULTIPLE_CHOICE", "MEDIUM", status,
                "", "One", "Two", "Three", "Four", 2,
                courseId, 3, versionNo
        );
    }

    private static Question questionEntity(QuestionDTO question) {
        return Question.rehydrate(
                question.getQuestionId(),
                question.getContent(),
                QuestionType.valueOf(question.getType()),
                DifficultyLevel.valueOf(question.getDifficulty()),
                QuestionStatus.valueOf(question.getStatus()),
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 9, 30),
                question.getTopic(),
                question.getIllustrationPath(),
                List.of(
                        new AnswerOption(1, question.getAnswerOption1(),
                                question.getCorrectOptionNumber() == 1),
                        new AnswerOption(2, question.getAnswerOption2(),
                                question.getCorrectOptionNumber() == 2),
                        new AnswerOption(3, question.getAnswerOption3(),
                                question.getCorrectOptionNumber() == 3),
                        new AnswerOption(4, question.getAnswerOption4(),
                                question.getCorrectOptionNumber() == 4)
                )
        );
    }

    private static Exam examEntity(ExamDTO exam, ExamStatus status) {
        LocalDateTime createdAt = exam.getCreatedAt();
        LocalDateTime submittedAt = status == ExamStatus.DRAFT
                ? null : createdAt.plusMinutes(5);
        LocalDateTime reviewedAt = status == ExamStatus.APPROVED
                || status == ExamStatus.REJECTED
                ? createdAt.plusMinutes(10) : null;
        LocalDateTime updatedAt = reviewedAt != null
                ? reviewedAt
                : submittedAt != null ? submittedAt : createdAt.plusMinutes(1);
        Integer reviewerId = reviewedAt == null ? null : 999;
        String rejectionReason = status == ExamStatus.REJECTED
                ? "Needs revision" : null;
        Question snapshot = questionEntity(question(17, exam.getCourseId(), 4, "ACTIVE"));

        return Exam.rehydrate(
                exam.getExamId(),
                exam.getExamCode(),
                exam.getCourseId(),
                exam.getCreatedByUserId(),
                exam.getVersionNo(),
                exam.getTitle(),
                exam.getDurationMinutes(),
                exam.getTeacherNotes(),
                exam.getStudentInstructions(),
                status,
                createdAt,
                updatedAt,
                submittedAt,
                reviewerId,
                reviewedAt,
                rejectionReason,
                List.of(ExamQuestion.select(
                        4,
                        1,
                        new BigDecimal("100.00"),
                        snapshot
                ))
        );
    }

    private static void assertExamQuestion(ExamQuestion selection, int questionId,
                                           int versionNo, int orderNumber,
                                           String score) {
        assertEquals(questionId, selection.getQuestionId());
        assertEquals(versionNo, selection.getQuestionVersionNo());
        assertEquals(orderNumber, selection.getOrderNumber());
        assertEquals(new BigDecimal(score), selection.getScoreValue());
        assertEquals(questionId, selection.getQuestion().getQuestionId());
    }

    private static ExamDTO exam(int examId, int courseId, int versionNo,
                                ExamStatus status, int creatorId) {
        return new ExamDTO(
                examId, "ABC123", courseId, "Course", 3, "Subject", creatorId,
                "Creator", versionNo, "Exam", 60, "", "Instructions", 100,
                status, LocalDateTime.of(2026, 7, 1, 10, 0),
                null, null, null, null, null, List.of()
        );
    }

    private static final class RecordingUserRepository extends UserRepository {
        private final Map<Integer, User> users = new LinkedHashMap<>();

        private RecordingUserRepository(User... initialUsers) {
            for (User user : initialUsers) {
                users.put(user.getUserId(), user);
            }
        }

        @Override
        public Optional<User> findById(int userId) {
            return Optional.ofNullable(users.get(userId));
        }
    }

    private static final class RecordingQuestionRepository extends QuestionRepository {
        private final Map<Integer, QuestionDTO> questions = new LinkedHashMap<>();
        private final List<Integer> requestedUserIds = new ArrayList<>();
        private final List<Integer> requestedQuestionIds = new ArrayList<>();

        @Override
        public Optional<QuestionDTO> findCurrentByIdForTeacher(int authenticatedUserId,
                                                                int questionId) {
            requestedUserIds.add(authenticatedUserId);
            requestedQuestionIds.add(questionId);
            return Optional.ofNullable(questions.get(questionId));
        }

        @Override
        public Optional<Question> findCurrentEntityByIdForTeacher(
                int authenticatedUserId,
                int questionId
        ) {
            QuestionDTO question = questions.get(questionId);
            return question == null
                    ? Optional.empty()
                    : Optional.of(questionEntity(question));
        }

        private void addQuestion(QuestionDTO question) {
            questions.put(question.getQuestionId(), question);
        }

        private int getFindCalls() {
            return requestedQuestionIds.size();
        }

        private List<Integer> getRequestedUserIds() {
            return requestedUserIds;
        }

        private List<Integer> getRequestedQuestionIds() {
            return requestedQuestionIds;
        }
    }

    private static final class RecordingExamRepository extends ExamRepository {
        private ExamDTO teacherExamBefore;
        private ExamDTO teacherExamAfter;
        private ExamDTO coordinatorExamAfter;
        private Exam teacherExamEntity;
        private Exam coordinatorExamEntity;
        private Exam lastUpdateExam;
        private Exam lastSubmitExam;
        private Exam lastApproveExam;
        private Exam lastRejectExam;
        private RuntimeException updateFailure;
        private RuntimeException submitFailure;
        private RuntimeException approveFailure;
        private boolean submitResult = true;
        private boolean approveResult = true;
        private boolean rejectResult = true;
        private int updateCalls;
        private int payloadUpdateCalls;
        private int submitCalls;
        private int approveCalls;
        private int rejectCalls;
        private int teacherDetailCalls;
        private int coordinatorDetailCalls;
        private int lastUpdateUserId;
        private int lastUpdateExpectedVersion;
        private int lastSubmitUserId;
        private int lastSubmitExamId;
        private int lastSubmitExpectedVersion;
        private int lastApproveUserId;
        private int lastApproveExamId;
        private int lastApproveExpectedVersion;
        private int lastRejectUserId;
        private int lastRejectExamId;
        private int lastRejectExpectedVersion;
        private String lastRejectReason;

        @Override
        public Optional<ExamDTO> findByIdForTeacher(int authenticatedUserId, int examId) {
            teacherDetailCalls++;
            ExamDTO result = updateCalls > 0 || submitCalls > 0
                    ? teacherExamAfter : teacherExamBefore;
            return Optional.ofNullable(result);
        }

        @Override
        public Optional<ExamDTO> findByIdForCoordinator(int authenticatedUserId, int examId) {
            coordinatorDetailCalls++;
            return Optional.ofNullable(coordinatorExamAfter);
        }

        @Override
        public Optional<Exam> findCurrentEntityForTeacher(int authenticatedUserId,
                                                          int examId) {
            Exam result = teacherExamEntity;
            if (result == null) {
                result = examEntity(
                        exam(examId, 7, 4, ExamStatus.DRAFT, authenticatedUserId),
                        ExamStatus.DRAFT
                );
            }
            return Optional.of(result);
        }

        @Override
        public Optional<Exam> findCurrentEntityForCoordinator(int authenticatedUserId,
                                                              int examId) {
            Exam result = coordinatorExamEntity;
            if (result == null) {
                result = examEntity(
                        exam(examId, 7, 4, ExamStatus.PENDING_APPROVAL,
                                authenticatedUserId),
                        ExamStatus.PENDING_APPROVAL
                );
            }
            return Optional.of(result);
        }

        @Override
        public int updateWithNewVersion(int authenticatedUserId, int examId,
                                        int expectedVersionNo, Exam exam) {
            updateCalls++;
            lastUpdateUserId = authenticatedUserId;
            lastUpdateExpectedVersion = expectedVersionNo;
            lastUpdateExam = exam;
            if (updateFailure != null) {
                throw updateFailure;
            }
            return expectedVersionNo + 1;
        }

        @Override
        public int updateWithNewVersion(int authenticatedUserId,
                                        UpdateExamPayload payload) {
            payloadUpdateCalls++;
            throw new AssertionError("Payload repository update must not be used");
        }

        @Override
        public boolean persistSubmissionForApproval(int authenticatedUserId,
                                                    Exam exam) {
            submitCalls++;
            lastSubmitUserId = authenticatedUserId;
            lastSubmitExamId = exam.getExamId();
            lastSubmitExpectedVersion = exam.getCurrentVersionNo();
            lastSubmitExam = exam;
            if (submitFailure != null) {
                throw submitFailure;
            }
            return submitResult;
        }

        @Override
        public boolean persistApproval(int authenticatedCoordinatorId, Exam exam) {
            approveCalls++;
            lastApproveUserId = authenticatedCoordinatorId;
            lastApproveExamId = exam.getExamId();
            lastApproveExpectedVersion = exam.getCurrentVersionNo();
            lastApproveExam = exam;
            if (approveFailure != null) {
                throw approveFailure;
            }
            return approveResult;
        }

        @Override
        public boolean persistRejection(int authenticatedCoordinatorId, Exam exam) {
            rejectCalls++;
            lastRejectUserId = authenticatedCoordinatorId;
            lastRejectExamId = exam.getExamId();
            lastRejectExpectedVersion = exam.getCurrentVersionNo();
            lastRejectReason = exam.getRejectionReason();
            lastRejectExam = exam;
            return rejectResult;
        }

        private void setTeacherExamBefore(ExamDTO teacherExamBefore) {
            this.teacherExamBefore = teacherExamBefore;
            this.teacherExamEntity = examEntity(
                    teacherExamBefore,
                    teacherExamBefore.getStatus()
            );
        }

        private void setTeacherExamAfter(ExamDTO teacherExamAfter) {
            this.teacherExamAfter = teacherExamAfter;
            if (teacherExamEntity == null) {
                this.teacherExamEntity = examEntity(
                        teacherExamAfter,
                        ExamStatus.DRAFT
                );
            }
        }

        private void setTeacherExamEntity(Exam teacherExamEntity) {
            this.teacherExamEntity = teacherExamEntity;
        }

        private void setCoordinatorExamAfter(ExamDTO coordinatorExamAfter) {
            this.coordinatorExamAfter = coordinatorExamAfter;
            this.coordinatorExamEntity = examEntity(
                    coordinatorExamAfter,
                    ExamStatus.PENDING_APPROVAL
            );
        }

        private void setUpdateFailure(RuntimeException updateFailure) {
            this.updateFailure = updateFailure;
        }

        private void setSubmitFailure(RuntimeException submitFailure) {
            this.submitFailure = submitFailure;
        }

        private void setApproveFailure(RuntimeException approveFailure) {
            this.approveFailure = approveFailure;
        }

        private int getTotalWorkflowCalls() {
            return updateCalls + submitCalls + approveCalls + rejectCalls
                    + teacherDetailCalls + coordinatorDetailCalls;
        }

        private int getUpdateCalls() { return updateCalls; }
        private int getRejectCalls() { return rejectCalls; }
        private int getTeacherDetailCalls() { return teacherDetailCalls; }
        private int getCoordinatorDetailCalls() { return coordinatorDetailCalls; }
        private int getLastUpdateUserId() { return lastUpdateUserId; }
        private int getLastUpdateExpectedVersion() { return lastUpdateExpectedVersion; }
        private Exam getLastUpdateExam() { return lastUpdateExam; }
        private int getPayloadUpdateCalls() { return payloadUpdateCalls; }
        private int getLastSubmitUserId() { return lastSubmitUserId; }
        private int getLastSubmitExamId() { return lastSubmitExamId; }
        private int getLastSubmitExpectedVersion() { return lastSubmitExpectedVersion; }
        private Exam getLastSubmitExam() { return lastSubmitExam; }
        private int getLastApproveUserId() { return lastApproveUserId; }
        private int getLastApproveExamId() { return lastApproveExamId; }
        private int getLastApproveExpectedVersion() { return lastApproveExpectedVersion; }
        private Exam getLastApproveExam() { return lastApproveExam; }
        private int getLastRejectUserId() { return lastRejectUserId; }
        private int getLastRejectExamId() { return lastRejectExamId; }
        private int getLastRejectExpectedVersion() { return lastRejectExpectedVersion; }
        private String getLastRejectReason() { return lastRejectReason; }
        private Exam getLastRejectExam() { return lastRejectExam; }
    }

    private enum Transition {
        SUBMIT {
            @Override
            void configureFalse(RecordingExamRepository repository) {
                repository.submitResult = false;
            }

            @Override
            void execute(ExamManagementService service, int userId) {
                service.submitExamForApproval(userId, new ExamVersionPayload(45, 4));
            }
        },
        APPROVE {
            @Override
            void configureFalse(RecordingExamRepository repository) {
                repository.approveResult = false;
            }

            @Override
            void execute(ExamManagementService service, int userId) {
                service.approveExam(userId, new ExamVersionPayload(45, 4));
            }
        },
        REJECT {
            @Override
            void configureFalse(RecordingExamRepository repository) {
                repository.rejectResult = false;
            }

            @Override
            void execute(ExamManagementService service, int userId) {
                service.rejectExam(userId, new RejectExamPayload(45, 4, "Reason"));
            }
        };

        abstract void configureFalse(RecordingExamRepository repository);

        abstract void execute(ExamManagementService service, int userId);
    }
}
