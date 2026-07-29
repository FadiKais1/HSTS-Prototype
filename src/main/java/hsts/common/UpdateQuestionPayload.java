package hsts.common;

import hsts.common.type.QuestionIllustrationChange;

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
    private final int expectedVersionNo;
    private final QuestionIllustrationChange illustrationChange;
    private final QuestionIllustrationUploadPayload illustrationUpload;

    public UpdateQuestionPayload(int questionId, String content) {
        this(questionId, content, "", "EASY", "ACTIVE", "", "", "", "", "", 1);
    }

    public UpdateQuestionPayload(int questionId, String content, String topic, String difficulty, String status,
                                 String illustrationPath, String answerOption1, String answerOption2,
                                 String answerOption3, String answerOption4, int correctOptionNumber) {
        this(questionId, content, topic, difficulty, status, illustrationPath,
                answerOption1, answerOption2, answerOption3, answerOption4,
                correctOptionNumber, 0);
    }

    public UpdateQuestionPayload(int questionId, String content, String topic, String difficulty, String status,
                                 String illustrationPath, String answerOption1, String answerOption2,
                                 String answerOption3, String answerOption4, int correctOptionNumber,
                                 int expectedVersionNo) {
        this(questionId, content, topic, difficulty, status, illustrationPath,
                answerOption1, answerOption2, answerOption3, answerOption4,
                correctOptionNumber, expectedVersionNo,
                QuestionIllustrationChange.KEEP, null);
    }

    public UpdateQuestionPayload(
            int questionId, String content, String topic, String difficulty,
            String status, String illustrationPath, String answerOption1,
            String answerOption2, String answerOption3, String answerOption4,
            int correctOptionNumber, int expectedVersionNo,
            QuestionIllustrationChange illustrationChange,
            QuestionIllustrationUploadPayload illustrationUpload
    ) {
        if (illustrationChange == null) {
            throw new IllegalArgumentException(
                    "Question illustration change is required"
            );
        }
        if (illustrationChange == QuestionIllustrationChange.REPLACE
                && illustrationUpload == null) {
            throw new IllegalArgumentException(
                    "Question illustration content is required"
            );
        }
        if (illustrationChange != QuestionIllustrationChange.REPLACE
                && illustrationUpload != null) {
            throw new IllegalArgumentException(
                    "Question illustration upload is invalid"
            );
        }
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
        this.expectedVersionNo = expectedVersionNo;
        this.illustrationChange = illustrationChange;
        this.illustrationUpload = illustrationUpload;
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
    public int getExpectedVersionNo() { return expectedVersionNo; }
    public QuestionIllustrationChange getIllustrationChange() {
        return illustrationChange;
    }
    public QuestionIllustrationUploadPayload getIllustrationUpload() {
        return illustrationUpload;
    }
}
