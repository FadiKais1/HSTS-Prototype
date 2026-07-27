package hsts.common.type;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class ExamStatusContractTest {
    @Test
    public void examStatusHasOnlyApprovedValuesInExactOrder() {
        assertArrayEquals(
                new ExamStatus[]{
                        ExamStatus.DRAFT,
                        ExamStatus.PENDING_APPROVAL,
                        ExamStatus.APPROVED,
                        ExamStatus.REJECTED
                },
                ExamStatus.values()
        );
    }
}
