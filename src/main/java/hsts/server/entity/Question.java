package hsts.server.entity;

import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;

import java.time.LocalDateTime;
import java.util.List;

public class Question {
    private int questionId;
    private String content;
    private QuestionType type;
    private DifficultyLevel difficulty;
    private QuestionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String topic;

    // RELATIONSHIP-DERIVED: Question contains four answer options.
    private List<AnswerOption> answerOptions;

    // RELATIONSHIP-DERIVED: Question is answered by zero or more student answers.
    private List<StudentAnswer> studentAnswers;

    // COMPATIBILITY-ONLY
    private String prototypeType;
    private String prototypeDifficulty;
    private String prototypeStatus;
    private String illustrationPath;
    private String answerOption1;
    private String answerOption2;
    private String answerOption3;
    private String answerOption4;
    private int correctOptionNumber;

    public Question(int questionId, String content, String topic, String type, String difficulty, String status,
                    String illustrationPath, String answerOption1, String answerOption2, String answerOption3,
                    String answerOption4, int correctOptionNumber) {
        this.questionId = questionId;
        this.content = content;
        this.topic = topic;
        this.prototypeType = type;
        this.prototypeDifficulty = difficulty;
        this.prototypeStatus = status;
        this.illustrationPath = illustrationPath;
        this.answerOption1 = answerOption1;
        this.answerOption2 = answerOption2;
        this.answerOption3 = answerOption3;
        this.answerOption4 = answerOption4;
        this.correctOptionNumber = correctOptionNumber;
    }

    public int getQuestionId() {
        return questionId;
    }

    public void setQuestionId(int questionId) {
        this.questionId = questionId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getType() {
        return prototypeType;
    }

    public void setType(String type) {
        this.prototypeType = type;
    }

    public String getDifficulty() {
        return prototypeDifficulty;
    }

    public void setDifficulty(String difficulty) {
        this.prototypeDifficulty = difficulty;
    }

    public String getStatus() {
        return prototypeStatus;
    }

    public void setStatus(String status) {
        this.prototypeStatus = status;
    }

    public String getIllustrationPath() {
        return illustrationPath;
    }

    public void setIllustrationPath(String illustrationPath) {
        this.illustrationPath = illustrationPath;
    }

    public String getAnswerOption1() {
        return answerOption1;
    }

    public void setAnswerOption1(String answerOption1) {
        this.answerOption1 = answerOption1;
    }

    public String getAnswerOption2() {
        return answerOption2;
    }

    public void setAnswerOption2(String answerOption2) {
        this.answerOption2 = answerOption2;
    }

    public String getAnswerOption3() {
        return answerOption3;
    }

    public void setAnswerOption3(String answerOption3) {
        this.answerOption3 = answerOption3;
    }

    public String getAnswerOption4() {
        return answerOption4;
    }

    public void setAnswerOption4(String answerOption4) {
        this.answerOption4 = answerOption4;
    }

    public int getCorrectOptionNumber() {
        return correctOptionNumber;
    }

    public void setCorrectOptionNumber(int correctOptionNumber) {
        this.correctOptionNumber = correctOptionNumber;
    }

    public void updateContent(String content) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void activate() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void deactivate() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void addAnswerOption(AnswerOption option) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void removeAnswerOption(int optionId) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public boolean isActive() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }
}
