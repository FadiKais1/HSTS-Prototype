package hsts.server.entity;

import hsts.common.type.ExamStatus;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ExamAggregateTest {
    @Test
    public void newDraftUsesExplicitUnsavedIdentityAndTypedStatus() {
        Exam exam = draft(List.of());

        assertEquals(0, exam.getExamId());
        assertNull(exam.getExamCode());
        assertEquals(1, exam.getCurrentVersionNo());
        assertSame(ExamStatus.DRAFT, exam.getStatus());
        assertTrue(exam.isEditable());
        assertEquals(BigDecimal.ZERO, exam.getTotalScoreValue());
    }

    @Test
    public void compatibilityConstructorStillCreatesAnIncompleteDraft() {
        Exam exam = new Exam();

        assertEquals(0, exam.getExamId());
        assertSame(ExamStatus.DRAFT, exam.getStatus());
        assertTrue(exam.getExamQuestions().isEmpty());
    }

    @Test
    public void hydrationPreservesStableIdentityVersionTimestampsAndStatus() {
        LocalDateTime created = LocalDateTime.of(2026, 2, 1, 9, 0);
        LocalDateTime submitted = created.plusHours(1);
        LocalDateTime reviewed = submitted.plusHours(1);
        Exam exam = Exam.rehydrate(
                12, "AB12CD", 5, 1002, 4, "Midterm", 90,
                "Teacher", "Students", ExamStatus.APPROVED,
                created, reviewed, submitted, 1003, reviewed, null,
                completeSelections()
        );

        assertEquals(12, exam.getExamId());
        assertEquals("AB12CD", exam.getExamCode());
        assertEquals(4, exam.getCurrentVersionNo());
        assertSame(ExamStatus.APPROVED, exam.getStatus());
        assertEquals(created, exam.getCreatedAt());
        assertEquals(reviewed, exam.getUpdatedAt());
        assertEquals(reviewed, exam.getApprovedAt());
        assertFalse(exam.isEditable());
    }

    @Test
    public void suppliedAndReturnedSelectionListsAreDefensive() {
        List<ExamQuestion> supplied = new ArrayList<>(completeSelections());
        Exam exam = draft(supplied);
        supplied.clear();

        List<ExamQuestion> returned = exam.getExamQuestions();
        returned.get(0).updateOrder(99);

        assertEquals(2, exam.getExamQuestions().size());
        assertEquals(1, exam.getExamQuestions().get(0).getOrderNumber());
        assertThrows(UnsupportedOperationException.class,
                () -> returned.add(selection(3, 3, "1.00")));
    }

    @Test
    public void addRemoveAndReorderMaintainDeterministicControlledSelections() {
        Exam exam = draft(List.of(selection(1, 1, "40.00")));
        LocalDateTime created = exam.getCreatedAt();

        exam.addExamQuestion(selection(2, 2, "60.00"));
        assertEquals(new BigDecimal("100.00"), exam.calculateTotalScoreValue());
        assertTrue(!exam.getUpdatedAt().isBefore(created));

        exam.reorderExamQuestion(2, 1);
        assertEquals(2, exam.getExamQuestions().get(0).getQuestionId());
        assertEquals(1, exam.getExamQuestions().get(0).getOrderNumber());
        assertEquals(2, exam.getExamQuestions().get(1).getOrderNumber());
        exam.removeExamQuestion(1);
        assertEquals(2, exam.getExamQuestions().get(0).getQuestionId());
    }

    @Test
    public void duplicateLogicalQuestionAndOrderAreRejected() {
        Exam exam = draft(List.of(selection(1, 1, "50.00")));

        assertThrows(IllegalArgumentException.class,
                () -> exam.addExamQuestion(selection(1, 2, "50.00")));
        assertThrows(IllegalArgumentException.class,
                () -> exam.addExamQuestion(selection(2, 1, "50.00")));
    }

    @Test
    public void exactBigDecimalTotalAvoidsFloatingPointDrift() {
        Exam exam = draft(List.of(
                selection(1, 1, "33.33"),
                selection(2, 2, "33.33"),
                selection(3, 3, "33.34")
        ));

        assertEquals(new BigDecimal("100.00"), exam.calculateTotalScoreValue());
        assertEquals(100.0, exam.calculateTotalScore(), 0.0);
    }

    @Test
    public void draftMayBeIncompleteButCannotBeSubmittedUntilComplete() {
        Exam exam = draft(List.of());

        IllegalStateException missing = assertThrows(
                IllegalStateException.class,
                exam::submitForApproval
        );
        assertEquals("At least one question is required", missing.getMessage());

        exam.addExamQuestion(selection(1, 1, "99.99"));
        IllegalStateException score = assertThrows(
                IllegalStateException.class,
                exam::submitForApproval
        );
        assertEquals("Exam total score must equal 100", score.getMessage());
    }

    @Test
    public void submissionMovesOnlyToPendingAndPreservesCreatedAt() {
        Exam exam = draft(completeSelections());
        LocalDateTime created = exam.getCreatedAt();
        LocalDateTime submitted = created.plusMinutes(5);

        exam.submitForApproval(submitted);

        assertSame(ExamStatus.PENDING_APPROVAL, exam.getStatus());
        assertFalse(exam.isEditable());
        assertEquals(created, exam.getCreatedAt());
        assertEquals(submitted, exam.getSubmittedAt());
        assertEquals(submitted, exam.getUpdatedAt());
    }

    @Test
    public void pendingExamRejectsAllDraftMutations() {
        Exam exam = draft(completeSelections());
        exam.submitForApproval();

        assertThrows(IllegalStateException.class,
                () -> exam.updateMetadata("Changed", 60, "", "Students"));
        assertThrows(IllegalStateException.class,
                () -> exam.addExamQuestion(selection(3, 3, "1.00")));
        assertThrows(IllegalStateException.class,
                () -> exam.updateExamQuestionScore(1, BigDecimal.ONE));
    }

    @Test
    public void approveRequiresPendingAndRecordsReviewMetadata() {
        Exam exam = draft(completeSelections());
        assertThrows(IllegalStateException.class, () -> exam.approve(1003));
        LocalDateTime submitted = exam.getCreatedAt().plusMinutes(1);
        LocalDateTime reviewed = submitted.plusMinutes(1);

        exam.submitForApproval(submitted);
        exam.approve(1003, reviewed);

        assertSame(ExamStatus.APPROVED, exam.getStatus());
        assertEquals(Integer.valueOf(1003), exam.getReviewedByUserId());
        assertEquals(reviewed, exam.getReviewedAt());
        assertEquals(reviewed, exam.getApprovedAt());
        assertNull(exam.getRejectionReason());
    }

    @Test
    public void rejectRequiresPendingAndTrimmedReason() {
        Exam exam = draft(completeSelections());
        exam.submitForApproval();

        assertThrows(IllegalArgumentException.class,
                () -> exam.reject(1003, "  "));
        exam.reject(1003, "  Needs revision  ");

        assertSame(ExamStatus.REJECTED, exam.getStatus());
        assertEquals("Needs revision", exam.getRejectionReason());
        assertNull(exam.getApprovedAt());
    }

    @Test
    public void approvedExamIsImmutableExceptForExplicitNewDraftVersion() {
        Exam exam = approvedExam();

        assertThrows(IllegalStateException.class,
                () -> exam.setExamNotes("Changed", "Changed"));
        exam.startNewDraftVersion(
                "Revised", 75, "Teacher", "Students", completeSelections()
        );

        assertSame(ExamStatus.DRAFT, exam.getStatus());
        assertEquals(3, exam.getCurrentVersionNo());
        assertEquals("Revised", exam.getTitle());
        assertNull(exam.getSubmittedAt());
        assertNull(exam.getReviewedAt());
    }

    @Test
    public void rejectedExamCanStartNewDraftButPendingCannot() {
        Exam rejected = draft(completeSelections());
        rejected.submitForApproval();
        rejected.reject(1003, "Reason");
        rejected.startNewDraftVersion(
                "Revision", 60, "", "Students", completeSelections()
        );
        assertSame(ExamStatus.DRAFT, rejected.getStatus());
        assertNull(rejected.getRejectionReason());

        Exam pending = draft(completeSelections());
        pending.submitForApproval();
        assertThrows(IllegalStateException.class, () -> pending.startNewDraftVersion(
                "Revision", 60, "", "Students", completeSelections()
        ));
    }

    @Test
    public void entityDoesNotApplyRoleOrSelfReviewPolicy() {
        Exam exam = Exam.createDraft(
                5, 1003, "Coordinator exam", 60, "", "Students",
                completeSelections()
        );
        exam.submitForApproval();
        exam.approve(1003);

        assertSame(ExamStatus.APPROVED, exam.getStatus());
        assertEquals(Integer.valueOf(1003), exam.getReviewedByUserId());
    }

    @Test
    public void hydrationRejectsContradictoryWorkflowMetadata() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThrows(IllegalArgumentException.class, () -> Exam.rehydrate(
                1, "ABC123", 5, 1002, 1, "Exam", 60, "", "Students",
                ExamStatus.DRAFT, created, created, created, null, null, null,
                completeSelections()
        ));
        assertThrows(IllegalArgumentException.class, () -> Exam.rehydrate(
                1, "ABC123", 5, 1002, 1, "Exam", 60, "", "Students",
                ExamStatus.REJECTED, created, created, created, 1003, created, null,
                completeSelections()
        ));
    }

    private static Exam approvedExam() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 10, 0);
        return Exam.rehydrate(
                8, "ABC123", 5, 1002, 2, "Approved", 60, "", "Students",
                ExamStatus.APPROVED, created, created.plusHours(2),
                created.plusHours(1), 1003, created.plusHours(2), null,
                completeSelections()
        );
    }

    private static Exam draft(List<ExamQuestion> selections) {
        return Exam.createDraft(
                5, 1002, "Exam", 60, "Teacher", "Students", selections
        );
    }

    private static List<ExamQuestion> completeSelections() {
        return List.of(
                selection(1, 1, "40.00"),
                selection(2, 2, "60.00")
        );
    }

    private static ExamQuestion selection(int questionId, int orderNumber,
                                          String score) {
        return ExamQuestionAggregateTest.selection(
                questionId,
                1,
                orderNumber,
                score
        );
    }
}
