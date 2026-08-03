package hsts.common;

/**
 * Kinds of unsolicited server-to-client events.
 *
 * <p>These support the non-functional requirement that screens stay current
 * without the user initiating a refresh.</p>
 */
public enum ServerEventType {
    /** An exam execution window or an individual submission gained extra time. */
    EXAM_TIME_EXTENDED,

    /** A notification was created for one or more users. */
    NOTIFICATION_CREATED,

    /** Submission grades were published for an execution. */
    GRADES_PUBLISHED,

    /** An exam version was approved or rejected by a coordinator. */
    EXAM_APPROVAL_CHANGED,

    /** A student submitted an exam attempt and it is now awaiting review. */
    SUBMISSION_RECEIVED,

    /** A student started an exam attempt, so the live counters have moved. */
    ATTEMPT_STARTED,

    /**
     * A question was created, edited, activated, deactivated or deleted, so any
     * open question bank is stale.
     */
    QUESTION_CHANGED,

    /**
     * An exam was created, generated or edited, so any open exam list is stale.
     * Approval transitions use {@link #EXAM_APPROVAL_CHANGED} instead.
     */
    EXAM_CHANGED,

    /**
     * One or more executions crossed their opening or closing time, so their
     * stored status changed. Nobody acts to cause this; the server publishes it
     * after advancing the statuses on its periodic cycle.
     */
    EXAM_SCHEDULE_CHANGED
}
