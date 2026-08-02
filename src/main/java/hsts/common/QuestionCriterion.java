package hsts.common;

import hsts.common.type.DifficultyLevel;

import java.io.Serializable;
import java.util.Objects;

/**
 * One line of an automatic exam generation breakdown: how many questions of a
 * given topic and difficulty the generated exam should contain.
 *
 * <p>Several criteria combine to satisfy the requirement that an automatically
 * generated exam is defined by a total question count broken down by topic and
 * by difficulty level, rather than by a single topic and difficulty.</p>
 *
 * <p>This type deliberately performs no validation. It is a transport record,
 * and the server validates the whole request so that every rejection produces a
 * single, consistent error message.</p>
 */
public final class QuestionCriterion implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String topic;
    private final DifficultyLevel difficulty;
    private final int questionCount;

    public QuestionCriterion(String topic, DifficultyLevel difficulty, int questionCount) {
        this.topic = topic;
        this.difficulty = difficulty;
        this.questionCount = questionCount;
    }

    public String getTopic() {
        return topic;
    }

    public DifficultyLevel getDifficulty() {
        return difficulty;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    /** Human readable form used in validation messages and in the builder table. */
    public String describe() {
        return (questionCount + " x "
                + (difficulty == null ? "?" : difficulty.name())
                + " " + (topic == null || topic.isBlank() ? "?" : topic.trim()));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuestionCriterion criterion)) {
            return false;
        }
        return questionCount == criterion.questionCount
                && difficulty == criterion.difficulty
                && Objects.equals(topic, criterion.topic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, difficulty, questionCount);
    }

    @Override
    public String toString() {
        return "QuestionCriterion{" + describe() + '}';
    }
}
