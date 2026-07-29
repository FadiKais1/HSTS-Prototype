package hsts.common;

import java.io.Serializable;

public class ExamQuestionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int questionId;
    private final int questionVersionNo;
    private final int orderNumber;
    private final double score;
    private final String content;
    private final String topic;
    private final String difficulty;
    private final String illustrationPath;
    private final String answerOption1;
    private final String answerOption2;
    private final String answerOption3;
    private final String answerOption4;
    private final int correctOptionNumber;
    private final QuestionIllustrationDTO illustration;

    public ExamQuestionDTO(int questionId, int questionVersionNo, int orderNumber,
                           double score, String content, String topic, String difficulty,
                           String illustrationPath, String answerOption1,
                           String answerOption2, String answerOption3,
                           String answerOption4, int correctOptionNumber) {
        this(questionId, questionVersionNo, orderNumber, score, content, topic,
                difficulty, illustrationPath, answerOption1, answerOption2,
                answerOption3, answerOption4, correctOptionNumber, null);
    }

    public ExamQuestionDTO(int questionId, int questionVersionNo, int orderNumber,
                           double score, String content, String topic,
                           String difficulty, String illustrationPath,
                           String answerOption1, String answerOption2,
                           String answerOption3, String answerOption4,
                           int correctOptionNumber,
                           QuestionIllustrationDTO illustration) {
        this.questionId = questionId;
        this.questionVersionNo = questionVersionNo;
        this.orderNumber = orderNumber;
        this.score = score;
        this.content = content;
        this.topic = topic;
        this.difficulty = difficulty;
        this.illustrationPath = illustrationPath;
        this.answerOption1 = answerOption1;
        this.answerOption2 = answerOption2;
        this.answerOption3 = answerOption3;
        this.answerOption4 = answerOption4;
        this.correctOptionNumber = correctOptionNumber;
        this.illustration = illustration;
    }

    public int getQuestionId() { return questionId; }
    public int getQuestionVersionNo() { return questionVersionNo; }
    public int getOrderNumber() { return orderNumber; }
    public double getScore() { return score; }
    public String getContent() { return content; }
    public String getTopic() { return topic; }
    public String getDifficulty() { return difficulty; }
    public String getIllustrationPath() { return illustrationPath; }
    public String getAnswerOption1() { return answerOption1; }
    public String getAnswerOption2() { return answerOption2; }
    public String getAnswerOption3() { return answerOption3; }
    public String getAnswerOption4() { return answerOption4; }
    public int getCorrectOptionNumber() { return correctOptionNumber; }
    public QuestionIllustrationDTO getIllustration() { return illustration; }
}
