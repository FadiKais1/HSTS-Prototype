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
    ATTEMPT_STARTED
}
