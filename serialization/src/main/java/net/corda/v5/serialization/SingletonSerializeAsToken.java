package net.corda.v5.serialization;

import net.corda.v5.base.annotations.CordaSerializable;
import org.jetbrains.annotations.NotNull;

/**
 * This interface should be implemented by classes that want to substitute a singleton token representation of
 * themselves upon checkpoint serialization.  This may be because of internal state that does not serialize well.
 *
 * This interface should only be used on singleton classes, meaning that only one should exist during runtime.
 *
 * In addition, this interface is strictly for checkpoint serialization.  It is not compatible with AMQP
 * serialization (or {@link CordaSerializable}) and will cause
 * serialization errors in AMQP.
 *
 */
public interface SingletonSerializeAsToken {

    /**
     * Gets the name of the singleton's token.
     *
     * Provide an implementation of this method to manually specify the name of this singleton's token. The token's
     * name will be set to the singleton's class name if the method is not overridden.
     *
     * @return The {@link String} name of the singleton's token.
     */
    @NotNull
    default String getTokenName() {
        return getClass().getName();
    }
}
