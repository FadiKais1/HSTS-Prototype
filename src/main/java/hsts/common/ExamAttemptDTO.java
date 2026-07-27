package hsts.common;

import hsts.common.type.SubmissionStatus;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public class ExamAttemptDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int submissionId;
    private final int executionId;
    private final String executionCode;
    private final int examId;
    private final int examVersionNo;
    private final String examTitle;
    private final String studentInstructions;
    private final LocalDateTime startedAt;
    private final LocalDateTime deadline;
    private final int allocatedDurationMinutes;
    private final int extraMinutes;
    private final long remainingSeconds;
    private final SubmissionStatus status;
    private final List<StudentExamQuestionDTO> questions;
    private final List<StudentAnswerDTO> answers;

    public ExamAttemptDTO(int submissionId, int executionId,
                          String executionCode, int examId, int examVersionNo,
                          String examTitle, String studentInstructions,
                          LocalDateTime startedAt, LocalDateTime deadline,
                          int allocatedDurationMinutes, int extraMinutes,
                          long remainingSeconds, SubmissionStatus status,
                          List<StudentExamQuestionDTO> questions,
                          List<StudentAnswerDTO> answers) {
        this.submissionId = submissionId;
        this.executionId = executionId;
        this.executionCode = executionCode;
        this.examId = examId;
        this.examVersionNo = examVersionNo;
        this.examTitle = examTitle;
        this.studentInstructions = studentInstructions;
        this.startedAt = startedAt;
        this.deadline = deadline;
        this.allocatedDurationMinutes = allocatedDurationMinutes;
        this.extraMinutes = extraMinutes;
        this.remainingSeconds = remainingSeconds;
        this.status = status;
        this.questions = List.copyOf(questions);
        this.answers = List.copyOf(answers);
    }

    public int getSubmissionId() { return submissionId; }
    public int getExecutionId() { return executionId; }
    public String getExecutionCode() { return executionCode; }
    public int getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public String getExamTitle() { return examTitle; }
    public String getStudentInstructions() { return studentInstructions; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getDeadline() { return deadline; }
    public int getAllocatedDurationMinutes() { return allocatedDurationMinutes; }
    public int getExtraMinutes() { return extraMinutes; }
    public long getRemainingSeconds() { return remainingSeconds; }
    public SubmissionStatus getStatus() { return status; }
    public List<StudentExamQuestionDTO> getQuestions() { return questions; }
    public List<StudentAnswerDTO> getAnswers() { return answers; }
}
