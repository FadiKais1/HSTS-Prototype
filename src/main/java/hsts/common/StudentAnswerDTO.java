package hsts.common;

import java.io.Serializable;
import java.time.LocalDateTime;

public class StudentAnswerDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int questionId;
    private final Integer selectedOptionNumber;
    private final LocalDateTime updatedAt;

    public StudentAnswerDTO(int questionId, Integer selectedOptionNumber,
                            LocalDateTime updatedAt) {
        this.questionId = questionId;
        this.selectedOptionNumber = selectedOptionNumber;
        this.updatedAt = updatedAt;
    }

    public int getQuestionId() { return questionId; }
    public Integer getSelectedOptionNumber() { return selectedOptionNumber; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
