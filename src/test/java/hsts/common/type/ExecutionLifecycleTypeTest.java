package hsts.common.type;

import hsts.common.RequestType;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class ExecutionLifecycleTypeTest {
    @Test
    public void executionStatusHasExactValuesInOrder() {
        assertArrayEquals(
                new ExecutionStatus[]{
                        ExecutionStatus.SCHEDULED,
                        ExecutionStatus.OPEN,
                        ExecutionStatus.CLOSED
                },
                ExecutionStatus.values()
        );
    }

    @Test
    public void submissionStatusHasExactValuesInOrder() {
        assertArrayEquals(
                new SubmissionStatus[]{
                        SubmissionStatus.IN_PROGRESS,
                        SubmissionStatus.SUBMITTED,
                        SubmissionStatus.AUTO_SUBMITTED,
                        SubmissionStatus.PUBLISHED
                },
                SubmissionStatus.values()
        );
    }

    @Test
    public void requestTypePreservesExistingValuesAndAddsExecutionValues() {
        assertArrayEquals(
                new RequestType[]{
                        RequestType.GET_ALL_QUESTIONS,
                        RequestType.GET_QUESTION_BY_ID,
                        RequestType.UPDATE_QUESTION,
                        RequestType.LOGIN,
                        RequestType.LOGOUT,
                        RequestType.GET_MY_COURSES,
                        RequestType.LIST_QUESTIONS,
                        RequestType.CREATE_QUESTION,
                        RequestType.ACTIVATE_QUESTION,
                        RequestType.DEACTIVATE_QUESTION,
                        RequestType.GET_QUESTION_HISTORY,
                        RequestType.LIST_MY_EXAMS,
                        RequestType.GET_MY_EXAM,
                        RequestType.CREATE_EXAM,
                        RequestType.GENERATE_EXAM,
                        RequestType.LIST_PENDING_EXAMS,
                        RequestType.GET_PENDING_EXAM,
                        RequestType.UPDATE_EXAM,
                        RequestType.SUBMIT_EXAM_FOR_APPROVAL,
                        RequestType.APPROVE_EXAM,
                        RequestType.REJECT_EXAM,
                        RequestType.SCHEDULE_EXAM_EXECUTION,
                        RequestType.LIST_MY_EXAM_EXECUTIONS,
                        RequestType.VALIDATE_EXECUTION_CODE,
                        RequestType.START_EXAM_ATTEMPT,
                        RequestType.GET_ACTIVE_EXAM_ATTEMPT,
                        RequestType.SAVE_EXAM_ANSWER,
                        RequestType.SUBMIT_EXAM_ATTEMPT,
                        RequestType.EXTEND_SUBMISSION_TIME
                },
                RequestType.values()
        );
    }
}
