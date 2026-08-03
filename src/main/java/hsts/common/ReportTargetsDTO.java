package hsts.common;

import java.io.Serializable;
import java.util.List;

/**
 * Everything the Principal can run a report about, fetched in one round trip so
 * the Reports page can fill all of its pickers from a single request.
 */
public final class ReportTargetsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<ReportTargetOptionDTO> teachers;
    private final List<ReportTargetOptionDTO> courses;
    private final List<ReportTargetOptionDTO> students;
    private final List<ReportTargetOptionDTO> executions;

    public ReportTargetsDTO(List<ReportTargetOptionDTO> teachers,
                            List<ReportTargetOptionDTO> courses,
                            List<ReportTargetOptionDTO> students,
                            List<ReportTargetOptionDTO> executions) {
        this.teachers = List.copyOf(teachers);
        this.courses = List.copyOf(courses);
        this.students = List.copyOf(students);
        this.executions = List.copyOf(executions);
    }

    public List<ReportTargetOptionDTO> getTeachers() {
        return teachers;
    }

    public List<ReportTargetOptionDTO> getCourses() {
        return courses;
    }

    public List<ReportTargetOptionDTO> getStudents() {
        return students;
    }

    public List<ReportTargetOptionDTO> getExecutions() {
        return executions;
    }
}
