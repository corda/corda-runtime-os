package net.corda.data

/**
 * Defines the interface for Message Bus deserialization.  The underlying mechanism may differ.
 */
interface CordaAvroDeserializer<T> {
    /**
     * Deserialize the given [data] into an object of type [T].
     *
     * @param data the serialized byte stream representing the data
     * @return the object represented by [data] or null if unsuccessful
     */
    fun deserialize(data: ByteArray): T?
}
