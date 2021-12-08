package net.corda.data

/**
 * Defines the interface for Message Bus deserialization.  The underlying mechanism may differ.
 */
interface CordaAvroSerializationFactory {
    /**
     * Create the [CordaAvroSerializer] for use in Avro/Message bus serialization
     */
    fun <T> createAvroSerializer(onError: (ByteArray) -> Unit): CordaAvroSerializer<T>

    /**
     * Create the [CordaAvroDeserializer] for use in Avro/Message bus serialization
     */
    fun <T> createAvroDeserializer(onError: (ByteArray) -> Unit): CordaAvroDeserializer<T>
}
