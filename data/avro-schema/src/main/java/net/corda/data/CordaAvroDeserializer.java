package net.corda.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the interface for Message Bus deserialization.  The underlying mechanism may differ.
 */
public interface CordaAvroDeserializer<T> {
    /**
     * Deserialize the given {@code data} into an object of type {@code T}.
     *
     * @param data the serialized byte stream representing the data
     * @return the object represented by {@code data} or {@code null} if unsuccessful
     */
    @Nullable
    T deserialize(@NotNull byte[] data);
}
