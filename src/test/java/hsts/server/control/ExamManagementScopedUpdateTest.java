package hsts.server.control;

import hsts.common.QuestionDTO;
import hsts.common.UpdateQuestionPayload;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.Question;
import hsts.server.entity.User;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.repository.UserRepository;
import hsts.server.support.InMemoryQuestionRepository;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ExamManagementScopedUpdateTest {
    @Test
    public void activeTeacherAndCoordinatorCanRetrieveAssignedNormalizedQuestion() {
        for (UserRole role : new UserRole[]{UserRole.TEACHER, UserRole.COORDINATOR}) {
            int userId = role == UserRole.TEACHER ? 101 : 102;
            RecordingQuestionRepository questions = new RecordingQuestionRepository();
            QuestionDTO expected = questionDto(17, 4);
            questions.setAccessibleQuestion(expected);
            ExamManagementService service = service(
                    questions,
                    user(userId, role, UserStatus.ACTIVE)
            );

            QuestionDTO result = service.getQuestionById(userId, 17);

            assertSame(expected, result);
            assertEquals(userId, questions.getLastFindUserId());
            assertEquals(17, questions.getLastFindQuestionId());
        }
    }

    @Test
    public void studentAndPrincipalAreRejected() {
        for (UserRole role : new UserRole[]{UserRole.STUDENT, UserRole.PRINCIPAL}) {
            int userId = role == UserRole.STUDENT ? 201 : 202;
            RecordingQuestionRepository questions = new RecordingQuestionRepository();
            questions.setAccessibleQuestion(questionDto(17, 4));
            ExamManagementService service = service(
                    questions,
                    user(userId, role, UserStatus.ACTIVE)
            );

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> service.getQuestionById(userId, 17)
            );

            assertEquals(
                    "Question management requires teacher or coordinator role",
                    exception.getMessage()
            );
            assertEquals(0, questions.getFindCalls());
        }
    }

    @Test
    public void blockedAndMissingUsersAreRejected() {
        RecordingQuestionRepository blockedQuestions = new RecordingQuestionRepository();
        ExamManagementService blockedService = service(
                blockedQuestions,
                user(301, UserRole.TEACHER, UserStatus.BLOCKED)
        );
        IllegalStateException blocked = assertThrows(
                IllegalStateException.class,
                () -> blockedService.getQuestionById(301, 17)
        );
        assertEquals("User account is blocked", blocked.getMessage());
        assertEquals(0, blockedQuestions.getFindCalls());

        RecordingQuestionRepository missingQuestions = new RecordingQuestionRepository();
        ExamManagementService missingService = service(missingQuestions);
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> missingService.getQuestionById(404, 17)
        );
        assertEquals("User not found: 404", missing.getMessage());
        assertEquals(0, missingQuestions.getFindCalls());
    }

    @Test
    public void missingOrInaccessibleQuestionUsesSameNotFoundMessage() {
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        ExamManagementService service = service(
                questions,
                user(401, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.getQuestionById(401, 88)
        );

        assertEquals("Question not found: 88", exception.getMessage());
    }

    @Test
    public void updateNormalizesPreservesExpectedVersionAndRereadsScopedQuestion() {
        int userId = 501;
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        questions.setAccessibleQuestion(questionDto(27, 7));
        QuestionDTO reread = new QuestionDTO(
                27, "Updated content", "General", "MULTIPLE_CHOICE", "EASY", "ACTIVE",
                "", "One", "Two", "Three", "Four", 3, 5, 2, 8
        );
        questions.setUpdatedQuestion(reread);
        ExamManagementService service = service(
                questions,
                user(userId, UserRole.TEACHER, UserStatus.ACTIVE)
        );
        UpdateQuestionPayload payload = new UpdateQuestionPayload(
                27,
                "  Updated content  ",
                "  ",
                null,
                " ",
                null,
                " One ",
                "  Two",
                "Three  ",
                " Four ",
                3,
                7
        );

        QuestionDTO result = service.updateQuestion(userId, payload);

        assertSame(reread, result);
        assertEquals(2, questions.getFindCalls());
        assertEquals(userId, questions.getLastFindUserId());
        assertEquals(27, questions.getLastFindQuestionId());
        assertEquals(1, questions.getUpdateCalls());
        assertEquals(userId, questions.getLastUpdatedByUserId());

        UpdateQuestionPayload normalized = questions.getLastUpdatePayload();
        assertEquals(27, normalized.getQuestionId());
        assertEquals("Updated content", normalized.getContent());
        assertEquals("General", normalized.getTopic());
        assertEquals("EASY", normalized.getDifficulty());
        assertEquals("ACTIVE", normalized.getStatus());
        assertEquals("", normalized.getIllustrationPath());
        assertEquals("One", normalized.getAnswerOption1());
        assertEquals("Two", normalized.getAnswerOption2());
        assertEquals("Three", normalized.getAnswerOption3());
        assertEquals("Four", normalized.getAnswerOption4());
        assertEquals(3, normalized.getCorrectOptionNumber());
        assertEquals(7, normalized.getExpectedVersionNo());
    }

    @Test
    public void optimisticVersionConflictPropagatesUnchanged() {
        IllegalStateException conflict = new IllegalStateException("Question version conflict");
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        questions.setAccessibleQuestion(questionDto(27, 7));
        questions.setUpdateFailure(conflict);
        ExamManagementService service = service(
                questions,
                user(601, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );

        IllegalStateException result = assertThrows(
                IllegalStateException.class,
                () -> service.updateQuestion(601, validPayload(27, 7))
        );

        assertSame(conflict, result);
        assertEquals(1, questions.getFindCalls());
        assertEquals(1, questions.getUpdateCalls());
    }

    @Test
    public void expectedVersionZeroIsPassedThroughForCompatibility() {
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        questions.setAccessibleQuestion(questionDto(27, 7));
        questions.setUpdatedQuestion(questionDto(27, 8));
        ExamManagementService service = service(
                questions,
                user(650, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        service.updateQuestion(650, validPayload(27, 0));

        assertEquals(0, questions.getLastUpdatePayload().getExpectedVersionNo());
    }

    @Test
    public void inaccessibleUpdateUsesNotFoundAndPerformsNoVersionWrite() {
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        ExamManagementService service = service(
                questions,
                user(675, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateQuestion(675, validPayload(91, 3))
        );

        assertEquals("Question not found: 91", exception.getMessage());
        assertEquals(1, questions.getFindCalls());
        assertEquals(0, questions.getUpdateCalls());
    }

    @Test
    public void validationFailurePerformsNoScopedReadOrVersionedUpdate() {
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        questions.setAccessibleQuestion(questionDto(27, 7));
        ExamManagementService service = service(
                questions,
                user(701, UserRole.TEACHER, UserStatus.ACTIVE)
        );
        UpdateQuestionPayload invalid = new UpdateQuestionPayload(
                27, " ", "Topic", "HARD", "ACTIVE", "",
                "One", "Two", "Three", "Four", 2, 7
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateQuestion(701, invalid)
        );

        assertEquals("Question content cannot be empty", exception.getMessage());
        assertEquals(0, questions.getFindCalls());
        assertEquals(0, questions.getUpdateCalls());
    }

    @Test
    public void contextFreeGetAndUpdateRetainLegacyRepositoryBehavior() {
        InMemoryQuestionRepository questions = new InMemoryQuestionRepository(
                new Question(
                        9, "Old", "Topic", "MULTIPLE_CHOICE", "EASY", "ACTIVE", "",
                        "One", "Two", "Three", "Four", 1
                )
        );
        ExamManagementService service = new ExamManagementService(questions);

        QuestionDTO before = service.getQuestionById(9);
        QuestionDTO after = service.updateQuestion(validPayload(9, 0));

        assertEquals("Old", before.getContent());
        assertEquals("Content", after.getContent());
        assertEquals(1, questions.getUpdateCalls());
        assertEquals(2, questions.getFindByIdCalls());
    }

    private static ExamManagementService service(RecordingQuestionRepository questions,
                                                 User... users) {
        return new ExamManagementService(
                questions,
                new CourseRepository(),
                new FakeUserRepository(users)
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

    private static UpdateQuestionPayload validPayload(int questionId, int expectedVersionNo) {
        return new UpdateQuestionPayload(
                questionId, "Content", "Topic", "MEDIUM", "ACTIVE", "",
                "One", "Two", "Three", "Four", 2, expectedVersionNo
        );
    }

    private static QuestionDTO questionDto(int questionId, int versionNo) {
        return new QuestionDTO(
                questionId, "Content", "Topic", "MULTIPLE_CHOICE", "HARD", "ACTIVE",
                "", "One", "Two", "Three", "Four", 2, 5, 2, versionNo
        );
    }

    private static final class FakeUserRepository extends UserRepository {
        private final Map<Integer, User> users = new LinkedHashMap<>();

        private FakeUserRepository(User... initialUsers) {
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
        private QuestionDTO accessibleQuestion;
        private QuestionDTO updatedQuestion;
        private RuntimeException updateFailure;
        private int findCalls;
        private int updateCalls;
        private int lastFindUserId;
        private int lastFindQuestionId;
        private int lastUpdatedByUserId;
        private UpdateQuestionPayload lastUpdatePayload;

        @Override
        public Optional<QuestionDTO> findCurrentByIdForTeacher(int authenticatedUserId,
                                                                int questionId) {
            findCalls++;
            lastFindUserId = authenticatedUserId;
            lastFindQuestionId = questionId;
            QuestionDTO result = updateCalls > 0 && updatedQuestion != null
                    ? updatedQuestion
                    : accessibleQuestion;
            if (result == null || result.getQuestionId() != questionId) {
                return Optional.empty();
            }
            return Optional.of(result);
        }

        @Override
        public int updateWithNewVersion(int updatedByUserId, UpdateQuestionPayload payload) {
            updateCalls++;
            lastUpdatedByUserId = updatedByUserId;
            lastUpdatePayload = payload;
            if (updateFailure != null) {
                throw updateFailure;
            }
            return payload.getExpectedVersionNo() > 0
                    ? payload.getExpectedVersionNo() + 1
                    : 1;
        }

        private void setAccessibleQuestion(QuestionDTO accessibleQuestion) {
            this.accessibleQuestion = accessibleQuestion;
        }

        private void setUpdatedQuestion(QuestionDTO updatedQuestion) {
            this.updatedQuestion = updatedQuestion;
        }

        private void setUpdateFailure(RuntimeException updateFailure) {
            this.updateFailure = updateFailure;
        }

        private int getFindCalls() {
            return findCalls;
        }

        private int getUpdateCalls() {
            return updateCalls;
        }

        private int getLastFindUserId() {
            return lastFindUserId;
        }

        private int getLastFindQuestionId() {
            return lastFindQuestionId;
        }

        private int getLastUpdatedByUserId() {
            return lastUpdatedByUserId;
        }

        private UpdateQuestionPayload getLastUpdatePayload() {
            return lastUpdatePayload;
        }
    }
}
