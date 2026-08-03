package hsts.common;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EncodedIdentifiersTest {

    // ---------------------------------------------------- requirements 33, 34
    // Course number first, then the question number within that course.

    @Test
    public void buildsFiveDigitQuestionCodeFromQuestionNumberAndCourseNumber() {
        assertEquals("01042", EncodedIdentifiers.formatQuestionCode(42, "01"));
        assertEquals("01001", EncodedIdentifiers.formatQuestionCode(1, "01"));
        assertEquals("99999", EncodedIdentifiers.formatQuestionCode(999, "99"));
    }

    @Test
    public void questionCodeAcceptsIntegerCourseNumbers() {
        assertEquals("03115", EncodedIdentifiers.formatQuestionCode(115, 3));
    }

    @Test
    public void splitsQuestionCodeBackIntoItsParts() {
        assertEquals(42, EncodedIdentifiers.questionNumberOf("01042"));
        assertEquals("01", EncodedIdentifiers.courseNumberOfQuestionCode("01042"));
    }

    @Test
    public void rejectsQuestionNumbersOutsideThreeDigits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EncodedIdentifiers.formatQuestionCode(0, "01")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> EncodedIdentifiers.formatQuestionCode(1000, "01")
        );
    }

    @Test
    public void rejectsCourseNumbersThatAreNotTwoDigits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EncodedIdentifiers.formatQuestionCode(42, "1")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> EncodedIdentifiers.formatQuestionCode(42, "AB")
        );
    }

    // ---------------------------------------------------- requirements 38, 39
    // Subject, then course within it, then exam within that course.

    @Test
    public void buildsSixDigitExamCodeFromExamCourseAndSubjectNumbers() {
        assertEquals("010101", EncodedIdentifiers.formatExamCode(1, "01", "01"));
        assertEquals("020103", EncodedIdentifiers.formatExamCode(3, "01", "02"));
        assertEquals("999999", EncodedIdentifiers.formatExamCode(99, "99", "99"));
    }

    @Test
    public void splitsExamCodeBackIntoItsParts() {
        assertEquals(3, EncodedIdentifiers.examNumberOf("020103"));
        assertEquals("01", EncodedIdentifiers.courseNumberOfExamCode("020103"));
        assertEquals("02", EncodedIdentifiers.subjectNumberOfExamCode("020103"));
    }

    @Test
    public void rejectsExamNumbersOutsideTwoDigits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EncodedIdentifiers.formatExamCode(0, "01", "01")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> EncodedIdentifiers.formatExamCode(100, "01", "01")
        );
    }

    // ------------------------------------------------------------- validation

    @Test
    public void validatesCodeShapes() {
        assertTrue(EncodedIdentifiers.isValidQuestionCode("01042"));
        assertFalse(EncodedIdentifiers.isValidQuestionCode("1042"));
        assertFalse(EncodedIdentifiers.isValidQuestionCode("A1042"));
        assertFalse(EncodedIdentifiers.isValidQuestionCode(null));

        assertTrue(EncodedIdentifiers.isValidExamCode("010101"));
        assertFalse(EncodedIdentifiers.isValidExamCode("ABC123"));
        assertFalse(EncodedIdentifiers.isValidExamCode("01010"));
        assertFalse(EncodedIdentifiers.isValidExamCode(null));
    }

    @Test
    public void normalisesExternallySuppliedOrganisationNumbers() {
        assertEquals("00", EncodedIdentifiers.formatOrganisationNumber(0));
        assertEquals("07", EncodedIdentifiers.formatOrganisationNumber(7));
        assertEquals("99", EncodedIdentifiers.formatOrganisationNumber(99));
        assertThrows(
                IllegalArgumentException.class,
                () -> EncodedIdentifiers.formatOrganisationNumber(100)
        );
    }
}
