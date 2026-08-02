package hsts.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Request to generate an exam automatically from a breakdown of question
 * criteria.
 *
 * <p>Each {@link QuestionCriterion} names a topic, a difficulty level and how
 * many questions of that combination the exam should contain, so a paper can
 * be composed from several topics at several difficulty levels.</p>
 *
 * <p>This is a separate request from {@link GenerateExamPayload}, which keeps
 * its original single topic and difficulty shape. Both are supported: the
 * single criterion request remains the simple case, and this one covers the
 * breakdown case.</p>
 */
public class GenerateExamBreakdownPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int courseId;
    private final String title;
    private final int durationMinutes;
    private final String teacherNotes;
    private final String studentInstructions;
    private final List<QuestionCriterion> criteria;

    public GenerateExamBreakdownPayload(int courseId, String title, int durationMinutes,
                                        String teacherNotes, String studentInstructions,
                                        List<QuestionCriterion> criteria) {
        this.courseId = courseId;
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.teacherNotes = teacherNotes;
        this.studentInstructions = studentInstructions;
        this.criteria = criteria == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(criteria));
    }

    public int getCourseId() { return courseId; }
    public String getTitle() { return title; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getTeacherNotes() { return teacherNotes; }
    public String getStudentInstructions() { return studentInstructions; }

    /** The breakdown lines. Never null; may be empty, which the server rejects. */
    public List<QuestionCriterion> getCriteria() { return criteria; }

    /** Total number of questions requested across every criterion. */
    public int getTotalQuestionCount() {
        int total = 0;
        for (QuestionCriterion criterion : criteria) {
            total += criterion.getQuestionCount();
        }
        return total;
    }
}
