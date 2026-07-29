package hsts.common;

import hsts.common.type.DifficultyLevel;

import java.io.Serializable;

public class CreateQuestionPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int courseId;
    private final String content;
    private final String topic;
    private final DifficultyLevel difficulty;
    private final String illustrationPath;
    private final String answerOption1;
    private final String answerOption2;
    private final String answerOption3;
    private final String answerOption4;
    private final int correctOptionNumber;
    private final QuestionIllustrationUploadPayload illustrationUpload;

    public CreateQuestionPayload(int courseId, String content, String topic, DifficultyLevel difficulty,
                                 String illustrationPath, String answerOption1, String answerOption2,
                                 String answerOption3, String answerOption4, int correctOptionNumber) {
        this(courseId, content, topic, difficulty, illustrationPath,
                answerOption1, answerOption2, answerOption3, answerOption4,
                correctOptionNumber, null);
    }

    public CreateQuestionPayload(int courseId, String content, String topic,
                                 DifficultyLevel difficulty, String illustrationPath,
                                 String answerOption1, String answerOption2,
                                 String answerOption3, String answerOption4,
                                 int correctOptionNumber,
                                 QuestionIllustrationUploadPayload illustrationUpload) {
        this.courseId = courseId;
        this.content = content;
        this.topic = topic;
        this.difficulty = difficulty;
        this.illustrationPath = illustrationPath;
        this.answerOption1 = answerOption1;
        this.answerOption2 = answerOption2;
        this.answerOption3 = answerOption3;
        this.answerOption4 = answerOption4;
        this.correctOptionNumber = correctOptionNumber;
        this.illustrationUpload = illustrationUpload;
    }

    public int getCourseId() { return courseId; }
    public String getContent() { return content; }
    public String getTopic() { return topic; }
    public DifficultyLevel getDifficulty() { return difficulty; }
    public String getIllustrationPath() { return illustrationPath; }
    public String getAnswerOption1() { return answerOption1; }
    public String getAnswerOption2() { return answerOption2; }
    public String getAnswerOption3() { return answerOption3; }
    public String getAnswerOption4() { return answerOption4; }
    public int getCorrectOptionNumber() { return correctOptionNumber; }
    public QuestionIllustrationUploadPayload getIllustrationUpload() {
        return illustrationUpload;
    }
}
