package net.corda.testing.driver.node;

public class FlowFatalException extends RuntimeException {
    public FlowFatalException(String message, Throwable cause) {
        super(message, cause);
    }

    public FlowFatalException(String message) {
        super(message);
    }
}
