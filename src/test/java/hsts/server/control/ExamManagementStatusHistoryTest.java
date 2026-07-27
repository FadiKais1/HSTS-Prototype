package hsts.server.control;

import hsts.common.QuestionDTO;
import hsts.common.QuestionVersionDTO;
import hsts.common.UpdateQuestionPayload;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.User;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.repository.UserRepository;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ExamManagementStatusHistoryTest {
    @Test
    public void teacherActivatesAssignedQuestionAndRereadsCurrentDto() {
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        QuestionDTO expected = questionDto(31, "ACTIVE");
        questions.setCurrentQuestion(expected);
        ExamManagementService service = service(
                questions,
                user(101, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        QuestionDTO result = service.activateQuestion(101, 31);

        assertSame(expected, result);
        assertStatusOperation(questions, 101, 31, QuestionStatus.ACTIVE);
        assertEquals(1, questions.getFindCalls());
        assertEquals(0, questions.getVersionUpdateCalls());
    }

    @Test
    public void coordinatorDeactivatesAssignedQuestionAndAlreadyMatchingStatusSucceeds() {
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        QuestionDTO expected = questionDto(32, "INACTIVE");
        questions.setCurrentQuestion(expected);
        ExamManagementService service = service(
                questions,
                user(102, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );

        QuestionDTO result = service.deactivateQuestion(102, 32);

        assertSame(expected, result);
        assertStatusOperation(questions, 102, 32, QuestionStatus.INACTIVE);
        assertEquals(0, questions.getVersionUpdateCalls());
    }

    @Test
    public void missingOrUnassignedStatusTargetUsesExactNotFoundMessage() {
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        questions.setStatusUpdated(false);
        ExamManagementService service = service(
                questions,
                user(103, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.activateQuestion(103, 77)
        );

        assertEquals("Question not found: 77", exception.getMessage());
        assertStatusOperation(questions, 103, 77, QuestionStatus.ACTIVE);
        assertEquals(0, questions.getFindCalls());
        assertEquals(0, questions.getVersionUpdateCalls());
    }

    @Test
    public void teacherReceivesHistoryUnchangedInRepositoryOrder() {
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        List<QuestionVersionDTO> expected = List.of(
                version(44, 3),
                version(44, 2),
                version(44, 1)
        );
        questions.setHistory(expected);
        ExamManagementService service = service(
                questions,
                user(104, UserRole.TEACHER, UserStatus.ACTIVE)
        );

        List<QuestionVersionDTO> result = service.getQuestionHistory(104, 44);

        assertSame(expected, result);
        assertEquals(3, result.get(0).getVersionNo());
        assertEquals(2, result.get(1).getVersionNo());
        assertEquals(1, result.get(2).getVersionNo());
        assertEquals(104, questions.getLastHistoryUserId());
        assertEquals(44, questions.getLastHistoryQuestionId());
        assertEquals(1, questions.getHistoryCalls());
    }

    @Test
    public void emptyHistoryUsesExactNotFoundMessage() {
        RecordingQuestionRepository questions = new RecordingQuestionRepository();
        ExamManagementService service = service(
                questions,
                user(105, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.getQuestionHistory(105, 88)
        );

        assertEquals("Question not found: 88", exception.getMessage());
        assertEquals(1, questions.getHistoryCalls());
    }

    @Test
    public void unauthorizedRolesPerformNoStatusOrHistoryOperations() {
        for (UserRole role : new UserRole[]{UserRole.STUDENT, UserRole.PRINCIPAL}) {
            int userId = role == UserRole.STUDENT ? 201 : 202;
            RecordingQuestionRepository questions = new RecordingQuestionRepository();
            ExamManagementService service = service(
                    questions,
                    user(userId, role, UserStatus.ACTIVE)
            );

            IllegalStateException statusException = assertThrows(
                    IllegalStateException.class,
                    () -> service.activateQuestion(userId, 1)
            );
            IllegalStateException historyException = assertThrows(
                    IllegalStateException.class,
                    () -> service.getQuestionHistory(userId, 1)
            );

            assertEquals(
                    "Question management requires teacher or coordinator role",
                    statusException.getMessage()
            );
            assertEquals(statusException.getMessage(), historyException.getMessage());
            assertNoStatusOrHistoryCalls(questions);
        }
    }

    @Test
    public void blockedAndMissingUsersPerformNoStatusOrHistoryOperations() {
        RecordingQuestionRepository blockedQuestions = new RecordingQuestionRepository();
        ExamManagementService blockedService = service(
                blockedQuestions,
                user(301, UserRole.TEACHER, UserStatus.BLOCKED)
        );
        IllegalStateException blocked = assertThrows(
                IllegalStateException.class,
                () -> blockedService.deactivateQuestion(301, 1)
        );
        assertEquals("User account is blocked", blocked.getMessage());
        assertNoStatusOrHistoryCalls(blockedQuestions);

        RecordingQuestionRepository missingQuestions = new RecordingQuestionRepository();
        ExamManagementService missingService = service(missingQuestions);
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> missingService.getQuestionHistory(404, 1)
        );
        assertEquals("User not found: 404", missing.getMessage());
        assertNoStatusOrHistoryCalls(missingQuestions);
    }

    @Test
    public void oneArgumentUmlStatusMethodsRemainUnsupported() {
        ExamManagementService service = new ExamManagementService(
                new RecordingQuestionRepository()
        );

        UnsupportedOperationException activate = assertThrows(
                UnsupportedOperationException.class,
                () -> service.activateQuestion(1)
        );
        UnsupportedOperationException deactivate = assertThrows(
                UnsupportedOperationException.class,
                () -> service.deactivateQuestion(1)
        );

        assertEquals("Not implemented in Assignment 2 skeleton", activate.getMessage());
        assertEquals(activate.getMessage(), deactivate.getMessage());
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

    private static QuestionDTO questionDto(int questionId, String status) {
        return new QuestionDTO(
                questionId, "Content", "Topic", "MULTIPLE_CHOICE", "MEDIUM", status,
                "", "One", "Two", "Three", "Four", 2, 7, 3, 2
        );
    }

    private static QuestionVersionDTO version(int questionId, int versionNo) {
        return new QuestionVersionDTO(
                questionId, versionNo, 7, "Content " + versionNo, "Topic",
                QuestionType.MULTIPLE_CHOICE, DifficultyLevel.MEDIUM, "",
                "One", "Two", "Three", "Four", 2, 101,
                LocalDateTime.of(2026, 1, versionNo, 10, 0)
        );
    }

    private static void assertStatusOperation(RecordingQuestionRepository questions,
                                              int userId, int questionId,
                                              QuestionStatus status) {
        assertEquals(1, questions.getStatusCalls());
        assertEquals(userId, questions.getLastStatusUserId());
        assertEquals(questionId, questions.getLastStatusQuestionId());
        assertEquals(status, questions.getLastStatus());
    }

    private static void assertNoStatusOrHistoryCalls(RecordingQuestionRepository questions) {
        assertEquals(0, questions.getStatusCalls());
        assertEquals(0, questions.getHistoryCalls());
        assertEquals(0, questions.getFindCalls());
        assertEquals(0, questions.getVersionUpdateCalls());
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
        private boolean statusUpdated = true;
        private QuestionDTO currentQuestion;
        private List<QuestionVersionDTO> history = List.of();
        private int statusCalls;
        private int historyCalls;
        private int findCalls;
        private int versionUpdateCalls;
        private int lastStatusUserId;
        private int lastStatusQuestionId;
        private QuestionStatus lastStatus;
        private int lastHistoryUserId;
        private int lastHistoryQuestionId;

        @Override
        public boolean updateStatusForTeacher(int authenticatedUserId, int questionId,
                                              QuestionStatus status) {
            statusCalls++;
            lastStatusUserId = authenticatedUserId;
            lastStatusQuestionId = questionId;
            lastStatus = status;
            return statusUpdated;
        }

        @Override
        public Optional<QuestionDTO> findCurrentByIdForTeacher(int authenticatedUserId,
                                                                int questionId) {
            findCalls++;
            if (currentQuestion == null || currentQuestion.getQuestionId() != questionId) {
                return Optional.empty();
            }
            return Optional.of(currentQuestion);
        }

        @Override
        public List<QuestionVersionDTO> findVersionsForTeacher(int authenticatedUserId,
                                                               int questionId) {
            historyCalls++;
            lastHistoryUserId = authenticatedUserId;
            lastHistoryQuestionId = questionId;
            return history;
        }

        @Override
        public int updateWithNewVersion(int updatedByUserId, UpdateQuestionPayload payload) {
            versionUpdateCalls++;
            return 1;
        }

        private void setStatusUpdated(boolean statusUpdated) {
            this.statusUpdated = statusUpdated;
        }

        private void setCurrentQuestion(QuestionDTO currentQuestion) {
            this.currentQuestion = currentQuestion;
        }

        private void setHistory(List<QuestionVersionDTO> history) {
            this.history = history;
        }

        private int getStatusCalls() { return statusCalls; }
        private int getHistoryCalls() { return historyCalls; }
        private int getFindCalls() { return findCalls; }
        private int getVersionUpdateCalls() { return versionUpdateCalls; }
        private int getLastStatusUserId() { return lastStatusUserId; }
        private int getLastStatusQuestionId() { return lastStatusQuestionId; }
        private QuestionStatus getLastStatus() { return lastStatus; }
        private int getLastHistoryUserId() { return lastHistoryUserId; }
        private int getLastHistoryQuestionId() { return lastHistoryQuestionId; }
    }
}
