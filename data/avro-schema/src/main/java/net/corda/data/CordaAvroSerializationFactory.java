package net.corda.data;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Defines the interface for Message Bus deserialization.  The underlying mechanism may differ.
 */
public interface CordaAvroSerializationFactory {
    /**
     * Create the {@link CordaAvroSerializer} for use in Avro/Message bus serialization
     */
    @NotNull
    <T> CordaAvroSerializer<T> createAvroSerializer(@NotNull Consumer<byte[]> onError);

    /**
     * Create the {@link CordaAvroDeserializer} for use in Avro/Message bus serialization
     */
    @NotNull
    <T> CordaAvroDeserializer<T> createAvroDeserializer(
        @NotNull Consumer<byte[]> onError,
        @NotNull Class<T> expectedClass
    );
}
