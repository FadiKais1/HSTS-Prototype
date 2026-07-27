package hsts.server.control;

import hsts.common.CreateExamPayload;
import hsts.common.ExamDTO;
import hsts.common.ExamQuestionSelectionPayload;
import hsts.common.ExamSummaryDTO;
import hsts.common.QuestionDTO;
import hsts.common.type.ExamStatus;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.Question;
import hsts.server.entity.User;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.ExamRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.repository.UserRepository;
import hsts.server.support.InMemoryQuestionRepository;
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

public class ExamManagementExamTest {
    @Test
    public void legacyConstructorsRemainCompatibleAndMissingExamRepositoryFailsClearly() {
        InMemoryQuestionRepository questions = new InMemoryQuestionRepository(
                new Question(
                        1, "Content", "Topic", "MULTIPLE_CHOICE", "EASY", "ACTIVE", "",
                        "One", "Two", "Three", "Four", 1
                )
        );
        ExamManagementService legacyService = new ExamManagementService(questions);

        assertEquals(1, legacyService.getAllQuestions().size());
        IllegalStateException legacyFailure = assertThrows(
                IllegalStateException.class,
                () -> legacyService.getMyExams(101)
        );
        assertEquals("Exam repository is not configured", legacyFailure.getMessage());

        RecordingUserRepository users = new RecordingUserRepository(
                user(101, UserRole.TEACHER, UserStatus.ACTIVE)
        );
        ExamManagementService questionBankService = new ExamManagementService(
                new RecordingQuestionRepository(), new CourseRepository(), users
        );
        IllegalStateException questionBankFailure = assertThrows(
                IllegalStateException.class,
                () -> questionBankService.getMyExams(101)
        );
        assertEquals("Exam repository is not configured", questionBankFailure.getMessage());
        assertEquals(1, users.getFindCalls());
    }

    @Test
    public void teacherAndCoordinatorUseTeacherReadsAndReceiveRepositoryResultsUnchanged() {
        for (UserRole role : new UserRole[]{UserRole.TEACHER, UserRole.COORDINATOR}) {
            int userId = role == UserRole.TEACHER ? 201 : 202;
            RecordingExamRepository exams = new RecordingExamRepository();
            List<ExamSummaryDTO> summaries = List.of(summary(11, userId));
            ExamDTO detail = exam(11, userId);
            exams.setTeacherSummaries(summaries);
            exams.setTeacherExam(detail);
            ExamManagementService service = service(exams, new RecordingQuestionRepository(),
                    user(userId, role, UserStatus.ACTIVE));

            assertSame(summaries, service.getMyExams(userId));
            assertSame(detail, service.getExamForTeacher(userId, 11));
            assertEquals(userId, exams.getLastTeacherListUserId());
            assertEquals(userId, exams.getLastTeacherDetailUserId());
            assertEquals(11, exams.getLastTeacherDetailExamId());
        }
    }

    @Test
    public void coordinatorReviewReadsAllowSelfAuthoredPendingExam() {
        int coordinatorId = 301;
        RecordingExamRepository exams = new RecordingExamRepository();
        List<ExamSummaryDTO> pending = List.of(summary(21, coordinatorId));
        ExamDTO detail = exam(21, coordinatorId);
        exams.setPendingSummaries(pending);
        exams.setCoordinatorExam(detail);
        ExamManagementService service = service(exams, new RecordingQuestionRepository(),
                user(coordinatorId, UserRole.COORDINATOR, UserStatus.ACTIVE));

        assertSame(pending, service.getPendingExams(coordinatorId));
        assertSame(detail, service.getExamForCoordinator(coordinatorId, 21));
        assertEquals(coordinatorId, pending.get(0).getCreatedByUserId());
        assertEquals(coordinatorId, exams.getLastPendingUserId());
        assertEquals(coordinatorId, exams.getLastCoordinatorDetailUserId());
        assertEquals(21, exams.getLastCoordinatorDetailExamId());
    }

