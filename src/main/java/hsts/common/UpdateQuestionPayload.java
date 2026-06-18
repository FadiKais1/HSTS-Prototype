package hsts.common;

import java.io.Serializable;

public class UpdateQuestionPayload implements Serializable {
    private static final long serialVersionUID = 2L;

    private final int questionId;
    private final String content;
    private final String topic;
    private final String difficulty;
    private final String status;
    private final String illustrationPath;
    private final String answerOption1;
    private final String answerOption2;
    private final String answerOption3;
    private final String answerOption4;
    private final int correctOptionNumber;

    public UpdateQuestionPayload(int questionId, String content) {
        this(questionId, content, "", "EASY", "ACTIVE", "", "", "", "", "", 1);
    }

    public UpdateQuestionPayload(int questionId, String content, String topic, String difficulty, String status,
                                 String illustrationPath, String answerOption1, String answerOption2,
                                 String answerOption3, String answerOption4, int correctOptionNumber) {
        this.questionId = questionId;
        this.content = content;
        this.topic = topic;
        this.difficulty = difficulty;
        this.status = status;
        this.illustrationPath = illustrationPath;
        this.answerOption1 = answerOption1;
        this.answerOption2 = answerOption2;
        this.answerOption3 = answerOption3;
        this.answerOption4 = answerOption4;
        this.correctOptionNumber = correctOptionNumber;
    }

    public int getQuestionId() { return questionId; }
    public String getContent() { return content; }
    public String getTopic() { return topic; }
    public String getDifficulty() { return difficulty; }
    public String getStatus() { return status; }
    public String getIllustrationPath() { return illustrationPath; }
    public String getAnswerOption1() { return answerOption1; }
    public String getAnswerOption2() { return answerOption2; }
    public String getAnswerOption3() { return answerOption3; }
    public String getAnswerOption4() { return answerOption4; }
    public int getCorrectOptionNumber() { return correctOptionNumber; }
}
