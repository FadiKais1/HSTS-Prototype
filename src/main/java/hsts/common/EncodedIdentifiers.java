package hsts.common;

import java.util.regex.Pattern;

/**
 * Single source of truth for the encoded business identifiers required by the
 * customer story.
 *
 * <p>Requirements 33 and 34 &mdash; every question carries a unique five digit
 * identifier. The first two digits are the course number; the last three are the
 * question number within that course.</p>
 *
 * <p>Requirements 38 and 39 &mdash; every exam carries a unique six digit
 * identifier built from the two digit subject number, the two digit course
 * number and the two digit exam number, in that order.</p>
 *
 * <p>Both read from the broadest container inwards: a subject holds courses, a
 * course holds questions and exams. Reading an identifier left to right narrows
 * from where the material lives to which item it is.</p>
 *
 * <p>Course and subject numbers originate in the external school administration
 * system (requirement 19); this class never invents them. Question and exam
 * numbers are allocated by HSTS as the next free number within their scope.</p>
 *
 * <p>These identifiers are business keys, not primary keys. {@code question_id}
 * and {@code exam_id} remain surrogate auto-increment integers so that existing
 * foreign keys are untouched; the encoded values live alongside them in
 * {@code questions.question_code} and {@code exams.exam_code}.</p>
 */
public final class EncodedIdentifiers {

    /** Total digit count of a question identifier (requirement 33). */
    public static final int QUESTION_CODE_LENGTH = 5;

    /** Total digit count of an exam identifier (requirement 38). */
    public static final int EXAM_CODE_LENGTH = 6;

    /** Digits reserved for the question number inside a question identifier. */
    public static final int QUESTION_NUMBER_DIGITS = 3;

    /** Digits reserved for the exam number inside an exam identifier. */
    public static final int EXAM_NUMBER_DIGITS = 2;

    /** Digits reserved for a course number wherever it appears. */
    public static final int COURSE_NUMBER_DIGITS = 2;

    /** Digits reserved for a subject number wherever it appears. */
    public static final int SUBJECT_NUMBER_DIGITS = 2;

    /** Lowest question number HSTS will allocate. */
    public static final int MIN_QUESTION_NUMBER = 1;

    /** Highest question number that fits in three digits. */
    public static final int MAX_QUESTION_NUMBER = 999;

    /** Lowest exam number HSTS will allocate. */
    public static final int MIN_EXAM_NUMBER = 1;

    /** Highest exam number that fits in two digits. */
    public static final int MAX_EXAM_NUMBER = 99;

    /** Lowest externally supplied course or subject number. */
    public static final int MIN_ORGANISATION_NUMBER = 0;

    /** Highest course or subject number that fits in two digits. */
    public static final int MAX_ORGANISATION_NUMBER = 99;

    public static final Pattern QUESTION_CODE_PATTERN = Pattern.compile("^[0-9]{5}$");

    public static final Pattern EXAM_CODE_PATTERN = Pattern.compile("^[0-9]{6}$");

    public static final Pattern ORGANISATION_NUMBER_PATTERN = Pattern.compile("^[0-9]{2}$");

    private EncodedIdentifiers() {
    }

    // ---------------------------------------------------------------- questions

    /**
     * Builds the five digit question identifier for a question number and the
     * two digit course number it belongs to.
     */
    public static String formatQuestionCode(int questionNumber, String courseNumber) {
        requireQuestionNumber(questionNumber);
        requireOrganisationNumber(courseNumber, "Course number");
        return courseNumber + pad(questionNumber, QUESTION_NUMBER_DIGITS);
    }

    /** Convenience overload taking the course number as an integer. */
    public static String formatQuestionCode(int questionNumber, int courseNumber) {
        return formatQuestionCode(questionNumber, formatOrganisationNumber(courseNumber));
    }

    /** Returns {@code true} when the value is a well formed question identifier. */
    public static boolean isValidQuestionCode(String questionCode) {
        return questionCode != null && QUESTION_CODE_PATTERN.matcher(questionCode).matches();
    }

    /** Extracts the three digit question number from a question identifier. */
    public static int questionNumberOf(String questionCode) {
        requireQuestionCode(questionCode);
        return Integer.parseInt(questionCode.substring(COURSE_NUMBER_DIGITS));
    }

    /** Extracts the two digit course number from a question identifier. */
    public static String courseNumberOfQuestionCode(String questionCode) {
        requireQuestionCode(questionCode);
        return questionCode.substring(0, COURSE_NUMBER_DIGITS);
    }

    // -------------------------------------------------------------------- exams