    @Test
    public void activeNonCoordinatorCannotReviewAndRepositoryIsNotCalled() {
        for (UserRole role : new UserRole[]{
                UserRole.TEACHER, UserRole.STUDENT, UserRole.PRINCIPAL
        }) {
            int userId = 400 + role.ordinal();
            RecordingExamRepository exams = new RecordingExamRepository();
            ExamManagementService service = service(exams, new RecordingQuestionRepository(),
                    user(userId, role, UserStatus.ACTIVE));

            IllegalStateException listFailure = assertThrows(
                    IllegalStateException.class,
                    () -> service.getPendingExams(userId)
            );
            IllegalStateException detailFailure = assertThrows(
                    IllegalStateException.class,
                    () -> service.getExamForCoordinator(userId, 1)
            );

            assertEquals("Only coordinators can review exams", listFailure.getMessage());
            assertEquals(listFailure.getMessage(), detailFailure.getMessage());
            assertEquals(0, exams.getTotalCalls());
        }
    }

    @Test
    public void studentPrincipalBlockedAndMissingUsersCannotUseTeacherOperations() {
        for (UserRole role : new UserRole[]{UserRole.STUDENT, UserRole.PRINCIPAL}) {
            int userId = 500 + role.ordinal();
            RecordingExamRepository exams = new RecordingExamRepository();
            ExamManagementService service = service(exams, new RecordingQuestionRepository(),
                    user(userId, role, UserStatus.ACTIVE));

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> service.getMyExams(userId)
            );
            assertEquals(
                    "Question management requires teacher or coordinator role",
                    failure.getMessage()
            );
            assertEquals(0, exams.getTotalCalls());
        }

        RecordingExamRepository blockedExams = new RecordingExamRepository();
        ExamManagementService blockedService = service(
                blockedExams,
                new RecordingQuestionRepository(),
                user(510, UserRole.COORDINATOR, UserStatus.BLOCKED)
        );
        IllegalStateException blocked = assertThrows(
                IllegalStateException.class,
                () -> blockedService.getPendingExams(510)
        );
        assertEquals("User account is blocked", blocked.getMessage());
        assertEquals(0, blockedExams.getTotalCalls());

