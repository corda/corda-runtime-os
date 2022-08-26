package net.corda.data

import java.util.function.Consumer

/**
 * Defines the interface for Message Bus deserialization.  The underlying mechanism may differ.
 */
interface CordaAvroSerializationFactory {
    /**
     * Create the [CordaAvroSerializer] for use in Avro/Message bus serialization
     */
    fun <T : Any> createAvroSerializer(onError: Consumer<ByteArray>): CordaAvroSerializer<T>

    /**
     * Create the [CordaAvroDeserializer] for use in Avro/Message bus serialization
     */
    fun <T : Any> createAvroDeserializer(
        onError: Consumer<ByteArray>,
        expectedClass: Class<T>
    ): CordaAvroDeserializer<T>
}
