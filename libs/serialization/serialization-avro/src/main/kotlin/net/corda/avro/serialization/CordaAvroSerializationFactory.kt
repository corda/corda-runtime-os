package net.corda.avro.serialization

import java.util.function.Consumer

/**
 * Defines the interface for message bus deserialization. The underlying mechanism may differ.
 */
interface CordaAvroSerializationFactory {

    /**
     * Create the {@link CordaAvroSerializer} for use in Avro/message bus serialization.
     */
    fun <T : Any> createAvroSerializer(throwOnError: Boolean = true, onError: Consumer<ByteArray>? = null): CordaAvroSerializer<T>

    /**
     * Create the {@link CordaAvroDeserializer} for use in Avro/message bus serialization.
     */
    fun <T : Any> createAvroDeserializer(
        onError: Consumer<ByteArray>,
        expectedClass: Class<T>
    ): CordaAvroDeserializer<T>
}