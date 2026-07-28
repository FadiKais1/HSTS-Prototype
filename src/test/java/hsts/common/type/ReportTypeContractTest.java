package hsts.common.type;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class ReportTypeContractTest {
    @Test
    public void valuesAreExactAndOrdered() {
        assertArrayEquals(
                new ReportType[]{
                        ReportType.TEACHER_EXAMS,
                        ReportType.COURSE_EXAMS,
                        ReportType.STUDENT_EXAMS,
                        ReportType.EXAM_EXECUTION
                },
                ReportType.values()
        );
    }
}
