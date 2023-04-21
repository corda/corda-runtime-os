package net.corda.v5.base.exceptions;

import org.jetbrains.annotations.Nullable;

/**
 * Exception, being thrown if a value for a specific key cannot be found in the {@link LayeredPropertyMap}.
 */
public final class ValueNotFoundException extends CordaRuntimeException {
    public ValueNotFoundException(@Nullable String message) {
        super(message);
    }
}
