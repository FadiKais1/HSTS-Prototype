package hsts.common;

import java.io.Serializable;

public class CourseSummaryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int courseId;
    private final int subjectId;
    private final String courseCode;
    private final String courseName;
    private final String subjectName;
    private final String gradeLevel;
    private final String schoolYear;

    public CourseSummaryDTO(int courseId, int subjectId, String courseCode, String courseName,
                            String subjectName, String gradeLevel, String schoolYear) {
        this.courseId = courseId;
        this.subjectId = subjectId;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.subjectName = subjectName;
        this.gradeLevel = gradeLevel;
        this.schoolYear = schoolYear;
    }

    public int getCourseId() { return courseId; }
    public int getSubjectId() { return subjectId; }
    public String getCourseCode() { return courseCode; }
    public String getCourseName() { return courseName; }
    public String getSubjectName() { return subjectName; }
    public String getGradeLevel() { return gradeLevel; }
    public String getSchoolYear() { return schoolYear; }
}
