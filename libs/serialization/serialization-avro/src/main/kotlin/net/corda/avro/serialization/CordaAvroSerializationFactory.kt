package net.corda.avro.serialization

import java.util.function.Consumer

/**
 * Defines the interface for message bus deserialization. The underlying mechanism may differ.
 */
interface CordaAvroSerializationFactory {

    /**
     * Create the {@link CordaAvroSerializer} for use in Avro/message bus serialization.
     *
     * @param T the type to serialize
     * @param throwOnSerializationError throw an exception on serialization failure, or return null (defaults to true)
     * @param onError a lambda to run on serialization error, will run regardless of throwOnSerializationError
     * @return an implementation of CordaAvroSerializer
     */
    fun <T : Any> createAvroSerializer(
        throwOnSerializationError: Boolean = true,
        onError: ((ByteArray) -> Unit)? = null
    ): CordaAvroSerializer<T>

    /**
     * Create the {@link CordaAvroDeserializer} for use in Avro/message bus serialization.
     *
     * @param T
     * @param onError lambda to be run on deserialization error
     * @param expectedClass the expected class type to serialize
     * @return an implementation of the CordaAvroDeserializer
     */
    fun <T : Any> createAvroDeserializer(
        onError: Consumer<ByteArray>,
        expectedClass: Class<T>
    ): CordaAvroDeserializer<T>
}