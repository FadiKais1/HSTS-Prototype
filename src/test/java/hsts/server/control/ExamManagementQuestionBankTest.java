package hsts.server.control;

import hsts.common.CourseSummaryDTO;
import hsts.common.CreateQuestionPayload;
import hsts.common.QuestionDTO;
import hsts.common.QuestionFilterPayload;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.UserRole;
import hsts.common.type.UserStatus;
import hsts.server.entity.User;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.repository.UserRepository;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamManagementQuestionBankTest {
    @Test
    public void teacherAndCoordinatorAreAuthorized() {
        for (UserRole role : new UserRole[]{UserRole.TEACHER, UserRole.COORDINATOR}) {
            FakeUserRepository users = new FakeUserRepository(user(10, role, UserStatus.ACTIVE));
            FakeCourseRepository courses = new FakeCourseRepository();
            FakeQuestionRepository questions = new FakeQuestionRepository();
            questions.setQuestions(List.of(questionDto(3)));
            ExamManagementService service = new ExamManagementService(questions, courses, users);

            List<QuestionDTO> result = service.getQuestions(10, null);

            assertEquals(1, result.size());
            assertEquals(3, result.get(0).getQuestionId());
            assertEquals(10, questions.getLastListUserId());
        }
    }

    @Test
    public void studentAndPrincipalAreRejected() {
        for (UserRole role : new UserRole[]{UserRole.STUDENT, UserRole.PRINCIPAL}) {
            ExamManagementService service = serviceFor(user(20, role, UserStatus.ACTIVE));

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> service.getCoursesForTeacher(20)
            );

            assertEquals(
                    "Question management requires teacher or coordinator role",
                    exception.getMessage()
            );
        }
    }

    @Test
    public void missingAndBlockedUsersAreRejected() {
        ExamManagementService missingService = serviceFor();
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> missingService.getCoursesForTeacher(404)
        );
        assertEquals("User not found: 404", missing.getMessage());

        ExamManagementService blockedService = serviceFor(
                user(30, UserRole.TEACHER, UserStatus.BLOCKED)
        );
        IllegalStateException blocked = assertThrows(
                IllegalStateException.class,
                () -> blockedService.getCoursesForTeacher(30)
        );
        assertEquals("User account is blocked", blocked.getMessage());
    }

    @Test
    public void assignedCoursesAreReturned() {
        FakeUserRepository users = new FakeUserRepository(
                user(40, UserRole.TEACHER, UserStatus.ACTIVE)
        );
        FakeCourseRepository courses = new FakeCourseRepository();
        CourseSummaryDTO expected = new CourseSummaryDTO(
                7, 2, "MATH-7", "Mathematics", "Mathematics", "7", "2026"
        );
        courses.setCourses(List.of(expected));
        ExamManagementService service = new ExamManagementService(
                new FakeQuestionRepository(), courses, users
        );

        List<CourseSummaryDTO> result = service.getCoursesForTeacher(40);

        assertEquals(1, result.size());
        assertSame(expected, result.get(0));
        assertEquals(40, courses.getLastCoursesUserId());
    }

    @Test
    public void nullAndCombinedQuestionFiltersAreDelegated() {
        FakeUserRepository users = new FakeUserRepository(
                user(50, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );
        FakeCourseRepository courses = new FakeCourseRepository();
        FakeQuestionRepository questions = new FakeQuestionRepository();
        ExamManagementService service = new ExamManagementService(questions, courses, users);

        service.getQuestions(50, null);
        assertNull(questions.getLastFilter());

        QuestionFilterPayload combined = new QuestionFilterPayload(
                12, 4, "Calculus", DifficultyLevel.HARD, QuestionStatus.INACTIVE
        );
        service.getQuestions(50, combined);

        assertSame(combined, questions.getLastFilter());
        assertEquals(50, questions.getLastListUserId());
        assertEquals(50, courses.getLastAssignmentUserId());
        assertEquals(12, courses.getLastAssignmentCourseId());
    }

    @Test
    public void invalidOrUnassignedCourseFilterIsRejected() {
        FakeUserRepository users = new FakeUserRepository(
                user(60, UserRole.TEACHER, UserStatus.ACTIVE)
        );
        FakeCourseRepository courses = new FakeCourseRepository();
        FakeQuestionRepository questions = new FakeQuestionRepository();
        ExamManagementService service = new ExamManagementService(questions, courses, users);

        QuestionFilterPayload invalid = new QuestionFilterPayload(0, null, null, null, null);
        IllegalArgumentException invalidException = assertThrows(
                IllegalArgumentException.class,
                () -> service.getQuestions(60, invalid)
        );
        assertEquals("Course ID must be positive", invalidException.getMessage());
        assertEquals(0, courses.getAssignmentCalls());

        courses.setAssigned(false);
        QuestionFilterPayload unassigned = new QuestionFilterPayload(9, null, null, null, null);
        IllegalStateException unassignedException = assertThrows(
                IllegalStateException.class,
                () -> service.getQuestions(60, unassigned)
        );
        assertEquals("User is not assigned to course: 9", unassignedException.getMessage());
        assertEquals(0, questions.getListCalls());
    }

    @Test
    public void createNormalizesPayloadUsesAuthenticatedCreatorAndRereads() {
        FakeUserRepository users = new FakeUserRepository(
                user(70, UserRole.TEACHER, UserStatus.ACTIVE)
        );
        FakeCourseRepository courses = new FakeCourseRepository();
        FakeQuestionRepository questions = new FakeQuestionRepository();
        QuestionDTO reloaded = questionDto(88);
        questions.setCreatedQuestionId(88);
        questions.setReloadedQuestion(reloaded);
        ExamManagementService service = new ExamManagementService(questions, courses, users);
        CreateQuestionPayload payload = payload(
                15, "  Content  ", "  ", DifficultyLevel.MEDIUM, null,
                "  One  ", " Two ", " Three  ", "  Four ", 3
        );

        QuestionDTO result = service.createQuestion(70, payload);

        assertSame(reloaded, result);
        assertEquals(70, questions.getLastCreateUserId());
        assertEquals(70, questions.getLastReloadUserId());
        assertEquals(88, questions.getLastReloadQuestionId());
        CreateQuestionPayload normalized = questions.getLastCreatePayload();
        assertEquals(15, normalized.getCourseId());
        assertEquals("Content", normalized.getContent());
        assertEquals("General", normalized.getTopic());
        assertEquals(DifficultyLevel.MEDIUM, normalized.getDifficulty());
        assertEquals("", normalized.getIllustrationPath());
        assertEquals("One", normalized.getAnswerOption1());
        assertEquals("Two", normalized.getAnswerOption2());
        assertEquals("Three", normalized.getAnswerOption3());
        assertEquals("Four", normalized.getAnswerOption4());
        assertEquals(3, normalized.getCorrectOptionNumber());
    }

    @Test
    public void createValidationUsesRequiredOrderAndMessages() {
        FakeUserRepository users = new FakeUserRepository();
        FakeCourseRepository courses = new FakeCourseRepository();
        FakeQuestionRepository questions = new FakeQuestionRepository();
        ExamManagementService service = new ExamManagementService(questions, courses, users);

        assertValidation(service, null, "Question data is required");
        assertValidation(service, payload(
                0, " ", null, null, null, "", "", "", "", 0
        ), "Course is required");
        assertValidation(service, payload(
                1, " ", null, null, null, "", "", "", "", 0
        ), "Question content cannot be empty");
        assertValidation(service, payload(
                1, "Content", null, null, null, "", "", "", "", 0
        ), "Question difficulty is required");
        assertValidation(service, payload(
                1, "Content", null, DifficultyLevel.EASY, null,
                "One", " ", "Three", "Four", 0
        ), "All four answer options are required");
        assertValidation(service, payload(
                1, "Content", null, DifficultyLevel.EASY, null,
                "One", "Two", "Three", "Four", 0
        ), "Correct answer number must be between 1 and 4");

        assertEquals(0, users.getFindCalls());
        assertEquals(0, courses.getAssignmentCalls());
        assertEquals(0, questions.getCreateCalls());
    }

    @Test
    public void unassignedCreateIsRejectedBeforeRepositoryWrite() {
        FakeUserRepository users = new FakeUserRepository(
                user(80, UserRole.TEACHER, UserStatus.ACTIVE)
        );
        FakeCourseRepository courses = new FakeCourseRepository();
        courses.setAssigned(false);
        FakeQuestionRepository questions = new FakeQuestionRepository();
        ExamManagementService service = new ExamManagementService(questions, courses, users);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.createQuestion(80, validPayload(25))
        );

        assertEquals("User is not assigned to course: 25", exception.getMessage());
        assertEquals(0, questions.getCreateCalls());
    }

    @Test
    public void missingCreatedQuestionRereadFailsClearly() {
        FakeUserRepository users = new FakeUserRepository(
                user(90, UserRole.COORDINATOR, UserStatus.ACTIVE)
        );
        FakeQuestionRepository questions = new FakeQuestionRepository();
        questions.setCreatedQuestionId(123);
        ExamManagementService service = new ExamManagementService(
                questions, new FakeCourseRepository(), users
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.createQuestion(90, validPayload(5))
        );

        assertEquals("Created question could not be reloaded: 123", exception.getMessage());
    }

    @Test
    public void infrastructureFailuresPropagateUnchanged() {
        RuntimeException userFailure = new IllegalStateException("user infrastructure failed");
        FakeUserRepository failingUsers = new FakeUserRepository();
        failingUsers.setFindFailure(userFailure);
        ExamManagementService userService = new ExamManagementService(
                new FakeQuestionRepository(), new FakeCourseRepository(), failingUsers
        );
        assertSame(userFailure, assertThrows(
                RuntimeException.class,
                () -> userService.getCoursesForTeacher(1)
        ));

        RuntimeException courseFailure = new IllegalStateException("course infrastructure failed");
        FakeCourseRepository failingCourses = new FakeCourseRepository();
        failingCourses.setCoursesFailure(courseFailure);
        ExamManagementService courseService = new ExamManagementService(
                new FakeQuestionRepository(), failingCourses,
                new FakeUserRepository(user(1, UserRole.TEACHER, UserStatus.ACTIVE))
        );
        assertSame(courseFailure, assertThrows(
                RuntimeException.class,
                () -> courseService.getCoursesForTeacher(1)
        ));

        RuntimeException questionFailure = new IllegalStateException("question infrastructure failed");
        FakeQuestionRepository failingQuestions = new FakeQuestionRepository();
        failingQuestions.setListFailure(questionFailure);
        ExamManagementService questionService = new ExamManagementService(
                failingQuestions, new FakeCourseRepository(),
                new FakeUserRepository(user(1, UserRole.TEACHER, UserStatus.ACTIVE))
        );
        assertSame(questionFailure, assertThrows(
                RuntimeException.class,
                () -> questionService.getQuestions(1, null)
        ));
    }

    @Test
    public void legacyConstructorRejectsNewApisClearly() {
        ExamManagementService service = new ExamManagementService(new FakeQuestionRepository());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.getCoursesForTeacher(1)
        );

        assertEquals("Question-bank dependencies are not configured", exception.getMessage());

        ExamManagementService missingQuestionRepository = new ExamManagementService(
                null,
                new FakeCourseRepository(),
                new FakeUserRepository(user(1, UserRole.TEACHER, UserStatus.ACTIVE))
        );
        IllegalStateException missingQuestionException = assertThrows(
                IllegalStateException.class,
                () -> missingQuestionRepository.getQuestions(1, null)
        );
        assertEquals(
                "Question-bank dependencies are not configured",
                missingQuestionException.getMessage()
        );
    }

    private static void assertValidation(ExamManagementService service,
                                         CreateQuestionPayload payload,
                                         String expectedMessage) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createQuestion(999, payload)
        );
        assertEquals(expectedMessage, exception.getMessage());
    }

    private static ExamManagementService serviceFor(User... users) {
        return new ExamManagementService(
                new FakeQuestionRepository(),
                new FakeCourseRepository(),
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

    private static CreateQuestionPayload validPayload(int courseId) {
        return payload(
                courseId, "Content", "Topic", DifficultyLevel.HARD, "image.png",
                "One", "Two", "Three", "Four", 2
        );
    }

    private static CreateQuestionPayload payload(int courseId, String content, String topic,
                                                 DifficultyLevel difficulty, String illustrationPath,
                                                 String option1, String option2, String option3,
                                                 String option4, int correctOptionNumber) {
        return new CreateQuestionPayload(
                courseId, content, topic, difficulty, illustrationPath,
                option1, option2, option3, option4, correctOptionNumber
        );
    }

    private static QuestionDTO questionDto(int questionId) {
        return new QuestionDTO(
                questionId, "Content", "Topic", "MULTIPLE_CHOICE", "EASY", "ACTIVE",
                "", "One", "Two", "Three", "Four", 2, 1, 1, 1
        );
    }

    private static final class FakeUserRepository extends UserRepository {
        private final Map<Integer, User> users = new LinkedHashMap<>();
        private RuntimeException findFailure;
        private int findCalls;

        private FakeUserRepository(User... initialUsers) {
            for (User user : initialUsers) {
                users.put(user.getUserId(), user);
            }
        }

        @Override
        public Optional<User> findById(int userId) {
            findCalls++;
            if (findFailure != null) {
                throw findFailure;
            }
            return Optional.ofNullable(users.get(userId));
        }

        private int getFindCalls() {
            return findCalls;
        }

        private void setFindFailure(RuntimeException findFailure) {
            this.findFailure = findFailure;
        }
    }

    private static final class FakeCourseRepository extends CourseRepository {
        private List<CourseSummaryDTO> courses = new ArrayList<>();
        private boolean assigned = true;
        private int assignmentCalls;
        private int lastCoursesUserId;
        private int lastAssignmentUserId;
        private int lastAssignmentCourseId;
        private RuntimeException coursesFailure;

        @Override
        public List<CourseSummaryDTO> findAssignedToTeacher(int userId) {
            if (coursesFailure != null) {
                throw coursesFailure;
            }
            lastCoursesUserId = userId;
            return courses;
        }

        @Override
        public boolean isAssignedToTeacher(int userId, int courseId) {
            assignmentCalls++;
            lastAssignmentUserId = userId;
            lastAssignmentCourseId = courseId;
            return assigned;
        }

        private void setCourses(List<CourseSummaryDTO> courses) {
            this.courses = courses;
        }

        private void setAssigned(boolean assigned) {
            this.assigned = assigned;
        }

        private int getAssignmentCalls() {
            return assignmentCalls;
        }

        private int getLastCoursesUserId() {
            return lastCoursesUserId;
        }

        private int getLastAssignmentUserId() {
            return lastAssignmentUserId;
        }

        private int getLastAssignmentCourseId() {
            return lastAssignmentCourseId;
        }

        private void setCoursesFailure(RuntimeException coursesFailure) {
            this.coursesFailure = coursesFailure;
        }
    }

    private static final class FakeQuestionRepository extends QuestionRepository {
        private List<QuestionDTO> questions = new ArrayList<>();
        private QuestionFilterPayload lastFilter;
        private CreateQuestionPayload lastCreatePayload;
        private QuestionDTO reloadedQuestion;
        private RuntimeException listFailure;
        private int createdQuestionId = 1;
        private int lastListUserId;
        private int lastCreateUserId;
        private int lastReloadUserId;
        private int lastReloadQuestionId;
        private int listCalls;
        private int createCalls;

        @Override
        public List<QuestionDTO> findCurrentForTeacher(int authenticatedUserId,
                                                       QuestionFilterPayload filter) {
            listCalls++;
            if (listFailure != null) {
                throw listFailure;
            }
            lastListUserId = authenticatedUserId;
            lastFilter = filter;
            return questions;
        }

        @Override
        public int create(int createdByUserId, CreateQuestionPayload payload) {
            createCalls++;
            lastCreateUserId = createdByUserId;
            lastCreatePayload = payload;
            return createdQuestionId;
        }

        @Override
        public Optional<QuestionDTO> findCurrentByIdForTeacher(int authenticatedUserId,
                                                                int questionId) {
            lastReloadUserId = authenticatedUserId;
            lastReloadQuestionId = questionId;
            return Optional.ofNullable(reloadedQuestion);
        }

        private void setQuestions(List<QuestionDTO> questions) {
            this.questions = questions;
        }

        private void setCreatedQuestionId(int createdQuestionId) {
            this.createdQuestionId = createdQuestionId;
        }

        private void setReloadedQuestion(QuestionDTO reloadedQuestion) {
            this.reloadedQuestion = reloadedQuestion;
        }

        private void setListFailure(RuntimeException listFailure) {
            this.listFailure = listFailure;
        }

        private QuestionFilterPayload getLastFilter() {
            return lastFilter;
        }

        private CreateQuestionPayload getLastCreatePayload() {
            return lastCreatePayload;
        }

        private int getLastListUserId() {
            return lastListUserId;
        }

        private int getLastCreateUserId() {
            return lastCreateUserId;
        }

        private int getLastReloadUserId() {
            return lastReloadUserId;
        }

        private int getLastReloadQuestionId() {
            return lastReloadQuestionId;
        }

        private int getListCalls() {
            return listCalls;
        }

        private int getCreateCalls() {
            return createCalls;
        }
    }
}
