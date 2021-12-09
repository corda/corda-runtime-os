package net.corda.data

/**
 * Defines the interface for Message Bus deserialization.  The underlying mechanism may differ.
 */
interface CordaAvroSerializationFactory {
    /**
     * Create the [CordaAvroSerializer] for use in Avro/Message bus serialization
     */
    fun <T : Any> createAvroSerializer(onError: (ByteArray) -> Unit): CordaAvroSerializer<T>

    /**
     * Create the [CordaAvroDeserializer] for use in Avro/Message bus serialization
     */
    fun <T : Any> createAvroDeserializer(
        onError: (ByteArray) -> Unit,
        expectedClass: Class<T>
    ): CordaAvroDeserializer<T>
}
