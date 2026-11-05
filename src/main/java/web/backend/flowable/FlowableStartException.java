package web.backend.flowable;

public class FlowableStartException extends RuntimeException {
    private final boolean outcomeUnknown;

    public FlowableStartException(String message, boolean outcomeUnknown) {
        super(message);
        this.outcomeUnknown = outcomeUnknown;
    }

    public FlowableStartException(String message, boolean outcomeUnknown, Throwable cause) {
        super(message, cause);
        this.outcomeUnknown = outcomeUnknown;
    }

    public boolean isOutcomeUnknown() {
        return outcomeUnknown;
    }
}