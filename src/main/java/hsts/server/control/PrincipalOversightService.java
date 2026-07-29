package hsts.server.control;

import hsts.common.ExamDTO;
import hsts.common.ExamExecutionSummaryDTO;
import hsts.common.ExamSummaryDTO;
import hsts.common.ExecutionSubmissionSummaryDTO;
import hsts.common.PrincipalQuestionDTO;
import hsts.common.SubmissionReviewDTO;
import hsts.common.type.UserRole;
import hsts.server.entity.User;
import hsts.server.repository.ExamExecutionRepository;
import hsts.server.repository.ExamRepository;
import hsts.server.repository.ExamSubmissionRepository;
import hsts.server.repository.QuestionRepository;
import hsts.server.repository.UserRepository;

import java.util.List;

public final class PrincipalOversightService {
    private static final String CONCEALED = "Record not found or access denied";

    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final ExamRepository examRepository;
    private final ExamExecutionRepository examExecutionRepository;
    private final ExamSubmissionRepository examSubmissionRepository;

    public PrincipalOversightService(
            UserRepository userRepository,
            QuestionRepository questionRepository,
            ExamRepository examRepository,
            ExamExecutionRepository examExecutionRepository,
            ExamSubmissionRepository examSubmissionRepository
    ) {
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.examRepository = examRepository;
        this.examExecutionRepository = examExecutionRepository;
        this.examSubmissionRepository = examSubmissionRepository;
    }

    public List<PrincipalQuestionDTO> getAllQuestions(int authenticatedUserId) {
        authorizePrincipal(authenticatedUserId);
        return List.copyOf(questionRepository.findAllForPrincipal());
    }

    public List<PrincipalQuestionDTO> getQuestionVersions(
            int authenticatedUserId, int questionId
    ) {
        authorizePrincipal(authenticatedUserId);
        requirePositive(questionId, "Question ID must be positive");
        List<PrincipalQuestionDTO> versions =
                questionRepository.findVersionsForPrincipal(questionId);
        if (versions.isEmpty()) {
            throw new IllegalArgumentException(CONCEALED);
        }
        return List.copyOf(versions);
    }

    public PrincipalQuestionDTO getQuestionVersion(
            int authenticatedUserId, int questionId, int versionNo
    ) {
        authorizePrincipal(authenticatedUserId);
        requirePositive(questionId, "Question ID must be positive");
        requirePositive(versionNo, "Question version must be positive");
        return questionRepository.findVersionForPrincipal(questionId, versionNo)
                .orElseThrow(() -> new IllegalArgumentException(CONCEALED));
    }

    public List<ExamSummaryDTO> getAllExams(int authenticatedUserId) {
        authorizePrincipal(authenticatedUserId);
        return List.copyOf(examRepository.findAllForPrincipal());
    }

    public List<ExamSummaryDTO> getExamVersions(int authenticatedUserId, int examId) {
        authorizePrincipal(authenticatedUserId);
        requirePositive(examId, "Exam ID must be positive");
        List<ExamSummaryDTO> versions = examRepository.findVersionsForPrincipal(examId);
        if (versions.isEmpty()) {
            throw new IllegalArgumentException(CONCEALED);
        }
        return List.copyOf(versions);
    }

    public ExamDTO getExamVersion(
            int authenticatedUserId, int examId, int versionNo
    ) {
        authorizePrincipal(authenticatedUserId);
        requirePositive(examId, "Exam ID must be positive");
        requirePositive(versionNo, "Exam version must be positive");
        return examRepository.findVersionForPrincipal(examId, versionNo)
                .orElseThrow(() -> new IllegalArgumentException(CONCEALED));
    }

    public List<ExamExecutionSummaryDTO> getAllExecutions(int authenticatedUserId) {
        authorizePrincipal(authenticatedUserId);
        return List.copyOf(examExecutionRepository.findAllForPrincipal());
    }

    public List<ExecutionSubmissionSummaryDTO> getExecutionResults(
            int authenticatedUserId, int executionId
    ) {
        authorizePrincipal(authenticatedUserId);
        requirePositive(executionId, "Execution ID must be positive");
        return List.copyOf(
                examSubmissionRepository.findSummariesForPrincipal(executionId)
        );
    }

    public SubmissionReviewDTO getSubmissionResult(
            int authenticatedUserId, int submissionId
    ) {
        authorizePrincipal(authenticatedUserId);
        requirePositive(submissionId, "Submission ID must be positive");
        return examSubmissionRepository.findReviewForPrincipal(submissionId)
                .orElseThrow(() -> new IllegalArgumentException(CONCEALED));
    }

    private void authorizePrincipal(int authenticatedUserId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found: " + authenticatedUserId
                ));
        if (!user.isActive()) {
            throw new IllegalStateException("User account is blocked");
        }
        if (user.getRole() != UserRole.PRINCIPAL) {
            throw new IllegalStateException("Principal access required");
        }
    }

    private static void requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
