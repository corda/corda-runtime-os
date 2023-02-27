package net.corda.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the interface for Message Bus serialization.  The underlying mechanism may differ.
 */
public interface CordaAvroSerializer<T> {
    /**
     * Serialize the {@code data} into a {@code byte[]}.
     *
     * @param data the object to be serialized
     * @return the serialized byte stream for transfer across the message bus or null if unsuccessful
     */
    @Nullable
    byte[] serialize(@NotNull T data);
}
