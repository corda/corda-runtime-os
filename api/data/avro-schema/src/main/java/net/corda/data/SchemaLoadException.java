package net.corda.data;

import net.corda.v5.base.exceptions.CordaRuntimeException;

public final class SchemaLoadException extends CordaRuntimeException {
    SchemaLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    SchemaLoadException(String message) {
        super(message);
    }
}
