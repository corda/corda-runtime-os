package net.corda.testing.driver.node;

import org.jetbrains.annotations.Nullable;

public class FlowErrorException extends RuntimeException {
    public FlowErrorException(@Nullable String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    public FlowErrorException(@Nullable String message) {
        super(message);
    }
}
