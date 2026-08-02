package hsts.server.control;

import hsts.common.CourseSummaryDTO;
import hsts.common.CreateExamPayload;
import hsts.common.CreateQuestionPayload;
import hsts.common.ExamDTO;
import hsts.common.ExamQuestionSelectionPayload;
import hsts.common.ExamSummaryDTO;
import hsts.common.ExamVersionPayload;
import hsts.common.GenerateExamBreakdownPayload;
import hsts.common.GenerateExamPayload;
import hsts.common.QuestionCriterion;
import hsts.common.QuestionDTO;
import hsts.common.QuestionFilterPayload;
import hsts.common.QuestionIllustrationDTO;
import hsts.common.QuestionIllustrationUploadPayload;
import hsts.common.QuestionVersionDTO;
import hsts.common.RejectExamPayload;
import hsts.common.UpdateExamPayload;
import hsts.common.UpdateQuestionPayload;
import hsts.common.type.DifficultyLevel;
import hsts.common.type.ExamStatus;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import hsts.common.type.QuestionIllustrationChange;
import hsts.common.type.UserRole;
import hsts.server.entity.AnswerOption;
import hsts.server.entity.Exam;
import hsts.server.entity.ExamQuestion;
import hsts.server.entity.Question;
import hsts.server.entity.QuestionIllustration;
import hsts.server.entity.User;
import hsts.server.repository.CourseRepository;
import hsts.server.repository.ExamRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.time.LocalDateTime;

public class ExamManagementService {
    private static final String MULTIPLE_CHOICE = "MULTIPLE_CHOICE";

    private DatabaseService databaseService;
    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ExamRepository examRepository;

    public ExamManagementService(QuestionRepository questionRepository) {
        this(questionRepository, null, null, null);
    }

    public ExamManagementService(QuestionRepository questionRepository,
                                 CourseRepository courseRepository,
                                 UserRepository userRepository) {
        this(questionRepository, courseRepository, userRepository, null);
    }

    public ExamManagementService(QuestionRepository questionRepository,
                                 CourseRepository courseRepository,
                                 UserRepository userRepository,
                                 ExamRepository examRepository) {
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.examRepository = examRepository;
    }

    public List<QuestionDTO> getAllQuestions() {
        return questionRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public QuestionDTO getQuestionById(int questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        return toDto(question);
    }

    public QuestionDTO getQuestionById(int authenticatedUserId, int questionId) {
        requireQuestionBankDependencies();
        authorizeQuestionManager(authenticatedUserId);
        return questionRepository.findCurrentByIdForTeacher(authenticatedUserId, questionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Question not found: " + questionId
                ));
    }

    public List<CourseSummaryDTO> getCoursesForTeacher(int authenticatedUserId) {
        requireQuestionBankDependencies();
        authorizeQuestionManager(authenticatedUserId);
        return courseRepository.findAssignedToTeacher(authenticatedUserId);
    }

    public List<QuestionDTO> getQuestions(int authenticatedUserId,
                                          QuestionFilterPayload filter) {
        requireQuestionBankDependencies();
        authorizeQuestionManager(authenticatedUserId);

        if (filter != null && filter.getCourseId() != null) {
            int courseId = filter.getCourseId();
            if (courseId <= 0) {
                throw new IllegalArgumentException("Course ID must be positive");
            }
            requireCourseAssignment(authenticatedUserId, courseId);
        }

        return questionRepository.findCurrentForTeacher(authenticatedUserId, filter);
    }

