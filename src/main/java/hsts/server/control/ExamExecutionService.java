package hsts.server.control;

import hsts.common.ExamAttemptDTO;
import hsts.common.ExamExecutionPreviewDTO;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExecutionCodePayload;
import hsts.common.ExtendSubmissionTimePayload;
import hsts.common.SaveExamAnswerPayload;
import hsts.common.ScheduleExamExecutionPayload;
import hsts.common.StartExamPayload;
import hsts.common.StudentAnswerDTO;
import hsts.common.SubmissionIdPayload;
import hsts.common.type.ExecutionStatus;
import hsts.common.type.UserRole;
import hsts.server.entity.Exam;
import hsts.server.entity.ExamExecution;
import hsts.server.entity.ExamQuestion;
import hsts.server.entity.ExamSubmission;
import hsts.server.entity.StudentAnswer;
import hsts.server.entity.User;
import hsts.server.repository.ExamExecutionRepository;
import hsts.server.repository.ExamRepository;
import hsts.server.repository.ExamSubmissionRepository;
import hsts.server.repository.StudentEnrollmentRepository;
import hsts.server.repository.StudentProfileRepository;
import hsts.server.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public class ExamExecutionService {
    private DatabaseService databaseService;

    private final ExamExecutionRepository examExecutionRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final ExamRepository examRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // COMPATIBILITY-ONLY: Preserves legacy skeleton construction.
    public ExamExecutionService() {
        this(null, null, null, null, null, null, Clock.systemUTC());
    }

    public ExamExecutionService(
            ExamExecutionRepository examExecutionRepository,
            ExamSubmissionRepository examSubmissionRepository,
            StudentEnrollmentRepository studentEnrollmentRepository,
            StudentProfileRepository studentProfileRepository,
            UserRepository userRepository
    ) {
        this(
                examExecutionRepository,
                examSubmissionRepository,
                studentEnrollmentRepository,
                studentProfileRepository,
                userRepository,
                null,
                Clock.systemUTC()
        );
    }

    public ExamExecutionService(
            ExamExecutionRepository examExecutionRepository,
            ExamSubmissionRepository examSubmissionRepository,
            StudentEnrollmentRepository studentEnrollmentRepository,
            StudentProfileRepository studentProfileRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this(
                examExecutionRepository,
                examSubmissionRepository,
                studentEnrollmentRepository,
                studentProfileRepository,
                userRepository,
                null,
                clock
        );
    }

    public ExamExecutionService(
            ExamExecutionRepository examExecutionRepository,
            ExamSubmissionRepository examSubmissionRepository,
            StudentEnrollmentRepository studentEnrollmentRepository,
            StudentProfileRepository studentProfileRepository,
            UserRepository userRepository,
            ExamRepository examRepository,
            Clock clock
    ) {
        this.examExecutionRepository = examExecutionRepository;
        this.examSubmissionRepository = examSubmissionRepository;
        this.examRepository = examRepository;
        this.studentEnrollmentRepository = studentEnrollmentRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public ExamExecutionSummaryDTO scheduleExecution(
            int authenticatedManagerId,
            ScheduleExamExecutionPayload payload
    ) {
        authorizeManager(authenticatedManagerId);
        requireDependencies();

        if (payload == null) {
            throw new IllegalArgumentException("Execution scheduling data is missing");
        }
        if (payload.getOpeningTime() == null || payload.getClosingTime() == null) {
            throw new IllegalArgumentException("Opening and closing times are required");
        }
        if (!payload.getClosingTime().isAfter(payload.getOpeningTime())) {
            throw new IllegalArgumentException(
                    "Execution closing time must be after opening time"
            );
        }
        LocalDateTime currentTime = currentTime();
        if (payload.getOpeningTime().isBefore(currentTime)) {
            throw new IllegalArgumentException("Execution opening time cannot be in the past");
        }

        ExamExecution execution = examExecutionRepository.schedule(
                authenticatedManagerId,
                payload.getExamId(),
                payload.getExamVersionNo(),
                payload.getOpeningTime(),
                payload.getClosingTime()
        );
        return examExecutionRepository.findByIdForManager(
                authenticatedManagerId,
                execution.getExecutionId()
        ).orElseThrow(() -> new IllegalArgumentException(
                "Execution not found: " + execution.getExecutionId()
        ));
    }

    public List<ExamExecutionSummaryDTO> getMyExecutions(int authenticatedManagerId) {
        authorizeManager(authenticatedManagerId);
        requireDependencies();
        return examExecutionRepository.findCreatedByManager(authenticatedManagerId);
    }

    public ExamExecutionSummaryDTO getExecutionForManager(int authenticatedManagerId,
                                                           int executionId) {
        authorizeManager(authenticatedManagerId);
        requireDependencies();
        return examExecutionRepository.findByIdForManager(
                authenticatedManagerId,
                executionId
        ).orElseThrow(() -> new IllegalArgumentException(
                "Execution not found: " + executionId
        ));
    }

    public ExamExecutionPreviewDTO validateExecutionCode(
            int authenticatedStudentId,
            ExecutionCodePayload payload
    ) {
        authorizeStudent(authenticatedStudentId);
        requireDependencies();
        if (payload == null || isBlank(payload.getExecutionCode())) {
            throw new IllegalArgumentException("Execution code is required");
        }

        String normalizedCode = payload.getExecutionCode()
                .trim()
                .toUpperCase(Locale.ROOT);
        if (!normalizedCode.matches("[A-Z0-9]{4}")) {
            throw new IllegalArgumentException("Invalid execution code");
        }

        ExamExecution execution = examExecutionRepository.findEntityByCodeForStudent(
                authenticatedStudentId,
                normalizedCode
        ).orElseThrow(() -> new IllegalStateException("Execution not available"));
        LocalDateTime currentTime = currentTime();
        ExecutionStatus currentStatus = execution.statusAt(currentTime);
        if (execution.getStatus() == ExecutionStatus.CLOSED) {
            throw new IllegalStateException("Exam is closed");
        }
        if (currentStatus == ExecutionStatus.SCHEDULED) {
            throw new IllegalStateException("Exam is not open yet");
        }
        ExamExecutionPreviewDTO preview = examExecutionRepository.findByCodeForStudent(
                authenticatedStudentId,
                normalizedCode
        ).orElseThrow(() -> new IllegalStateException("Execution not available"));
        if (currentStatus == ExecutionStatus.CLOSED && !preview.isResumable()) {
            throw new IllegalStateException("Exam is closed");
        }
        return preview;
    }

    public ExamAttemptDTO startOrResumeExam(int authenticatedStudentId,
                                             StartExamPayload payload) {
        authorizeStudent(authenticatedStudentId);
        requireDependencies();
        if (payload == null) {
            throw new IllegalArgumentException("Exam attempt data is missing");
        }
        if (isBlank(payload.getIdentityConfirmation())) {
            throw new IllegalArgumentException("Identity confirmation is required");
        }
        ExamExecution execution = examExecutionRepository.findEntityForStudent(
                authenticatedStudentId,
                payload.getExecutionId()
        ).orElseThrow(() -> new IllegalStateException("Execution not available"));
        if (!studentProfileRepository.matchesIdentity(
                authenticatedStudentId,
                payload.getIdentityConfirmation()
        )) {
            throw new IllegalArgumentException("Invalid identity confirmation");
        }

        LocalDateTime currentTime = currentTime();
        if (execution.statusAt(currentTime) == ExecutionStatus.SCHEDULED) {
            throw new IllegalStateException("Execution not available");
        }
        ExamSubmission submission = examSubmissionRepository.startOrResume(
                authenticatedStudentId,
                execution,
                currentTime
        );
        return examSubmissionRepository.findActiveForStudent(
                authenticatedStudentId,
                submission.getSubmissionId(),
                currentTime
        ).orElseThrow(() -> new IllegalArgumentException(
                "Exam attempt not found: " + submission.getSubmissionId()
        ));
    }

    public ExamAttemptDTO getActiveAttempt(int authenticatedStudentId,
                                           SubmissionIdPayload payload) {
        authorizeStudent(authenticatedStudentId);
        requireDependencies();
        if (payload == null) {
            throw new IllegalArgumentException("Submission data is missing");
        }
        ExamSubmission submission = examSubmissionRepository.findActiveEntityForStudent(
                authenticatedStudentId,
                payload.getSubmissionId()
        ).orElseThrow(() -> new IllegalArgumentException(
                "Exam attempt not found: " + payload.getSubmissionId()
        ));
        LocalDateTime currentTime = currentTime();
        if (!submission.isEditable(currentTime)) {
            throw new IllegalArgumentException(
                    "Exam attempt not found: " + payload.getSubmissionId()
            );
        }
        return examSubmissionRepository.findActiveForStudent(
                authenticatedStudentId,
                submission.getSubmissionId(),
                currentTime
        ).orElseThrow(() -> new IllegalArgumentException(
                "Exam attempt not found: " + payload.getSubmissionId()
        ));
    }

    public StudentAnswerDTO saveAnswer(int authenticatedStudentId,
                                       SaveExamAnswerPayload payload) {
        authorizeStudent(authenticatedStudentId);
        requireDependencies();
        if (payload == null) {
            throw new IllegalArgumentException("Answer data is missing");
        }
        requireExamRepository();
        ExamSubmission submission = examSubmissionRepository
                .findActiveEntityForStudent(
                        authenticatedStudentId,
                        payload.getSubmissionId()
                ).orElseThrow(() -> new IllegalArgumentException(
                        "Exam attempt not found: " + payload.getSubmissionId()
                ));
        Exam exam = examRepository.findEntityVersion(
                submission.getExamId(),
                submission.getExamVersionNo()
        ).orElseThrow(() -> new IllegalArgumentException(
                "Question not found in exam: " + payload.getQuestionId()
        ));
        if (exam.getExamId() != submission.getExamId()
                || exam.getCurrentVersionNo() != submission.getExamVersionNo()) {
            throw new IllegalStateException(
                    "Exam version does not match the submission"
            );
        }
        ExamQuestion examQuestion = exam.getExamQuestions().stream()
                .filter(candidate -> candidate.getQuestionId()
                        == payload.getQuestionId())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Question not found in exam: " + payload.getQuestionId()
                ));
        boolean validOption = examQuestion.getQuestion().getAnswerOptions().stream()
                .anyMatch(option -> option.getOptionId()
                        == payload.getSelectedOptionNumber());
        if (!validOption) {
            throw new IllegalArgumentException("Invalid answer option");
        }

        LocalDateTime currentTime = currentTime();
        submission.saveAnswer(
                payload.getQuestionId(),
                examQuestion.getQuestionVersionNo(),
                payload.getSelectedOptionNumber(),
                currentTime
        );
        StudentAnswer answer = findAnswer(
                submission,
                payload.getQuestionId(),
                examQuestion.getQuestionVersionNo()
        );
        ExamSubmission persisted = examSubmissionRepository.persistAnswer(
                authenticatedStudentId,
                submission,
                answer,
                currentTime
        );
        StudentAnswer persistedAnswer = findAnswer(
                persisted,
                payload.getQuestionId(),
                examQuestion.getQuestionVersionNo()
        );
        return new StudentAnswerDTO(
                persistedAnswer.getQuestionId(),
                persistedAnswer.getSelectedOptionNumber(),
                persistedAnswer.getUpdatedAt()
        );
    }

    public ExamAttemptDTO submitExam(int authenticatedStudentId,
                                     SubmissionIdPayload payload) {
        authorizeStudent(authenticatedStudentId);
        requireDependencies();
        if (payload == null) {
            throw new IllegalArgumentException("Submission data is missing");
        }
        return examSubmissionRepository.submit(
                authenticatedStudentId,
                payload.getSubmissionId(),
                currentTime()
        );
    }

    public int autoSubmitExpired() {
        requireDependencies();
        LocalDateTime currentTime = currentTime();
        int submittedCount = 0;
        for (int submissionId : examSubmissionRepository.findExpiredSubmissionIds(
                currentTime
        )) {
            if (examSubmissionRepository.autoSubmit(submissionId, currentTime)) {
                submittedCount++;
            }
        }
        return submittedCount;
    }

    public boolean extendStudentTime(int authenticatedManagerId,
                                     ExtendSubmissionTimePayload payload) {
        authorizeManager(authenticatedManagerId);
        requireDependencies();
        if (payload == null) {
            throw new IllegalArgumentException("Time extension data is missing");
        }
        if (payload.getExtraMinutes() <= 0) {
            throw new IllegalArgumentException("Extra minutes must be positive");
        }
        if (isBlank(payload.getReason())) {
            throw new IllegalArgumentException("Extension reason is required");
        }

        String normalizedReason = payload.getReason().trim();
        ExamSubmission submission = examSubmissionRepository.findEntityForManager(
                authenticatedManagerId,
                payload.getSubmissionId()
        ).orElseThrow(() -> new IllegalArgumentException(
                "Exam attempt not found: " + payload.getSubmissionId()
        ));
        if (submission.getStatus()
                != hsts.common.type.SubmissionStatus.IN_PROGRESS) {
            throw new IllegalArgumentException(
                    "Exam attempt not found: " + payload.getSubmissionId()
            );
        }
        LocalDateTime currentTime = currentTime();
        submission.extendTime(
                payload.getExtraMinutes(),
                normalizedReason,
                currentTime
        );
        examSubmissionRepository.persistExtension(
                authenticatedManagerId,
                submission,
                payload.getExtraMinutes(),
                normalizedReason,
                currentTime
        );
        return true;
    }

    public ExamExecution createExamExecution(int examId, LocalDateTime openingTime, LocalDateTime closingTime) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void closeExamExecution(int executionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean validateExecutionCode(String code) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public ExamSubmission startExam(int studentId, Exam exam) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void saveAnswer(int submissionId, int questionId, int answerChoice) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void submitExam(int submissionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void autoSubmit(int submissionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void extendTime(int submissionId, int extraMinutes, String reason) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean validateStudentId(int studentId, int id) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void startTime(int submissionId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void calculateRemainingTime(int durationMinutes, LocalDateTime currentTime, LocalDateTime startedAt) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    private User authorizeManager(int authenticatedManagerId) {
        User user = requireActiveUser(authenticatedManagerId);
        if (user.getRole() != UserRole.TEACHER
                && user.getRole() != UserRole.COORDINATOR) {
            throw new IllegalStateException(
                    "Only teachers and coordinators can manage executions"
            );
        }
        return user;
    }

    private User authorizeStudent(int authenticatedStudentId) {
        User user = requireActiveUser(authenticatedStudentId);
        if (user.getRole() != UserRole.STUDENT) {
            throw new IllegalStateException("Only students can take exams");
        }
        return user;
    }

    private User requireActiveUser(int userId) {
        if (userRepository == null) {
            throw new IllegalStateException("Exam execution dependencies are not configured");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found: " + userId
                ));
        if (!user.isActive()) {
            throw new IllegalStateException("User account is blocked");
        }
        return user;
    }

    private void requireDependencies() {
        if (examExecutionRepository == null
                || examSubmissionRepository == null
                || studentEnrollmentRepository == null
                || studentProfileRepository == null
                || userRepository == null
                || clock == null) {
            throw new IllegalStateException(
                    "Exam execution dependencies are not configured"
            );
        }
    }

    private void requireExamRepository() {
        if (examRepository == null) {
            throw new IllegalStateException("Exam repository is not configured");
        }
    }

    private StudentAnswer findAnswer(ExamSubmission submission, int questionId,
                                     int questionVersionNo) {
        return submission.getStudentAnswers().stream()
                .filter(answer -> answer.getQuestionId() == questionId
                        && answer.getQuestionVersionNo() == questionVersionNo)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Saved answer could not be reloaded: " + questionId
                ));
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.now(clock);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