        RecordingExamRepository missingExams = new RecordingExamRepository();
        ExamManagementService missingService = service(
                missingExams, new RecordingQuestionRepository()
        );
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> missingService.getMyExams(404)
        );
        assertEquals("User not found: 404", missing.getMessage());
        assertEquals(0, missingExams.getTotalCalls());
    }

    @Test
    public void missingOrInaccessibleExamUsesExactNotFoundMessage() {
        RecordingExamRepository exams = new RecordingExamRepository();
        ExamManagementService teacherService = service(
                exams,
                new RecordingQuestionRepository(),
                user(601, UserRole.TEACHER, UserStatus.ACTIVE)
        );
        IllegalArgumentException teacherFailure = assertThrows(
                IllegalArgumentException.class,
                () -> teacherService.getExamForTeacher(601, 71)
        );
        assertEquals("Exam not found: 71", teacherFailure.getMessage());

        ExamManagementService coordinatorService = service(
                exams,
                new RecordingQuestionRepository(),
                user(602, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );
        IllegalArgumentException coordinatorFailure = assertThrows(
                IllegalArgumentException.class,
                () -> coordinatorService.getExamForCoordinator(602, 72)
        );
        assertEquals("Exam not found: 72", coordinatorFailure.getMessage());
    }

    @Test
    public void createValidationUsesExactOrderAndMessagesBeforeQuestionReadsOrCreation() {
        RecordingExamRepository exams = new RecordingExamRepository();
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        ExamManagementService service = service(
                exams,
                questions,
                user(701, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        assertCreateFailure(service, null, "Exam creation data is missing");
        assertCreateFailure(service, payload(" ", 0, " ", List.of()),
                "Exam title is required");
        assertCreateFailure(service, payload("Title", 0, " ", List.of()),
                "Exam duration must be positive");
        assertCreateFailure(service, payload("Title", -1, " ", List.of()),
                "Exam duration must be positive");
        assertCreateFailure(service, payload("Title", 60, " ", List.of()),
                "Student instructions are required");
        assertCreateFailure(service, payload("Title", 60, "Instructions", List.of()),
                "At least one question is required");
        assertCreateFailure(service, payload("Title", 60, "Instructions", List.of(
                selection(1, 1, 2, 100)
        )), "Question order must start at 1 and be contiguous");
        assertCreateFailure(service, payload("Title", 60, "Instructions", List.of(
                selection(1, 1, 1, 50),
                selection(1, 1, 1, 50)
        )), "Question order must start at 1 and be contiguous");
        assertCreateFailure(service, payload("Title", 60, "Instructions", List.of(
                selection(1, 1, 1, 50),
                selection(1, 1, 2, 50)
        )), "Duplicate question: 1");
        assertCreateFailure(service, payload("Title", 60, "Instructions", List.of(
                selection(1, 1, 1, 99.99)
        )), "Exam total score must equal 100");

        assertEquals(0, questions.getFindCalls());
        assertEquals(0, exams.getCreateCalls());
    }

    @Test
    public void createRejectsNonFiniteZeroAndNegativeScores() {
        for (double invalidScore : new double[]{
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0, -1
        }) {
            RecordingExamRepository exams = new RecordingExamRepository();
            RecordingQuestionRepository questions = new RecordingQuestionRepository();
            ExamManagementService service = service(
                    exams,
                    questions,
                    user(701, UserRole.COORDINATOR, UserStatus.ACTIVE)
            );
            CreateExamPayload payload = payload("Title", 60, "Instructions", List.of(
                    selection(1, 1, 1, invalidScore)
            ));

            assertCreateFailure(
                    service,
                    payload,
                    "Question score must be positive and finite"
            );
            assertEquals(0, questions.getFindCalls());
            assertEquals(0, exams.getCreateCalls());
        }
    }

    @Test
    public void teacherAndCoordinatorCreateWithNormalizedTextAndPreservedSelections() {
        for (UserRole role : new UserRole[]{UserRole.TEACHER, UserRole.COORDINATOR}) {
            int userId = role == UserRole.TEACHER ? 801 : 802;
            RecordingExamRepository exams = new RecordingExamRepository();
            RecordingQuestionRepository questions = new RecordingQuestionRepository();
            ExamDTO created = exam(91, userId);
            exams.setCreatedExamId(91);
            exams.setTeacherExam(created);
            questions.addQuestion(question(12, 7, 3, "ACTIVE"));
            questions.addQuestion(question(11, 7, 2, "ACTIVE"));
            List<ExamQuestionSelectionPayload> selections = List.of(
                    selection(12, 3, 2, 66.67),
                    selection(11, 2, 1, 33.33)
            );
            ExamManagementService service = service(
                    exams,
                    questions,
                    user(userId, role, UserStatus.ACTIVE)
            );

            ExamDTO result = service.createExam(
                    userId,
                    new CreateExamPayload(
                            7, "  Midterm  ", 90, null, "  Read carefully  ", selections
                    )
            );

            assertSame(created, result);
            assertEquals(userId, exams.getLastCreateUserId());
            assertEquals(userId, exams.getLastTeacherDetailUserId());
            assertEquals(91, exams.getLastTeacherDetailExamId());
            assertEquals(List.of(12, 11), questions.getRequestedQuestionIds());
            assertEquals(List.of(userId, userId), questions.getRequestedUserIds());

            CreateExamPayload normalized = exams.getLastCreatePayload();
            assertEquals(7, normalized.getCourseId());
            assertEquals("Midterm", normalized.getTitle());
            assertEquals(90, normalized.getDurationMinutes());
            assertEquals("", normalized.getTeacherNotes());
            assertEquals("Read carefully", normalized.getStudentInstructions());
            assertEquals(2, normalized.getQuestions().size());
            assertSame(selections.get(0), normalized.getQuestions().get(0));
            assertSame(selections.get(1), normalized.getQuestions().get(1));
            assertEquals(66.67, normalized.getQuestions().get(0).getScore(), 0.0);
            assertEquals(3, normalized.getQuestions().get(0).getQuestionVersionNo());
            assertEquals(2, normalized.getQuestions().get(0).getOrderNumber());
        }
    }

    @Test
    public void createNormalizesNonblankTeacherNotes() {
        RecordingExamRepository exams = new RecordingExamRepository();
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        questions.addQuestion(question(1, 7, 1, "ACTIVE"));
        exams.setCreatedExamId(92);
        exams.setTeacherExam(exam(92, 803));
        ExamManagementService service = service(
                exams,
                questions,
                user(803, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        service.createExam(803, new CreateExamPayload(
                7, "Title", 60, "  Private note  ", "Instructions",
                List.of(selection(1, 1, 1, 100))
        ));

        assertEquals("Private note", exams.getLastCreatePayload().getTeacherNotes());
    }

    @Test
    public void inaccessibleCrossCourseInactiveAndStaleQuestionsPreventCreation() {
        assertUnavailableQuestion(null, 7, 1, 1);
        assertUnavailableQuestion(question(1, 8, 1, "ACTIVE"), 7, 1, 1);
        assertUnavailableQuestion(question(1, 7, 1, "INACTIVE"), 7, 1, 1);
        assertUnavailableQuestion(question(1, 7, 2, "ACTIVE"), 7, 1, 1);
    }

    @Test
    public void missingCreatedExamRereadUsesExactNotFoundMessage() {
        RecordingExamRepository exams = new RecordingExamRepository();
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        questions.addQuestion(question(1, 7, 1, "ACTIVE"));
        exams.setCreatedExamId(123);
        ExamManagementService service = service(
                exams,
                questions,
                user(901, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.createExam(901, validPayload())
        );

        assertEquals("Exam not found: 123", failure.getMessage());
        assertEquals(1, exams.getCreateCalls());
    }

    @Test
    public void repositoryFailuresPropagateUnchanged() {
        RuntimeException listFailure = new IllegalStateException("exam list failure");
        RecordingExamRepository listExams = new RecordingExamRepository();
        listExams.setListFailure(listFailure);
        ExamManagementService listService = service(
                listExams,
                new RecordingQuestionRepository(),
                user(1001, UserRole.TEACHER, UserStatus.ACTIVE)
        );
        assertSame(listFailure, assertThrows(
                RuntimeException.class,
                () -> listService.getMyExams(1001)
        ));

        RuntimeException questionFailure = new IllegalStateException("question read failure");
        RecordingExamRepository questionExams = new RecordingExamRepository();
        RecordingQuestionRepository failingQuestions = new RecordingQuestionRepository();
        failingQuestions.setFindFailure(questionFailure);
        ExamManagementService questionService = service(
                questionExams,
                failingQuestions,
                user(1002, UserRole.TEACHER, UserStatus.ACTIVE)
        );
        assertSame(questionFailure, assertThrows(
                RuntimeException.class,
                () -> questionService.createExam(1002, validPayload())
        ));
        assertEquals(0, questionExams.getCreateCalls());

        RuntimeException createFailure = new IllegalStateException("exam create failure");
        RecordingExamRepository createExams = new RecordingExamRepository();
        createExams.setCreateFailure(createFailure);
        RecordingQuestionRepository createQuestions = new RecordingQuestionRepository();
        createQuestions.addQuestion(question(1, 7, 1, "ACTIVE"));
        ExamManagementService createService = service(
                createExams,
                createQuestions,
                user(1003, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );
        assertSame(createFailure, assertThrows(
                RuntimeException.class,
                () -> createService.createExam(1003, validPayload())
        ));
    }

    private static void assertUnavailableQuestion(QuestionDTO availableQuestion,
                                                  int courseId,
                                                  int selectedVersion,
                                                  int questionId) {
        RecordingExamRepository exams = new RecordingExamRepository();
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        if (availableQuestion != null) {
            questions.addQuestion(availableQuestion);
        }
        ExamManagementService service = service(
                exams,
                questions,
                user(850, UserRole.TEACHER, UserStatus.ACTIVE)
        );
        CreateExamPayload payload = new CreateExamPayload(
                courseId, "Title", 60, "", "Instructions",
                List.of(selection(questionId, selectedVersion, 1, 100))
        );

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.createExam(850, payload)
        );

        assertEquals("Question unavailable for exam: " + questionId, failure.getMessage());
        assertEquals(0, exams.getCreateCalls());
        assertEquals(List.of(850), questions.getRequestedUserIds());
    }

    private static void assertCreateFailure(ExamManagementService service,
                                            CreateExamPayload payload,
                                            String expectedMessage) {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.createExam(701, payload)
        );
        assertEquals(expectedMessage, failure.getMessage());
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

    private static CreateExamPayload validPayload() {
        return new CreateExamPayload(
                7, "Title", 60, "", "Instructions",
                List.of(selection(1, 1, 1, 100))
        );
    }

    private static CreateExamPayload payload(String title, int duration,
                                             String instructions,
                                             List<ExamQuestionSelectionPayload> questions) {
        return new CreateExamPayload(7, title, duration, "", instructions, questions);
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

    private static ExamSummaryDTO summary(int examId, int creatorId) {
        return new ExamSummaryDTO(
                examId, "ABC123", 7, "Course", 3, "Subject", creatorId,
                "Creator", 1, "Exam", 60, 100, ExamStatus.DRAFT,
                LocalDateTime.of(2026, 7, 1, 10, 0), null, null, null
        );
    }

    private static ExamDTO exam(int examId, int creatorId) {
        return new ExamDTO(
                examId, "ABC123", 7, "Course", 3, "Subject", creatorId,
                "Creator", 1, "Exam", 60, "", "Instructions", 100,
                ExamStatus.DRAFT, LocalDateTime.of(2026, 7, 1, 10, 0),
                null, null, null, null, null, List.of()
        );
    }

    private static final class RecordingUserRepository extends UserRepository {
        private final Map<Integer, User> users = new LinkedHashMap<>();
        private int findCalls;

        private RecordingUserRepository(User... initialUsers) {
            for (User user : initialUsers) {
                users.put(user.getUserId(), user);
            }
        }

        @Override
        public Optional<User> findById(int userId) {
            findCalls++;
            return Optional.ofNullable(users.get(userId));
        }

        private int getFindCalls() {
            return findCalls;
        }
    }

    private static final class RecordingQuestionRepository extends QuestionRepository {
        private final Map<Integer, QuestionDTO> questions = new LinkedHashMap<>();
        private final List<Integer> requestedUserIds = new ArrayList<>();
        private final List<Integer> requestedQuestionIds = new ArrayList<>();
        private RuntimeException findFailure;

        @Override
        public Optional<QuestionDTO> findCurrentByIdForTeacher(int authenticatedUserId,
                                                                int questionId) {
            requestedUserIds.add(authenticatedUserId);
            requestedQuestionIds.add(questionId);
            if (findFailure != null) {
                throw findFailure;
            }
            return Optional.ofNullable(questions.get(questionId));
        }

        private void addQuestion(QuestionDTO question) {
            questions.put(question.getQuestionId(), question);
        }

        private void setFindFailure(RuntimeException findFailure) {
            this.findFailure = findFailure;
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
        private List<ExamSummaryDTO> teacherSummaries = List.of();
        private List<ExamSummaryDTO> pendingSummaries = List.of();
        private ExamDTO teacherExam;
        private ExamDTO coordinatorExam;
        private RuntimeException listFailure;
        private RuntimeException createFailure;
        private CreateExamPayload lastCreatePayload;
        private int createdExamId = 1;
        private int teacherListCalls;
        private int pendingCalls;
        private int teacherDetailCalls;
        private int coordinatorDetailCalls;
        private int createCalls;
        private int lastTeacherListUserId;
        private int lastPendingUserId;
        private int lastTeacherDetailUserId;
        private int lastTeacherDetailExamId;
        private int lastCoordinatorDetailUserId;
        private int lastCoordinatorDetailExamId;
        private int lastCreateUserId;

        @Override
        public List<ExamSummaryDTO> findCreatedByTeacher(int authenticatedUserId) {
            teacherListCalls++;
            lastTeacherListUserId = authenticatedUserId;
            if (listFailure != null) {
                throw listFailure;
            }
            return teacherSummaries;
        }

        @Override
        public List<ExamSummaryDTO> findPendingForCoordinator(int authenticatedUserId) {
            pendingCalls++;
            lastPendingUserId = authenticatedUserId;
            return pendingSummaries;
        }

        @Override
        public Optional<ExamDTO> findByIdForTeacher(int authenticatedUserId, int examId) {
            teacherDetailCalls++;
            lastTeacherDetailUserId = authenticatedUserId;
            lastTeacherDetailExamId = examId;
            return Optional.ofNullable(teacherExam);
        }

        @Override
        public Optional<ExamDTO> findByIdForCoordinator(int authenticatedUserId, int examId) {
            coordinatorDetailCalls++;
            lastCoordinatorDetailUserId = authenticatedUserId;
            lastCoordinatorDetailExamId = examId;
            return Optional.ofNullable(coordinatorExam);
        }

        @Override
        public int create(int authenticatedUserId, CreateExamPayload payload) {
            createCalls++;
            lastCreateUserId = authenticatedUserId;
            lastCreatePayload = payload;
            if (createFailure != null) {
                throw createFailure;
            }
            return createdExamId;
        }

        private void setTeacherSummaries(List<ExamSummaryDTO> teacherSummaries) {
            this.teacherSummaries = teacherSummaries;
        }

        private void setPendingSummaries(List<ExamSummaryDTO> pendingSummaries) {
            this.pendingSummaries = pendingSummaries;
        }

        private void setTeacherExam(ExamDTO teacherExam) {
            this.teacherExam = teacherExam;
        }

        private void setCoordinatorExam(ExamDTO coordinatorExam) {
            this.coordinatorExam = coordinatorExam;
        }

        private void setCreatedExamId(int createdExamId) {
            this.createdExamId = createdExamId;
        }

        private void setListFailure(RuntimeException listFailure) {
            this.listFailure = listFailure;
        }

        private void setCreateFailure(RuntimeException createFailure) {
            this.createFailure = createFailure;
        }

        private int getTotalCalls() {
            return teacherListCalls + pendingCalls + teacherDetailCalls
                    + coordinatorDetailCalls + createCalls;
        }

        private int getCreateCalls() {
            return createCalls;
        }

        private int getLastTeacherListUserId() {
            return lastTeacherListUserId;
        }

        private int getLastPendingUserId() {
            return lastPendingUserId;
        }

        private int getLastTeacherDetailUserId() {
            return lastTeacherDetailUserId;
        }

        private int getLastTeacherDetailExamId() {
            return lastTeacherDetailExamId;
        }

        private int getLastCoordinatorDetailUserId() {
            return lastCoordinatorDetailUserId;
        }

        private int getLastCoordinatorDetailExamId() {
            return lastCoordinatorDetailExamId;
        }

        private int getLastCreateUserId() {
            return lastCreateUserId;
        }

        private CreateExamPayload getLastCreatePayload() {
            return lastCreatePayload;
        }
    }
}
