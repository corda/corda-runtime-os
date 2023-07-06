package net.corda.testing.driver.node;

public class FlowErrorException extends RuntimeException {
    public FlowErrorException(String message, Throwable cause) {
        super(message, cause);
    }

    public FlowErrorException(String message) {
        super(message);
    }
}
