package net.corda.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the interface for message bus serialization. The underlying mechanism may differ.
 */
public interface CordaAvroSerializer<T> {
    /**
     * Serialize the {@code data} into a {@code byte[]}.
     *
     * @param data The object to be serialized.
     * @return The serialized byte stream for transfer across the message bus or null if unsuccessful.
     */
    @Nullable
    byte[] serialize(@NotNull T data);
}
