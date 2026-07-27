package hsts.server.entity;

import hsts.common.type.ExamStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class Exam {
    private static final BigDecimal REQUIRED_TOTAL_SCORE = new BigDecimal("100.00");

    private final int examId;
    private final String examCode;
    private final int courseId;
    private final int createdByUserId;
    private int currentVersionNo;
    private String title;
    private int durationMinutes;
    private String teacherNotes;
    private String studentInstructions;
    private BigDecimal totalScore;
    private ExamStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime submittedAt;
    private Integer reviewedByUserId;
    private LocalDateTime reviewedAt;
    private LocalDateTime approvedAt;
    private String rejectionReason;

    // RELATIONSHIP-DERIVED: Exam contains one or more exam questions when complete.
    private final List<ExamQuestion> examQuestions;

    // RELATIONSHIP-DERIVED: Exam has zero or more exam executions; loading is deferred.
    private final List<ExamExecution> examExecutions;

    // COMPATIBILITY-ONLY: Required by existing Assignment 2 skeleton callers.
    public Exam() {
        LocalDateTime now = LocalDateTime.now();
        this.examId = 0;
        this.examCode = null;
        this.courseId = 0;
        this.createdByUserId = 0;
        this.currentVersionNo = 1;
        this.title = "";
        this.durationMinutes = 0;
        this.teacherNotes = "";
        this.studentInstructions = "";
        this.totalScore = BigDecimal.ZERO;
        this.status = ExamStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
        this.examQuestions = new ArrayList<>();
        this.examExecutions = new ArrayList<>();
    }

    private Exam(int examId, String examCode, int courseId, int createdByUserId,
                 int currentVersionNo, String title, int durationMinutes,
                 String teacherNotes, String studentInstructions, ExamStatus status,
                 LocalDateTime createdAt, LocalDateTime updatedAt,
                 LocalDateTime submittedAt, Integer reviewedByUserId,
                 LocalDateTime reviewedAt, String rejectionReason,
                 List<ExamQuestion> examQuestions) {
        if (examId < 0) {
            throw new IllegalArgumentException("Exam ID cannot be negative");
        }
        if (courseId <= 0) {
            throw new IllegalArgumentException("Course ID must be positive");
        }
        if (createdByUserId <= 0) {
            throw new IllegalArgumentException("Creator user ID must be positive");
        }
        if (currentVersionNo <= 0) {
            throw new IllegalArgumentException("Exam version must be positive");
        }
        this.examId = examId;
        this.examCode = normalizeExamCode(examId, examCode);
        this.courseId = courseId;
        this.createdByUserId = createdByUserId;
        this.currentVersionNo = currentVersionNo;
        this.title = requireText(title, "Exam title is required");
        this.durationMinutes = requirePositiveDuration(durationMinutes);
        this.teacherNotes = normalizeOptionalText(teacherNotes);
        this.studentInstructions = requireText(
                studentInstructions,
                "Student instructions are required"
        );
        this.status = Objects.requireNonNull(status, "Exam status is required");
        this.createdAt = Objects.requireNonNull(createdAt, "Created timestamp is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Updated timestamp is required");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Updated timestamp cannot precede creation");
        }
        this.submittedAt = submittedAt;
        this.reviewedByUserId = reviewedByUserId;
        this.reviewedAt = reviewedAt;
        this.approvedAt = status == ExamStatus.APPROVED ? reviewedAt : null;
        this.rejectionReason = normalizeRejectionReason(status, rejectionReason);
        this.examQuestions = copyAndValidateQuestions(examQuestions);
        this.totalScore = calculateTotalScoreValue();
        this.examExecutions = new ArrayList<>();
        validateWorkflowMetadata();
    }

    public static Exam createDraft(int courseId, int createdByUserId,
                                   String title, int durationMinutes,
                                   String teacherNotes, String studentInstructions,
                                   List<ExamQuestion> examQuestions) {
        LocalDateTime now = LocalDateTime.now();
        return new Exam(
                0, null, courseId, createdByUserId, 1,
                title, durationMinutes, teacherNotes, studentInstructions,
                ExamStatus.DRAFT, now, now, null, null, null, null,
                examQuestions
        );
    }

    public static Exam rehydrate(int examId, String examCode, int courseId,
                                 int createdByUserId, int currentVersionNo,
                                 String title, int durationMinutes,
                                 String teacherNotes, String studentInstructions,
                                 ExamStatus status, LocalDateTime createdAt,
                                 LocalDateTime updatedAt, LocalDateTime submittedAt,
                                 Integer reviewedByUserId, LocalDateTime reviewedAt,
                                 String rejectionReason,
                                 List<ExamQuestion> examQuestions) {
        if (examId <= 0) {
            throw new IllegalArgumentException("Persisted exam ID must be positive");
        }
        return new Exam(
                examId, examCode, courseId, createdByUserId, currentVersionNo,
                title, durationMinutes, teacherNotes, studentInstructions,
                status, createdAt, updatedAt, submittedAt, reviewedByUserId,
                reviewedAt, rejectionReason, examQuestions
        );
    }

    public int getExamId() { return examId; }
    public String getExamCode() { return examCode; }
    public int getCourseId() { return courseId; }
    public int getCreatedByUserId() { return createdByUserId; }
    public int getCurrentVersionNo() { return currentVersionNo; }
    public String getTitle() { return title; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getTeacherNotes() { return teacherNotes; }
    public String getStudentInstructions() { return studentInstructions; }
    public ExamStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public Integer getReviewedByUserId() { return reviewedByUserId; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public String getRejectionReason() { return rejectionReason; }

    // COMPATIBILITY-ONLY: The diagram exposes total score as double.
    public double calculateTotalScore() {
        return calculateTotalScoreValue().doubleValue();
    }

    public BigDecimal calculateTotalScoreValue() {
        return examQuestions.stream()
                .map(ExamQuestion::getScoreValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalScoreValue() {
        return totalScore;
    }

    public List<ExamQuestion> getExamQuestions() {
        return examQuestions.stream().map(ExamQuestion::copy).toList();
    }

    public List<ExamExecution> getExamExecutions() {
        return List.copyOf(examExecutions);
    }

    public boolean isEditable() {
        return status == ExamStatus.DRAFT;
    }

    public void updateMetadata(String title, int durationMinutes,
                               String teacherNotes, String studentInstructions) {
        requireEditable();
        String normalizedTitle = requireText(title, "Exam title is required");
        int normalizedDuration = requirePositiveDuration(durationMinutes);
        String normalizedTeacherNotes = normalizeOptionalText(teacherNotes);
        String normalizedInstructions = requireText(
                studentInstructions,
                "Student instructions are required"
        );
        if (!this.title.equals(normalizedTitle)
                || this.durationMinutes != normalizedDuration
                || !this.teacherNotes.equals(normalizedTeacherNotes)
                || !this.studentInstructions.equals(normalizedInstructions)) {
            this.title = normalizedTitle;
            this.durationMinutes = normalizedDuration;
            this.teacherNotes = normalizedTeacherNotes;
            this.studentInstructions = normalizedInstructions;
            touch();
        }
    }

    public void addExamQuestion(ExamQuestion examQuestion) {
        requireEditable();
        ExamQuestion copy = requireQuestion(examQuestion).copy();
        ensureNoDuplicateQuestion(copy, -1);
        ensureNoDuplicateOrder(copy.getOrderNumber(), -1);
        examQuestions.add(copy);
        sortQuestions();
        refreshTotalAndTouch();
    }

    public void removeExamQuestion(int questionId) {
        requireEditable();
        boolean removed = examQuestions.removeIf(
                selection -> selection.getQuestionId() == questionId
        );
        if (!removed) {
            throw new IllegalArgumentException("Exam question not found: " + questionId);
        }
        refreshTotalAndTouch();
    }

    public void reorderExamQuestion(int questionId, int orderNumber) {
        requireEditable();
        ExamQuestion selection = findQuestion(questionId);
        if (selection == null) {
            throw new IllegalArgumentException("Exam question not found: " + questionId);
        }
        if (orderNumber <= 0 || orderNumber > examQuestions.size()) {
            throw new IllegalArgumentException(
                    "Question order must be between 1 and " + examQuestions.size()
            );
        }
        int previousOrder = selection.getOrderNumber();
        if (previousOrder == orderNumber) {
            return;
        }
        for (ExamQuestion other : examQuestions) {
            if (other == selection) {
                continue;
            }
            int currentOrder = other.getOrderNumber();
            if (previousOrder < orderNumber
                    && currentOrder > previousOrder && currentOrder <= orderNumber) {
                other.updateOrder(currentOrder - 1);
            } else if (previousOrder > orderNumber
                    && currentOrder >= orderNumber && currentOrder < previousOrder) {
                other.updateOrder(currentOrder + 1);
            }
        }
        selection.updateOrder(orderNumber);
        sortQuestions();
        touch();
    }

    public void updateExamQuestionScore(int questionId, BigDecimal score) {
        requireEditable();
        ExamQuestion selection = findQuestion(questionId);
        if (selection == null) {
            throw new IllegalArgumentException("Exam question not found: " + questionId);
        }
        if (score != null && selection.getScoreValue().compareTo(score) == 0) {
            return;
        }
        selection.updateScore(score);
        refreshTotalAndTouch();
    }

    public void setExamNotes(String teacherNotes, String studentNotes) {
        updateMetadata(title, durationMinutes, teacherNotes, studentNotes);
    }

    public void startNewDraftVersion(String title, int durationMinutes,
                                     String teacherNotes, String studentInstructions,
                                     List<ExamQuestion> questions) {
        if (status != ExamStatus.APPROVED && status != ExamStatus.REJECTED) {
            throw new IllegalStateException(
                    "A new draft version requires an approved or rejected exam"
            );
        }
        List<ExamQuestion> replacements = copyAndValidateQuestions(questions);
        String normalizedTitle = requireText(title, "Exam title is required");
        int normalizedDuration = requirePositiveDuration(durationMinutes);
        String normalizedTeacherNotes = normalizeOptionalText(teacherNotes);
        String normalizedInstructions = requireText(
                studentInstructions,
                "Student instructions are required"
        );
        this.currentVersionNo++;
        this.title = normalizedTitle;
        this.durationMinutes = normalizedDuration;
        this.teacherNotes = normalizedTeacherNotes;
        this.studentInstructions = normalizedInstructions;
        this.examQuestions.clear();
        this.examQuestions.addAll(replacements);
        this.totalScore = calculateTotalScoreValue();
        this.status = ExamStatus.DRAFT;
        this.submittedAt = null;
        this.reviewedByUserId = null;
        this.reviewedAt = null;
        this.approvedAt = null;
        this.rejectionReason = null;
        touch();
    }

    public void submitForApproval() {
        submitForApproval(LocalDateTime.now());
    }

    void submitForApproval(LocalDateTime submittedAt) {
        requireEditable();
        validateCompleteExam();
        LocalDateTime timestamp = requireMutationTimestamp(
                submittedAt,
                "Submission timestamp is required"
        );
        this.status = ExamStatus.PENDING_APPROVAL;
        this.submittedAt = timestamp;
        this.reviewedByUserId = null;
        this.reviewedAt = null;
        this.approvedAt = null;
        this.rejectionReason = null;
        touch(timestamp);
    }

    public void approve(int reviewerUserId) {
        approve(reviewerUserId, LocalDateTime.now());
    }

    void approve(int reviewerUserId, LocalDateTime reviewedAt) {
        requirePendingApproval();
        requirePositiveReviewer(reviewerUserId);
        LocalDateTime timestamp = requireMutationTimestamp(
                reviewedAt,
                "Review timestamp is required"
        );
        this.status = ExamStatus.APPROVED;
        this.reviewedByUserId = reviewerUserId;
        this.reviewedAt = timestamp;
        this.approvedAt = timestamp;
        this.rejectionReason = null;
        touch(timestamp);
    }

    public void reject(int reviewerUserId, String reason) {
        reject(reviewerUserId, reason, LocalDateTime.now());
    }

    void reject(int reviewerUserId, String reason, LocalDateTime reviewedAt) {
        requirePendingApproval();
        requirePositiveReviewer(reviewerUserId);
        String normalizedReason = requireText(reason, "Rejection reason is required");
        LocalDateTime timestamp = requireMutationTimestamp(
                reviewedAt,
                "Review timestamp is required"
        );
        this.status = ExamStatus.REJECTED;
        this.reviewedByUserId = reviewerUserId;
        this.reviewedAt = timestamp;
        this.approvedAt = null;
        this.rejectionReason = normalizedReason;
        touch(timestamp);
    }

    private void validateCompleteExam() {
        if (examQuestions.isEmpty()) {
            throw new IllegalStateException("At least one question is required");
        }
        for (int index = 0; index < examQuestions.size(); index++) {
            if (examQuestions.get(index).getOrderNumber() != index + 1) {
                throw new IllegalStateException(
                        "Question order must start at 1 and be contiguous"
                );
            }
        }
        if (totalScore.compareTo(REQUIRED_TOTAL_SCORE) != 0) {
            throw new IllegalStateException("Exam total score must equal 100");
        }
    }

    private void validateWorkflowMetadata() {
        if (status == ExamStatus.DRAFT) {
            if (submittedAt != null || reviewedByUserId != null || reviewedAt != null
                    || rejectionReason != null) {
                throw new IllegalArgumentException("Draft exam contains review metadata");
            }
            return;
        }
        if (submittedAt == null) {
            throw new IllegalArgumentException("Submitted timestamp is required");
        }
        if (status == ExamStatus.PENDING_APPROVAL) {
            if (reviewedByUserId != null || reviewedAt != null || rejectionReason != null) {
                throw new IllegalArgumentException("Pending exam contains review metadata");
            }
            return;
        }
        if (reviewedByUserId == null || reviewedByUserId <= 0 || reviewedAt == null) {
            throw new IllegalArgumentException("Review metadata is required");
        }
        if (status == ExamStatus.APPROVED && rejectionReason != null) {
            throw new IllegalArgumentException("Approved exam cannot have a rejection reason");
        }
        if (status == ExamStatus.REJECTED && rejectionReason == null) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
    }

    private static List<ExamQuestion> copyAndValidateQuestions(
            List<ExamQuestion> questions
    ) {
        if (questions == null) {
            throw new IllegalArgumentException("Exam questions are required");
        }
        List<ExamQuestion> copies = new ArrayList<>(questions.size());
        for (ExamQuestion question : questions) {
            ExamQuestion copy = requireQuestion(question).copy();
            if (copies.stream().anyMatch(existing ->
                    existing.getQuestionId() == copy.getQuestionId())) {
                throw new IllegalArgumentException(
                        "Duplicate question: " + copy.getQuestionId()
                );
            }
            if (copies.stream().anyMatch(existing ->
                    existing.getOrderNumber() == copy.getOrderNumber())) {
                throw new IllegalArgumentException(
                        "Duplicate question order: " + copy.getOrderNumber()
                );
            }
            copies.add(copy);
        }
        copies.sort(Comparator.comparingInt(ExamQuestion::getOrderNumber));
        return copies;
    }

    private static ExamQuestion requireQuestion(ExamQuestion question) {
        if (question == null) {
            throw new IllegalArgumentException("Exam question is required");
        }
        return question;
    }

    private ExamQuestion findQuestion(int questionId) {
        return examQuestions.stream()
                .filter(selection -> selection.getQuestionId() == questionId)
                .findFirst()
                .orElse(null);
    }

    private void ensureNoDuplicateQuestion(ExamQuestion candidate, int ignoredQuestionId) {
        if (examQuestions.stream().anyMatch(existing ->
                existing.getQuestionId() != ignoredQuestionId
                        && existing.getQuestionId() == candidate.getQuestionId())) {
            throw new IllegalArgumentException(
                    "Duplicate question: " + candidate.getQuestionId()
            );
        }
    }

    private void ensureNoDuplicateOrder(int orderNumber, int ignoredQuestionId) {
        if (orderNumber <= 0) {
            throw new IllegalArgumentException("Question order must be positive");
        }
        if (examQuestions.stream().anyMatch(existing ->
                existing.getQuestionId() != ignoredQuestionId
                        && existing.getOrderNumber() == orderNumber)) {
            throw new IllegalArgumentException("Duplicate question order: " + orderNumber);
        }
    }

    private void requireEditable() {
        if (!isEditable()) {
            throw new IllegalStateException("Exam is not editable");
        }
    }

    private void requirePendingApproval() {
        if (status != ExamStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Exam is not pending approval");
        }
    }

    private void refreshTotalAndTouch() {
        totalScore = calculateTotalScoreValue();
        touch();
    }

    private void sortQuestions() {
        examQuestions.sort(Comparator.comparingInt(ExamQuestion::getOrderNumber));
    }

    private void touch() {
        LocalDateTime now = LocalDateTime.now();
        updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1);
    }

    private void touch(LocalDateTime timestamp) {
        updatedAt = timestamp;
    }

    private LocalDateTime requireMutationTimestamp(LocalDateTime timestamp,
                                                   String nullMessage) {
        LocalDateTime required = Objects.requireNonNull(timestamp, nullMessage);
        if (required.isBefore(createdAt) || required.isBefore(updatedAt)) {
            throw new IllegalArgumentException(
                    "Updated timestamp cannot precede current aggregate state"
            );
        }
        return required;
    }

    private static String normalizeExamCode(int examId, String examCode) {
        if (examId == 0 && (examCode == null || examCode.isBlank())) {
            return null;
        }
        String normalized = requireText(examCode, "Exam code is required")
                .toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{6}")) {
            throw new IllegalArgumentException("Exam code must be six uppercase alphanumeric characters");
        }
        return normalized;
    }

    private static int requirePositiveDuration(int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Exam duration must be positive");
        }
        return durationMinutes;
    }

    private static void requirePositiveReviewer(int reviewerUserId) {
        if (reviewerUserId <= 0) {
            throw new IllegalArgumentException("Reviewer user ID must be positive");
        }
    }

    private static String normalizeRejectionReason(ExamStatus status, String reason) {
        if (status == ExamStatus.REJECTED) {
            return requireText(reason, "Rejection reason is required");
        }
        if (reason != null && !reason.isBlank()) {
            return reason.trim();
        }
        return null;
    }

    private static String normalizeOptionalText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
