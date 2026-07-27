package hsts.server.control;

import hsts.common.ExamDTO;
import hsts.common.ExamQuestionSelectionPayload;
import hsts.common.ExamVersionPayload;
import hsts.common.QuestionDTO;
import hsts.common.RejectExamPayload;
import hsts.common.UpdateExamPayload;
import hsts.common.type.ExamStatus;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.User;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.ExamRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.repository.UserRepository;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
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
        assertEquals(List.of(userId, userId), questions.getRequestedUserIds());
        assertEquals(List.of(18, 17), questions.getRequestedQuestionIds());
        assertEquals(2, exams.getTeacherDetailCalls());

        UpdateExamPayload normalized = exams.getLastUpdatePayload();
        assertEquals(45, normalized.getExamId());
        assertEquals(4, normalized.getExpectedVersionNo());
        assertEquals("Updated title", normalized.getTitle());
        assertEquals(90, normalized.getDurationMinutes());
        assertEquals("", normalized.getTeacherNotes());
        assertEquals("Read carefully", normalized.getStudentInstructions());
        assertSame(selections.get(0), normalized.getQuestions().get(0));
        assertSame(selections.get(1), normalized.getQuestions().get(1));
        assertEquals(66.67, normalized.getQuestions().get(0).getScore(), 0.0);
        assertEquals(2, normalized.getQuestions().get(0).getOrderNumber());
        assertEquals(2, normalized.getQuestions().get(0).getQuestionVersionNo());
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
        private UpdateExamPayload lastUpdatePayload;
        private RuntimeException updateFailure;
        private RuntimeException submitFailure;
        private RuntimeException approveFailure;
        private boolean submitResult = true;
        private boolean approveResult = true;
        private boolean rejectResult = true;
        private int updateCalls;
        private int submitCalls;
        private int approveCalls;
        private int rejectCalls;
        private int teacherDetailCalls;
        private int coordinatorDetailCalls;
        private int lastUpdateUserId;
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
        public int updateWithNewVersion(int authenticatedUserId, UpdateExamPayload payload) {
            updateCalls++;
            lastUpdateUserId = authenticatedUserId;
            lastUpdatePayload = payload;
            if (updateFailure != null) {
                throw updateFailure;
            }
            return payload.getExpectedVersionNo() + 1;
        }

        @Override
        public boolean submitForApproval(int authenticatedUserId, int examId,
                                         int expectedVersionNo) {
            submitCalls++;
            lastSubmitUserId = authenticatedUserId;
            lastSubmitExamId = examId;
            lastSubmitExpectedVersion = expectedVersionNo;
            if (submitFailure != null) {
                throw submitFailure;
            }
            return submitResult;
        }

        @Override
        public boolean approve(int authenticatedCoordinatorId, int examId,
                               int expectedVersionNo) {
            approveCalls++;
            lastApproveUserId = authenticatedCoordinatorId;
            lastApproveExamId = examId;
            lastApproveExpectedVersion = expectedVersionNo;
            if (approveFailure != null) {
                throw approveFailure;
            }
            return approveResult;
        }

        @Override
        public boolean reject(int authenticatedCoordinatorId, int examId,
                              int expectedVersionNo, String reason) {
            rejectCalls++;
            lastRejectUserId = authenticatedCoordinatorId;
            lastRejectExamId = examId;
            lastRejectExpectedVersion = expectedVersionNo;
            lastRejectReason = reason;
            return rejectResult;
        }

        private void setTeacherExamBefore(ExamDTO teacherExamBefore) {
            this.teacherExamBefore = teacherExamBefore;
        }

        private void setTeacherExamAfter(ExamDTO teacherExamAfter) {
            this.teacherExamAfter = teacherExamAfter;
        }

        private void setCoordinatorExamAfter(ExamDTO coordinatorExamAfter) {
            this.coordinatorExamAfter = coordinatorExamAfter;
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
        private UpdateExamPayload getLastUpdatePayload() { return lastUpdatePayload; }
        private int getLastSubmitUserId() { return lastSubmitUserId; }
        private int getLastSubmitExamId() { return lastSubmitExamId; }
        private int getLastSubmitExpectedVersion() { return lastSubmitExpectedVersion; }
        private int getLastApproveUserId() { return lastApproveUserId; }
        private int getLastApproveExamId() { return lastApproveExamId; }
        private int getLastApproveExpectedVersion() { return lastApproveExpectedVersion; }
        private int getLastRejectUserId() { return lastRejectUserId; }
        private int getLastRejectExamId() { return lastRejectExamId; }
        private int getLastRejectExpectedVersion() { return lastRejectExpectedVersion; }
        private String getLastRejectReason() { return lastRejectReason; }
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
