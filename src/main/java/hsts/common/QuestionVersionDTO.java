package hsts.common;

import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionType;

import java.io.Serializable;
import java.time.LocalDateTime;

public class QuestionVersionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int questionId;
    private final int versionNo;
    private final int courseId;
    private final String content;
    private final String topic;
    private final QuestionType type;
    private final DifficultyLevel difficulty;
    private final String illustrationPath;
    private final String answerOption1;
    private final String answerOption2;
    private final String answerOption3;
    private final String answerOption4;
    private final int correctOptionNumber;
    private final int createdByUserId;
    private final LocalDateTime createdAt;
    private final QuestionIllustrationDTO illustration;

    public QuestionVersionDTO(int questionId, int versionNo, int courseId, String content, String topic,
                              QuestionType type, DifficultyLevel difficulty, String illustrationPath,
                              String answerOption1, String answerOption2, String answerOption3,
                              String answerOption4, int correctOptionNumber, int createdByUserId,
                              LocalDateTime createdAt) {
        this(questionId, versionNo, courseId, content, topic, type, difficulty,
                illustrationPath, answerOption1, answerOption2, answerOption3,
                answerOption4, correctOptionNumber, createdByUserId, createdAt,
                null);
    }

    public QuestionVersionDTO(int questionId, int versionNo, int courseId,
                              String content, String topic, QuestionType type,
                              DifficultyLevel difficulty, String illustrationPath,
                              String answerOption1, String answerOption2,
                              String answerOption3, String answerOption4,
                              int correctOptionNumber, int createdByUserId,
                              LocalDateTime createdAt,
                              QuestionIllustrationDTO illustration) {
        this.questionId = questionId;
        this.versionNo = versionNo;
        this.courseId = courseId;
        this.content = content;
        this.topic = topic;
        this.type = type;
        this.difficulty = difficulty;
        this.illustrationPath = illustrationPath;
        this.answerOption1 = answerOption1;
        this.answerOption2 = answerOption2;
        this.answerOption3 = answerOption3;
        this.answerOption4 = answerOption4;
        this.correctOptionNumber = correctOptionNumber;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.illustration = illustration;
    }

    public int getQuestionId() { return questionId; }
    public int getVersionNo() { return versionNo; }
    public int getCourseId() { return courseId; }
    public String getContent() { return content; }
    public String getTopic() { return topic; }
    public QuestionType getType() { return type; }
    public DifficultyLevel getDifficulty() { return difficulty; }
    public String getIllustrationPath() { return illustrationPath; }
    public String getAnswerOption1() { return answerOption1; }
    public String getAnswerOption2() { return answerOption2; }
    public String getAnswerOption3() { return answerOption3; }
    public String getAnswerOption4() { return answerOption4; }
    public int getCorrectOptionNumber() { return correctOptionNumber; }
    public int getCreatedByUserId() { return createdByUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public QuestionIllustrationDTO getIllustration() { return illustration; }
}