    /**
     * Builds the six digit exam identifier from an exam number, the two digit
     * course number and the two digit subject number.
     */
    public static String formatExamCode(int examNumber, String courseNumber, String subjectNumber) {
        requireExamNumber(examNumber);
        requireOrganisationNumber(courseNumber, "Course number");
        requireOrganisationNumber(subjectNumber, "Subject number");
        return subjectNumber + courseNumber + pad(examNumber, EXAM_NUMBER_DIGITS);
    }

    /** Convenience overload taking course and subject numbers as integers. */
    public static String formatExamCode(int examNumber, int courseNumber, int subjectNumber) {
        return formatExamCode(
                examNumber,
                formatOrganisationNumber(courseNumber),
                formatOrganisationNumber(subjectNumber)
        );
    }

    /** Returns {@code true} when the value is a well formed exam identifier. */
    public static boolean isValidExamCode(String examCode) {
        return examCode != null && EXAM_CODE_PATTERN.matcher(examCode).matches();
    }

    /** Extracts the two digit exam number from an exam identifier. */
    public static int examNumberOf(String examCode) {
        requireExamCode(examCode);
        return Integer.parseInt(examCode.substring(
                SUBJECT_NUMBER_DIGITS + COURSE_NUMBER_DIGITS
        ));
    }

    /** Extracts the two digit course number from an exam identifier. */
    public static String courseNumberOfExamCode(String examCode) {
        requireExamCode(examCode);
        return examCode.substring(
                SUBJECT_NUMBER_DIGITS,
                SUBJECT_NUMBER_DIGITS + COURSE_NUMBER_DIGITS
        );
    }

    /** Extracts the two digit subject number from an exam identifier. */
    public static String subjectNumberOfExamCode(String examCode) {
        requireExamCode(examCode);
        return examCode.substring(0, SUBJECT_NUMBER_DIGITS);
    }

    // ------------------------------------------------------- shared formatting

    /** Normalises an externally supplied course or subject number to two digits. */
    public static String formatOrganisationNumber(int number) {
        if (number < MIN_ORGANISATION_NUMBER || number > MAX_ORGANISATION_NUMBER) {
            throw new IllegalArgumentException(
                    "Course and subject numbers must be between "
                            + MIN_ORGANISATION_NUMBER + " and " + MAX_ORGANISATION_NUMBER
                            + ", but was " + number
            );
        }
        return pad(number, COURSE_NUMBER_DIGITS);
    }

    /** Returns {@code true} when the value is a well formed two digit number. */
    public static boolean isValidOrganisationNumber(String number) {
        return number != null && ORGANISATION_NUMBER_PATTERN.matcher(number).matches();
    }

    /**
     * Renders an identifier for display, falling back to a readable placeholder
     * when a legacy row has not yet been assigned an encoded identifier.
     */
    public static String displayOrUnassigned(String code) {
        return code == null || code.isBlank() ? "—" : code;
    }

    private static String pad(int value, int digits) {
        String text = Integer.toString(value);
        if (text.length() >= digits) {
            return text;
        }
        return "0".repeat(digits - text.length()) + text;
    }

    private static void requireQuestionNumber(int questionNumber) {
        if (questionNumber < MIN_QUESTION_NUMBER || questionNumber > MAX_QUESTION_NUMBER) {
            throw new IllegalArgumentException(
                    "Question numbers must be between " + MIN_QUESTION_NUMBER
                            + " and " + MAX_QUESTION_NUMBER + ", but was " + questionNumber
            );
        }
    }

    private static void requireExamNumber(int examNumber) {
        if (examNumber < MIN_EXAM_NUMBER || examNumber > MAX_EXAM_NUMBER) {
            throw new IllegalArgumentException(
                    "Exam numbers must be between " + MIN_EXAM_NUMBER
                            + " and " + MAX_EXAM_NUMBER + ", but was " + examNumber
            );
        }
    }

    private static void requireOrganisationNumber(String number, String label) {
        if (!isValidOrganisationNumber(number)) {
            throw new IllegalArgumentException(
                    label + " must be exactly two digits, but was " + number
            );
        }
    }

    private static void requireQuestionCode(String questionCode) {
        if (!isValidQuestionCode(questionCode)) {
            throw new IllegalArgumentException(
                    "Question identifiers must be exactly "
                            + QUESTION_CODE_LENGTH + " digits, but was " + questionCode
            );
        }
    }

    private static void requireExamCode(String examCode) {
        if (!isValidExamCode(examCode)) {
            throw new IllegalArgumentException(
                    "Exam identifiers must be exactly "
                            + EXAM_CODE_LENGTH + " digits, but was " + examCode
            );
        }
    }
}
