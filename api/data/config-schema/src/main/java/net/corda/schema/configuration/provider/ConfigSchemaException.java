package net.corda.schema.configuration.provider;

import net.corda.v5.base.exceptions.CordaRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when requested config schema is not available.
 */
public final class ConfigSchemaException extends CordaRuntimeException {
    public ConfigSchemaException(@NotNull String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }

    public ConfigSchemaException(@NotNull String msg) {
        super(msg);
    }
}
