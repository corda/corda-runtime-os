package net.corda.schema.membership.provider;

import net.corda.v5.base.exceptions.CordaRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception thrown when requested config schema is not available.
 */
public final class MembershipSchemaException extends CordaRuntimeException {
    public MembershipSchemaException(@NotNull String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }

    public MembershipSchemaException(@NotNull String msg) {
        super(msg);
    }
}
