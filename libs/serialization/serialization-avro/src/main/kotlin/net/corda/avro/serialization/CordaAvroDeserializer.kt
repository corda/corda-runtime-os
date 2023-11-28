package net.corda.avro.serialization

/**
 * Defines the interface for message bus deserialization. The underlying mechanism may differ.
 */
interface CordaAvroDeserializer<T> {
    /**
     * Deserialize the given `data` into an object of type `T`.
     *
     * @param data the serialized byte stream representing the data
     * @return the object represented by `data` or `null` if unsuccessful
     */
    fun deserialize(data: ByteArray): T?
}
