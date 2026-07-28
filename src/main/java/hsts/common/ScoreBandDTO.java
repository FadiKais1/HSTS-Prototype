package hsts.common;

import java.io.Serializable;

public final class ScoreBandDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int lowerBoundInclusive;
    private final int upperBoundInclusive;
    private final int submissionCount;

    public ScoreBandDTO(int lowerBoundInclusive, int upperBoundInclusive,
                        int submissionCount) {
        if (lowerBoundInclusive < 0 || lowerBoundInclusive > 100) {
            throw new IllegalArgumentException(
                    "Score band lower bound must be between 0 and 100"
            );
        }
        if (upperBoundInclusive < 0 || upperBoundInclusive > 100) {
            throw new IllegalArgumentException(
                    "Score band upper bound must be between 0 and 100"
            );
        }
        if (lowerBoundInclusive > upperBoundInclusive) {
            throw new IllegalArgumentException(
                    "Score band lower bound cannot exceed upper bound"
            );
        }
        if (submissionCount < 0) {
            throw new IllegalArgumentException(
                    "Score band submission count cannot be negative"
            );
        }

        this.lowerBoundInclusive = lowerBoundInclusive;
        this.upperBoundInclusive = upperBoundInclusive;
        this.submissionCount = submissionCount;
    }

    public int getLowerBoundInclusive() {
        return lowerBoundInclusive;
    }

    public int getUpperBoundInclusive() {
        return upperBoundInclusive;
    }

    public int getSubmissionCount() {
        return submissionCount;
    }
}
