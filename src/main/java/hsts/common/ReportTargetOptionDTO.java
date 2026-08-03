package hsts.common;

import java.io.Serializable;
import java.util.Objects;

/**
 * One selectable target for a Principal report.
 *
 * <p>The Reports page previously asked the Principal to type a numeric id into a
 * free text field, so running a report meant knowing that a particular teacher
 * is user 1005 or that Mathematics - Grade 10 is course 2. These options let the
 * page offer real names instead, and carry the id the report routes still
 * expect.</p>
 */
public final class ReportTargetOptionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int targetId;
    private final String displayName;

    /** Secondary text such as a course code or a student's email. May be null. */
    private final String detail;

    public ReportTargetOptionDTO(int targetId, String displayName, String detail) {
        this.targetId = targetId;
        this.displayName = displayName;
        this.detail = detail;
    }

    public int getTargetId() {
        return targetId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDetail() {
        return detail;
    }

    /** What a combo box shows: the name, with the detail in brackets when present. */
    public String getLabel() {
        return detail == null || detail.isBlank()
                ? displayName
                : displayName + " (" + detail + ")";
    }

    @Override
    public String toString() {
        return getLabel();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReportTargetOptionDTO option)) {
            return false;
        }
        return targetId == option.targetId
                && Objects.equals(displayName, option.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetId, displayName);
    }
}
