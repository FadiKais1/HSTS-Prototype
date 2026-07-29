package hsts.common;

import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public final class PrincipalQuestionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int questionId;
    private final int versionNo;
    private final int courseId;
    private final String courseName;
    private final int createdByUserId;
    private final String creatorName;
    private final String content;
    private final String topic;
    private final QuestionType type;
    private final DifficultyLevel difficulty;
    private final QuestionStatus status;
    private final String illustrationPath;
    private final List<String> answerOptions;
    private final int correctOptionNumber;
    private final LocalDateTime versionCreatedAt;
    private final LocalDateTime questionUpdatedAt;
    private final QuestionIllustrationDTO illustration;

    public PrincipalQuestionDTO(
            int questionId, int versionNo, int courseId, String courseName,
            int createdByUserId, String creatorName, String content, String topic,
            QuestionType type, DifficultyLevel difficulty, QuestionStatus status,
            String illustrationPath, List<String> answerOptions,
            int correctOptionNumber, LocalDateTime versionCreatedAt,
            LocalDateTime questionUpdatedAt
    ) {
        this(questionId, versionNo, courseId, courseName, createdByUserId,
                creatorName, content, topic, type, difficulty, status,
                illustrationPath, answerOptions, correctOptionNumber,
                versionCreatedAt, questionUpdatedAt, null);
    }

    public PrincipalQuestionDTO(
            int questionId, int versionNo, int courseId, String courseName,
            int createdByUserId, String creatorName, String content, String topic,
            QuestionType type, DifficultyLevel difficulty, QuestionStatus status,
            String illustrationPath, List<String> answerOptions,
            int correctOptionNumber, LocalDateTime versionCreatedAt,
            LocalDateTime questionUpdatedAt, QuestionIllustrationDTO illustration
    ) {
        this.questionId = questionId;
        this.versionNo = versionNo;
        this.courseId = courseId;
        this.courseName = courseName;
        this.createdByUserId = createdByUserId;
        this.creatorName = creatorName;
        this.content = content;
        this.topic = topic;
        this.type = type;
        this.difficulty = difficulty;
        this.status = status;
        this.illustrationPath = illustrationPath;
        this.answerOptions = List.copyOf(answerOptions);
        this.correctOptionNumber = correctOptionNumber;
        this.versionCreatedAt = versionCreatedAt;
        this.questionUpdatedAt = questionUpdatedAt;
        this.illustration = illustration;
    }

    public int getQuestionId() { return questionId; }
    public int getVersionNo() { return versionNo; }
    public int getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public int getCreatedByUserId() { return createdByUserId; }
    public String getCreatorName() { return creatorName; }
    public String getContent() { return content; }
    public String getTopic() { return topic; }
    public QuestionType getType() { return type; }
    public DifficultyLevel getDifficulty() { return difficulty; }
    public QuestionStatus getStatus() { return status; }
    public String getIllustrationPath() { return illustrationPath; }
    public List<String> getAnswerOptions() { return answerOptions; }
    public int getCorrectOptionNumber() { return correctOptionNumber; }
    public LocalDateTime getVersionCreatedAt() { return versionCreatedAt; }
    public LocalDateTime getQuestionUpdatedAt() { return questionUpdatedAt; }
    public QuestionIllustrationDTO getIllustration() { return illustration; }
}
