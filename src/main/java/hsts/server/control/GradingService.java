package hsts.server.control;

import hsts.common.type.SubmissionStatus;
import hsts.server.entity.AnswerOption;
import hsts.server.entity.Exam;
import hsts.server.entity.ExamQuestion;
import hsts.server.entity.ExamSubmission;
import hsts.server.entity.Question;
import hsts.server.entity.StudentAnswer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GradingService {
    private static final BigDecimal REQUIRED_TOTAL_SCORE = new BigDecimal("100.00");
    private static final BigDecimal ZERO_SCORE = new BigDecimal("0.00");

    public ExamSubmission gradeAutomatically(
            ExamSubmission submission,
            Exam exactExamVersion,
            LocalDateTime gradedAt
    ) {
        requireSubmission(submission);
        if (exactExamVersion == null) {
            throw new IllegalArgumentException("Exact exam version is required");
        }
        if (gradedAt == null) {
            throw new IllegalArgumentException("Grading timestamp is required");
        }
        requireGradableStatus(submission);
        if (submission.getExamId() != exactExamVersion.getExamId()) {
            throw new IllegalArgumentException("Submission exam ID does not match exact exam");
        }
        if (submission.getExamVersionNo() != exactExamVersion.getCurrentVersionNo()) {
            throw new IllegalArgumentException(
                    "Submission exam version does not match exact exam"
            );
        }
        if (submission.getAutomaticScoreValue().isPresent()) {
            throw new IllegalStateException("Automatic grading has already been recorded");
        }

        List<ExamQuestion> selections = validateExactExam(exactExamVersion);
        Map<Integer, StudentAnswer> answers = validateAnswers(submission, selections);
        validateGradingTimestamp(submission, answers.values(), gradedAt);
        List<AnswerGrade> grades = calculateGrades(selections, answers);

        BigDecimal total = ZERO_SCORE;
        for (AnswerGrade grade : grades) {
            total = total.add(grade.earnedScore());
        }
        if (total.compareTo(REQUIRED_TOTAL_SCORE) > 0) {
            throw new IllegalStateException("Calculated automatic score exceeds 100");
        }

        for (AnswerGrade grade : grades) {
            if (grade.answerPresent()) {
                submission.recordAnswerGrade(
                        grade.questionId(),
                        grade.correct(),
                        grade.earnedScore(),
                        gradedAt
                );
            }
        }
        submission.recordAutomaticScore(total, gradedAt);
        return submission;
    }

    public ExamSubmission reviewGrade(
            ExamSubmission submission,
            int reviewerUserId,
            BigDecimal finalScore,
            String feedback,
            LocalDateTime reviewedAt
    ) {
        String adjustmentReason = submission != null
                && finalScore != null
                && submission.getAutomaticScoreValue()
                        .map(score -> score.compareTo(finalScore) != 0)
                        .orElse(false)
                ? feedback
                : null;
        return reviewGrade(
                submission,
                reviewerUserId,
                finalScore,
                feedback,
                adjustmentReason,
                reviewedAt
        );
    }

    public ExamSubmission reviewGrade(
            ExamSubmission submission,
            int reviewerUserId,
            BigDecimal finalScore,
            String feedback,
            String adjustmentReason,
            LocalDateTime reviewedAt
    ) {
        requireSubmission(submission);
        if (reviewerUserId <= 0) {
            throw new IllegalArgumentException("Reviewer user ID must be positive");
        }
        if (finalScore == null) {
            throw new IllegalArgumentException("Final score is required");
        }
        if (reviewedAt == null) {
            throw new IllegalArgumentException("Review timestamp is required");
        }
        if (submission.getAutomaticScoreValue().isEmpty()) {
            throw new IllegalStateException("Automatic grading must be recorded first");
        }

        submission.recordTeacherReview(
                reviewerUserId,
                finalScore,
                feedback,
                adjustmentReason,
                reviewedAt
        );
        return submission;
    }

    public ExamSubmission publishGrade(
            ExamSubmission submission,
            int publisherUserId,
            LocalDateTime publishedAt
    ) {
        requireSubmission(submission);
        if (publisherUserId <= 0) {
            throw new IllegalArgumentException("Publisher user ID must be positive");
        }
        if (publishedAt == null) {
            throw new IllegalArgumentException("Publication timestamp is required");
        }

        submission.publish(publisherUserId, publishedAt);
        return submission;
    }

    public Optional<BigDecimal> getPublishedScore(ExamSubmission submission) {
        requireSubmission(submission);
        return submission.getPublishedFinalScore();
    }

    // COMPATIBILITY-ONLY: The diagram signature lacks the submission aggregate,
    // exact exam version, and server grading timestamp required for safe grading.
    public double calculateAutomaticGrade(int submissionId) {
        requirePositiveId(submissionId, "Submission ID must be positive");
        throw new IllegalStateException(
                "Automatic grading requires submission and exact exam version"
        );
    }

    // COMPATIBILITY-ONLY: The diagram signature lacks reviewer identity,
    // adjustment reason, and server review timestamp.
    public void updateManualGrade(int submissionId, double score, String feedback) {
        requirePositiveId(submissionId, "Submission ID must be positive");
        throw new IllegalStateException(
                "Manual grade updates require submission, reviewer, reason, and timestamp"
        );
    }

    // COMPATIBILITY-ONLY: Publication requires the controlled aggregate,
    // publisher identity, and server timestamp.
    public void publishGrade(int submissionId) {
        requirePositiveId(submissionId, "Submission ID must be positive");
        throw new IllegalStateException(
                "Grade publication requires submission, publisher, and timestamp"
        );
    }

    // COMPATIBILITY-ONLY: Result loading belongs to a later persistence checkpoint.
    public ExamSubmission getSubmissionResult(int submissionId) {
        requirePositiveId(submissionId, "Submission ID must be positive");
        throw new IllegalStateException("Submission result persistence is not configured");
    }

    // COMPATIBILITY-ONLY: Result loading belongs to a later persistence checkpoint.
    public List getResultsByExam(int examId) {
        requirePositiveId(examId, "Exam ID must be positive");
        throw new IllegalStateException("Exam result persistence is not configured");
    }

    // COMPATIBILITY-ONLY: Result loading belongs to a later persistence checkpoint.
    public List getResultsByStudent(int studentId) {
        requirePositiveId(studentId, "Student ID must be positive");
        throw new IllegalStateException("Student result persistence is not configured");
    }

    private List<ExamQuestion> validateExactExam(Exam exactExamVersion) {
        List<ExamQuestion> selections = exactExamVersion.getExamQuestions();
        if (selections.isEmpty()) {
            throw new IllegalStateException("Exact exam contains no questions");
        }

        Set<Integer> questionIds = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        BigDecimal total = ZERO_SCORE;
        for (int index = 0; index < selections.size(); index++) {
            ExamQuestion selection = selections.get(index);
            if (selection == null) {
                throw new IllegalStateException("Exact exam contains a missing question");
            }
            if (!questionIds.add(selection.getQuestionId())) {
                throw new IllegalStateException("Exact exam contains a duplicate question");
            }
            if (!orders.add(selection.getOrderNumber())
                    || selection.getOrderNumber() != index + 1) {
                throw new IllegalStateException(
                        "Exact exam question order must start at 1 and be contiguous"
                );
            }
            BigDecimal score = requireSchemaScore(selection.getScoreValue());
            validateQuestionSnapshot(selection);
            total = total.add(score);
        }
        if (total.compareTo(REQUIRED_TOTAL_SCORE) != 0) {
            throw new IllegalStateException("Exact exam total score must equal 100.00");
        }
        return selections;
    }

    private Map<Integer, StudentAnswer> validateAnswers(
            ExamSubmission submission,
            List<ExamQuestion> selections
    ) {
        Map<Integer, ExamQuestion> selectionsByQuestion = new HashMap<>();
        for (ExamQuestion selection : selections) {
            selectionsByQuestion.put(selection.getQuestionId(), selection);
        }

        Map<Integer, StudentAnswer> answers = new HashMap<>();
        for (StudentAnswer answer : submission.getStudentAnswers()) {
            StudentAnswer previous = answers.putIfAbsent(answer.getQuestionId(), answer);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate student answer: " + answer.getQuestionId()
                );
            }
            ExamQuestion selection = selectionsByQuestion.get(answer.getQuestionId());
            if (selection == null) {
                throw new IllegalArgumentException(
                        "Student answer is not part of the exact exam version: "
                                + answer.getQuestionId()
                );
            }
            if (answer.getQuestionVersionNo() != selection.getQuestionVersionNo()) {
                throw new IllegalArgumentException(
                        "Student answer version does not match exact exam: "
                                + answer.getQuestionId()
                );
            }
            if (answer.isGraded()) {
                throw new IllegalStateException(
                        "Submission contains an already graded answer"
                );
            }
        }
        return answers;
    }

    private void validateGradingTimestamp(
            ExamSubmission submission,
            Iterable<StudentAnswer> answers,
            LocalDateTime gradedAt
    ) {
        if (submission.getUpdatedAt() != null
                && gradedAt.isBefore(submission.getUpdatedAt())) {
            throw new IllegalArgumentException(
                    "Grading timestamp cannot precede submission state"
            );
        }
        for (StudentAnswer answer : answers) {
            if (gradedAt.isBefore(answer.getUpdatedAt())) {
                throw new IllegalArgumentException(
                        "Grading timestamp cannot precede answer state"
                );
            }
        }
    }

    private List<AnswerGrade> calculateGrades(
            List<ExamQuestion> selections,
            Map<Integer, StudentAnswer> answers
    ) {
        return selections.stream().map(selection -> {
            StudentAnswer answer = answers.get(selection.getQuestionId());
            if (answer == null) {
                return new AnswerGrade(
                        selection.getQuestionId(),
                        false,
                        ZERO_SCORE,
                        false
                );
            }

            Question snapshot = selection.getQuestion();
            AnswerOption selected = snapshot.getAnswerOptions().stream()
                    .filter(option -> option.getOptionId() == answer.getSelectedOptionId())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Student answer option is absent from the exact question snapshot"
                    ));
            boolean correct = selected.isCorrect();
            return new AnswerGrade(
                    selection.getQuestionId(),
                    correct,
                    correct ? requireSchemaScore(selection.getScoreValue()) : ZERO_SCORE,
                    true
            );
        }).toList();
    }

    private void validateQuestionSnapshot(ExamQuestion selection) {
        Question snapshot = selection.getQuestion();
        if (snapshot.getQuestionId() != selection.getQuestionId()) {
            throw new IllegalStateException(
                    "Question snapshot identity does not match exam selection"
            );
        }

        List<AnswerOption> options = snapshot.getAnswerOptions();
        if (options.size() != 4) {
            throw new IllegalStateException(
                    "Exact question snapshot must contain four answer options"
            );
        }
        boolean[] optionIds = new boolean[5];
        int correctCount = 0;
        for (AnswerOption option : options) {
            if (option == null || option.getOptionId() < 1 || option.getOptionId() > 4
                    || optionIds[option.getOptionId()]) {
                throw new IllegalStateException(
                        "Exact question snapshot contains malformed answer options"
                );
            }
            optionIds[option.getOptionId()] = true;
            if (option.isCorrect()) {
                correctCount++;
            }
        }
        if (correctCount != 1) {
            throw new IllegalStateException(
                    "Exact question snapshot must contain exactly one correct answer"
            );
        }
    }

    private BigDecimal requireSchemaScore(BigDecimal score) {
        if (score == null || score.compareTo(BigDecimal.ZERO) <= 0
                || score.compareTo(REQUIRED_TOTAL_SCORE) > 0) {
            throw new IllegalStateException("Exact exam contains an invalid question score");
        }
        try {
            return score.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "Exact exam question score exceeds supported decimal scale",
                    exception
            );
        }
    }

    private void requireGradableStatus(ExamSubmission submission) {
        if (submission.getStatus() == SubmissionStatus.PUBLISHED) {
            throw new IllegalStateException("Published submissions cannot be regraded");
        }
        if (submission.getStatus() != SubmissionStatus.SUBMITTED
                && submission.getStatus() != SubmissionStatus.AUTO_SUBMITTED) {
            throw new IllegalStateException("Submission must be finalized before grading");
        }
    }

    private void requireSubmission(ExamSubmission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("Submission is required");
        }
    }

    private void requirePositiveId(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private record AnswerGrade(
            int questionId,
            boolean correct,
            BigDecimal earnedScore,
            boolean answerPresent
    ) {
    }
}
