package hsts.server.control;

import hsts.common.AddBotQuestionSourcesPayload;
import hsts.common.AddBotTextSourcePayload;
import hsts.common.AskCourseBotPayload;
import hsts.common.BotHistoryDTO;
import hsts.common.BotMessageDTO;
import hsts.common.BotQuestionResultDTO;
import hsts.common.BotQuestionVersionReference;
import hsts.common.BotSourceDTO;
import hsts.common.BotUsageSummaryDTO;
import hsts.common.CourseBotSummaryDTO;
import hsts.common.CourseSummaryDTO;
import hsts.common.CreateCourseBotPayload;
import hsts.common.QuestionVersionDTO;
import hsts.common.RemoveBotSourcePayload;
import hsts.common.UpdateCourseBotPayload;
import hsts.common.UploadBotSourcePayload;
import hsts.common.type.BotAnswerStatus;
import hsts.common.type.BotSourceStatus;
import hsts.common.type.BotStatus;
import hsts.common.type.UserRole;
import hsts.external.BotProviderRequest;
import hsts.external.BotProviderResponse;
import hsts.external.BotProviderSource;
import hsts.external.BotProviderTurn;
import hsts.external.ExternalBotSystem;
import hsts.server.bot.source.BotSourceExtractor;
import hsts.server.bot.source.ExtractedBotSource;
import hsts.server.entity.AnswerOption;
import hsts.server.entity.BotConversation;
import hsts.server.entity.BotMessage;
import hsts.server.entity.BotSource;
import hsts.server.entity.CourseBot;
import hsts.server.entity.Question;
import hsts.server.entity.User;
import hsts.server.repository.BotConversationRepository;
import hsts.server.repository.CourseBotRepository;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.ExamSubmissionRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class CourseBotService {
    private static final int COMMON_QUESTION_LIMIT = 10;
    private static final String AUTHENTICATED_CONTEXT_REQUIRED =
            "Authenticated context is required";

    private final CourseBotRepository courseBotRepository;
    private final BotConversationRepository conversationRepository;
    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final BotSourceExtractor sourceExtractor;
    private final ExternalBotSystem externalBotSystem;
    private final Clock clock;
    private final Supplier<String> providerSubjectSupplier;

    // COMPATIBILITY-ONLY: Legacy skeleton construction has no authenticated dependencies.
    public CourseBotService() {
        this(null, null, null, null, null, null, null, null, null, null);
    }

    public CourseBotService(
            CourseBotRepository courseBotRepository,
            BotConversationRepository conversationRepository,
            QuestionRepository questionRepository,
            CourseRepository courseRepository,
            UserRepository userRepository,
            ExamSubmissionRepository examSubmissionRepository,
            BotSourceExtractor sourceExtractor,
            ExternalBotSystem externalBotSystem,
            Clock clock,
            Supplier<String> providerSubjectSupplier
    ) {
        this.courseBotRepository = courseBotRepository;
        this.conversationRepository = conversationRepository;
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.examSubmissionRepository = examSubmissionRepository;
        this.sourceExtractor = sourceExtractor;
        this.externalBotSystem = externalBotSystem;
        this.clock = clock;
        this.providerSubjectSupplier = providerSubjectSupplier;
    }

    public List<CourseBotSummaryDTO> getMyCourseBots(int authenticatedUserId) {
        requireTeacher(authenticatedUserId);
        requireDependencies();
        List<CourseBotSummaryDTO> results = new ArrayList<>();
        for (CourseBot bot : courseBotRepository.findAssignedToTeacher(authenticatedUserId)) {
            results.add(toSummary(bot, courseName(bot.getCourseId())));
        }
        return List.copyOf(results);
    }

    public CourseBotSummaryDTO createCourseBot(
            int authenticatedUserId, CreateCourseBotPayload payload
    ) {
        if (payload == null) {
            throw new IllegalArgumentException("Course Bot data is required");
        }
        requireTeacher(authenticatedUserId);
        requireDependencies();
        if (!courseRepository.isAssignedToTeacher(
                authenticatedUserId, payload.getCourseId()
        )) {
            throw new IllegalStateException("Course not found or access denied");
        }
        LocalDateTime now = now();
        CourseBot bot = CourseBot.create(
                payload.getCourseId(), payload.getBotName(), authenticatedUserId,
                null, null, now
        );
        bot.deactivate(now);
        CourseBot persisted = courseBotRepository.create(authenticatedUserId, bot);
        return toSummary(persisted, courseName(persisted.getCourseId()));
    }

    public CourseBotSummaryDTO updateCourseBot(
            int authenticatedUserId, UpdateCourseBotPayload payload
    ) {
        if (payload == null) {
            throw new IllegalArgumentException("Course Bot update data is required");
        }
        requireTeacher(authenticatedUserId);
        requireDependencies();
        CourseBot bot = assignedBot(authenticatedUserId, payload.getBotId());
        LocalDateTime now = now();
        bot.rename(payload.getBotName(), now);
        if (payload.getStatus() == BotStatus.ACTIVE) {
            bot.activate(now);
        } else {
            bot.deactivate(now);
        }
        CourseBot persisted = courseBotRepository.persistConfiguration(
                authenticatedUserId, bot
        );
        return toSummary(persisted, courseName(persisted.getCourseId()));
    }

    public List<BotSourceDTO> getBotSources(int authenticatedUserId, int botId) {
        requireTeacher(authenticatedUserId);
        requireDependencies();
        assignedBot(authenticatedUserId, botId);
        return courseBotRepository.findSourcesForTeacher(authenticatedUserId, botId)
                .stream().map(CourseBotService::toSourceDto).toList();
    }

    public BotSourceDTO addTextSource(
            int authenticatedUserId, AddBotTextSourcePayload payload
    ) {
        if (payload == null) {
            throw new IllegalArgumentException("Bot text source data is required");
        }
        requireTeacher(authenticatedUserId);
        requireDependencies();
        CourseBot bot = assignedBot(authenticatedUserId, payload.getBotId());
        ExtractedBotSource extracted = sourceExtractor.extractFreeText(
                payload.getDisplayName(), payload.getText()
        );
        return persistExtractedSource(authenticatedUserId, bot, extracted, null, null);
    }

    public BotSourceDTO uploadSource(
            int authenticatedUserId, UploadBotSourcePayload payload
    ) {
        if (payload == null) {
            throw new IllegalArgumentException("Bot upload data is required");
        }
        requireTeacher(authenticatedUserId);
        requireDependencies();
        CourseBot bot = assignedBot(authenticatedUserId, payload.getBotId());
        ExtractedBotSource extracted = sourceExtractor.extractFile(
                payload.getFileName(), payload.getSourceType(), payload.getContent()
        );
        return persistExtractedSource(authenticatedUserId, bot, extracted, null, null);
    }

    public List<BotSourceDTO> addQuestionSources(
            int authenticatedUserId, AddBotQuestionSourcesPayload payload
    ) {
        if (payload == null) {
            throw new IllegalArgumentException("Question source data is required");
        }
        requireTeacher(authenticatedUserId);
        requireDependencies();
        CourseBot bot = assignedBot(authenticatedUserId, payload.getBotId());
        List<BotSource> prepared = prepareQuestionSources(
                authenticatedUserId, bot, payload.getQuestions()
        );
        List<BotSourceDTO> persisted = new ArrayList<>(prepared.size());
        for (BotSource source : prepared) {
            persisted.add(toSourceDto(
                    courseBotRepository.addSource(authenticatedUserId, source)
            ));
        }
        return List.copyOf(persisted);
    }

    public BotSourceDTO removeSource(
            int authenticatedUserId, RemoveBotSourcePayload payload
    ) {
        if (payload == null) {
            throw new IllegalArgumentException("Bot source removal data is required");
        }
        requireTeacher(authenticatedUserId);
        requireDependencies();
        assignedBot(authenticatedUserId, payload.getBotId());
        BotSource source = courseBotRepository.findSourcesForTeacher(
                        authenticatedUserId, payload.getBotId()
                ).stream()
                .filter(candidate -> candidate.getSourceId() == payload.getSourceId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Bot source not found or access denied"
                ));
        source.remove(now());
        return toSourceDto(courseBotRepository.persistSourceRemoval(
                authenticatedUserId, source
        ));
    }

    public BotUsageSummaryDTO getBotUsage(int authenticatedUserId, int botId) {
        requireTeacher(authenticatedUserId);
        requireDependencies();
        assignedBot(authenticatedUserId, botId);
        return courseBotRepository.findAnonymousUsageForTeacher(
                authenticatedUserId, botId, COMMON_QUESTION_LIMIT
        );
    }

    public List<CourseBotSummaryDTO> getMyAvailableBots(int authenticatedUserId) {
        requireStudent(authenticatedUserId);
        requireDependencies();
        List<CourseBotSummaryDTO> results = new ArrayList<>();
        for (CourseBot bot : courseBotRepository.findActiveForStudent(
                authenticatedUserId
        )) {
            if (!isLockedOut(authenticatedUserId, bot.getCourseId())) {
                results.add(toSummary(bot, courseName(bot.getCourseId())));
            }
        }
        return List.copyOf(results);
    }

    public BotHistoryDTO getMyBotHistory(int authenticatedUserId, int courseId) {
        if (courseId <= 0) {
            throw new IllegalArgumentException("Course ID must be positive");
        }
        requireStudent(authenticatedUserId);
        requireDependencies();
        CourseBot bot = activeStudentBot(authenticatedUserId, courseId);
        requireBotAvailable(authenticatedUserId, courseId);
        List<BotMessageDTO> messages = conversationRepository.findHistoryForStudent(
                        authenticatedUserId, bot.getBotId()
                ).map(BotConversation::getMessages)
                .orElseGet(List::of)
                .stream().map(CourseBotService::toMessageDto).toList();
        return new BotHistoryDTO(
                bot.getBotId(), bot.getCourseId(), bot.getName(),
                courseName(courseId), messages
        );
    }

    public BotQuestionResultDTO askCourseBot(
            int authenticatedUserId, AskCourseBotPayload payload
    ) {
        if (payload == null) {
            throw new IllegalArgumentException("Bot question data is required");
        }
        String normalizedQuestion = normalizeQuestion(payload.getQuestionText());
        requireStudent(authenticatedUserId);
        requireDependencies();
        CourseBot bot = activeStudentBot(authenticatedUserId, payload.getCourseId());
        requireBotAvailable(authenticatedUserId, payload.getCourseId());

        Optional<BotConversation> existing = conversationRepository.findHistoryForStudent(
                authenticatedUserId, bot.getBotId()
        );
        BotConversation conversation = existing.orElseGet(() ->
                conversationRepository.getOrCreateForStudent(
                        authenticatedUserId, bot, nextProviderSubject(), now()
                )
        );
        List<BotSource> activeSources = bot.getSources().stream()
                .filter(source -> source.getStatus() == BotSourceStatus.ACTIVE)
                .toList();
        if (activeSources.isEmpty()) {
            throw new IllegalStateException("Course Bot has no active sources");
        }

        BotProviderRequest request = new BotProviderRequest(
                conversation.getProviderSubjectId(), bot.getName(),
                courseName(bot.getCourseId()),
                activeSources.stream().map(source -> new BotProviderSource(
                        source.getDisplayName(), source.getSourceType(),
                        source.getExtractedText()
                )).toList(),
                conversation.getMessages().stream().map(message -> new BotProviderTurn(
                        message.getQuestionText(), message.getAnswerText(),
                        message.getAnswerStatus()
                )).toList(),
                normalizedQuestion
        );

        BotProviderResponse providerResponse;
        try {
            providerResponse = externalBotSystem.answer(request);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to obtain a Course Bot answer", exception);
        }
        if (providerResponse == null || providerResponse.getAnswerStatus() == null
                || (providerResponse.getAnswerStatus() == BotAnswerStatus.ANSWERED
                && (providerResponse.getAnswerText() == null
                || providerResponse.getAnswerText().isBlank()))) {
            throw new IllegalStateException("Course Bot did not return a valid response");
        }

        BotMessage message = BotMessage.create(
                conversation.getConversationId(), conversation.getMessages().size() + 1,
                payload.getQuestionText(), normalizedQuestion,
                providerResponse.getAnswerText(), providerResponse.getAnswerStatus(),
                providerResponse.getProviderRequestId(), now()
        );
        BotConversation persisted;
        try {
            persisted = conversationRepository.appendMessage(
                    authenticatedUserId, conversation, message
            );
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null
                    && (exception.getMessage().contains("sequence conflict")
                    || exception.getMessage().contains("was modified"))) {
                throw new IllegalStateException(
                        "Bot conversation was modified; retry the question", exception
                );
            }
            throw exception;
        }
        BotMessage authoritative = persisted.getMessages().get(
                persisted.getMessages().size() - 1
        );
        BotMessageDTO result = toMessageDto(authoritative);
        return new BotQuestionResultDTO(
                result, result.getAnswerStatus() == BotAnswerStatus.ANSWERED
        );
    }

    // COMPATIBILITY-ONLY: The UML signature has no authenticated connection context.
    public BotMessage sendQuestionToBot(int studentId, int courseId, String questionText) {
        throw new IllegalStateException(AUTHENTICATED_CONTEXT_REQUIRED);
    }

    // COMPATIBILITY-ONLY: The UML signature exposes entities and lacks authentication.
    public List<BotMessage> getBotHistory(int studentId, int courseId) {
        throw new IllegalStateException(AUTHENTICATED_CONTEXT_REQUIRED);
    }

    // COMPATIBILITY-ONLY: Access must be evaluated through authenticated APIs.
    public boolean validateCourseAccess(int studentId, int courseId) {
        throw new IllegalStateException(AUTHENTICATED_CONTEXT_REQUIRED);
    }

    // COMPATIBILITY-ONLY: Student-specific Bot state is not part of the approved model.
    public void deactivateBotForStudent(int studentId) {
        throw new IllegalStateException(AUTHENTICATED_CONTEXT_REQUIRED);
    }

    // COMPATIBILITY-ONLY: Student-specific Bot state is not part of the approved model.
    public void activateBotForStudent(int studentId) {
        throw new IllegalStateException(AUTHENTICATED_CONTEXT_REQUIRED);
    }

    private List<BotSource> prepareQuestionSources(
            int authenticatedUserId, CourseBot bot,
            List<BotQuestionVersionReference> references
    ) {
        Set<String> identities = new HashSet<>();
        Set<String> existing = new HashSet<>();
        for (BotSource source : bot.getSources()) {
            if (source.getStatus() == BotSourceStatus.ACTIVE
                    && source.getQuestionId() != null) {
                existing.add(source.getQuestionId() + ":" + source.getQuestionVersionNo());
            }
        }
        LocalDateTime timestamp = now();
        List<BotSource> prepared = new ArrayList<>(references.size());
        for (BotQuestionVersionReference reference : references) {
            String identity = reference.getQuestionId() + ":"
                    + reference.getQuestionVersionNo();
            if (!identities.add(identity) || existing.contains(identity)) {
                throw new IllegalStateException("Bot source already exists");
            }
            QuestionVersionDTO version = questionRepository.findVersionsForTeacher(
                            authenticatedUserId, reference.getQuestionId()
                    ).stream()
                    .filter(candidate -> candidate.getVersionNo()
                            == reference.getQuestionVersionNo())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Question not found or access denied"
                    ));
            if (version.getCourseId() != bot.getCourseId()) {
                throw new IllegalStateException("Question not found or access denied");
            }
            Question question = questionRepository.findEntityVersionForTeacher(
                    authenticatedUserId, reference.getQuestionId(),
                    reference.getQuestionVersionNo()
            ).orElseThrow(() -> new IllegalStateException(
                    "Question not found or access denied"
            ));
            ExtractedBotSource extracted = sourceExtractor.extractQuestionBankSource(
                    "Question " + reference.getQuestionId() + " version "
                            + reference.getQuestionVersionNo(),
                    questionStudyText(question)
            );
            prepared.add(newSource(
                    authenticatedUserId, bot.getBotId(), extracted,
                    reference.getQuestionId(), reference.getQuestionVersionNo(), timestamp
            ));
        }
        return List.copyOf(prepared);
    }

    private BotSourceDTO persistExtractedSource(
            int authenticatedUserId, CourseBot bot, ExtractedBotSource extracted,
            Integer questionId, Integer versionNo
    ) {
        BotSource source = newSource(
                authenticatedUserId, bot.getBotId(), extracted,
                questionId, versionNo, now()
        );
        return toSourceDto(courseBotRepository.addSource(authenticatedUserId, source));
    }

    private static BotSource newSource(
            int authenticatedUserId, int botId, ExtractedBotSource extracted,
            Integer questionId, Integer versionNo, LocalDateTime timestamp
    ) {
        return BotSource.create(
                botId, extracted.getSourceType(), extracted.getDisplayName(),
                extracted.getExtractedText(), extracted.getContentSha256(),
                questionId, versionNo, authenticatedUserId, null, timestamp
        );
    }

    private CourseBot assignedBot(int authenticatedUserId, int botId) {
        if (botId <= 0) {
            throw new IllegalArgumentException("Bot ID must be positive");
        }
        return courseBotRepository.findAssignedToTeacher(authenticatedUserId).stream()
                .filter(bot -> bot.getBotId() == botId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Course Bot not found or access denied"
                ));
    }

    private CourseBot activeStudentBot(int authenticatedUserId, int courseId) {
        return courseBotRepository.findActiveByCourseForStudent(
                authenticatedUserId, courseId
        ).orElseThrow(() -> new IllegalStateException(
                "Course Bot not found or access denied"
        ));
    }

    private void requireTeacher(int userId) {
        requireDependencies();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Teacher access required"));
        if (!user.isActive()) {
            throw new IllegalStateException("User account is inactive");
        }
        if (user.getRole() != UserRole.TEACHER
                && user.getRole() != UserRole.COORDINATOR) {
            throw new IllegalStateException("Teacher access required");
        }
    }

    private void requireStudent(int userId) {
        requireDependencies();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Student access required"));
        if (!user.isActive()) {
            throw new IllegalStateException("User account is inactive");
        }
        if (user.getRole() != UserRole.STUDENT) {
            throw new IllegalStateException("Student access required");
        }
    }

    private void requireBotAvailable(int studentId, int courseId) {
        if (isLockedOut(studentId, courseId)) {
            throw new IllegalStateException(
                    "Course Bot is unavailable during an active exam"
            );
        }
    }

    private boolean isLockedOut(int studentId, int courseId) {
        return examSubmissionRepository.existsInProgressForStudentAndCourse(
                studentId, courseId
        );
    }

    private String courseName(int courseId) {
        return courseRepository.findById(courseId)
                .map(CourseSummaryDTO::getCourseName)
                .orElseThrow(() -> new IllegalStateException(
                        "Course not found or access denied"
                ));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String nextProviderSubject() {
        String value;
        try {
            value = providerSubjectSupplier.get();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to create Bot conversation", exception);
        }
        if (value == null) {
            throw new IllegalStateException("Unable to create Bot conversation");
        }
        return value;
    }

    private void requireDependencies() {
        if (courseBotRepository == null || conversationRepository == null
                || questionRepository == null || courseRepository == null
                || userRepository == null || examSubmissionRepository == null
                || sourceExtractor == null || externalBotSystem == null
                || clock == null || providerSubjectSupplier == null) {
            throw new IllegalStateException(
                    "Course Bot service dependencies are not configured"
            );
        }
    }

    private static CourseBotSummaryDTO toSummary(CourseBot bot, String courseName) {
        int activeSources = (int) bot.getSources().stream()
                .filter(source -> source.getStatus() == BotSourceStatus.ACTIVE)
                .count();
        return new CourseBotSummaryDTO(
                bot.getBotId(), bot.getCourseId(), bot.getName(), courseName,
                bot.getStatus(), activeSources, bot.getCreatedAt(), bot.getUpdatedAt()
        );
    }

    private static BotSourceDTO toSourceDto(BotSource source) {
        return new BotSourceDTO(
                source.getSourceId(), source.getBotId(), source.getSourceType(),
                source.getDisplayName(), source.getQuestionId(),
                source.getQuestionVersionNo(), source.getStatus(),
                source.getCreatedAt(), source.getRemovedAt()
        );
    }

    private static BotMessageDTO toMessageDto(BotMessage message) {
        return new BotMessageDTO(
                message.getMessageId(), message.getSequenceNumber(),
                message.getQuestionText(), message.getAnswerText(),
                message.getAnswerStatus(), message.getCreatedAt()
        );
    }

    private static String normalizeQuestion(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Bot question is required");
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String questionStudyText(Question question) {
        List<AnswerOption> options = question.getAnswerOptions();
        if (options.size() != 4 || question.getCorrectOptionNumber() < 1
                || question.getCorrectOptionNumber() > 4) {
            throw new IllegalStateException("Question not found or access denied");
        }
        StringBuilder text = new StringBuilder()
                .append("Question: ").append(question.getContent()).append('\n')
                .append("Topic: ").append(question.getTopic()).append('\n');
        String correctText = null;
        for (AnswerOption option : options) {
            text.append("Option ").append(option.getOptionId()).append(": ")
                    .append(option.getOptionText()).append('\n');
            if (option.getOptionId() == question.getCorrectOptionNumber()) {
                correctText = option.getOptionText();
            }
        }
        if (correctText == null) {
            throw new IllegalStateException("Question not found or access denied");
        }
        return text.append("Correct option: ")
                .append(question.getCorrectOptionNumber()).append('\n')
                .append("Correct answer: ").append(correctText).toString();
    }
}
