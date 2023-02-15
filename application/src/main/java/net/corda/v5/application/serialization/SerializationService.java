package net.corda.v5.application.serialization;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.serialization.SerializedBytes;
import org.jetbrains.annotations.NotNull;

/**
 * Allows flows to serialize and deserialize objects to/from byte arrays.
 * <p>
 * Objects are serialized and deserialized using AMQP serialization.
 * <p>
 * Corda provides an instance of {@link SerializationService} to flows via property injection.
 */
@DoNotImplement
public interface SerializationService {

    /**
     * Serializes the input {@code obj}.
     *
     * @param obj The object to serialize.
     *
     * @return {@link SerializedBytes} containing the serialized representation of the input object.
     */
    @NotNull
    <T> SerializedBytes<T> serialize(@NotNull T obj);

    /**
     * Deserializes the input serialized bytes into an object of type <T>.
     *
     * @param serializedBytes The {@link SerializedBytes} to deserialize.
     * @param clazz {@link Class} containing the type <T> to deserialize to.
     * @param <T> The type to deserialize to.
     *
     * @return A new instance of type <T> created from the input {@code serializedBytes}.
     */
    @NotNull
    <T> T deserialize(@NotNull SerializedBytes<T> serializedBytes, @NotNull Class<T> clazz);

    /**
     * Deserializes the input serialized bytes into an object of type <T>.
     *
     * @param bytes The {@code byte[]} to deserialize.
     * @param clazz {@link Class} containing the type <T> to deserialize to.
     * @param <T> The type to deserialize to.
     *
     * @return A new instance of type <T> created from the input {@code bytes}.
     */
    @NotNull
    <T> T deserialize(@NotNull byte[] bytes, @NotNull Class<T> clazz);
}
