package hsts.server.control;

import hsts.common.CreateExamPayload;
import hsts.common.ExamDTO;
import hsts.common.ExamQuestionSelectionPayload;
import hsts.common.GenerateExamPayload;
import hsts.common.QuestionDTO;
import hsts.common.QuestionFilterPayload;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.ExamStatus;
import hsts.common.type.QuestionStatus;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.User;
import hsts.server.repository.ExamRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.repository.UserRepository;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamManagementAutomaticGenerationTest {
    @Test
    public void teacherAndCoordinatorAreAuthorizedWhileOtherRolesAreRejected() {
        for (UserRole role : new UserRole[]{UserRole.TEACHER, UserRole.COORDINATOR}) {
            int userId = 100 + role.ordinal();
            RecordingQuestionRepository questions = new RecordingQuestionRepository(
                    List.of(question(1, 4, 2))
            );
            RecordingService service = service(questions, resultExam(),
                    user(userId, role, UserStatus.ACTIVE));

            assertSame(resultExam(), service.generateAutomaticExam(
                    userId,
                    validPayload(1)
            ));
        }

        for (UserRole role : new UserRole[]{UserRole.STUDENT, UserRole.PRINCIPAL}) {
            int userId = 200 + role.ordinal();
            RecordingQuestionRepository questions = new RecordingQuestionRepository(List.of());
            RecordingService service = service(questions, resultExam(),
                    user(userId, role, UserStatus.ACTIVE));

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> service.generateAutomaticExam(userId, validPayload(1))
            );
            assertEquals(
                    "Question management requires teacher or coordinator role",
                    failure.getMessage()
            );
            assertEquals(0, questions.getFindCalls());
        }

        RecordingService blockedService = service(
                new RecordingQuestionRepository(List.of()),
                resultExam(),
                user(301, UserRole.TEACHER, UserStatus.BLOCKED)
        );
        IllegalStateException blocked = assertThrows(
                IllegalStateException.class,
                () -> blockedService.generateAutomaticExam(301, validPayload(1))
        );
        assertEquals("User account is blocked", blocked.getMessage());

        RecordingService missingService = service(
                new RecordingQuestionRepository(List.of()),
                resultExam()
        );
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> missingService.generateAutomaticExam(404, validPayload(1))
        );
        assertEquals("User not found: 404", missing.getMessage());
    }

    @Test
    public void validationUsesExactRequiredOrderAndMessages() {
        RecordingQuestionRepository questions = new RecordingQuestionRepository(List.of());
        RecordingService service = service(
                questions,
                resultExam(),
                user(401, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        assertFailure(service, null, "Automatic exam data is missing");
        assertFailure(service, payload(" ", 0, " ", " ", null, 0),
                "Exam title is required");
        assertFailure(service, payload("Title", 0, " ", " ", null, 0),
                "Exam duration must be positive");
        assertFailure(service, payload("Title", 60, " ", " ", null, 0),
                "Student instructions are required");
        assertFailure(service, payload("Title", 60, "Instructions", " ", null, 0),
                "Topic is required");
        assertFailure(service, payload("Title", 60, "Instructions", "Topic", null, 0),
                "Difficulty is required");
        assertFailure(service, payload(
                "Title", 60, "Instructions", "Topic", DifficultyLevel.MEDIUM, 0
        ), "Question count must be positive");

        assertEquals(0, questions.getFindCalls());
        assertEquals(0, service.getCreateCalls());
    }

    @Test
    public void repositoryConfigurationIsCheckedAfterAuthorizationBeforePayloadValidation() {
        RecordingUserRepository users = new RecordingUserRepository(
                user(451, UserRole.TEACHER, UserStatus.ACTIVE)
        );
        ExamManagementService service = new ExamManagementService(
                null,
                null,
                users,
                new ExamRepository()
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.generateAutomaticExam(451, null)
        );
        assertEquals("Automatic-exam repositories are not configured", failure.getMessage());

        ExamManagementService unauthorized = new ExamManagementService(
                null,
                null,
                new RecordingUserRepository(
                        user(452, UserRole.STUDENT, UserStatus.ACTIVE)
                ),
                new ExamRepository()
        );
        IllegalStateException authorization = assertThrows(
                IllegalStateException.class,
                () -> unauthorized.generateAutomaticExam(452, null)
        );
        assertEquals(
                "Question management requires teacher or coordinator role",
                authorization.getMessage()
        );
    }

    @Test
    public void insufficientQuestionsPreventsExamCreation() {
        RecordingQuestionRepository questions = new RecordingQuestionRepository(
                List.of(question(11, 7, 3), question(12, 7, 4))
        );
        RecordingService service = service(
                questions,
                resultExam(),
                user(501, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateAutomaticExam(501, validPayload(3))
        );

        assertEquals("Not enough matching questions", failure.getMessage());
        assertEquals(0, service.getCreateCalls());
    }

    @Test
    public void duplicateRepositoryRowsDoNotCountAsDistinctQuestions() {
        QuestionDTO duplicate = question(15, 7, 3);
        RecordingQuestionRepository questions = new RecordingQuestionRepository(
                List.of(duplicate, duplicate, question(16, 7, 4))
        );
        RecordingService service = service(
                questions,
                resultExam(),
                user(551, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateAutomaticExam(551, validPayload(3))
        );

        assertEquals("Not enough matching questions", failure.getMessage());
        assertEquals(0, service.getCreateCalls());
    }

    @Test
    public void searchSelectionScoringAndNormalCreationUseExactContracts() {
        List<QuestionDTO> repositoryResult = List.of(
                question(21, 7, 2),
                question(22, 7, 4),
                question(23, 7, 6),
                question(24, 7, 8),
                question(25, 7, 10)
        );
        RecordingQuestionRepository questions = new RecordingQuestionRepository(repositoryResult);
        ExamDTO expectedResult = resultExam();
        RecordingService service = service(
                questions,
                expectedResult,
                user(601, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );
        GenerateExamPayload payload = new GenerateExamPayload(
                7,
                "  Generated midterm  ",
                90,
                "  Keep private  ",
                "  Read every question  ",
                "  Calculus  ",
                DifficultyLevel.HARD,
                3
        );

        ExamDTO result = service.generateAutomaticExam(601, payload);

        assertSame(expectedResult, result);
        assertEquals(1, questions.getFindCalls());
        assertEquals(601, questions.getLastUserId());
        QuestionFilterPayload filter = questions.getLastFilter();
        assertEquals(Integer.valueOf(7), filter.getCourseId());
        assertNull(filter.getSubjectId());
        assertEquals("Calculus", filter.getTopic());
        assertEquals(DifficultyLevel.HARD, filter.getDifficulty());
        assertEquals(QuestionStatus.ACTIVE, filter.getStatus());

        assertEquals(1, service.getCreateCalls());
        assertEquals(601, service.getLastCreateUserId());
        CreateExamPayload createPayload = service.getLastCreatePayload();
        assertEquals(7, createPayload.getCourseId());
        assertEquals("Generated midterm", createPayload.getTitle());
        assertEquals(90, createPayload.getDurationMinutes());
        assertEquals("Keep private", createPayload.getTeacherNotes());
        assertEquals("Read every question", createPayload.getStudentInstructions());

        List<ExamQuestionSelectionPayload> selections = createPayload.getQuestions();
        assertEquals(3, selections.size());
        Set<Integer> selectedIds = new HashSet<>();
        Map<Integer, Integer> expectedVersions = new HashMap<>();
        for (QuestionDTO question : repositoryResult) {
            expectedVersions.put(question.getQuestionId(), question.getVersionNo());
        }

        BigDecimal scoreTotal = BigDecimal.ZERO;
        for (int index = 0; index < selections.size(); index++) {
            ExamQuestionSelectionPayload selection = selections.get(index);
            assertTrue(selectedIds.add(selection.getQuestionId()));
            assertEquals(
                    expectedVersions.get(selection.getQuestionId()).intValue(),
                    selection.getQuestionVersionNo()
            );
            assertEquals(index + 1, selection.getOrderNumber());
            assertTrue(selection.getScore() > 0);
            scoreTotal = scoreTotal.add(BigDecimal.valueOf(selection.getScore()));
        }
        assertEquals(new BigDecimal("33.33"),
                BigDecimal.valueOf(selections.get(0).getScore()));
        assertEquals(new BigDecimal("33.33"),
                BigDecimal.valueOf(selections.get(1).getScore()));
        assertEquals(new BigDecimal("33.34"),
                BigDecimal.valueOf(selections.get(2).getScore()));
        assertEquals(new BigDecimal("100.00"), scoreTotal);
        assertEquals(5, repositoryResult.size());
    }

    @Test
    public void blankTeacherNotesNormalizeToEmptyAndFourScoresAreEven() {
        RecordingQuestionRepository questions = new RecordingQuestionRepository(List.of(
                question(31, 7, 1),
                question(32, 7, 2),
                question(33, 7, 3),
                question(34, 7, 4)
        ));
        RecordingService service = service(
                questions,
                resultExam(),
                user(701, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        service.generateAutomaticExam(
                701,
                new GenerateExamPayload(
                        7, "Exam", 60, " ", "Instructions", "Topic",
                        DifficultyLevel.MEDIUM, 4
                )
        );

        assertEquals("", service.getLastCreatePayload().getTeacherNotes());
        for (ExamQuestionSelectionPayload selection
                : service.getLastCreatePayload().getQuestions()) {
            assertEquals(new BigDecimal("25.0"), BigDecimal.valueOf(selection.getScore()));
        }
    }

    private static void assertFailure(RecordingService service,
                                      GenerateExamPayload payload,
                                      String expectedMessage) {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateAutomaticExam(401, payload)
        );
        assertEquals(expectedMessage, failure.getMessage());
    }

    private static RecordingService service(RecordingQuestionRepository questions,
                                            ExamDTO result,
                                            User... users) {
        return new RecordingService(
                questions,
                new RecordingUserRepository(users),
                result
        );
    }

    private static GenerateExamPayload validPayload(int count) {
        return payload(
                "Exam", 60, "Instructions", "Topic", DifficultyLevel.MEDIUM, count
        );
    }

    private static GenerateExamPayload payload(String title, int duration,
                                               String instructions, String topic,
                                               DifficultyLevel difficulty, int count) {
        return new GenerateExamPayload(
                4, title, duration, "", instructions, topic, difficulty, count
        );
    }

    private static QuestionDTO question(int questionId, int courseId, int versionNo) {
        return new QuestionDTO(
                questionId, "Question", "Topic", "MULTIPLE_CHOICE", "MEDIUM", "ACTIVE",
                "", "One", "Two", "Three", "Four", 1,
                courseId, 2, versionNo
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

    private static ExamDTO resultExam() {
        return ResultHolder.RESULT;
    }

    private static final class ResultHolder {
        private static final ExamDTO RESULT = new ExamDTO(
                81, "AUTO81", 7, "Course", 2, "Subject", 601,
                "Creator", 1, "Generated", 90, "", "Instructions", 100,
                ExamStatus.DRAFT, LocalDateTime.of(2026, 7, 27, 12, 0),
                null, null, null, null, null, List.of()
        );
    }

    private static final class RecordingUserRepository extends UserRepository {
        private final Map<Integer, User> users = new LinkedHashMap<>();

        private RecordingUserRepository(User... users) {
            for (User user : users) {
                this.users.put(user.getUserId(), user);
            }
        }

        @Override
        public Optional<User> findById(int userId) {
            return Optional.ofNullable(users.get(userId));
        }
    }

    private static final class RecordingQuestionRepository extends QuestionRepository {
        private final List<QuestionDTO> questions;
        private int findCalls;
        private int lastUserId;
        private QuestionFilterPayload lastFilter;

        private RecordingQuestionRepository(List<QuestionDTO> questions) {
            this.questions = questions;
        }

        @Override
        public List<QuestionDTO> findCurrentForTeacher(int authenticatedUserId,
                                                       QuestionFilterPayload filter) {
            findCalls++;
            lastUserId = authenticatedUserId;
            lastFilter = filter;
            return questions;
        }

        private int getFindCalls() { return findCalls; }
        private int getLastUserId() { return lastUserId; }
        private QuestionFilterPayload getLastFilter() { return lastFilter; }
    }

    private static final class RecordingService extends ExamManagementService {
        private final ExamDTO result;
        private int createCalls;
        private int lastCreateUserId;
        private CreateExamPayload lastCreatePayload;

        private RecordingService(QuestionRepository questions,
                                 UserRepository users,
                                 ExamDTO result) {
            super(questions, null, users, new ExamRepository());
            this.result = result;
        }

        @Override
        public ExamDTO createExam(int authenticatedUserId, CreateExamPayload payload) {
            createCalls++;
            lastCreateUserId = authenticatedUserId;
            lastCreatePayload = payload;
            return result;
        }

        private int getCreateCalls() { return createCalls; }
        private int getLastCreateUserId() { return lastCreateUserId; }
        private CreateExamPayload getLastCreatePayload() { return lastCreatePayload; }
    }
}
