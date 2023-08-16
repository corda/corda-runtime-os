package net.corda.testing.driver.node;

import org.jetbrains.annotations.Nullable;

public class FlowFatalException extends RuntimeException {
    public FlowFatalException(@Nullable String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    public FlowFatalException(@Nullable String message) {
        super(message);
    }
}
