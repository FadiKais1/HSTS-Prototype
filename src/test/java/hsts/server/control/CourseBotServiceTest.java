package hsts.server.control;

import hsts.common.AddBotQuestionSourcesPayload;
import hsts.common.AddBotTextSourcePayload;
import hsts.common.AskCourseBotPayload;
import hsts.common.BotHistoryDTO;
import hsts.common.BotQuestionResultDTO;
import hsts.common.BotQuestionVersionReference;
import hsts.common.BotSourceDTO;
import hsts.common.BotUsageSummaryDTO;
import hsts.common.CommonBotQuestionDTO;
import hsts.common.CourseSummaryDTO;
import hsts.common.CreateCourseBotPayload;
import hsts.common.QuestionVersionDTO;
import hsts.common.type.BotAnswerStatus;
import hsts.common.type.BotSourceStatus;
import hsts.common.type.BotSourceType;
import hsts.common.type.BotStatus;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import hsts.common.type.UserStatus;
import hsts.external.BotProviderRequest;
import hsts.external.BotProviderResponse;
import hsts.external.ExternalBotSystem;
import hsts.server.bot.source.BotSourceExtractor;
import hsts.server.entity.AnswerOption;
import hsts.server.entity.BotConversation;
import hsts.server.entity.BotMessage;
import hsts.server.entity.BotSource;
import hsts.server.entity.Coordinator;
import hsts.server.entity.CourseBot;
import hsts.server.entity.Principal;
import hsts.server.entity.Question;
import hsts.server.entity.Student;
import hsts.server.entity.Teacher;
import hsts.server.entity.User;
import hsts.server.repository.BotConversationRepository;
import hsts.server.repository.CourseBotRepository;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.ExamSubmissionRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.repository.UserRepository;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CourseBotServiceTest {

    /**
     * Requirement 48 asks for a message when the study bot has no answer, and
     * that message is read by a student. BotConversation and the provider guard
     * describe broken rules in developer language, and the server returns every
     * exception message to the caller, so those strings would otherwise reach
     * her screen.
     *
     * <p>This pins the student wording and forbids the internal vocabulary that
     * previously leaked, such as "Conversation update time cannot precede
     * message creation".</p>
     */
    @Test
    public void studentFacingBotFailuresAvoidInternalRuleVocabulary() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/hsts/server/control/CourseBotService.java"
        ));

        assertTrue(source.contains(
                "The study bot could not answer right now. Please try again in a moment."
        ));
        assertTrue(source.contains(
                "The study bot's answer could not be saved. Please ask your question again."
        ));

        // An internal rule must never be thrown on for the student to read.
        for (String leaked : java.util.List.of(
                "\"Unable to obtain a Course Bot answer\"",
                "\"Course Bot did not return a valid response\"",
                "cannot precede message creation",
                "sequence must be contiguous"
        )) {
            assertFalse(
                    "Internal wording reaches the student: " + leaked,
                    source.contains(leaked)
            );
        }
    }
    private static final int TEACHER = 1002;
    private static final int COORDINATOR = 1003;
    private static final int STUDENT = 1001;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 20, 12, 0);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC
    );

    @Test
    public void managementRequiresActiveTeacherOrCoordinator() {
        Fixture fixture = new Fixture();
        fixture.users.put(TEACHER, Teacher.rehydrate(
                TEACHER, "Teacher", "teacher@test", "hash", UserStatus.ACTIVE
        ));
        fixture.users.put(COORDINATOR, Coordinator.rehydrate(
                COORDINATOR, "Coordinator", "coordinator@test", "hash", UserStatus.ACTIVE
        ));
        fixture.users.put(STUDENT, Student.rehydrate(
                STUDENT, "Student", "student@test", "hash", UserStatus.ACTIVE
        ));
        fixture.users.put(2000, Principal.rehydrate(
                2000, "Principal", "principal@test", "hash", UserStatus.ACTIVE
        ));
        fixture.bots.add(bot(7, TEACHER, BotStatus.ACTIVE, List.of()));

        assertEquals(1, fixture.service().getMyCourseBots(TEACHER).size());
        assertEquals(1, fixture.service().getMyCourseBots(COORDINATOR).size());
        assertMessage("Teacher access required",
                () -> fixture.service().getMyCourseBots(STUDENT));
        assertMessage("Teacher access required",
                () -> fixture.service().getMyCourseBots(2000));
        assertMessage("Teacher access required",
                () -> fixture.service().getMyCourseBots(9999));
    }

    @Test
    public void createUsesAuthenticatedCreatorAndInactiveDefault() {
        Fixture fixture = teacherFixture();
        fixture.assigned = true;

        var result = fixture.service().createCourseBot(
                TEACHER, new CreateCourseBotPayload(1, "  Study Bot  ")
        );

        assertEquals(TEACHER, fixture.createdBot.getCreatedByUserId());
        assertEquals(BotStatus.INACTIVE, fixture.createdBot.getStatus());
        assertEquals(NOW, fixture.createdBot.getCreatedAt());
        assertNull(fixture.createdBot.getExternalProvider());
        assertEquals("Study Bot", result.getBotName());
        assertEquals("Calculus", result.getCourseName());
    }

    @Test
    public void textSourceUsesExtractorAndReturnsMetadataOnly() {
        Fixture fixture = teacherFixture();
        fixture.bots.add(bot(7, TEACHER, BotStatus.ACTIVE, List.of()));

        BotSourceDTO result = fixture.service().addTextSource(
                TEACHER, new AddBotTextSourcePayload(7, " Notes ", " alpha\r\nbeta ")
        );

        assertEquals(BotSourceType.FREE_TEXT, result.getSourceType());
        assertEquals("alpha\nbeta", fixture.addedSources.get(0).getExtractedText());
        assertEquals(TEACHER, fixture.addedSources.get(0).getAddedByUserId());
        String dtoFields = java.util.Arrays.stream(BotSourceDTO.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).reduce("", String::concat);
        assertFalse(dtoFields.contains("extractedText"));
        assertFalse(dtoFields.contains("contentSha256"));
    }

    @Test
    public void exactQuestionVersionIsGroundedBeforeWritesAndCorrectnessStaysPrivate() {
        Fixture fixture = teacherFixture();
        fixture.bots.add(bot(7, TEACHER, BotStatus.ACTIVE, List.of()));
        fixture.versions = List.of(new QuestionVersionDTO(
                11, 2, 1, "What is two plus two?", "Arithmetic",
                QuestionType.MULTIPLE_CHOICE, DifficultyLevel.EASY, "",
                "3", "4", "5", "6", 2, TEACHER, NOW
        ));
        fixture.question = Question.rehydrate(
                11, "What is two plus two?", QuestionType.MULTIPLE_CHOICE,
                DifficultyLevel.EASY, QuestionStatus.ACTIVE, NOW, NOW,
                "Arithmetic", "", List.of(
                        new AnswerOption(1, "3", false),
                        new AnswerOption(2, "4", true),
                        new AnswerOption(3, "5", false),
                        new AnswerOption(4, "6", false)
                )
        );

        List<BotSourceDTO> results = fixture.service().addQuestionSources(
                TEACHER, new AddBotQuestionSourcesPayload(7, List.of(
                        new BotQuestionVersionReference(11, 2)
                ))
        );

        assertEquals(List.of("versions:11", "entity:11:2", "write:11:2"),
                fixture.questionEvents);
        String grounded = fixture.addedSources.get(0).getExtractedText();
        assertTrue(grounded.contains("Correct option: 2"));
        assertTrue(grounded.contains("Correct answer: 4"));
        assertEquals(11, results.get(0).getQuestionId().intValue());
        assertFalse(java.util.Arrays.stream(BotSourceDTO.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .anyMatch(name -> name.toLowerCase().contains("correct")));
    }

    @Test
    public void studentListingExcludesOnlySameCourseLockout() {
        Fixture fixture = studentFixture();
        fixture.studentBots.add(bot(7, TEACHER, BotStatus.ACTIVE, List.of()));
        fixture.studentBots.add(CourseBot.rehydrate(
                8, 2, "Physics Bot", BotStatus.ACTIVE, TEACHER,
                null, null, NOW, NOW, List.of()
        ));
        fixture.lockedCourses.add(1);

        var available = fixture.service().getMyAvailableBots(STUDENT);

        assertEquals(1, available.size());
        assertEquals(2, available.get(0).getCourseId());
        assertEquals(List.of(1, 2), fixture.lockoutChecks);
    }

    @Test
    public void personalHistoryIsEmptyOrOrderedAndLockoutConcealsIt() {
        Fixture fixture = studentFixture();
        fixture.studentBot = bot(7, TEACHER, BotStatus.ACTIVE, List.of());

        BotHistoryDTO empty = fixture.service().getMyBotHistory(STUDENT, 1);
        assertTrue(empty.getMessages().isEmpty());

        fixture.history = conversation(List.of(
                persistedMessage(1, "first", "answer one", 1),
                persistedMessage(2, "second", "answer two", 2)
        ));
        assertEquals(List.of("first", "second"),
                fixture.service().getMyBotHistory(STUDENT, 1).getMessages().stream()
                        .map(value -> value.getQuestionText()).toList());

        fixture.lockedCourses.add(1);
        assertMessage("Course Bot is unavailable during an active exam",
                () -> fixture.service().getMyBotHistory(STUDENT, 1));
    }

    @Test
    public void askBuildsPseudonymousGroundedRequestAndPersistsOnce() {
        Fixture fixture = studentFixture();
        BotSource source = BotSource.rehydrate(
                9, 7, BotSourceType.FREE_TEXT, "Limits", "A limit describes nearby behavior.",
                "a".repeat(64), null, null, TEACHER, BotSourceStatus.ACTIVE,
                null, NOW, null
        );
        fixture.studentBot = bot(7, TEACHER, BotStatus.ACTIVE, List.of(source));
        fixture.providerResponse = new BotProviderResponse(
                BotAnswerStatus.ANSWERED, "A limit describes nearby behavior.", "safe-1"
        );

        BotQuestionResultDTO result = fixture.service().askCourseBot(
                STUDENT, new AskCourseBotPayload(1, "  What IS a limit?  ")
        );

        assertEquals(1, fixture.providerCalls);
        assertEquals("what is a limit?", fixture.providerRequest.getQuestionText());
        assertEquals("123e4567-e89b-12d3-a456-426614174000",
                fixture.providerRequest.getProviderSubjectId());
        assertEquals(1, fixture.appendCalls);
        assertEquals(NOW, fixture.appendedMessage.getCreatedAt());
        assertTrue(result.isSuitableAnswer());
        assertEquals("A limit describes nearby behavior.",
                result.getMessage().getAnswerText());
    }

    @Test
    public void askNeverCallsProviderForLockoutOrMissingSourcesAndSanitizesFailure() {
        Fixture fixture = studentFixture();
        fixture.studentBot = bot(7, TEACHER, BotStatus.ACTIVE, List.of());
        assertMessage("Course Bot has no active sources",
                () -> fixture.service().askCourseBot(
                        STUDENT, new AskCourseBotPayload(1, "limits")
                ));
        assertEquals(0, fixture.providerCalls);

        fixture.lockedCourses.add(1);
        assertMessage("Course Bot is unavailable during an active exam",
                () -> fixture.service().askCourseBot(
                        STUDENT, new AskCourseBotPayload(1, "limits")
                ));
        assertEquals(0, fixture.providerCalls);
    }

    @Test
    public void usageIsAnonymousAndLegacyMethodsFailWithoutAuthenticatedContext() {
        Fixture fixture = teacherFixture();
        fixture.bots.add(bot(7, TEACHER, BotStatus.ACTIVE, List.of()));
        fixture.usage = new BotUsageSummaryDTO(
                7, 1, "Study Bot", "Calculus", 3, NOW,
                List.of(new CommonBotQuestionDTO("limits", 3))
        );
        assertEquals(3, fixture.service().getBotUsage(TEACHER, 7).getTotalQuestions());
        assertEquals(10, fixture.usageLimit);

        CourseBotService compatibility = new CourseBotService();
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> compatibility.sendQuestionToBot(1, 1, "question"));
        assertEquals("Authenticated context is required", error.getMessage());
        assertFalse(java.util.Arrays.stream(CourseBotService.class.getDeclaredMethods())
                .anyMatch(method -> method.toString().contains("UnsupportedOperationException")));
    }

    private static Fixture teacherFixture() {
        Fixture fixture = new Fixture();
        fixture.users.put(TEACHER, Teacher.rehydrate(
                TEACHER, "Teacher", "teacher@test", "hash", UserStatus.ACTIVE
        ));
        return fixture;
    }

    private static Fixture studentFixture() {
        Fixture fixture = new Fixture();
        fixture.users.put(STUDENT, Student.rehydrate(
                STUDENT, "Student", "student@test", "hash", UserStatus.ACTIVE
        ));
        return fixture;
    }

    private static CourseBot bot(int id, int creator, BotStatus status,
                                 List<BotSource> sources) {
        return CourseBot.rehydrate(
                id, 1, "Study Bot", status, creator,
                null, null, NOW, NOW, sources
        );
    }

    private static BotConversation conversation(List<BotMessage> messages) {
        return BotConversation.rehydrate(
                31, 7, STUDENT, "123e4567-e89b-12d3-a456-426614174000",
                NOW, NOW, messages
        );
    }

    private static BotMessage persistedMessage(int id, String question,
                                               String answer, int sequence) {
        return BotMessage.rehydrate(
                id, 31, sequence, question, question, answer,
                BotAnswerStatus.ANSWERED, null, NOW
        );
    }

    private static void assertMessage(String message, Runnable action) {
        RuntimeException error = assertThrows(RuntimeException.class, action::run);
        assertEquals(message, error.getMessage());
    }

    private static final class Fixture {
        final Map<Integer, User> users = new HashMap<>();
        final List<CourseBot> bots = new ArrayList<>();
        final List<CourseBot> studentBots = new ArrayList<>();
        final List<BotSource> addedSources = new ArrayList<>();
        final List<Integer> lockedCourses = new ArrayList<>();
        final List<Integer> lockoutChecks = new ArrayList<>();
        final List<String> questionEvents = new ArrayList<>();
        boolean assigned;
        CourseBot createdBot;
        CourseBot studentBot;
        BotConversation history;
        Question question;
        List<QuestionVersionDTO> versions = List.of();
        BotUsageSummaryDTO usage;
        int usageLimit;
        int providerCalls;
        int appendCalls;
        BotProviderRequest providerRequest;
        BotProviderResponse providerResponse;
        BotMessage appendedMessage;

        CourseBotService service() {
            CourseBotRepository botRepository = new CourseBotRepository() {
                @Override
                public List<CourseBot> findAssignedToTeacher(int userId) {
                    return List.copyOf(bots);
                }

                @Override
                public CourseBot create(int userId, CourseBot bot) {
                    createdBot = bot;
                    return CourseBot.rehydrate(
                            7, bot.getCourseId(), bot.getName(), bot.getStatus(),
                            bot.getCreatedByUserId(), null, null,
                            bot.getCreatedAt(), bot.getUpdatedAt(), List.of()
                    );
                }

                @Override
                public CourseBot persistConfiguration(int userId, CourseBot bot) {
                    return bot;
                }

                @Override
                public BotSource addSource(int userId, BotSource source) {
                    addedSources.add(source);
                    if (source.getQuestionId() != null) {
                        questionEvents.add("write:" + source.getQuestionId() + ":"
                                + source.getQuestionVersionNo());
                    }
                    return BotSource.rehydrate(
                            50 + addedSources.size(), source.getBotId(),
                            source.getSourceType(), source.getDisplayName(),
                            source.getExtractedText(), source.getContentSha256(),
                            source.getQuestionId(), source.getQuestionVersionNo(),
                            source.getAddedByUserId(), source.getStatus(), null,
                            source.getCreatedAt(), source.getRemovedAt()
                    );
                }

                @Override
                public List<BotSource> findSourcesForTeacher(int userId, int botId) {
                    return bots.stream().filter(bot -> bot.getBotId() == botId)
                            .findFirst().map(CourseBot::getSources).orElseGet(List::of);
                }

                @Override
                public List<CourseBot> findActiveForStudent(int userId) {
                    return List.copyOf(studentBots);
                }

                @Override
                public Optional<CourseBot> findActiveByCourseForStudent(
                        int userId, int courseId
                ) {
                    return Optional.ofNullable(studentBot);
                }

                @Override
                public BotUsageSummaryDTO findAnonymousUsageForTeacher(
                        int userId, int botId, int limit
                ) {
                    usageLimit = limit;
                    return usage;
                }
            };
            BotConversationRepository conversations = new BotConversationRepository() {
                @Override
                public Optional<BotConversation> findHistoryForStudent(
                        int userId, int botId
                ) {
                    return Optional.ofNullable(history);
                }

                @Override
                public BotConversation getOrCreateForStudent(
                        int userId, CourseBot bot, String subject, LocalDateTime time
                ) {
                    history = BotConversation.rehydrate(
                            31, bot.getBotId(), userId, subject, time, time, List.of()
                    );
                    return history;
                }

                @Override
                public BotConversation appendMessage(
                        int userId, BotConversation conversation, BotMessage message
                ) {
                    appendCalls++;
                    appendedMessage = message;
                    BotMessage persisted = BotMessage.rehydrate(
                            91, message.getConversationId(), message.getSequenceNumber(),
                            message.getQuestionText(), message.getNormalizedQuestion(),
                            message.getAnswerText(), message.getAnswerStatus(),
                            message.getProviderRequestId(), message.getCreatedAt()
                    );
                    return BotConversation.rehydrate(
                            conversation.getConversationId(), conversation.getBotId(),
                            conversation.getStudentUserId(), conversation.getProviderSubjectId(),
                            conversation.getCreatedAt(), message.getCreatedAt(),
                            List.of(persisted)
                    );
                }
            };
            QuestionRepository questions = new QuestionRepository() {
                @Override
                public List<QuestionVersionDTO> findVersionsForTeacher(int userId,
                                                                       int questionId) {
                    questionEvents.add("versions:" + questionId);
                    return versions;
                }

                @Override
                public Optional<Question> findEntityVersionForTeacher(
                        int userId, int questionId, int versionNo
                ) {
                    questionEvents.add("entity:" + questionId + ":" + versionNo);
                    return Optional.ofNullable(question);
                }
            };
            CourseRepository courses = new CourseRepository() {
                @Override
                public boolean isAssignedToTeacher(int userId, int courseId) {
                    return assigned;
                }

                @Override
                public Optional<CourseSummaryDTO> findById(int courseId) {
                    String name = courseId == 1 ? "Calculus" : "Physics";
                    return Optional.of(new CourseSummaryDTO(
                            courseId, 1, "C" + courseId, name,
                            "Science", "General", "2026"
                    ));
                }
            };
            UserRepository userRepository = new UserRepository() {
                @Override
                public Optional<User> findById(int userId) {
                    return Optional.ofNullable(users.get(userId));
                }
            };
            ExamSubmissionRepository submissions = new ExamSubmissionRepository() {
                @Override
                public boolean existsInProgressForStudentAndCourse(int userId,
                                                                    int courseId) {
                    lockoutChecks.add(courseId);
                    return lockedCourses.contains(courseId);
                }
            };
            ExternalBotSystem provider = request -> {
                providerCalls++;
                providerRequest = request;
                return providerResponse;
            };
            return new CourseBotService(
                    botRepository, conversations, questions, courses, userRepository,
                    submissions, new BotSourceExtractor(), provider, CLOCK,
                    () -> "123e4567-e89b-12d3-a456-426614174000"
            );
        }
    }
}
