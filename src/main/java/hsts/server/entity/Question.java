package hsts.server.entity;

public class Question {
    private int questionId;
    private String content;
    private String topic;
    private String type;
    private String difficulty;
    private String status;

    public Question(int questionId, String content, String topic, String type, String difficulty, String status) {
        this.questionId = questionId;
        this.content = content;
        this.topic = topic;
        this.type = type;
        this.difficulty = difficulty;
        this.status = status;
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
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
