package hsts.server.entity;

import java.time.LocalDateTime;

public class BotMessage {
    private int messageId;
    private String questionText;
    private String answerText;
    private LocalDateTime createdAt;
    private int studentId;
    private int courseId;
}
