package hsts.server.repository;

import hsts.common.type.DifficultyLevel;
import hsts.common.type.QuestionStatus;
import hsts.common.type.QuestionType;
import hsts.server.entity.AnswerOption;
import hsts.server.entity.Question;
import org.junit.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class QuestionRepositoryEntityReadTest {
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 2, 1, 10, 15, 30);
    private static final LocalDateTime UPDATED_AT =
            LocalDateTime.of(2026, 2, 2, 11, 16, 31);

    @Test
    public void currentEntityReadScopesAndHydratesTheNormalizedAggregate() {
        QuestionRepositoryEntityJdbcTestSupport.Controller database = controller(
                rows(14, "MULTIPLE_CHOICE", "HARD", "INACTIVE", 3, 1, 2, 3, 4)
        );
        QuestionRepository repository = new QuestionRepository(database);

        Question question = repository.findCurrentEntityByIdForTeacher(1003, 14)
                .orElseThrow();

        QuestionRepositoryEntityJdbcTestSupport.StatementRecord statement =
                database.statement("entity-read");
        assertEquals(1003, statement.getExecutions().get(0).get(1));
        assertEquals(14, statement.getExecutions().get(0).get(2));
        assertTrue(statement.getSql().contains("JOIN teacher_courses"));
        assertTrue(statement.getSql().contains("tc.teacher_user_id = ?"));
        assertTrue(statement.getSql().contains("qv.version_no = q.current_version_no"));
        assertTrue(statement.getSql().contains("option_row.version_no = qv.version_no"));
        assertTrue(statement.getSql().contains("ORDER BY option_row.option_number"));

        assertEquals(14, question.getQuestionId());
        assertEquals("Normalized content", question.getContent());
        assertEquals("Algebra", question.getTopic());
        assertSame(QuestionType.MULTIPLE_CHOICE, question.getQuestionType());
        assertSame(DifficultyLevel.HARD, question.getDifficultyLevel());
        assertSame(QuestionStatus.INACTIVE, question.getQuestionStatus());
        assertEquals("image.png", question.getIllustrationPath());
        assertEquals(CREATED_AT, question.getCreatedAt());
        assertEquals(UPDATED_AT, question.getUpdatedAt());
        assertEquals(List.of(1, 2, 3, 4), optionNumbers(question));
        assertEquals(List.of("One", "Two", "Three", "Four"), optionTexts(question));
        assertEquals(3, question.getCorrectOptionNumber());
        assertFalse(question.getAnswerOptions().get(0).isCorrect());
        assertTrue(question.getAnswerOptions().get(2).isCorrect());
        assertThrows(
                UnsupportedOperationException.class,
                () -> question.getAnswerOptions().add(
                        new AnswerOption(1, "Replacement", false)
                )
        );
    }

    @Test
    public void missingOrUnauthorizedCurrentEntityReturnsEmpty() {
        QuestionRepositoryEntityJdbcTestSupport.Controller database = controller(List.of());
        QuestionRepository repository = new QuestionRepository(database);

        Optional<Question> result = repository.findCurrentEntityByIdForTeacher(1002, 404);

        assertFalse(result.isPresent());
    }

    @Test
    public void entityReadPreservesSqlFailureCause() {
        SQLException failure = new SQLException("read failed");
        QuestionRepositoryEntityJdbcTestSupport.Controller database = controller(List.of());
        database.failQuery(failure);
        QuestionRepository repository = new QuestionRepository(database);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.findCurrentEntityByIdForTeacher(1002, 14)
        );

        assertEquals("Failed to load assigned question by id", exception.getMessage());
        assertSame(failure, exception.getCause());
    }

    @Test
    public void entityReadRejectsMissingVersionAndMalformedEnums() {
        Map<String, Object> missingVersion = baseRow(14);
        missingVersion.put("version_no", null);
        QuestionRepositoryEntityJdbcTestSupport.Controller database = controller(
                List.of(missingVersion)
        );

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> new QuestionRepository(database)
                        .findCurrentEntityByIdForTeacher(1002, 14)
        );
        assertEquals("Current question version is missing: 14", missing.getMessage());

        for (String column : List.of("question_type", "difficulty", "status")) {
            List<Map<String, Object>> malformedRows = rows(
                    14,
                    "MULTIPLE_CHOICE",
                    "HARD",
                    "ACTIVE",
                    1,
                    1,
                    2,
                    3,
                    4
            );
            malformedRows.forEach(row -> row.put(column, "INVALID"));
            QuestionRepositoryEntityJdbcTestSupport.Controller malformed = controller(
                    malformedRows
            );
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new QuestionRepository(malformed)
                            .findCurrentEntityByIdForTeacher(1002, 14)
            );
        }
    }

    @Test
    public void entityReadRejectsMalformedNormalizedOptionsAndCorrectAnswer() {
        List<List<Map<String, Object>>> malformedOptions = List.of(
                rows(14, "MULTIPLE_CHOICE", "EASY", "ACTIVE", 1, 1, 2, 3),
                rows(14, "MULTIPLE_CHOICE", "EASY", "ACTIVE", 1, 1, 2, 2, 4),
                rows(14, "MULTIPLE_CHOICE", "EASY", "ACTIVE", 1, 1, 2, 3, 5)
        );
        for (List<Map<String, Object>> rows : malformedOptions) {
            QuestionRepositoryEntityJdbcTestSupport.Controller database = controller(rows);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new QuestionRepository(database)
                            .findCurrentEntityByIdForTeacher(1002, 14)
            );
        }

        List<Map<String, Object>> blankText = rows(
                14,
                "MULTIPLE_CHOICE",
                "EASY",
                "ACTIVE",
                1,
                1,
                2,
                3,
                4
        );
        blankText.get(1).put("option_text", "  ");
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuestionRepository(controller(blankText))
                        .findCurrentEntityByIdForTeacher(1002, 14)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new QuestionRepository(controller(rows(
                        14,
                        "MULTIPLE_CHOICE",
                        "EASY",
                        "ACTIVE",
                        5,
                        1,
                        2,
                        3,
                        4
                ))).findCurrentEntityByIdForTeacher(1002, 14)
        );
    }

    private static QuestionRepositoryEntityJdbcTestSupport.Controller controller(
            List<Map<String, Object>> rows
    ) {
        QuestionRepositoryEntityJdbcTestSupport.Controller controller =
                new QuestionRepositoryEntityJdbcTestSupport.Controller(true);
        controller.setQueryRows(rows);
        return controller;
    }

    private static List<Map<String, Object>> rows(
            int questionId,
            String type,
            String difficulty,
            String status,
            int correctOption,
            int... optionNumbers
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String[] texts = {"One", "Two", "Three", "Four", "Five"};
        for (int optionNumber : optionNumbers) {
            Map<String, Object> row = baseRow(questionId);
            row.put("question_type", type);
            row.put("difficulty", difficulty);
            row.put("status", status);
            row.put("correct_option_number", correctOption);
            row.put("option_number", optionNumber);
            row.put("option_text", texts[optionNumber - 1]);
            rows.add(row);
        }
        return rows;
    }

    private static Map<String, Object> baseRow(int questionId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("question_id", questionId);
        row.put("status", "ACTIVE");
        row.put("created_at", CREATED_AT);
        row.put("updated_at", UPDATED_AT);
        row.put("current_version_no", 7);
        row.put("version_no", 7);
        row.put("content", "Normalized content");
        row.put("topic", "Algebra");
        row.put("question_type", "MULTIPLE_CHOICE");
        row.put("difficulty", "MEDIUM");
        row.put("illustration_path", "image.png");
        row.put("correct_option_number", 1);
        row.put("option_number", 1);
        row.put("option_text", "One");
        return row;
    }

    private static List<Integer> optionNumbers(Question question) {
        return question.getAnswerOptions().stream()
                .map(AnswerOption::getOptionId)
                .toList();
    }

    private static List<String> optionTexts(Question question) {
        return question.getAnswerOptions().stream()
                .map(AnswerOption::getOptionText)
                .toList();
    }
}
