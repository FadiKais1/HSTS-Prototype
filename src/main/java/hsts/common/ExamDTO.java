package hsts.common;

import hsts.common.type.ExamStatus;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public class ExamDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int examId;
    private final String examCode;
    private final int courseId;
    private final String courseName;
    private final int subjectId;
    private final String subjectName;
    private final int createdByUserId;
    private final String creatorName;
    private final int versionNo;
    private final String title;
    private final int durationMinutes;
    private final String teacherNotes;
    private final String studentInstructions;
    private final double totalScore;
    private final ExamStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime submittedAt;
    private final Integer reviewedByUserId;
    private final String reviewerName;
    private final LocalDateTime reviewedAt;
    private final String rejectionReason;
    private final List<ExamQuestionDTO> questions;

    public ExamDTO(int examId, String examCode, int courseId, String courseName,
                   int subjectId, String subjectName, int createdByUserId,
                   String creatorName, int versionNo, String title,
                   int durationMinutes, String teacherNotes, String studentInstructions,
                   double totalScore, ExamStatus status, LocalDateTime createdAt,
                   LocalDateTime submittedAt, Integer reviewedByUserId,
                   String reviewerName, LocalDateTime reviewedAt,
                   String rejectionReason, List<ExamQuestionDTO> questions) {
        this.examId = examId;
        this.examCode = examCode;
        this.courseId = courseId;
        this.courseName = courseName;
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.createdByUserId = createdByUserId;
        this.creatorName = creatorName;
        this.versionNo = versionNo;
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.teacherNotes = teacherNotes;
        this.studentInstructions = studentInstructions;
        this.totalScore = totalScore;
        this.status = status;
        this.createdAt = createdAt;
        this.submittedAt = submittedAt;
        this.reviewedByUserId = reviewedByUserId;
        this.reviewerName = reviewerName;
        this.reviewedAt = reviewedAt;
        this.rejectionReason = rejectionReason;
        this.questions = List.copyOf(questions);
    }

    public int getExamId() { return examId; }
    public String getExamCode() { return examCode; }
    public int getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public int getSubjectId() { return subjectId; }
    public String getSubjectName() { return subjectName; }
    public int getCreatedByUserId() { return createdByUserId; }
    public String getCreatorName() { return creatorName; }
    public int getVersionNo() { return versionNo; }
    public String getTitle() { return title; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getTeacherNotes() { return teacherNotes; }
    public String getStudentInstructions() { return studentInstructions; }
    public double getTotalScore() { return totalScore; }
    public ExamStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public Integer getReviewedByUserId() { return reviewedByUserId; }
    public String getReviewerName() { return reviewerName; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public List<ExamQuestionDTO> getQuestions() { return questions; }
}
