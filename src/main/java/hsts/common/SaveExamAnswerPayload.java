package hsts.common;

import java.io.Serializable;

public class SaveExamAnswerPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int submissionId;
    private final int questionId;
    private final int selectedOptionNumber;

    public SaveExamAnswerPayload(int submissionId, int questionId,
                                 int selectedOptionNumber) {
        this.submissionId = submissionId;
        this.questionId = questionId;
        this.selectedOptionNumber = selectedOptionNumber;
    }

    public int getSubmissionId() { return submissionId; }
    public int getQuestionId() { return questionId; }
    public int getSelectedOptionNumber() { return selectedOptionNumber; }
}