    public QuestionDTO createQuestion(int authenticatedUserId,
                                      CreateQuestionPayload payload) {
        requireQuestionBankDependencies();
        validateCreateQuestionPayload(payload);
        authorizeQuestionManager(authenticatedUserId);
        requireCourseAssignment(authenticatedUserId, payload.getCourseId());

        QuestionIllustration illustration = createIllustration(
                payload.getIllustrationUpload()
        );

        Question question = Question.rehydrate(
                0,
                payload.getContent().trim(),
                QuestionType.MULTIPLE_CHOICE,
                payload.getDifficulty(),
                QuestionStatus.ACTIVE,
                null,
                null,
                normalizeText(payload.getTopic(), "General"),
                "",
                illustration,
                answerOptions(
                        payload.getAnswerOption1().trim(),
                        payload.getAnswerOption2().trim(),
                        payload.getAnswerOption3().trim(),
                        payload.getAnswerOption4().trim(),
                        payload.getCorrectOptionNumber()
                )
        );

        int questionId = questionRepository.create(
                authenticatedUserId,
                payload.getCourseId(),
                question
        );
        return questionRepository.findCurrentByIdForTeacher(authenticatedUserId, questionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Created question could not be reloaded: " + questionId
                ));
    }

    public Question createQuestion(int teacherId, int courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void updateQuestion(int questionId, String content) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public QuestionDTO updateQuestion(int authenticatedUserId,
                                      UpdateQuestionPayload payload) {
        requireQuestionBankDependencies();
        authorizeQuestionManager(authenticatedUserId);
        validateQuestionPayload(payload);
        validateIllustrationChange(payload);

        int questionId = payload.getQuestionId();
        Question question = requireCurrentQuestionEntity(
                authenticatedUserId,
                questionId
        );
        question.updateContent(payload.getContent().trim());
        question.setTopic(normalizeText(payload.getTopic(), "General"));
        question.setDifficulty(normalizeText(payload.getDifficulty(), "EASY"));
        question.setIllustrationPath("");
        applyIllustrationChange(question, payload);
        question.setAnswerOption1(payload.getAnswerOption1().trim());
        question.setAnswerOption2(payload.getAnswerOption2().trim());
        question.setAnswerOption3(payload.getAnswerOption3().trim());
        question.setAnswerOption4(payload.getAnswerOption4().trim());
        question.setCorrectOptionNumber(payload.getCorrectOptionNumber());

        questionRepository.updateWithNewVersion(
                authenticatedUserId,
                questionId,
                payload.getExpectedVersionNo(),
                question
        );
        return getQuestionById(authenticatedUserId, questionId);
    }

    public QuestionDTO activateQuestion(int authenticatedUserId, int questionId) {
        return updateQuestionStatus(authenticatedUserId, questionId, QuestionStatus.ACTIVE);
    }

    public QuestionDTO deactivateQuestion(int authenticatedUserId, int questionId) {
        return updateQuestionStatus(authenticatedUserId, questionId, QuestionStatus.INACTIVE);
    }

    /**
     * Hides a question from the bank. The question, its versions and every exam
     * already containing it are left intact, so graded submissions still show
     * exactly what each student was asked.
     */
    public void deleteQuestion(int authenticatedUserId, int questionId) {
        requireQuestionBankDependencies();
        authorizeQuestionManager(authenticatedUserId);

        if (!questionRepository.softDelete(authenticatedUserId, questionId)) {
            throw new IllegalArgumentException(
                    "Question " + questionId + " is not available to delete"
            );
        }
    }

    public List<QuestionVersionDTO> getQuestionHistory(int authenticatedUserId,
                                                       int questionId) {
        requireQuestionBankDependencies();
        authorizeQuestionManager(authenticatedUserId);

        List<QuestionVersionDTO> versions = questionRepository.findVersionsForTeacher(
                authenticatedUserId,
                questionId
        );
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("Question not found: " + questionId);
        }
        return versions;
    }

    public List<ExamSummaryDTO> getMyExams(int authenticatedUserId) {
        authorizeExamManager(authenticatedUserId);
        requireExamRepository();
        return examRepository.findCreatedByTeacher(authenticatedUserId);
    }

    public List<ExamSummaryDTO> getPendingExams(int authenticatedUserId) {
        authorizeCoordinator(authenticatedUserId);
        requireExamRepository();
        return examRepository.findPendingForCoordinator(authenticatedUserId);
    }

    public ExamDTO getExamForTeacher(int authenticatedUserId, int examId) {
        authorizeExamManager(authenticatedUserId);
        requireExamRepository();
        return examRepository.findByIdForTeacher(authenticatedUserId, examId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exam not found: " + examId
                ));
    }

    public ExamDTO getExamForCoordinator(int authenticatedUserId, int examId) {
        authorizeCoordinator(authenticatedUserId);
        requireExamRepository();
        return examRepository.findByIdForCoordinator(authenticatedUserId, examId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exam not found: " + examId
                ));
    }

    public ExamDTO createExam(int authenticatedUserId, CreateExamPayload payload) {
        authorizeExamManager(authenticatedUserId);
        requireExamRepository();
        validateCreateExamPayload(payload);
        List<ExamQuestion> examQuestions = loadExamQuestionSnapshots(
                authenticatedUserId,
                payload.getCourseId(),
                payload.getQuestions()
        );

        Exam exam = Exam.createDraft(
                payload.getCourseId(),
                authenticatedUserId,
                payload.getTitle().trim(),
                payload.getDurationMinutes(),
                normalizeText(payload.getTeacherNotes(), ""),
                payload.getStudentInstructions().trim(),
                examQuestions
        );

        int examId = examRepository.create(authenticatedUserId, exam);
        return examRepository.findByIdForTeacher(authenticatedUserId, examId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exam not found: " + examId
                ));
    }

    /**
     * Generates an exam from a breakdown of question criteria.
     *
     * <p>Each criterion contributes its own questions, so a paper can combine
     * several topics at several difficulty levels. A question chosen for one
     * criterion is never reused for another, and the finished selection is
     * shuffled so the paper is not grouped by topic.</p>
     */
    public ExamDTO generateAutomaticExamFromBreakdown(
            int authenticatedUserId,
            GenerateExamBreakdownPayload payload
    ) {
        authorizeExamManager(authenticatedUserId);
        requireAutomaticExamRepositories();
        if (payload == null) {
            throw new IllegalArgumentException("Automatic exam data is missing");
        }

        validateExamPresentation(
                payload.getTitle(),
                payload.getDurationMinutes(),
                payload.getStudentInstructions()
        );

        List<QuestionCriterion> criteria = payload.getCriteria();
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("At least one question criterion is required");
        }
        for (QuestionCriterion criterion : criteria) {
            if (isBlank(criterion.getTopic())) {
                throw new IllegalArgumentException("Topic is required");
            }
            if (criterion.getDifficulty() == null) {
                throw new IllegalArgumentException("Difficulty is required");
            }
            if (criterion.getQuestionCount() <= 0) {
                throw new IllegalArgumentException("Question count must be positive");
            }
        }

        List<QuestionDTO> selectedQuestions = new ArrayList<>();
        Set<Integer> usedQuestionIds = new HashSet<>();
        for (QuestionCriterion criterion : criteria) {
            QuestionFilterPayload filter = new QuestionFilterPayload(
                    payload.getCourseId(),
                    null,
                    criterion.getTopic().trim(),
                    criterion.getDifficulty(),
                    QuestionStatus.ACTIVE
            );
            List<QuestionDTO> matchingQuestions =
                    questionRepository.findCurrentForTeacher(authenticatedUserId, filter);

            List<QuestionDTO> availableQuestions = new ArrayList<>();
            Set<Integer> seenForCriterion = new HashSet<>();
            for (QuestionDTO question : matchingQuestions) {
                if (usedQuestionIds.contains(question.getQuestionId())) {
                    continue;
                }
                if (seenForCriterion.add(question.getQuestionId())) {
                    availableQuestions.add(question);
                }
            }

            if (availableQuestions.size() < criterion.getQuestionCount()) {
                throw new IllegalArgumentException(
                        "Not enough matching questions for " + criterion.describe()
                );
            }

            Collections.shuffle(availableQuestions, ThreadLocalRandom.current());
            for (int index = 0; index < criterion.getQuestionCount(); index++) {
                QuestionDTO chosen = availableQuestions.get(index);
                usedQuestionIds.add(chosen.getQuestionId());
                selectedQuestions.add(chosen);
            }
        }

        Collections.shuffle(selectedQuestions, ThreadLocalRandom.current());
        List<ExamQuestionSelectionPayload> selections = createAutomaticSelections(
                selectedQuestions,
                selectedQuestions.size()
        );

        CreateExamPayload createPayload = new CreateExamPayload(
                payload.getCourseId(),
                payload.getTitle().trim(),
                payload.getDurationMinutes(),
                normalizeText(payload.getTeacherNotes(), ""),
                payload.getStudentInstructions().trim(),
                selections
        );
        return createExam(authenticatedUserId, createPayload);
    }

    public ExamDTO generateAutomaticExam(int authenticatedUserId,
                                         GenerateExamPayload payload) {
        authorizeExamManager(authenticatedUserId);
        requireAutomaticExamRepositories();
        if (payload == null) {
            throw new IllegalArgumentException("Automatic exam data is missing");
        }

        validateExamPresentation(
                payload.getTitle(),
                payload.getDurationMinutes(),
                payload.getStudentInstructions()
        );
        if (isBlank(payload.getTopic())) {
            throw new IllegalArgumentException("Topic is required");
        }
        if (payload.getDifficulty() == null) {
            throw new IllegalArgumentException("Difficulty is required");
        }
        if (payload.getQuestionCount() <= 0) {
            throw new IllegalArgumentException("Question count must be positive");
        }

        QuestionFilterPayload filter = new QuestionFilterPayload(
                payload.getCourseId(),
                null,
                payload.getTopic().trim(),
                payload.getDifficulty(),
                QuestionStatus.ACTIVE
        );
        List<QuestionDTO> matchingQuestions = questionRepository.findCurrentForTeacher(
                authenticatedUserId,
                filter
        );
        List<QuestionDTO> distinctQuestions = new ArrayList<>(matchingQuestions.size());
        Set<Integer> distinctQuestionIds = new HashSet<>();
        for (QuestionDTO question : matchingQuestions) {
            if (distinctQuestionIds.add(question.getQuestionId())) {
                distinctQuestions.add(question);
            }
        }
        if (distinctQuestions.size() < payload.getQuestionCount()) {
            throw new IllegalArgumentException("Not enough matching questions");
        }

        List<QuestionDTO> shuffledQuestions = new ArrayList<>(distinctQuestions);
        Collections.shuffle(shuffledQuestions, ThreadLocalRandom.current());
        List<ExamQuestionSelectionPayload> selections = createAutomaticSelections(
                shuffledQuestions,
                payload.getQuestionCount()
        );

        CreateExamPayload createPayload = new CreateExamPayload(
                payload.getCourseId(),
                payload.getTitle().trim(),
                payload.getDurationMinutes(),
                normalizeText(payload.getTeacherNotes(), ""),
                payload.getStudentInstructions().trim(),
                selections
        );
        return createExam(authenticatedUserId, createPayload);
    }

    public ExamDTO updateExam(int authenticatedUserId, UpdateExamPayload payload) {
        authorizeExamManager(authenticatedUserId);
        requireExamRepository();
        if (payload == null) {
            throw new IllegalArgumentException("Exam update data is missing");
        }

        Exam currentExam = requireCurrentExamForTeacher(
                authenticatedUserId,
                payload.getExamId()
        );
        validateExamData(
                payload.getTitle(),
                payload.getDurationMinutes(),
                payload.getStudentInstructions(),
                payload.getQuestions()
        );
        List<ExamQuestion> examQuestions = loadExamQuestionSnapshots(
                authenticatedUserId,
                currentExam.getCourseId(),
                payload.getQuestions()
        );

        requireExpectedExamVersion(currentExam, payload.getExpectedVersionNo());
        Exam updatedExam = createUpdatedDraft(currentExam, payload, examQuestions);
        examRepository.updateWithNewVersion(
                authenticatedUserId,
                payload.getExamId(),
                payload.getExpectedVersionNo(),
                updatedExam
        );
        return getExamForTeacher(authenticatedUserId, payload.getExamId());
    }

    public ExamDTO submitExamForApproval(int authenticatedUserId,
                                         ExamVersionPayload payload) {
        authorizeExamManager(authenticatedUserId);
        requireExamRepository();
        if (payload == null) {
            throw new IllegalArgumentException("Exam version data is missing");
        }

        Exam exam = requireCurrentExamForTeacher(authenticatedUserId, payload.getExamId());
        requireExpectedExamVersion(exam, payload.getExpectedVersionNo());
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new IllegalStateException("Exam is not a draft");
        }
        exam.submitForApproval();
        boolean submitted = examRepository.persistSubmissionForApproval(
                authenticatedUserId,
                exam
        );
        if (!submitted) {
            throw new IllegalArgumentException("Exam not found: " + payload.getExamId());
        }
        return getExamForTeacher(authenticatedUserId, payload.getExamId());
    }

    public ExamDTO approveExam(int authenticatedCoordinatorId,
                               ExamVersionPayload payload) {
        authorizeCoordinator(authenticatedCoordinatorId);
        requireExamRepository();
        if (payload == null) {
            throw new IllegalArgumentException("Exam version data is missing");
        }

        Exam exam = requireCurrentExamForCoordinator(
                authenticatedCoordinatorId,
                payload.getExamId()
        );
        requireExpectedExamVersion(exam, payload.getExpectedVersionNo());
        exam.approve(authenticatedCoordinatorId);
        boolean approved = examRepository.persistApproval(
                authenticatedCoordinatorId,
                exam
        );
        if (!approved) {
            throw new IllegalArgumentException("Exam not found: " + payload.getExamId());
        }
        return getExamForCoordinator(authenticatedCoordinatorId, payload.getExamId());
    }

    public ExamDTO rejectExam(int authenticatedCoordinatorId,
                              RejectExamPayload payload) {
        authorizeCoordinator(authenticatedCoordinatorId);
        requireExamRepository();
        if (payload == null) {
            throw new IllegalArgumentException("Exam rejection data is missing");
        }
        if (isBlank(payload.getReason())) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        Exam exam = requireCurrentExamForCoordinator(
                authenticatedCoordinatorId,
                payload.getExamId()
        );
        requireExpectedExamVersion(exam, payload.getExpectedVersionNo());
        exam.reject(authenticatedCoordinatorId, payload.getReason().trim());
        boolean rejected = examRepository.persistRejection(
                authenticatedCoordinatorId,
                exam
        );
        if (!rejected) {
            throw new IllegalArgumentException("Exam not found: " + payload.getExamId());
        }
        return getExamForCoordinator(authenticatedCoordinatorId, payload.getExamId());
    }

    public void deactivateQuestion(int questionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void activateQuestion(int questionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List getActiveQuestionsByCourse(int courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List getQuestionsByDifficulty(DifficultyLevel difficulty) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List getQuestionsByTopic(String topic) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public List getQuestionsBySubject(int subjectId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void addAnswerOption(int questionId, String optionText, boolean isCorrect) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void removeAnswerOption(int questionId, int optionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public Exam createExam(int teacherId, int courseId, int durationMinutes) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public Exam buildAutomaticExam(int courseId, List topics, DifficultyLevel difficulty, int numberOfQuestions) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void addQuestionToExam(int examId, int questionId, double score) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void removeQuestionFromExam(int examId, int questionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void updateQuestionScore(int questionId, double score) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void setExamDuration(int examId, int durationMinutes) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void sendExamForApproval(int examId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void approveExam(int coordinatorId, int examId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void rejectExam(int coordinatorId, int examId, String reason) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void publishExam(int examId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void editExamNotes(int examId, String teacherNotes, String studentNotes) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void getTopicsByCourse(String courseId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean validateExam(int examId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void addExamToDrawer(Object examId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    private void validateQuestionPayload(UpdateQuestionPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Question update data is missing");
        }
        if (isBlank(payload.getContent())) {
            throw new IllegalArgumentException("Question content cannot be empty");
        }
        if (isBlank(payload.getAnswerOption1()) || isBlank(payload.getAnswerOption2()) ||
                isBlank(payload.getAnswerOption3()) || isBlank(payload.getAnswerOption4())) {
            throw new IllegalArgumentException("All four answer options are required");
        }
        if (payload.getCorrectOptionNumber() < 1 || payload.getCorrectOptionNumber() > 4) {
            throw new IllegalArgumentException("Correct answer number must be between 1 and 4");
        }
        String status = normalizeText(payload.getStatus(), "ACTIVE");
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            throw new IllegalArgumentException("Question status must be ACTIVE or INACTIVE");
        }
    }

    private User authorizeQuestionManager(int userId) {
        User user = requireActiveUser(userId);
        if (user.getRole() != UserRole.TEACHER && user.getRole() != UserRole.COORDINATOR) {
            throw new IllegalStateException(
                    "Question management requires teacher or coordinator role"
            );
        }
        return user;
    }

    private User authorizeExamManager(int userId) {
        if (userRepository == null) {
            requireExamRepository();
        }
        return authorizeQuestionManager(userId);
    }

    private User authorizeCoordinator(int userId) {
        if (userRepository == null) {
            requireExamRepository();
        }
        User user = requireActiveUser(userId);
        if (user.getRole() != UserRole.COORDINATOR) {
            throw new IllegalStateException("Only coordinators can review exams");
        }
        return user;
    }

    private User requireActiveUser(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (!user.isActive()) {
            throw new IllegalStateException("User account is blocked");
        }
        return user;
    }

    private void requireCourseAssignment(int userId, int courseId) {
        if (!courseRepository.isAssignedToTeacher(userId, courseId)) {
            throw new IllegalStateException("User is not assigned to course: " + courseId);
        }
    }

    private QuestionDTO updateQuestionStatus(int authenticatedUserId, int questionId,
                                             QuestionStatus status) {
        requireQuestionBankDependencies();
        authorizeQuestionManager(authenticatedUserId);

        Question question = requireCurrentQuestionEntity(
                authenticatedUserId,
                questionId
        );
        if (status == QuestionStatus.ACTIVE) {
            question.activate();
        } else {
            question.deactivate();
        }

        boolean updated = questionRepository.updateStatusForTeacher(
                authenticatedUserId,
                questionId,
                question.getQuestionStatus()
        );
        if (!updated) {
            throw new IllegalArgumentException("Question not found: " + questionId);
        }
        return getQuestionById(authenticatedUserId, questionId);
    }

    private Question requireCurrentQuestionEntity(int authenticatedUserId,
                                                  int questionId) {
        return questionRepository.findCurrentEntityByIdForTeacher(
                authenticatedUserId,
                questionId
        ).orElseThrow(() -> new IllegalArgumentException(
                "Question not found: " + questionId
        ));
    }

    private List<AnswerOption> answerOptions(String option1, String option2,
                                             String option3, String option4,
                                             int correctOptionNumber) {
        return List.of(
                new AnswerOption(1, option1, correctOptionNumber == 1),
                new AnswerOption(2, option2, correctOptionNumber == 2),
                new AnswerOption(3, option3, correctOptionNumber == 3),
                new AnswerOption(4, option4, correctOptionNumber == 4)
        );
    }

    private void validateCreateQuestionPayload(CreateQuestionPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Question data is required");
        }
        if (payload.getCourseId() <= 0) {
            throw new IllegalArgumentException("Course is required");
        }
        if (isBlank(payload.getContent())) {
            throw new IllegalArgumentException("Question content cannot be empty");
        }
        if (payload.getDifficulty() == null) {
            throw new IllegalArgumentException("Question difficulty is required");
        }
        if (isBlank(payload.getAnswerOption1()) || isBlank(payload.getAnswerOption2())
                || isBlank(payload.getAnswerOption3()) || isBlank(payload.getAnswerOption4())) {
            throw new IllegalArgumentException("All four answer options are required");
        }
        if (payload.getCorrectOptionNumber() < 1 || payload.getCorrectOptionNumber() > 4) {
            throw new IllegalArgumentException("Correct answer number must be between 1 and 4");
        }
    }

    private void validateCreateExamPayload(CreateExamPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Exam creation data is missing");
        }
        validateExamData(
                payload.getTitle(),
                payload.getDurationMinutes(),
                payload.getStudentInstructions(),
                payload.getQuestions()
        );
    }

    private void validateExamData(String title, int durationMinutes,
                                  String studentInstructions,
                                  List<ExamQuestionSelectionPayload> questions) {
        validateExamPresentation(title, durationMinutes, studentInstructions);

        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("At least one question is required");
        }

        validateQuestionOrder(questions);

        Set<Integer> questionIds = new HashSet<>();
        for (ExamQuestionSelectionPayload question : questions) {
            if (!questionIds.add(question.getQuestionId())) {
                throw new IllegalArgumentException(
                        "Duplicate question: " + question.getQuestionId()
                );
            }
        }

        BigDecimal totalScore = BigDecimal.ZERO;
        for (ExamQuestionSelectionPayload question : questions) {
            double score = question.getScore();
            if (!Double.isFinite(score) || score <= 0) {
                throw new IllegalArgumentException(
                        "Question score must be positive and finite"
                );
            }
            totalScore = totalScore.add(BigDecimal.valueOf(score));
        }

        if (totalScore.compareTo(new BigDecimal("100.00")) != 0) {
            throw new IllegalArgumentException("Exam total score must equal 100");
        }
    }

    private void validateExamPresentation(String title, int durationMinutes,
                                          String studentInstructions) {
        if (isBlank(title)) {
            throw new IllegalArgumentException("Exam title is required");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Exam duration must be positive");
        }
        if (isBlank(studentInstructions)) {
            throw new IllegalArgumentException("Student instructions are required");
        }
    }

    private List<ExamQuestionSelectionPayload> createAutomaticSelections(
            List<QuestionDTO> shuffledQuestions,
            int questionCount) {
        BigDecimal totalScore = new BigDecimal("100.00");
        BigDecimal regularScore = totalScore.divide(
                BigDecimal.valueOf(questionCount),
                2,
                RoundingMode.DOWN
        );
        BigDecimal finalScore = totalScore.subtract(
                regularScore.multiply(BigDecimal.valueOf(questionCount - 1L))
        );

        List<ExamQuestionSelectionPayload> selections = new ArrayList<>(questionCount);
        for (int index = 0; index < questionCount; index++) {
            QuestionDTO question = shuffledQuestions.get(index);
            BigDecimal score = index == questionCount - 1 ? finalScore : regularScore;
            selections.add(new ExamQuestionSelectionPayload(
                    question.getQuestionId(),
                    question.getVersionNo(),
                    index + 1,
                    score.doubleValue()
            ));
        }
        return selections;
    }

    private List<ExamQuestion> loadExamQuestionSnapshots(
            int authenticatedUserId,
            int courseId,
            List<ExamQuestionSelectionPayload> selections
    ) {
        validateExamQuestions(authenticatedUserId, courseId, selections);

        List<ExamQuestion> examQuestions = new ArrayList<>(selections.size());
        for (ExamQuestionSelectionPayload selection : selections) {
            int questionId = selection.getQuestionId();
            Question question = questionRepository.findCurrentEntityByIdForTeacher(
                    authenticatedUserId,
                    questionId
            ).orElseThrow(() -> unavailableQuestion(questionId));

            QuestionDTO confirmedQuestion = questionRepository.findCurrentByIdForTeacher(
                    authenticatedUserId,
                    questionId
            ).orElseThrow(() -> unavailableQuestion(questionId));
            if (question.getQuestionId() != questionId
                    || question.getQuestionStatus() != QuestionStatus.ACTIVE
                    || confirmedQuestion.getCourseId() != courseId
                    || !QuestionStatus.ACTIVE.name().equals(confirmedQuestion.getStatus())
                    || confirmedQuestion.getVersionNo()
                    != selection.getQuestionVersionNo()) {
                throw unavailableQuestion(questionId);
            }

            examQuestions.add(ExamQuestion.select(
                    selection.getQuestionVersionNo(),
                    selection.getOrderNumber(),
                    BigDecimal.valueOf(selection.getScore()),
                    question
            ));
        }
        return examQuestions;
    }

    private Exam createUpdatedDraft(Exam currentExam,
                                    UpdateExamPayload payload,
                                    List<ExamQuestion> examQuestions) {
        if (currentExam.getStatus() == ExamStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Pending exam cannot be edited");
        }

        String title = payload.getTitle().trim();
        String teacherNotes = normalizeText(payload.getTeacherNotes(), "");
        String studentInstructions = payload.getStudentInstructions().trim();
        if (currentExam.getStatus() == ExamStatus.APPROVED
                || currentExam.getStatus() == ExamStatus.REJECTED) {
            currentExam.startNewDraftVersion(
                    title,
                    payload.getDurationMinutes(),
                    teacherNotes,
                    studentInstructions,
                    examQuestions
            );
            return currentExam;
        }

        currentExam.updateMetadata(
                title,
                payload.getDurationMinutes(),
                teacherNotes,
                studentInstructions
        );
        for (ExamQuestion existingQuestion : currentExam.getExamQuestions()) {
            currentExam.removeExamQuestion(existingQuestion.getQuestionId());
        }
        for (ExamQuestion examQuestion : examQuestions) {
            currentExam.addExamQuestion(examQuestion);
        }

        return Exam.rehydrate(
                currentExam.getExamId(),
                currentExam.getExamCode(),
                currentExam.getCourseId(),
                currentExam.getCreatedByUserId(),
                currentExam.getCurrentVersionNo() + 1,
                currentExam.getTitle(),
                currentExam.getDurationMinutes(),
                currentExam.getTeacherNotes(),
                currentExam.getStudentInstructions(),
                ExamStatus.DRAFT,
                currentExam.getCreatedAt(),
                currentExam.getUpdatedAt(),
                null,
                null,
                null,
                null,
                currentExam.getExamQuestions()
        );
    }

    private Exam requireCurrentExamForTeacher(int authenticatedUserId, int examId) {
        return examRepository.findCurrentEntityForTeacher(authenticatedUserId, examId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exam not found: " + examId
                ));
    }

    private Exam requireCurrentExamForCoordinator(int authenticatedUserId, int examId) {
        return examRepository.findCurrentEntityForCoordinator(authenticatedUserId, examId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exam not found: " + examId
                ));
    }

    private void requireExpectedExamVersion(Exam exam, int expectedVersionNo) {
        if (exam.getCurrentVersionNo() != expectedVersionNo) {
            throw new IllegalStateException("Exam version conflict");
        }
    }

    private void validateQuestionOrder(List<ExamQuestionSelectionPayload> questions) {
        boolean[] seenOrders = new boolean[questions.size() + 1];
        for (ExamQuestionSelectionPayload question : questions) {
            int orderNumber = question.getOrderNumber();
            if (orderNumber <= 0 || orderNumber > questions.size()
                    || seenOrders[orderNumber]) {
                throw new IllegalArgumentException(
                        "Question order must start at 1 and be contiguous"
                );
            }
            seenOrders[orderNumber] = true;
        }
    }

    private void validateExamQuestions(int authenticatedUserId, int courseId,
                                       List<ExamQuestionSelectionPayload> questions) {
        for (ExamQuestionSelectionPayload selection : questions) {
            QuestionDTO question = questionRepository.findCurrentByIdForTeacher(
                    authenticatedUserId,
                    selection.getQuestionId()
            ).orElseThrow(() -> unavailableQuestion(selection.getQuestionId()));

            if (question.getCourseId() != courseId
                    || !QuestionStatus.ACTIVE.name().equals(question.getStatus())
                    || question.getVersionNo() != selection.getQuestionVersionNo()) {
                throw unavailableQuestion(selection.getQuestionId());
            }
        }
    }

    private IllegalArgumentException unavailableQuestion(int questionId) {
        return new IllegalArgumentException(
                "Question unavailable for exam: " + questionId
        );
    }

    private void requireExamRepository() {
        if (examRepository == null) {
            throw new IllegalStateException("Exam repository is not configured");
        }
    }

    private void requireAutomaticExamRepositories() {
        if (questionRepository == null || examRepository == null) {
            throw new IllegalStateException(
                    "Automatic-exam repositories are not configured"
            );
        }
    }

    private void requireQuestionBankDependencies() {
        if (questionRepository == null || courseRepository == null || userRepository == null) {
            throw new IllegalStateException("Question-bank dependencies are not configured");
        }
    }

    private QuestionDTO toDto(Question question) {
        return new QuestionDTO(
                question.getQuestionId(),
                question.getContent(),
                question.getTopic(),
                MULTIPLE_CHOICE,
                question.getDifficulty(),
                question.getStatus(),
                question.getIllustrationPath(),
                question.getAnswerOption1(),
                question.getAnswerOption2(),
                question.getAnswerOption3(),
                question.getAnswerOption4(),
                question.getCorrectOptionNumber(),
                0,
                0,
                1,
                question.getIllustration() == null
                        ? null : toIllustrationDto(question.getIllustration())
        );
    }

    private QuestionIllustrationDTO toIllustrationDto(
            QuestionIllustration illustration
    ) {
        return new QuestionIllustrationDTO(
                illustration.getMediaType(),
                illustration.getContent(),
                illustration.getByteLength(),
                illustration.getWidth(),
                illustration.getHeight(),
                illustration.getSha256()
        );
    }

    private QuestionIllustration createIllustration(
            QuestionIllustrationUploadPayload upload
    ) {
        return upload == null
                ? null
                : QuestionIllustration.create(
                        upload.getFileName(), upload.getContent(), LocalDateTime.now()
                );
    }

    private void validateIllustrationChange(UpdateQuestionPayload payload) {
        QuestionIllustrationChange change = payload.getIllustrationChange();
        if (change == null) {
            throw new IllegalArgumentException(
                    "Question illustration change is required"
            );
        }
        if (change == QuestionIllustrationChange.REPLACE
                && payload.getIllustrationUpload() == null) {
            throw new IllegalArgumentException(
                    "Question illustration content is required"
            );
        }
        if (change != QuestionIllustrationChange.REPLACE
                && payload.getIllustrationUpload() != null) {
            throw new IllegalArgumentException(
                    "Question illustration upload is invalid"
            );
        }
    }

    private void applyIllustrationChange(Question question,
                                         UpdateQuestionPayload payload) {
        switch (payload.getIllustrationChange()) {
            case KEEP -> {
                // The hydrated exact current-version value remains attached.
            }
            case REPLACE -> question.setIllustration(
                    createIllustration(payload.getIllustrationUpload())
            );
            case REMOVE -> question.setIllustration(null);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeText(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }
}
