package hsts.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EncodedIdentifiersTest {

    // ---------------------------------------------------- requirements 33, 34

    @Test
    public void buildsFiveDigitQuestionCodeFromQuestionNumberAndCourseNumber() {
        assertEquals("04201", EncodedIdentifiers.formatQuestionCode(42, "01"));
        assertEquals("00101", EncodedIdentifiers.formatQuestionCode(1, "01"));
        assertEquals("99999", EncodedIdentifiers.formatQuestionCode(999, "99"));
    }

    @Test
    public void questionCodeAcceptsIntegerCourseNumbers() {
        assertEquals("11503", EncodedIdentifiers.formatQuestionCode(115, 3));
    }

    @Test
    public void splitsQuestionCodeBackIntoItsParts() {
        assertEquals(42, EncodedIdentifiers.questionNumberOf("04201"));
        assertEquals("01", EncodedIdentifiers.courseNumberOfQuestionCode("04201"));
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

    @Test
    public void buildsSixDigitExamCodeFromExamCourseAndSubjectNumbers() {
        assertEquals("010101", EncodedIdentifiers.formatExamCode(1, "01", "01"));
        assertEquals("030102", EncodedIdentifiers.formatExamCode(3, "01", "02"));
        assertEquals("999999", EncodedIdentifiers.formatExamCode(99, "99", "99"));
    }

    @Test
    public void splitsExamCodeBackIntoItsParts() {
        assertEquals(3, EncodedIdentifiers.examNumberOf("030102"));
        assertEquals("01", EncodedIdentifiers.courseNumberOfExamCode("030102"));
        assertEquals("02", EncodedIdentifiers.subjectNumberOfExamCode("030102"));
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
        assertTrue(EncodedIdentifiers.isValidQuestionCode("04201"));
        assertFalse(EncodedIdentifiers.isValidQuestionCode("4201"));
        assertFalse(EncodedIdentifiers.isValidQuestionCode("A4201"));
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
