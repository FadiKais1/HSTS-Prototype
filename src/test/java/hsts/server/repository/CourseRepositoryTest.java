package hsts.server.repository;

import hsts.common.CourseSummaryDTO;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CourseRepositoryTest {
    private static final String ASSIGNED_COURSES_SQL = """
            SELECT c.course_id,
                   c.subject_id,
                   c.course_code,
                   c.name AS course_name,
                   s.name AS subject_name,
                   c.grade_level,
                   c.school_year
            FROM teacher_courses tc
            JOIN courses c ON c.course_id = tc.course_id
            JOIN subjects s ON s.subject_id = c.subject_id
            WHERE tc.teacher_user_id = ?
            ORDER BY s.name, c.name, c.course_id
            """;

    private static final String ASSIGNMENT_CHECK_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM teacher_courses
                WHERE teacher_user_id = ? AND course_id = ?
            ) AS assigned
            """;

    private static final String COURSE_BY_ID_SQL = """
            SELECT c.course_id,
                   c.subject_id,
                   c.course_code,
                   c.name AS course_name,
                   s.name AS subject_name,
                   c.grade_level,
                   c.school_year
            FROM courses c
            JOIN subjects s ON s.subject_id = c.subject_id
            WHERE c.course_id = ?
            """;

    @Test
    public void findAssignedToTeacherUsesExactJoinAndMapsRowsInReturnedOrder() {
        RecordingDatabaseController databaseController = new RecordingDatabaseController(List.of(
                courseRow(8, 2, "ALG-201", "Algebra II", "Mathematics", "10", "2026"),
                courseRow(3, 2, "GEO-101", "Geometry", "Mathematics", "9", "2026")
        ));
        CourseRepository repository = new CourseRepository(databaseController);

        List<CourseSummaryDTO> result = repository.findAssignedToTeacher(1002);

        assertEquals(2, result.size());
        assertCourse(result.get(0), 8, 2, "ALG-201", "Algebra II",
                "Mathematics", "10", "2026");
        assertCourse(result.get(1), 3, 2, "GEO-101", "Geometry",
                "Mathematics", "9", "2026");
        assertEquals(normalizeSql(ASSIGNED_COURSES_SQL), normalizeSql(databaseController.getSql()));
        assertEquals(1002, databaseController.getParameter(1));
    }

    @Test
    public void findAssignedToTeacherReturnsEmptyListWhenNoRowsExist() {
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(List.of());
        CourseRepository repository = new CourseRepository(databaseController);

        List<CourseSummaryDTO> result = repository.findAssignedToTeacher(4040);

        assertTrue(result.isEmpty());
        assertEquals(4040, databaseController.getParameter(1));
    }

    @Test
    public void isAssignedToTeacherUsesExactPairAndReturnsTrue() {
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(List.of(assignmentRow(true)));
        CourseRepository repository = new CourseRepository(databaseController);

        assertTrue(repository.isAssignedToTeacher(1002, 7));
        assertEquals(normalizeSql(ASSIGNMENT_CHECK_SQL), normalizeSql(databaseController.getSql()));
        assertEquals(1002, databaseController.getParameter(1));
        assertEquals(7, databaseController.getParameter(2));
    }

    @Test
    public void isAssignedToTeacherReturnsFalseForMissingPair() {
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(List.of(assignmentRow(false)));
        CourseRepository repository = new CourseRepository(databaseController);

        assertFalse(repository.isAssignedToTeacher(1003, 99));
        assertEquals(1003, databaseController.getParameter(1));
        assertEquals(99, databaseController.getParameter(2));
    }

    @Test
    public void loadFailureIsWrappedWithOriginalCause() {
        SQLException sqlException = new SQLException("course query failed");
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(List.of());
        databaseController.setQueryFailure(sqlException);
        CourseRepository repository = new CourseRepository(databaseController);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.findAssignedToTeacher(1002)
        );

        assertEquals("Failed to load assigned courses", exception.getMessage());
        assertSame(sqlException, exception.getCause());
    }

    @Test
    public void assignmentFailureIsWrappedWithOriginalCause() {
        SQLException sqlException = new SQLException("assignment query failed");
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(List.of());
        databaseController.setQueryFailure(sqlException);
        CourseRepository repository = new CourseRepository(databaseController);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.isAssignedToTeacher(1002, 1)
        );

        assertEquals("Failed to verify course assignment", exception.getMessage());
        assertSame(sqlException, exception.getCause());
    }

    @Test
    public void findByIdUsesPreparedExactLookupAndMapsAuthoritativeCourse() {
        RecordingDatabaseController databaseController =
                new RecordingDatabaseController(List.of(courseRow(
                        31, 4, "BIO-101", "Biology", "Sciences", "10", "2026"
                )));

        Optional<CourseSummaryDTO> result =
                new CourseRepository(databaseController).findById(31);

        assertTrue(result.isPresent());
        assertCourse(result.orElseThrow(), 31, 4, "BIO-101", "Biology",
                "Sciences", "10", "2026");
        assertEquals(normalizeSql(COURSE_BY_ID_SQL),
                normalizeSql(databaseController.getSql()));
        assertEquals(31, databaseController.getParameter(1));
    }

    @Test
    public void findByIdReturnsEmptyAndWrapsSqlFailures() {
        RecordingDatabaseController empty = new RecordingDatabaseController(List.of());
        assertTrue(new CourseRepository(empty).findById(404).isEmpty());

        SQLException cause = new SQLException("course lookup failed");
        RecordingDatabaseController failed = new RecordingDatabaseController(List.of());
        failed.setQueryFailure(cause);
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new CourseRepository(failed).findById(31)
        );
        assertEquals("Failed to load course", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    private static Map<String, Object> courseRow(int courseId, int subjectId,
                                                  String courseCode, String courseName,
                                                  String subjectName, String gradeLevel,
                                                  String schoolYear) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("course_id", courseId);
        row.put("subject_id", subjectId);
        row.put("course_code", courseCode);
        row.put("course_name", courseName);
        row.put("subject_name", subjectName);
        row.put("grade_level", gradeLevel);
        row.put("school_year", schoolYear);
        return row;
    }

    private static Map<String, Object> assignmentRow(boolean assigned) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("assigned", assigned);
        return row;
    }

    private static void assertCourse(CourseSummaryDTO course, int courseId, int subjectId,
                                     String courseCode, String courseName, String subjectName,
                                     String gradeLevel, String schoolYear) {
        assertEquals(courseId, course.getCourseId());
        assertEquals(subjectId, course.getSubjectId());
        assertEquals(courseCode, course.getCourseCode());
        assertEquals(courseName, course.getCourseName());
        assertEquals(subjectName, course.getSubjectName());
        assertEquals(gradeLevel, course.getGradeLevel());
        assertEquals(schoolYear, course.getSchoolYear());
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private static final class RecordingDatabaseController extends DatabaseController {
        private final List<Map<String, Object>> rows;
        private final Map<Integer, Object> parameters = new HashMap<>();
        private String sql;
        private SQLException queryFailure;

        private RecordingDatabaseController(List<Map<String, Object>> rows) {
            this.rows = new ArrayList<>(rows);
        }

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if ("prepareStatement".equals(method.getName())) {
                            sql = (String) arguments[0];
                            return preparedStatement();
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private PreparedStatement preparedStatement() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, arguments) -> {
                        if ("setInt".equals(method.getName())) {
                            parameters.put((Integer) arguments[0], arguments[1]);
                            return null;
                        }
                        if ("executeQuery".equals(method.getName())) {
                            if (queryFailure != null) {
                                throw queryFailure;
                            }
                            return resultSet();
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private ResultSet resultSet() {
            int[] index = {-1};
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, arguments) -> {
                        if ("next".equals(method.getName())) {
                            index[0]++;
                            return index[0] < rows.size();
                        }
                        if ("getInt".equals(method.getName())) {
                            return (Integer) currentRow(rows, index[0]).get((String) arguments[0]);
                        }
                        if ("getString".equals(method.getName())) {
                            return (String) currentRow(rows, index[0]).get((String) arguments[0]);
                        }
                        if ("getBoolean".equals(method.getName())) {
                            return (Boolean) currentRow(rows, index[0]).get((String) arguments[0]);
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private static Map<String, Object> currentRow(List<Map<String, Object>> rows, int index) {
            return rows.get(index);
        }

        private String getSql() {
            return sql;
        }

        private Object getParameter(int index) {
            return parameters.get(index);
        }

        private void setQueryFailure(SQLException queryFailure) {
            this.queryFailure = queryFailure;
        }

        private static Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0.0F;
            }
            if (returnType == double.class) {
                return 0.0D;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }
    }
}
